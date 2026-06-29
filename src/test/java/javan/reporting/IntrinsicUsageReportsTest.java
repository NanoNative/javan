package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class IntrinsicUsageReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void countsReachableSupportedAndUnsupportedJdkCallsDeterministically() throws Exception {
        final IntrinsicUsageReports reports = new IntrinsicUsageReports();
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                instruction("java/lang/Math", "max", "(II)I"),
                instruction("java/lang/Math", "abs", "(I)I"),
                instruction("java/lang/System", "nanoTime", "()J"),
                instruction("java/lang/System", "nanoTime", "()J"),
                instruction("java/lang/System", "arraycopy", "(Ljava/lang/Object;ILjava/lang/Object;II)V"),
                instruction("java/util/Arrays", "copyOf", "([II)[I"),
                instruction("java/lang/Integer", "toString", "(I)Ljava/lang/String;"),
                instruction("java/lang/Long", "toString", "(J)Ljava/lang/String;"),
                instruction("java/io/PrintStream", "println", "(I)V"),
                instruction("java/lang/Thread", "currentThread", "()Ljava/lang/Thread;"),
                instruction("java/util/List", "of", "()Ljava/util/List;"),
                instruction("java/util/List", "getFirst", "()Ljava/lang/Object;"),
                instruction("java/lang/String", "valueOf", "(I)Ljava/lang/String;")
            ))
        );
        final CallGraph callGraph = new CallGraph(entry, List.of(entry), List.of());

        final List<Path> written = reports.write(tempDir, classes, callGraph);
        final IntrinsicUsageReport report = reports.analyze(classes, callGraph.reachableMethods());

        assertThat(written).containsExactly(
            tempDir.resolve("reports/intrinsics.json"),
            tempDir.resolve("reports/intrinsics.md")
        );
        assertThat(report.intrinsics()).containsExactly(
            new IntrinsicCallCount("Objects.requireNonNull", 0),
            new IntrinsicCallCount("Math.abs", 1),
            new IntrinsicCallCount("Math.min", 0),
            new IntrinsicCallCount("Math.max", 1),
            new IntrinsicCallCount("Math.toIntExact", 0),
            new IntrinsicCallCount("System.nanoTime", 2),
            new IntrinsicCallCount("System.currentTimeMillis", 0),
            new IntrinsicCallCount("System.lineSeparator", 0),
            new IntrinsicCallCount("System.getenv", 0),
            new IntrinsicCallCount("System.getProperty", 0),
            new IntrinsicCallCount("System.arraycopy", 1),
            new IntrinsicCallCount("System.exit", 0),
            new IntrinsicCallCount("Arrays.copyOf", 1),
            new IntrinsicCallCount("Arrays.copyOfRange", 0),
            new IntrinsicCallCount("Integer.toString", 1),
            new IntrinsicCallCount("Long.toString", 1),
            new IntrinsicCallCount("Float.toString", 0),
            new IntrinsicCallCount("Float.intBitsToFloat", 0),
            new IntrinsicCallCount("Double.toString", 0),
            new IntrinsicCallCount("Double.longBitsToDouble", 0),
            new IntrinsicCallCount("Boolean.toString", 0),
            new IntrinsicCallCount("String.valueOf", 1)
        );
        assertThat(report.runtimeCalls()).contains(
            new RuntimeJdkCallCount("List.getFirst", 1),
            new RuntimeJdkCallCount("List.of", 1),
            new RuntimeJdkCallCount("PrintStream.println", 1),
            new RuntimeJdkCallCount("Thread.currentThread", 1)
        );
        assertThat(report.runtimeCallSiteCount()).isEqualTo(4);
        assertThat(report.supportedDirectJdkCalls()).isEmpty();
        assertThat(report.supportedDirectJdkCallSiteCount()).isZero();
        assertThat(report.supportedJdkCallSiteCount()).isEqualTo(13);
        assertThat(report.unsupportedJdkCallCandidateCount()).isZero();
        assertThat(report.unsupportedJdkCallCandidates()).isEmpty();
        assertThat(Files.readString(tempDir.resolve("reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 0}",
                "{\"name\": \"Math.abs\", \"count\": 1}",
                "{\"name\": \"Arrays.copyOf\", \"count\": 1}",
                "{\"name\": \"Integer.toString\", \"count\": 1}",
                "{\"name\": \"String.valueOf\", \"count\": 1}",
                "\"intrinsicCallSiteCount\": 9",
                "{\"name\": \"List.getFirst\", \"count\": 1}",
                "{\"name\": \"List.of\", \"count\": 1}",
                "{\"name\": \"PrintStream.println\", \"count\": 1}",
                "{\"name\": \"Thread.currentThread\", \"count\": 1}",
                "\"runtimeCallSiteCount\": 4",
                "\"supportedDirectJdkCallSiteCount\": 0",
                "\"supportedJdkCallSiteCount\": 13",
                "\"unsupportedJdkCallCandidateCount\": 0"
            );
        assertThat(Files.readString(tempDir.resolve("reports/intrinsics.md")))
            .contains(
                "Supported reachable JDK call sites: `13`",
                "Runtime-registry reachable call sites: `4`",
                "Supported-direct reachable call sites: `0`",
                "| `System.nanoTime` | 2 |",
                "| `System.arraycopy` | 1 |",
                "| `String.valueOf` | 1 |",
                "| `List.getFirst` | 1 |",
                "| `List.of` | 1 |",
                "| `PrintStream.println` | 1 |",
                "| `Thread.currentThread` | 1 |",
                "| none | 0 |",
                "Total reachable call sites: `0`"
            );
    }

    @Test
    void writesZeroCountsAndNoneRowForReachableCodeWithoutJdkCalls() throws Exception {
        final IntrinsicUsageReports reports = new IntrinsicUsageReports();
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method("main", "([Ljava/lang/String;)V"))
        );

        reports.write(tempDir, classes, new CallGraph(entry, List.of(entry), List.of()));

        assertThat(Files.readString(tempDir.resolve("reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Math.max\", \"count\": 0}",
                "\"runtimeCallSiteCount\": 0",
                "\"supportedDirectJdkCallSiteCount\": 0",
                "\"supportedJdkCallSiteCount\": 0",
                "\"unsupportedJdkCallCandidateCount\": 0",
                "\"supportedDirectJdkCalls\": [\n  ],",
                "\"unsupportedJdkCallCandidates\": [\n  ]"
            );
        assertThat(Files.readString(tempDir.resolve("reports/intrinsics.md")))
            .contains("Supported reachable JDK call sites: `0`", "| none | 0 |");
    }

    @Test
    void unsupportedOverloadsAreReportedAsUnsupportedCandidates() {
        final IntrinsicUsageReports reports = new IntrinsicUsageReports();
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                instruction("java/lang/Math", "abs", "(F)F"),
                instruction("java/util/Objects", "requireNonNullElse", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
            ))
        );

        final IntrinsicUsageReport report = reports.analyze(classes, List.of(entry));

        assertThat(report.intrinsics()).contains(
            new IntrinsicCallCount("Math.abs", 1),
            new IntrinsicCallCount("Arrays.copyOf", 0),
            new IntrinsicCallCount("Objects.requireNonNull", 0)
        );
        assertThat(report.runtimeCallSiteCount()).isZero();
        assertThat(report.supportedDirectJdkCallSiteCount()).isZero();
        assertThat(report.supportedJdkCallSiteCount()).isEqualTo(1);
        assertThat(report.unsupportedJdkCallCandidateCount()).isEqualTo(1);
        assertThat(report.unsupportedJdkCallCandidates()).containsExactly(
            new UnsupportedJdkCallCandidate("java/util/Objects.requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", 1)
        );
    }

    private static ClassFile classFile(final String name, final MethodInfo method) {
        return new ClassFile(65, name, "java/lang/Object", 0, List.of(), List.of(), List.of(method), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final String name, final String descriptor, final Instruction... instructions) {
        return new MethodInfo(
            0,
            name,
            descriptor,
            Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(instructions)))
        );
    }

    private static Instruction instruction(final String owner, final String name, final String descriptor) {
        return new Instruction(
            0,
            184,
            "invokestatic",
            new byte[0],
            Optional.of(new MethodRef(owner, name, descriptor)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
