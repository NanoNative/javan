package javan;

import javan.testing.TestSuite.ExternalTest;
import javan.testing.TestSuite.NativeTest;
import javan.testing.TestSuite.PackagingTest;
import javan.testing.TestSuite.PlatformTest;
import javan.testing.TestSuite.WindowsCompatibilityProof;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class CiParallelWorkflowSurfaceTest {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path RELEASE = Path.of(".github/workflows/release.yml");
    private static final Path NATIVE_PROOF = Path.of(".github/workflows/native-proof.yml");
    private static final Path LINUX_PACKAGES = Path.of(".github/scripts/install-linux-packages.sh");
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
    void pullRequestWorkflowExposesOneCompletionGateForBranchProtection() throws Exception {
        assertThat(Files.readString(BUILD_PR))
            .contains(
                "  complete:",
                "    name: Complete",
                "    needs: verify",
                "    if: ${{ always() }}",
                "          VERIFY_RESULT: ${{ needs.verify.result }}",
                "        run: test \"$VERIFY_RESULT\" = success"
            );
    }

    @Test
    void pullRequestAndMainWorkflowsQueueRunsWithoutCancellation() throws Exception {
        for (final Path workflow : java.util.List.of(BUILD_PR, BUILD_MERGE)) {
            assertThat(Files.readString(workflow))
                .contains(
                    "concurrency:",
                    "queue: max",
                    "cancel-in-progress: false"
                );
        }
    }

    @Test
    void pullRequestsUseGenerationTwoWhileSnapshotsAndReleasesUseGenerationThree() throws Exception {
        final String common = Files.readString(BUILD_COMMON);
        assertThat(Files.readString(BUILD_PR))
            .contains("bootstrap_generation: 2")
            .doesNotContain("package_timeout_minutes:");
        assertThat(Files.readString(BUILD_MERGE))
            .contains("bootstrap_generation: 3")
            .doesNotContain("package_timeout_minutes:");
        assertThat(Files.readString(RELEASE))
            .contains("bootstrap_generation: 3")
            .doesNotContain("package_timeout_minutes:");
        assertThat(common)
            .contains("bootstrap_generation:")
            .contains("bootstrap_generation: ${{ inputs.bootstrap_generation }}")
            .contains("timeout_minutes: ${{ inputs.bootstrap_generation == 3 && 90 || 60 }}")
            .doesNotContain("package_timeout_minutes:")
            .contains("package_scope: ${{ inputs.prepare_publication && matrix.enabled && 'full' || 'bootstrap' }}")
            .contains("  linux-package-generation2:", "name: package_linux_x64")
            .contains("if: inputs.bootstrap_generation == 2")
            .contains("  linux-package-generation3:", "if: inputs.bootstrap_generation == 3")
            .contains("  linux-package-arm64:", "name: package_linux_arm64")
            .doesNotContain("sanitizer-scope: ${{ inputs.prepare_publication && 'full' || 'platform-smoke' }}")
            .contains("enabled: ${{ inputs.prepare_publication }}");
        final String macPackage = common.substring(
            common.indexOf("  macos-package-self-host:"),
            common.indexOf("  platform-smoke:")
        );
        assertThat(macPackage).contains(
            "package_scope: ${{ inputs.snapshot && 'bootstrap' || inputs.prepare_publication && 'full' || 'bootstrap' }}"
        );
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
    void commonBuildDoesNotCancelIndependentMatrixEvidence() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("fail-fast: false")
            .doesNotContain("fail-fast: true");
    }

    @Test
    void workflowsUseTheDefaultMavenRepositoryWithoutPluginDiscovery() throws Exception {
        for (final Path workflow : java.util.List.of(BUILD_COMMON, NATIVE_PROOF)) {
            assertThat(Files.readString(workflow))
                .contains("${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}")
                .doesNotContain("help:evaluate -Dexpression=settings.localRepository");
        }
    }

    @Test
    void linuxPackageSetupIsSharedAndBoundedBelowTheJobTimeout() throws Exception {
        final String installer = Files.readString(LINUX_PACKAGES);

        assertThat(installer)
            .contains(
                "for attempt in 1 2 3; do",
                "timeout -k 15s 4m sudo apt-get",
                "Acquire::http::Timeout=20",
                "Acquire::https::Timeout=20",
                "Acquire::Retries=2",
                "command -v cc",
                "command -v x86_64-w64-mingw32-gcc",
                "dpkg-query -W -f='${db:Status-Status}'",
                "if [ -z \"$missing_packages\" ]; then",
                "/etc/apt/apt-mirrors.txt",
                "^https?://(archive|ports)\\.ubuntu\\.com",
                "Linux package installation failed after 3 attempts."
            );
        for (final Path workflow : java.util.List.of(BUILD_COMMON, NATIVE_PROOF)) {
            assertThat(Files.readString(workflow))
                .contains("sh .github/scripts/install-linux-packages.sh")
                .doesNotContain("sudo apt-get -o Acquire::ForceIPv4=true update");
        }
        assertThat(Files.readString(BUILD_COMMON))
            .contains("sh .github/scripts/install-linux-packages.sh build-essential")
            .doesNotContain("install-linux-packages.sh build-essential mingw-w64");
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
            .contains(
                "name: ${{ matrix.label }}",
                "runs-on: ${{ matrix.os }}",
                "os: windows-2025",
                "label: runtime_win_x64",
                "msystem: MINGW64",
                "compiler: mingw-w64-x86_64-gcc",
                "toolchain: mingw64"
            )
            .contains("./mvnw.cmd -q -Dgroups=windows-compatibility test")
            .doesNotContain(
                "worker_index:",
                "worker_count:",
                "CiTestWorkerPlanner",
                "-Dtest=",
                "test-selector:",
                "RuntimeFilesTest#"
            );

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
            .filter(method -> method.isAnnotationPresent(WindowsCompatibilityProof.class))
            .map(method -> method.getName()))
            .containsExactlyInAnyOrder(
                "writeEmitsPlatformRecursiveRuntimeLockForSharedHeapState",
                "writeProvidesNativeWindowsProcessExecution",
                "runtimeWindowsProcessUsesUtf8WorkingDirectoryUnderGcStress",
                "runtimeWindowsPathsPreserveDriveRootsAndSeparators",
                "runtimeWindowsPathToAbsoluteUsesUtf8CurrentDirectory",
                "generatedRuntimeCrossCompilesToWindowsPeWhenMinGwIsAvailable",
                "generatedRuntimeExecutesBasicWindowsProbeWhenHostCompilerIsAvailable",
                "secureRandomFillsByteArraysFromOsEntropy",
                "randomUuidUsesVersionFourVariantTwoAndCanonicalText",
                "basicBase64CodecHandlesPaddingBinaryDataAndStrictFailures",
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
            .contains("windows-*) sh .github/scripts/install-linux-packages.sh build-essential mingw-w64 ;;")
            .contains("*) sh .github/scripts/install-linux-packages.sh build-essential ;;");
        assertThat(Files.readString(LINUX_PACKAGES))
            .contains("install -y --fix-missing \"$@\"");

        final var lines = Files.readAllLines(Path.of(".github/scripts/sanitizer-suite.sh"));
        final var projects = new ArrayList<Path>();
        for (final String line : lines) {
            final String command = line.strip();
            if (command.matches("run_(smoke|heap_smoke|gc_smoke|stress_smoke|failure|allocation_failure|gc_failure) .*")) {
                final String project = command.substring(command.lastIndexOf(' ') + 1);
                if (project.matches("[a-z0-9][a-z0-9-]*")) {
                    projects.add(Path.of("src/test/resources/projects/native-profile", project));
                }
            } else if (command.contains("sh .github/scripts/sanitizer-")
                && command.contains("src/test/resources/projects/")
                && !command.contains("$")) {
                projects.add(Path.of(command.substring(command.lastIndexOf(' ') + 1)));
            }
        }
        assertThat(projects).hasSize(81).doesNotHaveDuplicates().allMatch(Files::isDirectory);
        assertThat(lines.stream().filter(line -> line.contains("sanitizer-self-host-smoke.sh")))
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
            .contains("  macos-package-self-host:", "name: package_mac_arm64", "label: package_mac_arm64")
            .contains("native package self-host proof is incomplete")
            .contains("proof: package-self-host")
            .contains("enabled: ${{ matrix.enabled }}");
    }

    @Test
    void armGenerationThreePackagesReuseLinuxX64GeneratedC() throws Exception {
        final String workflow = Files.readString(BUILD_COMMON);
        final String linuxArmPackage = workflow.substring(
            workflow.indexOf("  linux-package-arm64:"),
            workflow.indexOf("  native-package-self-host:")
        );
        final String macosPackage = workflow.substring(
            workflow.indexOf("  macos-package-self-host:"),
            workflow.indexOf("  platform-smoke:")
        );

        for (final String packageJob : java.util.List.of(linuxArmPackage, macosPackage)) {
            assertThat(packageJob)
                .contains("- linux-package-generation3")
                .contains("bootstrap-source-linux-x64")
                .contains("timeout_minutes: 90");
        }
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
            .contains("selector_file=$(mktemp)")
            .contains("> \"$selector_file\"")
            .contains("selector=$(cat \"$selector_file\")")
            .contains("test_selector=%s\\n")
            .contains("Timings [native_${{ matrix.worker_index }}]")
            .contains("name: native-test-timings-${{ matrix.worker_index }}-linux-x64")
            .contains("path: target/surefire-reports/TEST-*.xml")
            .contains("retention-days: 14")
            .contains("if-no-files-found: error")
            .contains("max-parallel: 8")
            .doesNotContain("matrix.test-selector", "CliJdkSemanticsIntegrationTest#", "selector=$(./mvnw");

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
            .contains("This is a temporary compatibility proof, not a Windows-only test category;");
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
            .contains("./mvnw -Dgroups=windows-compatibility test");
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
            .contains("""
                  publish-central:
                    name: Central
                    needs:
                      - verify
                    if: ${{ false }}
                    permissions:
                      actions: read
                      contents: read
                      deployments: write
                    uses: ./.github/workflows/publish-central.yml
                """);
        assertThat(Files.readString(RELEASE))
            .contains("central:")
            .contains("if: ${{ false }}")
            .contains("uses: ./.github/workflows/publish-central.yml");
    }
}
