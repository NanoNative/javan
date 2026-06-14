package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final String[] values = new String[1];
        values[0] = "left";
        final String[] copy = values.clone();
        values[0] = "right";
        System.out.println(copy[0]);
    }
}
