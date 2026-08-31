package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldRef;
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

import static org.assertj.core.api.Assertions.assertThat;

final class LoggingReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void reportsReachableLoggerLevelsWithoutRecordingMessages() throws Exception {
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                invocation("java/util/logging/Logger", "getLogger", "(Ljava/lang/String;)Ljava/util/logging/Logger;"),
                invocation("java/util/logging/Logger", "info", "(Ljava/lang/String;)V"),
                level("WARNING"),
                literal("warning-message"),
                invocation("java/util/logging/Logger", "log", "(Ljava/util/logging/Level;Ljava/lang/String;)V"),
                nonLiteralLevel("FINE"),
                literal("dynamic-level"),
                invocation("java/util/logging/Logger", "log", "(Ljava/util/logging/Level;Ljava/lang/String;)V"),
                invocation("java/util/logging/Logger", "getName", "()Ljava/lang/String;")
            )),
            "com/acme/Hidden",
            classFile("com/acme/Hidden", method(
                "hidden",
                "()V",
                invocation("java/util/logging/Logger", "severe", "(Ljava/lang/String;)V")
            ))
        );
        final LoggingReports reports = new LoggingReports();
        final LoggingReports.Report report = reports.analyze(classes, List.of(entry));

        final List<Path> written = reports.write(tempDir, classes, new CallGraph(entry, List.of(entry), List.of()));

        assertThat(report.reachableLoggerCallSiteCount()).isEqualTo(5);
        assertThat(report.levelCallSiteCount()).isEqualTo(3);
        assertThat(report.inferredLevelCallSiteCount()).isEqualTo(1);
        assertThat(report.literalLevelCallSiteCount()).isEqualTo(1);
        assertThat(report.unknownLevelCallSiteCount()).isEqualTo(1);
        assertThat(report.nonEmittingCallSiteCount()).isEqualTo(2);
        assertThat(report.levels()).containsExactly(
            new LoggingReports.LevelCount("SEVERE", 0, 0),
            new LoggingReports.LevelCount("WARNING", 1, 0),
            new LoggingReports.LevelCount("INFO", 0, 1),
            new LoggingReports.LevelCount("CONFIG", 0, 0),
            new LoggingReports.LevelCount("FINE", 0, 0),
            new LoggingReports.LevelCount("FINER", 0, 0),
            new LoggingReports.LevelCount("FINEST", 0, 0),
            new LoggingReports.LevelCount("OFF", 0, 0),
            new LoggingReports.LevelCount("ALL", 0, 0)
        );
        assertThat(report.unknownCalls()).containsExactly(
            new LoggingReports.UnknownCall("java/util/logging/Logger.log(Ljava/util/logging/Level;Ljava/lang/String;)V", 1)
        );
        assertThat(written).containsExactly(
            tempDir.resolve("reports/logging.json"),
            tempDir.resolve("reports/logging.md")
        );
        assertThat(Files.readString(tempDir.resolve("reports/logging.json"))).contains(
            "\"reachableLoggerCallSiteCount\": 5",
            "\"literalLevelCallSiteCount\": 1",
            "\"unknownLevelCallSiteCount\": 1",
            "{\"level\": \"WARNING\", \"literal\": 1, \"inferred\": 0}",
            "{\"level\": \"INFO\", \"literal\": 0, \"inferred\": 1}"
        ).doesNotContain("warning-message", "dynamic-level");
        assertThat(Files.readString(tempDir.resolve("reports/logging.md"))).contains(
            "# Reachable Logging",
            "| `WARNING` | 1 | 0 |",
            "| `INFO` | 0 | 1 |",
            "| `java/util/logging/Logger.log(Ljava/util/logging/Level;Ljava/lang/String;)V` | 1 |"
        ).doesNotContain("warning-message", "dynamic-level");
    }

    private static ClassFile classFile(final String name, final MethodInfo method) {
        return new ClassFile(65, name, "java/lang/Object", 0, List.of(), List.of(), List.of(method), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final String name, final String descriptor, final Instruction... instructions) {
        return new MethodInfo(0, name, descriptor, Optional.of(new CodeAttribute(1, 1, new byte[0], 0, List.of(instructions))));
    }

    private static Instruction invocation(final String owner, final String name, final String descriptor) {
        return new Instruction(
            0, 182, "invokevirtual", new byte[0], Optional.of(new MethodRef(owner, name, descriptor)), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction level(final String name) {
        return level(178, "getstatic", name);
    }

    private static Instruction nonLiteralLevel(final String name) {
        return level(179, "putstatic", name);
    }

    private static Instruction level(final int opcode, final String mnemonic, final String name) {
        return new Instruction(
            0, opcode, mnemonic, new byte[0], Optional.empty(),
            Optional.of(new FieldRef("java/util/logging/Level", name, "Ljava/util/logging/Level;")),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    private static Instruction literal(final String value) {
        return new Instruction(
            0, 18, "ldc", new byte[0], Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value),
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
