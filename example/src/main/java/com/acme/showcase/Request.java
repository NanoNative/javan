package com.acme.showcase;

public final class Request {
    private final String name;
    private final int value;

    public Request(final String name, final int value) {
        this.name = name;
        this.value = value;
    }

    public String name() {
        return name;
    }

    public int value() {
        return value;
    }
}
