package com.acme;

import java.util.Arrays;

public final class Main {
    public static void main(final String[] args) {
        final int[] values = new int[] {
            1,
            2,
            3,
            4
        };
        final int[] copy = Arrays.copyOf(values, 64);
        System.out.println(copy.length);
    }
}
