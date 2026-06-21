package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int index = 0;
        while (index < 4) {
            final int[] values = new int[1024];
            values[1023] = 40 + index;
            System.out.println(values[1023]);
            index = index + 1;
        }
    }
}
