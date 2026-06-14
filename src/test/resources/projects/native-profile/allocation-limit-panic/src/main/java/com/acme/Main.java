package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final byte[] values = new byte[128];
        values[0] = 1;
        System.out.println(values[0]);
    }
}
