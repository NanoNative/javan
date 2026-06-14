package com.acme;

import java.util.List;

public final class Main {
    public static void main(final String[] args) {
        final Box[] items = new Box[] {
            new Box(13),
            new Box(29)
        };
        final List<Box> list = List.of(items);
        pressure();
        System.out.println(list.get(0).value() + list.get(1).value());
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
