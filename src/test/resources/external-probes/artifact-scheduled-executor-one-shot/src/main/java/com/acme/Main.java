package com.acme;

import java.util.concurrent.TimeUnit;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final var scheduler = new org.nanonative.nano.core.model.Scheduler("probe");
        scheduler.schedule(new Task(), 10L, TimeUnit.MILLISECONDS);
        Thread.sleep(30L);
        scheduler.shutdown();
        System.out.println(scheduler.awaitTermination(1L, TimeUnit.SECONDS));
    }
}
