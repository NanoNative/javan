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

        assertThat(summary.markdown()).contains("Known report families: `0` present, `0` partial, `12` absent.");
        assertThat(summary.json()).contains("\"presentFamilyCount\": 0", "\"partialFamilyCount\": 0", "\"absentFamilyCount\": 12");
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

        final ReportSummarizer.Summary summary = new ReportSummarizer().write(tempDir);

        assertThat(summary.markdown()).contains("diagnostics: `2`", "errors: `1`", "warnings: `1`");
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
              "abiVersion": 1,
              "stringOwnership": "input-borrowed-utf8-output-javan-owned-free-with-javan_free",
              "byteArrayOwnership": "input-copied-output-javan-owned-data-free-with-javan_free",
              "errorResultAbi": "not-yet-stable-panics-abort-current-call",
              "exceptionMapping": "uncaught-native-panic-limited-same-method-catch",
              "threadRuntimeRules": "single-threaded-native-profile-no-thread-api-yet",
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
            "abiVersion: `1`",
            "stringOwnership: `input-borrowed-utf8-output-javan-owned-free-with-javan_free`",
            "byteArrayOwnership: `input-copied-output-javan-owned-data-free-with-javan_free`",
            "errorResultAbi: `not-yet-stable-panics-abort-current-call`",
            "exceptionMapping: `uncaught-native-panic-limited-same-method-catch`",
            "threadRuntimeRules: `single-threaded-native-profile-no-thread-api-yet`",
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
    void writeSummarizesRuntimeFeatureMetrics() throws Exception {
        final Path reports = reportsDirectory();
        Files.writeString(reports.resolve("runtime-features.json"), """
            {
              "status": "pass",
              "containment": "system-linked",
              "optimize": "size",
              "reachableRuntimeModules": ["core", "strings"],
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
            "reachableRuntimeModules: `2`",
            "disabledRuntimeModules: `1`",
            "disabledReachableRuntimeModules: `0`",
            "disabledUnusedRuntimeModules: `1`",
            "unknownDisabledRuntimeModules: `0`"
        );
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
              "javaAllocationOwnership": "javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible",
              "ffiAllocationOwnership": "caller-frees-javan-owned-results-with-javan_free",
              "temporaryAllocationOwnership": "javan-owned-explicit-free",
              "heapMetadata": true,
              "heapMetadataStrategy": "allocation-ledger-kind-typeid-runtimekind-mark-collectible",
              "heapAccounting": true,
              "heapReclamation": true,
              "heapReclamationScope": "generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage",
              "typeDescriptors": true,
              "objectFieldDescriptors": true,
              "frameRootInventory": true,
              "managedHeap": false,
              "gc": "partial-mark-sweep",
              "gcStrategy": "single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep",
              "gcStress": "metadata-verify-and-safe-point-collection",
              "gcExcludedAllocationKinds": ["explicit-runtime-temporaries", "ffi-exports"],
              "runtimeContainerTraversal": "precise-rooted-runtime-container-mark-sweep",
              "operandCallTemporaryRoots": true,
              "operandCallTemporaryRootModel": "generated-expression-root-frame",
              "operandCallTemporaryRootScope": ["object-call-arguments", "nested-object-call-results"],
              "operandCallTemporaryRootLifetime": "until-enclosing-generated-statement-or-return-completes",
              "allocationPathCollection": true,
              "allocationPathCollectionModel": "allocator-gc-retry-before-out-of-memory",
              "allocationPathCollectionScope": "generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage",
              "allocationFailureMode": "deterministic-native-panic",
              "statementSafePoints": true,
              "statementSafePointScope": "generated-label-and-non-terminal-statement-boundaries",
              "returnValueRoots": true,
              "protectedObjectReturns": true,
              "protectedObjectReturnScope": "single-threaded-static-return-root-through-callee-safe-point-and-frame-pop",
              "staticRootInventory": true,
              "localRootInventory": true,
              "rootScanning": false,
              "rootModel": "generated-static-frame-return-and-expression-root-inventory-no-heap-scan",
              "threadRoots": false,
              "javaHeapAllocationsManaged": false,
              "exceptions": "panic-and-limited-same-method-catch",
              "threads": "none",
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
            "javaAllocationOwnership: `javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible`",
            "ffiAllocationOwnership: `caller-frees-javan-owned-results-with-javan_free`",
            "temporaryAllocationOwnership: `javan-owned-explicit-free`",
            "heapMetadata: `true`",
            "heapMetadataStrategy: `allocation-ledger-kind-typeid-runtimekind-mark-collectible`",
            "heapAccounting: `true`",
            "heapReclamation: `true`",
            "heapReclamationScope: `generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage`",
            "typeDescriptors: `true`",
            "objectFieldDescriptors: `true`",
            "frameRootInventory: `true`",
            "managedHeap: `false`",
            "gc: `partial-mark-sweep`",
            "gcStrategy: `single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep`",
            "gcStress: `metadata-verify-and-safe-point-collection`",
            "gcExcludedAllocationKinds: `2`",
            "runtimeContainerTraversal: `precise-rooted-runtime-container-mark-sweep`",
            "operandCallTemporaryRoots: `true`",
            "operandCallTemporaryRootModel: `generated-expression-root-frame`",
            "operandCallTemporaryRootScope: `2`",
            "operandCallTemporaryRootLifetime: `until-enclosing-generated-statement-or-return-completes`",
            "allocationPathCollection: `true`",
            "allocationPathCollectionModel: `allocator-gc-retry-before-out-of-memory`",
            "allocationPathCollectionScope: `generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage`",
            "allocationFailureMode: `deterministic-native-panic`",
            "statementSafePoints: `true`",
            "statementSafePointScope: `generated-label-and-non-terminal-statement-boundaries`",
            "returnValueRoots: `true`",
            "protectedObjectReturns: `true`",
            "protectedObjectReturnScope: `single-threaded-static-return-root-through-callee-safe-point-and-frame-pop`",
            "staticRootInventory: `true`",
            "localRootInventory: `true`",
            "rootScanning: `false`",
            "rootModel: `generated-static-frame-return-and-expression-root-inventory-no-heap-scan`",
            "threadRoots: `false`",
            "javaHeapAllocationsManaged: `false`",
            "sanitizerInstrumentation: `not-built`",
            "threads: `none`"
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
            "unknownFatalOpcodeUses: `0`"
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
