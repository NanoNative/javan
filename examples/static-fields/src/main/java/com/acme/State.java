package com.acme;

public final class State {
    static int count;
    static long total;
    static String label;

    static {
        count = 41;
        count = count + 1;
        total = 80L + 4L;
        label = "ready";
    }

    private State() {
    }
}
