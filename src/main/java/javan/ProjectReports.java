package javan;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import javan.util.Files2;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes machine-readable and text reports under .javan/reports.
 */
public final class ProjectReports {
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
        final String value = diagnosticsValue(diagnostics);
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        Files2.writeString(layout.outputDirectory().resolve("reports/diagnostics.txt"), value);
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
}
