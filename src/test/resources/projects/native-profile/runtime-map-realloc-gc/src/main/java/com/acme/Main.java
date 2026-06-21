package com.acme;

import java.util.HashMap;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        int round = 0;
        while (round < 32) {
            final HashMap<String, String> map = new HashMap<>();
            int index = 0;
            while (index < 12) {
                map.put("key-" + round + "-" + index, "value-" + index);
                index = index + 1;
            }
            checksum = checksum + map.get("key-" + round + "-11").length();
            round = round + 1;
        }
        System.out.println(checksum);
    }
}
