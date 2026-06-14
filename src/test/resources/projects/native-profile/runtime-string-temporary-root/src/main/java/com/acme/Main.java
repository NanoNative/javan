package com.acme;

public final class Main {
    public static void main(final String[] args) {
        System.out.println(join(left(), right()));
    }

    private static String left() {
        return builder("alpha123").toString().substring(0, 5);
    }

    private static String right() {
        pressure();
        return builder("beta").toString();
    }

    private static String join(final String left, final String right) {
        return left + ":" + right;
    }

    private static StringBuilder builder(final String value) {
        final StringBuilder builder = new StringBuilder();
        builder.append(value);
        return builder;
    }

    private static void pressure() {
        int index = 0;
        while (index < 40) {
            final String text = builder("pressure").append(index).toString();
            if (text.length() == -1) {
                System.out.println(text);
            }
            index = index + 1;
        }
    }
}
