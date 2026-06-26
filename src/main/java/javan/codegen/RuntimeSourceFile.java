package javan.codegen;

import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;

final class RuntimeSourceFile {
    private static final String CONTENT = new StringBuilder()
        .append(RuntimeSourceCoreSection.main())
        .append(RuntimeSourceMemorySections.heap())
        .append(RuntimeSourceMemorySections.heapAlloc())
        .append(RuntimeSourceMemorySections.arrays())
        .append(RuntimeSourceMemorySections.collections())
        .append(RuntimeSourcePlatformSection.tail())
        .append(RuntimeSourceIoSections.http())
        .append(RuntimeSourceIoSections.files())
        .toString();

    private RuntimeSourceFile() {
    }

    static Path writeTo(final Path generatedDirectory) throws IOException {
        return Files2.writeString(generatedDirectory.resolve("javan_runtime.c"), CONTENT);
    }
}
