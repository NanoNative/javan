package javan.reporting;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.verify.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class ThreadReportsTest {
    @Test
    void summarizeClassifiesTinyCpuThreadStartTask() {
        final ThreadReports.Summary summary = summarizeReachable(
            List.of(),
            method(
                "main",
                List.of(
                    instruction(0, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                    instruction(1, 177, "return")
                )
            )
        );

        assertThat(summary.tinyCpuTaskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("TINY_CPU_TASK");
    }

    @Test
    void summarizeClassifiesCpuBoundLoopingThreadStartTask() {
        final ThreadReports.Summary summary = summarizeReachable(
            List.of(),
            method(
                "main",
                List.of(
                    instruction(0, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                    instruction(1, 0, "nop"),
                    instruction(2, 0, "nop"),
                    instruction(3, 0, "nop"),
                    instruction(4, 0, "nop"),
                    instruction(5, 0, "nop"),
                    instruction(6, 0, "nop"),
                    instruction(7, 0, "nop"),
                    instruction(8, 0, "nop"),
                    instruction(9, 0, "nop"),
                    instruction(10, 0, "nop"),
                    instruction(11, 0, "nop"),
                    instructionOperands(12, 167, "goto", 0xFF, 0xFF)
                )
            )
        );

        assertThat(summary.cpuBoundTaskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("CPU_BOUND");
    }

    @Test
    void summarizeClassifiesIoBoundThreadStartTask() {
        final ThreadReports.Summary summary = summarizeReachable(
            List.of(),
            method(
                "main",
                List.of(
                    instruction(0, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                    instruction(1, 182, "invokevirtual", new MethodRef("java/io/InputStream", "read", "()I")),
                    instruction(2, 87, "pop"),
                    instruction(3, 177, "return")
                )
            )
        );

        assertThat(summary.ioBoundTaskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("IO_BOUND");
    }

    @Test
    void summarizeClassifiesMixedIoLoopThreadStartTask() {
        final ThreadReports.Summary summary = summarizeReachable(
            List.of(),
            method(
                "main",
                List.of(
                    instruction(0, 182, "invokevirtual", new MethodRef("java/lang/Thread", "start", "()V")),
                    instruction(1, 182, "invokevirtual", new MethodRef("java/io/InputStream", "read", "()I")),
                    instruction(2, 87, "pop"),
                    instructionOperands(3, 167, "goto", 0xFF, 0xFF)
                )
            )
        );

        assertThat(summary.mixedTaskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("MIXED");
    }

    @Test
    void summarizeClassifiesGenericBuilderStartTask() {
        final ThreadReports.Summary summary = summarizeReachable(
            List.of(),
            method(
                "main",
                List.of(
                    instruction(0, 185, "invokeinterface", new MethodRef("java/lang/Thread$Builder", "start", "(Ljava/lang/Runnable;)Ljava/lang/Thread;")),
                    instruction(1, 177, "return")
                )
            )
        );

        assertThat(summary.tinyCpuTaskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("TINY_CPU_TASK");
    }

    @Test
    void summarizeClassifiesBlockingWaitDiagnosticMethod() {
        final ThreadReports.Summary summary = ThreadReports.summarize(List.of(
            Diagnostic.error("JAVAN178", "", "com/acme/Main", "main()V", "Thread.sleep(long)", "", "")
        ));

        assertThat(summary.blockingTaskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("BLOCKING_WAIT");
    }

    @Test
    void summarizeClassifiesPinningRiskDiagnosticMethod() {
        final ThreadReports.Summary summary = ThreadReports.summarize(List.of(
            Diagnostic.error("JAVAN076", "", "com/acme/Main", "main()V", "monitorenter", "", "")
        ));

        assertThat(summary.pinningRiskMethods()).isEqualTo(1L);
        assertThat(summary.methods()).singleElement().extracting(ThreadReports.MethodActivity::classification).isEqualTo("PINNING_RISK");
    }

    private static ThreadReports.Summary summarizeReachable(final List<Diagnostic> diagnostics, final MethodInfo method) {
        final EntryPoint entry = new EntryPoint("com/acme/Main", method.name(), method.descriptor());
        final ClassFile classFile = new ClassFile(
            69,
            "com/acme/Main",
            "java/lang/Object",
            0,
            List.of(),
            List.of(),
            List.of(method),
            Path.of("Main.class"),
            true
        );
        return ThreadReports.summarize(diagnostics, Map.of(classFile.name(), classFile), List.of(entry));
    }

    private static MethodInfo method(final String name, final List<Instruction> instructions) {
        return new MethodInfo(
            0x0008,
            name,
            "()V",
            Optional.of(new CodeAttribute(4, 1, new byte[0], 0, instructions))
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

    private static Instruction instructionOperands(final int offset, final int opcode, final String mnemonic, final int... operands) {
        final byte[] bytes = new byte[operands.length];
        for (int index = 0; index < operands.length; index++) {
            bytes[index] = (byte) operands[index];
        }
        return new Instruction(
            offset,
            opcode,
            mnemonic,
            bytes,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
