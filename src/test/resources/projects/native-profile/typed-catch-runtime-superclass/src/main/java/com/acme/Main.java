package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        try {
            throw new IllegalStateException("runtime");
        } catch (final RuntimeException exception) {
            System.out.println("runtime:" + exception.getMessage());
        }
    }
}
