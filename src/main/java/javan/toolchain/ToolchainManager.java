package javan.toolchain;

import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reports local toolchain availability for javac, native C linking, and future bundled runtimes.
 */
public final class ToolchainManager {
    private final Path javanHome;
    private final CommandProbe commandProbe;
    private final SettingsTomlReader settingsReader;
    private final ToolchainRegistry registry;
    private final ToolchainListRenderer listRenderer;

    /**
     * Creates a toolchain manager.
     */
    public ToolchainManager() {
        this(defaultJavanHome(), new PathCommandProbe(System.getenv("PATH")));
    }

    /**
     * Creates a toolchain manager.
     *
     * @param processRunner process runner
     */
    public ToolchainManager(final ProcessRunner processRunner) {
        this(defaultJavanHome(), pathCommandProbe(processRunner));
    }

    /**
     * Creates a deterministic toolchain manager.
     *
     * @param javanHome user-global javan home
     * @param commandProbe command resolver
     */
    public ToolchainManager(final Path javanHome, final CommandProbe commandProbe) {
        this.javanHome = Objects.requireNonNull(javanHome, "javanHome").toAbsolutePath().normalize();
        this.commandProbe = Objects.requireNonNull(commandProbe, "commandProbe");
        this.settingsReader = new SettingsTomlReader();
        this.registry = new ToolchainRegistry(this.javanHome);
        this.listRenderer = new ToolchainListRenderer();
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
                .append(indentInstalledReport(listRenderer.render(entries)));
        }
        return report.toString();
    }

    /**
     * Returns the user-global javan home.
     *
     * @return home path
     */
    public Path javanHome() {
        return javanHome;
    }

    /**
     * Reads global settings.
     *
     * @return settings
     * @throws IOException when settings cannot be read
     */
    public JavanSettings settings() throws IOException {
        return settingsReader.read(javanHome);
    }

    /**
     * Lists installed toolchain metadata.
     *
     * @return installed toolchains
     * @throws IOException when metadata cannot be read
     */
    public List<ToolchainMetadata> installedToolchains() throws IOException {
        return registry.installed();
    }

    /**
     * Renders installed toolchain metadata.
     *
     * @return deterministic list
     * @throws IOException when metadata cannot be read
     */
    public String installedToolchainReport() throws IOException {
        return listRenderer.render(installedToolchains());
    }

    /**
     * Returns the global settings path.
     *
     * @return settings file path
     */
    public Path settingsPath() {
        return javanHome.resolve("settings.toml");
    }

    /**
     * Returns the global toolchain install root.
     *
     * @return toolchains directory
     */
    public Path toolchainsPath() {
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

    private static CommandProbe pathCommandProbe(final ProcessRunner processRunner) {
        Objects.requireNonNull(processRunner, "processRunner");
        return new PathCommandProbe(System.getenv("PATH"));
    }

    static Optional<Path> resolveExecutableOnPath(
        final String path,
        final String executable,
        final String pathExt,
        final String osName
    ) {
        return PathCommandProbe.resolveExecutableOnPath(path, executable, pathExt, osName);
    }

    /**
     * Resolves executable availability.
     */
    @FunctionalInterface
    public interface CommandProbe {
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
    public record ToolStatus(String name, Optional<Path> path) {
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
            return resolveExecutableOnPath(path, executable, System.getenv("PATHEXT"), System.getProperty("os.name", ""))
                .map(path -> new ToolStatus(executable, Optional.of(path)))
                .orElseGet(() -> new ToolStatus(executable));
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
                final Path extended = Path.of(candidate.toString() + extension);
                if (Files.isExecutable(extended)) {
                    return Optional.of(extended);
                }
            }
            return Optional.empty();
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
                    final String extension = Strings2.slice(pathExt, start, index).trim();
                    if (!Strings2.isBlank(extension)) {
                        result.add(extension.startsWith(".") ? extension : "." + extension);
                    }
                    start = index + 1;
                }
            }
            if (result.isEmpty()) {
                return List.of(".exe", ".cmd", ".bat", ".com");
            }
            return List.copyOf(result);
        }
    }
}
