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
    void ciWorkflowQueuesByRefWithoutCancelingRuns() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("concurrency:")
            .contains("group: ${{ github.workflow }}-${{ github.event.pull_request.number || github.ref }}")
            .contains("cancel-in-progress: false");
    }

    @Test
    void releaseWorkflowQueuesByRefWithoutCancelingRuns() throws Exception {
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
    }

    @Test
    void containerImagesWorkflowQueuesByRefWithoutCancelingRuns() throws Exception {
        assertThat(Files.readString(CONTAINER_IMAGES_WORKFLOW))
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
    }

    @Test
    void workflowsDoNotEnableCancelInProgress() throws Exception {
        for (final Path workflow : workflowFiles()) {
            assertThat(Files.readString(workflow))
                .as(workflow + " must not auto-cancel in-flight runs")
                .contains("cancel-in-progress: false")
                .doesNotContain("cancel-in-progress: true");
        }
    }

    @Test
    void ciWorkflowKeepsNinePercentCoverageAsSoftSignal() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("JAVAN_COVERAGE_SOFT_TARGET: \"0.09\"")
            .contains("mvn -q -Dmaven.repo.local=\"$MAVEN_REPO_LOCAL\" -Dtest='!Cli*IntegrationTest,!CliExternalProbeAcceptanceIntegrationTest' -Djavan.coverage.check.skip=true verify")
            .contains("name: verify-cli-integration (${{ matrix.shard }})")
            .contains("Cli*IntegrationTest,!CliExternalProbeAcceptanceIntegrationTest,!CliPackagingIntegrationTest,!CliJdkSemanticsIntegrationTest,!CliThreadRuntimeIntegrationTest,!CliRuntimeTranslationIntegrationTest")
            .contains("CliJdkSemanticsIntegrationTest")
            .contains("CliThreadRuntimeIntegrationTest,CliRuntimeTranslationIntegrationTest")
            .contains("CliExternalProbeAcceptanceIntegrationTest,CliPackagingIntegrationTest")
            .contains("mvn -q -Dmaven.repo.local=\"$MAVEN_REPO_LOCAL\" -Dtest='${{ matrix.test-selector }}' -Djavan.coverage.check.skip=true verify")
            .contains("find target -maxdepth 1 -type f -name 'jacoco-surefire-*.exec' -print -quit")
            .contains("Summarize coverage (non-blocking)")
            .contains("Soft target: {target_ratio:.0%} (signal only, not a workflow gate)")
            .contains("| Counter | Covered | Total | Ratio | Status |")
            .contains("Upload coverage artifact")
            .contains("name: jacoco-core-${{ matrix.target }}")
            .contains("name: jacoco-cli-integration-${{ matrix.shard }}")
            .contains("::warning::JaCoCo");
    }

    @Test
    void ciWorkflowRunsShardableSuitesAtFullParallelWidth() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("name: verify-core (${{ matrix.target }})")
            .contains("max-parallel: 3")
            .contains("shard: cli-general")
            .contains("shard: cli-jdk-semantics")
            .contains("shard: cli-runtime-heavy")
            .contains("shard: packaging-and-probes")
            .contains("max-parallel: 4")
            .contains("name: native-smoke (${{ matrix.target }})")
            .contains("name: windows-runtime-smoke (${{ matrix.shard }})");
    }

    @Test
    void pomKeepsCoverageHardGateOptInByDefault() throws Exception {
        assertThat(Files.readString(POM))
            .contains("<javan.coverage.check.skip>true</javan.coverage.check.skip>")
            .contains("<javan.coverage.line.minimum>0.95</javan.coverage.line.minimum>")
            .contains("<javan.coverage.branch.minimum>0.90</javan.coverage.branch.minimum>")
            .contains("<systemPropertyVariables>")
            .contains("<junit.jupiter.execution.parallel.enabled>true</junit.jupiter.execution.parallel.enabled>")
            .contains("<junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>")
            .contains("<junit.jupiter.execution.parallel.mode.classes.default>concurrent</junit.jupiter.execution.parallel.mode.classes.default>")
            .contains("<junit.jupiter.execution.parallel.config.strategy>dynamic</junit.jupiter.execution.parallel.config.strategy>")
            .contains("<junit.jupiter.execution.parallel.config.dynamic.factor>1.0</junit.jupiter.execution.parallel.config.dynamic.factor>")
            .contains("<forkCount>2</forkCount>")
            .contains("<reuseForks>true</reuseForks>")
            .contains("jacoco-surefire-${surefire.forkNumber}.exec")
            .contains("<include>jacoco-surefire-*.exec</include>")
            .contains("<testResources>")
            .contains("<exclude>projects/**/.javan/**</exclude>");
    }

    @Test
    void mavenVerifyRefreshesCompatibilityStatusThroughTheCanonicalCli() throws Exception {
        assertThat(Files.readString(POM))
            .contains("<javan.compatibility.reference.java.vendor>Eclipse Adoptium</javan.compatibility.reference.java.vendor>")
            .contains("<javan.compatibility.reference.java.version>25.0.1</javan.compatibility.reference.java.version>")
            .contains("<javan.compatibility.reference.os.name>Linux</javan.compatibility.reference.os.name>")
            .contains("<javan.compatibility.reference.os.arch>amd64</javan.compatibility.reference.os.arch>")
            .contains("<javan.compatibility.require-reference-jdk>false</javan.compatibility.require-reference-jdk>")
            .contains("<artifactId>exec-maven-plugin</artifactId>")
            .contains("<id>refresh-compatibility-status</id>")
            .contains("<phase>verify</phase>")
            .contains("<goal>java</goal>")
            .contains("<mainClass>javan.compat.CompatibilityStatusRefresh</mainClass>")
            .contains("<argument>${project.basedir}</argument>")
            .contains("<argument>${project.build.outputDirectory}</argument>")
            .contains("<argument>${java-version}</argument>")
            .contains("<argument>${javan.compatibility.reference.java.vendor}</argument>")
            .contains("<argument>${javan.compatibility.reference.java.version}</argument>")
            .contains("<argument>${javan.compatibility.reference.os.name}</argument>")
            .contains("<argument>${javan.compatibility.reference.os.arch}</argument>")
            .contains("<argument>${javan.compatibility.require-reference-jdk}</argument>");
    }

    @Test
    void ciWorkflowVerifiesTrackedCompatibilityStatusOnThePinnedReferenceJdk() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("verify-compatibility-status:")
            .contains("name: verify-compatibility-status")
            .contains("java-version: '25.0.1'")
            .contains("distribution: temurin")
            .contains("mvn -q -DskipTests -Djacoco.skip=true")
            .contains("-Djavan.compatibility.require-reference-jdk=true verify")
            .contains(
                "needs:\n"
                    + "      - verify-compatibility-status\n"
                    + "      - verify-core"
            );
    }

    @Test
    void ciWorkflowKeepsMacOsOutOfRemoteNativeMatrix() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("branches:\n      - main")
            .contains("pull_request:\n    branches:\n      - main")
            .contains("target: linux-x64")
            .contains("target: linux-aarch64")
            .doesNotContain("target: macos-aarch64")
            .doesNotContain("target: macos-x64")
            .doesNotContain("os: macos-13")
            .doesNotContain("os: macos-14");
    }

    @Test
    void ciWorkflowSkipsReleaseMetadataPushesBeforeHeavyJobsStart() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("if: \"${{ github.event_name != 'push' || !(startsWith(github.event.head_commit.message, 'chore: release ') || startsWith(github.event.head_commit.message, 'chore: prepare binary release repo')) }}\"")
            .contains("name: verify-core (${{ matrix.target }})")
            .contains("name: verify-cli-integration (${{ matrix.shard }})")
            .contains("name: native-smoke (${{ matrix.target }})")
            .contains("name: windows-runtime-smoke (${{ matrix.shard }})");
    }

    @Test
    void releaseWorkflowSkipsReleasePrepPushes() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("if: \"${{ github.event_name == 'push' && github.ref_name == 'main' && !(startsWith(github.event.head_commit.message, 'chore: release ') || startsWith(github.event.head_commit.message, 'chore: prepare binary release repo')) }}\"");
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
    void workflowsUseNode24ReadyArtifactAndCheckoutActionMajors() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("uses: actions/checkout@v7")
            .contains("uses: actions/upload-artifact@v6");
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains("uses: actions/checkout@v7")
            .contains("uses: actions/upload-artifact@v6")
            .contains("uses: actions/download-artifact@v8");
        assertThat(Files.readString(CONTAINER_IMAGES_WORKFLOW))
            .contains("uses: actions/checkout@v7");
    }

    @Test
    void junitPlatformKeepsParallelExecutionEnabledByDefault() throws Exception {
        assertThat(Files.readString(JUNIT_PLATFORM_PROPERTIES))
            .contains("junit.jupiter.execution.parallel.enabled = true")
            .contains("junit.jupiter.execution.parallel.mode.default = concurrent")
            .contains("junit.jupiter.execution.parallel.mode.classes.default = concurrent")
            .contains("junit.jupiter.execution.parallel.config.strategy = dynamic")
            .contains("junit.jupiter.execution.parallel.config.dynamic.factor = 1.0");
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
