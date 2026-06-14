package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        try {
            throw new Error("error");
        } catch (final Exception exception) {
            System.out.println("wrong");
        } catch (final Throwable throwable) {
            System.out.println("throwable:" + throwable.getMessage());
        }
    }
}
