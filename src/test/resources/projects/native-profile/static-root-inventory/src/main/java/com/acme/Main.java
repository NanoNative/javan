package com.acme;

public final class Main {
    private static final Holder ROOT = new Holder(73);

    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(ROOT.value);
    }
}
