package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        int round = 0;
        while (round < 2500) {
            final boolean[] flags = new boolean[4];
            final byte[] bytes = new byte[64];
            final short[] shorts = new short[8];
            final char[] chars = new char[8];
            final int[] ints = new int[64];
            final long[] longs = new long[16];
            final float[] floats = new float[8];
            final double[] doubles = new double[8];

            flags[3] = (round & 1) == 0;
            bytes[63] = (byte) (round & 127);
            shorts[7] = (short) (round & 255);
            chars[7] = (char) ('A' + (round & 7));
            ints[63] = round + 1;
            longs[15] = round + 2L;
            floats[7] = round + 3.0f;
            doubles[7] = round + 4.0d;

            if (flags[3]) {
                checksum = checksum + 1;
            }
            checksum = checksum + bytes[63];
            checksum = checksum + shorts[7];
            checksum = checksum + chars[7];
            checksum = checksum + ints[63];
            checksum = checksum + (int) longs[15];
            if (floats[7] > 0.0f) {
                checksum = checksum + 3;
            }
            if (doubles[7] > 0.0d) {
                checksum = checksum + 4;
            }
            round = round + 1;
        }
        System.out.println(checksum);
    }
}
