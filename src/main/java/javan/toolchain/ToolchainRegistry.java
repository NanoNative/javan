package javan.toolchain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Reads installed toolchains from a javan home directory.
 */
public final class ToolchainRegistry {
    private final Path home;
    private final ToolchainMetadataReader reader;

    /**
     * Creates a registry for a javan home.
     *
     * @param home javan home directory
     */
    public ToolchainRegistry(final Path home) {
        this(home, new ToolchainMetadataReader());
    }

    /**
     * Creates a registry for a javan home.
     *
     * @param home javan home directory
     * @param reader metadata reader
     */
    public ToolchainRegistry(final Path home, final ToolchainMetadataReader reader) {
        this.home = Objects.requireNonNull(home, "home").toAbsolutePath().normalize();
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    /**
     * Lists installed toolchains in deterministic order.
     *
     * @return installed toolchains
     * @throws IOException when metadata cannot be read
     */
    public List<ToolchainMetadata> installed() throws IOException {
        final Path toolchains = home.resolve("toolchains");
        if (!Files.isDirectory(toolchains)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(toolchains)) {
            return children
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(path -> path.resolve("toolchain.toml"))
                .map(this::readMetadata)
                .flatMap(List::stream)
                .sorted(Comparator
                    .comparing(ToolchainMetadata::id)
                    .thenComparing(metadata -> metadata.kind().value())
                    .thenComparing(ToolchainMetadata::version)
                    .thenComparing(metadata -> metadata.home().toString()))
                .toList();
        }
    }

    private List<ToolchainMetadata> readMetadata(final Path metadataFile) {
        try {
            return reader.read(metadataFile).stream().toList();
        } catch (final IOException exception) {
            throw new ToolchainMetadataException("Unable to read toolchain metadata: " + metadataFile, exception);
        }
    }
}
