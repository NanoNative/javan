package javan.toolchain;

import javan.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final SettingsTomlReader settingsReader;
    private final ToolchainRegistry registry;
    private final ToolchainListRenderer listRenderer;

    /**
     * Creates a toolchain manager.
     */
    public ToolchainManager() {
        this(defaultJavanHome(), new PathCommandProbe(System.getenv()));
    }

    /**
     * Creates a toolchain manager.
     *
     * @param processRunner process runner
     */
    public ToolchainManager(final ProcessRunner processRunner) {
        this(defaultJavanHome(), new ProcessRunnerCommandProbe(processRunner));
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
        final ToolStatus nativeImage = commandProbe.find("native-image");
        final ToolStatus cCompiler = commandProbe.firstAvailable(List.of("cc", "clang", "gcc"));
        final StringBuilder report = new StringBuilder();
        report.append("Toolchain").append(System.lineSeparator());
        report.append("  javan home:      ").append(javanHome).append(System.lineSeparator());
        report.append("  java.home:       ").append(systemProperty("java.home")).append(System.lineSeparator());
        report.append("  java.version:    ").append(systemProperty("java.version")).append(System.lineSeparator());
        report.append("  javac:           ").append(formatStatus(javac)).append(System.lineSeparator());
        report.append("  c compiler:      ").append(formatStatus(cCompiler)).append(System.lineSeparator());
        report.append("  native-image:    ").append(formatStatus(nativeImage)).append(System.lineSeparator());
        report.append("  global settings: ").append(settings).append(" (").append(fileStatus(settings)).append(")");
        return report.toString();
    }

    /**
     * Returns globally installed toolchains.
     *
     * @return deterministic human-readable toolchain list
     */
    public String listToolchains() {
        final StringBuilder report = new StringBuilder();
        report.append("Toolchains").append(System.lineSeparator());
        report.append("  home:      ").append(javanHome).append(System.lineSeparator());
        try {
            final List<ToolchainMetadata> entries = installedToolchains();
            if (entries.isEmpty()) {
                report.append("  installed: none");
            } else {
                report.append("  installed:").append(System.lineSeparator())
                    .append(indentInstalledReport(listRenderer.render(entries)));
            }
            return report.toString();
        } catch (final IOException | RuntimeException exception) {
            report.append("  installed: unreadable (").append(exception.getMessage()).append(")");
            return report.toString();
        }
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
            return "available " + status.path().map(path -> "(" + path + ")").orElse("(" + status.name() + ")");
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
        if (value == null || value.isBlank()) {
            return "missing";
        }
        return value;
    }

    private static String indentInstalledReport(final String report) {
        final String[] lines = report.split("\\R");
        final StringBuilder result = new StringBuilder();
        for (int index = 1; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                result.append("    ").append(lines[index]).append(System.lineSeparator());
            }
        }
        if (!result.isEmpty()) {
            result.setLength(result.length() - System.lineSeparator().length());
        }
        return result.toString();
    }

    private static Path defaultJavanHome() {
        return JavanHome.resolve();
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

        /**
         * Finds the first available command.
         *
         * @param executables executable names
         * @return command status
         */
        default ToolStatus firstAvailable(final List<String> executables) {
            return executables.stream()
                .map(this::find)
                .filter(ToolStatus::available)
                .findFirst()
                .orElseGet(() -> new ToolStatus(String.join("|", executables), Optional.empty()));
        }
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
        private final Map<String, String> environment;

        private PathCommandProbe(final Map<String, String> environment) {
            this.environment = Map.copyOf(environment);
        }

        @Override
        public ToolStatus find(final String executable) {
            return pathEntries().stream()
                .map(directory -> directory.resolve(executable))
                .filter(Files::isExecutable)
                .findFirst()
                .map(path -> new ToolStatus(executable, Optional.of(path)))
                .orElseGet(() -> new ToolStatus(executable));
        }

        private List<Path> pathEntries() {
            final String path = environment.getOrDefault("PATH", "");
            if (path.isBlank()) {
                return List.of();
            }
            return List.of(path.split(java.io.File.pathSeparator)).stream()
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();
        }
    }

    private static final class ProcessRunnerCommandProbe implements CommandProbe {
        private final ProcessRunner processRunner;

        private ProcessRunnerCommandProbe(final ProcessRunner processRunner) {
            this.processRunner = processRunner;
        }

        @Override
        public ToolStatus find(final String executable) {
            if (processRunner.commandExists(executable)) {
                return new ToolStatus(executable, Optional.of(Path.of(executable)));
            }
            return new ToolStatus(executable);
        }
    }
}
