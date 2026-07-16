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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes stable virtual-thread status reports for the current supported slice.
 */
public final class VirtualThreadReports {
    private static final String LONG_MIN_VALUE_LITERAL = "-9223372036854775808";
    private static final String STATUS = "partial";
    private static final String DIAGNOSTIC_SOURCE = "platform-thread-analysis-plus-virtual-builder-executor-park-slice";
    private static final String NEXT_GATE = "land remaining builder/factory/executor introspection such as getClass() plus scheduler/carrier runtime and runtime-backed profiling counters";
    private static final String SUPPORTED_REASON =
        "Thread.startVirtualThread(Runnable), Thread.ofVirtual().start(Runnable), exact single-local-alias builder start, Thread.ofVirtual().unstarted(Runnable), exact single-local-alias builder unstarted, supported name(...) builder flows including name(String) and name(String,long), discarded standalone Thread.ofVirtual()/name(...)/factory() expressions, exact Object-local alias round-trips back into supported builder/factory/executor terminal calls via checkcast, runtime-backed builder/factory/executor printing plus toString()/hashCode()/equals(), reusable builder-name counters, factory snapshot naming, exact static helper wrappers around supported builder/factory flows including direct parameter pass-through into name(String) and name(String,long), Thread.ofVirtual().factory().newThread(Runnable), exact single-local-alias factory newThread, Executors.newVirtualThreadPerTaskExecutor(), Executors.newThreadPerTaskExecutor(ThreadFactory), Executor.execute(Runnable), ExecutorService.submit(Runnable), ExecutorService.shutdown(), ExecutorService.awaitTermination(long,TimeUnit), ExecutorService.shutdownNow(), ExecutorService.close(), and Future.cancel(boolean)/isDone()/isCancelled() on the current thread-backed future handles produced by the supported executor and scheduler entrypoints, Thread.isVirtual(), Thread.getName(), ThreadLocal base storage, and LockSupport.park()/parkNanos(long)/parkUntil(long)/unpark(Thread) are supported through the current host-thread runtime slice.";
    private static final String UNSUPPORTED_REASON =
        "Broader builder/factory/executor introspection such as getClass(), scheduler/carrier behavior, blocking-I/O awareness, and richer virtual-thread runtime semantics are not linked yet and still fail clearly when reachable.";
    private static final String PROFILING_NOT_LINKED_REASON =
        "Virtual-thread profiling counters are not collected yet.";
    private static final String PROFILING_READY_REASON =
        "Virtual-thread profiling hooks are linked through runtime-profiling.*, but the current run has not collected counters yet.";
    private static final String PROFILING_COLLECTED_REASON =
        "Virtual-thread profiling counters are collected through runtime-profiling.* for the current host-thread-backed slice.";

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

    /**
     * Rewrites the existing report using the current runtime-profiling status while preserving the
     * last recorded reachable-code summary.
     *
     * @param reportsDirectory {@code .javan/reports} directory
     * @throws IOException when reading or writing fails
     */
    public void refresh(final Path reportsDirectory) throws IOException {
        final Path report = reportsDirectory.resolve("virtual-threads.json");
        if (!Files.exists(report)) {
            return;
        }
        write(reportsDirectory, Summary.fromExisting(report));
    }

    private static void write(final Path reportsDirectory, final Summary summary) throws IOException {
        final ProfilingStatus profiling = profilingStatus(reportsDirectory);
        final List<String> reasons = reasons(profiling);
        Files2.writeString(reportsDirectory.resolve("virtual-threads.json"), json(summary, profiling, reasons));
        Files2.writeString(reportsDirectory.resolve("virtual-threads.md"), markdown(summary, profiling, reasons));
    }

    private static ProfilingStatus profilingStatus(final Path reportsDirectory) throws IOException {
        final Path report = reportsDirectory.resolve("runtime-profiling.json");
        if (!Files.exists(report)) {
            return new ProfilingStatus(false, false);
        }
        final String content = Files.readString(report);
        final boolean enabled = booleanField(content, "enabled", false);
        final String collectionState = stringField(content, "collectionState", "");
        final boolean collected = "collected".equals(collectionState);
        final boolean supported = enabled || "linked-not-run".equals(collectionState) || collected;
        return new ProfilingStatus(supported, collected);
    }

    private static List<String> reasons(final ProfilingStatus profiling) {
        if (profiling.collected()) {
            return List.of(SUPPORTED_REASON, UNSUPPORTED_REASON, PROFILING_COLLECTED_REASON);
        }
        if (profiling.supported()) {
            return List.of(SUPPORTED_REASON, UNSUPPORTED_REASON, PROFILING_READY_REASON);
        }
        return List.of(SUPPORTED_REASON, UNSUPPORTED_REASON, PROFILING_NOT_LINKED_REASON);
    }

    private static String json(final Summary summary, final ProfilingStatus profiling, final List<String> reasons) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        field(result, "schemaVersion", "1", true);
        field(result, "status", Json.string(STATUS), true);
        field(result, "runtimeSupported", "true", true);
        field(result, "profilingSupported", Boolean.toString(profiling.supported()), true);
        field(result, "profilingCollected", Boolean.toString(profiling.collected()), true);
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
        field(result, "reasonCount", Integer.toString(reasons.size()), true);
        field(result, "nextGate", Json.string(NEXT_GATE), true);
        result.append("  \"reasons\": [\n");
        for (int index = 0; index < reasons.size(); index++) {
            result.append("    ").append(Json.string(reasons.get(index)));
            if (index + 1 < reasons.size()) {
                result.append(',');
            }
            result.append('\n');
        }
        result.append("  ]\n");
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(final Summary summary, final ProfilingStatus profiling, final List<String> reasons) {
        final StringBuilder result = new StringBuilder();
        result.append("# Virtual Thread Analysis\n\n");
        result.append("- status: `").append(STATUS).append("`\n");
        result.append("- runtimeSupported: `true`\n");
        result.append("- profilingSupported: `").append(profiling.supported()).append("`\n");
        result.append("- profilingCollected: `").append(profiling.collected()).append("`\n");
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
        result.append("- reasonCount: `").append(reasons.size()).append("`\n");
        result.append("- nextGate: ").append(NEXT_GATE).append("\n\n");
        result.append("## Reasons\n\n");
        for (final String reason : reasons) {
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

    private static String stringField(final String content, final String name, final String defaultValue) {
        final int start = valueStart(content, name);
        if (start < 0 || start >= content.length() || content.charAt(start) != '"') {
            return defaultValue;
        }
        final StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int index = start + 1; index < content.length(); index++) {
            final char ch = content.charAt(index);
            if (escaping) {
                if (ch == 'n') {
                    result.append('\n');
                } else if (ch == 'r') {
                    result.append('\r');
                } else if (ch == 't') {
                    result.append('\t');
                } else {
                    result.append(ch);
                }
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                return result.toString();
            }
            result.append(ch);
        }
        return defaultValue;
    }

    private static boolean booleanField(final String content, final String name, final boolean defaultValue) {
        final int start = valueStart(content, name);
        if (start < 0) {
            return defaultValue;
        }
        if (content.startsWith("true", start)) {
            return true;
        }
        if (content.startsWith("false", start)) {
            return false;
        }
        return defaultValue;
    }

    private static long longField(final String content, final String name, final long defaultValue) {
        final int start = valueStart(content, name);
        if (start < 0) {
            return defaultValue;
        }
        final int end = longFieldEnd(content, start);
        if (end <= start) {
            return defaultValue;
        }
        final long parsed = parseLongDecimal(content, start, end);
        if (parsed == Long.MIN_VALUE && !isLiteralLongMinValue(content, start, end)) {
            return defaultValue;
        }
        return parsed;
    }

    private static int longFieldEnd(final String content, final int start) {
        int end = start;
        if (end < content.length() && content.charAt(end) == '-') {
            end++;
        }
        final int digitsStart = end;
        while (end < content.length() && isAsciiDigit(content.charAt(end))) {
            end++;
        }
        if (digitsStart == end) {
            return -1;
        }
        return end;
    }

    private static boolean isAsciiDigit(final char value) {
        return value >= '0' && value <= '9';
    }

    private static long parseLongDecimal(final String content, final int start, final int end) {
        boolean negative = false;
        int index = start;
        if (content.charAt(index) == '-') {
            negative = true;
            index++;
        }
        long result = 0L;
        final long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        final long multMin = limit / 10L;
        while (index < end) {
            final int digit = content.charAt(index) - '0';
            if (result < multMin) {
                return Long.MIN_VALUE;
            }
            result *= 10L;
            if (result < limit + digit) {
                return Long.MIN_VALUE;
            }
            result -= digit;
            index++;
        }
        if (negative) {
            return result;
        }
        if (result == Long.MIN_VALUE) {
            return Long.MIN_VALUE;
        }
        return 0L - result;
    }

    private static boolean isLiteralLongMinValue(final String content, final int start, final int end) {
        if (end - start != LONG_MIN_VALUE_LITERAL.length()) {
            return false;
        }
        for (int index = 0; index < LONG_MIN_VALUE_LITERAL.length(); index++) {
            if (content.charAt(start + index) != LONG_MIN_VALUE_LITERAL.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int valueStart(final String content, final String name) {
        final String key = "\"" + name + "\"";
        final int keyIndex = content.indexOf(key);
        if (keyIndex < 0) {
            return -1;
        }
        final int colon = content.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return -1;
        }
        int index = colon + 1;
        while (index < content.length() && Character.isWhitespace(content.charAt(index))) {
            index++;
        }
        return index;
    }

    private record ProfilingStatus(boolean supported, boolean collected) {
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

        private static Summary fromExisting(final Path report) throws IOException {
            final String content = Files.readString(report);
            return new Summary(
                stringField(content, "reachableApiScan", "not-collected"),
                longField(content, "reachableVirtualStartSites", 0L),
                longField(content, "reachableVirtualStartMethods", 0L),
                longField(content, "reachableIsVirtualSites", 0L),
                longField(content, "unsupportedBuilderApis", 0L),
                longField(content, "unsupportedBuilderApisReachable", 0L),
                longField(content, "unsupportedBuilderApisUnreachable", 0L),
                longField(content, "unsupportedExecutorApis", 0L),
                longField(content, "unsupportedExecutorApisReachable", 0L),
                longField(content, "unsupportedExecutorApisUnreachable", 0L)
            );
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
            final long unsupportedExecutorApisReachable = countExactSubjects(
                diagnostics,
                "JAVAN077",
                "Executors.newVirtualThreadPerTaskExecutor()",
                "Executors.newThreadPerTaskExecutor(ThreadFactory)",
                "Executor.execute(Runnable)",
                "ExecutorService.submit(Runnable)",
                "ExecutorService.close()",
                "Future.cancel(boolean)",
                "Future.isDone()",
                "Future.isCancelled()"
            );
            final long unsupportedExecutorApisUnreachable = countExactSubjects(
                diagnostics,
                "JAVAN177",
                "Executors.newVirtualThreadPerTaskExecutor()",
                "Executors.newThreadPerTaskExecutor(ThreadFactory)",
                "Executor.execute(Runnable)",
                "ExecutorService.submit(Runnable)",
                "ExecutorService.close()",
                "Future.cancel(boolean)",
                "Future.isDone()",
                "Future.isCancelled()"
            );
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

    private static long countExactSubjects(
        final List<Diagnostic> diagnostics,
        final String code,
        final String... subjects
    ) {
        long count = 0L;
        for (final String subject : subjects) {
            count += countSubjects(diagnostics, code, subject);
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
