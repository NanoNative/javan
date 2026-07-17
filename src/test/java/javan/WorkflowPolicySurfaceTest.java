package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class WorkflowPolicySurfaceTest {
    private static final Path CI_WORKFLOW = Path.of(".github/workflows/ci.yml");
    private static final Path RELEASE_WORKFLOW = Path.of(".github/workflows/release.yml");
    private static final Path CONTAINER_IMAGES_WORKFLOW = Path.of(".github/workflows/container-images.yml");
    private static final Path JUNIT_PLATFORM_PROPERTIES = Path.of("src/test/resources/junit-platform.properties");
    private static final Path POM = Path.of("pom.xml");
    private static final Path WORKFLOW_ROOT = Path.of(".github/workflows");

    @Test
    void ciWorkflowAvoidsTopLevelConcurrencyQueueing() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .doesNotContain("concurrency:");
    }

    @Test
    void releaseWorkflowAvoidsTopLevelConcurrencyQueueing() throws Exception {
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .doesNotContain("concurrency:");
    }

    @Test
    void containerImagesWorkflowAvoidsTopLevelConcurrencyQueueing() throws Exception {
        assertThat(Files.readString(CONTAINER_IMAGES_WORKFLOW))
            .doesNotContain("concurrency:");
    }

    @Test
    void workflowsDoNotEnableCancelInProgress() throws Exception {
        for (final Path workflow : workflowFiles()) {
            assertThat(Files.readString(workflow))
                .as(workflow + " must not auto-cancel in-flight runs")
                .doesNotContain("cancel-in-progress:");
        }
    }

    @Test
    void ciWorkflowKeepsNinePercentCoverageAsSoftSignal() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("JAVAN_COVERAGE_SOFT_TARGET: \"0.09\"")
            .contains("-Dtest='!Cli*IntegrationTest,!CliExternalProbeAcceptanceIntegrationTest' -Djavan.coverage.check.skip=true verify")
            .contains("-Dtest='Cli*IntegrationTest,CliExternalProbeAcceptanceIntegrationTest' -Djavan.coverage.check.skip=true verify")
            .contains("Summarize coverage (non-blocking)")
            .contains("Soft target: {target_ratio:.0%} (signal only, not a workflow gate)")
            .contains("| Counter | Covered | Total | Ratio | Status |")
            .contains("Upload coverage artifact")
            .contains("name: jacoco-core-${{ matrix.target }}")
            .contains("name: jacoco-cli-integration")
            .contains("::warning::JaCoCo");
    }

    @Test
    void pomKeepsCoverageHardGateOptInByDefault() throws Exception {
        assertThat(Files.readString(POM))
            .contains("<javan.coverage.check.skip>true</javan.coverage.check.skip>")
            .contains("<javan.coverage.line.minimum>0.95</javan.coverage.line.minimum>")
            .contains("<javan.coverage.branch.minimum>0.90</javan.coverage.branch.minimum>");
    }

    @Test
    void ciWorkflowKeepsMacOsX64Disabled() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("branches:\n      - main")
            .contains("target: linux-x64")
            .contains("target: linux-aarch64")
            .contains("target: macos-aarch64")
            .doesNotContain("target: macos-x64")
            .doesNotContain("os: macos-13")
            .doesNotContain("os: macos-14");
    }

    @Test
    void ciWorkflowSkipsReleaseMetadataPushesBeforeHeavyJobsStart() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("if: \"${{ github.event_name != 'push' || !startsWith(github.event.head_commit.message, 'chore: release ') }}\"")
            .contains("name: verify-core (${{ matrix.target }})")
            .contains("name: verify-cli-integration")
            .contains("name: native-smoke (${{ matrix.target }})")
            .contains("name: windows-runtime-smoke (${{ matrix.shard }})");
    }

    @Test
    void releaseWorkflowKeepsMacOsX64Disabled() throws Exception {
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .doesNotContain("package-target: macos-x64")
            .doesNotContain("os: macos-13")
            .doesNotContain("os: macos-14")
            .doesNotContain("macos-15-intel");
    }

    @Test
    void junitPlatformKeepsParallelExecutionEnabledByDefault() throws Exception {
        assertThat(Files.readString(JUNIT_PLATFORM_PROPERTIES))
            .contains("junit.jupiter.execution.parallel.enabled = true")
            .contains("junit.jupiter.execution.parallel.mode.default = concurrent")
            .contains("junit.jupiter.execution.parallel.mode.classes.default = concurrent");
    }

    private static List<Path> workflowFiles() throws Exception {
        try (Stream<Path> files = Files.list(WORKFLOW_ROOT)) {
            return files
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }
    }
}
