package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        long checksum = 0L;
        long index = 0L;
        while (index < 1000L) {
            final Long value = Long.valueOf(index);
            checksum = checksum + value.longValue();
            index = index + 1L;
        }
        System.out.println(checksum);
    }
}
