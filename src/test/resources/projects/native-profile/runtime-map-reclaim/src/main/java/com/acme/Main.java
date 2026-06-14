package com.acme;

import java.util.HashMap;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int total = 0;
        int index = 0;
        while (index < 1000) {
            final HashMap<String, Box> values = new HashMap<>();
            values.put("key", new Box(index));
            total += values.get("key").value();
            index++;
        }
        System.out.println(total);
    }
}
