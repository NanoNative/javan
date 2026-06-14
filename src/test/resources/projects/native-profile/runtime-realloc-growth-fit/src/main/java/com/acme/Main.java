package com.acme;

import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(final String[] args) {
        final List<Box> values = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            values.add(new Box(index));
        }
        System.out.println(values.get(8).value());
    }
}
