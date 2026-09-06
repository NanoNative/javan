package com.acme;

public final class Failures {
    private Failures() {
    }

    public static int failInt() {
        final int[] values = new int[-1];
        return values.length;
    }

    public static int failRootedInt(final String text, final byte[] data) {
        final int[] values = new int[-1];
        return values.length + text.length() + data.length;
    }

    public static void failVoid(final String text) {
        final int[] values = new int[-1];
    }

    public static long failLong(final byte[] data) {
        final int[] values = new int[-1];
        return values.length;
    }

    public static float failFloat(final String text) {
        final int[] values = new int[-1];
        return values.length;
    }

    public static double failDouble(final byte[] data) {
        final int[] values = new int[-1];
        return values.length;
    }

    public static String failString() {
        final int[] values = new int[-1];
        return "unreachable";
    }

    public static byte[] failBytes() {
        return new byte[-1];
    }
}
