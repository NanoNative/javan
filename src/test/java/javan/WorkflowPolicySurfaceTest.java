package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class WorkflowPolicySurfaceTest {
    private static final Path CI_WORKFLOW = Path.of(".github/workflows/ci.yml");
    private static final Path RELEASE_WORKFLOW = Path.of(".github/workflows/release.yml");
    private static final Path CONTAINER_IMAGES_WORKFLOW = Path.of(".github/workflows/container-images.yml");
    private static final Path JUNIT_PLATFORM_PROPERTIES = Path.of("src/test/resources/junit-platform.properties");

    @Test
    void ciWorkflowDisablesAutoCancellation() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
    }

    @Test
    void releaseWorkflowDisablesAutoCancellation() throws Exception {
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
    }

    @Test
    void containerImagesWorkflowDisablesAutoCancellation() throws Exception {
        assertThat(Files.readString(CONTAINER_IMAGES_WORKFLOW))
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
    }

    @Test
    void ciWorkflowKeepsNinePercentCoverageAsSoftSignal() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("JAVAN_COVERAGE_SOFT_TARGET: \"0.09\"")
            .contains("Summarize coverage (non-blocking)")
            .contains("::warning::JaCoCo");
    }

    @Test
    void ciWorkflowKeepsMacOsX64Disabled() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .doesNotContain("target: macos-x64")
            .doesNotContain("os: macos-13")
            .doesNotContain("os: macos-14");
    }

    @Test
    void junitPlatformKeepsStableClassLevelParallelism() throws Exception {
        assertThat(Files.readString(JUNIT_PLATFORM_PROPERTIES))
            .contains("junit.jupiter.execution.parallel.enabled = true")
            .contains("junit.jupiter.execution.parallel.mode.default = same_thread")
            .contains("junit.jupiter.execution.parallel.mode.classes.default = concurrent");
    }
}
