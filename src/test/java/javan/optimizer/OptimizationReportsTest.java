package javan.optimizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class OptimizationReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesZeroValuedScaffoldReports() throws Exception {
        final OptimizationReports reports = new OptimizationReports();

        final var written = reports.writeScaffold(tempDir);

        assertThat(written).containsExactly(
            tempDir.resolve("reports/optimizations.json"),
            tempDir.resolve("reports/optimizations.md")
        );
        assertThat(Files.readString(tempDir.resolve("reports/optimizations.json")))
            .contains(
                "\"redundantNullChecks\": 0",
                "\"redundantBoundsChecks\": 0",
                "\"redundantTypeChecks\": 0",
                "\"redundantRangeChecks\": 0",
                "\"deadBranches\": 0",
                "\"specializedMethods\": 0",
                "\"skippedCandidates\": 0"
            );
        assertThat(Files.readString(tempDir.resolve("reports/optimizations.md")))
            .contains(
                "- redundant null checks: `0`",
                "- redundant bounds checks: `0`",
                "- redundant type checks: `0`",
                "- redundant range checks: `0`",
                "- dead branches: `0`",
                "- specialized methods: `0`",
                "- skipped candidates: `0`"
            );
    }
}
