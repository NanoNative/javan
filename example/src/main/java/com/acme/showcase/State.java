package com.acme.showcase;

public final class State {
    private static int bootCount;
    private static String mode;

    static {
        bootCount = 1;
        mode = "ready";
    }

    private State() {
    }

    public static int bootCount() {
        return bootCount;
    }

    public static String mode() {
        return mode;
    }
}
