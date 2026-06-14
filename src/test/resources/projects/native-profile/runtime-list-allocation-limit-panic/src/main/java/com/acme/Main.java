package com.acme;

import java.util.ArrayList;
import java.util.List;

public final class Main {
    public static void main(final String[] args) {
        final List<String> values = new ArrayList<>();
        values.add("a0");
        values.add("a1");
        values.add("a2");
        values.add("a3");
        values.add("a4");
        System.out.println(values.size());
    }
}
