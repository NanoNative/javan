package com.acme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Main {
    public static void main(final String[] args) {
        int total = 0;
        int index = 0;
        while (index < 1000) {
            final Map<String, List<Box>> map = new HashMap<>();
            final List<Box> boxes = new ArrayList<>();
            boxes.add(new Box(index));
            map.put("box", boxes);
            total = total + map.get("box").get(0).value();
            index = index + 1;
        }
        System.out.println(total);
    }
}
