package com.acme;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final Path path = Path.of("src/test/resources/projects/native-profile/runtime-filetime-gc/data.txt");
        int checksum = 0;
        int index = 0;
        while (index < 1000) {
            if (Files.getLastModifiedTime(path).toMillis() >= 0) {
                checksum = checksum + 1;
            }
            index = index + 1;
        }
        System.out.println(checksum);
    }
}
