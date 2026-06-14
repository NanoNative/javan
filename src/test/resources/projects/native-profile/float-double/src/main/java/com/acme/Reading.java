package com.acme;

public final class Reading {
    private float ratio;
    private double total;

    public Reading(final float ratio, final double total) {
        this.ratio = ratio;
        this.total = total;
    }

    public float ratio() {
        return ratio;
    }

    public double total() {
        return total;
    }
}
