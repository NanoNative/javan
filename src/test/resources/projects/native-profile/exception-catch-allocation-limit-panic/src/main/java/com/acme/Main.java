package com.acme;

public final class Main {
    public static void main(final String[] args) {
        try {
            throw new IllegalStateException("boom");
        } catch (final IllegalStateException exception) {
            final String value = "catch-allocation-denial-marker-catch-allocation-denial-marker-catch-allocation-denial-marker-" + exception.getMessage().length();
            System.out.println(value);
        }
    }
}
