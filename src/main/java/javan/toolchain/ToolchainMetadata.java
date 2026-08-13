package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Metadata for an installed javan toolchain.
 *
 * @param id stable toolchain id
 * @param kind toolchain kind
 * @param version version text
 * @param home toolchain home directory
 * @param javaExecutable java executable path
 * @param javacExecutable javac executable path
 * @param vendor optional vendor
 * @param checksum optional checksum
 */
public record ToolchainMetadata(
    String id,
    ToolchainKind kind,
    String version,
    Path home,
    Path javaExecutable,
    Path javacExecutable,
    Optional<String> vendor,
    Optional<String> checksum
) {
    /**
     * Creates validated metadata.
     */
    public ToolchainMetadata {
        id = requireText(id, "id");
        kind = Objects.requireNonNull(kind, "kind");
        version = requireText(version, "version");
        home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable").toAbsolutePath().normalize();
        javacExecutable = Objects.requireNonNull(javacExecutable, "javacExecutable").toAbsolutePath().normalize();
        vendor = Objects.requireNonNull(vendor, "vendor");
        checksum = Objects.requireNonNull(checksum, "checksum");
    }

    static Optional<ToolchainMetadata> read(final Path metadataFile) throws IOException {
        Objects.requireNonNull(metadataFile, "metadataFile");
        return Files.isRegularFile(metadataFile)
            ? Optional.of(parse(metadataFile, Files.readString(metadataFile)))
            : Optional.empty();
    }

    static ToolchainMetadata parse(final Path metadataFile, final String content) {
        Objects.requireNonNull(metadataFile, "metadataFile");
        final Map<String, String> values = SimpleToml.parse(Objects.requireNonNull(content, "content"));
        final Path installRoot = Objects.requireNonNull(
            metadataFile.toAbsolutePath().normalize().getParent(),
            "metadataFile parent"
        );
        final Path home = resolve(installRoot, values.getOrDefault("home", "."));
        final String kindValue = required(values, "kind");
        final Optional<ToolchainKind> parsedKind = ToolchainKind.parse(kindValue);
        if (parsedKind.isEmpty()) {
            throw new IllegalArgumentException("Unknown toolchain kind: " + kindValue);
        }
        return new ToolchainMetadata(
            required(values, "id"),
            parsedKind.orElseThrow(),
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
        if (Strings2.isBlank(value)) {
            throw new IllegalArgumentException("Missing toolchain metadata field: " + key);
        }
        return value;
    }

    private static Optional<String> optional(final Map<String, String> values, final String key) {
        final String value = values.get(key);
        return Strings2.isBlank(value) ? Optional.empty() : Optional.of(value);
    }

    private static Path resolve(final Path base, final String value) {
        final Path path = Path.of(value);
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static String requireText(final String value, final String field) {
        if (Strings2.isBlank(value)) {
            throw new IllegalArgumentException("Missing toolchain metadata field: " + field);
        }
        return Strings2.trimAscii(value);
    }
}
