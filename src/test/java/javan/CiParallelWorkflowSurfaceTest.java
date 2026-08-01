package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

final class CiParallelWorkflowSurfaceTest {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path RELEASE = Path.of(".github/workflows/release.yml");
    private static final Path PLATFORM_PROOF = Path.of(".github/workflows/platform-proof.yml");
    private static final Path PUBLISH_CENTRAL = Path.of(".github/workflows/publish-central.yml");

    @Test
    void workflowJobAndStepNamesRenderAsSingleTokens() throws Exception {
        try (Stream<Path> workflows = Files.list(Path.of(".github/workflows"))) {
            for (final Path workflow : workflows.filter(path -> path.toString().endsWith(".yml")).toList()) {
                for (final String line : Files.readAllLines(workflow)) {
                    if (line.startsWith("            label: ")) {
                        assertThat(line.substring(line.indexOf("label: ") + 7))
                            .as("single-token matrix label in %s: %s", workflow, line)
                            .matches("[A-Za-z0-9_]+");
                    }
                    if (!line.startsWith("name: ")
                        && !line.startsWith("    name: ")
                        && !line.startsWith("      - name: ")) {
                        continue;
                    }
                    final String name = line.substring(line.indexOf("name: ") + 6)
                        .replaceAll("\\$\\{\\{[^}]+}}", "value");
                    assertThat(name)
                        .as("single-token display name in %s: %s", workflow, line)
                        .matches("[A-Za-z0-9_]+");
                }
            }
        }
    }

    @Test
    void pullRequestAndMainWorkflowsDelegateToOneCommonBuild() throws Exception {
        assertThat(Files.readString(BUILD_PR))
            .contains("pull_request:")
            .contains("uses: ./.github/workflows/build-common.yml");
        assertThat(Files.readString(BUILD_MERGE))
            .contains("push:")
            .contains("branches:")
            .contains("- main")
            .contains("uses: ./.github/workflows/build-common.yml");
        assertThat(Files.readString(RELEASE))
            .contains("workflow_dispatch:")
            .contains("uses: ./.github/workflows/build-common.yml");
    }

    @Test
    void pullRequestsUseGenerationTwoWhileSnapshotsAndReleasesUseGenerationThree() throws Exception {
        assertThat(Files.readString(BUILD_PR))
            .contains("bootstrap_generation: 2")
            .contains("package_timeout_minutes: 360");
        assertThat(Files.readString(BUILD_MERGE))
            .contains("bootstrap_generation: 3")
            .contains("package_timeout_minutes: 360");
        assertThat(Files.readString(RELEASE))
            .contains("bootstrap_generation: 3")
            .contains("package_timeout_minutes: 360");
        assertThat(Files.readString(BUILD_COMMON))
            .contains("bootstrap_generation:")
            .contains("bootstrap_generation: ${{ inputs.bootstrap_generation }}")
            .contains("package_scope: ${{ inputs.prepare_publication && 'full' || 'bootstrap' }}")
            .contains("sanitizer-scope: ${{ inputs.prepare_publication && 'full' || 'platform-smoke' }}")
            .contains("enabled: ${{ inputs.prepare_publication }}");
        assertThat(Files.readString(Path.of(".github/workflows/native-proof.yml")))
            .contains("JAVAN_BOOTSTRAP_GENERATION: ${{ inputs.bootstrap_generation }}")
            .contains("JAVAN_PACKAGE_PROOF_SCOPE: ${{ inputs.package_scope }}");
    }

    @Test
    void commonBuildSplitsLongNativeProofsIntoIndependentJobs() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("verify-core:")
            .contains("verify-cli-integration:")
            .contains("native-acceptance:")
            .contains("native-sanitizer:")
            .contains("native-package-self-host:")
            .contains("platform-smoke:")
            .contains("windows-runtime-smoke:")
            .doesNotContain("native-smoke:");
    }

    @Test
    void sanitizerProofsAreDurationBoundedWithoutDroppingTheFullSuite() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("scope: baseline")
            .contains("scope: self_host")
            .contains("scope: gc_roots")
            .contains("scope: gc_values")
            .contains("scope: runtime_containers")
            .contains("scope: temporary_roots")
            .contains("scope: failure_exceptions")
            .contains("scope: failure_limits");

        final var invocations = Files.readAllLines(Path.of(".github/scripts/sanitizer-suite.sh")).stream()
            .filter(line -> line.contains("sh .github/scripts/sanitizer-"))
            .toList();
        assertThat(invocations).hasSize(78);
        assertThat(invocations.stream().filter(line -> line.contains("src/test/resources/projects/")).distinct())
            .hasSize(77);
        assertThat(invocations.stream().filter(line -> line.contains("sanitizer-self-host-smoke.sh")))
            .hasSize(1);
    }

    @Test
    void commonBuildRunsAllRequestedOperatingSystemArchitecturesInParallel() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("name: ${{ matrix.label }}")
            .contains("max-parallel: 6")
            .contains("target: linux-x64", "os: ubuntu-24.04")
            .contains("target: linux-arm64", "os: ubuntu-24.04-arm")
            .contains("target: macos-x64", "os: macos-15-intel")
            .contains("target: macos-arm64", "os: macos-15")
            .contains("target: windows-x64", "os: windows-2025")
            .contains("target: windows-arm64", "os: windows-11-arm")
            .contains("enabled: true")
            .contains("uses: ./.github/workflows/platform-proof.yml")
            .contains("enabled: ${{ matrix.enabled }}");
        assertThat(Files.readString(PLATFORM_PROOF)).contains("if: inputs.enabled");
    }

    @Test
    void nativeArtifactsKeepEveryPlatformRowWhenSlowOrUnsupportedTargetsAreDisabled() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("target: linux-x64", "target: linux-aarch64")
            .contains("target: macos-x64", "target: macos-aarch64")
            .contains("target: windows-x64", "target: windows-aarch64")
            .contains("historical slower architecture lane")
            .contains("local self-host proof exceeded the 45-minute job projection")
            .contains("native linker and process runtime are incomplete")
            .contains("proof: package-self-host")
            .contains("enabled: ${{ matrix.enabled }}");
    }

    @Test
    void cliSuitesUseEightIndependentDurationBalancedShards() throws Exception {
        final String workflow = Files.readString(BUILD_COMMON);
        assertThat(workflow)
            .contains("shard: cli-general-heavy")
            .contains("shard: cli-general-rest")
            .contains("shard: cli-jdk-map")
            .contains("shard: cli-jdk-set-plus")
            .contains("shard: cli-jdk-other-a")
            .contains("shard: cli-jdk-other-b")
            .contains("shard: cli-runtime-translation")
            .contains("shard: cli-thread-package-probes")
            .contains("max-parallel: 8");

        final Pattern methodPattern = Pattern.compile("^    void ([A-Za-z0-9_]+)\\(");
        final Set<String> methods = new HashSet<>();
        for (final String line : Files.readAllLines(Path.of("src/test/java/javan/CliJdkSemanticsIntegrationTest.java"))) {
            final var matcher = methodPattern.matcher(line);
            if (matcher.find()) {
                methods.add(matcher.group(1));
            }
        }

        final Set<String> assigned = new HashSet<>();
        final var jdkSelectors = workflow.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("CliJdkSemanticsIntegrationTest#"))
            .toList();
        assertThat(jdkSelectors).hasSize(4);
        for (final String selector : jdkSelectors) {
            final String methodSelector = selector.substring(selector.indexOf('#') + 1).split(",", 2)[0];
            final var prefixes = Stream.of(methodSelector.split("\\+"))
                .map(prefix -> prefix.replace("*", ""))
                .toList();
            final Set<String> selected = new HashSet<>();
            methods.stream()
                .filter(method -> prefixes.stream().anyMatch(method::startsWith))
                .forEach(selected::add);
            assertThat(selected).as("non-empty JDK selector %s", selector).isNotEmpty();
            final Set<String> overlap = new HashSet<>(selected);
            overlap.retainAll(assigned);
            assertThat(overlap).as("disjoint JDK selector %s", selector).isEmpty();
            assigned.addAll(selected);
        }
        assertThat(assigned).containsExactlyInAnyOrderElementsOf(methods);
    }

    @Test
    void mavenCentralPublishingRemainsPresentAndHardDisabled() throws Exception {
        assertThat(PUBLISH_CENTRAL).isRegularFile();
        assertThat(Files.readString(PUBLISH_CENTRAL))
            .contains("name: Central")
            .contains("if: ${{ false }}")
            .contains("GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}")
            .contains("GPG_SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}")
            .contains("OSSH_PASS: ${{ secrets.OSSH_PASS }}")
            .contains("OSSH_USER: ${{ secrets.OSSH_USER }}")
            .contains("name: build-workspace")
            .contains("gpg-private-key: ${{ secrets.GPG_SIGNING_KEY }}")
            .contains("-Dproject.build.outputTimestamp=\"$BUILD_OUTPUT_TIMESTAMP\"")
            .contains("-DskipTests deploy");
        assertThat(Files.readString(BUILD_MERGE))
            .contains("publish-central:")
            .contains("if: ${{ false }}")
            .contains("uses: ./.github/workflows/publish-central.yml");
        assertThat(Files.readString(RELEASE))
            .contains("publish-central:")
            .contains("if: ${{ false }}")
            .contains("uses: ./.github/workflows/publish-central.yml");
    }
}
