package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Files2;
import javan.util.Json;
import javan.verify.Diagnostic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * Writes stable virtual-thread status reports without pretending runtime profiling exists.
 */
public final class VirtualThreadReports {
    private static final String STATUS = "partial";
    private static final String DIAGNOSTIC_SOURCE = "platform-thread-analysis-plus-virtual-builder-executor-park-slice";
    private static final String NEXT_GATE = "land remaining builder/factory/executor introspection such as getClass() plus scheduler/carrier runtime and runtime-backed profiling counters";
    private static final List<String> REASONS = List.of(
        "Thread.startVirtualThread(Runnable), Thread.ofVirtual().start(Runnable), exact single-local-alias builder start, Thread.ofVirtual().unstarted(Runnable), exact single-local-alias builder unstarted, supported name(...) builder flows including name(String) and name(String,long), discarded standalone Thread.ofVirtual()/name(...)/factory() expressions, exact Object-local alias round-trips back into supported builder/factory/executor terminal calls via checkcast, runtime-backed builder/factory/executor printing plus toString()/hashCode()/equals(), reusable builder-name counters, factory snapshot naming, exact static helper wrappers around supported builder/factory flows including direct parameter pass-through into name(String) and name(String,long), Thread.ofVirtual().factory().newThread(Runnable), exact single-local-alias factory newThread, Executors.newVirtualThreadPerTaskExecutor(), Executors.newThreadPerTaskExecutor(ThreadFactory), Executor.execute(Runnable), ExecutorService.shutdown(), ExecutorService.close(), Thread.isVirtual(), Thread.getName(), ThreadLocal base storage, and LockSupport.park()/parkNanos(long)/parkUntil(long)/unpark(Thread) are supported through the current host-thread runtime slice.",
        "Broader builder/factory/executor introspection such as getClass(), scheduler/carrier behavior, blocking-I/O awareness, and richer virtual-thread runtime semantics are not linked yet and still fail clearly when reachable.",
        "Virtual-thread profiling counters are not collected yet."
    );

    /**
     * Writes {@code virtual-threads.json} and {@code virtual-threads.md}.
     *
     * @param reportsDirectory {@code .javan/reports} directory
     * @throws IOException when writing fails
     */
    public void write(final Path reportsDirectory) throws IOException {
        write(reportsDirectory, Summary.notCollected());
    }

    /**
     * Writes {@code virtual-threads.json} and {@code virtual-threads.md} with reachable-code scan metrics.
     *
     * @param reportsDirectory {@code .javan/reports} directory
     * @param diagnostics diagnostics for unsupported reachable APIs
     * @param classes parsed classes
     * @param callGraph reachable methods and caller edges
     * @throws IOException when writing fails
     */
    public void write(
        final Path reportsDirectory,
        final List<Diagnostic> diagnostics,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        write(reportsDirectory, Summary.scanned(diagnostics, classes, callGraph));
    }

    private static void write(final Path reportsDirectory, final Summary summary) throws IOException {
        Files2.writeString(reportsDirectory.resolve("virtual-threads.json"), json(summary));
        Files2.writeString(reportsDirectory.resolve("virtual-threads.md"), markdown(summary));
    }

    private static String json(final Summary summary) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        field(result, "schemaVersion", "1", true);
        field(result, "status", Json.string(STATUS), true);
        field(result, "runtimeSupported", "true", true);
        field(result, "profilingSupported", "false", true);
        field(result, "profilingCollected", "false", true);
        field(result, "schedulerImplemented", "false", true);
        field(result, "carrierPoolImplemented", "false", true);
        field(result, "threadModelImplemented", "true", true);
        field(result, "threadLocalImplemented", "true", true);
        field(result, "blockingIoAware", "false", true);
        field(result, "reachableApiScan", Json.string(summary.reachableApiScan()), true);
        field(result, "reachableVirtualStartSites", Long.toString(summary.reachableVirtualStartSites()), true);
        field(result, "reachableVirtualStartMethods", Long.toString(summary.reachableVirtualStartMethods()), true);
        field(result, "reachableIsVirtualSites", Long.toString(summary.reachableIsVirtualSites()), true);
        field(result, "unsupportedBuilderApis", Long.toString(summary.unsupportedBuilderApis()), true);
        field(result, "unsupportedBuilderApisReachable", Long.toString(summary.unsupportedBuilderApisReachable()), true);
        field(result, "unsupportedBuilderApisUnreachable", Long.toString(summary.unsupportedBuilderApisUnreachable()), true);
        field(result, "unsupportedExecutorApis", Long.toString(summary.unsupportedExecutorApis()), true);
        field(result, "unsupportedExecutorApisReachable", Long.toString(summary.unsupportedExecutorApisReachable()), true);
        field(result, "unsupportedExecutorApisUnreachable", Long.toString(summary.unsupportedExecutorApisUnreachable()), true);
        field(result, "diagnosticSource", Json.string(DIAGNOSTIC_SOURCE), true);
        field(result, "reasonCount", Integer.toString(REASONS.size()), true);
        field(result, "nextGate", Json.string(NEXT_GATE), true);
        result.append("  \"reasons\": [\n");
        for (int index = 0; index < REASONS.size(); index++) {
            result.append("    ").append(Json.string(REASONS.get(index)));
            if (index + 1 < REASONS.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(final Summary summary) {
        final StringBuilder result = new StringBuilder();
        result.append("# Virtual Thread Analysis\n\n");
        result.append("- status: `").append(STATUS).append("`\n");
        result.append("- runtimeSupported: `true`\n");
        result.append("- profilingSupported: `false`\n");
        result.append("- profilingCollected: `false`\n");
        result.append("- schedulerImplemented: `false`\n");
        result.append("- carrierPoolImplemented: `false`\n");
        result.append("- threadModelImplemented: `true`\n");
        result.append("- threadLocalImplemented: `true`\n");
        result.append("- blockingIoAware: `false`\n");
        result.append("- reachableApiScan: `").append(summary.reachableApiScan()).append("`\n");
        result.append("- reachableVirtualStartSites: `").append(summary.reachableVirtualStartSites()).append("`\n");
        result.append("- reachableVirtualStartMethods: `").append(summary.reachableVirtualStartMethods()).append("`\n");
        result.append("- reachableIsVirtualSites: `").append(summary.reachableIsVirtualSites()).append("`\n");
        result.append("- unsupportedBuilderApis: `").append(summary.unsupportedBuilderApis()).append("`\n");
        result.append("- unsupportedBuilderApisReachable: `").append(summary.unsupportedBuilderApisReachable()).append("`\n");
        result.append("- unsupportedBuilderApisUnreachable: `").append(summary.unsupportedBuilderApisUnreachable()).append("`\n");
        result.append("- unsupportedExecutorApis: `").append(summary.unsupportedExecutorApis()).append("`\n");
        result.append("- unsupportedExecutorApisReachable: `").append(summary.unsupportedExecutorApisReachable()).append("`\n");
        result.append("- unsupportedExecutorApisUnreachable: `").append(summary.unsupportedExecutorApisUnreachable()).append("`\n");
        result.append("- diagnosticSource: `").append(DIAGNOSTIC_SOURCE).append("`\n");
        result.append("- reasonCount: `").append(REASONS.size()).append("`\n");
        result.append("- nextGate: ").append(NEXT_GATE).append("\n\n");
        result.append("## Reasons\n\n");
        for (final String reason : REASONS) {
            result.append("- ").append(reason).append('\n');
        }
        return result.toString();
    }

    private static void field(final StringBuilder result, final String name, final String value, final boolean comma) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private record Summary(
        String reachableApiScan,
        long reachableVirtualStartSites,
        long reachableVirtualStartMethods,
        long reachableIsVirtualSites,
        long unsupportedBuilderApis,
        long unsupportedBuilderApisReachable,
        long unsupportedBuilderApisUnreachable,
        long unsupportedExecutorApis,
        long unsupportedExecutorApisReachable,
        long unsupportedExecutorApisUnreachable
    ) {
        private static Summary notCollected() {
            return new Summary("not-collected", 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        }

        private static Summary scanned(
            final List<Diagnostic> diagnostics,
            final Map<String, ClassFile> classes,
            final CallGraph callGraph
        ) {
            long reachableVirtualStartSites = 0L;
            long reachableVirtualStartMethods = 0L;
            long reachableIsVirtualSites = 0L;
            for (final EntryPoint entry : callGraph.reachableMethods()) {
                final Optional<MethodInfo> method = method(classes, entry);
                if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                    continue;
                }
                final CodeAttribute code = method.orElseThrow().code().orElseThrow();
                boolean startsVirtualThread = false;
                for (final Instruction instruction : code.instructions()) {
                    final Optional<MethodRef> methodRef = instruction.methodRef();
                    if (methodRef.isEmpty()) {
                        continue;
                    }
                    if (isVirtualThreadStart(methodRef.orElseThrow()) || isVirtualThreadBuilderStart(methodRef.orElseThrow())) {
                        reachableVirtualStartSites++;
                        startsVirtualThread = true;
                    }
                    if (isThreadIsVirtual(methodRef.orElseThrow())) {
                        reachableIsVirtualSites++;
                    }
                }
                if (startsVirtualThread) {
                    reachableVirtualStartMethods++;
                }
            }
            final long unsupportedBuilderApisReachable = countSubjects(diagnostics, "JAVAN077", "Thread.ofVirtual()")
                + countSubjectsWithPrefixExcluding(diagnostics, "JAVAN077", "Thread.Builder.", "Thread.Builder.OfVirtual.")
                + countSubjectsWithPrefix(diagnostics, "JAVAN077", "Thread.Builder.OfVirtual.");
            final long unsupportedBuilderApisUnreachable = countSubjects(diagnostics, "JAVAN177", "Thread.ofVirtual()")
                + countSubjectsWithPrefixExcluding(diagnostics, "JAVAN177", "Thread.Builder.", "Thread.Builder.OfVirtual.")
                + countSubjectsWithPrefix(diagnostics, "JAVAN177", "Thread.Builder.OfVirtual.");
            final long unsupportedExecutorApisReachable = countSubjects(diagnostics, "JAVAN077", "Executors.newVirtualThreadPerTaskExecutor()");
            final long unsupportedExecutorApisUnreachable = countSubjects(diagnostics, "JAVAN177", "Executors.newVirtualThreadPerTaskExecutor()");
            return new Summary(
                "reachable-method-scan",
                reachableVirtualStartSites,
                reachableVirtualStartMethods,
                reachableIsVirtualSites,
                unsupportedBuilderApisReachable + unsupportedBuilderApisUnreachable,
                unsupportedBuilderApisReachable,
                unsupportedBuilderApisUnreachable,
                unsupportedExecutorApisReachable + unsupportedExecutorApisUnreachable,
                unsupportedExecutorApisReachable,
                unsupportedExecutorApisUnreachable
            );
        }
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile classFile = classes.get(entry.className());
        if (classFile == null) {
            return Optional.empty();
        }
        return classFile.method(entry.methodName(), entry.descriptor());
    }

    private static boolean isVirtualThreadStart(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "startVirtualThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderStart(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "start".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderOwner(final String owner) {
        return "java/lang/Thread$Builder".equals(owner)
            || "java/lang/Thread$Builder$OfVirtual".equals(owner);
    }

    private static boolean isThreadIsVirtual(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "isVirtual".equals(methodRef.name())
            && "()Z".equals(methodRef.descriptor());
    }

    private static long countSubjects(final List<Diagnostic> diagnostics, final String code, final String subject) {
        long count = 0L;
        for (final Diagnostic diagnostic : diagnostics) {
            if (code.equals(diagnostic.code()) && subject.equals(diagnostic.subject())) {
                count++;
            }
        }
        return count;
    }

    private static long countSubjectsWithPrefix(final List<Diagnostic> diagnostics, final String code, final String prefix) {
        long count = 0L;
        for (final Diagnostic diagnostic : diagnostics) {
            if (code.equals(diagnostic.code()) && diagnostic.subject().startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private static long countSubjectsWithPrefixExcluding(
        final List<Diagnostic> diagnostics,
        final String code,
        final String prefix,
        final String excludedPrefix
    ) {
        long count = 0L;
        for (final Diagnostic diagnostic : diagnostics) {
            if (!code.equals(diagnostic.code())) {
                continue;
            }
            final String subject = diagnostic.subject();
            if (subject.startsWith(prefix) && !subject.startsWith(excludedPrefix)) {
                count++;
            }
        }
        return count;
    }
}
