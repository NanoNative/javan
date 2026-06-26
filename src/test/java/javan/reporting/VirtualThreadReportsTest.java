package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class VirtualThreadReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeReportsNotCollectedWhenNoReachabilityScanIsProvided() throws Exception {
        new VirtualThreadReports().write(tempDir);

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"not-collected\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"reachableIsVirtualSites\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedExecutorApis\": 0"
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- reachableApiScan: `not-collected`",
            "- unsupportedBuilderApis: `0`",
            "- unsupportedExecutorApis: `0`"
        );
    }

    @Test
    void writeReportsScannedReachableVirtualThreadSupportAndUnsupportedCounts() throws Exception {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final EntryPoint helper = new EntryPoint("com/acme/Helper", "helper", "()V");
        final EntryPoint missing = new EntryPoint("com/acme/Missing", "missing", "()V");
        final EntryPoint noCode = new EntryPoint("com/acme/NoCode", "noop", "()V");

        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main", classFile(
                "com/acme/Main",
                method(
                    "main",
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "isVirtual", "()Z")),
                        instruction(2, 0, "nop")
                    )
                )
            ),
            "com/acme/Helper", classFile(
                "com/acme/Helper",
                method(
                    "helper",
                    List.of(
                        instruction(0, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder$OfVirtual", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "isVirtual", "()Z"))
                    )
                )
            ),
            "com/acme/NoCode", new ClassFile(
                69,
                "com/acme/NoCode",
                "java/lang/Object",
                0,
                List.of(),
                List.of(),
                List.of(new MethodInfo(0x0008, "noop", "()V", Optional.empty())),
                Path.of("NoCode.class"),
                true
            )
        );
        final List<Diagnostic> diagnostics = List.of(
            Diagnostic.error("JAVAN077", "", "", "", "Thread.ofVirtual()", "", ""),
            Diagnostic.error("JAVAN077", "", "", "", "Thread.Builder.OfVirtual.factory()", "", ""),
            Diagnostic.warning("JAVAN177", "", "", "", "Thread.Builder.OfVirtual.scheduler()", "", ""),
            Diagnostic.error("OTHER", "", "", "", "Thread.Builder.OfVirtual.ignored()", "", "")
        );

        new VirtualThreadReports().write(
            tempDir,
            diagnostics,
            classes,
            new CallGraph(main, List.of(main, helper, missing, noCode), List.of())
        );

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 2",
            "\"reachableVirtualStartMethods\": 2",
            "\"reachableIsVirtualSites\": 2",
            "\"unsupportedBuilderApis\": 3",
            "\"unsupportedBuilderApisReachable\": 2",
            "\"unsupportedBuilderApisUnreachable\": 1",
            "\"unsupportedExecutorApis\": 0",
            "\"unsupportedExecutorApisReachable\": 0",
            "\"unsupportedExecutorApisUnreachable\": 0",
            "LockSupport.park()/parkNanos(long)/parkUntil(long)/unpark(Thread)"
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- reachableVirtualStartSites: `2`",
            "- reachableVirtualStartMethods: `2`",
            "- reachableIsVirtualSites: `2`",
            "- unsupportedBuilderApis: `3`",
            "- unsupportedExecutorApis: `0`"
        );
    }

    @Test
    void writeReportsScannedWithoutVirtualApisKeepsReachableCountsAtZero() throws Exception {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile(
                "com/acme/Main",
                method(
                    "main",
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/System", "nanoTime", "()J")),
                        instruction(1, 0, "nop")
                    )
                )
            )
        );

        new VirtualThreadReports().write(
            tempDir,
            List.of(Diagnostic.warning("OTHER", "", "", "", "ignored", "", "")),
            classes,
            new CallGraph(main, List.of(main), List.of())
        );

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"reachableIsVirtualSites\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedExecutorApis\": 0"
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- reachableVirtualStartSites: `0`",
            "- reachableVirtualStartMethods: `0`",
            "- reachableIsVirtualSites: `0`"
        );
    }

    @Test
    void writeReportsCountsGenericBuilderSubjectsWithoutDoubleCountingTypedBuilderSubjects() throws Exception {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile(
                "com/acme/Main",
                method(
                    "main",
                    List.of(
                        instruction(0, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                        instruction(1, 182, "invokevirtual", new MethodRef("java/lang/Thread", "isVirtual", "()Z"))
                    )
                )
            )
        );

        new VirtualThreadReports().write(
            tempDir,
            List.of(
                Diagnostic.error("JAVAN077", "", "", "", "Thread.Builder.start(Runnable)", "", ""),
                Diagnostic.warning("JAVAN177", "", "", "", "Thread.Builder.factory()", "", ""),
                Diagnostic.error("JAVAN077", "", "", "", "Thread.Builder.OfVirtual.factory()", "", "")
            ),
            classes,
            new CallGraph(main, List.of(main), List.of())
        );

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"reachableVirtualStartSites\": 1",
            "\"reachableVirtualStartMethods\": 1",
            "\"reachableIsVirtualSites\": 1",
            "\"unsupportedBuilderApis\": 3",
            "\"unsupportedBuilderApisReachable\": 2",
            "\"unsupportedBuilderApisUnreachable\": 1"
        );
    }

    private static ClassFile classFile(final String name, final MethodInfo method) {
        return new ClassFile(
            69,
            name,
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(method),
            Path.of(name + ".class"),
            true
        );
    }

    private static MethodInfo method(final String name, final List<Instruction> instructions) {
        return new MethodInfo(
            0x0008,
            name,
            "()V",
            Optional.of(new CodeAttribute(2, 1, new byte[0], 0, instructions))
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic, final MethodRef methodRef) {
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            new byte[0],
            Optional.of(methodRef),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
