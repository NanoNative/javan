package com.acme;

public final class Main {
    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        final Worker first = new Worker(1000);
        final Worker second = new Worker(2000);
        final Thread firstThread = new Thread(first);
        final Thread secondThread = new Thread(second);
        firstThread.start();
        secondThread.start();
        firstThread.join();
        secondThread.join();
        System.out.println(first.valid + ":" + first.checksum);
        System.out.println(second.valid + ":" + second.checksum);
    }

    private static void fail(final Problem problem) {
        throw problem;
    }

    private static void preserve(final Problem problem, final int index) {
        try {
            fail(problem);
        } finally {
            Long.toString(index);
        }
    }

    private static void replace(final Problem problem, final int index) {
        final RuntimeException replacement = replacement(index);
        try {
            fail(problem);
        } finally {
            Long.toString(index);
            throw replacement;
        }
    }

    private static RuntimeException replacement(final int index) {
        return (index & 1) == 0
            ? new Replacement("replacement")
            : new AlternateReplacement("alternate");
    }

    private static int preserveOnce(final int base, final int index) {
        final Problem expected = new Problem("original", base + index);
        try {
            preserve(expected, index);
            return -1;
        } catch (final Problem actual) {
            if (actual != expected
                || !"original".equals(actual.getMessage())
                || actual.code() != base + index) {
                return -1;
            }
            return actual.code();
        }
    }

    private static int replaceOnce(final int base, final int index) {
        final Problem original = new Problem("original", base + index);
        try {
            replace(original, index);
            return -1;
        } catch (final Replacement replacement) {
            if ((index & 1) != 0 || !"replacement".equals(replacement.getMessage())) {
                return -1;
            }
            return index;
        } catch (final AlternateReplacement replacement) {
            if ((index & 1) == 0 || !"alternate".equals(replacement.getMessage())) {
                return -1;
            }
            return index;
        }
    }

    private static final class Worker implements Runnable {
        private final int base;
        private boolean valid = true;
        private int checksum;

        private Worker(final int base) {
            this.base = base;
        }

        @Override
        public void run() {
            int nextChecksum = 0;
            for (int index = 0; index < 128; index++) {
                final int preserved = preserveOnce(base, index);
                final int replaced = replaceOnce(base, index);
                valid &= preserved >= 0 && replaced >= 0;
                nextChecksum += preserved + replaced;
                if ((index & 15) == 0) {
                    Thread.yield();
                }
            }
            checksum = nextChecksum;
        }
    }
}
