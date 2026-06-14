package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        Node current = new Node(0);
        int checksum = 0;
        int index = 1;
        while (index < 2500) {
            current = new Node(index);
            checksum = checksum + current.value;
            index = index + 1;
        }
        System.out.println(checksum + current.value);
    }
}
