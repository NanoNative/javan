package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(reject("unsupported"));
    }

    private static native int reject(String value);
}
