package com.acme;

public final class Node {
    public final Node next;
    private final int value;

    public Node(final int value, final Node next) {
        this.value = value;
        this.next = next;
    }

    public int value() {
        return value;
    }
}
