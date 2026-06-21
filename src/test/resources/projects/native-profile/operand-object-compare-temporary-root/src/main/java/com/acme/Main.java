package com.acme;

public final class Main {
    private static int marker;

    public static void main(final String[] args) {
        System.out.println(compareEqualBranch());
        System.out.println(compareNotEqualBranch());
    }

    private static int compareEqualBranch() {
        marker = 0;
        return (left(1) == right(2) ? 1000 : 10) + marker;
    }

    private static int compareNotEqualBranch() {
        marker = 0;
        return (left(3) != right(4) ? 20 : 2000) + marker;
    }

    private static Node left(final int value) {
        marker = marker == 0 ? 1 : 100;
        return new Node(value);
    }

    private static Node right(final int value) {
        marker = marker == 1 ? 2 : 100;
        final Node result = new Node(value);
        pressure(value);
        return result;
    }

    private static void pressure(final int value) {
        int index = 0;
        while (index < 40) {
            final int[] values = new int[128];
            values[0] = value + index;
            index = index + 1;
        }
    }
}
