package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int total = 0;
        int index = 0;
        while (index < 1000) {
            final StringBuilder builder = new StringBuilder();
            builder.append("value");
            builder.append(index);
            total += builder.length();
            index++;
        }
        System.out.println(total);
    }
}
