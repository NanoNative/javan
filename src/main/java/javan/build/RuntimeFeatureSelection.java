package javan.build;

import javan.toolchain.SimpleToml;
import javan.reporting.RuntimeProfilingReports;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enforces user-selected runtime feature constraints from {@code javan.toml}.
 */
public final class RuntimeFeatureSelection {
    private final RuntimeProfilingReports runtimeProfilingReports = new RuntimeProfilingReports();
    private static final List<String> KNOWN_RUNTIME_MODULES = List.of(
        "arrays",
        "collections",
        "core",
        "certificates",
        "debug-symbols",
        "environment",
        "exceptions",
        "ffi-memory",
        "filesystem",
        "gc",
        "http",
        "io",
        "live-profiling",
        "managed-heap",
        "maps",
        "math",
        "network",
        "optional",
        "process",
        "random",
        "reflection-metadata",
        "socket",
        "strings",
        "thread-profiling",
        "threads",
        "tls",
        "time"
    );

    /**
     * Reads configuration, enforces disabled module contracts, and writes reports.
     *
     * @param root project root
     * @param outputDirectory javan output directory
     * @param plan deduplication/runtime module plan
     * @return runtime feature report
     * @throws IOException when reading configuration or writing reports fails
     */
    public Report write(
        final Path root,
        final Path outputDirectory,
        final DeduplicationPlanner.Plan plan
    ) throws IOException {
        final Settings settings = read(root);
        final List<String> reachable = sortedUnique(plan.runtimeModules());
        final List<String> disabled = sortedUnique(settings.disabledRuntimeModules());
        final List<String> disabledReachable = intersection(disabled, reachable);
        final List<String> disabledUnused = difference(disabled, reachable);
        final List<String> unknownDisabled = unknown(disabled);
        final List<Diagnostic> diagnostics = diagnostics(disabledReachable, unknownDisabled);
        final Path reports = outputDirectory.resolve("reports");
        final Path jsonPath = reports.resolve("runtime-features.json");
        final Path markdownPath = reports.resolve("runtime-features.md");
        Files2.writeString(jsonPath, json(settings, reachable, disabledReachable, disabledUnused, unknownDisabled));
        Files2.writeString(markdownPath, markdown(settings, reachable, disabledReachable, disabledUnused, unknownDisabled));
        runtimeProfilingReports.write(reports, settings);
        return new Report(jsonPath, markdownPath, settings, reachable, disabledReachable, disabledUnused, unknownDisabled, diagnostics);
    }

    /**
     * Reads runtime feature settings from {@code javan.toml}.
     *
     * @param root project root
     * @return runtime feature settings
     * @throws IOException when the config file cannot be read
     */
    public Settings read(final Path root) throws IOException {
        final Path config = root.resolve("javan.toml");
        if (!Files.isRegularFile(config)) {
            return Settings.defaults(config);
        }
        final Map<String, String> values = SimpleToml.parse(Files.readString(config));
        return new Settings(
            Optional.of(config),
            normalizedList(first(values, "runtime.disabled", "build.runtime.disabled", "disabled", "")),
            firstText(values, "runtime.containment", "build.runtime.containment").orElse("system-linked"),
            firstText(values, "runtime.optimize", "build.runtime.optimize").orElse("balanced"),
            bool(first(values, "runtime.debug", "build.runtime.debug", "debug", ""), false),
            bool(first(values, "runtime.profiling", "build.runtime.profiling", "profiling", ""), false)
        );
    }

    private static List<Diagnostic> diagnostics(final List<String> disabledReachable, final List<String> unknownDisabled) {
        final List<Diagnostic> result = new ArrayList<>();
        for (final String module : unknownDisabled) {
            result.add(Diagnostic.error(
                "JAVAN060",
                "unknown disabled runtime module",
                "",
                "",
                module,
                "The runtime disabled list contains a module that javan does not know.",
                "Use one of: " + join(", ", KNOWN_RUNTIME_MODULES) + "."
            ));
        }
        for (final String module : disabledReachable) {
            result.add(Diagnostic.error(
                "JAVAN060",
                "disabled runtime module is reachable",
                "",
                "",
                module,
                "The project disables `" + module + "`, but reachable code needs that runtime module.",
                "Remove `" + module + "` from [runtime].disabled or remove the reachable code that needs it."
            ));
        }
        return List.copyOf(result);
    }

    private static String json(
        final Settings settings,
        final List<String> reachable,
        final List<String> disabledReachable,
        final List<String> disabledUnused,
        final List<String> unknownDisabled
    ) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        field(result, "schemaVersion", Json.string("1"), true);
        field(result, "configPath", Json.string(configPathText(settings, "")), true);
        field(result, "containment", Json.string(settings.containment()), true);
        field(result, "optimize", Json.string(settings.optimize()), true);
        field(result, "debug", bool(settings.debug()), true);
        field(result, "profiling", bool(settings.profiling()), true);
        field(result, "reachableRuntimeModules", Json.stringList(reachable), true);
        field(result, "disabledRuntimeModules", Json.stringList(settings.disabledRuntimeModules()), true);
        field(result, "disabledReachableRuntimeModules", Json.stringList(disabledReachable), true);
        field(result, "disabledUnusedRuntimeModules", Json.stringList(disabledUnused), true);
        field(result, "unknownDisabledRuntimeModules", Json.stringList(unknownDisabled), true);
        field(result, "status", Json.string(disabledReachable.isEmpty() && unknownDisabled.isEmpty() ? "pass" : "fail"), false);
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(
        final Settings settings,
        final List<String> reachable,
        final List<String> disabledReachable,
        final List<String> disabledUnused,
        final List<String> unknownDisabled
    ) {
        return "# Runtime Features\n\n"
            + "- config: `" + configPathText(settings, "-") + "`\n"
            + "- containment: `" + settings.containment() + "`\n"
            + "- optimize: `" + settings.optimize() + "`\n"
            + "- debug: `" + settings.debug() + "`\n"
            + "- profiling: `" + settings.profiling() + "`\n"
            + "- reachable runtime modules: `" + join(", ", reachable) + "`\n"
            + "- disabled runtime modules: `" + join(", ", settings.disabledRuntimeModules()) + "`\n"
            + "- disabled reachable modules: `" + join(", ", disabledReachable) + "`\n"
            + "- disabled unused modules: `" + join(", ", disabledUnused) + "`\n"
            + "- unknown disabled modules: `" + join(", ", unknownDisabled) + "`\n"
            + "- status: `" + (disabledReachable.isEmpty() && unknownDisabled.isEmpty() ? "pass" : "fail") + "`\n";
    }

    private static Optional<String> normalizedText(final String value) {
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(Strings2.replaceChar(Strings2.toAsciiLowerCase(Strings2.trimAscii(value)), '_', '-'));
    }

    private static List<String> normalizedList(final String raw) {
        final List<String> result = new ArrayList<>();
        for (final String value : rawList(raw)) {
            final Optional<String> normalized = normalizedText(value);
            if (normalized.isPresent()) {
                result.add(normalized.orElseThrow());
            }
        }
        return sortedUnique(result);
    }

    private static String configPathText(final Settings settings, final String fallback) {
        if (settings.configPath().isEmpty()) {
            return fallback;
        }
        return settings.configPath().orElseThrow().toString();
    }

    private static List<String> rawList(final String raw) {
        if (Strings2.isBlank(raw)) {
            return List.of();
        }
        final String trimmed = Strings2.trimAscii(raw);
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of(unquote(trimmed));
        }
        final List<String> result = new ArrayList<>();
        boolean quoted = false;
        int start = 1;
        for (int index = 1; index < trimmed.length() - 1; index++) {
            final char value = trimmed.charAt(index);
            if (value == '"') {
                quoted = !quoted;
            }
            if (value == ',' && !quoted) {
                addRawListValue(result, trimmed, start, index);
                start = index + 1;
            }
        }
        addRawListValue(result, trimmed, start, trimmed.length() - 1);
        return List.copyOf(result);
    }

    private static void addRawListValue(final List<String> values, final String raw, final int start, final int end) {
        final String value = unquote(Strings2.trimAscii(Strings2.slice(raw, start, end)));
        if (!Strings2.isBlank(value)) {
            values.add(value);
        }
    }

    private static String unquote(final String value) {
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return Strings2.slice(value, 1, value.length() - 1);
        }
        return value;
    }

    private static Optional<String> firstText(final Map<String, String> values, final String first, final String second) {
        final Optional<String> firstValue = normalizedText(values.get(first));
        if (firstValue.isPresent()) {
            return firstValue;
        }
        return normalizedText(values.get(second));
    }

    private static String first(
        final Map<String, String> values,
        final String first,
        final String second,
        final String third,
        final String fallback
    ) {
        final String firstValue = values.get(first);
        if (!Strings2.isBlank(firstValue)) {
            return firstValue;
        }
        final String secondValue = values.get(second);
        if (!Strings2.isBlank(secondValue)) {
            return secondValue;
        }
        final String thirdValue = values.get(third);
        return Strings2.isBlank(thirdValue) ? fallback : thirdValue;
    }

    private static boolean bool(final String raw, final boolean fallback) {
        if (Strings2.isBlank(raw)) {
            return fallback;
        }
        final String normalized = Strings2.toAsciiLowerCase(Strings2.trimAscii(raw));
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Expected boolean runtime setting: " + raw);
    }

    private static List<String> intersection(final List<String> left, final List<String> right) {
        final List<String> result = new ArrayList<>();
        for (final String value : left) {
            if (contains(right, value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> difference(final List<String> left, final List<String> right) {
        final List<String> result = new ArrayList<>();
        for (final String value : left) {
            if (!contains(right, value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> unknown(final List<String> values) {
        final List<String> result = new ArrayList<>();
        for (final String value : values) {
            if (!contains(KNOWN_RUNTIME_MODULES, value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> sortedUnique(final List<String> values) {
        final List<String> result = new ArrayList<>();
        for (final String value : values) {
            if (contains(result, value)) {
                continue;
            }
            int index = 0;
            while (index < result.size() && Strings2.compareAscii(result.get(index), value) <= 0) {
                index++;
            }
            result.add(index, value);
        }
        return List.copyOf(result);
    }

    private static boolean contains(final List<String> values, final String target) {
        for (final String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static String join(final String delimiter, final List<String> values) {
        if (values.isEmpty()) {
            return "-";
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(delimiter);
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static void field(final StringBuilder result, final String name, final String value, final boolean comma) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static String bool(final boolean value) {
        return value ? "true" : "false";
    }

    /**
     * Runtime feature settings loaded from project configuration.
     *
     * @param configPath config path when present
     * @param disabledRuntimeModules normalized disabled runtime module names
     * @param containment requested containment policy
     * @param optimize requested optimization posture
     * @param debug true when debug runtime posture is requested
     * @param profiling true when profiling runtime posture is requested
     */
    public record Settings(
        Optional<Path> configPath,
        List<String> disabledRuntimeModules,
        String containment,
        String optimize,
        boolean debug,
        boolean profiling
    ) {
        /**
         * Returns default settings for a project without {@code javan.toml}.
         *
         * @param expectedConfigPath expected config path
         * @return default settings
         */
        public static Settings defaults(final Path expectedConfigPath) {
            return new Settings(Optional.empty(), List.of(), "system-linked", "balanced", false, false);
        }
    }

    /**
     * Written runtime feature report.
     *
     * @param jsonPath JSON report path
     * @param markdownPath Markdown report path
     * @param settings parsed settings
     * @param reachableRuntimeModules runtime modules needed by reachable code
     * @param disabledReachableRuntimeModules disabled modules used by reachable code
     * @param disabledUnusedRuntimeModules disabled modules not reached
     * @param unknownDisabledRuntimeModules disabled names unknown to javan
     * @param diagnostics generated diagnostics
     */
    public record Report(
        Path jsonPath,
        Path markdownPath,
        Settings settings,
        List<String> reachableRuntimeModules,
        List<String> disabledReachableRuntimeModules,
        List<String> disabledUnusedRuntimeModules,
        List<String> unknownDisabledRuntimeModules,
        List<Diagnostic> diagnostics
    ) {
    }
}
