package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(args.length);
        final String joined = args[0] + ":" + args[1];
        System.out.println(joined);
        System.out.println(joined.length());
    }
}
