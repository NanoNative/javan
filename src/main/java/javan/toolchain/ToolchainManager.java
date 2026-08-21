package javan.toolchain;

import javan.util.Strings2;
import javan.toolchain.facade.JdkFacadeGenerator;
import javan.toolchain.facade.JdkFacadeStore;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports local toolchain availability for javac, native C linking, and future bundled runtimes.
 */
public final class ToolchainManager {
    private final Path javanHome;
    private final CommandProbe commandProbe;
    private final JdkInventory jdkInventory;
    private final JdkProvisioner jdkProvisioner;

    /**
     * Creates a toolchain manager.
     */
    public ToolchainManager() {
        this(defaultJavanHome(), new PathCommandProbe(System.getenv("PATH")));
    }

    /**
     * Creates a deterministic toolchain manager.
     *
     * @param javanHome user-global javan home
     * @param commandProbe command resolver
     */
    ToolchainManager(final Path javanHome, final CommandProbe commandProbe) {
        this.javanHome = Objects.requireNonNull(javanHome, "javanHome").toAbsolutePath().normalize();
        this.commandProbe = Objects.requireNonNull(commandProbe, "commandProbe");
        this.jdkInventory = new JdkInventory();
        this.jdkProvisioner = new JdkProvisioner(this.javanHome);
    }

    /**
     * Returns a concise doctor report.
     *
     * @return human-readable report
     */
    public String doctor() {
        final Path settings = settingsPath();
        final ToolStatus javac = commandProbe.find("javac");
        final ToolStatus cCompiler = firstAvailable(commandProbe, cCompilerCandidates());
        final StringBuilder report = new StringBuilder();
        report.append("Toolchain").append(System.lineSeparator());
        report.append("  javan home:      ").append(javanHome).append(System.lineSeparator());
        report.append("  java.home:       ").append(systemProperty("java.home")).append(System.lineSeparator());
        report.append("  java.version:    ").append(systemProperty("java.version")).append(System.lineSeparator());
        report.append("  javac:           ").append(formatStatus(javac)).append(System.lineSeparator());
        report.append("  c compiler:      ").append(formatStatus(cCompiler)).append(System.lineSeparator());
        report.append("  global settings: ").append(settings).append(" (").append(fileStatus(settings)).append(")");
        return report.toString();
    }

    /**
     * Returns the JDK-specific doctor report, including managed-store fallback policy.
     *
     * <p>This report is read-only. Writable directory creation happens only during a future
     * verified managed-JDK installation.</p>
     *
     * @return human-readable JDK report
     */
    public String jdkDoctor() {
        final StringBuilder report = new StringBuilder(doctor());
        report.append(System.lineSeparator()).append("Managed JDK install policy").append(System.lineSeparator());
        report.append("  order: machine, user, temporary").append(System.lineSeparator());
        for (final ManagedJdkStore.Location location : new ManagedJdkStore(javanHome).locations()) {
            report.append("  ").append(location.scope()).append(" install: ")
                .append(location.installRoot()).append(System.lineSeparator());
            report.append("  ").append(location.scope()).append(" cache:   ")
                .append(location.downloadCache());
            if (!location.persistent()) {
                report.append(" (ephemeral)");
            }
            report.append(System.lineSeparator());
        }
        report.setLength(report.length() - System.lineSeparator().length());
        return report.toString();
    }

    /**
     * Returns globally installed toolchains.
     *
     * @return deterministic human-readable toolchain list
     */
    public String listToolchains() throws IOException {
        final StringBuilder report = new StringBuilder();
        report.append("Toolchains").append(System.lineSeparator());
        report.append("  home:      ").append(javanHome).append(System.lineSeparator());
        final List<ToolchainMetadata> entries = installedToolchains();
        if (entries.isEmpty()) {
            report.append("  installed: none");
        } else {
            report.append("  installed:").append(System.lineSeparator())
                .append(indentInstalledReport(renderToolchains(entries)));
        }
        return report.toString();
    }

    /**
     * Resolves and renders the selected local JDK without downloading anything.
     *
     * @param explicitHome explicitly requested JDK home when present
     * @return deterministic human-readable JDK resolution report
     * @throws IOException when managed toolchain metadata cannot be read
     */
    public String resolveJdk(final Optional<Path> explicitHome) throws IOException {
        return renderResolution(resolveLocalJdk(explicitHome));
    }

    /**
     * Rescans every known local JDK location and renders the complete JDK inventory.
     *
     * @return discovered JDKs and active facade state
     * @throws IOException when managed toolchain metadata cannot be read
     */
    public String listJdks() throws IOException {
        final List<JdkInventory.Entry> entries = jdkInventory.inspect(resolveLocalJdk(Optional.empty()));
        return renderJdkInventory(entries, activeFacadeBackend());
    }

    /**
     * Selects an already installed JDK and switches the stable current facade link.
     *
     * @param selector feature version such as {@code 25}, or vendor/version such as {@code corretto@25}
     * @return selected JDK and switched facade path
     * @throws IOException when the selected facade cannot be created
     * @throws InterruptedException when interrupted while switching the facade link
     */
    public String useJdk(final String selector) throws IOException, InterruptedException {
        final JdkInventory.Entry entry = selectedJdk(selector);
        final JdkFacadeStore.Activation activation = new JdkFacadeStore(facadeRoot()).activate(entry);
        return "JDK selected" + System.lineSeparator()
            + "  vendor:  " + entry.vendor() + System.lineSeparator()
            + "  version: " + entry.version() + System.lineSeparator()
            + "  backend: " + entry.candidate().home() + System.lineSeparator()
            + "  facade:  " + activation.current();
    }

    /**
     * Installs a stable JDK-shaped facade with the configured default backend.
     *
     * @param launcher packaged native Javan executable
     * @return installed facade paths
     * @throws IOException when installation or JDK discovery fails
     * @throws InterruptedException when interrupted while publishing the facade
     */
    public JavanInstallation.Installation installJavan(final Path launcher) throws IOException, InterruptedException {
        return new JavanInstallation(javanHome).install(launcher, selectedJdk(settings().defaultJdkSelector()));
    }

    /**
     * Resolves local JDK candidates without downloading or mutating anything.
     *
     * @param explicitHome explicitly requested JDK home when present
     * @return ordered resolution result
     * @throws IOException when managed toolchain metadata cannot be read
     */
    public JdkResolver.Resolution resolveLocalJdk(final Optional<Path> explicitHome) throws IOException {
        Objects.requireNonNull(explicitHome, "explicitHome");
        final String osName = System.getProperty("os.name", "");
        final Map<String, String> environment = toolchainEnvironment();
        final JdkResolver resolver = new JdkResolver(
            environment,
            currentJavaHome(),
            commandProbe.find("javac").path(),
            installedToolchains(),
            osName,
            new JdkLocationDiscovery(environment, userHome(), osName).homes()
        );
        return resolver.resolve(explicitHome);
    }

    /**
     * Resolves one executable from a locally available JDK.
     *
     * <p>This is the native-Javan fallback when no JVM supplies {@code java.home}. It never
     * downloads or mutates a JDK installation.</p>
     *
     * @param name JDK executable name without a platform suffix
     * @return executable path from the first usable candidate that provides it
     * @throws IOException when no locally available JDK provides the executable
     */
    public Path requiredJdkTool(final String name) throws IOException {
        if (Strings2.isBlank(name)) {
            throw new IllegalArgumentException("name");
        }
        final String tool = Strings2.trimAscii(name);
        for (final JdkResolver.Candidate candidate : resolveLocalJdk(Optional.empty()).candidates()) {
            final Path executable = jdkTool(candidate, tool);
            if (candidate.usable() && Files.isExecutable(executable)) {
                return executable;
            }
        }
        throw new IOException("No usable local JDK provides " + tool + "; run javan jdk install");
    }

    /**
     * Resolves a locally available JDK home with runtime-image metadata.
     *
     * @return verified JDK home
     * @throws IOException when no locally available verified JDK exists
     */
    public Path requiredJdkHome() throws IOException {
        for (final JdkResolver.Candidate candidate : resolveLocalJdk(Optional.empty()).candidates()) {
            if (candidate.usable() && Files.isRegularFile(candidate.home().resolve("release"))) {
                return candidate.home();
            }
        }
        throw new IOException("No usable local JDK home found; run javan jdk install");
    }

    /**
     * Creates a JDK-shaped facade over the selected local backend JDK.
     *
     * @param output generated facade home
     * @return generated facade metadata
     * @throws IOException when no JDK is resolvable or output cannot be created
     * @throws InterruptedException when interrupted while linking the facade
     */
    public JdkFacadeGenerator.Result createJdkFacade(final Path output) throws IOException, InterruptedException {
        final JdkResolver.Resolution resolution = resolveLocalJdk(Optional.empty());
        if (resolution.selected().isEmpty()) {
            throw new IOException("No usable local JDK found; run javan jdk resolve");
        }
        return new JdkFacadeGenerator().generate(output, resolution.selected().orElseThrow());
    }

    private Path facadeRoot() throws IOException {
        final String facadeRoot = System.getenv("JAVAN_FACADE_ROOT");
        if (!Strings2.isBlank(facadeRoot)) {
            return Path.of(facadeRoot).toAbsolutePath().normalize();
        }
        final Optional<Path> executable = JavanExecutable.resolve();
        if (executable.isPresent()) {
            final Path parent = executable.orElseThrow().getParent();
            if (parent != null && parent.getParent() != null) {
                final Optional<Path> installedRoot = facadeMetadataValue(parent.getParent().resolve("javan-backend.txt"), "facadeRoot=");
                if (installedRoot.isPresent()) {
                    return installedRoot.orElseThrow();
                }
            }
        }
        return javanHome.resolve("facades");
    }

    private JdkInventory.Entry selectedJdk(final String selector) throws IOException, InterruptedException {
        List<JdkInventory.Entry> entries = jdkInventory.inspect(resolveLocalJdk(Optional.empty()));
        Optional<JdkInventory.Entry> selected = jdkInventory.select(entries, selector);
        if (selected.isEmpty()) {
            jdkProvisioner.provision(selector);
            entries = jdkInventory.inspect(resolveLocalJdk(Optional.empty()));
            selected = jdkInventory.select(entries, selector);
        }
        if (selected.isEmpty()) {
            throw new IOException("Verified JDK installation completed but no usable JDK matches " + selector);
        }
        return selected.orElseThrow();
    }

    private Optional<Path> activeFacadeBackend() throws IOException {
        final Path metadata = facadeRoot().resolve("current/javan-backend.txt");
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        final String content = Files.readString(metadata);
        final String prefix = "backendHome=";
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.slice(content, start, index);
                if (line.startsWith(prefix)) {
                    return Optional.of(Path.of(Strings2.slice(line, prefix.length(), line.length())).toAbsolutePath().normalize());
                }
                start = index + 1;
            }
        }
        return Optional.empty();
    }

    private static Path jdkTool(final JdkResolver.Candidate candidate, final String name) {
        if ("java".equals(name)) {
            return candidate.javaExecutable();
        }
        if ("javac".equals(name)) {
            return candidate.javacExecutable();
        }
        final Path bin = candidate.javaExecutable().getParent();
        if (bin == null) {
            return candidate.home().resolve("bin").resolve(name);
        }
        final String executable = java.io.File.separatorChar == '\\' ? name + ".exe" : name;
        return bin.resolve(executable);
    }

    private static Optional<Path> facadeMetadataValue(final Path metadata, final String prefix) throws IOException {
        if (!Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        final String content = Files.readString(metadata);
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.slice(content, start, index);
                if (line.startsWith(prefix)) {
                    return Optional.of(Path.of(Strings2.slice(line, prefix.length(), line.length())).toAbsolutePath().normalize());
                }
                start = index + 1;
            }
        }
        return Optional.empty();
    }

    private static String renderJdkInventory(final List<JdkInventory.Entry> entries, final Optional<Path> activeBackend) {
        final StringBuilder report = new StringBuilder("JDKs").append(System.lineSeparator());
        if (entries.isEmpty()) {
            return report.append("  discovered: none").toString();
        }
        report.append("  discovered:").append(System.lineSeparator());
        for (final JdkInventory.Entry entry : entries) {
            report.append("    ")
                .append(activeBackend.filter(path -> path.equals(entry.candidate().home())).isPresent() ? "active" : status(entry))
                .append("  ")
                .append(entry.vendor()).append(" ").append(entry.version())
                .append(" (Java ").append(entry.featureVersion()).append(")")
                .append("  ").append(entry.candidate().origin())
                .append("  ").append(entry.candidate().home())
                .append(System.lineSeparator());
        }
        report.setLength(report.length() - System.lineSeparator().length());
        return report.toString();
    }

    private static String status(final JdkInventory.Entry entry) {
        if (entry.facadeReady()) {
            return "available";
        }
        if (entry.candidate().usable()) {
            return "delegatable";
        }
        return "rejected";
    }

    Path javanHome() {
        return javanHome;
    }

    JavanSettings settings() throws IOException {
        return JavanSettings.read(javanHome);
    }

    List<ToolchainMetadata> installedToolchains() throws IOException {
        final Path toolchains = toolchainsPath();
        if (!Files.isDirectory(toolchains)) {
            return List.of();
        }
        final List<Path> installs = new ArrayList<>();
        final DirectoryStream<Path> children = Files.newDirectoryStream(toolchains);
        for (final Path child : children) {
            if (Files.isDirectory(child)) {
                insertPath(installs, child);
            }
        }
        children.close();
        final List<ToolchainMetadata> installed = new ArrayList<>();
        for (final Path install : installs) {
            final Optional<ToolchainMetadata> metadata = ToolchainMetadata.read(install.resolve("toolchain.toml"));
            if (metadata.isPresent()) {
                insertMetadata(installed, metadata.orElseThrow());
            }
        }
        return List.copyOf(installed);
    }

    private static void insertPath(final List<Path> paths, final Path path) {
        int index = 0;
        while (index < paths.size()
            && Strings2.compareAscii(paths.get(index).getFileName().toString(), path.getFileName().toString()) <= 0) {
            index++;
        }
        paths.add(index, path);
    }

    private static void insertMetadata(final List<ToolchainMetadata> values, final ToolchainMetadata metadata) {
        int index = 0;
        while (index < values.size() && compareMetadata(values.get(index), metadata) <= 0) {
            index++;
        }
        values.add(index, metadata);
    }

    private static int compareMetadata(final ToolchainMetadata left, final ToolchainMetadata right) {
        int result = Strings2.compareAscii(left.id(), right.id());
        if (result == 0) {
            result = Strings2.compareAscii(left.kind().value(), right.kind().value());
        }
        if (result == 0) {
            result = Strings2.compareAscii(left.version(), right.version());
        }
        return result == 0 ? Strings2.compareAscii(left.home().toString(), right.home().toString()) : result;
    }

    private static String renderToolchains(final List<ToolchainMetadata> toolchains) {
        final StringBuilder report = new StringBuilder("Toolchains").append(System.lineSeparator());
        if (toolchains.isEmpty()) {
            return report.append("  (none)").toString();
        }
        for (final ToolchainMetadata metadata : toolchains) {
            report.append("  ")
                .append(metadata.id()).append(" | ")
                .append(metadata.kind().value()).append(" | ")
                .append(metadata.version()).append(" | ")
                .append(metadata.javacExecutable()).append(System.lineSeparator());
        }
        return Strings2.stripTrailingAscii(report.toString());
    }

    Path settingsPath() {
        return javanHome.resolve("settings.toml");
    }

    Path toolchainsPath() {
        return javanHome.resolve("toolchains");
    }

    private static String formatStatus(final ToolStatus status) {
        if (status.available()) {
            final Optional<Path> path = status.path();
            if (path.isPresent()) {
                return "available (" + path.orElseThrow().toString() + ")";
            }
            return "available (" + status.name() + ")";
        }
        return "missing (" + status.name() + ")";
    }

    private static String fileStatus(final Path path) {
        if (Files.isRegularFile(path)) {
            return "present";
        }
        if (Files.exists(path)) {
            return "invalid";
        }
        return "missing";
    }

    private static String systemProperty(final String name) {
        final String value = System.getProperty(name);
        if (Strings2.isBlank(value)) {
            return "missing";
        }
        return value;
    }

    private static Optional<Path> currentJavaHome() {
        final String value = System.getProperty("java.home");
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(Path.of(value));
    }

    private static Path userHome() {
        return Path.of(System.getProperty("user.home", ""));
    }

    private static Map<String, String> toolchainEnvironment() {
        final Map<String, String> result = new HashMap<>();
        addEnvironmentValue(result, "JAVA_HOME");
        addEnvironmentValue(result, "JDK_HOME");
        addEnvironmentValue(result, "ProgramFiles");
        addEnvironmentValue(result, "ProgramW6432");
        addEnvironmentValue(result, "ProgramFiles(x86)");
        return result;
    }

    private static void addEnvironmentValue(final Map<String, String> target, final String name) {
        final String value = System.getenv(name);
        if (!Strings2.isBlank(value)) {
            target.put(name, value);
        }
    }

    private static String renderResolution(final JdkResolver.Resolution resolution) {
        final StringBuilder report = new StringBuilder();
        report.append("JDK resolution").append(System.lineSeparator());
        if (resolution.selected().isEmpty()) {
            report.append("  selected: none").append(System.lineSeparator());
        } else {
            final JdkResolver.Candidate selected = resolution.selected().orElseThrow();
            report.append("  selected: ").append(selected.origin()).append(System.lineSeparator());
            report.append("  home:     ").append(selected.home()).append(System.lineSeparator());
            report.append("  java:     ").append(selected.javaExecutable()).append(System.lineSeparator());
            report.append("  javac:    ").append(selected.javacExecutable()).append(System.lineSeparator());
        }
        appendCandidates(report, resolution);
        return report.toString();
    }

    private static void appendCandidates(final StringBuilder report, final JdkResolver.Resolution resolution) {
        if (resolution.candidates().isEmpty()) {
            report.append("  candidates: none");
            return;
        }
        report.append("  candidates:").append(System.lineSeparator());
        for (final JdkResolver.Candidate candidate : resolution.candidates()) {
            report.append("    ").append(candidateStatus(candidate, resolution.selected())).append("  ")
                .append(candidate.origin()).append("  ").append(candidate.home())
                .append("  (").append(candidate.reason()).append(')').append(System.lineSeparator());
        }
        report.setLength(report.length() - System.lineSeparator().length());
    }

    private static String candidateStatus(
        final JdkResolver.Candidate candidate,
        final Optional<JdkResolver.Candidate> selected
    ) {
        if (isSelected(candidate, selected)) {
            return "selected";
        }
        if (candidate.usable()) {
            return "available";
        }
        return "rejected";
    }

    private static boolean isSelected(
        final JdkResolver.Candidate candidate,
        final Optional<JdkResolver.Candidate> selected
    ) {
        if (selected.isEmpty()) {
            return false;
        }
        final JdkResolver.Candidate value = selected.orElseThrow();
        return candidate.origin().equals(value.origin())
            && candidate.home().equals(value.home())
            && candidate.javacExecutable().equals(value.javacExecutable());
    }

    private static ToolStatus firstAvailable(final CommandProbe probe, final List<String> executables) {
        for (final String executable : executables) {
            final ToolStatus status = probe.find(executable);
            if (status.available()) {
                return status;
            }
        }
        return new ToolStatus(joinExecutableNames(executables), Optional.empty());
    }

    private static String joinExecutableNames(final List<String> executables) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < executables.size(); index++) {
            if (index > 0) {
                result.append('|');
            }
            result.append(executables.get(index));
        }
        return result.toString();
    }

    private static String indentInstalledReport(final String report) {
        final StringBuilder result = new StringBuilder();
        int line = 0;
        int start = 0;
        for (int index = 0; index <= report.length(); index++) {
            if (index == report.length() || report.charAt(index) == '\n') {
                int end = index;
                if (end > start && report.charAt(end - 1) == '\r') {
                    end--;
                }
                final String text = Strings2.slice(report, start, end);
                if (line > 0 && !Strings2.isBlank(text)) {
                    result.append("    ").append(text).append(System.lineSeparator());
                }
                line++;
                start = index + 1;
            }
        }
        if (result.length() > 0) {
            result.setLength(result.length() - System.lineSeparator().length());
        }
        return result.toString();
    }

    private static Path defaultJavanHome() {
        return JavanHome.resolve();
    }

    private static List<String> cCompilerCandidates() {
        if (Strings2.toAsciiLowerCase(System.getProperty("os.name", "")).contains("win")) {
            return List.of("gcc", "clang", "cc");
        }
        return List.of("cc", "clang", "gcc");
    }

    static Optional<Path> resolveExecutableOnPath(
        final String path,
        final String executable,
        final String pathExt,
        final String osName
    ) {
        return PathCommandProbe.resolveExecutableOnPath(path, executable, pathExt, osName);
    }

    static ToolStatus findExecutableOnPath(
        final String path,
        final String executable,
        final String pathExt,
        final String osName
    ) {
        final Optional<Path> resolved = resolveExecutableOnPath(path, executable, pathExt, osName);
        if (resolved.isPresent()) {
            return new ToolStatus(executable, resolved);
        }
        return new ToolStatus(executable);
    }

    static String normalizedProbePathForTesting(final String path) {
        return new PathCommandProbe(path).path;
    }

    static List<Path> pathEntriesForTesting(final String path) {
        return PathCommandProbe.pathEntries(path);
    }

    static boolean hasExplicitExtensionForTesting(final Path candidate) {
        return PathCommandProbe.hasExplicitExtension(candidate);
    }

    /**
     * Resolves executable availability.
     */
    @FunctionalInterface
    interface CommandProbe {
        /**
         * Finds a command.
         *
         * @param executable executable name
         * @return command status
         */
        ToolStatus find(String executable);
    }

    /**
     * Executable status.
     *
     * @param name requested command name
     * @param path resolved executable path
     */
    record ToolStatus(String name, Optional<Path> path) {
        /**
         * Creates a status.
         */
        public ToolStatus {
            Objects.requireNonNull(name, "name");
            path = Objects.requireNonNull(path, "path");
        }

        /**
         * Creates an unavailable status.
         *
         * @param name command name
         */
        public ToolStatus(final String name) {
            this(name, Optional.empty());
        }

        /**
         * Returns true when the executable is available.
         *
         * @return true when resolved
         */
        public boolean available() {
            return path.isPresent();
        }
    }

    private static final class PathCommandProbe implements CommandProbe {
        private final String path;

        private PathCommandProbe(final String path) {
            if (path == null) {
                this.path = "";
                return;
            }
            this.path = path;
        }

        @Override
        public ToolStatus find(final String executable) {
            return findExecutableOnPath(
                path,
                executable,
                System.getenv("PATHEXT"),
                System.getProperty("os.name", "")
            );
        }

        private static Optional<Path> resolveExecutableOnPath(
            final String path,
            final String executable,
            final String pathExt,
            final String osName
        ) {
            if (Strings2.isBlank(path) || Strings2.isBlank(executable)) {
                return Optional.empty();
            }
            final List<Path> entries = pathEntries(path);
            for (final Path directory : entries) {
                final Optional<Path> candidate = resolveExecutable(directory.resolve(executable), pathExt, osName);
                if (candidate.isPresent()) {
                    return candidate;
                }
            }
            return Optional.empty();
        }

        private static Optional<Path> resolveExecutable(final Path candidate, final String pathExt, final String osName) {
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
            if (!isWindowsHost(osName) || hasExplicitExtension(candidate)) {
                return Optional.empty();
            }
            for (final String extension : windowsExecutableExtensions(pathExt)) {
                final Path extended = appendExtension(candidate, extension);
                if (Files.isExecutable(extended)) {
                    return Optional.of(extended);
                }
            }
            return Optional.empty();
        }

        private static Path appendExtension(final Path candidate, final String extension) {
            final StringBuilder value = new StringBuilder();
            value.append(candidate);
            value.append(extension);
            return Path.of(value.toString());
        }

        private List<Path> pathEntries() {
            return pathEntries(path);
        }

        private static List<Path> pathEntries(final String path) {
            if (Strings2.isBlank(path)) {
                return List.of();
            }
            final List<Path> result = new ArrayList<>();
            int start = 0;
            for (int index = 0; index <= path.length(); index++) {
                if (index == path.length() || path.charAt(index) == java.io.File.pathSeparatorChar) {
                    addPathEntry(result, path, start, index);
                    start = index + 1;
                }
            }
            return List.copyOf(result);
        }

        private static void addPathEntry(final List<Path> result, final String path, final int start, final int end) {
            if (start >= end) {
                return;
            }
            final StringBuilder entry = new StringBuilder();
            for (int index = start; index < end; index++) {
                entry.append(path.charAt(index));
            }
            if (!entry.isEmpty()) {
                result.add(Path.of(entry.toString()));
            }
        }

        private static boolean isWindowsHost(final String osName) {
            return Strings2.toAsciiLowerCase(osName).contains("win");
        }

        private static boolean hasExplicitExtension(final Path candidate) {
            final Path fileName = candidate.getFileName();
            if (fileName == null) {
                return false;
            }
            final String name = fileName.toString();
            final int index = name.lastIndexOf('.');
            return index > 0 && index < name.length() - 1;
        }

        private static List<String> windowsExecutableExtensions(final String pathExt) {
            if (Strings2.isBlank(pathExt)) {
                return List.of(".exe", ".cmd", ".bat", ".com");
            }
            final List<String> result = new ArrayList<>();
            int start = 0;
            for (int index = 0; index <= pathExt.length(); index++) {
                if (index == pathExt.length() || pathExt.charAt(index) == ';') {
                    final String extension = Strings2.toAsciiLowerCase(Strings2.slice(pathExt, start, index).trim());
                    if (!Strings2.isBlank(extension)) {
                        result.add(dotPrefixedExtension(extension));
                    }
                    start = index + 1;
                }
            }
            if (result.isEmpty()) {
                return List.of(".exe", ".cmd", ".bat", ".com");
            }
            return List.copyOf(result);
        }

        private static String dotPrefixedExtension(final String extension) {
            if (extension.startsWith(".")) {
                return extension;
            }
            final StringBuilder value = new StringBuilder();
            value.append('.');
            value.append(extension);
            return value.toString();
        }
    }
}
