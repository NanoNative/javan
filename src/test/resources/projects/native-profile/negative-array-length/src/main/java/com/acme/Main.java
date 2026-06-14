package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final int size = -1;
        final int[] values = new int[size];
        System.out.println(values.length);
    }
}
