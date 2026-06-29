package javan.reporting;

import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads generated javan report files and writes a deterministic unified summary.
 */
public final class ReportSummarizer {
    private static final long NO_NUMBER = Long.MIN_VALUE;
    private static final List<ReportSpec> REPORTS = List.of(
        new ReportSpec("project", List.of("project.json")),
        new ReportSpec("diagnostics", List.of("diagnostics.txt", "diagnostics.json", "diagnostics.md")),
        new ReportSpec("threads", List.of("threads.json", "threads.md")),
        new ReportSpec("virtual-threads", List.of("virtual-threads.json", "virtual-threads.md")),
        new ReportSpec("runtime-profiling", List.of("runtime-profiling.json", "runtime-profiling.md")),
        new ReportSpec("reachability", List.of("reachability.txt")),
        new ReportSpec("exceptions", List.of("exceptions.json", "exceptions.md", "debug-map.json")),
        new ReportSpec("intrinsics", List.of("intrinsics.json", "intrinsics.md")),
        new ReportSpec("optimizations", List.of("optimizations.json", "optimizations.md")),
        new ReportSpec("dependencies", List.of("dependencies.json", "dependencies.md")),
        new ReportSpec("licenses", List.of("licenses.json", "licenses.md")),
        new ReportSpec("resources", List.of("resources.json", "resources.md")),
        new ReportSpec("library-build", List.of("library-build.json", "library-build.md")),
        new ReportSpec("deduplication", List.of("deduplication-plan.json", "deduplication-plan.md")),
        new ReportSpec("runtime-features", List.of("runtime-features.json", "runtime-features.md")),
        new ReportSpec("runtime", List.of("runtime.json", "runtime.md")),
        new ReportSpec("runtime-footprint", List.of("runtime-footprint.json", "runtime-footprint.md")),
        new ReportSpec("sanitizer-proof", List.of("sanitizer-proof.json", "sanitizer-proof.md")),
        new ReportSpec("compatibility", List.of("compatibility-summary.json", "compatibility-summary.md"))
    );

    /**
     * Writes {@code report.md} and {@code report.json} for an existing report directory.
     *
     * @param target project root, {@code .javan}, or {@code .javan/reports} path
     * @return written summary content and paths
     * @throws IOException when report files cannot be read or written
     */
    public Summary write(final Path target) throws IOException {
        final Path reportsDirectory = reportsDirectory(target);
        if (!Files.isDirectory(reportsDirectory)) {
            throw new IllegalArgumentException(
                new StringBuilder()
                    .append("No .javan/reports directory at ")
                    .append(reportsDirectory)
                    .toString()
            );
        }
        final List<ReportSection> sections = sections(reportsDirectory);
        final String markdown = markdown(reportsDirectory, sections);
        final String json = json(reportsDirectory, sections);
        final Path markdownPath = reportsDirectory.resolve("report.md");
        final Path jsonPath = reportsDirectory.resolve("report.json");
        Files2.writeString(markdownPath, markdown);
        Files2.writeString(jsonPath, json);
        return new Summary(reportsDirectory, markdownPath, jsonPath, markdown, json);
    }

    private static Path reportsDirectory(final Path target) {
        final Path normalized = target.toAbsolutePath().normalize();
        final Path fileName = normalized.getFileName();
        if (fileName != null && "reports".equals(fileName.toString())) {
            final Path parent = normalized.getParent();
            if (parent != null && parent.getFileName() != null && ".javan".equals(parent.getFileName().toString())) {
                return normalized;
            }
        }
        if (fileName != null && ".javan".equals(fileName.toString())) {
            return normalized.resolve("reports");
        }
        return normalized.resolve(".javan/reports");
    }

    private static List<ReportSection> sections(final Path reportsDirectory) throws IOException {
        final List<ReportSection> result = new ArrayList<>();
        for (final ReportSpec spec : REPORTS) {
            result.add(section(reportsDirectory, spec));
        }
        return List.copyOf(result);
    }

    private static ReportSection section(final Path reportsDirectory, final ReportSpec spec) throws IOException {
        final List<FileStatus> files = new ArrayList<>();
        for (final String filename : spec.files()) {
            final Path file = reportsDirectory.resolve(filename);
            if (Files.isRegularFile(file)) {
                files.add(new FileStatus(filename, true, Files.size(file)));
            } else {
                files.add(new FileStatus(filename, false, 0L));
            }
        }
        return new ReportSection(spec.name(), List.copyOf(files), metrics(reportsDirectory, spec.name()));
    }

    private static List<Metric> metrics(final Path reportsDirectory, final String name) throws IOException {
        if ("project".equals(name)) {
            return projectMetrics(read(reportsDirectory.resolve("project.json")));
        }
        if ("diagnostics".equals(name)) {
            return diagnosticsMetrics(read(reportsDirectory.resolve("diagnostics.txt")));
        }
        if ("threads".equals(name)) {
            return threadsMetrics(read(reportsDirectory.resolve("threads.json")));
        }
        if ("virtual-threads".equals(name)) {
            return virtualThreadMetrics(read(reportsDirectory.resolve("virtual-threads.json")));
        }
        if ("runtime-profiling".equals(name)) {
            return runtimeProfilingMetrics(read(reportsDirectory.resolve("runtime-profiling.json")));
        }
        if ("reachability".equals(name)) {
            return reachabilityMetrics(read(reportsDirectory.resolve("reachability.txt")));
        }
        if ("exceptions".equals(name)) {
            return exceptionMetrics(read(reportsDirectory.resolve("exceptions.json")), read(reportsDirectory.resolve("debug-map.json")));
        }
        if ("intrinsics".equals(name)) {
            return intrinsicsMetrics(read(reportsDirectory.resolve("intrinsics.json")));
        }
        if ("optimizations".equals(name)) {
            return optimizationMetrics(read(reportsDirectory.resolve("optimizations.json")));
        }
        if ("dependencies".equals(name)) {
            return dependencyMetrics(read(reportsDirectory.resolve("dependencies.json")));
        }
        if ("licenses".equals(name)) {
            return licenseMetrics(read(reportsDirectory.resolve("licenses.json")));
        }
        if ("resources".equals(name)) {
            return resourceMetrics(read(reportsDirectory.resolve("resources.json")));
        }
        if ("library-build".equals(name)) {
            return libraryBuildMetrics(read(reportsDirectory.resolve("library-build.json")));
        }
        if ("deduplication".equals(name)) {
            return deduplicationMetrics(read(reportsDirectory.resolve("deduplication-plan.json")));
        }
        if ("runtime-features".equals(name)) {
            return runtimeFeaturesMetrics(read(reportsDirectory.resolve("runtime-features.json")));
        }
        if ("runtime".equals(name)) {
            return runtimeMetrics(read(reportsDirectory.resolve("runtime.json")));
        }
        if ("runtime-footprint".equals(name)) {
            return runtimeFootprintMetrics(read(reportsDirectory.resolve("runtime-footprint.json")));
        }
        if ("sanitizer-proof".equals(name)) {
            return sanitizerProofMetrics(read(reportsDirectory.resolve("sanitizer-proof.json")));
        }
        if ("compatibility".equals(name)) {
            return compatibilityMetrics(read(reportsDirectory.resolve("compatibility-summary.json")));
        }
        return List.of();
    }

    private static Optional<String> read(final Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(path));
    }

    private static List<Metric> projectMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "buildTool");
        addText(result, value, "profile");
        addText(result, value, "outputName");
        addArrayCount(result, value, "sourceFolders");
        addArrayCount(result, value, "resourceFolders");
        addArrayCount(result, value, "classFolders");
        addArrayCount(result, value, "classpathEntries");
        addArrayCount(result, value, "warnings");
        return List.copyOf(result);
    }

    private static List<Metric> diagnosticsMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final long errors = countLinePrefix(value, "error[");
        final long warnings = countLinePrefix(value, "warning[");
        return List.of(
            Metric.number("diagnostics", errors + warnings),
            Metric.number("errors", errors),
            Metric.number("warnings", warnings)
        );
    }

    private static List<Metric> reachabilityMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        final Optional<String> entry = prefixedLine(value, "entry: ");
        if (entry.isPresent()) {
            result.add(Metric.text("entry", entry.orElseThrow()));
        }
        result.add(Metric.number("reachableMethods", reachableMethodCount(value)));
        return List.copyOf(result);
    }

    private static List<Metric> threadsMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addNumber(result, value, "diagnostics");
        addNumber(result, value, "errors");
        addNumber(result, value, "warnings");
        addNumber(result, value, "lifecycle");
        addNumber(result, value, "synchronization");
        addNumber(result, value, "concurrencyRuntime");
        addNumber(result, value, "blocking");
        addNumber(result, value, "threadStartSites");
        addNumber(result, value, "threadStartMethods");
        addNumber(result, value, "lifecycleMethods");
        addNumber(result, value, "blockingMethods");
        addNumber(result, value, "synchronizationMethods");
        addNumber(result, value, "concurrencyRuntimeMethods");
        addNumber(result, value, "unknownBlockingMethods");
        addNumber(result, value, "unsupportedThreadTaskMethods");
        addNumber(result, value, "sleepWaits");
        addNumber(result, value, "joinWaits");
        addNumber(result, value, "blockingTaskMethods");
        addNumber(result, value, "cpuBoundTaskMethods");
        addNumber(result, value, "tinyCpuTaskMethods");
        addNumber(result, value, "pinningRiskMethods");
        addNumber(result, value, "unknownTaskMethods");
        addNumber(result, value, "ioSignalMethods");
        addNumber(result, value, "taskRoots");
        addNumber(result, value, "threadStartRoots");
        addNumber(result, value, "blockingRoots");
        addNumber(result, value, "pinningRiskRoots");
        addNumber(result, value, "unsupportedRuntimeRoots");
        addNumber(result, value, "lifecycleRiskRoots");
        addNumber(result, value, "unknownRoots");
        addArrayCount(result, value, "methods");
        addArrayCount(result, value, "roots");
        addArrayCount(result, value, "items");
        return List.copyOf(result);
    }

    private static List<Metric> intrinsicsMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addArrayCount(result, value, "intrinsics");
        addArrayNumberSum(result, value, "intrinsics", "count", "intrinsicCallSites");
        addArrayCount(result, value, "runtimeCalls");
        addArrayNumberSum(result, value, "runtimeCalls", "count", "runtimeCallSites");
        addArrayCount(result, value, "supportedDirectJdkCalls");
        addNumber(result, value, "supportedDirectJdkCallSiteCount");
        addNumber(result, value, "supportedJdkCallSiteCount");
        addNumber(result, value, "unsupportedJdkCallCandidateCount");
        addArrayCount(result, value, "unsupportedJdkCallCandidates");
        return List.copyOf(result);
    }

    private static List<Metric> virtualThreadMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "status");
        addBoolean(result, value, "runtimeSupported");
        addBoolean(result, value, "profilingSupported");
        addBoolean(result, value, "profilingCollected");
        addBoolean(result, value, "schedulerImplemented");
        addBoolean(result, value, "carrierPoolImplemented");
        addBoolean(result, value, "threadModelImplemented");
        addBoolean(result, value, "threadLocalImplemented");
        addBoolean(result, value, "blockingIoAware");
        addText(result, value, "reachableApiScan");
        addNumber(result, value, "reachableVirtualStartSites");
        addNumber(result, value, "reachableVirtualStartMethods");
        addNumber(result, value, "reachableIsVirtualSites");
        addNumber(result, value, "unsupportedBuilderApis");
        addNumber(result, value, "unsupportedBuilderApisReachable");
        addNumber(result, value, "unsupportedBuilderApisUnreachable");
        addNumber(result, value, "unsupportedExecutorApis");
        addNumber(result, value, "unsupportedExecutorApisReachable");
        addNumber(result, value, "unsupportedExecutorApisUnreachable");
        addText(result, value, "diagnosticSource");
        addNumber(result, value, "reasonCount");
        addText(result, value, "nextGate");
        addArrayCount(result, value, "reasons");
        return List.copyOf(result);
    }

    private static List<Metric> optimizationMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addNumber(result, value, "redundantNullChecks");
        addNumber(result, value, "redundantBoundsChecks");
        addNumber(result, value, "redundantTypeChecks");
        addNumber(result, value, "redundantRangeChecks");
        addNumber(result, value, "deadBranches");
        addNumber(result, value, "specializedMethods");
        addNumber(result, value, "skippedCandidates");
        return List.copyOf(result);
    }

    private static List<Metric> resourceMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addNumber(result, value, "resourceCount");
        addArrayNumberSum(result, value, "resources", "size", "resourceBytes");
        return List.copyOf(result);
    }

    private static List<Metric> dependencyMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addNumber(result, value, "dependencyCount");
        addNumber(result, value, "presentDependencies");
        addNumber(result, value, "missingDependencies");
        addNumber(result, value, "usedDependencies");
        addNumber(result, value, "unusedDependencies");
        addNumber(result, value, "reachableDependencyClasses");
        addArrayCount(result, value, "dependencies");
        return List.copyOf(result);
    }

    private static List<Metric> licenseMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addNumber(result, value, "licenseCount");
        addNumber(result, value, "knownLicenses");
        addNumber(result, value, "unknownLicenses");
        addNumber(result, value, "warningLicenses");
        addNumber(result, value, "blockedLicenses");
        addArrayCount(result, value, "licenses");
        return List.copyOf(result);
    }

    private static List<Metric> exceptionMetrics(final Optional<String> exceptions, final Optional<String> debugMap) {
        final List<Metric> result = new ArrayList<>();
        if (exceptions.isPresent()) {
            addNumber(result, exceptions.orElseThrow(), "panicSites");
            addArrayCount(result, exceptions.orElseThrow(), "sites");
        }
        if (debugMap.isPresent()) {
            addNumber(result, debugMap.orElseThrow(), "debugEntries");
        }
        return List.copyOf(result);
    }

    private static List<Metric> libraryBuildMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addNumber(result, value, "abiVersion");
        addText(result, value, "stringOwnership");
        addText(result, value, "byteArrayOwnership");
        addText(result, value, "errorResultAbi");
        addText(result, value, "exceptionMapping");
        addText(result, value, "threadRuntimeRules");
        addText(result, value, "generatedAbiTests");
        addNumber(result, value, "inputClasses");
        addNumber(result, value, "inputMethods");
        addNumber(result, value, "reachableClassesFromExports");
        addNumber(result, value, "reachableMethodsFromExports");
        addNumber(result, value, "exportedMethods");
        addArrayCount(result, value, "artifacts");
        addNumber(result, value, "artifactBytes");
        addArrayCount(result, value, "runtimeModulesLinked");
        addNumber(result, value, "dependencyReductionMethods");
        addArrayCount(result, value, "bindings");
        return List.copyOf(result);
    }

    private static List<Metric> deduplicationMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addArrayCount(result, value, "runtimeModules");
        addNumber(result, value, "deduplicatedStringLiterals");
        addArrayCount(result, value, "arrayHelperFamilies");
        addArrayCount(result, value, "boundsCheckHelpers");
        return List.copyOf(result);
    }

    private static List<Metric> runtimeFeaturesMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "status");
        addText(result, value, "containment");
        addText(result, value, "optimize");
        addBoolean(result, value, "profiling");
        addArrayText(result, value, "reachableRuntimeModules", "reachableRuntimeModuleNames");
        addArrayText(result, value, "disabledRuntimeModules", "disabledRuntimeModuleNames");
        addArrayCount(result, value, "reachableRuntimeModules");
        addArrayCount(result, value, "disabledRuntimeModules");
        addArrayCount(result, value, "disabledReachableRuntimeModules");
        addArrayCount(result, value, "disabledUnusedRuntimeModules");
        addArrayCount(result, value, "unknownDisabledRuntimeModules");
        return List.copyOf(result);
    }

    private static List<Metric> runtimeProfilingMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "status");
        addBoolean(result, value, "requested");
        addBoolean(result, value, "enabled");
        addText(result, value, "collectionState");
        addText(result, value, "reason");
        addArrayText(result, value, "disabledProfilingModules", "disabledProfilingModuleNames");
        addArrayCount(result, value, "disabledProfilingModules");
        addNumber(result, value, "platformThreadObjectsCreated");
        addNumber(result, value, "virtualThreadObjectsCreated");
        addNumber(result, value, "threadStartCalls");
        addNumber(result, value, "threadCompletions");
        addNumber(result, value, "threadJoinCalls");
        addNumber(result, value, "threadJoinInterruptions");
        addNumber(result, value, "threadInterruptCalls");
        addNumber(result, value, "threadParkCalls");
        addNumber(result, value, "threadParkNanosCalls");
        addNumber(result, value, "threadParkUntilCalls");
        addNumber(result, value, "threadUnparkCalls");
        addNumber(result, value, "threadLocalGetCalls");
        addNumber(result, value, "threadLocalSetCalls");
        addNumber(result, value, "threadLocalRemoveCalls");
        addNumber(result, value, "executorExecuteCalls");
        return List.copyOf(result);
    }

    private static List<Metric> compatibilityMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "status");
        addNumber(result, value, "javaFeatureVersion");
        addNumber(result, value, "projectClasses");
        addNumber(result, value, "jdkClasses");
        addNestedNumber(result, value, "exactSupportedJdkCallables", "classes", "exactSupportedJdkCallableClasses");
        addNestedNumber(result, value, "exactSupportedJdkCallables", "constructors", "exactSupportedJdkConstructors");
        addNestedNumber(result, value, "exactSupportedJdkCallables", "methods", "exactSupportedJdkMethods");
        addNestedNumber(result, value, "exactSupportedJdkCallables", "callables", "exactSupportedJdkCallables");
        addNestedNumber(result, value, "exactSupportedJdkCallables", "totalCallables", "totalJdkCallables");
        addNestedNumber(result, value, "exactSupportedJdkCallables", "leftCallables", "leftJdkCallables");
        addNestedText(result, value, "exactSupportedJdkCallables", "coveragePercent", "exactSupportedJdkCallableCoveragePercent");
        addNestedNumber(result, value, "exactJdkCallableAccounting", "supportedCallables", "accountedSupportedJdkCallables");
        addNestedNumber(result, value, "exactJdkCallableAccounting", "explicitRejectedCallables", "accountedRejectedJdkCallables");
        addNestedNumber(result, value, "exactJdkCallableAccounting", "doneCallables", "accountedDoneJdkCallables");
        addNestedNumber(result, value, "exactJdkCallableAccounting", "unknownCallables", "unknownJdkCallables");
        addNestedNumber(result, value, "exactJdkCallableAccounting", "totalCallables", "accountingTotalJdkCallables");
        addNestedText(result, value, "exactJdkCallableAccounting", "donePercent", "accountedDoneJdkCallablePercent");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "reachableCurrentThreadLifecycle", "flowQualifiedReachableCurrentThreadLifecycleRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "unreachableCurrentThreadLifecycle", "flowQualifiedUnreachableCurrentThreadLifecycleRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "reachableThreadBuilderReceiverShape", "flowQualifiedReachableThreadBuilderReceiverRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "unreachableThreadBuilderReceiverShape", "flowQualifiedUnreachableThreadBuilderReceiverRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "reachableVirtualThreadFactoryShape", "flowQualifiedReachableVirtualThreadFactoryRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "unreachableVirtualThreadFactoryShape", "flowQualifiedUnreachableVirtualThreadFactoryRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "reachableExecutorReceiverShape", "flowQualifiedReachableExecutorReceiverRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "unreachableExecutorReceiverShape", "flowQualifiedUnreachableExecutorReceiverRejects");
        addNestedNumber(result, value, "flowQualifiedRejectedJdkCalls", "total", "flowQualifiedRejectedJdkCallShapes");
        addNumber(result, value, "supportRows");
        addNumber(result, value, "passRows");
        addNumber(result, value, "scopedRows");
        addNumber(result, value, "targetRows");
        addNumber(result, value, "rejectedRows");
        addNumber(result, value, "accountedRows");
        addNumber(result, value, "unaccountedRows");
        addNumber(result, value, "diagnosticErrors");
        addNumber(result, value, "recognizedRejectedOpcodeUses");
        addNumber(result, value, "unknownFatalOpcodeUses");
        return List.copyOf(result);
    }

    private static List<Metric> runtimeMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "artifactKind");
        addArrayCount(result, value, "artifacts");
        addArrayNumberSum(result, value, "artifacts", "bytes", "artifactBytes");
        addArrayCount(result, value, "abiSymbols");
        addText(result, value, "runtimePackaging");
        addArrayCount(result, value, "runtimeModulesIncluded");
        addText(result, value, "memoryModel");
        addText(result, value, "allocator");
        addText(result, value, "javaAllocationOwnership");
        addText(result, value, "ffiAllocationOwnership");
        addText(result, value, "temporaryAllocationOwnership");
        addBoolean(result, value, "heapMetadata");
        addText(result, value, "heapMetadataStrategy");
        addBoolean(result, value, "heapAccounting");
        addBoolean(result, value, "heapReclamation");
        addText(result, value, "heapReclamationScope");
        addBoolean(result, value, "typeDescriptors");
        addBoolean(result, value, "objectFieldDescriptors");
        addBoolean(result, value, "frameRootInventory");
        addBoolean(result, value, "managedHeap");
        addText(result, value, "gc");
        addText(result, value, "gcStrategy");
        addText(result, value, "gcStress");
        addArrayCount(result, value, "gcExcludedAllocationKinds");
        addText(result, value, "runtimeContainerTraversal");
        addBoolean(result, value, "ownedBufferReferenceValidation");
        addText(result, value, "ownedBufferReferenceValidationScope");
        addBoolean(result, value, "operandCallTemporaryRoots");
        addText(result, value, "operandCallTemporaryRootModel");
        addArrayCount(result, value, "operandCallTemporaryRootScope");
        addText(result, value, "operandCallTemporaryRootLifetime");
        addBoolean(result, value, "allocationPathCollection");
        addText(result, value, "allocationPathCollectionModel");
        addText(result, value, "allocationPathCollectionScope");
        addText(result, value, "allocationFailureMode");
        addBoolean(result, value, "statementSafePoints");
        addText(result, value, "statementSafePointScope");
        addBoolean(result, value, "returnValueRoots");
        addBoolean(result, value, "protectedObjectReturns");
        addText(result, value, "protectedObjectReturnScope");
        addBoolean(result, value, "staticRootInventory");
        addBoolean(result, value, "localRootInventory");
        addBoolean(result, value, "localRootLiveness");
        addText(result, value, "localRootLivenessModel");
        addBoolean(result, value, "rootScanning");
        addText(result, value, "rootModel");
        addBoolean(result, value, "threadRoots");
        addBoolean(result, value, "threadRootRegistry");
        addText(result, value, "threadRootScope");
        addBoolean(result, value, "threadLifecycleInventory");
        addText(result, value, "threadLifecycleInventoryScope");
        addBoolean(result, value, "javaHeapAllocationsManaged");
        addText(result, value, "exceptions");
        addText(result, value, "threads");
        addText(result, value, "sanitizerInstrumentation");
        addText(result, value, "sanitizers");
        return List.copyOf(result);
    }

    private static List<Metric> runtimeFootprintMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "hostTarget");
        addText(result, value, "requestedTarget");
        addText(result, value, "actualTarget");
        addArrayCount(result, value, "artifacts");
        addArrayNumberSum(result, value, "artifacts", "bytes", "artifactBytes");
        addArrayCount(result, value, "footprints");
        addArrayCount(result, value, "osArchCoverage");
        return List.copyOf(result);
    }

    private static List<Metric> sanitizerProofMetrics(final Optional<String> report) {
        if (report.isEmpty()) {
            return List.of();
        }
        final String value = report.orElseThrow();
        final List<Metric> result = new ArrayList<>();
        addText(result, value, "status");
        addText(result, value, "kind");
        addText(result, value, "leakDetection");
        addBoolean(result, value, "sanitizerRequired");
        addBoolean(result, value, "counterCheck");
        addBoolean(result, value, "failureSignatures");
        addNumber(result, value, "expectedExit");
        addNumber(result, value, "actualExit");
        addNumber(result, value, "actualLiveAllocations");
        addNumber(result, value, "actualLiveBytes");
        addNumber(result, value, "actualPeakLiveBytes");
        addNumber(result, value, "actualTotalAllocations");
        addNumber(result, value, "actualGcCollections");
        addNumber(result, value, "actualGcCollectedAllocations");
        addNumber(result, value, "actualGcCollectedBytes");
        addNumber(result, value, "actualThreadObjects");
        addNumber(result, value, "actualStartedThreads");
        addNumber(result, value, "actualCompletedThreads");
        addNumber(result, value, "actualActiveThreads");
        addNumber(result, value, "actualThreadsWithTarget");
        addNumber(result, value, "actualCurrentThreadRootPresent");
        addNumber(result, value, "actualRootFrameDepth");
        addNumber(result, value, "actualFrameRootCount");
        addNumber(result, value, "maxLiveAllocations");
        addNumber(result, value, "maxLiveBytes");
        addNumber(result, value, "maxPeakLiveBytes");
        addNumber(result, value, "minTotalAllocations");
        addNumber(result, value, "minGcCollections");
        addNumber(result, value, "minGcCollectedAllocations");
        addNumber(result, value, "minGcCollectedBytes");
        addNumber(result, value, "maxRootFrameDepth");
        addNumber(result, value, "maxFrameRootCount");
        addArrayCount(result, value, "probes");
        return List.copyOf(result);
    }

    private static void addText(final List<Metric> result, final String report, final String name) {
        final Optional<String> value = stringField(report, name);
        if (value.isPresent()) {
            result.add(Metric.text(name, value.orElseThrow()));
        }
    }

    private static void addNumber(final List<Metric> result, final String report, final String name) {
        final long value = numberField(report, name);
        if (value != NO_NUMBER) {
            result.add(Metric.number(name, value));
        }
    }

    private static void addBoolean(final List<Metric> result, final String report, final String name) {
        final Optional<String> value = booleanField(report, name);
        if (value.isPresent()) {
            result.add(Metric.text(name, value.orElseThrow()));
        }
    }

    private static void addArrayCount(final List<Metric> result, final String report, final String name) {
        final long value = arrayCount(report, name);
        if (value != NO_NUMBER) {
            result.add(Metric.number(name, value));
        }
    }

    private static void addArrayText(final List<Metric> result, final String report, final String name, final String metricName) {
        final Optional<String> value = stringArrayText(report, name);
        if (value.isPresent()) {
            result.add(Metric.text(metricName, value.orElseThrow()));
        }
    }

    private static void addArrayNumberSum(
        final List<Metric> result,
        final String report,
        final String arrayName,
        final String fieldName,
        final String metricName
    ) {
        final Optional<String> body = arrayBody(report, arrayName);
        if (body.isPresent()) {
            result.add(Metric.number(metricName, sumNumberFields(body.orElseThrow(), fieldName)));
        }
    }

    private static void addNestedNumber(
        final List<Metric> result,
        final String report,
        final String objectName,
        final String fieldName,
        final String metricName
    ) {
        final Optional<String> body = objectBody(report, objectName);
        if (body.isEmpty()) {
            return;
        }
        final long value = numberField(body.orElseThrow(), fieldName);
        if (value != NO_NUMBER) {
            result.add(Metric.number(metricName, value));
        }
    }

    private static void addNestedText(
        final List<Metric> result,
        final String report,
        final String objectName,
        final String fieldName,
        final String metricName
    ) {
        final Optional<String> body = objectBody(report, objectName);
        if (body.isEmpty()) {
            return;
        }
        final Optional<String> value = stringField(body.orElseThrow(), fieldName);
        if (value.isPresent()) {
            result.add(Metric.text(metricName, value.orElseThrow()));
        }
    }

    private static Optional<String> stringField(final String report, final String name) {
        final int valueStart = fieldValueStart(report, name, 0);
        if (valueStart < 0 || valueStart >= report.length() || report.charAt(valueStart) != '"') {
            return Optional.empty();
        }
        final StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int index = valueStart + 1; index < report.length(); index++) {
            final char ch = report.charAt(index);
            if (escaped) {
                appendEscaped(result, ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return Optional.of(result.toString());
            } else {
                result.append(ch);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> booleanField(final String report, final String name) {
        final int valueStart = fieldValueStart(report, name, 0);
        if (valueStart < 0 || valueStart >= report.length()) {
            return Optional.empty();
        }
        if (matchesAt(report, valueStart, "true")) {
            return Optional.of("true");
        }
        if (matchesAt(report, valueStart, "false")) {
            return Optional.of("false");
        }
        return Optional.empty();
    }

    private static boolean matchesAt(final String value, final int start, final String expected) {
        if (start < 0 || start > value.length() - expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            if (value.charAt(start + index) != expected.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static long numberField(final String report, final String name) {
        final int valueStart = fieldValueStart(report, name, 0);
        return numberAt(report, valueStart);
    }

    private static long numberAt(final String value, final int start) {
        if (start < 0 || start >= value.length()) {
            return NO_NUMBER;
        }
        int index = start;
        final boolean negative = value.charAt(index) == '-';
        if (value.charAt(index) == '-') {
            index++;
        }
        final int digitsStart = index;
        long result = 0L;
        while (index < value.length() && isDigit(value.charAt(index))) {
            result = (result * 10L) + (long) (value.charAt(index) - '0');
            index++;
        }
        if (index == digitsStart) {
            return NO_NUMBER;
        }
        if (negative) {
            return 0L - result;
        }
        return result;
    }

    private static long arrayCount(final String report, final String name) {
        final Optional<String> body = arrayBody(report, name);
        if (body.isEmpty()) {
            return NO_NUMBER;
        }
        final String value = body.orElseThrow();
        if (Strings2.isBlank(value)) {
            return 0L;
        }
        long count = 1L;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (quoted && ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (!quoted && (ch == '[' || ch == '{')) {
                depth++;
            } else if (!quoted && (ch == ']' || ch == '}')) {
                depth--;
            } else if (!quoted && depth == 0 && ch == ',') {
                count++;
            }
        }
        return count;
    }

    private static Optional<String> arrayBody(final String report, final String name) {
        final int valueStart = fieldValueStart(report, name, 0);
        if (valueStart < 0 || valueStart >= report.length() || report.charAt(valueStart) != '[') {
            return Optional.empty();
        }
        final int valueEnd = arrayEnd(report, valueStart);
        if (valueEnd < 0) {
            return Optional.empty();
        }
        return Optional.of(Strings2.slice(report, valueStart + 1, valueEnd));
    }

    private static Optional<String> objectBody(final String report, final String name) {
        final int valueStart = fieldValueStart(report, name, 0);
        if (valueStart < 0 || valueStart >= report.length() || report.charAt(valueStart) != '{') {
            return Optional.empty();
        }
        final int valueEnd = objectEnd(report, valueStart);
        if (valueEnd < 0) {
            return Optional.empty();
        }
        return Optional.of(Strings2.slice(report, valueStart + 1, valueEnd));
    }

    private static Optional<String> stringArrayText(final String report, final String name) {
        final Optional<String> body = arrayBody(report, name);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        final List<String> values = new ArrayList<>();
        final String value = body.orElseThrow();
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '"') {
                continue;
            }
            final StringBuilder item = new StringBuilder();
            boolean escaped = false;
            index++;
            while (index < value.length()) {
                final char ch = value.charAt(index);
                if (escaped) {
                    item.append(ch);
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    break;
                } else {
                    item.append(ch);
                }
                index++;
            }
            values.add(item.toString());
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(join(", ", values));
    }

    private static int arrayEnd(final String value, final int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (quoted && ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (!quoted && ch == '[') {
                depth++;
            } else if (!quoted && ch == ']') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static int objectEnd(final String value, final int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (quoted && ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (!quoted && ch == '{') {
                depth++;
            } else if (!quoted && ch == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static long sumNumberFields(final String report, final String name) {
        long result = 0L;
        int offset = 0;
        while (offset < report.length()) {
            final int valueStart = fieldValueStart(report, name, offset);
            if (valueStart < 0) {
                return result;
            }
            final long value = numberAt(report, valueStart);
            if (value != NO_NUMBER) {
                result += value;
            }
            offset = valueStart + 1;
        }
        return result;
    }

    private static int fieldValueStart(final String report, final String name, final int offset) {
        final String field = new StringBuilder().append('"').append(name).append('"').toString();
        int search = offset;
        while (search < report.length()) {
            final int fieldStart = report.indexOf(field, search);
            if (fieldStart < 0) {
                return -1;
            }
            int index = fieldStart + field.length();
            index = skipWhitespace(report, index);
            if (index < report.length() && report.charAt(index) == ':') {
                return skipWhitespace(report, index + 1);
            }
            search = fieldStart + field.length();
        }
        return -1;
    }

    private static void appendEscaped(final StringBuilder result, final char ch) {
        switch (ch) {
            case 'n' -> result.append('\n');
            case 'r' -> result.append('\r');
            case 't' -> result.append('\t');
            case '"' -> result.append('"');
            case '\\' -> result.append('\\');
            default -> result.append(ch);
        }
    }

    private static long countLinePrefix(final String value, final String prefix) {
        long count = 0L;
        int lineStart = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '\n') {
                if (startsWith(value, lineStart, index, prefix)) {
                    count++;
                }
                lineStart = index + 1;
            }
        }
        return count;
    }

    private static Optional<String> prefixedLine(final String value, final String prefix) {
        int lineStart = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '\n') {
                if (startsWith(value, lineStart, index, prefix)) {
                    return Optional.of(Strings2.slice(value, lineStart + prefix.length(), trimCarriageReturn(value, index)));
                }
                lineStart = index + 1;
            }
        }
        return Optional.empty();
    }

    private static long reachableMethodCount(final String value) {
        long count = 0L;
        boolean reachable = false;
        int lineStart = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '\n') {
                if (startsWith(value, lineStart, index, "reachable:")) {
                    reachable = true;
                } else if (reachable && startsWith(value, lineStart, index, "  ")) {
                    count++;
                }
                lineStart = index + 1;
            }
        }
        return count;
    }

    private static boolean startsWith(final String value, final int start, final int end, final String prefix) {
        if (end - start < prefix.length()) {
            return false;
        }
        for (int index = 0; index < prefix.length(); index++) {
            if (value.charAt(start + index) != prefix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int trimCarriageReturn(final String value, final int end) {
        if (end > 0 && value.charAt(end - 1) == '\r') {
            return end - 1;
        }
        return end;
    }

    private static String join(final String delimiter, final List<String> values) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(delimiter);
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static int skipWhitespace(final String value, final int start) {
        int index = start;
        while (index < value.length()) {
            final char ch = value.charAt(index);
            if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') {
                return index;
            }
            index++;
        }
        return index;
    }

    private static boolean isDigit(final char ch) {
        if (ch < '0') {
            return false;
        }
        if (ch > '9') {
            return false;
        }
        return true;
    }

    private static String markdown(final Path reportsDirectory, final List<ReportSection> sections) {
        final StringBuilder result = new StringBuilder();
        result.append("# Javan Report").append('\n').append('\n');
        result.append("Reports directory: `").append(reportsDirectory.toString()).append("`").append('\n').append('\n');
        result.append("Known report families: `").append(countStatus(sections, "present")).append("` present, `")
            .append(countStatus(sections, "partial")).append("` partial, `")
            .append(countStatus(sections, "absent")).append("` absent.").append('\n').append('\n');
        result.append("| report | status | files | metrics |").append('\n');
        result.append("| --- | --- | --- | --- |").append('\n');
        for (final ReportSection section : sections) {
            result.append("| `").append(section.name()).append("` | ")
                .append(section.status())
                .append(" | ")
                .append(filesMarkdown(section.files()))
                .append(" | ")
                .append(metricsMarkdown(section.metrics()))
                .append(" |").append('\n');
        }
        return result.toString();
    }

    private static String filesMarkdown(final List<FileStatus> files) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < files.size(); index++) {
            if (index > 0) {
                result.append("; ");
            }
            final FileStatus file = files.get(index);
            if (file.present()) {
                result.append('`').append(file.name()).append("` (").append(file.bytes()).append(" bytes)");
            } else {
                result.append("missing `").append(file.name()).append('`');
            }
        }
        return result.toString();
    }

    private static String metricsMarkdown(final List<Metric> metrics) {
        if (metrics.isEmpty()) {
            return "-";
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < metrics.size(); index++) {
            if (index > 0) {
                result.append("; ");
            }
            final Metric metric = metrics.get(index);
            result.append(metric.name()).append(": `").append(metric.display()).append('`');
        }
        return result.toString();
    }

    private static String json(final Path reportsDirectory, final List<ReportSection> sections) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"reportsDirectory\": ").append(Json.string(reportsDirectory.toString())).append(",\n");
        result.append("  \"presentFamilyCount\": ").append(countStatus(sections, "present")).append(",\n");
        result.append("  \"partialFamilyCount\": ").append(countStatus(sections, "partial")).append(",\n");
        result.append("  \"absentFamilyCount\": ").append(countStatus(sections, "absent")).append(",\n");
        result.append("  \"reports\": [\n");
        for (int index = 0; index < sections.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            appendSectionJson(result, sections.get(index));
        }
        result.append("\n  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static void appendSectionJson(final StringBuilder result, final ReportSection section) {
        result.append("    {\"name\": ").append(Json.string(section.name()))
            .append(", \"status\": ").append(Json.string(section.status()))
            .append(", \"files\": [");
        for (int index = 0; index < section.files().size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final FileStatus file = section.files().get(index);
            result.append("{\"name\": ").append(Json.string(file.name()))
                .append(", \"present\": ").append(file.present())
                .append(", \"bytes\": ").append(file.bytes())
                .append("}");
        }
        result.append("], \"metrics\": {");
        for (int index = 0; index < section.metrics().size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final Metric metric = section.metrics().get(index);
            result.append(Json.string(metric.name())).append(": ").append(metric.jsonValue());
        }
        result.append("}}");
    }

    private static long countStatus(final List<ReportSection> sections, final String status) {
        long count = 0L;
        for (final ReportSection section : sections) {
            if (section.status().equals(status)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Written unified report summary.
     *
     * @param reportsDirectory source report directory
     * @param markdownPath written Markdown summary
     * @param jsonPath written JSON summary
     * @param markdown Markdown content
     * @param json JSON content
     */
    public record Summary(Path reportsDirectory, Path markdownPath, Path jsonPath, String markdown, String json) {
    }

    private record ReportSpec(String name, List<String> files) {
    }

    private record ReportSection(String name, List<FileStatus> files, List<Metric> metrics) {
        private String status() {
            int present = 0;
            for (final FileStatus file : files) {
                if (file.present()) {
                    present++;
                }
            }
            if (present == 0) {
                return "absent";
            }
            if (present == files.size()) {
                return "present";
            }
            return "partial";
        }
    }

    private record FileStatus(String name, boolean present, long bytes) {
    }

    private record Metric(String name, boolean number, String text, long value) {
        private static Metric number(final String name, final long value) {
            return new Metric(name, true, "", value);
        }

        private static Metric text(final String name, final String value) {
            return new Metric(name, false, value, 0L);
        }

        private String jsonValue() {
            if (number) {
                return Long.toString(value);
            }
            return Json.string(text);
        }

        private String display() {
            if (number) {
                return Long.toString(value);
            }
            return text;
        }
    }
}
