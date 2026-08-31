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

import static org.assertj.core.api.Assertions.assertThat;

final class FileReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void reportsOnlyReachableLiteralPathsAndClassifiesDynamicPathsAsUnknown() throws Exception {
        final EntryPoint entry = new EntryPoint("com/acme/Main", "main", "([Ljava/lang/String;)V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile("com/acme/Main", method(
                "main",
                "([Ljava/lang/String;)V",
                path("data"), resolve("config.properties"), files("readString", "(Ljava/nio/file/Path;)Ljava/lang/String;"),
                path("cache.txt"), literal("payload"), zero(), emptyArray("java/nio/file/OpenOption"),
                files("writeString", "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"),
                path("stale.txt"), files("deleteIfExists", "(Ljava/nio/file/Path;)Z"),
                path("cache.txt"), files("size", "(Ljava/nio/file/Path;)J"),
                path("logs"), store(1), load(1), zero(), emptyArray("java/nio/file/attribute/FileAttribute"),
                files("createDirectories", "(Ljava/nio/file/Path;[Ljava/nio/file/attribute/FileAttribute;)Ljava/nio/file/Path;"),
                load(3), files("readAllBytes", "(Ljava/nio/file/Path;)[B"),
                newObject(), duplicate(), literal("legacy.txt"), instanceInvocation(183, "invokespecial", "java/io/FileInputStream", "<init>", "(Ljava/lang/String;)V")
            )),
            "com/acme/Hidden",
            classFile("com/acme/Hidden", method(
                "hidden", "()V", path("hidden.txt"), files("writeString", "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;")
            ))
        );
        final FileReports reports = new FileReports();
        final FileReports.Report report = reports.analyze(classes, List.of(entry));

        final List<Path> written = reports.write(tempDir, classes, new CallGraph(entry, List.of(entry), List.of()));

        assertThat(report.reachableFileCallSiteCount()).isEqualTo(7);
        assertThat(report.readCallSiteCount()).isEqualTo(3);
        assertThat(report.writeCallSiteCount()).isEqualTo(2);
        assertThat(report.deleteCallSiteCount()).isEqualTo(1);
        assertThat(report.metadataCallSiteCount()).isEqualTo(1);
        assertThat(report.knownFilePathCount()).isEqualTo(5);
        assertThat(report.knownPathReferenceCount()).isEqualTo(6);
        assertThat(report.unknownPathCallSiteCount()).isEqualTo(1);
        assertThat(report.knownPaths()).containsExactly(
            new FileReports.PathCount("cache.txt", "metadata", 1),
            new FileReports.PathCount("cache.txt", "write", 1),
            new FileReports.PathCount("data/config.properties", "read", 1),
            new FileReports.PathCount("legacy.txt", "read", 1),
            new FileReports.PathCount("logs", "write", 1),
            new FileReports.PathCount("stale.txt", "delete", 1)
        );
        assertThat(report.unknownPathCalls()).containsExactly(
            new FileReports.CallCount("java/nio/file/Files.readAllBytes(Ljava/nio/file/Path;)[B", "read", 1)
        );
        assertThat(written).containsExactly(
            tempDir.resolve("reports/files.json"),
            tempDir.resolve("reports/files.md")
        );
        assertThat(Files.readString(tempDir.resolve("reports/files.json"))).contains(
            "\"reachableFileCallSiteCount\": 7",
            "\"knownFilePathCount\": 5",
            "\"knownPathReferenceCount\": 6",
            "{\"path\": \"data/config.properties\", \"operation\": \"read\", \"count\": 1}",
            "{\"path\": \"legacy.txt\", \"operation\": \"read\", \"count\": 1}"
        ).doesNotContain("hidden.txt");
        assertThat(Files.readString(tempDir.resolve("reports/files.md"))).contains(
            "# Reachable File Access",
            "| `data/config.properties` | read | 1 |",
            "| `legacy.txt` | read | 1 |"
        ).doesNotContain("hidden.txt");
    }

    private static Instruction[] path(final String value) {
        return new Instruction[]{literal(value), zero(), emptyArray("java/lang/String"), pathOf()};
    }

    private static Instruction pathOf() {
        return invocation("java/nio/file/Path", "of", "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;");
    }

    private static Instruction[] resolve(final String value) {
        return new Instruction[]{
            literal(value), instanceInvocation(185, "invokeinterface", "java/nio/file/Path", "resolve", "(Ljava/lang/String;)Ljava/nio/file/Path;")
        };
    }

    private static Instruction files(final String name, final String descriptor) {
        return invocation("java/nio/file/Files", name, descriptor);
    }

    private static ClassFile classFile(final String name, final MethodInfo method) {
        return new ClassFile(65, name, "java/lang/Object", 0, List.of(), List.of(), List.of(method), Path.of(name + ".class"), true);
    }

    private static MethodInfo method(final String name, final String descriptor, final Object... parts) {
        final List<Instruction> instructions = new java.util.ArrayList<>();
        for (final Object part : parts) {
            if (part instanceof Instruction instruction) {
                instructions.add(instruction);
            } else {
                instructions.addAll(List.of((Instruction[]) part));
            }
        }
        return new MethodInfo(0, name, descriptor, Optional.of(new CodeAttribute(2, 4, new byte[0], 0, instructions)));
    }

    private static Instruction invocation(final String owner, final String name, final String descriptor) {
        return instruction(184, "invokestatic", Optional.of(new MethodRef(owner, name, descriptor)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction instanceInvocation(
        final int opcode,
        final String mnemonic,
        final String owner,
        final String name,
        final String descriptor
    ) {
        return instruction(opcode, mnemonic, Optional.of(new MethodRef(owner, name, descriptor)), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction literal(final String value) {
        return instruction(18, "ldc", Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(value), Optional.empty());
    }

    private static Instruction zero() {
        return instruction(3, "iconst_0", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction emptyArray(final String component) {
        return instruction(189, "anewarray", Optional.empty(), Optional.empty(), Optional.of(component), Optional.empty(), Optional.empty());
    }

    private static Instruction load(final int index) {
        return instruction(25, "aload", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), (byte) index);
    }

    private static Instruction store(final int index) {
        return instruction(58, "astore", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), (byte) index);
    }

    private static Instruction newObject() {
        return instruction(187, "new", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction duplicate() {
        return instruction(89, "dup", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static Instruction instruction(
        final int opcode,
        final String mnemonic,
        final Optional<MethodRef> methodRef,
        final Optional<javan.classfile.FieldRef> fieldRef,
        final Optional<String> className,
        final Optional<String> stringValue,
        final Optional<Integer> intValue,
        final byte... operands
    ) {
        return new Instruction(
            0, opcode, mnemonic, operands, methodRef, fieldRef, className, stringValue, intValue, Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
