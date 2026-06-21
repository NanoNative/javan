package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        new StringBuilder().setLength(2_147_483_647);
    }
}
