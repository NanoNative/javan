package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final Problem expected = new Problem("boom", 7);
        try {
            throw expected;
        } catch (final Problem actual) {
            System.out.println((actual == expected) + ":" + actual.getMessage() + ":" + actual.code());
        }
        final Worker first = new Worker();
        final Worker second = new Worker();
        final Thread firstThread = new Thread(first);
        final Thread secondThread = new Thread(second);
        firstThread.start();
        secondThread.start();
        firstThread.join();
        secondThread.join();
        System.out.println(first.checksum);
        System.out.println(second.checksum);
    }

    static final class Worker implements Runnable {
        private int checksum;

        @Override
        public void run() {
            int nextChecksum = 0;
            for (int index = 0; index < 256; index++) {
                try {
                    throw new Problem("worker", 1);
                } catch (final Problem caught) {
                    nextChecksum += caught.code();
                }
                if ((index & 15) == 0) {
                    Thread.yield();
                }
            }
            checksum = nextChecksum;
        }
    }
}
