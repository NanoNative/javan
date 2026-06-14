package com.acme;

import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int total = 0;
        int index = 0;
        while (index < 1000) {
            final List<Box> values = new ArrayList<>();
            values.add(new Box(index));
            total += values.get(0).value();
            index++;
        }
        System.out.println(total);
    }
}
