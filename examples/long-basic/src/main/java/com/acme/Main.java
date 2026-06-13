package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(calculate(40L, 2L));
    }

    public static long calculate(final long left, final long right) {
        final long sum = left + right;
        return (sum * 2L) - 4L;
    }
}
