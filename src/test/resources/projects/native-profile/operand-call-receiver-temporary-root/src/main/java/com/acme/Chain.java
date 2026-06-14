package com.acme;

public final class Chain {
    private final int value;
    private Chain next;

    public Chain(final int value) {
        this.value = value;
    }

    public Chain link(final Chain next) {
        this.next = next;
        return this;
    }

    public int sum() {
        int total = 0;
        Chain current = this;
        while (current != null) {
            total = total + current.value;
            current = current.next;
        }
        return total;
    }
}
