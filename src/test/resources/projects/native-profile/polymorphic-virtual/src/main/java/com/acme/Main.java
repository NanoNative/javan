package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Base value = new Child();
        System.out.println(value.text());
    }
}
