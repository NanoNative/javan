package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Node node = makeNode();
        System.out.println(node.value());
    }

    private static Node makeNode() {
        int[] waste = new int[256];
        waste[255] = 99;
        waste = null;
        return new Node(42);
    }
}
