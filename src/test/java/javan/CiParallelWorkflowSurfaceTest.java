package javan;

import javan.testing.TestSuite.ExternalTest;
import javan.testing.TestSuite.NativeTest;
import javan.testing.TestSuite.PackagingTest;
import javan.testing.TestSuite.PlatformTest;
import javan.testing.TestSuite.WindowsTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class CiParallelWorkflowSurfaceTest {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path RELEASE = Path.of(".github/workflows/release.yml");
    private static final Path NATIVE_PROOF = Path.of(".github/workflows/native-proof.yml");
    private static final Path PLATFORM_PROOF = Path.of(".github/workflows/platform-proof.yml");
    private static final Path PUBLISH_CENTRAL = Path.of(".github/workflows/publish-central.yml");

    @Test
    void workflowAndJobNamesStayCompactWhileStepsShowActionAndContext() throws Exception {
        try (Stream<Path> workflows = Files.list(Path.of(".github/workflows"))) {
            for (final Path workflow : workflows.filter(path -> path.toString().endsWith(".yml")).toList()) {
                for (final String line : Files.readAllLines(workflow)) {
                    if (line.startsWith("            label: ")) {
                        assertThat(line.substring(line.indexOf("label: ") + 7))
                            .as("single-token matrix label in %s: %s", workflow, line)
                            .matches("[A-Za-z0-9_]+");
                    }
                    if (line.startsWith("      - name: ")) {
                        final String step = line.substring(line.indexOf("name: ") + 6);
                        assertThat(step)
                            .as("decorated single-action step name in %s: %s", workflow, line)
                            .matches("^\"\\S+ [A-Za-z0-9]+(?: \\[[^\\]]+\\])?\"$")
                            .doesNotContain("secrets.");
                        continue;
                    }
                    if (!line.startsWith("name: ") && !line.startsWith("    name: ")) {
                        continue;
                    }
                    final String name = line.substring(line.indexOf("name: ") + 6)
                        .replaceAll("\\$\\{\\{[^}]+}}", "value");
                    assertThat(name)
                        .as("single-token workflow or job name in %s: %s", workflow, line)
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
            .contains("package_scope: ${{ inputs.prepare_publication && startsWith(matrix.target, 'linux-') && 'full' || 'bootstrap' }}")
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
            .contains("cli:")
            .contains("native-acceptance:")
            .contains("native-sanitizer:")
            .contains("native-package-self-host:")
            .contains("platform-smoke:")
            .contains("windows-runtime-smoke:")
            .doesNotContain("native-smoke:");
    }

    @Test
    void platformAndWindowsProofsUseDocumentedSuitesInsteadOfOwnedSelectors() throws Exception {
        assertThat(Files.readString(PLATFORM_PROOF))
            .contains("-Dgroups=platform test")
            .doesNotContain("RuntimeFootprintReportsTest", "JdkCallSupportTest", "ClassFileReaderTest");

        final String common = Files.readString(BUILD_COMMON);
        final String windows = common.substring(
            common.indexOf("  windows-runtime-smoke:"),
            common.indexOf("  prepare-publication:")
        );
        assertThat(windows)
            .contains("worker_index: 0", "worker_index: 1", "worker_count: 2")
            .contains("javan.testing.CiTestWorkerPlanner")
            .contains("-Dgroups=windows")
            .doesNotContain("test-selector:", "RuntimeFilesTest#");

        for (final String testClass : java.util.List.of(
            "javan.reporting.RuntimeFootprintReportsTest",
            "javan.compat.JdkCallSupportTest",
            "javan.classfile.ClassFileReaderTest"
        )) {
            assertThat(Class.forName(testClass).isAnnotationPresent(PlatformTest.class))
                .as(testClass + " must remain in the platform phase")
                .isTrue();
        }
        assertThat(Stream.of(Class.forName("javan.codegen.RuntimeFilesTest").getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(WindowsTest.class))
            .map(method -> method.getName()))
            .containsExactlyInAnyOrder(
                "writeEmitsPlatformRecursiveRuntimeLockForSharedHeapState",
                "writeMarksWindowsProcessExecutionUnsupportedUntilPorted",
                "generatedRuntimeCrossCompilesToWindowsPeWhenMinGwIsAvailable",
                "generatedRuntimeExecutesBasicWindowsProbeWhenHostCompilerIsAvailable",
                "runtimeHostThreadGetsDistinctCurrentThreadAndDetachesCleanly",
                "runtimeConcurrentHostThreadsCanAttachCollectDetachWithoutLeakingRoots",
                "runtimeHostThreadRootFramesStayPublishedAcrossConcurrentGc",
                "runtimeDetachedReachableCurrentThreadClearsThreadLocalStorageOnDetach",
                "runtimeDetachedReachableCurrentThreadClearsNestedThreadLocalObjectGraphOnDetach",
                "runtimeHostThreadThreadLocalValueSurvivesConcurrentGcAndDetachesCleanly",
                "runtimeHostThreadThreadLocalObjectGraphSurvivesConcurrentGcAndDetachesCleanly",
                "runtimeHostThreadThreadLocalSiblingRemoveKeepsRetainedGraphAliveDuringConcurrentGcAndDetachesCleanly",
                "runtimeHostThreadThreadLocalOverwriteSurvivesRepeatedSafepointGcDuringMutationAndDetachesCleanly",
                "runtimeHostThreadThreadLocalRemoveAndSiblingRetentionSurviveRepeatedSafepointGcDuringMutationAndDetachesCleanly",
                "runtimeHostThreadThreadLocalMapGrowthSurvivesRepeatedSafepointGcAndDetachesCleanly",
                "runtimeThreadLifecycleInventoryTracksCurrentThreadState",
                "runtimeThreadTargetSurvivesPreStartCollectionThroughWorkerField",
                "runtimeThreadLifecycleInventoryDropsFinishedNonCurrentThreadObjectsAfterCollection",
                "runtimeCompletedThreadDoesNotRetainTargetAfterCollectionWhenWorkerStaysReachable",
                "runtimeCompletedReachableWorkerClearsThreadLocalStorageOnCompletion",
                "runtimeStartedWorkerThreadLocalObjectGraphSurvivesConcurrentGcAndCleansUpAfterJoin",
                "runtimeStartedWorkerThreadLocalOverwriteCollectsPreviousGraphDuringConcurrentGc",
                "runtimeStartedWorkerThreadLocalRemoveCollectsRemovedGraphDuringConcurrentGc",
                "runtimeStartedWorkerThreadLocalSiblingRemoveKeepsOtherGraphAliveDuringConcurrentGc",
                "runtimeStartedWorkerThreadLocalSiblingRemoveKeepsOtherGraphAliveDuringRepeatedSafepointGc",
                "runtimeStartedWorkerThreadLocalOverwriteSurvivesRepeatedParentSafepointGcDuringMutation",
                "runtimeStartedWorkerThreadLocalRemoveAndSiblingRetentionSurviveRepeatedParentSafepointGcDuringMutation",
                "runtimeSharedThreadLocalKeyRemainsThreadIsolatedAcrossWorkerRemoveAndConcurrentGc",
                "runtimeCompletedReachableWorkerClearsNestedThreadLocalObjectGraphOnCompletion",
                "runtimeParentCollectionPreservesBlockedWorkerLocalRootedObject"
            );
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
        assertThat(Files.readString(NATIVE_PROOF))
            .contains("if: runner.os == 'Linux' && inputs.proof != 'sanitizer'")
            .contains("set -- build-essential")
            .contains("windows-*) set -- \"$@\" mingw-w64 ;;")
            .contains("install -y --fix-missing \"$@\"");

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
    void nativeArtifactsKeepEveryPlatformRowAndEnableMacArmProof() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("target: linux-x64", "target: linux-aarch64")
            .contains("target: macos-x64", "target: macos-aarch64")
            .contains("target: windows-x64", "target: windows-aarch64")
            .contains("historical slower architecture lane")
            .contains("label: package_mac_arm64\n            enabled: true")
            .contains("native linker and process runtime are incomplete")
            .contains("proof: package-self-host")
            .contains("enabled: ${{ matrix.enabled }}");
    }

    @Test
    void cliSuitesUseDocumentedSuitesAndAutomaticWorkers() throws Exception {
        final String workflow = Files.readString(BUILD_COMMON);
        final String cliWorkflow = workflow.substring(
            workflow.indexOf("  cli:"),
            workflow.indexOf("  native-acceptance:")
        );
        assertThat(cliWorkflow)
            .contains("{ suite: native, worker_index: 0, worker_count: 6")
            .contains("{ suite: native, worker_index: 5, worker_count: 6")
            .contains("{ suite: packaging, worker_index: 0, worker_count: 1")
            .contains("{ suite: external, worker_index: 0, worker_count: 1")
            .contains("javan.testing.CiTestWorkerPlanner")
            .contains("Discover [${{ matrix.suite }}_${{ matrix.worker_index }}]")
            .contains("Test [${{ matrix.suite }}_${{ matrix.worker_index }}]")
            .contains("test_selector=%s\\n")
            .contains("max-parallel: 8")
            .doesNotContain("matrix.test-selector", "CliJdkSemanticsIntegrationTest#");

        try (Stream<Path> tests = Files.list(Path.of("src/test/java/javan"))) {
            final var cliTests = tests
                .filter(path -> path.getFileName().toString().matches("Cli.*IntegrationTest[.]java"))
                .toList();
            int nativeTests = 0;
            int packagingTests = 0;
            int externalTests = 0;
            for (final Path test : cliTests) {
                final String simpleName = test.getFileName().toString().replace(".java", "");
                final Class<?> testClass = Class.forName("javan." + simpleName);
                final boolean nativeTest = testClass.isAnnotationPresent(NativeTest.class);
                final boolean packagingTest = testClass.isAnnotationPresent(PackagingTest.class);
                final boolean externalTest = testClass.isAnnotationPresent(ExternalTest.class);
                assertThat(Stream.of(nativeTest, packagingTest, externalTest).filter(Boolean::booleanValue))
                    .as("exactly one test suite for %s", simpleName)
                    .hasSize(1);
                nativeTests += nativeTest ? 1 : 0;
                packagingTests += packagingTest ? 1 : 0;
                externalTests += externalTest ? 1 : 0;
            }
            assertThat(nativeTests).isEqualTo(cliTests.size() - 2);
            assertThat(packagingTests).isOne();
            assertThat(externalTests).isOne();
        }
        assertThat(Files.readString(Path.of("src/test/java/javan/testing/TestSuite.java")))
            .contains("Generates, compiles, and executes native C through the JavaN CLI.")
            .contains("Builds and verifies distributable or self-hosted JavaN packages.")
            .contains("Uses external probe artifacts, toolchains, or services.")
            .contains("Runs portable JVM-only behavior on every enabled CI operating system and architecture.")
            .contains("Compiles or executes the generated runtime with the supported Windows toolchain.");
        assertThat(Files.readString(Path.of("pom.xml")))
            .contains("<id>quick</id>", "<id>standard</id>")
            .contains("<javan.test.excluded-groups>native,packaging,external</javan.test.excluded-groups>")
            .contains("<javan.test.excluded-groups>packaging,external</javan.test.excluded-groups>");
        assertThat(Files.readString(Path.of("doc/spec/testing.md")))
            .contains("./mvnw -Pquick verify")
            .contains("./mvnw -Pstandard verify")
            .contains("./mvnw clean verify")
            .contains("./mvnw -Dgroups=native test")
            .contains("./mvnw -Dgroups=platform test")
            .contains("./mvnw -Dgroups=windows test");
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
            .contains("central:")
            .contains("if: ${{ false }}")
            .contains("uses: ./.github/workflows/publish-central.yml");
    }
}
