package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final int[] values = new int[]{2, 3};
        values[1] = 9;
        System.out.println(values.length);
        System.out.println(values[1]);
    }
}
