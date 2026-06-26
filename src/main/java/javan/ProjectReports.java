package javan;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import javan.reporting.ThreadReports;
import javan.reporting.VirtualThreadReports;
import javan.util.Files2;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes machine-readable and text reports under .javan/reports.
 */
public final class ProjectReports {
    private final ThreadReports threadReports = new ThreadReports();
    private final VirtualThreadReports virtualThreadReports = new VirtualThreadReports();

    /**
     * Writes the detected project layout as JSON.
     *
     * @param layout detected project layout
     * @throws IOException when writing fails
     */
    public void writeProject(final ProjectLayout layout) throws IOException {
        writeProject(layout, Profile.CORE);
    }

    /**
     * Writes the detected project layout and selected profile as JSON.
     *
     * @param layout detected project layout
     * @param profile selected profile
     * @throws IOException when writing fails
     */
    public void writeProject(final ProjectLayout layout, final Profile profile) throws IOException {
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendJsonField(json, "root", json(layout.root().toString()), true);
        appendJsonField(json, "input", json(layout.input().toString()), true);
        appendJsonField(json, "inputKind", json(layout.inputKind().name()), true);
        appendJsonField(json, "buildTool", json(layout.buildTool().name()), true);
        appendJsonField(json, "profile", json(profile.cliName()), true);
        appendJsonField(json, "sourceFolders", pathJsonList(layout.sourceFolders()), true);
        appendJsonField(json, "resourceFolders", pathJsonList(layout.resourceFolders()), true);
        appendJsonField(json, "classFolders", pathJsonList(layout.classFolders()), true);
        appendJsonField(json, "classpathEntries", pathJsonList(layout.classpathEntries()), true);
        appendJsonField(json, "outputDirectory", json(layout.outputDirectory().toString()), true);
        appendJsonField(json, "outputName", json(layout.outputName()), true);
        appendJsonField(json, "warnings", jsonList(layout.warnings()), false);
        json.append("}\n");
        Files2.writeString(layout.outputDirectory().resolve("reports/project.json"), json.toString());
    }

    /**
     * Writes reachability information.
     *
     * @param layout detected project layout
     * @param callGraph call graph
     * @throws IOException when writing fails
     */
    public void writeReachability(final ProjectLayout layout, final CallGraph callGraph) throws IOException {
        final StringBuilder report = new StringBuilder();
        report.append("entry: ").append(callGraph.entryPoint().display()).append(System.lineSeparator());
        report.append("reachable:").append(System.lineSeparator());
        for (final String line : sortedEntries(callGraph.reachableMethods())) {
            report.append("  ").append(line).append(System.lineSeparator());
        }
        Files2.writeString(layout.outputDirectory().resolve("reports/reachability.txt"), report.toString());
    }

    /**
     * Writes diagnostics.
     *
     * @param layout detected project layout
     * @param diagnostics diagnostics
     * @throws IOException when writing fails
     */
    public void writeDiagnostics(final ProjectLayout layout, final List<Diagnostic> diagnostics) throws IOException {
        writeDiagnostics(layout, diagnostics, ThreadReports.summarize(diagnostics));
    }

    /**
     * Writes diagnostics and reachable thread summary details.
     *
     * @param layout detected project layout
     * @param diagnostics diagnostics
     * @param classes scanned classes
     * @param callGraph reachable methods and caller edges
     * @throws IOException when writing fails
     */
    public void writeDiagnostics(
        final ProjectLayout layout,
        final List<Diagnostic> diagnostics,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        writeDiagnostics(layout, diagnostics, ThreadReports.summarize(diagnostics, classes, callGraph), classes, callGraph);
    }

    private void writeDiagnostics(
        final ProjectLayout layout,
        final List<Diagnostic> diagnostics,
        final ThreadReports.Summary threadSummary
    ) throws IOException {
        final String value = diagnosticsValue(diagnostics);
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        final Path reports = layout.outputDirectory().resolve("reports");
        Files2.writeString(reports.resolve("diagnostics.txt"), value);
        Files2.writeString(reports.resolve("diagnostics.json"), diagnosticsJson(diagnostics));
        Files2.writeString(reports.resolve("diagnostics.md"), diagnosticsMarkdown(diagnostics));
        threadReports.write(reports, diagnostics, threadSummary);
        virtualThreadReports.write(reports);
    }

    private void writeDiagnostics(
        final ProjectLayout layout,
        final List<Diagnostic> diagnostics,
        final ThreadReports.Summary threadSummary,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        final String value = diagnosticsValue(diagnostics);
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        final Path reports = layout.outputDirectory().resolve("reports");
        Files2.writeString(reports.resolve("diagnostics.txt"), value);
        Files2.writeString(reports.resolve("diagnostics.json"), diagnosticsJson(diagnostics));
        Files2.writeString(reports.resolve("diagnostics.md"), diagnosticsMarkdown(diagnostics));
        threadReports.write(reports, diagnostics, threadSummary);
        virtualThreadReports.write(reports, diagnostics, classes, callGraph);
    }

    private static String jsonList(final List<String> values) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(json(values.get(index)));
        }
        return result.append("]").toString();
    }

    private static String pathJsonList(final List<Path> values) {
        final List<String> strings = new ArrayList<>();
        for (final Path value : values) {
            strings.add(value.toString());
        }
        return jsonList(strings);
    }

    private static List<String> sortedEntries(final List<EntryPoint> entries) {
        final List<String> result = new ArrayList<>();
        for (final EntryPoint entry : entries) {
            insertSorted(result, entry.toString());
        }
        return List.copyOf(result);
    }

    private static void insertSorted(final List<String> values, final String value) {
        int index = 0;
        while (index < values.size() && Strings2.compareAscii(values.get(index), value) <= 0) {
            index++;
        }
        values.add(index, value);
    }

    private static String diagnosticsValue(final List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return new StringBuilder().append("No diagnostics.").append(System.lineSeparator()).toString();
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < diagnostics.size(); index++) {
            if (index > 0) {
                result.append(System.lineSeparator()).append(System.lineSeparator());
            }
            result.append(diagnostics.get(index).format());
        }
        return result.append(System.lineSeparator()).toString();
    }

    private static String diagnosticsJson(final List<Diagnostic> diagnostics) {
        final long errors = errorCount(diagnostics);
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"schemaVersion\": 1,\n");
        result.append("  \"diagnostics\": ").append(diagnostics.size()).append(",\n");
        result.append("  \"errors\": ").append(errors).append(",\n");
        result.append("  \"warnings\": ").append(diagnostics.size() - errors).append(",\n");
        result.append("  \"items\": [\n");
        for (int index = 0; index < diagnostics.size(); index++) {
            final Diagnostic diagnostic = diagnostics.get(index);
            result.append("    {\n");
            appendJsonField(result, "severity", json(diagnostic.error() ? "error" : "warning"), true, 6);
            appendJsonField(result, "code", json(diagnostic.code()), true, 6);
            appendJsonField(result, "message", json(diagnostic.message()), true, 6);
            appendJsonField(result, "class", json(diagnostic.className()), true, 6);
            appendJsonField(result, "method", json(diagnostic.methodName()), true, 6);
            appendJsonField(result, "subject", json(diagnostic.subject()), true, 6);
            appendJsonField(result, "reason", json(diagnostic.reason()), true, 6);
            appendJsonField(result, "fix", json(diagnostic.fix()), false, 6);
            result.append("    }");
            if (index + 1 < diagnostics.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        return result.append("}\n").toString();
    }

    private static String diagnosticsMarkdown(final List<Diagnostic> diagnostics) {
        final long errors = errorCount(diagnostics);
        final StringBuilder result = new StringBuilder();
        result.append("# Diagnostics\n\n");
        result.append("- diagnostics: `").append(diagnostics.size()).append("`\n");
        result.append("- errors: `").append(errors).append("`\n");
        result.append("- warnings: `").append(diagnostics.size() - errors).append("`\n\n");
        if (diagnostics.isEmpty()) {
            return result.append("No diagnostics.\n").toString();
        }
        for (final Diagnostic diagnostic : diagnostics) {
            result.append("## ")
                .append(diagnostic.error() ? "error" : "warning")
                .append("[")
                .append(diagnostic.code())
                .append("] ")
                .append(diagnostic.message())
                .append("\n\n");
            result.append("- class: `").append(emptyDash(diagnostic.className())).append("`\n");
            result.append("- method: `").append(emptyDash(diagnostic.methodName())).append("`\n");
            result.append("- subject: `").append(emptyDash(diagnostic.subject())).append("`\n");
            result.append("- reason: ").append(emptyDash(diagnostic.reason())).append('\n');
            result.append("- fix: ").append(emptyDash(diagnostic.fix())).append("\n\n");
        }
        return result.toString();
    }

    private static String emptyDash(final String value) {
        return Strings2.isBlank(value) ? "-" : value;
    }

    private static long errorCount(final List<Diagnostic> diagnostics) {
        long result = 0L;
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                result++;
            }
        }
        return result;
    }

    private static String json(final String value) {
        final StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            switch (ch) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(ch);
            }
        }
        return result.append('"').toString();
    }

    private static void appendJsonField(
        final StringBuilder result,
        final String name,
        final String value,
        final boolean comma
    ) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static void appendJsonField(
        final StringBuilder result,
        final String name,
        final String value,
        final boolean comma,
        final int spaces
    ) {
        appendSpaces(result, spaces);
        result.append("\"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static void appendSpaces(final StringBuilder result, final int spaces) {
        for (int index = 0; index < spaces; index++) {
            result.append(' ');
        }
    }
}
