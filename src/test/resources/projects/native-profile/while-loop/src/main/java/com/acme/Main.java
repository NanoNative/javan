package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int index = 0;
        while (index < 3) {
            System.out.println(index);
            index++;
        }
        System.out.println(sum(5));
    }

    public static int sum(final int limit) {
        int total = 0;
        int index = 1;
        while (index <= limit) {
            total = total + index;
            index++;
        }
        return total;
    }
}
