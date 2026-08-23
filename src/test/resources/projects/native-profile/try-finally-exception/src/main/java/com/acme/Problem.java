package com.acme;

public final class Problem extends RuntimeException {
    private final int code;

    public Problem(final String message, final int code) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
