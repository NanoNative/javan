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
            List.of(),
            new MethodEffectAnalyzer.Analysis(List.of())
        );
    }

    /**
     * Writes optimizer counters, observed fact counts, and proof records.
     *
     * @param outputDirectory javan output directory
     * @param result optimizer result
     * @param effects transitive method effects
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> write(
        final Path outputDirectory,
        final LocalValueOptimizer.Result result,
        final MethodEffectAnalyzer.Analysis effects
    ) throws IOException {
        return write(outputDirectory, result.report(), result.facts(), result.proofs(), effects);
    }

    private static List<Path> write(
        final Path outputDirectory,
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofs,
        final MethodEffectAnalyzer.Analysis effects
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        return List.of(
            Files2.writeString(reports.resolve("optimizations.json"), json(report, facts, proofs, effects)),
            Files2.writeString(reports.resolve("optimizations.md"), markdown(report, facts, proofs, effects))
        );
    }

    private static String json(
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofRecords,
        final MethodEffectAnalyzer.Analysis effects
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
            + "  \"proofs\": [\n" + proofs + "\n  ],\n"
            + "  \"methodEffects\": [\n" + effectJson(effects) + "\n  ]\n"
            + "}\n";
    }

    private static String effectJson(final MethodEffectAnalyzer.Analysis analysis) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < analysis.methods().size(); index++) {
            final MethodEffectAnalyzer.MethodEffect method = analysis.methods().get(index);
            final MethodEffectAnalyzer.Effect effect = method.effect();
            if (index > 0) {
                result.append(",\n");
            }
            result.append("    {\"owner\": ").append(Json.string(method.owner()))
                .append(", \"method\": ").append(Json.string(method.name()))
                .append(", \"descriptor\": ").append(Json.string(method.descriptor()))
                .append(", \"symbol\": ").append(Json.string(method.symbol()))
                .append(", \"pure\": ").append(effect.pure())
                .append(", \"mayThrow\": ").append(effect.mayThrow())
                .append(", \"allocates\": ").append(effect.allocates())
                .append(", \"reads\": ").append(effect.reads())
                .append(", \"writes\": ").append(effect.writes())
                .append(", \"unknown\": ").append(effect.unknown()).append('}');
        }
        return result.toString();
    }

    private static String markdown(
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofRecords,
        final MethodEffectAnalyzer.Analysis effects
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
            + (proofs.isEmpty() ? "None.\n" : proofs.toString())
            + effectMarkdown(effects);
    }

    private static String effectMarkdown(final MethodEffectAnalyzer.Analysis analysis) {
        long pure = 0;
        long throwing = 0;
        long allocating = 0;
        long reading = 0;
        long writing = 0;
        long unknown = 0;
        for (final MethodEffectAnalyzer.MethodEffect method : analysis.methods()) {
            final MethodEffectAnalyzer.Effect effect = method.effect();
            pure += effect.pure() ? 1 : 0;
            throwing += effect.mayThrow() ? 1 : 0;
            allocating += effect.allocates() ? 1 : 0;
            reading += effect.reads() ? 1 : 0;
            writing += effect.writes() ? 1 : 0;
            unknown += effect.unknown() ? 1 : 0;
        }
        return "\n## Method effects\n\n"
            + "- methods: `" + analysis.methods().size() + "`\n"
            + "- pure: `" + pure + "`\n"
            + "- may throw: `" + throwing + "`\n"
            + "- allocates: `" + allocating + "`\n"
            + "- reads: `" + reading + "`\n"
            + "- writes: `" + writing + "`\n"
            + "- unknown: `" + unknown + "`\n";
    }
}
