package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println(max(10, 7));
        System.out.println(max(2, 9));
        printSign(-3);
        printSign(0);
        printSign(5);
    }

    public static int max(final int left, final int right) {
        if (left > right) {
            return left;
        }
        return right;
    }

    public static void printSign(final int value) {
        if (value < 0) {
            System.out.println(-1);
        } else if (value == 0) {
            System.out.println(0);
        } else {
            System.out.println(1);
        }
    }
}
