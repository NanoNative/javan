package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        Class.forName((Module) null, "com.acme.Plugin");
    }
}
