package javan.codegen;

import javan.build.ResourceBundler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the tiny C runtime used by generated programs.
 */
public final class RuntimeFiles {
    /**
     * Writes runtime header and source files without embedded resources.
     *
     * @param generatedDirectory output directory
     * @return runtime C source path
     * @throws IOException when writing fails
     */
    public Path write(final Path generatedDirectory) throws IOException {
        return write(generatedDirectory, List.of());
    }

    /**
     * Writes runtime header and source files.
     *
     * @param generatedDirectory output directory
     * @return runtime C source path
     * @throws IOException when writing fails
     */
    public Path write(final Path generatedDirectory, final List<ResourceBundler.ResourceFile> resources) throws IOException {
        RuntimeHeaderFile.writeTo(generatedDirectory);
        return RuntimeSourceFile.writeTo(generatedDirectory, resources);
    }
}
