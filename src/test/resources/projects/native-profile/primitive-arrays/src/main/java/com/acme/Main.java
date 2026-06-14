package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final boolean[] flags = new boolean[]{false, true};
        flags[0] = true;
        final byte[] bytes = new byte[]{-2, 3};
        bytes[1] = -5;
        final short[] shorts = new short[]{300, -7};
        final char[] chars = new char[]{'A', 'B'};
        chars[1] = 'C';

        System.out.println(flags[0]);
        System.out.println(flags[1]);
        System.out.println(bytes[0]);
        System.out.println(bytes[1]);
        System.out.println(shorts[0]);
        System.out.println(shorts[1]);
        System.out.println(chars[0] + 1);
        System.out.println(chars[1] + 0);
    }
}
