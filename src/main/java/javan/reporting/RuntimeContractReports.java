package javan.reporting;

import javan.util.Files2;
import javan.util.Json;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes the native runtime contract for generated artifacts.
 */
public final class RuntimeContractReports {
    static final long INSPECTION_TIMEOUT_MILLIS = 5_000L;

    private static final List<String> INCLUDED_RUNTIME_MODULES = List.of(
        "core",
        "arrays",
        "strings",
        "collections",
        "maps",
        "optional",
        "filesystem",
        "process",
        "environment",
        "time",
        "math",
        "ffi-memory"
    );

    private final ProcessRunner processRunner;

    /**
     * Creates runtime contract reports using the local toolchain.
     */
    public RuntimeContractReports() {
        this(new ProcessRunner(INSPECTION_TIMEOUT_MILLIS));
    }

    /**
     * Creates runtime contract reports.
     *
     * @param processRunner process runner used for optional binary inspection tools
     */
    public RuntimeContractReports(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Writes runtime reports for native artifacts.
     *
     * @param outputDirectory javan output directory
     * @param artifactKind artifact kind
     * @param artifacts generated native artifacts
     * @return written report paths and inspected artifacts
     * @throws IOException when reports cannot be written
     * @throws InterruptedException when interrupted while inspecting binaries
     */
    public Report write(final Path outputDirectory, final String artifactKind, final List<Path> artifacts)
        throws IOException, InterruptedException {
        return write(outputDirectory, artifactKind, artifacts, List.of());
    }

    /**
     * Writes runtime reports for native artifacts.
     *
     * @param outputDirectory javan output directory
     * @param artifactKind artifact kind
     * @param artifacts generated native artifacts
     * @param abiSymbols exported ABI symbols
     * @return written report paths and inspected artifacts
     * @throws IOException when reports cannot be written
     * @throws InterruptedException when interrupted while inspecting binaries
     */
    public Report write(
        final Path outputDirectory,
        final String artifactKind,
        final List<Path> artifacts,
        final List<String> abiSymbols
    ) throws IOException, InterruptedException {
        final List<ArtifactReport> artifactReports = inspectArtifacts(artifacts);
        final Path reportsDirectory = outputDirectory.resolve("reports");
        final Path jsonPath = reportsDirectory.resolve("runtime.json");
        final Path markdownPath = reportsDirectory.resolve("runtime.md");
        final String json = json(artifactKind, artifactReports, abiSymbols);
        final String markdown = markdown(artifactKind, artifactReports, abiSymbols);
        Files2.writeString(jsonPath, json);
        Files2.writeString(markdownPath, markdown);
        return new Report(jsonPath, markdownPath, artifactReports);
    }

    private List<ArtifactReport> inspectArtifacts(final List<Path> artifacts) throws IOException, InterruptedException {
        final List<ArtifactReport> result = new ArrayList<>();
        for (final Path artifact : artifacts) {
            result.add(inspectArtifact(artifact));
        }
        return List.copyOf(result);
    }

    private ArtifactReport inspectArtifact(final Path artifact) throws IOException, InterruptedException {
        final long bytes = Files.isRegularFile(artifact) ? Files.size(artifact) : 0L;
        final String linkage = linkage(artifact);
        final List<String> libraries = systemLibraries(artifact, linkage);
        final String debugInfo = debugInfo(artifact);
        final String symbolTable = symbolTable(artifact);
        return new ArtifactReport(artifact, bytes, linkage, libraries, debugInfo, symbolTable);
    }

    private static String linkage(final Path artifact) {
        final String name = artifact.getFileName() == null ? "" : artifact.getFileName().toString();
        if (name.endsWith(".a")) {
            return "static-archive";
        }
        if (name.endsWith(".so") || name.endsWith(".dylib") || name.endsWith(".dll")) {
            return "dynamic-library";
        }
        return "dynamic-executable";
    }

    private List<String> systemLibraries(final Path artifact, final String linkage) throws IOException, InterruptedException {
        if ("static-archive".equals(linkage)) {
            return List.of();
        }
        final String os = Strings2.toAsciiLowerCase(System.getProperty("os.name", ""));
        if (os.contains("mac") || os.contains("darwin")) {
            return macLibraries(artifact);
        }
        if (os.contains("linux")) {
            return linuxLibraries(artifact);
        }
        return List.of("unknown");
    }

    private List<String> macLibraries(final Path artifact) throws IOException, InterruptedException {
        final Optional<String> tool = processRunner.firstAvailable(List.of("otool"));
        if (tool.isEmpty()) {
            return List.of("unknown");
        }
        final ProcessRunner.Result result = processRunner.run(
            artifact.toAbsolutePath().getParent(),
            List.of(tool.orElseThrow(), "-L", artifact.toAbsolutePath().toString())
        );
        if (result.exitCode() != 0) {
            return List.of("unknown");
        }
        final List<String> libraries = new ArrayList<>();
        final List<String> lines = lines(result.stdout());
        for (int index = 1; index < lines.size(); index++) {
            final String value = beforeFirstSpace(Strings2.trimAscii(lines.get(index)));
            if (!Strings2.isBlank(value)) {
                libraries.add(value);
            }
        }
        return List.copyOf(libraries);
    }

    private List<String> linuxLibraries(final Path artifact) throws IOException, InterruptedException {
        final Optional<String> tool = processRunner.firstAvailable(List.of("ldd"));
        if (tool.isEmpty()) {
            return List.of("unknown");
        }
        final ProcessRunner.Result result = processRunner.run(
            artifact.toAbsolutePath().getParent(),
            List.of(tool.orElseThrow(), artifact.toAbsolutePath().toString())
        );
        if (result.exitCode() != 0) {
            return List.of("unknown");
        }
        final List<String> libraries = new ArrayList<>();
        final List<String> lines = lines(result.stdout());
        for (final String line : lines) {
            final String value = linuxLibrary(line);
            if (!Strings2.isBlank(value)) {
                libraries.add(value);
            }
        }
        return List.copyOf(libraries);
    }

    private String debugInfo(final Path artifact) {
        if (!Files.isRegularFile(artifact)) {
            return "unknown";
        }
        return "not-requested";
    }

    private String symbolTable(final Path artifact) throws IOException, InterruptedException {
        final Optional<String> tool = processRunner.firstAvailable(List.of("nm"));
        if (tool.isEmpty()) {
            return "unknown";
        }
        final ProcessRunner.Result result = processRunner.run(
            artifact.toAbsolutePath().getParent(),
            List.of(tool.orElseThrow(), artifact.toAbsolutePath().toString())
        );
        if (result.exitCode() != 0) {
            return "unknown";
        }
        if (Strings2.isBlank(result.stdout())) {
            return "absent";
        }
        return "present";
    }

    private static String json(final String artifactKind, final List<ArtifactReport> artifacts, final List<String> abiSymbols) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        field(result, "schemaVersion", "1", true);
        field(result, "artifactKind", Json.string(artifactKind), true);
        field(result, "artifacts", artifactsJson(artifacts), true);
        field(result, "abiSymbols", Json.stringList(abiSymbols), true);
        field(result, "runtimePackaging", Json.string("monolithic-c-runtime"), true);
        field(result, "runtimeModulesIncluded", Json.stringList(INCLUDED_RUNTIME_MODULES), true);
        field(result, "memoryModel", Json.string("tracked-c-heap-safe-point-partial-gc"), true);
        field(result, "allocator", Json.string("tracked-calloc-free-at-shutdown"), true);
        field(result, "javaAllocationOwnership", Json.string("javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible"), true);
        field(result, "ffiAllocationOwnership", Json.string("caller-frees-javan-owned-results-with-javan_free"), true);
        field(result, "temporaryAllocationOwnership", Json.string("javan-owned-explicit-free"), true);
        field(result, "heapMetadata", "true", true);
        field(result, "heapMetadataStrategy", Json.string("allocation-ledger-kind-typeid-runtimekind-mark-collectible"), true);
        field(result, "heapAccounting", "true", true);
        field(result, "heapReclamation", "true", true);
        field(result, "heapReclamationScope", Json.string("generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage"), true);
        field(result, "typeDescriptors", "true", true);
        field(result, "objectFieldDescriptors", "true", true);
        field(result, "frameRootInventory", "true", true);
        field(result, "managedHeap", "false", true);
        field(result, "gc", Json.string("partial-mark-sweep"), true);
        field(result, "gcStrategy", Json.string("single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep"), true);
        field(result, "gcStress", Json.string("metadata-verify-and-safe-point-collection"), true);
        field(result, "gcExcludedAllocationKinds", Json.stringList(List.of(
            "explicit-runtime-temporaries",
            "ffi-exports"
        )), true);
        field(result, "runtimeContainerTraversal", Json.string("precise-rooted-runtime-container-mark-sweep"), true);
        field(result, "operandCallTemporaryRoots", "true", true);
        field(result, "operandCallTemporaryRootModel", Json.string("generated-expression-root-frame"), true);
        field(result, "operandCallTemporaryRootScope", Json.stringList(List.of(
            "object-call-arguments",
            "nested-object-call-results",
            "field-store-receiver",
            "field-store-value",
            "array-store-array",
            "array-store-value",
            "return-operand",
            "object-print-operands"
        )), true);
        field(result, "operandCallTemporaryRootLifetime", Json.string("until-enclosing-generated-statement-or-return-completes"), true);
        field(result, "allocationPathCollection", "true", true);
        field(result, "allocationPathCollectionModel", Json.string("allocator-gc-retry-before-out-of-memory"), true);
        field(result, "allocationPathCollectionScope", Json.string("generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage"), true);
        field(result, "allocationFailureMode", Json.string("deterministic-native-panic"), true);
        field(result, "statementSafePoints", "true", true);
        field(result, "statementSafePointScope", Json.string("generated-label-and-non-terminal-statement-boundaries"), true);
        field(result, "returnValueRoots", "true", true);
        field(result, "protectedObjectReturns", "true", true);
        field(result, "protectedObjectReturnScope", Json.string("single-threaded-static-return-root-through-callee-safe-point-and-frame-pop"), true);
        field(result, "staticRootInventory", "true", true);
        field(result, "localRootInventory", "true", true);
        field(result, "rootScanning", "false", true);
        field(result, "rootModel", Json.string("generated-static-frame-return-and-expression-root-inventory-no-heap-scan"), true);
        field(result, "threadRoots", "false", true);
        field(result, "javaHeapAllocationsManaged", "false", true);
        field(result, "cAllocationOwnership", Json.string("explicit-free-for-runtime-temporaries-and-ffi-results"), true);
        field(result, "ffiMemory", Json.string("returned strings and byte arrays are javan-owned and released with javan_free"), true);
        field(result, "exceptions", Json.string("panic-and-limited-same-method-catch"), true);
        field(result, "threads", Json.string("none"), true);
        field(result, "sanitizerInstrumentation", Json.string("not-built"), true);
        field(result, "sanitizers", Json.string("not-enabled"), false);
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(final String artifactKind, final List<ArtifactReport> artifacts, final List<String> abiSymbols) {
        final StringBuilder result = new StringBuilder();
        result.append("# Runtime Contract").append('\n').append('\n');
        result.append("- artifact kind: `").append(artifactKind).append("`\n");
        result.append("- ABI symbols: `").append(join(abiSymbols)).append("`\n");
        result.append("- runtime packaging: `monolithic-c-runtime`\n");
        result.append("- runtime modules included: `").append(join(INCLUDED_RUNTIME_MODULES)).append("`\n");
        result.append("- memory model: `tracked-c-heap-safe-point-partial-gc`\n");
        result.append("- allocator: `tracked-calloc-free-at-shutdown`\n");
        result.append("- Java allocation ownership: `javan-owned-generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage-gc-eligible`\n");
        result.append("- FFI allocation ownership: `caller-frees-javan-owned-results-with-javan_free`\n");
        result.append("- temporary allocation ownership: `javan-owned-explicit-free`\n");
        result.append("- heap metadata: `true`\n");
        result.append("- heap metadata strategy: `allocation-ledger-kind-typeid-runtimekind-mark-collectible`\n");
        result.append("- heap accounting: `true`\n");
        result.append("- heap reclamation: `true`\n");
        result.append("- heap reclamation scope: `generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage`\n");
        result.append("- type descriptors: `true`\n");
        result.append("- object field descriptors: `true`\n");
        result.append("- frame root inventory: `true`\n");
        result.append("- managed heap: `false`\n");
        result.append("- gc: `partial-mark-sweep`\n");
        result.append("- gc strategy: `single-threaded-entry-statement-and-return-safe-point-generated-object-object-array-primitive-array-runtime-string-runtime-container-and-owned-container-storage-mark-sweep`\n");
        result.append("- gc stress: `metadata-verify-and-safe-point-collection`\n");
        result.append("- gc excluded allocation kinds: `explicit-runtime-temporaries, ffi-exports`\n");
        result.append("- runtime container traversal: `precise-rooted-runtime-container-mark-sweep`\n");
        result.append("- operand/call temporary roots: `true`\n");
        result.append("- operand/call temporary root model: `generated-expression-root-frame`\n");
        result.append("- operand/call temporary root scope: `object-call-arguments, nested-object-call-results, field-store-receiver, field-store-value, array-store-array, array-store-value, return-operand, object-print-operands`\n");
        result.append("- operand/call temporary root lifetime: `until-enclosing-generated-statement-or-return-completes`\n");
        result.append("- allocation-path collection: `true`\n");
        result.append("- allocation-path collection model: `allocator-gc-retry-before-out-of-memory`\n");
        result.append("- allocation-path collection scope: `generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage`\n");
        result.append("- allocation failure mode: `deterministic-native-panic`\n");
        result.append("- statement safe points: `true`\n");
        result.append("- statement safe point scope: `generated-label-and-non-terminal-statement-boundaries`\n");
        result.append("- return value roots: `true`\n");
        result.append("- protected object returns: `true`\n");
        result.append("- protected object return scope: `single-threaded-static-return-root-through-callee-safe-point-and-frame-pop`\n");
        result.append("- static root inventory: `true`\n");
        result.append("- local root inventory: `true`\n");
        result.append("- root scanning: `false`\n");
        result.append("- root model: `generated-static-frame-return-and-expression-root-inventory-no-heap-scan`\n");
        result.append("- thread roots: `false`\n");
        result.append("- Java heap allocations managed: `false`\n");
        result.append("- FFI memory: returned strings and byte arrays are javan-owned and released with `javan_free`\n");
        result.append("- exceptions: `panic-and-limited-same-method-catch`\n");
        result.append("- threads: `none`\n");
        result.append("- sanitizer instrumentation: `not-built`\n");
        result.append("- sanitizers: `not-enabled`\n\n");
        result.append("| artifact | bytes | linkage | system libraries | debug info | symbol table |\n");
        result.append("| --- | ---: | --- | --- | --- | --- |\n");
        for (final ArtifactReport artifact : artifacts) {
            result.append("| `").append(artifact.path().toString()).append("` | ")
                .append(artifact.bytes())
                .append(" | `").append(artifact.linkage()).append("` | `")
                .append(join(artifact.systemLibraries()))
                .append("` | `").append(artifact.debugInfo()).append("` | `")
                .append(artifact.symbolTable()).append("` |\n");
        }
        return result.toString();
    }

    private static String artifactsJson(final List<ArtifactReport> artifacts) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < artifacts.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final ArtifactReport artifact = artifacts.get(index);
            result.append("{")
                .append("\"path\": ").append(Json.string(artifact.path().toString())).append(", ")
                .append("\"bytes\": ").append(artifact.bytes()).append(", ")
                .append("\"linkage\": ").append(Json.string(artifact.linkage())).append(", ")
                .append("\"systemLibraries\": ").append(Json.stringList(artifact.systemLibraries())).append(", ")
                .append("\"debugInfo\": ").append(Json.string(artifact.debugInfo())).append(", ")
                .append("\"symbolTable\": ").append(Json.string(artifact.symbolTable()))
                .append("}");
        }
        result.append("]");
        return result.toString();
    }

    private static void field(final StringBuilder result, final String name, final String value, final boolean comma) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static List<String> lines(final String value) {
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '\n') {
                result.add(Strings2.slice(value, start, index));
                start = index + 1;
            }
        }
        return List.copyOf(result);
    }

    private static String beforeFirstSpace(final String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == ' ' || value.charAt(index) == '\t') {
                return Strings2.slice(value, 0, index);
            }
        }
        return value;
    }

    private static String linuxLibrary(final String line) {
        final String trimmed = Strings2.trimAscii(line);
        final int arrow = trimmed.indexOf("=>");
        if (arrow >= 0) {
            final String right = Strings2.trimAscii(Strings2.slice(trimmed, arrow + 2, trimmed.length()));
            return beforeFirstSpace(right);
        }
        return beforeFirstSpace(trimmed);
    }

    private static String join(final List<String> values) {
        if (values.isEmpty()) {
            return "-";
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    /**
     * Written runtime report.
     *
     * @param jsonPath JSON report path
     * @param markdownPath Markdown report path
     * @param artifacts inspected artifacts
     */
    public record Report(Path jsonPath, Path markdownPath, List<ArtifactReport> artifacts) {
    }

    /**
     * Native artifact metadata.
     *
     * @param path artifact path
     * @param bytes artifact size
     * @param linkage linkage kind
     * @param systemLibraries linked system libraries
     * @param debugInfo debug info status
     * @param symbolTable symbol table status
     */
    public record ArtifactReport(
        Path path,
        long bytes,
        String linkage,
        List<String> systemLibraries,
        String debugInfo,
        String symbolTable
    ) {
    }
}
