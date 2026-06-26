package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(report).contains("c compiler:      missing (cc|clang|gcc)");
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

    private static ToolchainManager.CommandProbe missingProbe() {
        return ToolchainManager.ToolStatus::new;
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
