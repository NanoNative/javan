package com.acme.showcase;

public final class Metric {
    private final String name;
    private final int value;
    private final int delta;

    public Metric(final String name, final int value, final int delta) {
        this.name = name;
        this.value = value;
        this.delta = delta;
    }

    public String name() {
        return name;
    }

    public int score() {
        return Math.max(value + Math.abs(delta), 0);
    }
}
