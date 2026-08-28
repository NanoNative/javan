package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final byte[] values = {3, 4, 5};
        System.out.println(adjust(7) + ":" + mutate(values) + ":" + values[0] + ":" + values[1] + ":" + values[2]);
    }

    private static native int adjust(int value);

    private static native int mutate(byte[] values);

    private static native int unreachableInvalid(String value);
}
