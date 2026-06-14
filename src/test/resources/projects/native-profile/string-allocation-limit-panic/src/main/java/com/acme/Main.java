package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final String value = "allocation-denial-marker-allocation-denial-marker-allocation-denial-marker-" + marker();
        System.out.println(value);
    }

    private static int marker() {
        return 17;
    }
}
