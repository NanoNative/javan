package javan.reporting;

import javan.build.RuntimeFeatureSelection;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes honest runtime-profiling status reports.
 */
public final class RuntimeProfilingReports {
    /**
     * Writes {@code runtime-profiling.json} and {@code runtime-profiling.md}.
     *
     * @param reportsDirectory {@code .javan/reports} directory
     * @param settings runtime settings
     * @throws IOException when writing fails
     */
    public void write(final Path reportsDirectory, final RuntimeFeatureSelection.Settings settings) throws IOException {
        final Status status = status(settings);
        Files2.writeString(reportsDirectory.resolve("runtime-profiling.json"), json(status));
        Files2.writeString(reportsDirectory.resolve("runtime-profiling.md"), markdown(status));
    }

    private static Status status(final RuntimeFeatureSelection.Settings settings) {
        final List<String> disabledModules = disabledProfilingModules(settings);
        if (!settings.profiling()) {
            return new Status(
                "disabled",
                false,
                false,
                "disabled",
                "Runtime profiling is off in configuration.",
                disabledModules
            );
        }
        if (!disabledModules.isEmpty()) {
            return new Status(
                "disabled",
                true,
                false,
                "disabled-by-module",
                "Runtime profiling was requested, but disabled runtime modules block profiling hooks: " + join(disabledModules) + ".",
                disabledModules
            );
        }
        return new Status(
            "ready",
            true,
            true,
            "linked-not-run",
            "Runtime profiling is linked and will collect counters when the native binary runs through a profiling-enabled launch path.",
            List.of()
        );
    }

    private static List<String> disabledProfilingModules(final RuntimeFeatureSelection.Settings settings) {
        final List<String> result = new ArrayList<>();
        for (final String module : settings.disabledRuntimeModules()) {
            if ("live-profiling".equals(module) || "thread-profiling".equals(module)) {
                result.add(module);
            }
        }
        return List.copyOf(result);
    }

    private static String json(final Status status) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        field(result, "schemaVersion", "1", true);
        field(result, "status", Json.string(status.status()), true);
        field(result, "requested", Boolean.toString(status.requested()), true);
        field(result, "enabled", Boolean.toString(status.enabled()), true);
        field(result, "collectionState", Json.string(status.collectionState()), true);
        field(result, "reason", Json.string(status.reason()), true);
        field(result, "disabledProfilingModules", Json.stringList(status.disabledProfilingModules()), false);
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(final Status status) {
        return "# Runtime Profiling\n\n"
            + "- status: `" + status.status() + "`\n"
            + "- requested: `" + status.requested() + "`\n"
            + "- enabled: `" + status.enabled() + "`\n"
            + "- collectionState: `" + status.collectionState() + "`\n"
            + "- disabledProfilingModules: `" + join(status.disabledProfilingModules()) + "`\n"
            + "- reason: " + status.reason() + "\n";
    }

    private static void field(final StringBuilder result, final String name, final String value, final boolean comma) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static String join(final List<String> values) {
        if (values.isEmpty()) {
            return "-";
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    private record Status(
        String status,
        boolean requested,
        boolean enabled,
        String collectionState,
        String reason,
        List<String> disabledProfilingModules
    ) {
    }
}
