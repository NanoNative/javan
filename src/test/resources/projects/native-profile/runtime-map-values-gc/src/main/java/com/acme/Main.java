package com.acme;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class Main {
    public static void main(final String[] args) {
        final Map<String, Box> source = new HashMap<>();
        source.put("left", new Box(31));
        source.put("right", new Box(37));
        final Collection<Box> values = source.values();
        final Iterator<Box> iterator = values.iterator();
        pressure();
        System.out.println(iterator.next().value() + iterator.next().value());
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
