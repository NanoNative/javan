package com.acme;

public final class Main {
    public static void main(final String[] args) {
        throw new IllegalStateException(piece("left") + "-" + piece("right"));
    }

    private static String piece(final String value) {
        final StringBuilder builder = new StringBuilder();
        builder.append(value);
        return builder.toString();
    }
}
