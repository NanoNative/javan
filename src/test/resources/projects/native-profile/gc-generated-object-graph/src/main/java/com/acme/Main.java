package com.acme;

public final class Main {
    private static Node root;

    private Main() {
    }

    public static void main(final String[] args) {
        root = graph();
        System.out.println(visit(root) + churn());
    }

    private static Node graph() {
        final Node head = new Node(3);
        head.next = new Node(5);
        head.children = new Node[2];
        head.children[0] = new Node(7);
        head.children[1] = head.next;
        head.children[0].next = head;
        return head;
    }

    private static int visit(final Node node) {
        return node.value
            + node.next.value
            + node.children[0].value
            + node.children[1].value
            + node.children[0].next.value;
    }

    private static int churn() {
        int result = 0;
        int index = 0;
        while (index < 300) {
            final Node dead = new Node(index);
            dead.next = new Node(index + 1);
            final Node[] deadArray = new Node[2];
            deadArray[0] = dead;
            deadArray[1] = dead.next;
            result = result + deadArray[0].value + touch(root);
            index = index + 1;
        }
        return result;
    }

    private static int touch(final Node node) {
        return node.value;
    }
}
