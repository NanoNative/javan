package javan.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
              "intrinsicCallSiteCount": 5,
              "runtimeCalls": [{"name": "PrintStream.println", "count": 1}],
              "runtimeCallSiteCount": 1,
              "supportedDirectJdkCalls": [{"target": "java/util/List.of()Ljava/util/List;", "count": 2}],
              "supportedDirectJdkCallSiteCount": 2,
              "supportedJdkCallSiteCount": 8,
              "unsupportedJdkCallCandidateCount": 1,
              "unsupportedJdkCallCandidates": [{"owner": "java/lang/Class"}]
            }
            """);
        Files.writeString(reports.resolve("intrinsics.md"), "# Intrinsics\n");

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains(
            "intrinsics: `2`",
            "intrinsicCallSites: `5`",
            "runtimeCalls: `1`",
            "runtimeCallSites: `1`",
            "supportedDirectJdkCalls: `1`",
            "supportedDirectJdkCallSiteCount: `2`",
            "supportedJdkCallSiteCount: `8`",
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
                "leftCallables": 267727,
                "coveragePercent": "0.0"
              },
              "exactJdkCallableAccounting": {
                "supportedCallables": 159,
                "explicitRejectedCallables": 8,
                "doneCallables": 167,
                "unknownCallables": 267719,
                "totalCallables": 267886,
                "donePercent": "0.0"
              },
              "flowQualifiedRejectedJdkCalls": {
                "reachableCurrentThreadLifecycle": 1,
                "unreachableCurrentThreadLifecycle": 2,
                "reachableThreadBuilderReceiverShape": 3,
                "unreachableThreadBuilderReceiverShape": 4,
                "reachableVirtualThreadFactoryShape": 5,
                "unreachableVirtualThreadFactoryShape": 6,
                "reachableExecutorReceiverShape": 7,
                "unreachableExecutorReceiverShape": 8,
                "total": 36
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
            "leftJdkCallables: `267727`",
            "exactSupportedJdkCallableCoveragePercent: `0.0`",
            "accountedSupportedJdkCallables: `159`",
            "accountedRejectedJdkCallables: `8`",
            "accountedDoneJdkCallables: `167`",
            "unknownJdkCallables: `267719`",
            "accountingTotalJdkCallables: `267886`",
            "accountedDoneJdkCallablePercent: `0.0`",
            "flowQualifiedReachableCurrentThreadLifecycleRejects: `1`",
            "flowQualifiedUnreachableCurrentThreadLifecycleRejects: `2`",
            "flowQualifiedReachableThreadBuilderReceiverRejects: `3`",
            "flowQualifiedUnreachableThreadBuilderReceiverRejects: `4`",
            "flowQualifiedReachableVirtualThreadFactoryRejects: `5`",
            "flowQualifiedUnreachableVirtualThreadFactoryRejects: `6`",
            "flowQualifiedReachableExecutorReceiverRejects: `7`",
            "flowQualifiedUnreachableExecutorReceiverRejects: `8`",
            "flowQualifiedRejectedJdkCallShapes: `36`",
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

    @Test
    void reportsDirectoryFallsBackForRootPathWithoutFileName() throws Exception {
        final Path resolved = invokeReportsDirectory(Path.of("/"));

        assertThat(resolved).isEqualTo(Path.of("/.javan/reports"));
    }

    @Test
    void reportsDirectoryRejectsPlainReportsPathAtFilesystemRootShape() throws Exception {
        final Path resolved = invokeReportsDirectory(Path.of("/reports"));

        assertThat(resolved).isEqualTo(Path.of("/reports/.javan/reports"));
    }

    @Test
    void reportsDirectoryRejectsRelativeReportsPathOutsideJavan() throws Exception {
        final Path resolved = invokeReportsDirectory(Path.of("reports"));

        assertThat(resolved).isEqualTo(Path.of("reports/.javan/reports").toAbsolutePath().normalize());
    }

    @Test
    void metricsReturnsEmptyForUnknownFamily() throws Exception {
        assertThat(invokeMetrics(reportsDirectory(), "unknown-family")).isEmpty();
    }

    @Test
    void addNestedNumberSkipsMissingObjectBody() throws Exception {
        final List<Object> metrics = new ArrayList<>();

        invokeAddNestedNumber(metrics, "{\"other\":{}}", "threading", "count", "count");

        assertThat(metrics).isEmpty();
    }

    @Test
    void addNestedNumberSkipsMissingNestedField() throws Exception {
        final List<Object> metrics = new ArrayList<>();

        invokeAddNestedNumber(metrics, "{\"threading\":{\"other\":1}}", "threading", "count", "count");

        assertThat(metrics).isEmpty();
    }

    @Test
    void addNestedTextSkipsMissingNestedField() throws Exception {
        final List<Object> metrics = new ArrayList<>();

        invokeAddNestedText(metrics, "{\"threading\":{\"other\":\"x\"}}", "threading", "name", "name");

        assertThat(metrics).isEmpty();
    }

    @Test
    void addNestedTextAddsMetricWhenNestedTextExists() throws Exception {
        final List<Object> metrics = new ArrayList<>();

        invokeAddNestedText(metrics, "{\"threading\":{\"name\":\"carrier\"}}", "threading", "name", "name");

        assertThat(metrics).hasSize(1);
        assertThat(metrics.getFirst().toString()).contains("name");
    }

    @Test
    void addNestedTextSkipsMissingObjectBody() throws Exception {
        final List<Object> metrics = new ArrayList<>();

        invokeAddNestedText(metrics, "{\"other\":{}}", "threading", "name", "name");

        assertThat(metrics).isEmpty();
    }

    @Test
    void stringFieldReturnsEmptyForNonQuotedValue() throws Exception {
        assertThat(invokeStringField("{\"name\":42}", "name")).isEmpty();
    }

    @Test
    void stringFieldReturnsEmptyWhenFieldIsMissing() throws Exception {
        assertThat(invokeStringField("{\"other\":\"x\"}", "name")).isEmpty();
    }

    @Test
    void stringFieldReturnsEmptyWhenValueIsMissing() throws Exception {
        assertThat(invokeStringField("{\"name\":}", "name")).isEmpty();
    }

    @Test
    void booleanFieldReturnsEmptyForUnsupportedLiteral() throws Exception {
        assertThat(invokeBooleanField("{\"enabled\":truthy}", "enabled")).isEmpty();
    }

    @Test
    void booleanFieldReturnsEmptyWhenFieldIsMissing() throws Exception {
        assertThat(invokeBooleanField("{\"other\":true}", "enabled")).isEmpty();
    }

    @Test
    void booleanFieldReturnsEmptyWhenValueIsMissing() throws Exception {
        assertThat(invokeBooleanField("{\"enabled\":}", "enabled")).isEmpty();
    }

    @Test
    void matchesAtRejectsOutOfBoundsStart() throws Exception {
        assertThat(invokeMatchesAt("true", 2, "true")).isFalse();
    }

    @Test
    void matchesAtRejectsNegativeStart() throws Exception {
        assertThat(invokeMatchesAt("true", -1, "tr")).isFalse();
    }

    @Test
    void numberAtReturnsNoNumberWhenStartIsOutOfBounds() throws Exception {
        assertThat(invokeNumberAt("42", 2)).isEqualTo(noNumberSentinel());
    }

    @Test
    void arrayBodyReturnsEmptyForUnclosedArray() throws Exception {
        assertThat(invokeArrayBody("{\"items\":[1,2}", "items")).isEmpty();
    }

    @Test
    void objectBodyReturnsEmptyForUnclosedObject() throws Exception {
        assertThat(invokeObjectBody("{\"nested\":{\"a\":1", "nested")).isEmpty();
    }

    @Test
    void objectBodyReturnsBodyWhenQuotedBraceAppearsInsideString() throws Exception {
        assertThat(invokeObjectBody("{\"nested\":{\"text\":\"}\",\"value\":1}}", "nested"))
            .contains("\"text\":\"}\",\"value\":1");
    }

    @Test
    void objectBodyReturnsEmptyWhenFieldIsNotObject() throws Exception {
        assertThat(invokeObjectBody("{\"nested\":true}", "nested")).isEmpty();
    }

    @Test
    void stringArrayTextReturnsEmptyWhenArrayIsMissing() throws Exception {
        assertThat(invokeStringArrayText("{\"items\":true}", "items")).isEmpty();
    }

    @Test
    void stringArrayTextKeepsEscapedCharactersInsideItems() throws Exception {
        assertThat(invokeStringArrayText("{\"items\":[\"a\\\\\\\"b\",\"c\\\\d\"]}", "items"))
            .contains("a\\\"b, c\\d");
    }

    @Test
    void stringArrayTextPreservesEmptyStringItems() throws Exception {
        assertThat(invokeStringArrayText("{\"items\":[\"\"]}", "items")).contains("");
    }

    @Test
    void objectEndIgnoresQuotedBraces() throws Exception {
        assertThat(invokeObjectEnd("{\"text\":\"}\",\"value\":1}", 0)).isEqualTo(21);
    }

    @Test
    void objectEndTraversesNestedObjectsBeforeReturning() throws Exception {
        assertThat(invokeObjectEnd("{\"outer\":{\"inner\":{}}}", 0)).isEqualTo(21);
    }

    @Test
    void objectEndIgnoresEscapedQuotesInsideStrings() throws Exception {
        assertThat(invokeObjectEnd("{\"text\":\"\\\\\\\"\",\"value\":1}", 0)).isEqualTo(24);
    }

    @Test
    void sumNumberFieldsSkipsBrokenValuesAndKeepsLaterNumbers() throws Exception {
        assertThat(invokeSumNumberFields("{\"count\":oops,\"count\":2,\"count\":3}", "count")).isEqualTo(5L);
    }

    @Test
    void sumNumberFieldsReturnsZeroWhenFieldIsMissing() throws Exception {
        assertThat(invokeSumNumberFields("{\"other\":1}", "count")).isZero();
    }

    @Test
    void sumNumberFieldsReturnsZeroForEmptyReport() throws Exception {
        assertThat(invokeSumNumberFields("", "count")).isZero();
    }

    @Test
    void fieldValueStartReturnsMinusOneWhenFieldIsMissing() throws Exception {
        assertThat(invokeFieldValueStart("{\"other\":1}", "count", 0)).isEqualTo(-1);
    }

    @Test
    void fieldValueStartReturnsMinusOneWhenOffsetStartsAtEnd() throws Exception {
        assertThat(invokeFieldValueStart("{\"count\":1}", "count", 11)).isEqualTo(-1);
    }

    @Test
    void trimCarriageReturnLeavesPlainLineEndUnchanged() throws Exception {
        assertThat(invokeTrimCarriageReturn("line", 4)).isEqualTo(4);
    }

    @Test
    void trimCarriageReturnRemovesTrailingCarriageReturn() throws Exception {
        assertThat(invokeTrimCarriageReturn("line\r", 5)).isEqualTo(4);
    }

    @Test
    void skipWhitespaceReturnsLengthWhenStartingAtEnd() throws Exception {
        assertThat(invokeSkipWhitespace("value", 5)).isEqualTo(5);
    }

    @Test
    void skipWhitespaceStopsAtFirstVisibleCharacter() throws Exception {
        assertThat(invokeSkipWhitespace(" \n\tvalue", 0)).isEqualTo(3);
    }

    private Path reportsDirectory() throws Exception {
        final Path reports = tempDir.resolve(".javan/reports");
        Files.createDirectories(reports);
        return reports;
    }

    private static Path invokeReportsDirectory(final Path target) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("reportsDirectory", Path.class);
        method.setAccessible(true);
        return (Path) method.invoke(null, target);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeMetrics(final Path reportsDirectory, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("metrics", Path.class, String.class);
        method.setAccessible(true);
        return (List<Object>) method.invoke(null, reportsDirectory, name);
    }

    private static void invokeAddNestedNumber(
        final List<Object> result,
        final String report,
        final String objectName,
        final String fieldName,
        final String metricName
    ) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod(
            "addNestedNumber",
            List.class,
            String.class,
            String.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        method.invoke(null, result, report, objectName, fieldName, metricName);
    }

    private static void invokeAddNestedText(
        final List<Object> result,
        final String report,
        final String objectName,
        final String fieldName,
        final String metricName
    ) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod(
            "addNestedText",
            List.class,
            String.class,
            String.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        method.invoke(null, result, report, objectName, fieldName, metricName);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> invokeStringField(final String report, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("stringField", String.class, String.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(null, report, name);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> invokeBooleanField(final String report, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("booleanField", String.class, String.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(null, report, name);
    }

    private static boolean invokeMatchesAt(final String value, final int start, final String expected) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("matchesAt", String.class, int.class, String.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, value, start, expected);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> invokeArrayBody(final String report, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("arrayBody", String.class, String.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(null, report, name);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> invokeObjectBody(final String report, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("objectBody", String.class, String.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(null, report, name);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> invokeStringArrayText(final String report, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("stringArrayText", String.class, String.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(null, report, name);
    }

    private static int invokeObjectEnd(final String value, final int start) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("objectEnd", String.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, value, start);
    }

    private static long invokeSumNumberFields(final String report, final String name) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("sumNumberFields", String.class, String.class);
        method.setAccessible(true);
        return (long) method.invoke(null, report, name);
    }

    private static long invokeNumberAt(final String value, final int start) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("numberAt", String.class, int.class);
        method.setAccessible(true);
        return (long) method.invoke(null, value, start);
    }

    private static int invokeFieldValueStart(final String report, final String name, final int offset) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("fieldValueStart", String.class, String.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, report, name, offset);
    }

    private static int invokeTrimCarriageReturn(final String value, final int end) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("trimCarriageReturn", String.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, value, end);
    }

    private static int invokeSkipWhitespace(final String value, final int start) throws Exception {
        final Method method = ReportSummarizer.class.getDeclaredMethod("skipWhitespace", String.class, int.class);
        method.setAccessible(true);
        return (int) method.invoke(null, value, start);
    }

    private static long noNumberSentinel() throws Exception {
        final var field = ReportSummarizer.class.getDeclaredField("NO_NUMBER");
        field.setAccessible(true);
        return field.getLong(null);
    }
}
