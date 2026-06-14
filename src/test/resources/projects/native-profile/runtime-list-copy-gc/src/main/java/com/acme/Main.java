package com.acme;

import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(final String[] args) {
        final List<Box> source = new ArrayList<>();
        source.add(new Box(17));
        source.add(new Box(25));
        final List<Box> copy = List.copyOf(source);
        pressure();
        System.out.println(copy.get(0).value() + copy.get(1).value());
    }

    private static void pressure() {
        for (int index = 0; index < 48; index++) {
            final Box box = new Box(index);
            if (box.value() == -1) {
                System.out.println(box.value());
            }
        }
    }
}
