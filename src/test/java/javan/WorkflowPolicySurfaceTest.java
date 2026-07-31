package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class WorkflowPolicySurfaceTest {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path NATIVE_PROOF = Path.of(".github/workflows/native-proof.yml");
    private static final Path PLATFORM_PROOF = Path.of(".github/workflows/platform-proof.yml");
    private static final Path RELEASE_WORKFLOW = Path.of(".github/workflows/release.yml");
    private static final Path CONTAINER_IMAGES_WORKFLOW = Path.of(".github/workflows/container-images.yml");
    private static final Path PUBLISH_CENTRAL = Path.of(".github/workflows/publish-central.yml");
    private static final Path DEPENDABOT = Path.of(".github/dependabot.yml");
    private static final Path JUNIT_PLATFORM_PROPERTIES = Path.of("src/test/resources/junit-platform.properties");
    private static final Path POM = Path.of("pom.xml");
    private static final Path WORKFLOW_ROOT = Path.of(".github/workflows");

    @Test
    void entryWorkflowsQueueWithoutCancelingInFlightRuns() throws Exception {
        for (final Path workflow : List.of(BUILD_PR, BUILD_MERGE, RELEASE_WORKFLOW, CONTAINER_IMAGES_WORKFLOW, PUBLISH_CENTRAL)) {
            assertThat(Files.readString(workflow))
                .as(workflow + " must queue instead of canceling in-flight work")
                .contains("concurrency:")
                .contains("cancel-in-progress: false");
        }
    }

    @Test
    void noWorkflowEnablesCancellationOfInFlightRuns() throws Exception {
        for (final Path workflow : workflowFiles()) {
            assertThat(Files.readString(workflow))
                .as(workflow + " must not auto-cancel in-flight runs")
                .doesNotContain("cancel-in-progress: true");
        }
    }

    @Test
    void commonBuildReportsCoverageWithoutTargetsOrArtifacts() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("name: Core tests · Linux x64")
            .contains("name: CLI tests · ${{ matrix.shard }} · Linux x64")
            .contains("-Dexec.skip=true -Dtest='!Cli*IntegrationTest,!CliExternalProbeAcceptanceIntegrationTest' verify")
            .contains("-Dexec.skip=true -Dtest='${{ matrix.test-selector }}' verify")
            .contains("Report coverage")
            .contains("JaCoCo {counter_type.lower()} coverage: {covered}/{total} = {ratio:.2%}")
            .doesNotContain(
                "JAVAN_COVERAGE_SOFT_TARGET",
                "Soft target:",
                "Upload coverage artifact",
                "jacoco-core-linux-x64",
                "jacoco-cli-integration-${{ matrix.shard }}"
            );
    }

    @Test
    void pomKeepsNonBlockingCoverageAndBoundedParallelExecutionContracts() throws Exception {
        assertThat(Files.readString(POM))
            .contains("<forkCount>2</forkCount>")
            .contains("jacoco-surefire-${surefire.forkNumber}.exec")
            .contains("<include>jacoco-surefire-*.exec</include>")
            .doesNotContain(
                "<goal>check</goal>",
                "javan.coverage.",
                "javan.jacoco.",
                "junit.jupiter.execution.parallel",
                "<reuseForks>true</reuseForks>",
                "<include>**/*Test.java</include>"
            );
    }

    @Test
    void localMavenVerifyRefreshesCompatibilityStatusWhileCiSkipsIt() throws Exception {
        assertThat(Files.readString(POM))
            .contains("<id>refresh-compatibility-status</id>")
            .contains("<mainClass>javan.compat.CompatibilityStatusRefresh</mainClass>")
            .contains("<argument>${java.version}</argument>")
            .doesNotContain("javan.compatibility.");
        assertThat(Files.readString(BUILD_COMMON))
            .contains("-Dexec.skip=true")
            .doesNotContain(
                "verify-compatibility-status:",
                "Set up canonical compatibility JDK",
                "javan.compatibility."
            );
    }

    @Test
    void matricesStopSiblingWorkAfterFailure() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("fail-fast: true")
            .doesNotContain("fail-fast: false");
    }

    @Test
    void windowsPlatformProofEnablesLongPathsBeforeCheckout() throws Exception {
        assertThat(Files.readString(PLATFORM_PROOF))
            .contains("name: Enable Git long paths")
            .contains("git config --system core.longpaths true");
    }

    @Test
    void normalJobsReadJavaAndMavenFromTheProject() throws Exception {
        for (final Path workflow : List.of(BUILD_COMMON, NATIVE_PROOF, PLATFORM_PROOF, PUBLISH_CENTRAL)) {
            assertThat(Files.readString(workflow))
                .as(workflow + " must use the project Java and build-tool declaration")
                .contains("YunaBraska/java-info-action@11a434ffbf6bb3357363d1933be71a4076a90a6b # 3")
                .contains("java-version: ${{ steps.java_info.outputs.java_version }}");
        }
        assertThat(Files.readString(BUILD_COMMON))
            .contains("./mvnw.cmd -q")
            .doesNotContain("java-version: '25'", "java-version: '25.0.1'", "steps.java_info.outputs.cmd");
        assertThat(Files.readString(PLATFORM_PROOF))
            .contains("shell: bash")
            .contains("./mvnw -q")
            .doesNotContain("steps.java_info.outputs.cmd");
    }

    @Test
    void commonBuildKeepsAllRequestedPlatformRowsExplicit() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("target: linux-x64", "os: ubuntu-24.04")
            .contains("target: linux-arm64", "os: ubuntu-24.04-arm")
            .contains("target: macos-x64", "os: macos-15-intel")
            .contains("target: macos-arm64", "os: macos-15")
            .contains("target: windows-x64", "os: windows-2025")
            .contains("target: windows-arm64", "os: windows-11-arm")
            .contains("enabled: true")
            .contains("Temurin 25 is unavailable on the GitHub-hosted Windows ARM64 runner")
            .contains("enabled: ${{ matrix.enabled }}");
        assertThat(Files.readString(PLATFORM_PROOF)).contains("if: inputs.enabled");
    }

    @Test
    void mainWorkflowSkipsReleaseMetadataPushesBeforeCommonBuild() throws Exception {
        assertThat(Files.readString(BUILD_MERGE))
            .contains("github.event_name != 'push'")
            .contains("chore: release ")
            .contains("chore: prepare binary release repo");
    }

    @Test
    void nativePackagingKeepsSlowMacOsAndUnsupportedWindowsRowsVisibleButDisabled() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("target: macos-x64", "os: macos-15-intel")
            .contains("target: macos-aarch64", "os: macos-15")
            .contains("target: windows-x64", "os: windows-2025")
            .contains("target: windows-aarch64", "os: windows-11-arm")
            .contains("historical slower architecture lane")
            .contains("local self-host proof exceeded the 45-minute job projection")
            .contains("enabled: false");
    }

    @Test
    void mavenCentralWorkflowIsPresentButCannotPublish() throws Exception {
        assertThat(Files.readString(PUBLISH_CENTRAL))
            .contains("if: ${{ false }}")
            .contains("GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}")
            .contains("GPG_SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}")
            .contains("OSSH_PASS: ${{ secrets.OSSH_PASS }}")
            .contains("OSSH_USER: ${{ secrets.OSSH_USER }}")
            .contains("name: build-workspace")
            .contains("Restore Maven wrapper")
            .contains("./mvnw -B -Ppublish")
            .contains("-Dproject.build.outputTimestamp=\"$BUILD_OUTPUT_TIMESTAMP\"")
            .contains("-DskipTests deploy");
        assertThat(Files.readString(POM))
            .contains("<id>publish</id>")
            .contains("<artifactId>maven-source-plugin</artifactId>")
            .contains("<artifactId>maven-javadoc-plugin</artifactId>")
            .contains("<artifactId>maven-gpg-plugin</artifactId>")
            .contains("<artifactId>central-publishing-maven-plugin</artifactId>")
            .contains("<publishingServerId>central</publishingServerId>")
            .contains("https://central.sonatype.com/repository/maven-snapshots")
            .contains("https://ossrh-staging-api.central.sonatype.com/service/local");
    }

    @Test
    void everyExternalActionUsesAnImmutableCommitSha() throws Exception {
        for (final Path workflow : workflowFiles()) {
            for (final String line : Files.readAllLines(workflow)) {
                final String trimmed = line.trim();
                if (!trimmed.startsWith("uses: ") || trimmed.startsWith("uses: ./")) {
                    continue;
                }
                assertThat(trimmed)
                    .as(workflow + " must pin external action: " + trimmed)
                    .matches("uses: [^@]+@[0-9a-f]{40} # .+");
            }
        }
    }

    @Test
    void manualPublicationRequiresMainAndReusesTheCommonBuild() throws Exception {
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .contains("if [ \"$GITHUB_REF_NAME\" != \"main\" ]")
            .contains("uses: ./.github/workflows/build-common.yml")
            .contains("prepare_publication: true")
            .contains("format('v{0}', needs.build.outputs.resolved_version)")
            .doesNotContain("release_strategy:", "build_version:", "inputs.tag", "plan:");
        assertThat(Files.readString(BUILD_COMMON))
            .contains("name: build-workspace")
            .contains("prepare-publication:")
            .contains("version=$(date -u +%Y.%m.%d)")
            .doesNotContain("publication_version", "release_strategy", "build_version");
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

    @Test
    void dependabotGroupsWeeklyWorkflowAndMavenUpdates() throws Exception {
        assertThat(Files.readString(DEPENDABOT))
            .contains("version: 2")
            .contains("package-ecosystem: github-actions")
            .contains("package-ecosystem: maven")
            .contains("interval: weekly")
            .contains("day: monday")
            .contains("workflow-dependencies:")
            .contains("maven-dependencies:")
            .contains("- \"*\"");
    }

    private static List<Path> workflowFiles() throws Exception {
        try (Stream<Path> files = Files.list(WORKFLOW_ROOT)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
