package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Node root = join(node(1), join(node(2), node(3)));
        final Holder holder = new Holder();
        holder.value = join(node(4), node(5));
        final Node[] array = new Node[1];
        array[0] = join(node(6), node(7));
        final int noise = churn();
        System.out.println(sum(root) + sum(holder.value) + sum(array[0]) + noise - noise);
    }

    private static Node node(final int value) {
        return new Node(value);
    }

    private static Node join(final Node left, final Node right) {
        left.next = right;
        return left;
    }

    private static int sum(final Node start) {
        int total = 0;
        Node current = start;
        while (current != null) {
            total = total + current.value;
            current = current.next;
        }
        return total;
    }

    private static int churn() {
        int result = 0;
        int index = 0;
        while (index < 250) {
            result = result + join(node(index), node(index + 1)).value;
            index = index + 1;
        }
        return result;
    }
}
