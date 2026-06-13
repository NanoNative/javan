package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println("value " + args.length);
        System.out.println("long " + 42L);
        System.out.println("float " + 1.25f);
        System.out.println("double " + 2.5);
        System.out.println("bool " + true);
        System.out.println("char " + 'A');
    }
}
