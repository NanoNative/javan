package com.acme;

import java.nio.file.Path;

public final class Main {
    public static void main(final String[] args) {
        final Path path = Path.of("/tmp").resolve("path-allocation-denial-marker-path-allocation-denial-marker-path-allocation-denial-marker-path-allocation-denial-marker-path-allocation-denial-marker");
        System.out.println(path.toString());
    }
}
