package javan.analysis;

import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.Instruction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a reachable rethrow of the throwable stored by a catch-all handler.
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
                if (!enqueueOffset(pending, instructions, state, branchTarget(instruction), 0)) {
                    return Optional.empty();
                }
                if (!enqueueState(pending, nextState(state, state.instructionIndex() + 1, 0))) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode == 170 || opcode == 171) {
                if (!enqueueSwitchTargets(pending, instructions, state, instruction)) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode == 167) {
                if (!enqueueOffset(
                    pending,
                    instructions,
                    state,
                    branchTarget(instruction),
                    state.throwableStackCopies()
                )) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode >= 168 && opcode <= 177 || opcode >= 198 && opcode <= 201) {
                return Optional.empty();
            }
            if (!enqueueState(pending, nextState(state, state.instructionIndex() + 1, 0))) {
                return Optional.empty();
            }
        }
        return rethrowOffset < 0 ? Optional.empty() : Optional.of(rethrowOffset);
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

    private static boolean enqueueOffset(
        final List<AnalysisState> pending,
        final List<Instruction> instructions,
        final AnalysisState state,
        final int offset,
        final int throwableStackCopies
    ) {
        final int index = instructionIndex(instructions, offset);
        if (index < 0) {
            return false;
        }
        return enqueueState(pending, nextState(state, index, throwableStackCopies));
    }

    private static boolean enqueueSwitchTargets(
        final List<AnalysisState> pending,
        final List<Instruction> instructions,
        final AnalysisState state,
        final Instruction instruction
    ) {
        final byte[] operands = instruction.operands();
        final int padding = switchPadding(instruction.offset());
        if (!enqueueOffset(
            pending,
            instructions,
            state,
            instruction.offset() + int32(operands, padding),
            0
        )) {
            return false;
        }
        if (instruction.opcode() == 170) {
            final int low = int32(operands, padding + 4);
            final int high = int32(operands, padding + 8);
            final long entries = (long) high - low + 1;
            if (entries < 0 || entries > MAX_ANALYSIS_STATES - pending.size()) {
                return false;
            }
            int operandOffset = padding + 12;
            for (long index = 0; index < entries; index++) {
                if (!enqueueOffset(
                    pending,
                    instructions,
                    state,
                    instruction.offset() + int32(operands, operandOffset),
                    0
                )) {
                    return false;
                }
                operandOffset += 4;
            }
            return true;
        }

        final int pairs = int32(operands, padding + 4);
        if (pairs < 0 || pairs > MAX_ANALYSIS_STATES - pending.size()) {
            return false;
        }
        int operandOffset = padding + 8;
        for (int index = 0; index < pairs; index++) {
            if (!enqueueOffset(
                pending,
                instructions,
                state,
                instruction.offset() + int32(operands, operandOffset + 4),
                0
            )) {
                return false;
            }
            operandOffset += 8;
        }
        return true;
    }

    private static boolean conditionalBranch(final int opcode) {
        return opcode >= 153 && opcode <= 166 || opcode == 198 || opcode == 199;
    }

    private static int branchTarget(final Instruction instruction) {
        final byte[] operands = instruction.operands();
        final int relative = (short) ((unsigned(operands[0]) << 8) | unsigned(operands[1]));
        return instruction.offset() + relative;
    }

    private static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor - offset - 1;
    }

    private static int int32(final byte[] operands, final int offset) {
        return (unsigned(operands[offset]) << 24)
            | (unsigned(operands[offset + 1]) << 16)
            | (unsigned(operands[offset + 2]) << 8)
            | unsigned(operands[offset + 3]);
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

    private record AnalysisState(int instructionIndex, List<Integer> aliases, int throwableStackCopies) {
    }

}
