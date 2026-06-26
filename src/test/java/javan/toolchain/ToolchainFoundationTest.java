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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    void blankPropertyFallsBackToEnvironmentOverride() {
        final Path configured = tempDir.resolve("environment");
        final Properties properties = new Properties();
        properties.setProperty(JavanHome.PROPERTY, "   ");

        final Path resolved = JavanHome.resolve(
            Map.of(JavanHome.ENVIRONMENT, configured.toString()),
            properties,
            tempDir.resolve("home")
        );

        assertThat(resolved).isEqualTo(configured.toAbsolutePath().normalize());
    }

    @Test
    void blankPropertyAndEnvironmentFallBackToUserHome() {
        final Properties properties = new Properties();
        properties.setProperty(JavanHome.PROPERTY, "   ");
        final Path userHome = tempDir.resolve("home");

        final Path resolved = JavanHome.resolve(
            Map.of(JavanHome.ENVIRONMENT, "   "),
            properties,
            userHome
        );

        assertThat(resolved).isEqualTo(userHome.resolve(".javan").toAbsolutePath().normalize());
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

    @Test
    void listRendererSortsTiesByKindVersionAndHome() {
        final ToolchainMetadata jdk21 = new ToolchainMetadata(
            "temurin",
            ToolchainKind.JDK,
            "21",
            tempDir.resolve("jdk-21"),
            tempDir.resolve("jdk-21/bin/java"),
            tempDir.resolve("jdk-21/bin/javac"),
            Optional.empty(),
            Optional.empty()
        );
        final ToolchainMetadata jdk25b = new ToolchainMetadata(
            "temurin",
            ToolchainKind.JDK,
            "25",
            tempDir.resolve("jdk-25-b"),
            tempDir.resolve("jdk-25-b/bin/java"),
            tempDir.resolve("jdk-25-b/bin/javac"),
            Optional.empty(),
            Optional.empty()
        );
        final ToolchainMetadata jdk25a = new ToolchainMetadata(
            "temurin",
            ToolchainKind.JDK,
            "25",
            tempDir.resolve("jdk-25-a"),
            tempDir.resolve("jdk-25-a/bin/java"),
            tempDir.resolve("jdk-25-a/bin/javac"),
            Optional.empty(),
            Optional.empty()
        );

        final String rendered = new ToolchainListRenderer().render(List.of(jdk25b, jdk21, jdk25a));

        assertThat(rendered).isEqualTo("""
            Toolchains
              temurin | jdk | 21 | %s
              temurin | jdk | 25 | %s
              temurin | jdk | 25 | %s""".formatted(
            jdk21.javacExecutable(),
            jdk25a.javacExecutable(),
            jdk25b.javacExecutable()
        ));
    }

    @Test
    void listRendererRejectsNullInput() {
        assertThatThrownBy(() -> new ToolchainListRenderer().render(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("toolchains");
    }

    @Test
    void registrySortsDirectoriesAndMetadataDeterministically() throws Exception {
        final Path zulu = tempDir.resolve("toolchains/zulu");
        final Path alphaB = tempDir.resolve("toolchains/alpha-b");
        final Path alphaA = tempDir.resolve("toolchains/alpha-a");
        Files.createDirectories(zulu);
        Files.createDirectories(alphaB);
        Files.createDirectories(alphaA);
        Files.writeString(zulu.resolve("toolchain.toml"), """
            id = "zulu"
            kind = "jdk"
            version = "25"
            home = "."
            """);
        Files.writeString(alphaB.resolve("toolchain.toml"), """
            id = "alpha"
            kind = "jdk"
            version = "25"
            home = "."
            """);
        Files.writeString(alphaA.resolve("toolchain.toml"), """
            id = "alpha"
            kind = "jdk"
            version = "25"
            home = "."
            """);

        final List<ToolchainMetadata> installed = new ToolchainRegistry(tempDir).installed();

        assertThat(installed).extracting(ToolchainMetadata::id, metadata -> metadata.home().getFileName().toString())
            .containsExactly(
                tuple("alpha", "alpha-a"),
                tuple("alpha", "alpha-b"),
                tuple("zulu", "zulu")
            );
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
