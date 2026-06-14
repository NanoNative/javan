package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final int size = 65536;
        final int[] ints = new int[size];
        final byte[] bytes = new byte[size];
        final boolean[] flags = new boolean[8192];
        final short[] shorts = new short[8192];
        final char[] chars = new char[8192];
        final long[] longs = new long[4096];
        final float[] floats = new float[1024];
        final double[] doubles = new double[1024];
        final String[] names = new String[2048];

        ints[size - 1] = 41;
        bytes[size - 2] = 1;
        flags[8191] = true;
        shorts[8191] = 7;
        chars[8191] = 'A';
        longs[4095] = 1000L;
        floats[1023] = 1.5f;
        doubles[1023] = 2.5d;
        names[2047] = "ok";

        long checksum = 0L;
        checksum = checksum + ints[size - 1];
        checksum = checksum + bytes[size - 2];
        checksum = checksum + longs[4095];
        checksum = checksum + names[2047].length();
        checksum = checksum + ints.length / 1024;
        checksum = checksum + bytes.length / 1024;
        checksum = checksum + longs.length / 1024;
        checksum = checksum + names.length / 1024;
        if (flags[8191]) {
            checksum = checksum + 1L;
        }
        checksum = checksum + shorts[8191];
        checksum = checksum + chars[8191];
        if (floats[1023] > 1.0f) {
            checksum = checksum + 1L;
        }
        if (doubles[1023] > 2.0d) {
            checksum = checksum + 2L;
        }
        System.out.println(checksum);
    }
}
