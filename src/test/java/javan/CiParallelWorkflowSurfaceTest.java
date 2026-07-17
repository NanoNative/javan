package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class CiParallelWorkflowSurfaceTest {
    private static final Path CI_WORKFLOW = Path.of(".github/workflows/ci.yml");

    @Test
    void ciWorkflowSplitsCoreVerifyCliVerifyAndNativeSmokeIntoParallelJobs() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("verify-core:")
            .contains("verify-cli-integration:")
            .contains("native-smoke:")
            .contains("JAVAN_JUNIT_PARALLEL_ARGS: >-")
            .contains("name: verify-core (${{ matrix.target }})")
            .contains("name: verify-cli-integration (${{ matrix.shard }})")
            .contains("shard: cli-general")
            .contains("shard: cli-jdk-semantics")
            .contains("shard: cli-runtime-heavy")
            .contains("shard: packaging-and-probes")
            .contains("name: native-smoke (${{ matrix.target }})")
            .contains("max-parallel: 4")
            .contains("$JAVAN_JUNIT_PARALLEL_ARGS -Dtest='!Cli*IntegrationTest,!CliExternalProbeAcceptanceIntegrationTest' -Djavan.coverage.check.skip=true verify")
            .contains("$JAVAN_JUNIT_PARALLEL_ARGS -Dtest='${{ matrix.test-selector }}' -Djavan.coverage.check.skip=true verify")
            .contains("Verify core Maven suite")
            .contains("Verify CLI integration Maven suite")
            .contains("Compile test fixtures")
            .contains("Verify acceptance suite");
    }

    @Test
    void ciWorkflowLeavesParallelLanesIndependentUntilRelease() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("verify-core:")
            .contains("verify-cli-integration:")
            .contains("native-smoke:")
            .contains("windows-runtime-smoke:")
            .contains("max-parallel: 2")
            .contains("shard: current-thread")
            .contains("shard: worker-thread")
            .contains("needs:\n      - verify-core\n      - verify-cli-integration\n      - native-smoke\n      - windows-runtime-smoke")
            .doesNotContain("verify-core:\n    needs:")
            .doesNotContain("verify-cli-integration:\n    needs:")
            .doesNotContain("native-smoke:\n    needs:")
            .doesNotContain("windows-runtime-smoke:\n    needs:");
    }

    @Test
    void releaseWaitsForNativeSmokeBeforePackaging() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("needs:")
            .contains("- verify-core")
            .contains("- verify-cli-integration")
            .contains("- native-smoke")
            .contains("- windows-runtime-smoke");
    }

    @Test
    void nativeSmokeKeepsMacOsX64Disabled() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("target: linux-x64")
            .contains("target: linux-aarch64")
            .contains("target: macos-aarch64")
            .doesNotContain("target: macos-x64")
            .doesNotContain("os: macos-13")
            .doesNotContain("os: macos-14");
    }
}
