package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Reading reading = new Reading(1.25f, 2.5);
        final float[] ratios = new float[]{reading.ratio(), 3.75f};
        final double[] totals = new double[]{reading.total(), 4.5};
        System.out.println(scale(ratios[0], ratios[1]));
        System.out.println(measure(totals[0], totals[1]));
        System.out.println(score(ratios[1], ratios[0]));
    }

    public static float scale(final float left, final float right) {
        return left + right;
    }

    public static double measure(final double left, final double right) {
        return left + right;
    }

    public static int score(final float left, final float right) {
        if (left > right) {
            return 1;
        }
        return 0;
    }
}
