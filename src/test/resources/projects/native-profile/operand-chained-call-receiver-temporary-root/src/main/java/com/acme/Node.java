package com.acme;

public final class Node {
    private final int value;

    public Node(final int value, final Node next) {
        this.value = value;
    }

    public Node link(final Node other) {
        return new Node(value + other.value(), null);
    }

    public int value() {
        return value;
    }
}
