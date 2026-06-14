package javan.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class RuntimeContractReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void optionalInspectionTimeoutStaysShortForBuildPath() {
        assertThat(RuntimeContractReports.INSPECTION_TIMEOUT_MILLIS).isEqualTo(5_000L);
    }

    @Test
    void writeReportsStaticArchiveWithoutSystemLibraries() throws Exception {
        final Path archive = tempDir.resolve(".javan/dist/libdemo.a");
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "archive\n");

        final RuntimeContractReports.Report report = new RuntimeContractReports().write(
            tempDir.resolve(".javan"),
            "library",
            List.of(archive),
            List.of("javan_export_com_acme_Math_add_int_int")
        );

        final String json = Files.readString(report.jsonPath());
        final String markdown = Files.readString(report.markdownPath());
        assertThat(json).contains(
            "\"artifactKind\": \"library\"",
            "\"linkage\": \"static-archive\"",
            "\"systemLibraries\": []",
            "\"abiSymbols\": [\"javan_export_com_acme_Math_add_int_int\"]",
            "\"debugInfo\": \"not-requested\"",
            "\"memoryModel\": \"tracked-c-heap-safe-point-partial-gc\"",
            "\"javaAllocationOwnership\": \"javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible\"",
            "\"ffiAllocationOwnership\": \"caller-frees-javan-owned-results-with-javan_free\"",
            "\"temporaryAllocationOwnership\": \"javan-owned-explicit-free\"",
            "\"allocator\": \"tracked-calloc-free-at-shutdown\"",
            "\"heapMetadata\": true",
            "\"heapMetadataStrategy\": \"allocation-ledger-kind-typeid-runtimekind-mark-collectible\"",
            "\"heapAccounting\": true",
            "\"heapReclamation\": true",
            "\"heapReclamationScope\": \"generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage\"",
            "\"typeDescriptors\": true",
            "\"objectFieldDescriptors\": true",
            "\"frameRootInventory\": true",
            "\"gc\": \"partial-mark-sweep\"",
            "\"gcStrategy\": \"single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep\"",
            "\"gcStress\": \"metadata-verify-and-safe-point-collection\"",
            "\"gcExcludedAllocationKinds\": [\"explicit-runtime-temporaries\", \"ffi-exports\"]",
            "\"runtimeContainerTraversal\": \"precise-rooted-runtime-container-mark-sweep\"",
            "\"operandCallTemporaryRoots\": true",
            "\"operandCallTemporaryRootModel\": \"generated-expression-root-frame\"",
            "\"operandCallTemporaryRootScope\": [\"object-call-arguments\", \"nested-object-call-results\", \"field-store-receiver\", \"field-store-value\", \"array-store-array\", \"array-store-value\", \"return-operand\", \"object-print-operands\"]",
            "\"operandCallTemporaryRootLifetime\": \"until-enclosing-generated-statement-or-return-completes\"",
            "\"allocationPathCollection\": true",
            "\"allocationPathCollectionModel\": \"allocator-gc-retry-before-out-of-memory\"",
            "\"allocationPathCollectionScope\": \"generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage\"",
            "\"allocationFailureMode\": \"deterministic-native-panic\"",
            "\"statementSafePoints\": true",
            "\"statementSafePointScope\": \"generated-label-and-non-terminal-statement-boundaries\"",
            "\"returnValueRoots\": true",
            "\"protectedObjectReturns\": true",
            "\"protectedObjectReturnScope\": \"single-threaded-static-return-root-through-callee-safe-point-and-frame-pop\"",
            "\"staticRootInventory\": true",
            "\"localRootInventory\": true",
            "\"rootModel\": \"generated-static-frame-return-and-expression-root-inventory-no-heap-scan\"",
            "\"sanitizerInstrumentation\": \"not-built\"",
            "\"threads\": \"none\""
        );
        assertThat(markdown).contains(
            "Runtime Contract",
            "ABI symbols: `javan_export_com_acme_Math_add_int_int`",
            "memory model: `tracked-c-heap-safe-point-partial-gc`",
            "Java allocation ownership: `javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible`",
            "heap metadata strategy: `allocation-ledger-kind-typeid-runtimekind-mark-collectible`",
            "heap accounting: `true`",
            "heap reclamation: `true`",
            "heap reclamation scope: `generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage`",
            "type descriptors: `true`",
            "object field descriptors: `true`",
            "frame root inventory: `true`",
            "gc strategy: `single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep`",
            "gc excluded allocation kinds: `explicit-runtime-temporaries, ffi-exports`",
            "runtime container traversal: `precise-rooted-runtime-container-mark-sweep`",
            "operand/call temporary roots: `true`",
            "operand/call temporary root model: `generated-expression-root-frame`",
            "allocation-path collection: `true`",
            "allocation-path collection model: `allocator-gc-retry-before-out-of-memory`",
            "allocation-path collection scope: `generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage`",
            "allocation failure mode: `deterministic-native-panic`",
            "statement safe points: `true`",
            "statement safe point scope: `generated-label-and-non-terminal-statement-boundaries`",
            "return value roots: `true`",
            "protected object returns: `true`",
            "protected object return scope: `single-threaded-static-return-root-through-callee-safe-point-and-frame-pop`",
            "root model: `generated-static-frame-return-and-expression-root-inventory-no-heap-scan`",
            "static-archive"
        );
        assertThat(report.artifacts()).hasSize(1);
        assertThat(report.artifacts().getFirst().bytes()).isEqualTo(Files.size(archive));
    }

    @Test
    void writeReportsMissingExecutableInspectionAsUnknown() throws Exception {
        final Path binary = tempDir.resolve(".javan/bin/missing");
        Files.createDirectories(binary.getParent());

        final RuntimeContractReports.Report report = new RuntimeContractReports().write(
            tempDir.resolve(".javan"),
            "app",
            List.of(binary)
        );

        final String json = Files.readString(report.jsonPath());
        assertThat(json).contains(
            "\"artifactKind\": \"app\"",
            "\"bytes\": 0",
            "\"linkage\": \"dynamic-executable\"",
            "\"debugInfo\": \"unknown\""
        );
        assertThat(report.artifacts().getFirst().systemLibraries()).isNotEmpty();
        assertThat(report.artifacts().getFirst().symbolTable()).isEqualTo("unknown");
    }

    @Test
    void writeClassifiesSharedLibraries() throws Exception {
        final Path shared = tempDir.resolve(".javan/dist/libdemo.dylib");
        Files.createDirectories(shared.getParent());
        Files.writeString(shared, "not really a dylib\n");

        final RuntimeContractReports.Report report = new RuntimeContractReports().write(
            tempDir.resolve(".javan"),
            "library",
            List.of(shared),
            List.of()
        );

        assertThat(Files.readString(report.jsonPath())).contains(
            "\"artifactKind\": \"library\"",
            "\"linkage\": \"dynamic-library\"",
            "\"abiSymbols\": []"
        );
        assertThat(report.artifacts().getFirst().debugInfo()).isEqualTo("not-requested");
    }

    @Test
    void writeReportsMultipleSharedArtifactsWithUnknownHostLibraries() throws Exception {
        final Path unixShared = tempDir.resolve(".javan/dist/libdemo.so");
        final Path windowsShared = tempDir.resolve(".javan/dist/demo.dll");
        Files.createDirectories(unixShared.getParent());
        Files.writeString(unixShared, "not really a so\n");
        Files.writeString(windowsShared, "not really a dll\n");
        final String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Plan 9");
        try {
            final RuntimeContractReports.Report report = new RuntimeContractReports().write(
                tempDir.resolve(".javan"),
                "library",
                List.of(unixShared, windowsShared),
                List.of("first_symbol", "second_symbol")
            );

            final String json = Files.readString(report.jsonPath());
            final String markdown = Files.readString(report.markdownPath());
            assertThat(json).contains(
                "\"path\": \"" + unixShared + "\"",
                "\"path\": \"" + windowsShared + "\"",
                "\"systemLibraries\": [\"unknown\"]",
                "\"abiSymbols\": [\"first_symbol\", \"second_symbol\"]"
            );
            assertThat(markdown).contains(
                "ABI symbols: `first_symbol, second_symbol`",
                "libdemo.so",
                "demo.dll"
            );
            assertThat(report.artifacts()).hasSize(2);
            assertThat(report.artifacts()).allSatisfy(artifact ->
                assertThat(artifact.linkage()).isEqualTo("dynamic-library")
            );
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

    @Test
    void writeReportsLinuxSharedLibraryInspectionAsUnknownWhenProbeCannotReadArtifact() throws Exception {
        final Path shared = tempDir.resolve(".javan/dist/libdemo.so");
        Files.createDirectories(shared.getParent());
        Files.writeString(shared, "not really a shared library\n");
        final String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");
        try {
            final RuntimeContractReports.Report report = new RuntimeContractReports().write(
                tempDir.resolve(".javan"),
                "library",
                List.of(shared),
                List.of()
            );

            assertThat(report.artifacts().getFirst().systemLibraries()).contains("unknown");
            assertThat(Files.readString(report.jsonPath())).contains(
                "\"linkage\": \"dynamic-library\"",
                "\"systemLibraries\": [\"unknown\"]"
            );
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }
}
