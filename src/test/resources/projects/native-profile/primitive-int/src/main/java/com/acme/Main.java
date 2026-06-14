package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final int value = calculate(40000, 9);
        System.out.println(value);
    }

    public static int calculate(final int left, final int right) {
        final int sum = left + right;
        final int product = sum * 2;
        return product - 3;
    }
}
