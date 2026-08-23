package javan.analysis;

import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.Instruction;
import javan.classfile.MethodRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Analyzes catch-all handlers that rethrow or replace the caught throwable.
 */
public final class CaughtThrowableRethrowAnalysis {
    private static final int MAX_ANALYSIS_STATES = 8_192;
    private static final int MAX_THROWABLE_ALIASES = 128;
    private static final int MAX_TRACKED_STACK_COPIES = 64;

    private CaughtThrowableRethrowAnalysis() {
    }

    /**
     * Finds a reachable {@code athrow} whose operand aliases the handler's caught throwable.
     *
     * @param code decoded method code
     * @param handler catch-all handler to inspect
     * @return bytecode offset of a reachable caught-throwable rethrow
     */
    public static Optional<Integer> rethrowOffset(
        final CodeAttribute code,
        final CodeException handler
    ) {
        final List<Instruction> instructions = code.instructions();
        final BytecodeControlFlow.Result controlFlow = BytecodeControlFlow.analyze(code);
        if (!controlFlow.structurallyValid()) {
            return Optional.empty();
        }
        final int handlerIndex = instructionIndex(instructions, handler.handlerPc());
        if (handlerIndex < 0) {
            return Optional.empty();
        }
        final int throwableLocal = astoreLocalIndex(instructions.get(handlerIndex));
        if (throwableLocal < 0) {
            return Optional.empty();
        }

        final List<AnalysisState> pending = new ArrayList<>();
        final Set<AnalysisState> visited = new HashSet<>();
        pending.add(new AnalysisState(handlerIndex + 1, List.of(throwableLocal), 0));
        int pendingIndex = 0;
        int rethrowOffset = -1;
        while (pendingIndex < pending.size()) {
            final AnalysisState state = pending.get(pendingIndex);
            pendingIndex++;
            if (!validInstructionIndex(instructions, state.instructionIndex())) {
                return Optional.empty();
            }
            if (!visited.add(state)) {
                continue;
            }

            final Instruction instruction = instructions.get(state.instructionIndex());
            final int opcode = instruction.opcode();
            final int loadedLocal = aloadLocalIndex(instruction);
            if (loadedLocal >= 0) {
                if (!enqueueState(pending, nextState(
                    state,
                    state.instructionIndex() + 1,
                    state.aliases().contains(loadedLocal) ? 1 : 0
                ))) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode == 192) {
                return Optional.empty();
            }
            if (opcode == 89) {
                if (state.throwableStackCopies() == 0) {
                    return Optional.empty();
                }
                final int throwableStackCopies =
                    Math.min(MAX_TRACKED_STACK_COPIES, state.throwableStackCopies() + 1);
                if (!enqueueState(
                    pending,
                    nextState(state, state.instructionIndex() + 1, throwableStackCopies)
                )) {
                    return Optional.empty();
                }
                continue;
            }

            final int storedLocal = astoreLocalIndex(instruction);
            if (storedLocal >= 0) {
                final List<Integer> aliases = new ArrayList<>(state.aliases());
                if (state.throwableStackCopies() > 0) {
                    addSorted(aliases, storedLocal);
                } else {
                    aliases.remove(Integer.valueOf(storedLocal));
                }
                if (!enqueueState(pending, new AnalysisState(
                    state.instructionIndex() + 1,
                    List.copyOf(aliases),
                    Math.max(0, state.throwableStackCopies() - 1)
                ))) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode == 191) {
                if (state.throwableStackCopies() == 0) {
                    return Optional.empty();
                }
                if (rethrowOffset < 0) {
                    rethrowOffset = instruction.offset();
                }
                continue;
            }
            if (conditionalBranch(opcode)) {
                for (final int successor : controlFlow.graph().successors(state.instructionIndex())) {
                    if (!enqueueState(pending, nextState(state, successor, 0))) {
                        return Optional.empty();
                    }
                }
                continue;
            }
            if (opcode == 170 || opcode == 171) {
                for (final int successor : controlFlow.graph().successors(state.instructionIndex())) {
                    if (!enqueueState(pending, nextState(state, successor, 0))) {
                        return Optional.empty();
                    }
                }
                continue;
            }
            if (opcode == 167 || opcode == 200) {
                final List<Integer> successors = controlFlow.graph().successors(state.instructionIndex());
                if (successors.size() != 1 || !enqueueState(
                    pending,
                    nextState(state, successors.getFirst(), state.throwableStackCopies())
                )) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode >= 168 && opcode <= 177 || opcode == 198 || opcode == 199 || opcode == 201) {
                return Optional.empty();
            }
            if (!enqueueState(pending, nextState(state, state.instructionIndex() + 1, 0))) {
                return Optional.empty();
            }
        }
        return rethrowOffset < 0 ? Optional.empty() : Optional.of(rethrowOffset);
    }

    /**
     * Finds javac's straight-line catch-all handler that discards the caught throwable and throws
     * a newly constructed supported platform exception instead.
     *
     * @param code decoded method code
     * @param handler catch-all handler to inspect
     * @return replacement throwable type and {@code athrow} offset
     */
    public static Optional<ReplacementThrow> replacementThrow(
        final CodeAttribute code,
        final CodeException handler
    ) {
        if (handler.catchType().isPresent()) {
            return Optional.empty();
        }
        final List<Instruction> instructions = code.instructions();
        final int handlerIndex = instructionIndex(instructions, handler.handlerPc());
        if (handlerIndex < 0) {
            return Optional.empty();
        }
        final int throwableLocal = astoreLocalIndex(instructions.get(handlerIndex));
        if (throwableLocal < 0) {
            return Optional.empty();
        }
        int index = handlerIndex + 1;
        while (validInstructionIndex(instructions, index) && instructions.get(index).opcode() != 187) {
            final Instruction instruction = instructions.get(index);
            final int opcode = instruction.opcode();
            if (aloadLocalIndex(instruction) == throwableLocal
                || opcode >= 153 && opcode <= 177
                || opcode >= 191 && opcode <= 201) {
                return Optional.empty();
            }
            index++;
        }
        if (!validInstructionIndex(instructions, index)
            || instructions.get(index).className().isEmpty()) {
            return Optional.empty();
        }
        final String throwableType = instructions.get(index).className().orElseThrow();
        index++;
        if (!validInstructionIndex(instructions, index) || instructions.get(index).opcode() != 89) {
            return Optional.empty();
        }
        index++;
        final int messageOpcode = validInstructionIndex(instructions, index)
            ? instructions.get(index).opcode()
            : -1;
        final int messageLocal = validInstructionIndex(instructions, index)
            ? aloadLocalIndex(instructions.get(index))
            : -1;
        final boolean hasMessage = messageOpcode == 1
            || messageOpcode == 18
            || messageOpcode == 19
            || messageLocal >= 0 && messageLocal != throwableLocal;
        if (hasMessage) {
            index++;
        }
        if (!validInstructionIndex(instructions, index) || instructions.get(index).methodRef().isEmpty()) {
            return Optional.empty();
        }
        final MethodRef constructor = instructions.get(index).methodRef().orElseThrow();
        if (instructions.get(index).opcode() != 183
            || !throwableType.equals(constructor.owner())
            || !"<init>".equals(constructor.name())
            || !(hasMessage && "(Ljava/lang/String;)V".equals(constructor.descriptor())
                || !hasMessage && "()V".equals(constructor.descriptor()))) {
            return Optional.empty();
        }
        index++;
        if (!validInstructionIndex(instructions, index) || instructions.get(index).opcode() != 191) {
            return Optional.empty();
        }
        return Optional.of(new ReplacementThrow(throwableType, instructions.get(index).offset()));
    }

    private static boolean validInstructionIndex(final List<Instruction> instructions, final int index) {
        return index < instructions.size();
    }

    private static AnalysisState nextState(
        final AnalysisState state,
        final int instructionIndex,
        final int throwableStackCopies
    ) {
        return new AnalysisState(instructionIndex, state.aliases(), throwableStackCopies);
    }

    private static boolean enqueueState(
        final List<AnalysisState> pending,
        final AnalysisState state
    ) {
        if (pending.size() >= MAX_ANALYSIS_STATES || state.aliases().size() > MAX_THROWABLE_ALIASES) {
            return false;
        }
        pending.add(state);
        return true;
    }

    private static boolean conditionalBranch(final int opcode) {
        return opcode >= 153 && opcode <= 166 || opcode == 198 || opcode == 199;
    }

    private static int instructionIndex(final List<Instruction> instructions, final int offset) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() == offset) {
                return index;
            }
        }
        return -1;
    }

    private static int aloadLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 25) {
            return instruction.operands().length == 0 ? -1 : unsigned(instruction.operands()[0]);
        }
        return opcode >= 42 && opcode <= 45 ? opcode - 42 : -1;
    }

    private static int astoreLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 58) {
            return instruction.operands().length == 0 ? -1 : unsigned(instruction.operands()[0]);
        }
        return opcode >= 75 && opcode <= 78 ? opcode - 75 : -1;
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static void addSorted(final List<Integer> values, final int value) {
        if (values.contains(value)) {
            return;
        }
        int index = 0;
        while (index < values.size() && values.get(index) < value) {
            index++;
        }
        values.add(index, value);
    }

    /**
     * A straight-line catch-all replacement throw.
     *
     * @param throwableType JVM internal replacement throwable type
     * @param offset replacement {@code athrow} bytecode offset
     */
    public record ReplacementThrow(String throwableType, int offset) {
    }

    private record AnalysisState(int instructionIndex, List<Integer> aliases, int throwableStackCopies) {
    }

}
