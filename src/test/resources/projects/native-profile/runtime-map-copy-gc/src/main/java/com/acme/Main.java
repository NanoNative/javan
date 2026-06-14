package com.acme;

import java.util.HashMap;
import java.util.Map;

public final class Main {
    public static void main(final String[] args) {
        final Map<String, Box> source = new HashMap<>();
        source.put("left", new Box(19));
        source.put("right", new Box(23));
        final Map<String, Box> copy = Map.copyOf(source);
        pressure();
        System.out.println(copy.get("left").value() + copy.get("right").value());
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
