package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(mask(0b1110, 0b1011));
    }

    public static int mask(final int left, final int right) {
        return left & right;
    }
}
