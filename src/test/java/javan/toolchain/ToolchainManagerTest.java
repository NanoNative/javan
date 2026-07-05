package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Isolated
final class ToolchainManagerTest {
    @TempDir
    private Path tempDir;

    @Test
    void doctorReportsJavanHome() {
        final Path home = tempDir.resolve("home");
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("javan home:      " + home.toAbsolutePath().normalize());
    }

    @Test
    void doctorReportsJavaHome() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("java.home:       " + System.getProperty("java.home"));
    }

    @Test
    void doctorReportsJavaVersion() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("java.version:    " + System.getProperty("java.version"));
    }

    @Test
    void doctorReportsAvailableJavacPath() {
        final Path javac = Path.of("/toolchains/jdk/bin/javac");
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), executable -> {
            if ("javac".equals(executable)) {
                return new ToolchainManager.ToolStatus(executable, Optional.of(javac));
            }
            return new ToolchainManager.ToolStatus(executable);
        });

        final String report = manager.doctor();

        assertThat(report).contains("javac:           available (" + javac + ")");
    }

    @Test
    void doctorReportsAvailableFallbackCCompilerPath() {
        final Path clang = Path.of("/usr/bin/clang");
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), executable -> {
            if ("clang".equals(executable)) {
                return new ToolchainManager.ToolStatus(executable, Optional.of(clang));
            }
            return new ToolchainManager.ToolStatus(executable);
        });

        final String report = manager.doctor();

        assertThat(report).contains("c compiler:      available (" + clang + ")");
    }

    @Test
    void doctorReportsMissingCCompilerWhenNoneAreAvailable() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("c compiler:      missing (" + expectedCompilerMissingLabel() + ")");
    }

    @Test
    void doctorPrefersGccOnWindowsProbeOrder() {
        final Path gcc = Path.of("/toolchains/mingw/bin/gcc");
        final Path clang = Path.of("/toolchains/llvm/bin/clang");
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), executable -> {
            if ("gcc".equals(executable)) {
                return new ToolchainManager.ToolStatus(executable, Optional.of(gcc));
            }
            if ("clang".equals(executable)) {
                return new ToolchainManager.ToolStatus(executable, Optional.of(clang));
            }
            return new ToolchainManager.ToolStatus(executable);
        });

        final String previousOs = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 11");
        try {
            assertThat(manager.doctor()).contains("c compiler:      available (" + gcc + ")");
        } finally {
            restoreOsName(previousOs);
        }
    }

    @Test
    void windowsPathProbeResolvesExeSuffixFromPathext() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "gcc",
            ".EXE;.CMD",
            "Windows 11"
        );

        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().getParent()).isEqualTo(compiler.getParent());
        assertThat(resolved.orElseThrow().getFileName().toString().toLowerCase(java.util.Locale.ROOT))
            .isEqualTo("gcc.exe");
    }

    @Test
    void windowsPathProbeAddsLeadingDotAndNormalizesCaseFromPathext() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "gcc",
            "EXE;Cmd",
            "Windows 11"
        );

        assertThat(resolved).contains(compiler);
    }

    @Test
    void findExecutableOnPathReturnsResolvedStatusWhenExecutableExists() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        final ToolchainManager.ToolStatus status = ToolchainManager.findExecutableOnPath(
            tempDir.toString(),
            "gcc",
            ".EXE",
            "Windows 11"
        );

        assertThat(status.available()).isTrue();
        assertThat(status.path()).contains(compiler);
    }

    @Test
    void findExecutableOnPathReturnsMissingStatusWhenExecutableDoesNotExist() {
        final ToolchainManager.ToolStatus status = ToolchainManager.findExecutableOnPath(
            tempDir.toString(),
            "gcc",
            ".EXE",
            "Windows 11"
        );

        assertThat(status.available()).isFalse();
        assertThat(status.path()).isEmpty();
    }

    @Test
    void windowsPathProbeUsesDefaultExtensionsWhenPathextIsMissing() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "gcc",
            null,
            "Windows 11"
        );

        assertThat(resolved).contains(compiler);
    }

    @Test
    void windowsPathProbeUsesDefaultExtensionsWhenPathextContainsOnlyBlankEntries() throws Exception {
        final Path compiler = Files.createFile(tempDir.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "gcc",
            " ; ; ",
            "Windows 11"
        );

        assertThat(resolved).contains(compiler);
    }

    @Test
    void pathProbeReturnsEmptyWhenPathIsBlank() {
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            "",
            "gcc",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void pathProbeReturnsEmptyWhenPathContainsOnlySeparators() {
        final String separators = java.io.File.pathSeparator + java.io.File.pathSeparator;
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            separators,
            "gcc",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void pathProbeReturnsEmptyWhenExecutableIsBlank() {
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void windowsPathProbeIgnoresEmptyPathEntries() throws Exception {
        final Path nested = Files.createDirectories(tempDir.resolve("nested"));
        final Path compiler = Files.createFile(nested.resolve("gcc.exe"));
        assertThat(compiler.toFile().setExecutable(true)).isTrue();

        final String path = tempDir + java.io.File.pathSeparator + java.io.File.pathSeparator + nested;
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            path,
            "gcc",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).contains(compiler);
    }

    @Test
    void windowsPathProbeReturnsEmptyForMissingExecutableWithExplicitExtension() {
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "gcc.exe",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void windowsPathProbeReturnsEmptyForLeadingDotExecutableName() {
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            ".gcc",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void windowsPathProbeReturnsEmptyForTrailingDotExecutableName() {
        final Optional<Path> resolved = ToolchainManager.resolveExecutableOnPath(
            tempDir.toString(),
            "gcc.",
            ".EXE",
            "Windows 11"
        );

        assertThat(resolved).isEmpty();
    }

    @Test
    void indentInstalledReportHandlesCrLfAndSkipsBlankLines() {
        final String indented = ToolchainManager.indentInstalledReportForTesting("Toolchains\r\n\r\nentry\r\n");

        assertThat(indented).isEqualTo("    entry");
    }

    @Test
    void indentInstalledReportReturnsEmptyWhenNothingCanBeIndented() {
        assertThat(ToolchainManager.indentInstalledReportForTesting("Toolchains")).isEmpty();
    }

    @Test
    void normalizedProbePathUsesEmptyStringWhenInputIsNull() {
        assertThat(ToolchainManager.normalizedProbePathForTesting(null)).isEmpty();
    }

    @Test
    void pathEntriesForTestingReturnsEmptyWhenPathIsBlank() {
        assertThat(ToolchainManager.pathEntriesForTesting("")).isEmpty();
    }

    @Test
    void hasExplicitExtensionForTestingReturnsFalseForRootPath() {
        assertThat(ToolchainManager.hasExplicitExtensionForTesting(Path.of("/"))).isFalse();
    }

    @Test
    void doctorReportsMissingJavaHomeWhenSystemPropertyIsBlank() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());
        final String previousJavaHome = System.getProperty("java.home");
        System.clearProperty("java.home");
        try {
            assertThat(manager.doctor()).contains("java.home:       missing");
        } finally {
            restoreSystemProperty("java.home", previousJavaHome);
        }
    }

    @Test
    void doctorReportsMissingJavaVersionWhenSystemPropertyIsBlank() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());
        final String previousJavaVersion = System.getProperty("java.version");
        System.clearProperty("java.version");
        try {
            assertThat(manager.doctor()).contains("java.version:    missing");
        } finally {
            restoreSystemProperty("java.version", previousJavaVersion);
        }
    }

    @Test
    void doctorDoesNotReportNativeImage() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.doctor();

        assertThat(report).doesNotContain("native-image");
    }

    @Test
    void doctorDoesNotReportGraalVm() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.doctor();

        assertThat(report).doesNotContain("GraalVM");
    }

    @Test
    void doctorReportsPresentGlobalSettings() throws Exception {
        final Path home = tempDir.resolve("home");
        Files.createDirectories(home);
        Files.writeString(home.resolve("settings.toml"), "default_jdk = 25\n");
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("global settings: " + home.resolve("settings.toml").toAbsolutePath().normalize() + " (present)");
    }

    @Test
    void doctorReportsInvalidGlobalSettingsPathWhenDirectoryExists() throws Exception {
        final Path home = tempDir.resolve("home");
        Files.createDirectories(home.resolve("settings.toml"));
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("global settings: " + home.resolve("settings.toml").toAbsolutePath().normalize() + " (invalid)");
    }

    @Test
    void listReportsNoneWhenToolchainDirectoryIsMissing() throws Exception {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.listToolchains();

        assertThat(report).contains("installed: none");
    }

    @Test
    void listSortsInstalledToolchainsById() throws Exception {
        final Path home = tempDir.resolve("home");
        writeToolchain(home, "zulu-25", "25");
        writeToolchain(home, "temurin-21", "21");
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        final String report = manager.listToolchains();

        assertThat(report.indexOf("temurin-21")).isLessThan(report.indexOf("zulu-25"));
    }

    @Test
    void listReadsToolchainMetadata() throws Exception {
        final Path home = tempDir.resolve("home");
        final Path toolchain = home.resolve("toolchains/temurin-25");
        Files.createDirectories(toolchain);
        Files.writeString(toolchain.resolve("toolchain.toml"), """
            id = "temurin-25"
            kind = "jdk"
            version = "25"
            """);
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        final String report = manager.listToolchains();

        assertThat(report).contains("temurin-25 | jdk | 25 | " + toolchain.resolve("bin/javac").toAbsolutePath().normalize());
    }

    @Test
    void managerExposesDeterministicPathsAndSettings() throws Exception {
        final Path home = tempDir.resolve("home");
        Files.createDirectories(home);
        Files.writeString(home.resolve("settings.toml"), """
            default_toolchain = "temurin-25"
            default_jdk = "25"
            auto_install = true
            """);
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        assertThat(manager.javanHome()).isEqualTo(home.toAbsolutePath().normalize());
        assertThat(manager.settingsPath()).isEqualTo(home.resolve("settings.toml").toAbsolutePath().normalize());
        assertThat(manager.toolchainsPath()).isEqualTo(home.resolve("toolchains").toAbsolutePath().normalize());
        assertThat(manager.settings()).isEqualTo(new JavanSettings(Optional.of("temurin-25"), Optional.of("25"), true));
    }

    @Test
    void installedToolchainReportRendersSortedInstalledEntries() throws Exception {
        final Path home = tempDir.resolve("home");
        writeToolchain(home, "zulu-25", "25");
        writeToolchain(home, "temurin-21", "21");
        final ToolchainManager manager = new ToolchainManager(home, missingProbe());

        assertThat(manager.installedToolchainReport()).isEqualTo("""
            Toolchains
              temurin-21 | jdk | 21 | %s
              zulu-25 | jdk | 25 | %s""".formatted(
            home.resolve("toolchains/temurin-21/bin/javac").toAbsolutePath().normalize(),
            home.resolve("toolchains/zulu-25/bin/javac").toAbsolutePath().normalize()
        ));
    }

    @Test
    void processRunnerConstructorRejectsNullProcessRunner() {
        assertThatThrownBy(() -> new ToolchainManager((javan.util.ProcessRunner) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("processRunner");
    }

    @Test
    void processRunnerConstructorAcceptsNonNullProcessRunner() {
        final ToolchainManager manager = new ToolchainManager(new javan.util.ProcessRunner());

        assertThat(manager.javanHome()).isNotNull();
    }

    private static ToolchainManager.CommandProbe missingProbe() {
        return ToolchainManager.ToolStatus::new;
    }

    private static String expectedCompilerMissingLabel() {
        final String osName = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (osName.contains("win")) {
            return "gcc|clang|cc";
        }
        return "cc|clang|gcc";
    }

    private static void restoreOsName(final String value) {
        if (value == null) {
            System.clearProperty("os.name");
            return;
        }
        System.setProperty("os.name", value);
    }

    private static void restoreSystemProperty(final String name, final String value) {
        if (value == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, value);
    }

    private static Path writeToolchain(final Path home, final String id, final String version) throws Exception {
        final Path toolchain = home.resolve("toolchains").resolve(id);
        Files.createDirectories(toolchain);
        Files.writeString(toolchain.resolve("toolchain.toml"), """
            id = "%s"
            kind = "jdk"
            version = "%s"
            """.formatted(id, version));
        return toolchain;
    }
}
