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
            new MethodEffectAnalyzer.Analysis(List.of()),
            new EscapeAnalyzer.Analysis(List.of()),
            new EscapeAnalyzer.StackAllocationPlan(List.of())
        );
    }

    /**
     * Writes optimizer counters, evidence, and the allocation strategy selected from that evidence.
     *
     * @param outputDirectory javan output directory
     * @param result optimizer result
     * @param effects transitive method effects
     * @param escapes managed allocation escape classifications
     * @param stackAllocations proven release stack allocations
     * @return report files
     * @throws IOException when writing fails
     */
    public List<Path> write(
        final Path outputDirectory,
        final LocalValueOptimizer.Result result,
        final MethodEffectAnalyzer.Analysis effects,
        final EscapeAnalyzer.Analysis escapes,
        final EscapeAnalyzer.StackAllocationPlan stackAllocations
    ) throws IOException {
        return write(
            outputDirectory,
            result.report(),
            result.facts(),
            result.proofs(),
            effects,
            escapes,
            stackAllocations
        );
    }

    private static List<Path> write(
        final Path outputDirectory,
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofs,
        final MethodEffectAnalyzer.Analysis effects,
        final EscapeAnalyzer.Analysis escapes,
        final EscapeAnalyzer.StackAllocationPlan stackAllocations
    ) throws IOException {
        final Path reports = outputDirectory.resolve("reports");
        return List.of(
            Files2.writeString(
                reports.resolve("optimizations.json"),
                json(report, facts, proofs, effects, escapes, stackAllocations)
            ),
            Files2.writeString(
                reports.resolve("optimizations.md"),
                markdown(report, facts, proofs, effects, escapes, stackAllocations)
            )
        );
    }

    private static String json(
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofRecords,
        final MethodEffectAnalyzer.Analysis effects,
        final EscapeAnalyzer.Analysis escapes,
        final EscapeAnalyzer.StackAllocationPlan stackAllocations
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
            + "  \"methodEffects\": " + effectJson(effects) + ",\n"
            + "  \"escapeAnalysis\": " + escapeJson(escapes, stackAllocations) + "\n"
            + "}\n";
    }

    private static String escapeJson(
        final EscapeAnalyzer.Analysis analysis,
        final EscapeAnalyzer.StackAllocationPlan stackAllocations
    ) {
        final EscapeCounts counts = escapeCounts(analysis);
        return "{\"allocationSites\": " + counts.sites()
            + ", \"noEscape\": " + counts.noEscape()
            + ", \"argumentEscape\": " + counts.argumentEscape()
            + ", \"globalEscape\": " + counts.globalEscape()
            + ", \"stackAllocated\": " + stackAllocations.sites().size() + "}";
    }

    private static String effectJson(final MethodEffectAnalyzer.Analysis analysis) {
        final EffectCounts counts = effectCounts(analysis);
        return "{\"methodCount\": " + counts.methods()
            + ", \"pureMethods\": " + counts.pure()
            + ", \"throwingMethods\": " + counts.throwing()
            + ", \"allocatingMethods\": " + counts.allocating()
            + ", \"readingMethods\": " + counts.reading()
            + ", \"writingMethods\": " + counts.writing()
            + ", \"unknownMethods\": " + counts.unknown() + "}";
    }

    private static String markdown(
        final OptimizationReport report,
        final LocalValueOptimizer.FactSummary facts,
        final List<LocalValueOptimizer.Proof> proofRecords,
        final MethodEffectAnalyzer.Analysis effects,
        final EscapeAnalyzer.Analysis escapes,
        final EscapeAnalyzer.StackAllocationPlan stackAllocations
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
            + effectMarkdown(effects)
            + escapeMarkdown(escapes, stackAllocations);
    }

    private static String effectMarkdown(final MethodEffectAnalyzer.Analysis analysis) {
        final EffectCounts counts = effectCounts(analysis);
        return "\n## Method effects\n\n"
            + "- methods: `" + counts.methods() + "`\n"
            + "- pure: `" + counts.pure() + "`\n"
            + "- may throw: `" + counts.throwing() + "`\n"
            + "- allocates: `" + counts.allocating() + "`\n"
            + "- reads: `" + counts.reading() + "`\n"
            + "- writes: `" + counts.writing() + "`\n"
            + "- unknown: `" + counts.unknown() + "`\n";
    }

    private static EffectCounts effectCounts(final MethodEffectAnalyzer.Analysis analysis) {
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
        return new EffectCounts(analysis.methods().size(), pure, throwing, allocating, reading, writing, unknown);
    }

    private static String escapeMarkdown(
        final EscapeAnalyzer.Analysis analysis,
        final EscapeAnalyzer.StackAllocationPlan stackAllocations
    ) {
        final EscapeCounts counts = escapeCounts(analysis);
        return "\n## Escape analysis\n\n"
            + "- allocation sites: `" + counts.sites() + "`\n"
            + "- no escape: `" + counts.noEscape() + "`\n"
            + "- argument escape: `" + counts.argumentEscape() + "`\n"
            + "- global escape: `" + counts.globalEscape() + "`\n"
            + "- stack allocated: `" + stackAllocations.sites().size() + "`\n";
    }

    private static EscapeCounts escapeCounts(final EscapeAnalyzer.Analysis analysis) {
        long noEscape = 0;
        long argumentEscape = 0;
        long globalEscape = 0;
        for (final EscapeAnalyzer.AllocationSite site : analysis.sites()) {
            switch (site.escape()) {
                case NO_ESCAPE -> noEscape++;
                case ARGUMENT_ESCAPE -> argumentEscape++;
                case GLOBAL_ESCAPE -> globalEscape++;
            }
        }
        return new EscapeCounts(analysis.sites().size(), noEscape, argumentEscape, globalEscape);
    }

    private record EffectCounts(
        long methods,
        long pure,
        long throwing,
        long allocating,
        long reading,
        long writing,
        long unknown
    ) {
    }

    private record EscapeCounts(long sites, long noEscape, long argumentEscape, long globalEscape) {
    }
}
