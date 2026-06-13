package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
    void doctorReportsMissingNativeImage() {
        final ToolchainManager manager = new ToolchainManager(tempDir.resolve("home"), missingProbe());

        final String report = manager.doctor();

        assertThat(report).contains("native-image:    missing (native-image)");
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
    void listReportsNoneWhenToolchainDirectoryIsMissing() {
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
