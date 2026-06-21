package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ToolchainParsingTest {
    @TempDir
    private Path tempDir;

    @Test
    void simpleTomlParsesCrLfAndInlineComments() {
        assertThat(SimpleToml.parse("[defaults]\r\nauto_install = true # keep comment\r\nname = \"temurin\" # quoted\r\n"))
            .isEqualTo(Map.of("defaults.auto_install", "true", "defaults.name", "temurin"));
    }

    @Test
    void simpleTomlRejectsEmptySection() {
        assertThatThrownBy(() -> SimpleToml.parse("[]\n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Empty TOML section at line 1");
    }

    @Test
    void simpleTomlRejectsMissingEquals() {
        assertThatThrownBy(() -> SimpleToml.parse("toolchain\n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expected key = value at line 1");
    }

    @Test
    void simpleTomlRejectsMissingValue() {
        assertThatThrownBy(() -> SimpleToml.parse("toolchain =   \n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing TOML value at line 1");
    }

    @Test
    void settingsReaderPrefersPrimaryKeysAndParsesFalseBoolean() {
        final JavanSettings settings = new SettingsTomlReader().parse("""
            default_toolchain = "temurin-25"
            default_jdk = "25"
            auto_install = false
            [defaults]
            toolchain = "ignored"
            jdk = "21"
            auto_install = true
            """);

        assertThat(settings).isEqualTo(new JavanSettings(Optional.of("temurin-25"), Optional.of("25"), false));
    }

    @Test
    void settingsReaderRejectsInvalidBoolean() {
        assertThatThrownBy(() -> new SettingsTomlReader().parse("auto_install = maybe\n"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Expected boolean for auto_install: maybe");
    }

    @Test
    void toolchainKindRejectsBlankValue() {
        assertThat(ToolchainKind.parse("  ")).isEmpty();
    }

    @Test
    void toolchainKindRejectsUnknownValue() {
        assertThat(ToolchainKind.parse("runtime")).isEmpty();
    }

    @Test
    void toolchainMetadataTrimsTextFields() {
        final ToolchainMetadata metadata = new ToolchainMetadata(
            " temurin-25 ",
            ToolchainKind.JDK,
            " 25.0.1 ",
            tempDir,
            tempDir.resolve("bin/java"),
            tempDir.resolve("bin/javac"),
            Optional.of(" vendor "),
            Optional.of(" sha256:abc ")
        );

        assertThat(metadata.id()).isEqualTo("temurin-25");
        assertThat(metadata.version()).isEqualTo("25.0.1");
    }

    @Test
    void toolchainMetadataRejectsBlankId() {
        assertThatThrownBy(() -> new ToolchainMetadata(
            " ",
            ToolchainKind.JDK,
            "25",
            tempDir,
            tempDir.resolve("bin/java"),
            tempDir.resolve("bin/javac"),
            Optional.empty(),
            Optional.empty()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing toolchain metadata field: id");
    }

    @Test
    void toolchainMetadataReaderReturnsEmptyForMissingFile() throws Exception {
        assertThat(new ToolchainMetadataReader().read(tempDir.resolve("missing.toml"))).isEmpty();
    }

    @Test
    void toolchainMetadataReaderRejectsUnknownKind() {
        assertThatThrownBy(() -> new ToolchainMetadataReader().parse(tempDir.resolve("toolchain.toml"), """
            id = "temurin-25"
            kind = "runtime"
            version = "25"
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown toolchain kind: runtime");
    }

    @Test
    void toolchainMetadataReaderRejectsMissingRequiredField() {
        assertThatThrownBy(() -> new ToolchainMetadataReader().parse(tempDir.resolve("toolchain.toml"), """
            kind = "jdk"
            version = "25"
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Missing toolchain metadata field: id");
    }

    @Test
    void toolchainMetadataReaderKeepsAbsoluteHome() {
        final Path absoluteHome = tempDir.resolve("jdk-home").toAbsolutePath().normalize();

        final ToolchainMetadata metadata = new ToolchainMetadataReader().parse(tempDir.resolve("toolchain.toml"), """
            id = "temurin-25"
            kind = "jdk"
            version = "25"
            home = "%s"
            """.formatted(absoluteHome));

        assertThat(metadata.home()).isEqualTo(absoluteHome);
    }

    @Test
    void toolchainListRendererRendersEmptyList() {
        assertThat(new ToolchainListRenderer().render(List.of())).isEqualTo("Toolchains\n  (none)");
    }

    @Test
    void toolchainRegistryIgnoresNonDirectoriesAndMissingMetadata() throws Exception {
        final Path home = tempDir.resolve("home");
        final Path toolchains = home.resolve("toolchains");
        Files.createDirectories(toolchains.resolve("zulu-25"));
        Files.writeString(toolchains.resolve("README.txt"), "ignore");

        assertThat(new ToolchainRegistry(home).installed()).isEmpty();
    }

    @Test
    void toolchainMetadataExceptionKeepsMessageAndCause() {
        final IllegalArgumentException cause = new IllegalArgumentException("broken");
        final ToolchainMetadataException exception = new ToolchainMetadataException("invalid metadata", cause);

        assertThat(exception).hasMessage("invalid metadata").hasCause(cause);
    }
}
