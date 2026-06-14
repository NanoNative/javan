package com.acme;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        System.out.println(Files.readAllBytes(Path.of("src/test/resources/projects/native-profile/runtime-read-all-bytes-allocation-limit-panic/data.bin")).length);
    }
}
