package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        int checksum = 0;
        int round = 0;
        while (round < 2000) {
            final Box box = new Box(round, Integer.toString(round));
            final int[] values = new int[8];
            final byte[] bytes = new byte[8];
            final String[] labels = new String[3];
            labels[0] = "round";
            labels[1] = box.name();
            labels[2] = "done";

            int index = 0;
            while (index < values.length) {
                values[index] = round + index;
                bytes[index] = (byte) (values[index] & 127);
                index = index + 1;
            }

            checksum = checksum + box.value();
            checksum = checksum + box.name().length();
            checksum = checksum + values[7];
            checksum = checksum + bytes[3];
            checksum = checksum + labels[0].length();
            checksum = checksum + labels[1].length();
            checksum = checksum + labels[2].length();
            round = round + 1;
        }
        System.out.println(checksum);
    }
}
