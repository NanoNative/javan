package com.acme;

public final class Point {
    private final int x;
    private final int y;

    public Point(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public int sum() {
        return x + y;
    }

    public int score(final int factor) {
        return sum() * factor;
    }
}
