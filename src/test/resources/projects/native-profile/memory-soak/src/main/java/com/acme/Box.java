package com.acme;

public final class Box {
    private final int value;
    private final String name;

    public Box(final int value, final String name) {
        this.value = value;
        this.name = name;
    }

    public int value() {
        return value;
    }

    public String name() {
        return name;
    }
}
