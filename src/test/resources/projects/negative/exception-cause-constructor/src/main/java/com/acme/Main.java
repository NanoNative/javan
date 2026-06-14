package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        try {
            throw new IllegalStateException("outer", new RuntimeException("cause"));
        } catch (final IllegalStateException exception) {
            System.out.println(exception.getMessage());
        }
    }
}
