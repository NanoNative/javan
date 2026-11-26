package com.acme;

import java.util.Locale;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        System.out.println("caf\u00e9".toLowerCase(Locale.ROOT));
    }
}
