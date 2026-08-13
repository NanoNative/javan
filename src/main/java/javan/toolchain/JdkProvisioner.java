package javan.toolchain;

import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Downloads, verifies, stages, and registers a JDK from a verified provider.
 *
 * <p>Provider metadata is the only vendor-specific seam. The network boundary is deliberately
 * narrow: a provider catalog supplies a concrete artifact URL and SHA-256 value, {@code curl}
 * is restricted to HTTPS, and the archive is verified locally before extraction. A staging
 * directory is never registered or selected.</p>
 */
public final class JdkProvisioner {
    private static final Provider TEMURIN = new Provider(
        "temurin",
        "Eclipse Adoptium",
        "https://api.adoptium.net/v3/assets/latest/",
        "eclipse"
    );
    private final Path javanHome;
    private final ManagedJdkStore store;
    private final ProcessRunner processRunner;
    private final String osName;
    private final String architecture;

    /**
     * Creates the JDK provisioner for the current host.
     *
     * @param javanHome user-global Javan home
     */
    public JdkProvisioner(final Path javanHome) {
        this(javanHome, new ManagedJdkStore(javanHome), new ProcessRunner(), System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    JdkProvisioner(
        final Path javanHome,
        final ManagedJdkStore store,
        final ProcessRunner processRunner,
        final String osName,
        final String architecture
    ) {
        this.javanHome = normalize(javanHome, "javanHome");
        this.store = Objects.requireNonNull(store, "store");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.osName = Objects.requireNonNull(osName, "osName");
        this.architecture = Objects.requireNonNull(architecture, "architecture");
    }

    /**
     * Installs a JDK for a plain feature version or {@code provider@feature} selector.
     *
     * @param selector requested Java feature version
     * @return verified installed JDK metadata
     * @throws IOException when the catalog, transfer, checksum, extraction, or publication fails
     * @throws InterruptedException when the process is interrupted
     */
    public ToolchainMetadata provision(final String selector) throws IOException, InterruptedException {
        final Request request = Request.parse(selector, TEMURIN, platform(), architecture());
        final ManagedJdkStore.Location location = store.prepare().orElseThrow(
            () -> new IOException("No writable JDK installation location is available")
        );
        final Asset asset = asset(request);
        final Path archive = location.downloadCache().resolve(asset.name());
        if (!Files.isRegularFile(archive)) {
            download(asset.link(), archive);
        }
        verifyChecksum(archive, asset.checksum());
        final Path finalHome = location.installRoot().resolve(request.provider().id() + "-" + request.feature() + "-" + request.platform() + "-" + request.architecture());
        final ToolchainMetadata installed = existingOrPublish(request, asset, archive, finalHome);
        register(installed);
        return installed;
    }

    private ToolchainMetadata existingOrPublish(
        final Request request,
        final Asset asset,
        final Path archive,
        final Path finalHome
    ) throws IOException, InterruptedException {
        if (Files.exists(finalHome)) {
            return metadata(request, asset, verifiedHome(finalHome));
        }
        if (isMacPackage(archive)) {
            return publishMacPackage(request, asset, archive, finalHome);
        }
        final Path staging = sibling(finalHome, finalHome.getFileName() + ".javan-staging");
        if (Files.exists(staging)) {
            throw new IOException("JDK staging directory already exists: " + staging);
        }
        Files.createDirectories(staging);
        extract(archive, staging);
        final Path home = verifiedHome(staging);
        publish(staging, finalHome);
        return metadata(request, asset, finalHome.equals(home) ? finalHome : finalHome.resolve(relativeHome(staging, home)));
    }

    private ToolchainMetadata publishMacPackage(
        final Request request,
        final Asset asset,
        final Path archive,
        final Path finalHome
    ) throws IOException, InterruptedException {
        final Path staging = sibling(finalHome, finalHome.getFileName() + ".javan-staging");
        if (Files.exists(staging)) {
            throw new IOException("JDK staging directory already exists: " + staging);
        }
        requireSuccess(
            processRunner.run(
                Objects.requireNonNull(staging.getParent(), "JDK package staging parent"),
                List.of("pkgutil", "--expand-full", archive.toString(), staging.toString())
            ),
            "expand JDK package"
        );
        final Path home = macPackageHome(staging);
        final Path bundle = Objects.requireNonNull(home.getParent(), "JDK contents").getParent();
        if (bundle == null) {
            throw new IOException("Expanded JDK package has no JDK bundle: " + archive);
        }
        publish(bundle, finalHome);
        requireSuccess(processRunner.run(staging, List.of("rm", "-rf", staging.toString())), "remove JDK package staging");
        return metadata(request, asset, finalHome.resolve("Contents/Home"));
    }

    private Asset asset(final Request request) throws IOException, InterruptedException {
        final String catalog = request.provider().catalog() + request.feature() + "/hotspot?architecture=" + request.architecture()
            + "&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=" + request.platform()
            + "&page=0&page_size=1&project=jdk&release_type=ga&sort_method=DEFAULT&sort_order=DESC&vendor=" + request.provider().catalogVendor();
        final ProcessRunner.Result result = processRunner.run(
            javanHome,
            List.of("curl", "-fsSL", "--proto", "=https", "--tlsv1.2", catalog)
        );
        requireSuccess(result, "read " + request.provider().name() + " catalog");
        final String link = jsonValue(result.stdout(), "link");
        final String checksum = jsonValue(result.stdout(), "checksum");
        final String name = jsonValue(result.stdout(), "name");
        if (!isHttps(link) || !isSha256(checksum) || Strings2.isBlank(name)) {
            throw new IOException(request.provider().name() + " catalog returned incomplete or unsafe JDK metadata");
        }
        return new Asset(link, lowercaseAscii(checksum), name);
    }

    private void download(final String link, final Path archive) throws IOException, InterruptedException {
        final Path parent = Objects.requireNonNull(archive.getParent(), "archive parent");
        Files.createDirectories(parent);
        final Path partial = sibling(archive, archive.getFileName() + ".partial");
        if (Files.exists(partial)) {
            Files.deleteIfExists(partial);
        }
        final ProcessRunner.Result result = processRunner.run(
            parent,
            List.of("curl", "-fL", "--proto", "=https", "--tlsv1.2", "--output", partial.toString(), link)
        );
        requireSuccess(result, "download JDK archive");
        publish(partial, archive);
    }

    private void verifyChecksum(final Path archive, final String expected) throws IOException, InterruptedException {
        final ProcessRunner.Result result = checksumResult(archive);
        requireSuccess(result, "calculate SHA-256");
        final String actual = firstSha256(result.stdout() + "\n" + result.stderr());
        if (!constantTimeEquals(expected, actual)) {
            throw new IOException("JDK archive SHA-256 mismatch: " + archive);
        }
    }

    private ProcessRunner.Result checksumResult(final Path archive) throws IOException, InterruptedException {
        if (isWindows()) {
            return processRunner.run(archive.getParent(), List.of("certutil", "-hashfile", archive.toString(), "SHA256"));
        }
        final ProcessRunner.Result sha256sum = processRunner.run(archive.getParent(), List.of("sha256sum", archive.toString()));
        if (sha256sum.exitCode() == 0) {
            return sha256sum;
        }
        return processRunner.run(archive.getParent(), List.of("shasum", "-a", "256", archive.toString()));
    }

    private void extract(final Path archive, final Path staging) throws IOException, InterruptedException {
        final List<String> command = archive.getFileName().toString().endsWith(".zip")
            ? List.of("tar", "-xf", archive.toString(), "-C", staging.toString(), "--strip-components=1")
            : List.of("tar", "-xzf", archive.toString(), "-C", staging.toString(), "--strip-components=1");
        final ProcessRunner.Result result = processRunner.run(staging, command);
        requireSuccess(result, "extract JDK archive");
    }

    private Path macPackageHome(final Path staging) throws IOException, InterruptedException {
        final ProcessRunner.Result result = processRunner.run(
            staging,
            List.of("find", staging.toString(), "-path", "*/Contents/Home/release", "-type", "f", "-print", "-quit")
        );
        requireSuccess(result, "locate JDK package home");
        final String release = Strings2.trimAscii(result.stdout());
        if (Strings2.isBlank(release)) {
            throw new IOException("Expanded JDK package does not contain Contents/Home/release");
        }
        final Path releaseFile = Path.of(release).toAbsolutePath().normalize();
        final Path home = releaseFile.getParent();
        if (home == null || !Files.isRegularFile(home.resolve("release"))) {
            throw new IOException("Expanded JDK package has an invalid JDK home: " + releaseFile);
        }
        return home;
    }

    private void publish(final Path staging, final Path output) throws IOException, InterruptedException {
        if (isWindows()) {
            final String command = "move /Y \"" + windowsCommandPath(staging) + "\" \"" + windowsCommandPath(output) + "\"";
            requireSuccess(processRunner.run(output.getParent(), List.of("cmd.exe", "/d", "/s", "/c", command)), "publish JDK");
            return;
        }
        requireSuccess(processRunner.run(output.getParent(), List.of("mv", staging.toString(), output.toString())), "publish JDK");
    }

    private Path verifiedHome(final Path root) throws IOException {
        final Path direct = root.resolve("release");
        if (Files.isRegularFile(direct)) {
            return root;
        }
        final Path macHome = root.resolve("Contents/Home");
        if (Files.isRegularFile(macHome.resolve("release"))) {
            return macHome;
        }
        throw new IOException("Extracted archive does not contain a JDK release file: " + root);
    }

    private ToolchainMetadata metadata(final Request request, final Asset asset, final Path home) throws IOException {
        final Path javaExecutable = home.resolve("bin").resolve(executable("java"));
        final Path javac = home.resolve("bin").resolve(executable("javac"));
        if (!Files.isRegularFile(javaExecutable) || !Files.isExecutable(javaExecutable) || !Files.isRegularFile(javac) || !Files.isExecutable(javac)) {
            throw new IOException("Extracted JDK does not contain usable java and javac launchers: " + home);
        }
        final String version = releaseValue(home, "JAVA_VERSION");
        if (Strings2.isBlank(version)) {
            throw new IOException("Extracted JDK does not declare JAVA_VERSION: " + home);
        }
        return new ToolchainMetadata(
            request.provider().id() + "-" + request.feature() + "-" + request.platform() + "-" + request.architecture(),
            ToolchainKind.JDK,
            version,
            home,
            javaExecutable,
            javac,
            Optional.of(request.provider().name()),
            Optional.of("sha256:" + asset.checksum())
        );
    }

    private void register(final ToolchainMetadata metadata) throws IOException {
        final Path file = javanHome.resolve("toolchains").resolve(metadata.id()).resolve("toolchain.toml");
        final String content = "id = \"" + metadata.id() + "\"\n"
            + "kind = \"jdk\"\n"
            + "version = \"" + metadata.version() + "\"\n"
            + "home = \"" + metadata.home() + "\"\n"
            + "java = \"" + metadata.javaExecutable() + "\"\n"
            + "javac = \"" + metadata.javacExecutable() + "\"\n"
            + "vendor = \"" + metadata.vendor().orElseThrow() + "\"\n"
            + "checksum = \"" + metadata.checksum().orElseThrow() + "\"\n";
        Files2.writeString(file, content);
    }

    private String executable(final String name) {
        return isWindows() ? name + ".exe" : name;
    }

    private String platform() {
        final String value = lowercaseAscii(osName);
        if (value.contains("win")) {
            return "windows";
        }
        if (value.contains("mac") || value.contains("darwin")) {
            return "mac";
        }
        if (value.contains("linux")) {
            return "linux";
        }
        throw new IllegalArgumentException("Temurin download is unsupported on this operating system: " + osName);
    }

    private String architecture() {
        final String value = lowercaseAscii(architecture);
        if ("x86_64".equals(value) || "amd64".equals(value) || "x64".equals(value)) {
            return "x64";
        }
        if ("aarch64".equals(value) || "arm64".equals(value)) {
            return "aarch64";
        }
        throw new IllegalArgumentException("Temurin download is unsupported on this architecture: " + architecture);
    }

    private boolean isWindows() {
        return lowercaseAscii(osName).contains("win");
    }

    private boolean isMacPackage(final Path archive) {
        return platform().equals("mac") && archive.getFileName().toString().endsWith(".pkg");
    }

    private static String releaseValue(final Path home, final String key) throws IOException {
        final String content = Files.readString(home.resolve("release"));
        final String prefix = key + "=";
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.trimAscii(Strings2.slice(content, start, index));
                if (line.startsWith(prefix)) {
                    return unquote(Strings2.slice(line, prefix.length(), line.length()));
                }
                start = index + 1;
            }
        }
        return "";
    }

    private static Path relativeHome(final Path staging, final Path home) {
        return staging.relativize(home);
    }

    private static String jsonValue(final String json, final String key) throws IOException {
        final String marker = "\"" + key + "\"";
        final int keyIndex = json.indexOf(marker);
        if (keyIndex < 0) {
            throw new IOException("Adoptium catalog is missing " + key);
        }
        int index = keyIndex + marker.length();
        while (index < json.length() && json.charAt(index) != ':') {
            index++;
        }
        index++;
        while (index < json.length() && asciiWhitespace(json.charAt(index))) {
            index++;
        }
        if (index >= json.length() || json.charAt(index) != '"') {
            throw new IOException("Adoptium catalog has an invalid " + key);
        }
        index++;
        final StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (; index < json.length(); index++) {
            final char character = json.charAt(index);
            if (character == '"' && !escaped) {
                return value.toString();
            }
            if (character == '\\' && !escaped) {
                escaped = true;
            } else {
                value.append(character);
                escaped = false;
            }
        }
        throw new IOException("Adoptium catalog has an unterminated " + key);
    }

    private static String firstSha256(final String text) throws IOException {
        for (int index = 0; index + 64 <= text.length(); index++) {
            final String candidate = Strings2.slice(text, index, index + 64);
            if (isSha256(candidate)) {
                return lowercaseAscii(candidate);
            }
        }
        throw new IOException("SHA-256 tool did not return a checksum");
    }

    private static boolean constantTimeEquals(final String expected, final String actual) {
        if (expected.length() != actual.length()) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < expected.length(); index++) {
            difference |= expected.charAt(index) ^ actual.charAt(index);
        }
        return difference == 0;
    }

    private static boolean isSha256(final String value) {
        if (value.length() != 64) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f') || (character >= 'A' && character <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHttps(final String value) {
        return value.startsWith("https://");
    }

    private static String lowercaseAscii(final String value) {
        final StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                result.append((char) (character + ('a' - 'A')));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String unquote(final String value) {
        final String trimmed = Strings2.trimAscii(value);
        if (trimmed.length() >= 2 && trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length() - 1) == '"') {
            return Strings2.slice(trimmed, 1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static boolean asciiWhitespace(final char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private static String windowsCommandPath(final Path path) {
        final String value = path.toString();
        final StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '"') {
                escaped.append('"');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static void requireSuccess(final ProcessRunner.Result result, final String action) throws IOException {
        if (result.exitCode() == 0) {
            return;
        }
        final String detail = Strings2.isBlank(result.stderr()) ? result.stdout() : result.stderr();
        throw new IOException("Unable to " + action + ": " + detail);
    }

    private static Path normalize(final Path path, final String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    private static Path sibling(final Path path, final String name) {
        return Objects.requireNonNull(path.getParent(), "path parent").resolve(name);
    }

    private record Request(Provider provider, String feature, String platform, String architecture) {
        private static Request parse(
            final String selector,
            final Provider defaultProvider,
            final String platform,
            final String architecture
        ) {
            if (Strings2.isBlank(selector)) {
                throw new IllegalArgumentException("Missing JDK selector; use 25 or temurin@25");
            }
            final String value = Strings2.trimAscii(selector);
            final int separator = value.indexOf('@');
            final String vendor = separator < 0 ? defaultProvider.id() : Strings2.slice(value, 0, separator);
            final String version = separator < 0 ? value : Strings2.slice(value, separator + 1, value.length());
            if (separator >= 0 && (separator == 0 || separator != value.lastIndexOf('@') || separator == value.length() - 1)) {
                throw new IllegalArgumentException("Invalid JDK selector: " + value);
            }
            final Provider provider = JdkProvisioner.provider(vendor).orElseThrow(() -> new IllegalArgumentException(
                "No verified JDK download provider for " + vendor + "; install it locally first or select " + defaultProvider.id() + "@" + version
            ));
            if (!feature(version).equals(version)) {
                throw new IllegalArgumentException("Automatic JDK download requires a Java feature version: " + version);
            }
            return new Request(provider, version, platform, architecture);
        }

        private static String feature(final String value) {
            if (Strings2.isBlank(value)) {
                return "";
            }
            for (int index = 0; index < value.length(); index++) {
                final char character = value.charAt(index);
                if (character < '0' || character > '9') {
                    return "";
                }
            }
            return value;
        }
    }

    private record Asset(String link, String checksum, String name) {
    }

    private record Provider(String id, String name, String catalog, String catalogVendor) {
        private Provider {
            id = Strings2.trimAscii(Objects.requireNonNull(id, "id"));
            name = Strings2.trimAscii(Objects.requireNonNull(name, "name"));
            catalog = Strings2.trimAscii(Objects.requireNonNull(catalog, "catalog"));
            catalogVendor = Strings2.trimAscii(Objects.requireNonNull(catalogVendor, "catalogVendor"));
        }
    }

    private static Optional<Provider> provider(final String id) {
        if (TEMURIN.id().equals(lowercaseAscii(id))) {
            return Optional.of(TEMURIN);
        }
        return Optional.empty();
    }
}
