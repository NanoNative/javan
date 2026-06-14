package com.acme;

public final class PointOps {
    private PointOps() {
    }

    public static int weighted(final Point point, final int factor) {
        return point.score(factor);
    }
}
