package com.acme;

import java.util.Optional;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int total = 0;
        int index = 0;
        while (index < 1000) {
            final Optional<Box> value = Optional.of(new Box(index));
            total += value.orElseThrow().value();
            index++;
        }
        System.out.println(total);
    }
}
