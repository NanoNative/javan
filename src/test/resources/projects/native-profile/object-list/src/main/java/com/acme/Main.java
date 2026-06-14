package com.acme;

import java.util.ArrayList;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final List<String> values = new ArrayList<>();
        values.add("left");
        System.out.println(values.get(0));
    }
}
