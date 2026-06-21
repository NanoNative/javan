package javan;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class ProjectReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeProjectDefaultsProfileToCore() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeProject(layout);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/project.json")))
            .contains("\"profile\": \"core\"");
    }

    @Test
    void writeProjectEscapesWarningControlCharacters() throws Exception {
        final ProjectLayout layout = layout(List.of("quote \" slash \\ newline\nreturn\rtab\t"));

        new ProjectReports().writeProject(layout, Profile.STRICT);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/project.json")))
            .contains("\"warnings\": [\"quote \\\" slash \\\\ newline\\nreturn\\rtab\\t\"]");
    }

    @Test
    void writeProjectSeparatesMultipleListEntries() throws Exception {
        final ProjectLayout layout = layout(List.of("first", "second"));

        new ProjectReports().writeProject(layout, Profile.SERVICE);

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/project.json")))
            .contains("\"warnings\": [\"first\", \"second\"]");
    }

    @Test
    void writeReachabilitySortsReachableMethods() throws Exception {
        final ProjectLayout layout = layout(List.of());
        final CallGraph graph = new CallGraph(
            new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V"),
            List.of(
                new EntryPoint("com/acme/Zed", "call", "()V"),
                new EntryPoint("com/acme/Alpha", "call", "()V")
            ),
            List.of()
        );

        new ProjectReports().writeReachability(layout, graph);

        final String report = Files.readString(layout.outputDirectory().resolve("reports/reachability.txt"));
        assertThat(report.indexOf("com/acme/Alpha.call()V")).isLessThan(report.indexOf("com/acme/Zed.call()V"));
    }

    @Test
    void writeDiagnosticsWritesEmptyReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of());

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.txt")))
            .isEqualTo("No diagnostics." + System.lineSeparator());
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.json")))
            .contains("\"diagnostics\": 0", "\"errors\": 0", "\"warnings\": 0", "\"items\": [");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.md")))
            .contains("- diagnostics: `0`", "No diagnostics.");
    }

    @Test
    void writeDiagnosticsSeparatesMultipleDiagnostics() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error("JAVAN001", "first", "com/acme/Main", "main", "a", "because", "fix"),
            Diagnostic.warning("JAVAN101", "second", "com/acme/Main", "main", "b", "maybe", "fix")
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.txt")))
            .contains("error[JAVAN001]: first")
            .contains("Fix:" + System.lineSeparator() + "  fix" + System.lineSeparator()
                + System.lineSeparator() + "warning[JAVAN101]: second");
    }

    @Test
    void writeDiagnosticsWritesMachineReadableReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.error(
                "JAVAN001",
                "quote \" unsafe",
                "com/acme/Main",
                "main",
                "java/lang/Class.forName",
                "dynamic class loading is not supported",
                "Use direct static references."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.json")))
            .contains(
                "\"schemaVersion\": 1",
                "\"severity\": \"error\"",
                "\"code\": \"JAVAN001\"",
                "\"message\": \"quote \\\" unsafe\"",
                "\"class\": \"com/acme/Main\"",
                "\"subject\": \"java/lang/Class.forName\"",
                "\"reason\": \"dynamic class loading is not supported\"",
                "\"fix\": \"Use direct static references.\""
            );
    }

    @Test
    void writeDiagnosticsWritesReadableMarkdownReport() throws Exception {
        final ProjectLayout layout = layout(List.of());

        new ProjectReports().writeDiagnostics(layout, List.of(
            Diagnostic.warning(
                "JAVAN101",
                "unsupported API in unreachable code",
                "com/acme/Main",
                "unused",
                "java/lang/Class.forName",
                "unreachable reflection is ignored for native code generation",
                "Remove it when possible."
            )
        ));

        assertThat(Files.readString(layout.outputDirectory().resolve("reports/diagnostics.md")))
            .contains(
                "# Diagnostics",
                "- warnings: `1`",
                "## warning[JAVAN101] unsupported API in unreachable code",
                "- class: `com/acme/Main`",
                "- method: `unused`",
                "- subject: `java/lang/Class.forName`",
                "- reason: unreachable reflection is ignored for native code generation",
                "- fix: Remove it when possible."
            );
    }

    private ProjectLayout layout(final List<String> warnings) {
        return new ProjectLayout(
            tempDir,
            tempDir,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(tempDir.resolve("src/main/java")),
            List.of(tempDir.resolve("src/main/resources")),
            List.of(tempDir.resolve("target/classes")),
            List.of(tempDir.resolve("lib/app.jar")),
            tempDir.resolve(".javan"),
            "report-test",
            warnings
        );
    }
}
