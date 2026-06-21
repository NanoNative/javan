package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        int index = 0;
        while (index < 1000) {
            final Boolean value = Boolean.valueOf((index & 1) == 0);
            if (value.booleanValue()) {
                checksum = checksum + 1;
            }
            index = index + 1;
        }
        System.out.println(checksum);
    }
}
