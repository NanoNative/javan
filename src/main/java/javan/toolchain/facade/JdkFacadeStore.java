package javan.toolchain.facade;

import javan.toolchain.JdkInventory;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Maintains immutable per-JDK facades and one switchable {@code current} link.
 */
public final class JdkFacadeStore {
    private final Path root;
    private final JdkFacadeGenerator generator;
    private final ProcessRunner processRunner;
    private final String osName;

    /**
     * Creates a facade store using the default facade generator and process runner.
     *
     * @param root facade store root
     */
    public JdkFacadeStore(final Path root) {
        this(root, new JdkFacadeGenerator(), new ProcessRunner(), System.getProperty("os.name", ""));
    }

    /**
     * Creates a facade store with supplied infrastructure.
     *
     * @param root facade store root
     * @param generator JDK facade generator
     * @param processRunner process runner for atomic link replacement
     */
    public JdkFacadeStore(final Path root, final JdkFacadeGenerator generator, final ProcessRunner processRunner) {
        this(root, generator, processRunner, System.getProperty("os.name", ""));
    }

    /**
     * Creates a facade store with supplied infrastructure and operating-system identity.
     *
     * @param root facade store root
     * @param generator JDK facade generator
     * @param processRunner process runner for atomic link replacement
     * @param osName operating-system name used for public JDK bundle metadata
     */
    public JdkFacadeStore(
        final Path root,
        final JdkFacadeGenerator generator,
        final ProcessRunner processRunner,
        final String osName
    ) {
        this.root = normalize(root, "root");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.osName = Objects.requireNonNull(osName, "osName");
    }

    /**
     * Ensures a facade exists for the selected JDK and switches the current link.
     *
     * @param selected selected facade-ready JDK
     * @return activated facade and stable current link
     * @throws IOException when the facade cannot be created or published
     * @throws InterruptedException when interrupted while switching links
     */
    public Activation activate(final JdkInventory.Entry selected) throws IOException, InterruptedException {
        Objects.requireNonNull(selected, "selected");
        if (!selected.facadeReady()) {
            throw new IllegalArgumentException("Selected JDK does not contain usable release metadata: " + selected.candidate().home());
        }
        Files.createDirectories(root.resolve("available"));
        final Path facadeHome = root.resolve("available").resolve(facadeId(selected));
        final JdkFacadeGenerator.Result facade = facade(facadeHome, selected);
        final Path current = root.resolve("current");
        replaceCurrentLink(current, facade.home());
        refreshPublicBundle(selected);
        return new Activation(current, facade);
    }

    /**
     * Registers one Javan-owned public JDK home for platform discovery metadata.
     *
     * @param publicHome stable public JDK home
     * @throws IOException when the private facade metadata cannot be written
     */
    public void registerPublicHome(final Path publicHome) throws IOException {
        final Path home = normalize(publicHome, "publicHome");
        Files2.writeString(root.resolve("javan-public-home.txt"), "publicHome=" + home + System.lineSeparator());
    }

    /**
     * Refreshes platform JDK-bundle metadata after publishing the stable public home.
     *
     * @param selected selected backend JDK
     * @throws IOException when owned public bundle metadata cannot be updated
     */
    public void refreshPublicBundle(final JdkInventory.Entry selected) throws IOException, InterruptedException {
        Objects.requireNonNull(selected, "selected");
        if (!isMac()) {
            return;
        }
        final Path publicHome = registeredPublicHome();
        if (publicHome == null || !symbolicLink(publicHome)) {
            return;
        }
        final Path contents = Objects.requireNonNull(publicHome.getParent(), "public JDK contents directory");
        Files2.writeString(contents.resolve("Info.plist"), macBundleInfo(selected));
    }

    /**
     * Returns the root containing the stable current link.
     *
     * @return normalized facade store root
     */
    public Path root() {
        return root;
    }

    private JdkFacadeGenerator.Result facade(final Path facadeHome, final JdkInventory.Entry selected)
        throws IOException, InterruptedException {
        if (!Files.exists(facadeHome)) {
            return generator.generate(facadeHome, selected.candidate(), root);
        }
        final Path metadata = facadeHome.resolve("javan-backend.txt");
        if (!Files.isRegularFile(metadata) || !Files.readString(metadata).contains("backendHome=" + selected.candidate().home())) {
            throw new IOException("Facade path is not owned by the selected backend: " + facadeHome);
        }
        generator.refreshLaunchers(facadeHome, selected.candidate(), root);
        return new JdkFacadeGenerator.Result(facadeHome, selected.candidate().home(), selected.candidate().javacExecutable());
    }

    private void replaceCurrentLink(final Path current, final Path facadeHome) throws IOException, InterruptedException {
        if (isWindows()) {
            replaceWindowsCurrentLink(current, facadeHome);
            return;
        }
        if (Files.exists(current)) {
            final ProcessRunner.Result inspected = processRunner.run(root, List.of("test", "-L", current.toString()));
            if (inspected.exitCode() != 0) {
                throw new IOException("Facade current path is not a link: " + current);
            }
        }
        final Path target = root.relativize(facadeHome);
        requireSuccess(processRunner.run(root, List.of("ln", "-sfn", target.toString(), current.toString())), "switch", current);
    }

    private void replaceWindowsCurrentLink(final Path current, final Path facadeHome) throws IOException, InterruptedException {
        if (Files.exists(current)) {
            final Path metadata = current.resolve("javan-backend.txt");
            if (!Files.isRegularFile(metadata) || !Files.readString(metadata).contains("facadeRoot=" + root)) {
                throw new IOException("Facade current path is not owned by Javan: " + current);
            }
            requireSuccess(
                processRunner.run(root, List.of("cmd", "/d", "/s", "/c", "rmdir " + windowsQuote(current))),
                "replace",
                current
            );
        }
        requireSuccess(
            processRunner.run(root, List.of("cmd", "/d", "/s", "/c", "mklink /J " + windowsQuote(current) + " " + windowsQuote(facadeHome))),
            "switch",
            current
        );
    }

    private Path registeredPublicHome() throws IOException {
        final Path metadata = root.resolve("javan-public-home.txt");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        final String prefix = "publicHome=";
        final String content = Files.readString(metadata);
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.slice(content, start, index);
                if (line.startsWith(prefix)) {
                    return normalize(Path.of(Strings2.slice(line, prefix.length(), line.length())), "publicHome");
                }
                start = index + 1;
            }
        }
        return null;
    }

    private boolean symbolicLink(final Path path) throws IOException, InterruptedException {
        final Path parent = path.getParent();
        if (parent == null) {
            return false;
        }
        return processRunner.run(parent, List.of("test", "-L", path.toString())).exitCode() == 0;
    }

    private static String macBundleInfo(final JdkInventory.Entry selected) {
        final String version = xml(selected.version());
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
            + "<plist version=\"1.0\">\n"
            + "<dict>\n"
            + "  <key>CFBundleIdentifier</key><string>org.nanonative.javan.jdk</string>\n"
            + "  <key>CFBundleExecutable</key><string>libjli.dylib</string>\n"
            + "  <key>CFBundleGetInfoString</key><string>Javan JDK facade " + version + "</string>\n"
            + "  <key>CFBundleName</key><string>Javan</string>\n"
            + "  <key>CFBundlePackageType</key><string>BNDL</string>\n"
            + "  <key>CFBundleShortVersionString</key><string>" + version + "</string>\n"
            + "  <key>CFBundleVersion</key><string>1</string>\n"
            + "  <key>JavaVM</key>\n"
            + "  <dict>\n"
            + "    <key>JVMCapabilities</key><array><string>CommandLine</string></array>\n"
            + "    <key>JVMPlatformVersion</key><string>" + version + "</string>\n"
            + "    <key>JVMVendor</key><string>Javan</string>\n"
            + "    <key>JVMVersion</key><string>" + version + "</string>\n"
            + "  </dict>\n"
            + "</dict>\n"
            + "</plist>\n";
    }

    private static String xml(final String value) {
        final StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '&') {
                escaped.append("&amp;");
            } else if (character == '<') {
                escaped.append("&lt;");
            } else if (character == '>') {
                escaped.append("&gt;");
            } else if (character == '\"') {
                escaped.append("&quot;");
            } else if (character == '\'') {
                escaped.append("&apos;");
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }

    private static String facadeId(final JdkInventory.Entry selected) {
        return slug(selected.vendor()) + "-" + slug(selected.version()) + "-" + pathToken(selected.candidate().home().toString());
    }

    private static String slug(final String value) {
        final StringBuilder result = new StringBuilder();
        boolean previousDash = false;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (asciiLetterOrDigit(character)) {
                result.append(asciiLower(character));
                previousDash = false;
            } else if (!previousDash && result.length() > 0) {
                result.append('-');
                previousDash = true;
            }
        }
        if (result.length() > 0 && result.charAt(result.length() - 1) == '-') {
            result.setLength(result.length() - 1);
        }
        return result.isEmpty() ? "unknown" : result.toString();
    }

    private static String pathToken(final String value) {
        int hash = 0x811c9dc5;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x01000193;
        }
        final StringBuilder result = new StringBuilder(8);
        for (int shift = 28; shift >= 0; shift -= 4) {
            result.append(hex((hash >>> shift) & 15));
        }
        return result.toString();
    }

    private static char hex(final int value) {
        return (char) (value < 10 ? '0' + value : 'a' + (value - 10));
    }

    private static boolean asciiLetterOrDigit(final char value) {
        return (value >= '0' && value <= '9') || (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean isWindows() {
        return Strings2.toAsciiLowerCase(System.getProperty("os.name", "")).contains("win");
    }

    private boolean isMac() {
        final String normalized = Strings2.toAsciiLowerCase(osName);
        return normalized.contains("mac") || normalized.contains("darwin");
    }

    private static String windowsQuote(final Path path) {
        return "\"" + path + "\"";
    }

    private static char asciiLower(final char value) {
        if (value >= 'A' && value <= 'Z') {
            return (char) (value + ('a' - 'A'));
        }
        return value;
    }

    private static void requireSuccess(final ProcessRunner.Result result, final String action, final Path path) throws IOException {
        if (result.exitCode() != 0) {
            final String detail = Strings2.isBlank(result.stderr()) ? result.stdout() : result.stderr();
            throw new IOException("Unable to " + action + " facade link " + path + ": " + detail);
        }
    }

    private static Path normalize(final Path path, final String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    /**
     * One completed facade activation.
     *
     * @param current stable current link
     * @param facade selected JDK facade
     */
    public record Activation(Path current, JdkFacadeGenerator.Result facade) {
        /**
         * Creates normalized activation paths.
         */
        public Activation {
            current = normalize(current, "current");
            facade = Objects.requireNonNull(facade, "facade");
        }
    }
}
