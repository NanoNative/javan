package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Node root = node(21);
        root.next = node(34);
        final int noise = churn();
        System.out.println(root.value + root.next.value + noise - noise);
    }

    private static Node node(final int value) {
        return new Node(value);
    }

    private static int churn() {
        int result = 0;
        int index = 0;
        while (index < 200) {
            final Node dead = node(index);
            dead.next = node(index + 1);
            result = result + dead.value + dead.next.value;
            index = index + 1;
        }
        return result;
    }
}
