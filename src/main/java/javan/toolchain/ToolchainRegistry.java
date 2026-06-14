package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        final List<Path> installs = new ArrayList<>();
        final DirectoryStream<Path> children = Files.newDirectoryStream(toolchains);
        for (final Path child : children) {
            if (Files.isDirectory(child)) {
                insertPath(installs, child);
            }
        }
        children.close();
        final List<ToolchainMetadata> installed = new ArrayList<>();
        for (final Path install : installs) {
            final List<ToolchainMetadata> metadata = readMetadata(install.resolve("toolchain.toml"));
            for (final ToolchainMetadata item : metadata) {
                insertMetadata(installed, item);
            }
        }
        return List.copyOf(installed);
    }

    private List<ToolchainMetadata> readMetadata(final Path metadataFile) throws IOException {
        final java.util.Optional<ToolchainMetadata> metadata = reader.read(metadataFile);
        if (metadata.isEmpty()) {
            return List.of();
        }
        return List.of(metadata.orElseThrow());
    }

    private static void insertPath(final List<Path> paths, final Path path) {
        int index = 0;
        while (index < paths.size() && comparePath(paths.get(index), path) <= 0) {
            index++;
        }
        paths.add(index, path);
    }

    private static int comparePath(final Path left, final Path right) {
        return Strings2.compareAscii(left.getFileName().toString(), right.getFileName().toString());
    }

    private static void insertMetadata(final List<ToolchainMetadata> values, final ToolchainMetadata metadata) {
        int index = 0;
        while (index < values.size() && compareMetadata(values.get(index), metadata) <= 0) {
            index++;
        }
        values.add(index, metadata);
    }

    private static int compareMetadata(final ToolchainMetadata left, final ToolchainMetadata right) {
        int result = Strings2.compareAscii(left.id(), right.id());
        if (result != 0) {
            return result;
        }
        result = Strings2.compareAscii(left.kind().value(), right.kind().value());
        if (result != 0) {
            return result;
        }
        result = Strings2.compareAscii(left.version(), right.version());
        if (result != 0) {
            return result;
        }
        return Strings2.compareAscii(left.home().toString(), right.home().toString());
    }
}
