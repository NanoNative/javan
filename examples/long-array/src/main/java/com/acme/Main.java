package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final long[] values = new long[]{1L, 2L};
        values[1] = 9L;
        System.out.println(values.length);
        System.out.println(values[0]);
        System.out.println(values[1]);
    }
}
