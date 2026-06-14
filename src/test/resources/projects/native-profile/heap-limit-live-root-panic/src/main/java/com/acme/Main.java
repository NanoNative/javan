package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final Node root = new Node(7);
        root.next = new Node(11);
        final int[] denied = new int[1024];
        System.out.println(root.value + root.next.value + denied.length);
    }
}
