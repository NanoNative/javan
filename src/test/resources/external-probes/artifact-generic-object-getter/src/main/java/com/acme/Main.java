package com.acme;

import org.example.externalprobe.pair.Pair;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Pair<String, String> pair = new Pair<>("key", "value");
        System.out.println(pair.getValue());
    }
}
