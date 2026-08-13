package javan.toolchain.facade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class JavacWrapperReportTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesJsonAndMarkdownForAnIncompleteAnalysis() throws Exception {
        final Path classes = tempDir.resolve("classes");
        final Path report = JavacWrapper.writeReport(
            tempDir.resolve(".javan"),
            new JavacWrapper.InvocationOutcome(0, "unavailable", "missing -d", Optional.of(classes), 0)
        );

        assertThat(report).exists();
        assertThat(Files.readString(report)).contains(
            "\"javacExitCode\": 0",
            "\"analysis\": \"unavailable\"",
            "\"classOutput\": \"" + classes.toAbsolutePath().normalize() + "\""
        );
        assertThat(Files.readString(report.resolveSibling("javac-invocation.md")))
            .contains("native analysis: unavailable", "reason: missing -d");
    }
}
