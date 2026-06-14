package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final String value = source();
        final String result = value.replace('-', '/');
        pressure();
        System.out.println(result);
    }

    private static String source() {
        final StringBuilder builder = new StringBuilder();
        builder.append("com-acme-Main");
        return builder.toString();
    }

    private static void pressure() {
        int index = 0;
        while (index < 48) {
            final String value = ("dead-" + index).replace('-', '_');
            if (value.length() == -1) {
                System.out.println(value);
            }
            index = index + 1;
        }
    }
}
