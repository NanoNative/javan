package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final Box first = new Box(11);
        first.next = new Box(5);
        final Box second = new Box(31);
        final Box selected = choose(first, second, true);
        System.out.println(sum(selected));
    }

    private static Box choose(final Box left, final Box right, final boolean useLeft) {
        final Box local = useLeft ? left : right;
        final Box[] roots = new Box[2];
        roots[0] = local;
        roots[1] = right;
        if (useLeft) {
            return roots[0];
        }
        return roots[1];
    }

    private static int sum(final Box start) {
        Box current = start;
        int total = 0;
        while (current != null) {
            total = total + current.value;
            current = current.next;
        }
        return total;
    }
}
