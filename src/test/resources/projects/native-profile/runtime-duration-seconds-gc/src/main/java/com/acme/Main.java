package com.acme;

import java.time.Duration;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        int index = 0;
        while (index < 1000) {
            if (Duration.ofSeconds(65L).toMillis() == 65000L) {
                checksum = checksum + 1;
            }
            index = index + 1;
        }
        System.out.println(checksum);
    }
}
