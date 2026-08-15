package javan.optimizer;

import javan.util.Files2;
import javan.util.Json;

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
        return write(
            outputDirectory,
            OptimizationReport.scaffold(),
            new LocalValueOptimizer.FactSummary(0, 0, 0, 0, 0, 0, 0),
            List.of()
        );
    }

    /**
     * Writes optimizer counters, observed fact counts, and proof records.
     *
     * @param outputDirectory javan output directory
     * @param result optimizer result
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> write(final Path outputDirectory, final LocalValueOptimizer.Result result) throws IOException {
        return write(outputDirectory, result.report(), result.facts(), result.proofs());
    }

    private static List<Path> write(
        final Path outputDirectory,
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofs
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        return List.of(
            Files2.writeString(reports.resolve("optimizations.json"), json(report, facts, proofs)),
            Files2.writeString(reports.resolve("optimizations.md"), markdown(report, facts, proofs))
        );
    }

    private static String json(
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofRecords
    ) {
        final StringBuilder proofs = new StringBuilder();
        for (int index = 0; index < proofRecords.size(); index++) {
            final LocalValueOptimizer.Proof proof = proofRecords.get(index);
            if (index > 0) {
                proofs.append(",\n");
            }
            proofs.append("    {\"owner\": ").append(Json.string(proof.owner()))
                .append(", \"method\": ").append(Json.string(proof.method()))
                .append(", \"descriptor\": ").append(Json.string(proof.descriptor()))
                .append(", \"bytecodeOffset\": ").append(proof.bytecodeOffset())
                .append(", \"kind\": ").append(Json.string(proof.kind()))
                .append(", \"reason\": ").append(Json.string(proof.reason())).append('}');
        }
        return "{\n"
            + "  \"redundantNullChecks\": " + report.redundantNullChecks() + ",\n"
            + "  \"redundantBoundsChecks\": " + report.redundantBoundsChecks() + ",\n"
            + "  \"redundantTypeChecks\": " + report.redundantTypeChecks() + ",\n"
            + "  \"redundantRangeChecks\": " + report.redundantRangeChecks() + ",\n"
            + "  \"deadBranches\": " + report.deadBranches() + ",\n"
            + "  \"specializedMethods\": " + report.specializedMethods() + ",\n"
            + "  \"skippedCandidates\": " + report.skippedCandidates() + ",\n"
            + "  \"facts\": {\"nonNullValues\": " + facts.nonNullValues()
            + ", \"nullValues\": " + facts.nullValues()
            + ", \"integerConstants\": " + facts.integerConstants()
            + ", \"integerRanges\": " + facts.integerRanges()
            + ", \"exactTypes\": " + facts.exactTypes()
            + ", \"arrayLengths\": " + facts.arrayLengths()
            + ", \"stringLengths\": " + facts.stringLengths() + "},\n"
            + "  \"proofs\": [\n" + proofs + "\n  ]\n"
            + "}\n";
    }

    private static String markdown(
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofRecords
    ) {
        final StringBuilder proofs = new StringBuilder();
        for (final LocalValueOptimizer.Proof proof : proofRecords) {
            proofs.append("- `").append(proof.kind()).append("` ")
                .append(proof.owner()).append('.').append(proof.method()).append(proof.descriptor())
                .append(" @ ").append(proof.bytecodeOffset()).append(": ").append(proof.reason()).append('\n');
        }
        return "# Optimizations\n\n"
            + "- redundant null checks: `" + report.redundantNullChecks() + "`\n"
            + "- redundant bounds checks: `" + report.redundantBoundsChecks() + "`\n"
            + "- redundant type checks: `" + report.redundantTypeChecks() + "`\n"
            + "- redundant range checks: `" + report.redundantRangeChecks() + "`\n"
            + "- dead branches: `" + report.deadBranches() + "`\n"
            + "- specialized methods: `" + report.specializedMethods() + "`\n"
            + "- skipped candidates: `" + report.skippedCandidates() + "`\n\n"
            + "## Local facts\n\n"
            + "- non-null values: `" + facts.nonNullValues() + "`\n"
            + "- null values: `" + facts.nullValues() + "`\n"
            + "- integer constants: `" + facts.integerConstants() + "`\n"
            + "- integer ranges: `" + facts.integerRanges() + "`\n"
            + "- exact types: `" + facts.exactTypes() + "`\n"
            + "- array lengths: `" + facts.arrayLengths() + "`\n"
            + "- string lengths: `" + facts.stringLengths() + "`\n\n"
            + "## Proofs\n\n"
            + (proofs.isEmpty() ? "None.\n" : proofs.toString());
    }
}
