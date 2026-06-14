package com.acme;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws IOException {
        int count = 0;
        int a = 0;
        int b = 0;
        final DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("src/test/resources/projects/native-profile/runtime-directory-stream-source-root/data"));
        for (final Path entry : stream) {
            final String name = entry.getFileName().toString();
            count = count + 1;
            if ("a.txt".equals(name)) {
                a = 1;
            }
            if ("b.txt".equals(name)) {
                b = 1;
            }
        }
        System.out.println(count + ":" + a + ":" + b);
    }
}
