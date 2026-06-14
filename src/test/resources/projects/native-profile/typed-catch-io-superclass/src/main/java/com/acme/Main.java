package com.acme;

import java.io.FileNotFoundException;
import java.io.IOException;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        try {
            throw new FileNotFoundException("missing");
        } catch (final IOException exception) {
            System.out.println("io:" + exception.getMessage());
        }
    }
}
