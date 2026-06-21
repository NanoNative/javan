package com.acme;

public final class Main {
    public static void main(final String[] args) {
        int total = 0;
        int index = 0;
        while (index < 80) {
            total = total + add(make(index).child, pressureNode(index));
            index = index + 1;
        }
        System.out.println(total);
    }

    private static Holder make(final int value) {
        return new Holder(new Node(value + 1));
    }

    private static Node pressureNode(final int value) {
        int index = 0;
        while (index < 40) {
            final int[] values = new int[128];
            values[0] = value + index;
            index = index + 1;
        }
        return new Node(value + 3);
    }

    private static int add(final Node left, final Node right) {
        return left.value() + right.value();
    }
}
