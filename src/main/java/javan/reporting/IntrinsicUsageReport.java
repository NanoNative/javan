package javan.reporting;

import java.util.List;

/**
 * Reachable intrinsic usage report data.
 *
 * @param intrinsics planned intrinsic counts in stable order
 * @param unsupportedJdkCallCandidates reachable non-intrinsic JDK method call candidates
 * @param unsupportedJdkCallCandidateCount total reachable non-intrinsic JDK call sites
 */
public record IntrinsicUsageReport(
    List<IntrinsicCallCount> intrinsics,
    List<UnsupportedJdkCallCandidate> unsupportedJdkCallCandidates,
    int unsupportedJdkCallCandidateCount
) {
}
