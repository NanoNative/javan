package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

final class ToolchainFoundationTest {
    @TempDir
    private Path tempDir;

    @Test
    void defaultHomeUsesDotJavanBelowUserHome() {
        final Path userHome = tempDir.resolve("home");

        final Path resolved = JavanHome.resolve(Map.of(), new Properties(), userHome);

        assertThat(resolved).isEqualTo(userHome.resolve(".javan").toAbsolutePath().normalize());
    }

    @Test
    void systemPropertyOverrideSelectsConfiguredHome() {
        final Path configured = tempDir.resolve("configured");
        final Properties properties = new Properties();
        properties.setProperty(JavanHome.PROPERTY, configured.toString());

        final Path resolved = JavanHome.resolve(Map.of(), properties, tempDir.resolve("home"));

        assertThat(resolved).isEqualTo(configured.toAbsolutePath().normalize());
    }

    @Test
    void environmentOverrideSelectsConfiguredHome() {
        final Path configured = tempDir.resolve("environment");

        final Path resolved = JavanHome.resolve(
            Map.of(JavanHome.ENVIRONMENT, configured.toString()),
            new Properties(),
            tempDir.resolve("home")
        );

        assertThat(resolved).isEqualTo(configured.toAbsolutePath().normalize());
    }

    @Test
    void missingSettingsFileUsesDefaults() throws Exception {
        final JavanSettings settings = new SettingsTomlReader().read(tempDir);

        assertThat(settings).isEqualTo(JavanSettings.defaults());
    }

    @Test
    void settingsFileParsesDefaultsTable() throws Exception {
        Files.writeString(tempDir.resolve("settings.toml"), """
            [defaults]
            toolchain = "temurin-25"
            jdk = "25"
            auto_install = true
            """);

        final JavanSettings settings = new SettingsTomlReader().read(tempDir);

        assertThat(settings).isEqualTo(new JavanSettings(
            Optional.of("temurin-25"),
            Optional.of("25"),
            true
        ));
    }

    @Test
    void emptyToolchainsDirectoryListsNoInstalledToolchains() throws Exception {
        Files.createDirectories(tempDir.resolve("toolchains"));

        final List<ToolchainMetadata> installed = new ToolchainRegistry(tempDir).installed();

        assertThat(installed).isEmpty();
    }

    @Test
    void installedToolchainMetadataIsRead() throws Exception {
        final Path install = tempDir.resolve("toolchains/temurin-25");
        Files.createDirectories(install);
        Files.writeString(install.resolve("toolchain.toml"), """
            id = "temurin-25"
            kind = "jdk"
            version = "25.0.1"
            home = "."
            java = "bin/java"
            javac = "bin/javac"
            vendor = "Eclipse Temurin"
            checksum = "sha256:abc"
            """);

        final List<ToolchainMetadata> installed = new ToolchainRegistry(tempDir).installed();

        assertThat(installed).containsExactly(new ToolchainMetadata(
            "temurin-25",
            ToolchainKind.JDK,
            "25.0.1",
            install,
            install.resolve("bin/java"),
            install.resolve("bin/javac"),
            Optional.of("Eclipse Temurin"),
            Optional.of("sha256:abc")
        ));
    }

    @Test
    void listRendererSortsToolchainsById() {
        final ToolchainMetadata beta = metadata("beta", "25");
        final ToolchainMetadata alpha = metadata("alpha", "21");

        final String rendered = new ToolchainListRenderer().render(List.of(beta, alpha));

        assertThat(rendered).isEqualTo("""
            Toolchains
              alpha | jdk | 21 | %s
              beta | jdk | 25 | %s""".formatted(alpha.javacExecutable(), beta.javacExecutable()));
    }

    private ToolchainMetadata metadata(final String id, final String version) {
        final Path home = tempDir.resolve(id);
        return new ToolchainMetadata(
            id,
            ToolchainKind.JDK,
            version,
            home,
            home.resolve("bin/java"),
            home.resolve("bin/javac"),
            Optional.empty(),
            Optional.empty()
        );
    }
}
