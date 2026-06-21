package com.acme;

import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        for (int index = 0; index < 180; index++) {
            final List<String> values = List.of(
                dynamic("left", index),
                dynamic("right", index),
                dynamic("tail", index)
            );
            checksum += values.get(0).length();
            checksum += values.get(1).charAt(0);
            checksum += values.get(2).length();
        }
        System.out.println(checksum);
    }

    private static String dynamic(final String prefix, final int index) {
        return prefix + "-" + index;
    }
}
