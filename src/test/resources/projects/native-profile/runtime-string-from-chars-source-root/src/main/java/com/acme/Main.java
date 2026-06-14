package com.acme;

public final class Main {
    public static void main(final String[] args) {
        final char[] chars = new char[] {
            'x',
            'j',
            'a',
            'v',
            'a',
            'n',
            'z'
        };
        final String result = new String(chars, 1, 5);
        pressure();
        System.out.println(result);
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
