package com.acme;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        System.out.println(Files.readString(Path.of("src/test/resources/projects/native-profile/runtime-read-string-allocation-limit-panic/data.txt")).length());
    }
}
