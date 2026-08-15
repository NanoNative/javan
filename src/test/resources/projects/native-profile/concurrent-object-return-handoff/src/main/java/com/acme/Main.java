package com.acme;

import java.util.concurrent.atomic.AtomicInteger;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final AtomicInteger ready = new AtomicInteger();
        final Worker first = new Worker(new Value(7), ready);
        final Worker second = new Worker(new Value(1007), ready);
        final Thread firstThread = new Thread(first);
        final Thread secondThread = new Thread(second);
        firstThread.start();
        secondThread.start();
        firstThread.join();
        secondThread.join();
        System.out.println(first.checksum);
        System.out.println(second.checksum);
    }

    static final class Value {
        private final int value;

        Value(final int value) {
            this.value = value;
        }

        Value copy() {
            return new Value(value);
        }
    }

    static final class Worker implements Runnable {
        private final Value source;
        private final AtomicInteger ready;
        private int checksum;

        Worker(final Value source, final AtomicInteger ready) {
            this.source = source;
            this.ready = ready;
        }

        @Override
        public void run() {
            ready.incrementAndGet();
            while (ready.get() < 2) {
                Thread.yield();
            }
            int nextChecksum = 0;
            for (int index = 0; index < 2048; index++) {
                final Value copy = source.copy();
                nextChecksum += copy.value;
                if ((index & 31) == 0) {
                    Thread.yield();
                }
            }
            checksum = nextChecksum;
        }
    }
}
