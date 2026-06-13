package javan.toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads installed toolchain metadata files.
 */
public final class ToolchainMetadataReader {
    /**
     * Reads a toolchain metadata file.
     *
     * @param metadataFile toolchain.toml path
     * @return metadata when the file exists
     * @throws IOException when the metadata file cannot be read
     */
    public Optional<ToolchainMetadata> read(final Path metadataFile) throws IOException {
        Objects.requireNonNull(metadataFile, "metadataFile");
        if (!Files.isRegularFile(metadataFile)) {
            return Optional.empty();
        }
        return Optional.of(parse(metadataFile, Files.readString(metadataFile)));
    }

    /**
     * Parses toolchain metadata content.
     *
     * @param metadataFile metadata file path used for relative paths
     * @param content metadata content
     * @return parsed metadata
     */
    public ToolchainMetadata parse(final Path metadataFile, final String content) {
        Objects.requireNonNull(metadataFile, "metadataFile");
        Objects.requireNonNull(content, "content");
        final Map<String, String> values = SimpleToml.parse(content);
        final Path installRoot = Objects.requireNonNull(
            metadataFile.toAbsolutePath().normalize().getParent(),
            "metadataFile parent"
        );
        final Path home = resolve(installRoot, values.getOrDefault("home", "."));
        return new ToolchainMetadata(
            required(values, "id"),
            ToolchainKind.parse(required(values, "kind"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown toolchain kind: " + required(values, "kind"))),
            required(values, "version"),
            home,
            resolve(home, values.getOrDefault("java", "bin/java")),
            resolve(home, values.getOrDefault("javac", "bin/javac")),
            optional(values, "vendor"),
            optional(values, "checksum")
        );
    }

    private static String required(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing toolchain metadata field: " + key);
        }
        return value;
    }

    private static Optional<String> optional(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static Path resolve(final Path base, final String value) {
        final Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return base.resolve(path).toAbsolutePath().normalize();
    }
}
