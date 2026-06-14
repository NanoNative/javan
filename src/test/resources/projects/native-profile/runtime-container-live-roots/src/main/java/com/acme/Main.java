package com.acme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final List<Box> values = new ArrayList<>();
        values.add(new Box(11));
        values.add(new Box(17));
        final Iterator<Box> iterator = values.iterator();
        final HashMap<String, Box> map = new HashMap<>();
        map.put("left", new Box(19));
        final Optional<Box> optional = Optional.of(new Box(23));
        final StringBuilder builder = new StringBuilder();
        builder.append("root");
        pressure();
        int total = 0;
        total += iterator.next().value();
        total += values.get(1).value();
        total += map.get("left").value();
        total += optional.orElseThrow().value();
        total += builder.toString().length();
        System.out.println(total);
    }

    private static void pressure() {
        int index = 0;
        while (index < 40) {
            final int[] unused = new int[128];
            unused[0] = index;
            index++;
        }
    }
}
