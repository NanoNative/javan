package javan.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ReportSummarizerTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeAcceptsProjectRoot() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("diagnostics.txt"), "No diagnostics.\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.reportsDirectory()).isEqualTo(reports);
        assertThat(summary.markdownPath()).isEqualTo(reports.resolve("report.md"));
        assertThat(summary.jsonPath()).isEqualTo(reports.resolve("report.json"));
    }

    @Test
    void writeAcceptsJavanDirectory() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("diagnostics.txt"), "No diagnostics.\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir.resolve(".javan"));

        assertThat(summary.reportsDirectory()).isEqualTo(reports);
    }

    @Test
    void writeAcceptsReportsDirectory() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("diagnostics.txt"), "No diagnostics.\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(reports);

        assertThat(summary.reportsDirectory()).isEqualTo(reports);
    }

    @Test
    void writeRejectsMissingReportsDirectory() {
        assertThatThrownBy(() -> new ReportSummarizer().write(tempDir))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(".javan/reports");
    }

    @Test
    void writeRejectsPlainReportsDirectoryOutsideJavan() throws Exception {
        final Path reports = tempDir.resolve("reports");
        Files.createDirectories(reports);

        assertThatThrownBy(() -> new ReportSummarizer().write(reports))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reports/.javan/reports");
    }

    @Test
    void writeMarksAbsentFamiliesWhenReportsDirectoryIsEmpty() throws Exception {
        final Path reports = reportsDirectory();

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("Known report families: `0` present, `0` partial, `19` absent.");
        assertThat(summary.json()).contains("\"presentFamilyCount\": 0", "\"partialFamilyCount\": 0", "\"absentFamilyCount\": 19");
    }

    @Test
    void writeMarksPartialFamilyWhenOnlyOneExpectedFileExists() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("intrinsics.md"), "# Intrinsics\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("| `intrinsics` | partial | missing `intrinsics.json`; `intrinsics.md`");
        assertThat(summary.json()).contains("\"name\": \"intrinsics\", \"status\": \"partial\"");
    }

    @Test
    void writeSummarizesProjectMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("project.json"), """
            {
              "buildTool": "PLAIN",
              "profile": "core",
              "outputName": "demo",
              "sourceFolders": ["src/main/java"],
              "resourceFolders": ["src/main/resources"],
              "classFolders": ["target/classes"],
              "classpathEntries": ["libs/a,b.jar", "libs/c.jar"],
              "warnings": ["line\\nwrapped"]
            }
            """);

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "buildTool: `PLAIN`",
            "profile: `core`",
            "outputName: `demo`",
            "sourceFolders: `1`",
            "classpathEntries: `2`",
            "warnings: `1`"
        );
    }

    @Test
    void writeDecodesEscapedProjectTextMetric() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("project.json"), """
            {
              "outputName": "line\\nreturn\\rcarriage\\ttab\\\"quote\\\\slash\\/solid"
            }
            """);

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("outputName: `line\nreturn\rcarriage\ttab\"quote\\slash/solid`");
    }

    @Test
    void writeIgnoresProjectStringWithoutClosingQuote() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("project.json"), "{\"outputName\": \"demo");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("| `project` | present | `project.json`");
        assertThat(summary.markdown()).doesNotContain("outputName:");
    }

    @Test
    void writeFindsFieldAfterNameWithoutColon() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("project.json"), """
            {
              "profile" "broken",
              "profile": "strict"
            }
            """);

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("profile: `strict`");
    }

    @Test
    void writeCountsEmptyArrayMetricAsZero() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("project.json"), """
            {
              "sourceFolders": []
            }
            """);

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("sourceFolders: `0`");
    }

    @Test
    void writeCountsWhitespaceArrayMetricAsZero() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("project.json"), """
            {
              "warnings": [
              \s
              \t
              ]
            }
            """);

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("warnings: `0`");
    }

    @Test
    void writeSummarizesDiagnosticMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("diagnostics.txt"), "error[JAVAN001]: bad\r\nwarning[JAVAN101]: maybe\n");
        Files.writeString(reports.resolve("diagnostics.json"), "{\"diagnostics\": 2}\n");
        Files.writeString(reports.resolve("diagnostics.md"), "# Diagnostics\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "| `diagnostics` | present |",
            "`diagnostics.txt`",
            "`diagnostics.json`",
            "`diagnostics.md`",
            "diagnostics: `2`",
            "errors: `1`",
            "warnings: `1`"
        );
    }

    @Test
    void writeSummarizesThreadMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("threads.json"), """
            {
              "diagnostics": 2,
              "errors": 1,
              "warnings": 1,
              "lifecycle": 1,
              "synchronization": 0,
              "concurrencyRuntime": 1,
              "blocking": 0,
              "threadStartSites": 0,
              "threadStartMethods": 0,
              "lifecycleMethods": 1,
              "blockingMethods": 0,
              "synchronizationMethods": 0,
              "concurrencyRuntimeMethods": 1,
              "unknownBlockingMethods": 1,
              "unsupportedThreadTaskMethods": 1,
              "sleepWaits": 0,
              "joinWaits": 0,
              "blockingTaskMethods": 0,
              "cpuBoundTaskMethods": 0,
              "tinyCpuTaskMethods": 0,
              "pinningRiskMethods": 0,
              "unknownTaskMethods": 1,
              "ioSignalMethods": 0,
              "taskRoots": 1,
              "threadStartRoots": 0,
              "blockingRoots": 0,
              "pinningRiskRoots": 0,
              "unsupportedRuntimeRoots": 1,
              "lifecycleRiskRoots": 0,
              "unknownRoots": 0,
              "methods": [
                {"class": "com/acme/Main", "method": "dead", "threadStartSites": 0, "lifecycleRisks": 1, "blockingWaits": 0, "synchronizationRisks": 0, "concurrencyRuntimeRisks": 1, "sleepWaits": 0, "joinWaits": 0, "estimatedInstructions": 0, "allocationSites": 0, "ioCallSites": 0, "hasLoop": false, "classification": "UNKNOWN"}
              ],
              "roots": [
                {"class": "com/acme/Main", "method": "dead", "rootKind": "UNSUPPORTED_RUNTIME", "classification": "UNKNOWN", "threadStartSites": 0, "blockingWaits": 0, "lifecycleRisks": 1, "synchronizationRisks": 0, "concurrencyRuntimeRisks": 1, "ioCallSites": 0}
              ],
              "items": [
                {"code": "JAVAN075"},
                {"code": "JAVAN177"}
              ]
            }
            """);
        Files.writeString(reports.resolve("threads.md"), "# Thread Analysis\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "| `threads` | present |",
            "`threads.json`",
            "`threads.md`",
            "diagnostics: `2`",
            "errors: `1`",
            "warnings: `1`",
            "lifecycle: `1`",
            "synchronization: `0`",
            "concurrencyRuntime: `1`",
            "blocking: `0`",
            "threadStartSites: `0`",
            "threadStartMethods: `0`",
            "lifecycleMethods: `1`",
            "blockingMethods: `0`",
            "synchronizationMethods: `0`",
            "concurrencyRuntimeMethods: `1`",
            "unknownBlockingMethods: `1`",
            "unsupportedThreadTaskMethods: `1`",
            "sleepWaits: `0`",
            "joinWaits: `0`",
            "blockingTaskMethods: `0`",
            "cpuBoundTaskMethods: `0`",
            "tinyCpuTaskMethods: `0`",
            "pinningRiskMethods: `0`",
            "unknownTaskMethods: `1`",
            "ioSignalMethods: `0`",
            "taskRoots: `1`",
            "threadStartRoots: `0`",
            "blockingRoots: `0`",
            "pinningRiskRoots: `0`",
            "unsupportedRuntimeRoots: `1`",
            "lifecycleRiskRoots: `0`",
            "unknownRoots: `0`",
            "methods: `1`",
            "roots: `1`",
            "items: `2`"
        );
    }

    @Test
    void writeSummarizesVirtualThreadMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("virtual-threads.json"), """
            {
              "status": "partial",
              "runtimeSupported": true,
              "profilingSupported": false,
              "profilingCollected": false,
              "schedulerImplemented": false,
              "carrierPoolImplemented": false,
              "threadModelImplemented": true,
              "threadLocalImplemented": true,
              "blockingIoAware": false,
              "reachableApiScan": "not-collected",
              "reachableVirtualStartSites": 0,
              "reachableVirtualStartMethods": 0,
              "reachableIsVirtualSites": 0,
              "unsupportedBuilderApis": 0,
              "unsupportedBuilderApisReachable": 0,
              "unsupportedBuilderApisUnreachable": 0,
              "unsupportedExecutorApis": 0,
              "unsupportedExecutorApisReachable": 0,
              "unsupportedExecutorApisUnreachable": 0,
              "diagnosticSource": "platform-thread-analysis-plus-virtual-builder-executor-park-slice",
              "reasonCount": 3,
              "nextGate": "land remaining builder/factory/executor introspection such as getClass() plus scheduler/carrier runtime and runtime-backed profiling counters",
              "reasons": [
                "a",
                "b",
                "c"
              ]
            }
            """);
        Files.writeString(reports.resolve("virtual-threads.md"), "# Virtual Thread Analysis\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "| `virtual-threads` | present |",
            "`virtual-threads.json`",
            "`virtual-threads.md`",
            "status: `partial`",
            "runtimeSupported: `true`",
            "profilingSupported: `false`",
            "profilingCollected: `false`",
            "schedulerImplemented: `false`",
            "carrierPoolImplemented: `false`",
            "threadModelImplemented: `true`",
            "threadLocalImplemented: `true`",
            "blockingIoAware: `false`",
            "reachableApiScan: `not-collected`",
            "reachableVirtualStartSites: `0`",
            "reachableVirtualStartMethods: `0`",
            "reachableIsVirtualSites: `0`",
            "unsupportedBuilderApis: `0`",
            "unsupportedBuilderApisReachable: `0`",
            "unsupportedBuilderApisUnreachable: `0`",
            "unsupportedExecutorApis: `0`",
            "unsupportedExecutorApisReachable: `0`",
            "unsupportedExecutorApisUnreachable: `0`",
            "diagnosticSource: `platform-thread-analysis-plus-virtual-builder-executor-park-slice`",
            "reasonCount: `3`",
            "nextGate: `land remaining builder/factory/executor introspection such as getClass() plus scheduler/carrier runtime and runtime-backed profiling counters`",
            "reasons: `3`"
        );
    }

    @Test
    void writeSummarizesRuntimeProfilingMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("runtime-profiling.json"), """
            {
              "status": "ready",
              "requested": true,
              "enabled": true,
              "collectionState": "linked-not-run",
              "reason": "Runtime profiling is linked and will collect counters when the native binary runs through a profiling-enabled launch path.",
              "disabledProfilingModules": []
            }
            """);
        Files.writeString(reports.resolve("runtime-profiling.md"), "# Runtime Profiling\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "| `runtime-profiling` | present |",
            "`runtime-profiling.json`",
            "`runtime-profiling.md`",
            "status: `ready`",
            "requested: `true`",
            "enabled: `true`",
            "collectionState: `linked-not-run`",
            "reason: `Runtime profiling is linked and will collect counters when the native binary runs through a profiling-enabled launch path.`",
            "disabledProfilingModules: `0`"
        );
    }

    @Test
    void writeSummarizesReachabilityMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("reachability.txt"), "entry: com/acme/Main.main([Ljava/lang/String;)V\r\nreachable:\n  a\n  b\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("entry: `com/acme/Main.main([Ljava/lang/String;)V`", "reachableMethods: `2`");
    }

    @Test
    void writeOmitsMissingReachabilityEntryMetric() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("reachability.txt"), "reachable:\n  a\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("reachableMethods: `1`");
        assertThat(summary.markdown()).doesNotContain("entry:");
    }

    @Test
    void writeSummarizesIntrinsicMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("intrinsics.json"), """
            {
              "intrinsics": [{"name": "Math.abs", "count": 2}, {"name": "System.nanoTime", "count": 3}],
              "unsupportedJdkCallCandidateCount": 1,
              "unsupportedJdkCallCandidates": [{"owner": "java/lang/Class"}]
            }
            """);
        Files.writeString(reports.resolve("intrinsics.md"), "# Intrinsics\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "intrinsics: `2`",
            "intrinsicCallSites: `5`",
            "unsupportedJdkCallCandidateCount: `1`",
            "unsupportedJdkCallCandidates: `1`"
        );
    }

    @Test
    void writeSummarizesExceptionMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("exceptions.json"), """
            {
              "panicSites": 2,
              "sites": [{"id": "panic-1"}, {"id": "panic-2"}]
            }
            """);
        Files.writeString(reports.resolve("exceptions.md"), "# Runtime Exceptions\n");
        Files.writeString(reports.resolve("debug-map.json"), """
            {
              "debugEntries": 2,
              "entries": [{"id": "panic-1"}, {"id": "panic-2"}]
            }
            """);

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "panicSites: `2`",
            "sites: `2`",
            "debugEntries: `2`"
        );
    }

    @Test
    void writeSummarizesOptimizationMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("optimizations.json"), """
            {
              "redundantNullChecks": 1,
              "redundantBoundsChecks": 2,
              "redundantTypeChecks": 3,
              "redundantRangeChecks": 4,
              "deadBranches": 5,
              "specializedMethods": 6,
              "skippedCandidates": 7
            }
            """);
        Files.writeString(reports.resolve("optimizations.md"), "# Optimizations\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("deadBranches: `5`", "specializedMethods: `6`", "skippedCandidates: `7`");
    }

    @Test
    void writeSummarizesResourceMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("resources.json"), """
            {
              "resourceCount": 2,
              "resources": [{"path": "a.txt", "size": 4}, {"path": "b.txt", "size": 6}]
            }
            """);
        Files.writeString(reports.resolve("resources.md"), "# Resources\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("resourceCount: `2`", "resourceBytes: `10`");
    }

    @Test
    void writeIgnoresMalformedNumberMetric() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("resources.json"), """
            {
              "resourceCount": "two"
            }
            """);
        Files.writeString(reports.resolve("resources.md"), "# Resources\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("| `resources` | present | `resources.json`");
        assertThat(summary.markdown()).doesNotContain("resourceCount:");
    }

    @Test
    void writeIgnoresMalformedArrayMetric() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("resources.json"), """
            {
              "resources": [{"size": 1}
            }
            """);
        Files.writeString(reports.resolve("resources.md"), "# Resources\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).doesNotContain("resourceBytes:");
    }

    @Test
    void writeSumsOnlyNumericArrayFields() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("resources.json"), """
            {
              "resources": [{"size": 4}, {"size": "x"}, {"other": 9}]
            }
            """);
        Files.writeString(reports.resolve("resources.md"), "# Resources\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("resourceBytes: `4`");
    }

    @Test
    void writeSummarizesLibraryBuildMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("library-build.json"), """
            {
              "abiVersion": 2,
              "stringOwnership": "input-copied-gc-managed-utf8-output-javan-owned-free-with-javan_free",
              "byteArrayOwnership": "input-copied-gc-managed-output-javan-owned-data-free-with-javan_free",
              "errorResultAbi": "abi-v2-c-owned-javanresult-try-wrappers-v1-direct-exports-compatible",
              "exceptionMapping": "caught-runtime-panic-to-last-error-limited-same-method-catch",
              "threadRuntimeRules": "parallel-host-thread-bootstrap-current-thread-interrupt-isalive-sleep-start-join-runnable-target-plus-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-and-isvirtual-no-virtual-scheduler",
              "generatedAbiTests": "c-header-compile-test",
              "inputClasses": 3,
              "inputMethods": 4,
              "reachableClassesFromExports": 2,
              "reachableMethodsFromExports": 5,
              "exportedMethods": 1,
              "artifacts": ["libdemo.a", "libdemo.dylib"],
              "artifactBytes": 42,
              "runtimeModulesLinked": ["strings"],
              "dependencyReductionMethods": 7,
              "bindings": ["demo.h", "lib.rs"]
            }
            """);
        Files.writeString(reports.resolve("library-build.md"), "# Library\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "abiVersion: `2`",
            "stringOwnership: `input-copied-gc-managed-utf8-output-javan-owned-free-with-javan_free`",
            "byteArrayOwnership: `input-copied-gc-managed-output-javan-owned-data-free-with-javan_free`",
            "errorResultAbi: `abi-v2-c-owned-javanresult-try-wrappers-v1-direct-exports-compatible`",
            "exceptionMapping: `caught-runtime-panic-to-last-error-limited-same-method-catch`",
            "threadRuntimeRules: `parallel-host-thread-bootstrap-current-thread-interrupt-isalive-sleep-start-join-runnable-target-plus-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-and-isvirtual-no-virtual-scheduler`",
            "generatedAbiTests: `c-header-compile-test`",
            "inputClasses: `3`",
            "exportedMethods: `1`",
            "artifacts: `2`",
            "bindings: `2`"
        );
    }

    @Test
    void writeSummarizesDeduplicationMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("deduplication-plan.json"), """
            {
              "runtimeModules": ["strings", "arrays"],
              "deduplicatedStringLiterals": -1,
              "arrayHelperFamilies": ["int-array"],
              "boundsCheckHelpers": ["bounds"]
            }
            """);
        Files.writeString(reports.resolve("deduplication-plan.md"), "# Dedup\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "runtimeModules: `2`",
            "deduplicatedStringLiterals: `-1`",
            "arrayHelperFamilies: `1`",
            "boundsCheckHelpers: `1`"
        );
    }

    @Test
    void writeSummarizesDependencyAndLicenseMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("dependencies.json"), """
            {
              "dependencyCount": 2,
              "presentDependencies": 2,
              "missingDependencies": 0,
              "usedDependencies": 1,
              "unusedDependencies": 1,
              "reachableDependencyClasses": 3,
              "dependencies": [{"path": "used.jar"}, {"path": "unused.jar"}]
            }
            """);
        Files.writeString(reports.resolve("dependencies.md"), "# Dependencies\n");
        Files.writeString(reports.resolve("licenses.json"), """
            {
              "licenseCount": 2,
              "knownLicenses": 1,
              "unknownLicenses": 1,
              "warningLicenses": 1,
              "blockedLicenses": 0,
              "licenses": [{"dependency": "used.jar"}, {"dependency": "unused.jar"}]
            }
            """);
        Files.writeString(reports.resolve("licenses.md"), "# Licenses\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "dependencyCount: `2`",
            "usedDependencies: `1`",
            "unusedDependencies: `1`",
            "reachableDependencyClasses: `3`",
            "licenseCount: `2`",
            "knownLicenses: `1`",
            "unknownLicenses: `1`",
            "blockedLicenses: `0`"
        );
    }

    @Test
    void writeSummarizesRuntimeFeatureMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("runtime-features.json"), """
            {
              "status": "pass",
              "containment": "system-linked",
              "optimize": "size",
              "profiling": true,
              "reachableRuntimeModules": ["core", "network", "socket"],
              "disabledRuntimeModules": ["thread-profiling"],
              "disabledReachableRuntimeModules": [],
              "disabledUnusedRuntimeModules": ["thread-profiling"],
              "unknownDisabledRuntimeModules": []
            }
            """);
        Files.writeString(reports.resolve("runtime-features.md"), "# Runtime Features\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "status: `pass`",
            "containment: `system-linked`",
            "optimize: `size`",
            "profiling: `true`",
            "reachableRuntimeModuleNames: `core, network, socket`",
            "reachableRuntimeModules: `3`",
            "disabledRuntimeModuleNames: `thread-profiling`",
            "disabledRuntimeModules: `1`",
            "disabledReachableRuntimeModules: `0`",
            "disabledUnusedRuntimeModules: `1`",
            "unknownDisabledRuntimeModules: `0`"
        );
    }

    @Test
    void writeOmitsEmptyRuntimeFeatureNameMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("runtime-features.json"), """
            {
              "reachableRuntimeModules": [],
              "disabledRuntimeModules": []
            }
            """);
        Files.writeString(reports.resolve("runtime-features.md"), "# Runtime Features\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown())
            .contains("reachableRuntimeModules: `0`", "disabledRuntimeModules: `0`")
            .doesNotContain("reachableRuntimeModuleNames:", "disabledRuntimeModuleNames:");
    }

    @Test
    void writeSummarizesRuntimeMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("runtime.json"), """
            {
              "artifactKind": "app",
              "artifacts": [{"path": ".javan/bin/demo", "bytes": 42, "linkage": "dynamic-executable"}],
              "abiSymbols": ["javan_export_com_acme_Math_add_int_int"],
              "runtimePackaging": "monolithic-c-runtime",
              "runtimeModulesIncluded": ["core", "arrays", "strings"],
              "memoryModel": "tracked-c-heap-safe-point-partial-gc",
              "allocator": "tracked-calloc-free-at-shutdown",
              "javaAllocationOwnership": "javan-owned-generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible",
              "ffiAllocationOwnership": "caller-frees-javan-owned-strings-and-byte-arrays-with-javan_free-result-diagnostics-with-javan_result_free",
              "temporaryAllocationOwnership": "javan-owned-explicit-free",
              "heapMetadata": true,
              "heapMetadataStrategy": "allocation-ledger-kind-typeid-runtimekind-mark-collectible",
              "heapAccounting": true,
              "heapReclamation": true,
              "heapReclamationScope": "generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage",
              "typeDescriptors": true,
              "objectFieldDescriptors": true,
              "frameRootInventory": true,
              "managedHeap": false,
              "gc": "partial-mark-sweep",
              "gcStrategy": "single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-boxed-primitive-wrapper-runtime-string-runtime-container-and-owned-container-storage-mark-sweep",
              "gcStress": "metadata-verify-and-safe-point-collection",
              "gcExcludedAllocationKinds": ["explicit-runtime-temporaries", "ffi-exports"],
              "runtimeContainerTraversal": "precise-rooted-runtime-container-mark-sweep",
              "ownedBufferReferenceValidation": true,
              "ownedBufferReferenceValidationScope": "list-map-stringbuilder-owned-backing-storage",
              "operandCallTemporaryRoots": true,
              "operandCallTemporaryRootModel": "generated-expression-root-frame",
              "operandCallTemporaryRootScope": ["object-call-arguments", "nested-object-call-results"],
              "operandCallTemporaryRootLifetime": "until-enclosing-generated-statement-or-return-completes",
              "allocationPathCollection": true,
              "allocationPathCollectionModel": "allocator-gc-retry-before-out-of-memory",
              "allocationPathCollectionScope": "generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage",
              "allocationFailureMode": "deterministic-native-panic",
              "statementSafePoints": true,
              "statementSafePointScope": "generated-label-and-non-terminal-statement-boundaries",
              "returnValueRoots": true,
              "protectedObjectReturns": true,
              "protectedObjectReturnScope": "single-threaded-static-return-root-through-callee-safe-point-and-frame-pop",
              "staticRootInventory": true,
              "localRootInventory": true,
              "localRootLiveness": true,
              "localRootLivenessModel": "cfg-safe-point-dead-root-clearing",
              "rootScanning": false,
              "rootModel": "generated-static-frame-return-and-expression-root-inventory-no-heap-scan",
              "threadRoots": true,
              "threadRootRegistry": true,
              "threadRootScope": "parallel-host-thread-bootstrap-live-thread-root-registry-current-thread-root-membership-and-thread-target-field-traversal",
              "threadLifecycleInventory": true,
              "threadLifecycleInventoryScope": "heap-thread-object-thread-root-registry-started-completed-active-non-current-target-current-root-and-completed-target-release-counters",
              "javaHeapAllocationsManaged": false,
              "exceptions": "panic-and-limited-same-method-catch",
              "threads": "current-thread-interrupt-state-isalive-isvirtual-entry-interrupted-sleep-start-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-parallel-host-thread-bootstrap-join-same-method-catch-thread-construction-duplicate-start-rejection-current-join-rejection-and-runnable-target-no-virtual-scheduler",
              "sanitizerInstrumentation": "not-built",
              "sanitizers": "not-enabled"
            }
            """);
        Files.writeString(reports.resolve("runtime.md"), "# Runtime\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "artifactKind: `app`",
            "artifacts: `1`",
            "artifactBytes: `42`",
            "abiSymbols: `1`",
            "runtimeModulesIncluded: `3`",
            "memoryModel: `tracked-c-heap-safe-point-partial-gc`",
            "allocator: `tracked-calloc-free-at-shutdown`",
            "javaAllocationOwnership: `javan-owned-generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible`",
            "ffiAllocationOwnership: `caller-frees-javan-owned-strings-and-byte-arrays-with-javan_free-result-diagnostics-with-javan_result_free`",
            "temporaryAllocationOwnership: `javan-owned-explicit-free`",
            "heapMetadata: `true`",
            "heapMetadataStrategy: `allocation-ledger-kind-typeid-runtimekind-mark-collectible`",
            "heapAccounting: `true`",
            "heapReclamation: `true`",
            "heapReclamationScope: `generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage`",
            "typeDescriptors: `true`",
            "objectFieldDescriptors: `true`",
            "frameRootInventory: `true`",
            "managedHeap: `false`",
            "gc: `partial-mark-sweep`",
            "gcStrategy: `single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-boxed-primitive-wrapper-runtime-string-runtime-container-and-owned-container-storage-mark-sweep`",
            "gcStress: `metadata-verify-and-safe-point-collection`",
            "gcExcludedAllocationKinds: `2`",
            "runtimeContainerTraversal: `precise-rooted-runtime-container-mark-sweep`",
            "ownedBufferReferenceValidation: `true`",
            "ownedBufferReferenceValidationScope: `list-map-stringbuilder-owned-backing-storage`",
            "operandCallTemporaryRoots: `true`",
            "operandCallTemporaryRootModel: `generated-expression-root-frame`",
            "operandCallTemporaryRootScope: `2`",
            "operandCallTemporaryRootLifetime: `until-enclosing-generated-statement-or-return-completes`",
            "allocationPathCollection: `true`",
            "allocationPathCollectionModel: `allocator-gc-retry-before-out-of-memory`",
            "allocationPathCollectionScope: `generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage`",
            "allocationFailureMode: `deterministic-native-panic`",
            "statementSafePoints: `true`",
            "statementSafePointScope: `generated-label-and-non-terminal-statement-boundaries`",
            "returnValueRoots: `true`",
            "protectedObjectReturns: `true`",
            "protectedObjectReturnScope: `single-threaded-static-return-root-through-callee-safe-point-and-frame-pop`",
            "staticRootInventory: `true`",
            "localRootInventory: `true`",
            "localRootLiveness: `true`",
            "localRootLivenessModel: `cfg-safe-point-dead-root-clearing`",
            "rootScanning: `false`",
            "rootModel: `generated-static-frame-return-and-expression-root-inventory-no-heap-scan`",
            "threadRoots: `true`",
            "threadRootRegistry: `true`",
            "threadRootScope: `parallel-host-thread-bootstrap-live-thread-root-registry-current-thread-root-membership-and-thread-target-field-traversal`",
            "threadLifecycleInventory: `true`",
            "threadLifecycleInventoryScope: `heap-thread-object-thread-root-registry-started-completed-active-non-current-target-current-root-and-completed-target-release-counters`",
            "javaHeapAllocationsManaged: `false`",
            "sanitizerInstrumentation: `not-built`",
            "threads: `current-thread-interrupt-state-isalive-isvirtual-entry-interrupted-sleep-start-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-parallel-host-thread-bootstrap-join-same-method-catch-thread-construction-duplicate-start-rejection-current-join-rejection-and-runnable-target-no-virtual-scheduler`"
        );
    }

    @Test
    void writeSummarizesRuntimeFootprintMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("runtime-footprint.json"), """
            {
              "hostTarget": "linux-x64",
              "requestedTarget": "linux-x64",
              "actualTarget": "linux-x64",
              "artifacts": [{"path": ".javan/bin/demo", "bytes": 42}],
              "footprints": [{"name": "system-linked"}, {"name": "self-contained"}],
              "osArchCoverage": [{"target": "linux-x64"}, {"target": "linux-aarch64"}]
            }
            """);
        Files.writeString(reports.resolve("runtime-footprint.md"), "# Runtime Footprint\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "hostTarget: `linux-x64`",
            "requestedTarget: `linux-x64`",
            "actualTarget: `linux-x64`",
            "artifactBytes: `42`",
            "footprints: `2`",
            "osArchCoverage: `2`"
        );
    }

    @Test
    void writeSummarizesCompatibilityMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("compatibility-summary.json"), """
            {
              "status": "pass",
              "javaFeatureVersion": 25,
              "projectClasses": 8,
              "jdkClasses": 32482,
              "exactSupportedJdkCallables": {
                "classes": 37,
                "constructors": 18,
                "methods": 141,
                "callables": 159,
                "totalCallables": 267886,
                "coveragePercent": "0.0"
              },
              "supportRows": 108,
              "passRows": 107,
              "scopedRows": 0,
              "targetRows": 1,
              "rejectedRows": 0,
              "accountedRows": 107,
              "unaccountedRows": 1,
              "diagnosticErrors": 0,
              "recognizedRejectedOpcodeUses": 2,
              "unknownFatalOpcodeUses": 0
            }
            """);
        Files.writeString(reports.resolve("compatibility-summary.md"), "# Compatibility\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "status: `pass`",
            "javaFeatureVersion: `25`",
            "jdkClasses: `32482`",
            "exactSupportedJdkCallableClasses: `37`",
            "exactSupportedJdkConstructors: `18`",
            "exactSupportedJdkMethods: `141`",
            "exactSupportedJdkCallables: `159`",
            "totalJdkCallables: `267886`",
            "exactSupportedJdkCallableCoveragePercent: `0.0`",
            "supportRows: `108`",
            "passRows: `107`",
            "scopedRows: `0`",
            "targetRows: `1`",
            "rejectedRows: `0`",
            "accountedRows: `107`",
            "unaccountedRows: `1`",
            "unknownFatalOpcodeUses: `0`"
        );
    }

    @Test
    void writeSummarizesSanitizerProofMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("sanitizer-proof.json"), """
            {
              "schemaVersion": 1,
              "status": "pass",
              "kind": "app",
              "project": "src/test/resources/projects/native-profile/memory-soak",
              "sanitizerRequired": true,
              "counterCheck": true,
              "leakDetection": "AddressSanitizer leak detection enabled",
              "expectedExit": 0,
              "actualExit": 0,
              "actualLiveAllocations": 0,
              "actualLiveBytes": 0,
              "actualPeakLiveBytes": 24192,
              "actualTotalAllocations": 5500,
              "actualGcCollections": 8,
              "actualGcCollectedAllocations": 5500,
              "actualGcCollectedBytes": 24192,
              "actualThreadObjects": 1,
              "actualStartedThreads": 1,
              "actualCompletedThreads": 0,
              "actualActiveThreads": 0,
              "actualThreadsWithTarget": 0,
              "actualCurrentThreadRootPresent": 1,
              "actualRootFrameDepth": 0,
              "actualFrameRootCount": 0,
              "maxLiveAllocations": 0,
              "maxLiveBytes": 0,
              "maxPeakLiveBytes": 32768,
              "minTotalAllocations": 5000,
              "minGcCollections": 1,
              "minGcCollectedAllocations": 5000,
              "failureSignatures": false,
              "probes": [{"name": "generated-app"}, {"name": "heap-counters"}]
            }
            """);
        Files.writeString(reports.resolve("sanitizer-proof.md"), "# Sanitizer Proof\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "status: `pass`",
            "kind: `app`",
            "sanitizerRequired: `true`",
            "counterCheck: `true`",
            "leakDetection: `AddressSanitizer leak detection enabled`",
            "expectedExit: `0`",
            "actualExit: `0`",
            "actualLiveAllocations: `0`",
            "actualLiveBytes: `0`",
            "actualPeakLiveBytes: `24192`",
            "actualTotalAllocations: `5500`",
            "actualGcCollections: `8`",
            "actualGcCollectedAllocations: `5500`",
            "actualGcCollectedBytes: `24192`",
            "actualThreadObjects: `1`",
            "actualStartedThreads: `1`",
            "actualCompletedThreads: `0`",
            "actualActiveThreads: `0`",
            "actualThreadsWithTarget: `0`",
            "actualCurrentThreadRootPresent: `1`",
            "actualRootFrameDepth: `0`",
            "actualFrameRootCount: `0`",
            "maxLiveAllocations: `0`",
            "maxLiveBytes: `0`",
            "maxPeakLiveBytes: `32768`",
            "minTotalAllocations: `5000`",
            "minGcCollections: `1`",
            "minGcCollectedAllocations: `5000`",
            "failureSignatures: `false`",
            "probes: `2`"
        );
    }

    @Test
    void writePersistsMarkdownAndJson() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("diagnostics.txt"), "No diagnostics.\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(Files.readString(summary.markdownPath())).isEqualTo(summary.markdown());
        assertThat(Files.readString(summary.jsonPath())).isEqualTo(summary.json());
    }

    private Path reportsDirectory() throws Exception {
        final Path reports = tempDir.resolve(".javan/reports");
        Files.createDirectories(reports);
        return reports;
    }
}
