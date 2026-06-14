package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        String value = "seed";
        int index = 0;
        while (index < 5000) {
            value = value + index;
            if (value.length() > 48) {
                value = value.substring(1, 9).trim();
            }
            index = index + 1;
        }
        System.out.println(value.length());
    }
}
