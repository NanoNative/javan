package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Color selected = Color.RED;
        System.out.println(selected.name());
    }
}
