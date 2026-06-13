package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Files2;
import javan.util.Json;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes deterministic reachable JDK intrinsic usage reports.
 */
public final class IntrinsicUsageReports {
    private static final List<IntrinsicMatcher> PLANNED_INTRINSICS = List.of(
        new IntrinsicMatcher(
            "Objects.requireNonNull",
            "java/util/Objects",
            "requireNonNull",
            Set.of("(Ljava/lang/Object;)Ljava/lang/Object;")
        ),
        new IntrinsicMatcher("Math.abs", "java/lang/Math", "abs", Set.of("(I)I", "(J)J")),
        new IntrinsicMatcher("Math.min", "java/lang/Math", "min", Set.of("(II)I", "(JJ)J")),
        new IntrinsicMatcher("Math.max", "java/lang/Math", "max", Set.of("(II)I", "(JJ)J")),
        new IntrinsicMatcher("System.nanoTime", "java/lang/System", "nanoTime", Set.of("()J")),
        new IntrinsicMatcher("System.currentTimeMillis", "java/lang/System", "currentTimeMillis", Set.of("()J")),
        new IntrinsicMatcher("System.arraycopy", "java/lang/System", "arraycopy", Set.of("(Ljava/lang/Object;ILjava/lang/Object;II)V")),
        new IntrinsicMatcher(
            "Arrays.copyOf",
            "java/util/Arrays",
            "copyOf",
            Set.of(
                "([II)[I",
                "([JI)[J",
                "([BI)[B",
                "([SI)[S",
                "([CI)[C",
                "([FI)[F",
                "([DI)[D",
                "([Ljava/lang/Object;I)[Ljava/lang/Object;"
            )
        ),
        new IntrinsicMatcher("Integer.toString", "java/lang/Integer", "toString", Set.of("(I)Ljava/lang/String;")),
        new IntrinsicMatcher("Long.toString", "java/lang/Long", "toString", Set.of("(J)Ljava/lang/String;"))
    );

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
    public IntrinsicUsageReport analyze(final Map<String, ClassFile> classes, final Set<EntryPoint> reachable) {
        final Map<String, Integer> intrinsicCounts = new LinkedHashMap<>();
        PLANNED_INTRINSICS.forEach(intrinsic -> intrinsicCounts.put(intrinsic.name(), 0));
        final Map<String, Integer> unsupportedCounts = new TreeMap<>();

        reachable.stream()
            .map(entry -> method(classes, entry))
            .flatMap(Optional::stream)
            .flatMap(method -> method.code().stream())
            .flatMap(code -> code.instructions().stream())
            .map(Instruction::methodRef)
            .flatMap(Optional::stream)
            .filter(IntrinsicUsageReports::jdkCall)
            .forEach(methodRef -> countCall(methodRef, intrinsicCounts, unsupportedCounts));

        final List<UnsupportedJdkCallCandidate> unsupported = unsupportedCounts.entrySet().stream()
            .map(entry -> new UnsupportedJdkCallCandidate(entry.getKey(), entry.getValue()))
            .toList();
        final int unsupportedCount = unsupported.stream().mapToInt(UnsupportedJdkCallCandidate::count).sum();
        return new IntrinsicUsageReport(intrinsicCallCounts(intrinsicCounts), unsupported, unsupportedCount);
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
        final Map<String, Integer> intrinsicCounts,
        final Map<String, Integer> unsupportedCounts
    ) {
        final Optional<IntrinsicMatcher> intrinsic = PLANNED_INTRINSICS.stream()
            .filter(candidate -> candidate.matches(methodRef))
            .findFirst();
        if (intrinsic.isPresent()) {
            final String name = intrinsic.orElseThrow().name();
            intrinsicCounts.put(name, intrinsicCounts.get(name) + 1);
            return;
        }
        unsupportedCounts.merge(methodRef.display(), 1, Integer::sum);
    }

    private static boolean jdkCall(final MethodRef methodRef) {
        return methodRef.owner().startsWith("java/")
            || methodRef.owner().startsWith("jdk/")
            || methodRef.owner().startsWith("sun/");
    }

    private static List<IntrinsicCallCount> intrinsicCallCounts(final Map<String, Integer> counts) {
        final List<IntrinsicCallCount> result = new ArrayList<>();
        counts.forEach((name, count) -> result.add(new IntrinsicCallCount(name, count)));
        return List.copyOf(result);
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
        return String.join(",\n", intrinsics.stream()
            .map(intrinsic -> "    {\"name\": " + Json.string(intrinsic.name()) + ", \"count\": " + intrinsic.count() + "}")
            .toList()) + "\n";
    }

    private static String unsupportedJson(final List<UnsupportedJdkCallCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "";
        }
        return String.join(",\n", candidates.stream()
            .map(candidate -> "    {\"target\": " + Json.string(candidate.target()) + ", \"count\": " + candidate.count() + "}")
            .toList()) + "\n";
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
        return String.join("", intrinsics.stream()
            .map(intrinsic -> "| `" + intrinsic.name() + "` | " + intrinsic.count() + " |\n")
            .toList());
    }

    private static String unsupportedRows(final List<UnsupportedJdkCallCandidate> candidates) {
        if (candidates.isEmpty()) {
            return "| none | 0 |\n";
        }
        return String.join("", candidates.stream()
            .map(candidate -> "| `" + candidate.target() + "` | " + candidate.count() + " |\n")
            .toList());
    }

    private record IntrinsicMatcher(String name, String owner, String methodName, Set<String> descriptors) {
        private boolean matches(final MethodRef methodRef) {
            return owner.equals(methodRef.owner())
                && methodName.equals(methodRef.name())
                && descriptors.contains(methodRef.descriptor());
        }
    }
}
