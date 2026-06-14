package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final Chain chain = new Chain(11).link(pressureNode(31));
        pressure();
        System.out.println(chain.sum());
    }

    private static Chain pressureNode(final int value) {
        int index = 0;
        while (index < 40) {
            final int[] values = new int[128];
            values[0] = index;
            index = index + 1;
        }
        return new Chain(value);
    }

    private static void pressure() {
        int index = 0;
        while (index < 40) {
            final Chain chain = new Chain(index);
            if (chain.sum() == -1) {
                System.out.println(index);
            }
            index = index + 1;
        }
    }
}
