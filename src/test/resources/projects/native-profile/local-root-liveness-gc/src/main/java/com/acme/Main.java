package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final int[] first = new int[1024];
        first[1023] = 41;
        System.out.println(first[1023]);

        final int[] second = new int[1024];
        second[1023] = 1;
        System.out.println(second[1023]);
    }
}
