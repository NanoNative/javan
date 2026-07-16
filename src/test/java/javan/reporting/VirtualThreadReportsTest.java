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
    void refreshPromotesProfilingStateWhileKeepingExistingReachabilitySummary() throws Exception {
        new VirtualThreadReports().write(tempDir);
        Files.writeString(tempDir.resolve("runtime-profiling.json"), """
            {
              "status": "collected",
              "requested": true,
              "enabled": true,
              "collectionState": "collected"
            }
            """);

        new VirtualThreadReports().refresh(tempDir);

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"profilingSupported\": true",
            "\"profilingCollected\": true",
            "\"reachableApiScan\": \"not-collected\"",
            "\"reachableVirtualStartSites\": 0"
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- profilingSupported: `true`",
            "- profilingCollected: `true`",
            "- reachableApiScan: `not-collected`",
            "Virtual-thread profiling counters are collected through runtime-profiling.* for the current host-thread-backed slice."
        );
    }

    @Test
    void refreshFallsBackWhenRuntimeProfilingReportIsMalformed() throws Exception {
        new VirtualThreadReports().write(tempDir);
        Files.writeString(tempDir.resolve("runtime-profiling.json"), """
            {
              "enabled": tru
            """);

        new VirtualThreadReports().refresh(tempDir);

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"profilingSupported\": false",
            "\"profilingCollected\": false",
            "\"reachableApiScan\": \"not-collected\""
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- profilingSupported: `false`",
            "- profilingCollected: `false`"
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
            Diagnostic.error("JAVAN077", "", "", "", "Executors.newVirtualThreadPerTaskExecutor()", "", ""),
            Diagnostic.error("JAVAN077", "", "", "", "ExecutorService.submit(Runnable)", "", ""),
            Diagnostic.warning("JAVAN177", "", "", "", "Future.cancel(boolean)", "", ""),
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
            "\"unsupportedExecutorApis\": 3",
            "\"unsupportedExecutorApisReachable\": 2",
            "\"unsupportedExecutorApisUnreachable\": 1",
            "LockSupport.park()/parkNanos(long)/parkUntil(long)/unpark(Thread)"
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- reachableVirtualStartSites: `2`",
            "- reachableVirtualStartMethods: `2`",
            "- reachableIsVirtualSites: `2`",
            "- unsupportedBuilderApis: `3`",
            "- unsupportedExecutorApis: `3`"
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

    @Test
    void writeReportsCountsExecutorSubjectsAcrossFactoryAndReceiverShapes() throws Exception {
        final EntryPoint main = new EntryPoint("com/acme/Main", "main", "()V");
        final Map<String, ClassFile> classes = Map.of(
            "com/acme/Main",
            classFile(
                "com/acme/Main",
                method(
                    "main",
                    List.of(
                        instruction(0, 184, "invokestatic", new MethodRef("java/lang/Thread", "startVirtualThread", "(Ljava/lang/Runnable;)Ljava/lang/Thread;"))
                    )
                )
            )
        );

        new VirtualThreadReports().write(
            tempDir,
            List.of(
                Diagnostic.error("JAVAN077", "", "", "", "Executors.newVirtualThreadPerTaskExecutor()", "", ""),
                Diagnostic.error("JAVAN077", "", "", "", "Executors.newThreadPerTaskExecutor(ThreadFactory)", "", ""),
                Diagnostic.error("JAVAN077", "", "", "", "Executor.execute(Runnable)", "", ""),
                Diagnostic.warning("JAVAN177", "", "", "", "ExecutorService.submit(Runnable)", "", ""),
                Diagnostic.warning("JAVAN177", "", "", "", "ExecutorService.close()", "", ""),
                Diagnostic.warning("JAVAN177", "", "", "", "Future.cancel(boolean)", "", ""),
                Diagnostic.warning("JAVAN177", "", "", "", "Future.isDone()", "", ""),
                Diagnostic.warning("JAVAN177", "", "", "", "Future.isCancelled()", "", "")
            ),
            classes,
            new CallGraph(main, List.of(main), List.of())
        );

        assertThat(Files.readString(tempDir.resolve("virtual-threads.json"))).contains(
            "\"unsupportedExecutorApis\": 8",
            "\"unsupportedExecutorApisReachable\": 3",
            "\"unsupportedExecutorApisUnreachable\": 5"
        );
        assertThat(Files.readString(tempDir.resolve("virtual-threads.md"))).contains(
            "- unsupportedExecutorApis: `8`",
            "- unsupportedExecutorApisReachable: `3`",
            "- unsupportedExecutorApisUnreachable: `5`"
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
