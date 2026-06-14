package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final char[] chars = new char[] {
            'a',
            'b',
            'c'
        };
        final String value = new String(chars, 0, chars.length);
        chars[1] = 'x';
        pressure();
        System.out.println(value);
        System.out.println(value.length());
    }

    private static void pressure() {
        int index = 0;
        while (index < 48) {
            final char[] chars = new char[] {
                'd',
                'e',
                'a',
                'd'
            };
            final String value = new String(chars, 0, chars.length);
            if (value.length() == -1) {
                System.out.println(value);
            }
            index = index + 1;
        }
    }
}
