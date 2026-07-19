package javan.codegen;

import javan.build.ResourceBundler;
import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

final class RuntimeSourceFile {
    private static final String BASE_CONTENT = new StringBuilder()
        .append(RuntimeSourceCoreSection.main())
        .append(RuntimeSourceMemorySections.heap())
        .append(RuntimeSourceMemorySections.heapAlloc())
        .append(RuntimeSourceMemorySections.arrays())
        .append(RuntimeSourceMemorySections.collections())
        .append(RuntimeSourcePlatformSection.tail())
        .append(RuntimeSourcePlatformSection.protocol())
        .append(RuntimeSourceIoSections.http())
        .append(RuntimeSourceIoSections.files())
        .toString();

    private RuntimeSourceFile() {
    }

    static Path writeTo(final Path generatedDirectory) throws IOException {
        return writeTo(generatedDirectory, List.of());
    }

    static Path writeTo(final Path generatedDirectory, final List<ResourceBundler.ResourceFile> resources) throws IOException {
        return Files2.writeString(
            generatedDirectory.resolve("javan_runtime.c"),
            BASE_CONTENT + RuntimeSourceResourceSection.render(resources)
        );
    }
}
