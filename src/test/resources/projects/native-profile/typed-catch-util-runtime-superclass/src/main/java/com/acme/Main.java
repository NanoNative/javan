package com.acme;

import java.util.NoSuchElementException;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        try {
            throw new NoSuchElementException("empty");
        } catch (final RuntimeException exception) {
            System.out.println("runtime:" + exception.getMessage());
        }
    }
}
