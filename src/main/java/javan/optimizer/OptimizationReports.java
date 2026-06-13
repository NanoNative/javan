package javan.optimizer;

import javan.util.Files2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes deterministic optimizer reports under .javan/reports.
 */
public final class OptimizationReports {
    /**
     * Writes the current optimizer report scaffold.
     *
     * @param outputDirectory javan output directory
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> writeScaffold(final Path outputDirectory) throws IOException {
        final OptimizationReport report = OptimizationReport.scaffold();
        final Path reports = outputDirectory.resolve("reports");
        return List.of(
            Files2.writeString(reports.resolve("optimizations.json"), json(report)),
            Files2.writeString(reports.resolve("optimizations.md"), markdown(report))
        );
    }

    private static String json(final OptimizationReport report) {
        return "{\n"
            + "  \"redundantNullChecks\": " + report.redundantNullChecks() + ",\n"
            + "  \"redundantBoundsChecks\": " + report.redundantBoundsChecks() + ",\n"
            + "  \"redundantTypeChecks\": " + report.redundantTypeChecks() + ",\n"
            + "  \"redundantRangeChecks\": " + report.redundantRangeChecks() + ",\n"
            + "  \"deadBranches\": " + report.deadBranches() + ",\n"
            + "  \"specializedMethods\": " + report.specializedMethods() + ",\n"
            + "  \"skippedCandidates\": " + report.skippedCandidates() + "\n"
            + "}\n";
    }

    private static String markdown(final OptimizationReport report) {
        return "# Optimizations\n\n"
            + "- redundant null checks: `" + report.redundantNullChecks() + "`\n"
            + "- redundant bounds checks: `" + report.redundantBoundsChecks() + "`\n"
            + "- redundant type checks: `" + report.redundantTypeChecks() + "`\n"
            + "- redundant range checks: `" + report.redundantRangeChecks() + "`\n"
            + "- dead branches: `" + report.deadBranches() + "`\n"
            + "- specialized methods: `" + report.specializedMethods() + "`\n"
            + "- skipped candidates: `" + report.skippedCandidates() + "`\n\n"
            + "This scaffold reports optimizer decisions only. It does not remove checks or rewrite code.\n";
    }
}
