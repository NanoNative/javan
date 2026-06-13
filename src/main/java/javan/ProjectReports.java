package javan;

import javan.analysis.CallGraph;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import javan.util.Files2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Files;
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
        final String json = "{\n"
            + "  \"root\": " + json(layout.root().toString()) + ",\n"
            + "  \"input\": " + json(layout.input().toString()) + ",\n"
            + "  \"inputKind\": " + json(layout.inputKind().name()) + ",\n"
            + "  \"buildTool\": " + json(layout.buildTool().name()) + ",\n"
            + "  \"profile\": " + json(profile.cliName()) + ",\n"
            + "  \"sourceFolders\": " + jsonList(layout.sourceFolders().stream().map(Object::toString).toList()) + ",\n"
            + "  \"resourceFolders\": " + jsonList(layout.resourceFolders().stream().map(Object::toString).toList()) + ",\n"
            + "  \"classFolders\": " + jsonList(layout.classFolders().stream().map(Object::toString).toList()) + ",\n"
            + "  \"classpathEntries\": " + jsonList(layout.classpathEntries().stream().map(Object::toString).toList()) + ",\n"
            + "  \"outputDirectory\": " + json(layout.outputDirectory().toString()) + ",\n"
            + "  \"outputName\": " + json(layout.outputName()) + ",\n"
            + "  \"warnings\": " + jsonList(layout.warnings()) + "\n"
            + "}\n";
        Files2.writeString(layout.outputDirectory().resolve("reports/project.json"), json);
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
        callGraph.reachableMethods().stream()
            .map(Object::toString)
            .sorted()
            .forEach(line -> report.append("  ").append(line).append(System.lineSeparator()));
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
        final String value = diagnostics.isEmpty()
            ? "No diagnostics." + System.lineSeparator()
            : String.join(System.lineSeparator() + System.lineSeparator(), diagnostics.stream().map(Diagnostic::format).toList())
                + System.lineSeparator();
        Files.createDirectories(layout.outputDirectory().resolve("reports"));
        Files2.writeString(layout.outputDirectory().resolve("reports/diagnostics.txt"), value);
    }

    private static String jsonList(final List<String> values) {
        return "[" + String.join(", ", values.stream().map(ProjectReports::json).toList()) + "]";
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
}
