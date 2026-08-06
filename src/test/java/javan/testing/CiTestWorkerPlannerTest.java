package javan.testing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

final class CiTestWorkerPlannerTest {
    @Test
    void nativeWorkersAreNonEmptyDisjointAndComplete() {
        assertWorkersComplete("native", 6);
    }

    @Test
    void windowsWorkersAreNonEmptyDisjointAndComplete() {
        assertWorkersComplete("windows", 2);
        final int first = expand(CiTestWorkerPlanner.selector("windows", 0, 2)).size();
        final int second = expand(CiTestWorkerPlanner.selector("windows", 1, 2)).size();
        assertThat(Math.abs(first - second)).isLessThanOrEqualTo(1);
    }

    @Test
    void platformPhaseIsDiscoverable() {
        assertThat(expand(CiTestWorkerPlanner.selector("platform", 0, 1))).isNotEmpty();
    }

    @Test
    void packedCommandLineAvoidsWindowsBatchArgumentSplitting() {
        assertThatCode(() -> CiTestWorkerPlanner.main(new String[]{"windows:0:2"}))
            .doesNotThrowAnyException();
    }

    private static void assertWorkersComplete(final String suite, final int workerCount) {
        final Set<String> expected = expand(CiTestWorkerPlanner.selector(suite, 0, 1));
        final Set<String> assigned = new HashSet<>();

        IntStream.range(0, workerCount).forEach(index -> {
            final Set<String> worker = expand(CiTestWorkerPlanner.selector(suite, index, workerCount));
            assertThat(worker).isNotEmpty();
            assertThat(worker.stream().filter(assigned::contains)).isEmpty();
            assigned.addAll(worker);
        });

        assertThat(assigned).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void dedicatedPhasesAreDiscoverable() {
        assertThat(expand(CiTestWorkerPlanner.selector("packaging", 0, 1))).isNotEmpty();
        assertThat(expand(CiTestWorkerPlanner.selector("external", 0, 1))).isNotEmpty();
    }

    @Test
    void invalidWorkerIsRejectedClearly() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CiTestWorkerPlanner.selector("native", 1, 1))
            .withMessage("worker index must be within worker count");
    }

    private static Set<String> expand(final String selector) {
        final Set<String> tests = new HashSet<>();
        for (final String classSelector : selector.split(",")) {
            final String[] parts = classSelector.split("#", 2);
            for (final String method : parts[1].split("[+]")) {
                tests.add(parts[0] + "#" + method);
            }
        }
        return tests;
    }
}
