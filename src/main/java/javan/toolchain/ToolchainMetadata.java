package javan.toolchain;

import javan.util.Strings2;

import java.nio.file.Path;
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

    private static String requireText(final String value, final String field) {
        if (Strings2.isBlank(value)) {
            throw new IllegalArgumentException("Missing toolchain metadata field: " + field);
        }
        return Strings2.trimAscii(value);
    }
}
