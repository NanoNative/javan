package javan.reporting;

import javan.analysis.CallEdge;
import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes thread and concurrency diagnostic reports derived from the static verifier.
 */
public final class ThreadReports {
    /**
     * Writes {@code threads.json} and {@code threads.md}.
     *
     * @param reportsDirectory {@code .javan/reports} directory
     * @param diagnostics full verifier diagnostics
     * @throws IOException when writing fails
     */
    public void write(final Path reportsDirectory, final List<Diagnostic> diagnostics) throws IOException {
        write(reportsDirectory, diagnostics, summarize(diagnostics));
    }

    /**
     * Writes {@code threads.json} and {@code threads.md} including reachable-code thread summary metrics.
     *
     * @param reportsDirectory {@code .javan/reports} directory
     * @param diagnostics full verifier diagnostics
     * @param summary reachable thread summary
     * @throws IOException when writing fails
     */
    public void write(final Path reportsDirectory, final List<Diagnostic> diagnostics, final Summary summary) throws IOException {
        final List<DiagnosticItem> items = relevant(diagnostics);
        Files2.writeString(reportsDirectory.resolve("threads.json"), json(items, summary));
        Files2.writeString(reportsDirectory.resolve("threads.md"), markdown(items, summary));
    }

    /**
     * Builds a small summary of reachable thread behavior from the closed-world call graph.
     *
     * @param diagnostics full verifier diagnostics
     * @param classes scanned classes
     * @param callGraph reachable methods and caller edges
     * @return summary
     */
    public static Summary summarize(
        final List<Diagnostic> diagnostics,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) {
        long threadStartSites = 0L;
        long threadStartMethods = 0L;
        final List<MethodActivity> methods = new ArrayList<>(diagnosticOnlyMethods(diagnostics));
        for (final EntryPoint entry : callGraph.reachableMethods()) {
            final Optional<MethodInfo> method = method(classes, entry);
            if (method.isEmpty()) {
                continue;
            }
            final Optional<CodeAttribute> code = method.orElseThrow().code();
            if (code.isEmpty()) {
                continue;
            }
            boolean startsThread = false;
            final CodeAttribute methodCode = code.orElseThrow();
            for (final Instruction instruction : methodCode.instructions()) {
                if (invokesThreadStart(instruction)) {
                    threadStartSites++;
                    startsThread = true;
                }
            }
            if (startsThread) {
                threadStartMethods++;
            }
            final String methodName = entry.methodName() + entry.descriptor();
            final int index = methodIndex(methods, entry.className(), methodName);
            final MethodStats stats = methodStats(methodCode);
            if (index >= 0) {
                methods.set(index, methods.get(index).withReachableCode(threadStartSitesInMethod(methodCode), stats));
            } else {
                final long methodThreadStartSites = threadStartSitesInMethod(methodCode);
                if (methodThreadStartSites > 0) {
                    methods.add(new MethodActivity(
                        entry.className(),
                        methodName,
                        methodThreadStartSites,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        0L,
                        stats.estimatedInstructions(),
                        stats.allocationSites(),
                        stats.ioCallSites(),
                        stats.hasLoop(),
                        TaskClass.UNKNOWN.name()
                    ).refreshClassification());
                }
            }
        }
        sortMethods(methods);
        final List<TaskRoot> roots = taskRoots(methods, callGraph.callEdges());
        return new Summary(
            threadStartSites,
            threadStartMethods,
            methodCountWith(methods, ActivityField.LIFECYCLE),
            methodCountWith(methods, ActivityField.BLOCKING),
            methodCountWith(methods, ActivityField.SYNCHRONIZATION),
            methodCountWith(methods, ActivityField.CONCURRENCY_RUNTIME),
            methodCountWith(methods, ActivityField.UNKNOWN_BLOCKING),
            methodCountWith(methods, ActivityField.UNSUPPORTED_THREAD_TASK),
            classificationCount(methods, TaskClass.BLOCKING_WAIT),
            classificationCount(methods, TaskClass.CPU_BOUND),
            classificationCount(methods, TaskClass.TINY_CPU_TASK),
            classificationCount(methods, TaskClass.IO_BOUND),
            classificationCount(methods, TaskClass.MIXED),
            classificationCount(methods, TaskClass.PINNING_RISK),
            classificationCount(methods, TaskClass.UNKNOWN),
            ioSignalCount(methods),
            roots.size(),
            rootKindCount(roots, RootKind.THREAD_START),
            rootKindCount(roots, RootKind.BLOCKING_WAIT),
            rootKindCount(roots, RootKind.PINNING_RISK),
            rootKindCount(roots, RootKind.UNSUPPORTED_RUNTIME),
            rootKindCount(roots, RootKind.LIFECYCLE_RISK),
            rootKindCount(roots, RootKind.UNKNOWN),
            List.copyOf(methods),
            roots
        );
    }

    /**
     * Builds a reachable thread summary without caller edges.
     *
     * @param diagnostics full verifier diagnostics
     * @param classes scanned classes
     * @param reachableMethods reachable methods
     * @return summary
     */
    public static Summary summarize(
        final List<Diagnostic> diagnostics,
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        return summarize(
            diagnostics,
            classes,
            new CallGraph(new EntryPoint("", "", ""), reachableMethods, List.of(), List.of())
        );
    }

    /**
     * Builds the diagnostic-only portion of the thread summary when no reachable-method context is available.
     *
     * @param diagnostics full verifier diagnostics
     * @return summary
     */
    public static Summary summarize(final List<Diagnostic> diagnostics) {
        final List<MethodActivity> methods = diagnosticOnlyMethods(diagnostics);
        final List<TaskRoot> roots = taskRoots(methods, List.of());
        return new Summary(
            0L,
            0L,
            methodCountWith(methods, ActivityField.LIFECYCLE),
            methodCountWith(methods, ActivityField.BLOCKING),
            methodCountWith(methods, ActivityField.SYNCHRONIZATION),
            methodCountWith(methods, ActivityField.CONCURRENCY_RUNTIME),
            methodCountWith(methods, ActivityField.UNKNOWN_BLOCKING),
            methodCountWith(methods, ActivityField.UNSUPPORTED_THREAD_TASK),
            classificationCount(methods, TaskClass.BLOCKING_WAIT),
            classificationCount(methods, TaskClass.CPU_BOUND),
            classificationCount(methods, TaskClass.TINY_CPU_TASK),
            classificationCount(methods, TaskClass.IO_BOUND),
            classificationCount(methods, TaskClass.MIXED),
            classificationCount(methods, TaskClass.PINNING_RISK),
            classificationCount(methods, TaskClass.UNKNOWN),
            ioSignalCount(methods),
            roots.size(),
            rootKindCount(roots, RootKind.THREAD_START),
            rootKindCount(roots, RootKind.BLOCKING_WAIT),
            rootKindCount(roots, RootKind.PINNING_RISK),
            rootKindCount(roots, RootKind.UNSUPPORTED_RUNTIME),
            rootKindCount(roots, RootKind.LIFECYCLE_RISK),
            rootKindCount(roots, RootKind.UNKNOWN),
            methods,
            roots
        );
    }

    private static List<DiagnosticItem> relevant(final List<Diagnostic> diagnostics) {
        final List<DiagnosticItem> result = new ArrayList<>();
        for (final Diagnostic diagnostic : diagnostics) {
            final String category = category(diagnostic.code());
            if (category != null) {
                result.add(new DiagnosticItem(category, diagnostic));
            }
        }
        return List.copyOf(result);
    }

    private static String category(final String code) {
        if ("JAVAN075".equals(code) || "JAVAN175".equals(code)) {
            return "lifecycle";
        }
        if ("JAVAN076".equals(code) || "JAVAN176".equals(code)) {
            return "synchronization";
        }
        if ("JAVAN077".equals(code) || "JAVAN177".equals(code)) {
            return "concurrency-runtime";
        }
        if ("JAVAN178".equals(code)) {
            return "blocking";
        }
        return null;
    }

    private static String json(final List<DiagnosticItem> items, final Summary summary) {
        final long errors = errorCount(items);
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"schemaVersion\": 1,\n");
        result.append("  \"diagnostics\": ").append(items.size()).append(",\n");
        result.append("  \"errors\": ").append(errors).append(",\n");
        result.append("  \"warnings\": ").append(items.size() - errors).append(",\n");
        result.append("  \"lifecycle\": ").append(categoryCount(items, "lifecycle")).append(",\n");
        result.append("  \"synchronization\": ").append(categoryCount(items, "synchronization")).append(",\n");
        result.append("  \"concurrencyRuntime\": ").append(categoryCount(items, "concurrency-runtime")).append(",\n");
        result.append("  \"blocking\": ").append(categoryCount(items, "blocking")).append(",\n");
        result.append("  \"threadStartSites\": ").append(summary.threadStartSites()).append(",\n");
        result.append("  \"threadStartMethods\": ").append(summary.threadStartMethods()).append(",\n");
        result.append("  \"lifecycleMethods\": ").append(summary.lifecycleMethods()).append(",\n");
        result.append("  \"blockingMethods\": ").append(summary.blockingMethods()).append(",\n");
        result.append("  \"synchronizationMethods\": ").append(summary.synchronizationMethods()).append(",\n");
        result.append("  \"concurrencyRuntimeMethods\": ").append(summary.concurrencyRuntimeMethods()).append(",\n");
        result.append("  \"unknownBlockingMethods\": ").append(summary.unknownBlockingMethods()).append(",\n");
        result.append("  \"unsupportedThreadTaskMethods\": ").append(summary.unsupportedThreadTaskMethods()).append(",\n");
        result.append("  \"sleepWaits\": ").append(subjectCount(items, "Thread.sleep(long)")).append(",\n");
        result.append("  \"joinWaits\": ").append(subjectCount(items, "Thread.join()")).append(",\n");
        result.append("  \"blockingTaskMethods\": ").append(summary.blockingTaskMethods()).append(",\n");
        result.append("  \"cpuBoundTaskMethods\": ").append(summary.cpuBoundTaskMethods()).append(",\n");
        result.append("  \"tinyCpuTaskMethods\": ").append(summary.tinyCpuTaskMethods()).append(",\n");
        result.append("  \"ioBoundTaskMethods\": ").append(summary.ioBoundTaskMethods()).append(",\n");
        result.append("  \"mixedTaskMethods\": ").append(summary.mixedTaskMethods()).append(",\n");
        result.append("  \"pinningRiskMethods\": ").append(summary.pinningRiskMethods()).append(",\n");
        result.append("  \"unknownTaskMethods\": ").append(summary.unknownTaskMethods()).append(",\n");
        result.append("  \"ioSignalMethods\": ").append(summary.ioSignalMethods()).append(",\n");
        result.append("  \"taskRoots\": ").append(summary.taskRoots()).append(",\n");
        result.append("  \"threadStartRoots\": ").append(summary.threadStartRoots()).append(",\n");
        result.append("  \"blockingRoots\": ").append(summary.blockingRoots()).append(",\n");
        result.append("  \"pinningRiskRoots\": ").append(summary.pinningRiskRoots()).append(",\n");
        result.append("  \"unsupportedRuntimeRoots\": ").append(summary.unsupportedRuntimeRoots()).append(",\n");
        result.append("  \"lifecycleRiskRoots\": ").append(summary.lifecycleRiskRoots()).append(",\n");
        result.append("  \"unknownRoots\": ").append(summary.unknownRoots()).append(",\n");
        result.append("  \"methods\": [\n");
        for (int index = 0; index < summary.methods().size(); index++) {
            final MethodActivity method = summary.methods().get(index);
            result.append("    {\n");
            appendField(result, "class", Json.string(method.className()), true);
            appendField(result, "method", Json.string(method.methodName()), true);
            appendField(result, "threadStartSites", Long.toString(method.threadStartSites()), true);
            appendField(result, "lifecycleRisks", Long.toString(method.lifecycleRisks()), true);
            appendField(result, "blockingWaits", Long.toString(method.blockingWaits()), true);
            appendField(result, "synchronizationRisks", Long.toString(method.synchronizationRisks()), true);
            appendField(result, "concurrencyRuntimeRisks", Long.toString(method.concurrencyRuntimeRisks()), true);
            appendField(result, "sleepWaits", Long.toString(method.sleepWaits()), true);
            appendField(result, "joinWaits", Long.toString(method.joinWaits()), true);
            appendField(result, "estimatedInstructions", Long.toString(method.estimatedInstructions()), true);
            appendField(result, "allocationSites", Long.toString(method.allocationSites()), true);
            appendField(result, "ioCallSites", Long.toString(method.ioCallSites()), true);
            appendField(result, "hasLoop", Boolean.toString(method.hasLoop()), true);
            appendField(result, "classification", Json.string(method.classification()), false);
            result.append("    }");
            if (index + 1 < summary.methods().size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ],\n");
        result.append("  \"roots\": [\n");
        for (int index = 0; index < summary.roots().size(); index++) {
            final TaskRoot root = summary.roots().get(index);
            result.append("    {\n");
            appendField(result, "class", Json.string(root.className()), true);
            appendField(result, "method", Json.string(root.methodName()), true);
            appendField(result, "rootKind", Json.string(root.rootKind()), true);
            appendField(result, "classification", Json.string(root.classification()), true);
            appendField(result, "threadStartSites", Long.toString(root.threadStartSites()), true);
            appendField(result, "blockingWaits", Long.toString(root.blockingWaits()), true);
            appendField(result, "lifecycleRisks", Long.toString(root.lifecycleRisks()), true);
            appendField(result, "synchronizationRisks", Long.toString(root.synchronizationRisks()), true);
            appendField(result, "concurrencyRuntimeRisks", Long.toString(root.concurrencyRuntimeRisks()), true);
            appendField(result, "ioCallSites", Long.toString(root.ioCallSites()), false);
            result.append("    }");
            if (index + 1 < summary.roots().size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ],\n");
        result.append("  \"items\": [\n");
        for (int index = 0; index < items.size(); index++) {
            final DiagnosticItem item = items.get(index);
            result.append("    {\n");
            appendField(result, "severity", Json.string(item.diagnostic().error() ? "error" : "warning"), true);
            appendField(result, "code", Json.string(item.diagnostic().code()), true);
            appendField(result, "category", Json.string(item.category()), true);
            appendField(result, "message", Json.string(item.diagnostic().message()), true);
            appendField(result, "class", Json.string(item.diagnostic().className()), true);
            appendField(result, "method", Json.string(item.diagnostic().methodName()), true);
            appendField(result, "subject", Json.string(item.diagnostic().subject()), true);
            appendField(result, "reason", Json.string(item.diagnostic().reason()), true);
            appendField(result, "fix", Json.string(item.diagnostic().fix()), false);
            result.append("    }");
            if (index + 1 < items.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(final List<DiagnosticItem> items, final Summary summary) {
        final long errors = errorCount(items);
        final StringBuilder result = new StringBuilder();
        result.append("# Thread Analysis\n\n");
        result.append("- diagnostics: `").append(items.size()).append("`\n");
        result.append("- errors: `").append(errors).append("`\n");
        result.append("- warnings: `").append(items.size() - errors).append("`\n");
        result.append("- lifecycle: `").append(categoryCount(items, "lifecycle")).append("`\n");
        result.append("- synchronization: `").append(categoryCount(items, "synchronization")).append("`\n");
        result.append("- concurrencyRuntime: `").append(categoryCount(items, "concurrency-runtime")).append("`\n");
        result.append("- blocking: `").append(categoryCount(items, "blocking")).append("`\n");
        result.append("- threadStartSites: `").append(summary.threadStartSites()).append("`\n");
        result.append("- threadStartMethods: `").append(summary.threadStartMethods()).append("`\n");
        result.append("- lifecycleMethods: `").append(summary.lifecycleMethods()).append("`\n");
        result.append("- blockingMethods: `").append(summary.blockingMethods()).append("`\n");
        result.append("- synchronizationMethods: `").append(summary.synchronizationMethods()).append("`\n");
        result.append("- concurrencyRuntimeMethods: `").append(summary.concurrencyRuntimeMethods()).append("`\n");
        result.append("- unknownBlockingMethods: `").append(summary.unknownBlockingMethods()).append("`\n");
        result.append("- unsupportedThreadTaskMethods: `").append(summary.unsupportedThreadTaskMethods()).append("`\n");
        result.append("- sleepWaits: `").append(subjectCount(items, "Thread.sleep(long)")).append("`\n");
        result.append("- joinWaits: `").append(subjectCount(items, "Thread.join()")).append("`\n");
        result.append("- blockingTaskMethods: `").append(summary.blockingTaskMethods()).append("`\n");
        result.append("- cpuBoundTaskMethods: `").append(summary.cpuBoundTaskMethods()).append("`\n");
        result.append("- tinyCpuTaskMethods: `").append(summary.tinyCpuTaskMethods()).append("`\n");
        result.append("- ioBoundTaskMethods: `").append(summary.ioBoundTaskMethods()).append("`\n");
        result.append("- mixedTaskMethods: `").append(summary.mixedTaskMethods()).append("`\n");
        result.append("- pinningRiskMethods: `").append(summary.pinningRiskMethods()).append("`\n");
        result.append("- unknownTaskMethods: `").append(summary.unknownTaskMethods()).append("`\n");
        result.append("- ioSignalMethods: `").append(summary.ioSignalMethods()).append("`\n");
        result.append("- taskRoots: `").append(summary.taskRoots()).append("`\n");
        result.append("- threadStartRoots: `").append(summary.threadStartRoots()).append("`\n");
        result.append("- blockingRoots: `").append(summary.blockingRoots()).append("`\n");
        result.append("- pinningRiskRoots: `").append(summary.pinningRiskRoots()).append("`\n");
        result.append("- unsupportedRuntimeRoots: `").append(summary.unsupportedRuntimeRoots()).append("`\n");
        result.append("- lifecycleRiskRoots: `").append(summary.lifecycleRiskRoots()).append("`\n");
        result.append("- unknownRoots: `").append(summary.unknownRoots()).append("`\n\n");
        if (!summary.roots().isEmpty()) {
            result.append("## Task Roots\n\n");
            for (final TaskRoot root : summary.roots()) {
                result.append("- `")
                    .append(root.className())
                    .append('#')
                    .append(root.methodName())
                    .append("`: rootKind=`")
                    .append(root.rootKind())
                    .append("`, classification=`")
                    .append(root.classification())
                    .append("`, threadStartSites=`")
                    .append(root.threadStartSites())
                    .append("`, blockingWaits=`")
                    .append(root.blockingWaits())
                    .append("`, lifecycleRisks=`")
                    .append(root.lifecycleRisks())
                    .append("`, synchronizationRisks=`")
                    .append(root.synchronizationRisks())
                    .append("`, concurrencyRuntimeRisks=`")
                    .append(root.concurrencyRuntimeRisks())
                    .append("`, ioCallSites=`")
                    .append(root.ioCallSites())
                    .append("`\n");
            }
            result.append('\n');
        }
        if (!summary.methods().isEmpty()) {
            result.append("## Reachable Thread Methods\n\n");
            for (final MethodActivity method : summary.methods()) {
                result.append("- `")
                    .append(method.className())
                    .append('#')
                    .append(method.methodName())
                    .append("`: threadStartSites=`")
                    .append(method.threadStartSites())
                    .append("`, lifecycleRisks=`")
                    .append(method.lifecycleRisks())
                    .append("`, blockingWaits=`")
                    .append(method.blockingWaits())
                    .append("`, synchronizationRisks=`")
                    .append(method.synchronizationRisks())
                    .append("`, concurrencyRuntimeRisks=`")
                    .append(method.concurrencyRuntimeRisks())
                    .append("`, sleepWaits=`")
                    .append(method.sleepWaits())
                    .append("`, joinWaits=`")
                    .append(method.joinWaits())
                    .append("`, estimatedInstructions=`")
                    .append(method.estimatedInstructions())
                    .append("`, allocationSites=`")
                    .append(method.allocationSites())
                    .append("`, ioCallSites=`")
                    .append(method.ioCallSites())
                    .append("`, hasLoop=`")
                    .append(method.hasLoop())
                    .append("`, classification=`")
                    .append(method.classification())
                    .append("`\n");
            }
            result.append('\n');
        }
        if (items.isEmpty()) {
            return result.append("No thread diagnostics.\n").toString();
        }
        for (final DiagnosticItem item : items) {
            result.append("## ")
                .append(item.diagnostic().error() ? "error" : "warning")
                .append("[")
                .append(item.diagnostic().code())
                .append("] ")
                .append(item.diagnostic().message())
                .append("\n\n");
            result.append("- category: `").append(item.category()).append("`\n");
            result.append("- class: `").append(emptyDash(item.diagnostic().className())).append("`\n");
            result.append("- method: `").append(emptyDash(item.diagnostic().methodName())).append("`\n");
            result.append("- subject: `").append(emptyDash(item.diagnostic().subject())).append("`\n");
            result.append("- reason: ").append(emptyDash(item.diagnostic().reason())).append('\n');
            result.append("- fix: ").append(emptyDash(item.diagnostic().fix())).append("\n\n");
        }
        return result.toString();
    }

    private static void appendField(final StringBuilder result, final String name, final String value, final boolean comma) {
        result.append("      \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static long errorCount(final List<DiagnosticItem> items) {
        long result = 0L;
        for (final DiagnosticItem item : items) {
            if (item.diagnostic().error()) {
                result++;
            }
        }
        return result;
    }

    private static long categoryCount(final List<DiagnosticItem> items, final String category) {
        long result = 0L;
        for (final DiagnosticItem item : items) {
            if (category.equals(item.category())) {
                result++;
            }
        }
        return result;
    }

    private static long subjectCount(final List<DiagnosticItem> items, final String subject) {
        long result = 0L;
        for (final DiagnosticItem item : items) {
            if (subject.equals(item.diagnostic().subject())) {
                result++;
            }
        }
        return result;
    }

    private static List<MethodActivity> diagnosticOnlyMethods(final List<Diagnostic> diagnostics) {
        final List<MethodActivity> methods = new ArrayList<>();
        for (final Diagnostic diagnostic : diagnostics) {
            if (!isThreadDiagnosticCode(diagnostic.code())) {
                continue;
            }
            final int index = methodIndex(methods, diagnostic.className(), diagnostic.methodName());
            if (index >= 0) {
                methods.set(index, methods.get(index).withDiagnostic(diagnostic.code(), diagnostic.subject()));
            } else {
                methods.add(MethodActivity.fromDiagnostic(
                    diagnostic.className(),
                    diagnostic.methodName(),
                    diagnostic.code(),
                    diagnostic.subject()
                ));
            }
        }
        sortMethods(methods);
        return List.copyOf(methods);
    }

    private static boolean isThreadDiagnosticCode(final String code) {
        return "JAVAN075".equals(code)
            || "JAVAN175".equals(code)
            || "JAVAN076".equals(code)
            || "JAVAN176".equals(code)
            || "JAVAN077".equals(code)
            || "JAVAN177".equals(code)
            || "JAVAN178".equals(code);
    }

    private static long methodCountWith(final List<MethodActivity> methods, final ActivityField field) {
        long result = 0L;
        for (final MethodActivity method : methods) {
            if (field.present(method)) {
                result++;
            }
        }
        return result;
    }

    private static long classificationCount(final List<MethodActivity> methods, final TaskClass value) {
        long result = 0L;
        for (final MethodActivity method : methods) {
            if (value.name().equals(method.classification())) {
                result++;
            }
        }
        return result;
    }

    private static long ioSignalCount(final List<MethodActivity> methods) {
        long result = 0L;
        for (final MethodActivity method : methods) {
            if (method.ioCallSites() > 0L) {
                result++;
            }
        }
        return result;
    }

    private static List<TaskRoot> taskRoots(final List<MethodActivity> methods, final List<CallEdge> callEdges) {
        final List<TaskRoot> result = new ArrayList<>();
        for (final MethodActivity method : methods) {
            if (!hasRootSignal(method) || hasSignaledCaller(methods, method, callEdges)) {
                continue;
            }
            result.add(new TaskRoot(
                method.className(),
                method.methodName(),
                rootKind(method).name(),
                method.classification(),
                method.threadStartSites(),
                method.blockingWaits(),
                method.lifecycleRisks(),
                method.synchronizationRisks(),
                method.concurrencyRuntimeRisks(),
                method.ioCallSites()
            ));
        }
        return List.copyOf(result);
    }

    private static boolean hasSignaledCaller(
        final List<MethodActivity> methods,
        final MethodActivity callee,
        final List<CallEdge> callEdges
    ) {
        for (final CallEdge edge : callEdges) {
            if (!isSameThreadCallerEdge(edge)) {
                continue;
            }
            if (!matches(edge.callee(), callee)) {
                continue;
            }
            final int callerIndex = methodIndex(methods, edge.caller().className(), edge.caller().methodName() + edge.caller().descriptor());
            if (callerIndex >= 0 && hasRootSignal(methods.get(callerIndex))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSameThreadCallerEdge(final CallEdge edge) {
        return edge.kind() == CallEdge.Kind.CALL
            || edge.kind() == CallEdge.Kind.CLASS_INITIALIZER
            || edge.kind() == CallEdge.Kind.THREAD_START_TASK;
    }

    private static boolean matches(final EntryPoint entryPoint, final MethodActivity method) {
        return entryPoint.className().equals(method.className())
            && (entryPoint.methodName() + entryPoint.descriptor()).equals(method.methodName());
    }

    private static boolean hasRootSignal(final MethodActivity method) {
        return method.threadStartSites() > 0L
            || method.blockingWaits() > 0L
            || method.lifecycleRisks() > 0L
            || method.synchronizationRisks() > 0L
            || method.concurrencyRuntimeRisks() > 0L;
    }

    private static RootKind rootKind(final MethodActivity method) {
        if (method.threadStartSites() > 0L) {
            return RootKind.THREAD_START;
        }
        if (method.blockingWaits() > 0L) {
            return RootKind.BLOCKING_WAIT;
        }
        if (method.synchronizationRisks() > 0L) {
            return RootKind.PINNING_RISK;
        }
        if (method.concurrencyRuntimeRisks() > 0L) {
            return RootKind.UNSUPPORTED_RUNTIME;
        }
        if (method.lifecycleRisks() > 0L) {
            return RootKind.LIFECYCLE_RISK;
        }
        return RootKind.UNKNOWN;
    }

    private static long rootKindCount(final List<TaskRoot> roots, final RootKind value) {
        long result = 0L;
        for (final TaskRoot root : roots) {
            if (value.name().equals(root.rootKind())) {
                result++;
            }
        }
        return result;
    }

    private static int methodIndex(final List<MethodActivity> methods, final String className, final String methodName) {
        for (int index = 0; index < methods.size(); index++) {
            final MethodActivity method = methods.get(index);
            if (className.equals(method.className()) && methodName.equals(method.methodName())) {
                return index;
            }
        }
        return -1;
    }

    private static void sortMethods(final List<MethodActivity> methods) {
        int index = 1;
        while (index < methods.size()) {
            final MethodActivity value = methods.get(index);
            int insert = index - 1;
            while (insert >= 0 && compareMethod(methods.get(insert), value) > 0) {
                methods.set(insert + 1, methods.get(insert));
                insert--;
            }
            methods.set(insert + 1, value);
            index++;
        }
    }

    private static int compareMethod(final MethodActivity left, final MethodActivity right) {
        final int classCompare = Strings2.compareAscii(left.className(), right.className());
        if (classCompare != 0) {
            return classCompare;
        }
        return Strings2.compareAscii(left.methodName(), right.methodName());
    }

    private static long threadStartSitesInMethod(final CodeAttribute code) {
        long result = 0L;
        for (final Instruction instruction : code.instructions()) {
            if (invokesThreadStart(instruction)) {
                result++;
            }
        }
        return result;
    }

    private static MethodStats methodStats(final CodeAttribute code) {
        long allocationSites = 0L;
        long ioCallSites = 0L;
        boolean hasLoop = false;
        for (final Instruction instruction : code.instructions()) {
            if (isAllocation(instruction)) {
                allocationSites++;
            }
            if (isIoCall(instruction)) {
                ioCallSites++;
            }
            if (!hasLoop && isBackwardBranch(instruction)) {
                hasLoop = true;
            }
        }
        return new MethodStats(code.instructions().size(), allocationSites, ioCallSites, hasLoop);
    }

    private static boolean isAllocation(final Instruction instruction) {
        return "new".equals(instruction.mnemonic())
            || "newarray".equals(instruction.mnemonic())
            || "anewarray".equals(instruction.mnemonic())
            || "multianewarray".equals(instruction.mnemonic());
    }

    private static boolean isIoCall(final Instruction instruction) {
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final String owner = instruction.methodRef().orElseThrow().owner();
        return owner.startsWith("java/io/")
            || owner.startsWith("java/net/")
            || owner.startsWith("java/net/http/")
            || owner.startsWith("java/nio/channels/")
            || owner.startsWith("java/nio/file/")
            || owner.startsWith("javax/net/");
    }

    private static boolean isBackwardBranch(final Instruction instruction) {
        final byte[] operands = instruction.operands();
        final String mnemonic = instruction.mnemonic();
        if (operands.length == 2 && branchMnemonic(mnemonic)) {
            return instruction.offset() + signedShort(operands) <= instruction.offset();
        }
        if (operands.length == 4 && ("goto_w".equals(mnemonic) || "jsr_w".equals(mnemonic))) {
            return instruction.offset() + signedInt(operands) <= instruction.offset();
        }
        return false;
    }

    private static boolean branchMnemonic(final String mnemonic) {
        return mnemonic.startsWith("if_")
            || "ifeq".equals(mnemonic)
            || "ifne".equals(mnemonic)
            || "iflt".equals(mnemonic)
            || "ifge".equals(mnemonic)
            || "ifgt".equals(mnemonic)
            || "ifle".equals(mnemonic)
            || "ifnull".equals(mnemonic)
            || "ifnonnull".equals(mnemonic)
            || "goto".equals(mnemonic)
            || "jsr".equals(mnemonic);
    }

    private static int signedShort(final byte[] operands) {
        return (short) (((operands[0] & 0xFF) << 8) | (operands[1] & 0xFF));
    }

    private static int signedInt(final byte[] operands) {
        return ((operands[0] & 0xFF) << 24)
            | ((operands[1] & 0xFF) << 16)
            | ((operands[2] & 0xFF) << 8)
            | (operands[3] & 0xFF);
    }

    private static String emptyDash(final String value) {
        if (Strings2.isBlank(value)) {
            return "-";
        }
        return value;
    }

    private record DiagnosticItem(String category, Diagnostic diagnostic) {
    }

    /**
     * Reachable thread-behavior summary written alongside thread diagnostics.
     *
     * @param threadStartSites reachable {@code Thread.start()} invocation count
     * @param threadStartMethods reachable methods that invoke {@code Thread.start()}
     * @param lifecycleMethods reachable methods with at least one lifecycle diagnostic
     * @param blockingMethods reachable methods with at least one explicit blocking wait diagnostic
     * @param synchronizationMethods reachable methods with at least one synchronization diagnostic
     * @param concurrencyRuntimeMethods reachable methods with at least one concurrency-runtime diagnostic
     * @param unknownBlockingMethods reachable methods that hit unsupported synchronization or concurrency runtime shapes
     * @param unsupportedThreadTaskMethods reachable methods that still rely on unsupported lifecycle, synchronization, or concurrency-runtime behavior
     * @param blockingTaskMethods reachable methods conservatively classified as blocking waits
     * @param cpuBoundTaskMethods reachable methods conservatively classified as CPU-bound
     * @param tinyCpuTaskMethods reachable methods conservatively classified as tiny CPU-only work
     * @param ioBoundTaskMethods reachable methods conservatively classified as I/O-bound work
     * @param mixedTaskMethods reachable methods conservatively classified as mixed I/O and looping work
     * @param pinningRiskMethods reachable methods conservatively classified as pinning risks
     * @param unknownTaskMethods reachable methods that still cannot be classified more precisely
     * @param ioSignalMethods reachable methods with at least one direct I/O call site
     * @param taskRoots conservative reachable task-root count
     * @param threadStartRoots roots anchored by direct {@code Thread.start()} calls
     * @param blockingRoots roots anchored by direct blocking waits
     * @param pinningRiskRoots roots anchored by synchronization risk
     * @param unsupportedRuntimeRoots roots anchored by unsupported concurrency-runtime usage
     * @param lifecycleRiskRoots roots anchored by lifecycle risk
     * @param unknownRoots roots that still lack a more precise kind
     */
    public record Summary(
        long threadStartSites,
        long threadStartMethods,
        long lifecycleMethods,
        long blockingMethods,
        long synchronizationMethods,
        long concurrencyRuntimeMethods,
        long unknownBlockingMethods,
        long unsupportedThreadTaskMethods,
        long blockingTaskMethods,
        long cpuBoundTaskMethods,
        long tinyCpuTaskMethods,
        long ioBoundTaskMethods,
        long mixedTaskMethods,
        long pinningRiskMethods,
        long unknownTaskMethods,
        long ioSignalMethods,
        long taskRoots,
        long threadStartRoots,
        long blockingRoots,
        long pinningRiskRoots,
        long unsupportedRuntimeRoots,
        long lifecycleRiskRoots,
        long unknownRoots,
        List<MethodActivity> methods,
        List<TaskRoot> roots
    ) {
        public static final Summary EMPTY = new Summary(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, List.of(), List.of());
    }

    /**
     * Reachable method-level thread activity summary.
     *
     * @param className internal class name
     * @param methodName method plus descriptor
     * @param threadStartSites reachable {@code Thread.start()} invocation count in this method
     * @param lifecycleRisks reachable lifecycle diagnostic count in this method
     * @param blockingWaits reachable explicit blocking wait count in this method
     * @param synchronizationRisks reachable synchronization diagnostic count in this method
     * @param concurrencyRuntimeRisks reachable concurrency-runtime diagnostic count in this method
     * @param sleepWaits reachable {@code Thread.sleep(long)} count in this method
     * @param joinWaits reachable {@code Thread.join()} count in this method
     * @param estimatedInstructions conservative bytecode instruction count
     * @param allocationSites conservative allocation-site count
     * @param ioCallSites conservative direct I/O call-site count
     * @param hasLoop true when a backward branch is present
     * @param classification conservative task classification
     */
    public record MethodActivity(
        String className,
        String methodName,
        long threadStartSites,
        long lifecycleRisks,
        long blockingWaits,
        long synchronizationRisks,
        long concurrencyRuntimeRisks,
        long sleepWaits,
        long joinWaits,
        long estimatedInstructions,
        long allocationSites,
        long ioCallSites,
        boolean hasLoop,
        String classification
    ) {
        private static MethodActivity fromDiagnostic(
            final String className,
            final String methodName,
            final String code,
            final String subject
        ) {
            return new MethodActivity(className, methodName, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, false, TaskClass.UNKNOWN.name())
                .withDiagnostic(code, subject);
        }

        private MethodActivity withDiagnostic(final String code, final String subject) {
            if ("JAVAN075".equals(code) || "JAVAN175".equals(code)) {
                return new MethodActivity(className, methodName, threadStartSites, lifecycleRisks + 1L, blockingWaits, synchronizationRisks, concurrencyRuntimeRisks, sleepWaits, joinWaits, estimatedInstructions, allocationSites, ioCallSites, hasLoop, classification)
                    .refreshClassification();
            }
            if ("JAVAN178".equals(code)) {
                if ("Thread.sleep(long)".equals(subject)) {
                    return new MethodActivity(className, methodName, threadStartSites, lifecycleRisks, blockingWaits + 1L, synchronizationRisks, concurrencyRuntimeRisks, sleepWaits + 1L, joinWaits, estimatedInstructions, allocationSites, ioCallSites, hasLoop, classification)
                        .refreshClassification();
                }
                if ("Thread.join()".equals(subject)) {
                    return new MethodActivity(className, methodName, threadStartSites, lifecycleRisks, blockingWaits + 1L, synchronizationRisks, concurrencyRuntimeRisks, sleepWaits, joinWaits + 1L, estimatedInstructions, allocationSites, ioCallSites, hasLoop, classification)
                        .refreshClassification();
                }
                return new MethodActivity(className, methodName, threadStartSites, lifecycleRisks, blockingWaits + 1L, synchronizationRisks, concurrencyRuntimeRisks, sleepWaits, joinWaits, estimatedInstructions, allocationSites, ioCallSites, hasLoop, classification)
                    .refreshClassification();
            }
            if ("JAVAN076".equals(code) || "JAVAN176".equals(code)) {
                return new MethodActivity(className, methodName, threadStartSites, lifecycleRisks, blockingWaits, synchronizationRisks + 1L, concurrencyRuntimeRisks, sleepWaits, joinWaits, estimatedInstructions, allocationSites, ioCallSites, hasLoop, classification)
                    .refreshClassification();
            }
            if ("JAVAN077".equals(code) || "JAVAN177".equals(code)) {
                return new MethodActivity(className, methodName, threadStartSites, lifecycleRisks, blockingWaits, synchronizationRisks, concurrencyRuntimeRisks + 1L, sleepWaits, joinWaits, estimatedInstructions, allocationSites, ioCallSites, hasLoop, classification)
                    .refreshClassification();
            }
            return this;
        }

        private MethodActivity withReachableCode(final long value, final MethodStats stats) {
            return new MethodActivity(
                className,
                methodName,
                value,
                lifecycleRisks,
                blockingWaits,
                synchronizationRisks,
                concurrencyRuntimeRisks,
                sleepWaits,
                joinWaits,
                stats.estimatedInstructions(),
                stats.allocationSites(),
                stats.ioCallSites(),
                stats.hasLoop(),
                classification
            ).refreshClassification();
        }

        private MethodActivity refreshClassification() {
            return new MethodActivity(
                className,
                methodName,
                threadStartSites,
                lifecycleRisks,
                blockingWaits,
                synchronizationRisks,
                concurrencyRuntimeRisks,
                sleepWaits,
                joinWaits,
                estimatedInstructions,
                allocationSites,
                ioCallSites,
                hasLoop,
                classify(this).name()
            );
        }
    }

    private record MethodStats(long estimatedInstructions, long allocationSites, long ioCallSites, boolean hasLoop) {
    }

    private enum TaskClass {
        BLOCKING_WAIT,
        CPU_BOUND,
        TINY_CPU_TASK,
        IO_BOUND,
        MIXED,
        PINNING_RISK,
        UNKNOWN
    }

    /**
     * Conservative reachable task-root summary derived from existing method activity.
     *
     * @param className internal class name
     * @param methodName method plus descriptor
     * @param rootKind conservative root kind
     * @param classification conservative task classification
     * @param threadStartSites direct thread-start sites in the root
     * @param blockingWaits direct blocking waits in the root
     * @param lifecycleRisks lifecycle risks in the root
     * @param synchronizationRisks synchronization risks in the root
     * @param concurrencyRuntimeRisks concurrency-runtime risks in the root
     * @param ioCallSites direct I/O call sites in the root
     */
    public record TaskRoot(
        String className,
        String methodName,
        String rootKind,
        String classification,
        long threadStartSites,
        long blockingWaits,
        long lifecycleRisks,
        long synchronizationRisks,
        long concurrencyRuntimeRisks,
        long ioCallSites
    ) {
    }

    private enum RootKind {
        THREAD_START,
        BLOCKING_WAIT,
        PINNING_RISK,
        UNSUPPORTED_RUNTIME,
        LIFECYCLE_RISK,
        UNKNOWN
    }

    private static TaskClass classify(final MethodActivity method) {
        if (method.blockingWaits() > 0L) {
            return TaskClass.BLOCKING_WAIT;
        }
        if (method.synchronizationRisks() > 0L) {
            return TaskClass.PINNING_RISK;
        }
        if (method.ioCallSites() > 0L && method.hasLoop()) {
            return TaskClass.MIXED;
        }
        if (method.ioCallSites() > 0L) {
            return TaskClass.IO_BOUND;
        }
        if (method.hasLoop() && method.estimatedInstructions() >= 12L) {
            return TaskClass.CPU_BOUND;
        }
        if (method.threadStartSites() > 0L
            && method.blockingWaits() == 0L
            && method.synchronizationRisks() == 0L
            && method.concurrencyRuntimeRisks() == 0L
            && method.estimatedInstructions() <= 12L
            && method.ioCallSites() == 0L
            && !method.hasLoop()
            && method.allocationSites() <= 1L) {
            return TaskClass.TINY_CPU_TASK;
        }
        return TaskClass.UNKNOWN;
    }

    private enum ActivityField {
        LIFECYCLE {
            @Override
            boolean present(final MethodActivity method) {
                return method.lifecycleRisks() > 0L;
            }
        },
        BLOCKING {
            @Override
            boolean present(final MethodActivity method) {
                return method.blockingWaits() > 0L;
            }
        },
        SYNCHRONIZATION {
            @Override
            boolean present(final MethodActivity method) {
                return method.synchronizationRisks() > 0L;
            }
        },
        CONCURRENCY_RUNTIME {
            @Override
            boolean present(final MethodActivity method) {
                return method.concurrencyRuntimeRisks() > 0L;
            }
        },
        UNKNOWN_BLOCKING {
            @Override
            boolean present(final MethodActivity method) {
                return method.synchronizationRisks() > 0L || method.concurrencyRuntimeRisks() > 0L;
            }
        },
        UNSUPPORTED_THREAD_TASK {
            @Override
            boolean present(final MethodActivity method) {
                return method.lifecycleRisks() > 0L
                    || method.synchronizationRisks() > 0L
                    || method.concurrencyRuntimeRisks() > 0L;
            }
        };

        abstract boolean present(MethodActivity method);
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entryPoint) {
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return Optional.empty();
        }
        return classFile.method(entryPoint.methodName(), entryPoint.descriptor());
    }

    private static boolean invokesThreadStart(final Instruction instruction) {
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final var method = instruction.methodRef().orElseThrow();
        if ("java/lang/Thread".equals(method.owner())) {
            if ("start".equals(method.name()) && "()V".equals(method.descriptor())) {
                return true;
            }
            return "startVirtualThread".equals(method.name())
                && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(method.descriptor());
        }
        return ("java/lang/Thread$Builder".equals(method.owner())
            || "java/lang/Thread$Builder$OfVirtual".equals(method.owner()))
            && "start".equals(method.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(method.descriptor());
    }
}
