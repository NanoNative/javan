package com.acme;

import org.example.externalprobe.time.DurationFormatter;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final String duration = DurationFormatter.formatDuration(65_000_000_000L);
        System.out.println(duration);
    }
}
