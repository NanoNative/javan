package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes deterministic reachable JDK intrinsic usage reports.
 */
public final class IntrinsicUsageReports {
    /**
     * Analyzes reachable bytecode and writes JSON and Markdown reports.
     *
     * @param outputDirectory javan output directory
     * @param classes parsed classes
     * @param callGraph reachability result
     * @return written report paths
     * @throws IOException when report files cannot be written
     */
    public List<Path> write(final Path outputDirectory, final Map<String, ClassFile> classes, final CallGraph callGraph) throws IOException {
        final IntrinsicUsageReport report = analyze(classes, callGraph.reachableMethods());
        final Path json = outputDirectory.resolve("reports/intrinsics.json");
        final Path markdown = outputDirectory.resolve("reports/intrinsics.md");
        Files2.writeString(json, json(report));
        Files2.writeString(markdown, markdown(report));
        return List.of(json, markdown);
    }

    /**
     * Builds report data from reachable bytecode.
     *
     * @param classes parsed classes
     * @param reachable reachable method identities
     * @return intrinsic usage report
     */
    public IntrinsicUsageReport analyze(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<IntrinsicCallCount> intrinsicCounts = zeroIntrinsicCounts();
        final List<UnsupportedJdkCallCandidate> unsupportedCounts = new ArrayList<>();

        for (final EntryPoint entry : reachable) {
            final Optional<MethodInfo> reachableMethod = method(classes, entry);
            if (reachableMethod.isEmpty() || reachableMethod.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : reachableMethod.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.methodRef().isPresent()) {
                    final MethodRef methodRef = instruction.methodRef().orElseThrow();
                    if (JdkCallSupport.isJdkCall(methodRef)) {
                        countCall(methodRef, intrinsicCounts, unsupportedCounts);
                    }
                }
            }
        }

        return new IntrinsicUsageReport(
            List.copyOf(intrinsicCounts),
            List.copyOf(unsupportedCounts),
            unsupportedCount(unsupportedCounts)
        );
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile classFile = classes.get(entry.className());
        if (classFile == null) {
            return Optional.empty();
        }
        return classFile.method(entry.methodName(), entry.descriptor());
    }

    private static void countCall(
        final MethodRef methodRef,
        final List<IntrinsicCallCount> intrinsicCounts,
        final List<UnsupportedJdkCallCandidate> unsupportedCounts
    ) {
        final Optional<JdkCallSupport.SupportedCall> supported = JdkCallSupport.supportedCall(methodRef);
        if (supported.isPresent() && supported.orElseThrow().kind() == JdkCallSupport.Kind.INTRINSIC) {
            incrementIntrinsic(intrinsicCounts, supported.orElseThrow().name());
            return;
        }
        if (supported.isPresent() || JdkCallSupport.isSupported(methodRef)) {
            return;
        }
        incrementUnsupported(unsupportedCounts, methodRef.display());
    }

    private static List<IntrinsicCallCount> zeroIntrinsicCounts() {
        final List<IntrinsicCallCount> result = new ArrayList<>();
        for (final JdkCallSupport.SupportedCall intrinsic : JdkCallSupport.intrinsics()) {
            result.add(new IntrinsicCallCount(intrinsic.name(), 0));
        }
        return result;
    }

    private static void incrementIntrinsic(final List<IntrinsicCallCount> counts, final String name) {
        for (int index = 0; index < counts.size(); index++) {
            final IntrinsicCallCount count = counts.get(index);
            if (count.name().equals(name)) {
                counts.set(index, new IntrinsicCallCount(name, count.count() + 1));
                return;
            }
        }
    }

    private static void incrementUnsupported(final List<UnsupportedJdkCallCandidate> counts, final String target) {
        for (int index = 0; index < counts.size(); index++) {
            final UnsupportedJdkCallCandidate candidate = counts.get(index);
            final int comparison = Strings2.compareAscii(target, candidate.target());
            if (comparison == 0) {
                counts.set(index, new UnsupportedJdkCallCandidate(target, candidate.count() + 1));
                return;
            }
            if (comparison < 0) {
                counts.add(index, new UnsupportedJdkCallCandidate(target, 1));
                return;
            }
        }
        counts.add(new UnsupportedJdkCallCandidate(target, 1));
    }

    private static int unsupportedCount(final List<UnsupportedJdkCallCandidate> counts) {
        int result = 0;
        for (final UnsupportedJdkCallCandidate count : counts) {
            result += count.count();
        }
        return result;
    }

    private static String json(final IntrinsicUsageReport report) {
        return "{\n"
            + "  \"intrinsics\": [\n"
            + intrinsicJson(report.intrinsics())
            + "  ],\n"
            + "  \"unsupportedJdkCallCandidateCount\": " + report.unsupportedJdkCallCandidateCount() + ",\n"
            + "  \"unsupportedJdkCallCandidates\": [\n"
            + unsupportedJson(report.unsupportedJdkCallCandidates())
            + "  ]\n"
            + "}\n";
    }

    private static String intrinsicJson(final List<IntrinsicCallCount> intrinsics) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < intrinsics.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final IntrinsicCallCount intrinsic = intrinsics.get(index);
            result.append("    {\"name\": ")
                .append(Json.string(intrinsic.name()))
                .append(", \"count\": ")
                .append(intrinsic.count())
                .append("}");
        }
        return result.append("\n").toString();
    }

    private static String unsupportedJson(final List<UnsupportedJdkCallCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < candidates.size(); index++) {
            if (index > 0) {
                result.append(",\n");
            }
            final UnsupportedJdkCallCandidate candidate = candidates.get(index);
            result.append("    {\"target\": ")
                .append(Json.string(candidate.target()))
                .append(", \"count\": ")
                .append(candidate.count())
                .append("}");
        }
        return result.append("\n").toString();
    }

    private static String markdown(final IntrinsicUsageReport report) {
        return "# Intrinsic Usage\n\n"
            + "## Planned intrinsics\n\n"
            + "| Intrinsic | Reachable call sites |\n"
            + "| --- | ---: |\n"
            + plannedRows(report.intrinsics())
            + "\n## Unsupported reachable JDK call candidates\n\n"
            + "Total reachable call sites: `" + report.unsupportedJdkCallCandidateCount() + "`\n\n"
            + "| Target | Reachable call sites |\n"
            + "| --- | ---: |\n"
            + unsupportedRows(report.unsupportedJdkCallCandidates());
    }

    private static String plannedRows(final List<IntrinsicCallCount> intrinsics) {
        final StringBuilder result = new StringBuilder();
        for (final IntrinsicCallCount intrinsic : intrinsics) {
            result.append("| `").append(intrinsic.name()).append("` | ").append(intrinsic.count()).append(" |\n");
        }
        return result.toString();
    }

    private static String unsupportedRows(final List<UnsupportedJdkCallCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "| none | 0 |\n";
        }
        final StringBuilder result = new StringBuilder();
        for (final UnsupportedJdkCallCandidate candidate : candidates) {
            result.append("| `").append(candidate.target()).append("` | ").append(candidate.count()).append(" |\n");
        }
        return result.toString();
    }
}
