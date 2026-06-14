package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final int[] values = new int[1];
        values[0] = 4;
        final int[] copy = values.clone();
        values[0] = 8;
        System.out.println(copy[0]);
    }
}
