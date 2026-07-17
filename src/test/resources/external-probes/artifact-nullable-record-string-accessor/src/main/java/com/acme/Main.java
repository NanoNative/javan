package com.acme;

import org.example.externalprobe.metric.MetricUpdate;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) {
        final MetricUpdate update = new MetricUpdate(null, "requests", null, null);
        System.out.println(update.name());
    }
}
