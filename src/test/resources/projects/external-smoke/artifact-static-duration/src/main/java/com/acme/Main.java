package com.acme;

import org.nanonative.nano.helper.NanoUtils;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final String duration = NanoUtils.formatDuration(65_000_000_000L);
        System.out.println(duration);
    }
}
