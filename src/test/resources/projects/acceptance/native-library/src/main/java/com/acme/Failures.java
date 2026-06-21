package com.acme;

public final class Failures {
    private Failures() {
    }

    public static int failInt() {
        final int[] values = new int[-1];
        return values.length;
    }
}
