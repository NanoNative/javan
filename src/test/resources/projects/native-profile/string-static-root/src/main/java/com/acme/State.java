package com.acme;

public final class State {
    static final String ROOT = "r00t-" + value();

    private State() {
    }

    private static int value() {
        return 7;
    }
}
