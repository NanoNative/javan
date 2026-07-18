package org.example.externalprobe.time;

public final class DurationFormatter {
    private DurationFormatter() {
    }

    public static String formatDuration(final long nanos) {
        final long totalSeconds = nanos / 1_000_000_000L;
        final long minutes = totalSeconds / 60L;
        final long seconds = totalSeconds % 60L;
        return minutes + "m " + seconds + "s";
    }
}
