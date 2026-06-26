package javan.codegen;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the tiny C runtime used by generated programs.
 */
public final class RuntimeFiles {
    /**
     * Writes runtime header and source files.
     *
     * @param generatedDirectory output directory
     * @return runtime C source path
     * @throws IOException when writing fails
     */
    public Path write(final Path generatedDirectory) throws IOException {
        RuntimeHeaderFile.writeTo(generatedDirectory);
        return RuntimeSourceFile.writeTo(generatedDirectory);
    }
}
