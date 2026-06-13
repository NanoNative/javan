package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final String value = "javan";
        final int code = value.charAt(1);
        System.out.println(value.length());
        System.out.println(code);
        System.out.println(value.equals("javan"));
        System.out.println(value.isEmpty());
    }
}
