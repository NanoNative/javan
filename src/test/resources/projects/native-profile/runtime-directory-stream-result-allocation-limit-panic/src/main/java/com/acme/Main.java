package com.acme;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        Files.newDirectoryStream(Path.of(".")).close();
    }
}
