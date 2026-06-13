package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Greeter greeter = new EnglishGreeter();
        System.out.println(greeter.greet());
    }
}
