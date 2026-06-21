package com.acme;

public final class Store {
    private static String text;
    private static byte[] bytes;

    private Store() {
    }

    public static void rememberString(final String value) {
        text = value;
    }

    public static String lastString() {
        return text;
    }

    public static void rememberBytes(final byte[] value) {
        bytes = value;
    }

    public static byte[] lastBytes() {
        return bytes;
    }

    public static void clear() {
        text = null;
        bytes = null;
    }
}
