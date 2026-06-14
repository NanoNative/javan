package com.acme;

public final class Main {
    public static void main(final String[] args) {
        try {
            throw new IllegalStateException("boom" + 7);
        } catch (final IllegalStateException exception) {
            pressure();
            System.out.println(exception.getMessage().length());
        }
    }

    private static void pressure() {
        int index = 0;
        while (index < 40) {
            final int[] values = new int[128];
            values[0] = index;
            index = index + 1;
        }
    }
}
