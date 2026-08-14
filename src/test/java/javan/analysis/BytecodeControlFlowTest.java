package javan.analysis;

import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class BytecodeControlFlowTest {
    @Test
    void buildsBlocksForBranchesSwitchesAndHandlers() {
        final MethodInfo method = method(2, List.of(
            instruction(0, 3, "iconst_0"),
            branch(1, 153, "ifeq", 8),
            instruction(4, 4, "iconst_1"),
            branch(5, 167, "goto", 9),
            instruction(8, 5, "iconst_2"),
            instruction(9, 172, "ireturn"),
            instruction(10, 75, "astore_0"),
            instruction(11, 2, "iconst_m1"),
            instruction(12, 172, "ireturn")
        ), List.of(new CodeException(0, 9, 10, Optional.of("java/lang/RuntimeException"))));

        final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method);

        assertThat(result.valid()).isTrue();
        assertThat(result.graph().blocks()).extracting(BytecodeControlFlow.Block::startOffset)
            .containsExactly(0, 4, 8, 9, 10);
        assertThat(result.graph().edges()).extracting(BytecodeControlFlow.Edge::kind)
            .contains(BytecodeControlFlow.EdgeKind.BRANCH, BytecodeControlFlow.EdgeKind.FALLTHROUGH,
                BytecodeControlFlow.EdgeKind.EXCEPTION);
    }

    @Test
    void rejectsBranchTargetsThatAreNotInstructionBoundaries() {
        final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method(1, List.of(
            branch(0, 167, "goto", 2),
            instruction(3, 177, "return")
        ), List.of()));

        assertThat(result.valid()).isFalse();
        assertThat(result.structurallyValid()).isFalse();
        assertThat(result.issues()).containsExactly("branch at 0 targets non-instruction offset 2");
    }

    @Test
    void rejectsDifferentStackDepthsAtAMerge() {
        final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method(2, List.of(
            instruction(0, 3, "iconst_0"),
            branch(1, 153, "ifeq", 8),
            instruction(4, 4, "iconst_1"),
            branch(5, 167, "goto", 9),
            instruction(8, 0, "nop"),
            instruction(9, 177, "return")
        ), List.of()));

        assertThat(result.valid()).isFalse();
        assertThat(result.structurallyValid()).isTrue();
        assertThat(result.issues()).containsExactly("stack merge at 9 has depths 0 and 1");
    }

    @Test
    void treatsPrimitiveArraysAsSingleInvocationArguments() {
        final Instruction call = new Instruction(
            2, 184, "invokestatic", new byte[0],
            Optional.of(new MethodRef("example/Target", "accept", "([J[D)V")), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
        final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method(2, List.of(
            instruction(0, 1, "aconst_null"),
            instruction(1, 1, "aconst_null"),
            call,
            instruction(3, 177, "return")
        ), List.of()));

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsStackDuplicationWithoutAnOperand() {
        final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method(2, List.of(
            instruction(0, 89, "dup"),
            instruction(1, 177, "return")
        ), List.of()));

        assertThat(result.issues()).containsExactly("stack underflow at 0");
    }

    @Test
    void preservesBasicBlockSelfLoops() {
        final BytecodeControlFlow.Result result = BytecodeControlFlow.analyze(method(0, List.of(
            branch(0, 167, "goto", 0)
        ), List.of()));

        assertThat(result.valid()).isTrue();
        assertThat(result.graph().edges()).containsExactly(
            new BytecodeControlFlow.Edge(0, 0, BytecodeControlFlow.EdgeKind.BRANCH)
        );
    }

    private static MethodInfo method(
        final int maxStack,
        final List<Instruction> instructions,
        final List<CodeException> handlers
    ) {
        final int length = instructions.getLast().offset() + 1;
        return new MethodInfo(0x0009, "main", "()V", Optional.of(new CodeAttribute(
            maxStack, 1, new byte[length], handlers.size(), handlers, instructions
        )));
    }

    private static Instruction branch(final int offset, final int opcode, final String mnemonic, final int target) {
        final int relative = target - offset;
        return instruction(offset, opcode, mnemonic, new byte[] {(byte) (relative >>> 8), (byte) relative});
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic) {
        return instruction(offset, opcode, mnemonic, new byte[0]);
    }

    private static Instruction instruction(final int offset, final int opcode, final String mnemonic, final byte[] operands) {
        return new Instruction(
            offset, opcode, mnemonic, operands, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
