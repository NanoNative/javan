package javan.toolchain.facade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes the immutable outcome of one Javan javac facade invocation.
 */
public final class JavacInvocationReport {
    private static final String HEX = "0123456789abcdef";

    /**
     * Writes JSON and Markdown reports below the supplied Javan output directory.
     *
     * @param outputDirectory Javan output directory, normally {@code .javan}
     * @param outcome facade invocation outcome
     * @return JSON report path
     * @throws IOException when the report cannot be written
     */
    public Path write(final Path outputDirectory, final Outcome outcome) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(outcome, "outcome");
        final Path reports = outputDirectory.toAbsolutePath().normalize().resolve("reports");
        Files.createDirectories(reports);
        final Path json = reports.resolve("javac-invocation.json");
        Files.writeString(json, json(outcome));
        Files.writeString(reports.resolve("javac-invocation.md"), markdown(outcome));
        return json;
    }

    private static String json(final Outcome outcome) {
        final StringBuilder report = new StringBuilder();
        report.append("{\n");
        report.append("  \"schemaVersion\": 1,\n");
        report.append("  \"javacExitCode\": ").append(outcome.javacExitCode()).append(",\n");
        report.append("  \"analysis\": \"").append(json(outcome.analysis())).append("\",\n");
        report.append("  \"reason\": \"").append(json(outcome.reason())).append("\",\n");
        report.append("  \"classOutput\": ");
        if (outcome.classOutput().isPresent()) {
            report.append('"').append(json(outcome.classOutput().orElseThrow().toString())).append('"');
        } else {
            report.append("null");
        }
        report.append(",\n");
        report.append("  \"diagnostics\": ").append(outcome.diagnosticCount()).append("\n");
        return report.append("}\n").toString();
    }

    private static String markdown(final Outcome outcome) {
        final String classOutput = outcome.classOutput().isPresent()
            ? outcome.classOutput().orElseThrow().toString()
            : "not available";
        final StringBuilder report = new StringBuilder();
        report.append("# Javan Javac Invocation\n\n");
        report.append("- javac exit code: ").append(outcome.javacExitCode()).append('\n');
        report.append("- native analysis: ").append(outcome.analysis()).append('\n');
        report.append("- reason: ").append(outcome.reason()).append('\n');
        report.append("- class output: ").append(classOutput).append('\n');
        return report.append("- diagnostics: ").append(outcome.diagnosticCount()).append('\n').toString();
    }

    private static String json(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                result.append('\\');
            }
            if (character < ' ') {
                appendControlCharacter(result, character);
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static void appendControlCharacter(final StringBuilder result, final char character) {
        if (character == '\n') {
            result.append("\\n");
            return;
        }
        if (character == '\r') {
            result.append("\\r");
            return;
        }
        if (character == '\t') {
            result.append("\\t");
            return;
        }
        result.append("\\u00");
        result.append(HEX.charAt((character >>> 4) & 0x0f));
        result.append(HEX.charAt(character & 0x0f));
    }

    /**
     * One complete facade invocation outcome.
     *
     * @param javacExitCode real javac exit code
     * @param analysis native analysis state
     * @param reason concise state explanation
     * @param classOutput class-output directory when known
     * @param diagnosticCount diagnostics written by completed analysis
     */
    public record Outcome(
        int javacExitCode,
        String analysis,
        String reason,
        Optional<Path> classOutput,
        int diagnosticCount
    ) {
        /**
         * Creates an immutable invocation outcome.
         */
        public Outcome {
            analysis = Objects.requireNonNull(analysis, "analysis");
            reason = Objects.requireNonNull(reason, "reason");
            classOutput = Objects.requireNonNull(classOutput, "classOutput");
            if (classOutput.isPresent()) {
                classOutput = Optional.of(classOutput.orElseThrow().toAbsolutePath().normalize());
            }
            if (diagnosticCount < 0) {
                throw new IllegalArgumentException("diagnosticCount");
            }
        }
    }
}
