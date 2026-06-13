package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Point point = new Point(10, 5);
        System.out.println(point.sum());
        System.out.println(PointOps.weighted(point, 3));
    }
}
