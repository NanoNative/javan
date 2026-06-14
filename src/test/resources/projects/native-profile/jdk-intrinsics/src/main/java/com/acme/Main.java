package com.acme;

import java.util.Arrays;
import java.util.Objects;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final String label = Objects.requireNonNull("intrinsics");
        final int value = Math.abs(-7);
        final int floor = Math.min(value, 5);
        final int ceiling = Math.max(floor, 9);
        final int[] copied = Arrays.copyOf(new int[] {1, 2}, 3);
        System.arraycopy(copied, 0, copied, 1, 2);
        System.out.println(label);
        System.out.println(value);
        System.out.println(floor);
        System.out.println(ceiling);
        System.out.println(Integer.toString(copied[1]));
        System.out.println(Long.toString(9876543210L));
    }
}
