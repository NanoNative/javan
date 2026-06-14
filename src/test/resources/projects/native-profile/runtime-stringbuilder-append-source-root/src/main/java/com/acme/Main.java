package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final String source = source();
        final StringBuilder builder = new StringBuilder();
        builder.append(source);
        pressure();
        final String value = builder.toString();
        System.out.println(value);
        System.out.println(value.length());
    }

    private static String source() {
        final StringBuilder builder = new StringBuilder();
        builder.append("0123456789abcdefghijklmnopqrstuvwxyz");
        return builder.toString();
    }

    private static void pressure() {
        int index = 0;
        while (index < 48) {
            final String value = ("dead-" + index).trim();
            if (value.length() == -1) {
                System.out.println(value);
            }
            index = index + 1;
        }
    }
}
