package javan.testing;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

final class CiTestShardPlannerTest {
    @Test
    void nativeShardsAreNonEmptyDisjointAndComplete() {
        final Set<String> expected = expand(CiTestShardPlanner.selector("native", 0, 1));
        final Set<String> assigned = new HashSet<>();

        IntStream.range(0, 6).forEach(index -> {
            final Set<String> shard = expand(CiTestShardPlanner.selector("native", index, 6));
            assertThat(shard).isNotEmpty();
            assertThat(shard.stream().filter(assigned::contains)).isEmpty();
            assigned.addAll(shard);
        });

        assertThat(assigned).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void dedicatedPhasesAreDiscoverable() {
        assertThat(expand(CiTestShardPlanner.selector("packaging", 0, 1))).isNotEmpty();
        assertThat(expand(CiTestShardPlanner.selector("external", 0, 1))).isNotEmpty();
    }

    @Test
    void invalidShardIsRejectedClearly() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> CiTestShardPlanner.selector("native", 1, 1))
            .withMessage("shard index must be within shard count");
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
