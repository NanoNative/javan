package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Flag flag = new Flag(true);
        System.out.println(flag.value());
        System.out.println(invert(flag.value()));
    }

    public static boolean invert(final boolean value) {
        if (value) {
            return false;
        }
        return true;
    }
}
