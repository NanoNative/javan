package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        String value = "seed";
        int index = 0;
        while (index < 2000) {
            value = ("dead-" + index).substring(1).trim();
            index = index + 1;
        }
        System.out.println(State.ROOT);
        System.out.println(State.ROOT.length());
    }
}
