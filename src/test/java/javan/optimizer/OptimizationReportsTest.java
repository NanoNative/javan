package javan.optimizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void writesFactsAndProofs() throws Exception {
        final OptimizationReports reports = new OptimizationReports();
        final LocalValueOptimizer.Result result = new LocalValueOptimizer.Result(
            new javan.ir.IrProgram(List.of(), ""),
            new OptimizationReport(1, 0, 0, 0, 1, 0, 0),
            List.of(new LocalValueOptimizer.Proof("example/Main", "run", "()V", 7, "null-check", "receiver is proven non-null")),
            new LocalValueOptimizer.FactSummary(2, 0, 1, 2, 2, 1, 1)
        );

        reports.write(
            tempDir,
            result,
            new MethodEffectAnalyzer.Analysis(List.of(new MethodEffectAnalyzer.MethodEffect(
                "example/Main",
                "run",
                "()V",
                "example_Main_run",
                new MethodEffectAnalyzer.Effect(true, true, false, false, false)
            )))
        );

        assertThat(Files.readString(tempDir.resolve("reports/optimizations.json"))).contains(
            "\"facts\": {\"nonNullValues\": 2",
            "\"arrayLengths\": 1",
            "\"kind\": \"null-check\"",
            "\"bytecodeOffset\": 7",
            "\"methodEffects\"",
            "\"methodCount\": 1",
            "\"throwingMethods\": 1",
            "\"allocatingMethods\": 1"
        );
        assertThat(Files.readString(tempDir.resolve("reports/optimizations.md"))).contains(
            "## Local facts",
            "- array lengths: `1`",
            "## Proofs",
            "`null-check` example/Main.run()V @ 7: receiver is proven non-null",
            "## Method effects",
            "- methods: `1`",
            "- may throw: `1`"
        );
    }
}
