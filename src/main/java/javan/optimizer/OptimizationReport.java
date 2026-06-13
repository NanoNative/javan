package javan.optimizer;

/**
 * Deterministic optimizer report counters.
 *
 * @param redundantNullChecks redundant null checks removed
 * @param redundantBoundsChecks redundant bounds checks removed
 * @param redundantTypeChecks redundant type checks removed
 * @param redundantRangeChecks redundant range checks removed
 * @param deadBranches dead branches removed
 * @param specializedMethods specialized methods emitted
 * @param skippedCandidates optimization candidates skipped
 */
public record OptimizationReport(
    long redundantNullChecks,
    long redundantBoundsChecks,
    long redundantTypeChecks,
    long redundantRangeChecks,
    long deadBranches,
    long specializedMethods,
    long skippedCandidates
) {
    /**
     * Creates the no-op scaffold report.
     *
     * @return zero-valued report
     */
    public static OptimizationReport scaffold() {
        return new OptimizationReport(0, 0, 0, 0, 0, 0, 0);
    }
}
