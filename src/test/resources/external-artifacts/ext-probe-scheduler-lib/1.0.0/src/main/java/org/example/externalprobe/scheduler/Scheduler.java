package org.example.externalprobe.scheduler;

import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class Scheduler extends ScheduledThreadPoolExecutor {
    public Scheduler(final String name) {
        super(1);
    }
}
