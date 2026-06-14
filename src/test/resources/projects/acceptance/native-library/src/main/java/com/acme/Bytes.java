package com.acme;

public final class Bytes {
    private Bytes() {
    }

    public static byte[] duplicate(final byte[] data) {
        final byte[] result = new byte[data.length];
        int index = 0;
        while (index < data.length) {
            result[index] = data[index];
            index = index + 1;
        }
        return result;
    }
}
