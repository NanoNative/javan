package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final String value = source();
        final String result = value.substring(1, 9);
        pressure();
        System.out.println(result);
    }

    private static String source() {
        final StringBuilder builder = new StringBuilder();
        builder.append("xjavan-rtz");
        return builder.toString();
    }

    private static void pressure() {
        int index = 0;
        while (index < 48) {
            final String value = ("dead-" + index).substring(1).trim();
            if (value.length() == -1) {
                System.out.println(value);
            }
            index = index + 1;
        }
    }
}
