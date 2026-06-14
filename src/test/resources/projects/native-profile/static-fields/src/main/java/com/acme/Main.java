package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(State.count);
        System.out.println(State.total);
        System.out.println(State.label);
    }
}
