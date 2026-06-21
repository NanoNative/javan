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

    public static byte[] merge(final byte[] left, final byte[] right) {
        final byte[] result = new byte[left.length + right.length];
        int index = 0;
        while (index < left.length) {
            result[index] = left[index];
            index = index + 1;
        }
        int rightIndex = 0;
        while (rightIndex < right.length) {
            result[index] = right[rightIndex];
            index = index + 1;
            rightIndex = rightIndex + 1;
        }
        return result;
    }
}
