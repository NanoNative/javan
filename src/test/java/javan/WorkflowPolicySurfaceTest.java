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
    private static final Path MAINTENANCE_WORKFLOW = Path.of(".github/workflows/maintenance.yml");
    private static final Path CONTAINER_IMAGES_WORKFLOW = Path.of(".github/workflows/container-images.yml");
    private static final Path PUBLISH_CENTRAL = Path.of(".github/workflows/publish-central.yml");
    private static final Path DEPENDABOT = Path.of(".github/dependabot.yml");
    private static final Path JUNIT_PLATFORM_PROPERTIES = Path.of("src/test/resources/junit-platform.properties");
    private static final Path POM = Path.of("pom.xml");
    private static final Path WORKFLOW_ROOT = Path.of(".github/workflows");
    private static final Path INSTALL_EXTERNAL_PROBES =
        Path.of(".github/scripts/install-external-probe-artifacts.sh");
    private static final Path LIST_EXTERNAL_PROBES =
        Path.of(".github/scripts/list-external-probe-artifacts.sh");

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
    void repositoryOwnedOutputsAreAlsoVisibleInStepLogs() throws Exception {
        for (final Path workflow : workflowFiles()) {
            final String source = Files.readString(workflow);
            assertThat(source)
                .as(workflow + " must print every repository-owned GITHUB_OUTPUT payload")
                .doesNotContain(">> \"$GITHUB_OUTPUT\"");
            if (source.contains("$GITHUB_OUTPUT")) {
                assertThat(source).contains("| tee -a \"$GITHUB_OUTPUT\"");
            }
        }
    }

    @Test
    void commonBuildGeneratesCoverageWithoutWorkflowSummariesOrArtifacts() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("name: core_linux_x64")
            .contains("name: ${{ matrix.label }}")
            .contains("-Dexec.skip=true -Pquick verify")
            .contains("-Dgroups='${{ matrix.suite }}'")
            .contains("-Dtest='${{ steps.tests.outputs.test_selector }}' verify")
            .doesNotContain(
                "JAVAN_COVERAGE_SOFT_TARGET",
                "Soft target:",
                "GITHUB_STEP_SUMMARY",
                "name: \"📊 Coverage",
                "Upload coverage artifact",
                "jacoco-core-linux-x64",
                "jacoco-cli-integration-${{ matrix.worker_index }}"
            );
    }

    @Test
    void workflowsUseProfilesOrTagsInsteadOfLiteralTestSelectors() throws Exception {
        for (final Path workflow : workflowFiles()) {
            final String source = Files.readString(workflow);
            assertThat(source)
                .as(workflow + " must not own Java test class or method selectors")
                .doesNotContain("!Cli*IntegrationTest", "test-selector:");
            for (final String line : Files.readAllLines(workflow)) {
                if (line.contains("-Dtest=")) {
                    assertThat(line)
                        .as(workflow + " must obtain Maven test selectors from the suite planner")
                        .contains("${{ steps.tests.outputs.test_selector }}");
                }
            }
        }
        assertThat(Files.readString(PLATFORM_PROOF)).contains("-Dgroups=platform test");
        assertThat(Files.readString(BUILD_COMMON))
            .contains("-Dgroups=windows")
            .contains("javan.testing.CiTestWorkerPlanner");
    }

    @Test
    void workflowsReuseOwnedScriptsAndKeepPrivateProbeDiscoveryTogether() throws Exception {
        assertThat(Files.readString(CONTAINER_IMAGES_WORKFLOW))
            .contains("sh .github/scripts/verify-image.sh")
            .doesNotContain("verify_image()");
        assertThat(LIST_EXTERNAL_PROBES).doesNotExist();
        assertThat(Files.readString(INSTALL_EXTERNAL_PROBES))
            .contains("groupId=", "artifactId=", "version=");
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
    void matricesRetainSiblingEvidenceAfterFailure() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("fail-fast: false")
            .doesNotContain("fail-fast: true");
    }

    @Test
    void windowsPlatformProofEnablesLongPathsBeforeCheckout() throws Exception {
        assertThat(Files.readString(PLATFORM_PROOF))
            .contains("name: \"🪟 Longpaths")
            .contains("git config --system core.longpaths true");
    }

    @Test
    void normalJobsReadJavaAndMavenFromTheProject() throws Exception {
        for (final Path workflow : List.of(BUILD_COMMON, NATIVE_PROOF, PLATFORM_PROOF, PUBLISH_CENTRAL)) {
            assertThat(Files.readString(workflow))
                .as(workflow + " must use the project Java and build-tool declaration")
                .contains("uses: YunaBraska/java-info-action@")
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
    void mainWorkflowPublishesSnapshotWithoutReleaseMetadataCommits() throws Exception {
        assertThat(Files.readString(BUILD_MERGE))
            .contains("uses: ./.github/workflows/publish-github-packages.yml")
            .contains("snapshot: true")
            .doesNotContain("chore: release ", "git tag", "gh release create");
    }

    @Test
    void nativePackagingEnablesMacArmAndKeepsUnsupportedRowsVisible() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("target: macos-x64", "os: macos-15-intel")
            .contains("target: macos-aarch64", "os: macos-15")
            .contains("target: windows-x64", "os: windows-2025")
            .contains("target: windows-aarch64", "os: windows-11-arm")
            .contains("historical slower architecture lane")
            .contains("label: package_mac_arm64\n            enabled: true")
            .contains("enabled: false");
        assertThat(Files.readString(NATIVE_PROOF))
            .contains("JAVAN_HEAP_LIMIT_BYTES: ${{ startsWith(inputs.target, 'macos-') && '1073741824' || '2147483648' }}");
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
            .contains("name: \"🧰 Wrapper\"")
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
            .contains("gh release create \"$RELEASE_VERSION\"")
            .contains("--target \"$TARGET_SHA\"")
            .doesNotContain("release_strategy:", "build_version:", "inputs.tag", "plan:");
        assertThat(Files.readString(BUILD_COMMON))
            .contains("name: build-workspace")
            .contains("prepare-publication:")
            .contains("version=$(date -u '+%Y.%m.%d' | sed -E 's/\\.0+([0-9])/\\.\\1/g')")
            .doesNotContain("publication_version", "release_strategy", "build_version");
    }

    @Test
    void weeklyWrapperMaintenanceUsesSharedWorkflowWithoutRepositoryPat() throws Exception {
        assertThat(Files.readString(MAINTENANCE_WORKFLOW))
            .contains("cron: '0 6 * * 0'")
            .contains("uses: YunaBraska/YunaBraska/.github/workflows/wc_java_update_maven_wrapper.yml@")
            .contains("contents: write", "pull-requests: write", "actions: write")
            .contains("github.event_name == 'workflow_dispatch' && inputs.dry_run || false")
            .doesNotContain("PAT", "BOT_TOKEN", "CI_TOKEN", "secrets:");
    }

    @Test
    void junitPlatformKeepsParallelExecutionEnabledByDefault() throws Exception {
        assertThat(Files.readString(JUNIT_PLATFORM_PROPERTIES))
            .contains("junit.jupiter.execution.parallel.enabled = true")
            .contains("junit.jupiter.execution.parallel.mode.default = concurrent")
            .contains("junit.jupiter.execution.parallel.mode.classes.default = concurrent")
            .contains("junit.jupiter.execution.parallel.config.strategy = dynamic")
            .contains("junit.jupiter.execution.parallel.config.executor-service = FORK_JOIN_POOL")
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
