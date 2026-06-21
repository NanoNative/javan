package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        int index = 0;
        while (index < 1000) {
            final Integer value = Integer.valueOf(index);
            checksum = checksum + value.intValue();
            index = index + 1;
        }
        System.out.println(checksum);
    }
}
