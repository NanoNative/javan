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
                    if (!duplicatesFreshAllocation(instructions, state.instructionIndex())) {
                        return Optional.empty();
                    }
                    if (!enqueueState(pending, nextState(state, state.instructionIndex() + 1, 0))) {
                        return Optional.empty();
                    }
                    continue;
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
     * Analyzes every normal control-flow path from a catch-all handler entry.
     *
     * @param code decoded method code
     * @param handler catch-all handler to inspect
     * @return supported rethrow, replacement, return, or cleanup-jump flow; empty when ambiguous
     */
    public static Optional<FinallyFlow> analyzeFinally(
        final CodeAttribute code,
        final CodeException handler
    ) {
        if (handler.catchType().isPresent()) {
            return Optional.empty();
        }
        final List<Instruction> instructions = code.instructions();
        final int handlerIndex = instructionIndex(instructions, handler.handlerPc());
        if (handlerIndex < 0 || astoreLocalIndex(instructions.get(handlerIndex)) < 0) {
            return Optional.empty();
        }
        final BytecodeControlFlow.Result controlFlow = BytecodeControlFlow.analyze(code);
        if (!controlFlow.structurallyValid()) {
            return Optional.empty();
        }
        final List<AnalysisState> states = new ArrayList<>();
        final List<List<Integer>> edges = new ArrayList<>();
        final Set<Integer> terminalStates = new HashSet<>();
        final Set<Integer> handlerOffsets = new HashSet<>();
        final Set<ReplacementThrow> replacements = new HashSet<>();
        final Set<Integer> replacementLocals = new HashSet<>();
        final Set<Integer> replacementValueOffsets = new HashSet<>();
        final java.util.Map<AnalysisState, Integer> indexes = new java.util.HashMap<>();
        addState(states, edges, indexes, new AnalysisState(
            handlerIndex + 1,
            List.of(astoreLocalIndex(instructions.get(handlerIndex))),
            0
        ));
        int pendingIndex = 0;
        while (pendingIndex < states.size()) {
            final int stateIndex = pendingIndex++;
            final AnalysisState state = states.get(stateIndex);
            if (!validInstructionIndex(instructions, state.instructionIndex())) {
                return Optional.empty();
            }
            handlerOffsets.add(Integer.valueOf(instructions.get(state.instructionIndex()).offset()));
            final Instruction instruction = instructions.get(state.instructionIndex());
            final int opcode = instruction.opcode();
            final int loadedLocal = aloadLocalIndex(instruction);
            if (loadedLocal >= 0) {
                if (!addEdge(states, edges, indexes, stateIndex, nextState(
                    state,
                    state.instructionIndex() + 1,
                    state.aliases().contains(loadedLocal) ? 1 : 0
                ))) {
                    return Optional.empty();
                }
                continue;
            }
            if (opcode == 192 || opcode == 168 || opcode == 169 || opcode == 201) {
                return Optional.empty();
            }
            if (opcode == 89) {
                if (state.throwableStackCopies() == 0
                    && !duplicatesFreshAllocation(instructions, state.instructionIndex())) {
                    return Optional.empty();
                }
                final int copies = state.throwableStackCopies() == 0
                    ? 0
                    : Math.min(MAX_TRACKED_STACK_COPIES, state.throwableStackCopies() + 1);
                if (!addEdge(states, edges, indexes, stateIndex, nextState(
                    state, state.instructionIndex() + 1, copies
                ))) {
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
                if (!addEdge(states, edges, indexes, stateIndex, new AnalysisState(
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
                    final Optional<ReplacementThrow> replacement = replacementThrowAt(
                        instructions,
                        handlerIndex,
                        state.instructionIndex()
                    );
                    if (replacement.isPresent()) {
                        replacements.add(replacement.orElseThrow());
                    } else {
                        final Optional<Integer> replacementLocal = directThrowLocal(
                            instructions,
                            state.instructionIndex(),
                            state.aliases()
                        );
                        if (replacementLocal.isEmpty()) {
                            if (!directReplacementValue(instructions, state.instructionIndex())) {
                                return Optional.empty();
                            }
                            replacementValueOffsets.add(Integer.valueOf(instruction.offset()));
                        } else {
                            replacementLocals.add(replacementLocal.orElseThrow());
                        }
                    }
                }
                terminalStates.add(Integer.valueOf(stateIndex));
                continue;
            }
            if (opcode >= 172 && opcode <= 177) {
                terminalStates.add(Integer.valueOf(stateIndex));
                continue;
            }
            final List<Integer> successors = controlFlow.graph().successors(state.instructionIndex());
            if (opcode == 167 || opcode == 200) {
                if (successors.size() != 1) {
                    return Optional.empty();
                }
                final int successor = successors.getFirst();
                if (state.throwableStackCopies() == 0
                    && normalCleanupContinuation(controlFlow, instructions, handler, handlerIndex, successor)) {
                    terminalStates.add(Integer.valueOf(stateIndex));
                    continue;
                }
                if (successor < handlerIndex
                    || !addEdge(states, edges, indexes, stateIndex, nextState(
                        state, successor, state.throwableStackCopies()
                    ))) {
                    return Optional.empty();
                }
                continue;
            }
            if (successors.isEmpty()) {
                return Optional.empty();
            }
            for (final int successor : successors) {
                if (successor < handlerIndex || !addEdge(states, edges, indexes, stateIndex, nextState(
                    state, successor, 0
                ))) {
                    return Optional.empty();
                }
            }
        }
        if (terminalStates.isEmpty() && !hasCycle(edges)) {
            return Optional.empty();
        }
        return Optional.of(new FinallyFlow(
            replacements,
            replacementLocals,
            replacementValueOffsets,
            handlerOffsets
        ));
    }

    private static boolean normalCleanupContinuation(
        final BytecodeControlFlow.Result controlFlow,
        final List<Instruction> instructions,
        final CodeException handler,
        final int handlerIndex,
        final int targetIndex
    ) {
        if (!validInstructionIndex(instructions, targetIndex)) {
            return false;
        }
        for (int index = 0; index < handlerIndex; index++) {
            final Instruction instruction = instructions.get(index);
            final boolean outsideProtectedRange = instruction.offset() < handler.startPc()
                || instruction.offset() >= handler.endPc();
            if ((instruction.opcode() != 167 && instruction.opcode() != 200)
                || !outsideProtectedRange
                || !controlFlow.graph().successors(index).contains(targetIndex)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * Finds javac's straight-line catch-all handler that discards the caught throwable and throws
     * a newly constructed application or platform exception instead.
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

    private static Optional<ReplacementThrow> replacementThrowAt(
        final List<Instruction> instructions,
        final int handlerIndex,
        final int throwIndex
    ) {
        final int constructorIndex = throwIndex - 1;
        if (constructorIndex <= handlerIndex) {
            return Optional.empty();
        }
        final Optional<String> throwableType = constructedThrowableType(instructions, constructorIndex);
        if (throwableType.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ReplacementThrow(throwableType.orElseThrow(), instructions.get(throwIndex).offset()));
    }

    private static Optional<String> constructedThrowableType(
        final List<Instruction> instructions,
        final int constructorIndex
    ) {
        if (!validInstructionIndex(instructions, constructorIndex)
            || instructions.get(constructorIndex).opcode() != 183
            || instructions.get(constructorIndex).methodRef().isEmpty()) {
            return Optional.empty();
        }
        final MethodRef constructor = instructions.get(constructorIndex).methodRef().orElseThrow();
        final int newIndex = "(Ljava/lang/String;)V".equals(constructor.descriptor())
            ? constructorIndex - 3
            : constructorIndex - 2;
        if (newIndex < 0 || instructions.get(newIndex).opcode() != 187
            || instructions.get(newIndex).className().isEmpty()
            || instructions.get(newIndex + 1).opcode() != 89
            || !instructions.get(newIndex).className().orElseThrow().equals(constructor.owner())
            || !"<init>".equals(constructor.name())
            || !("()V".equals(constructor.descriptor())
                || "(Ljava/lang/String;)V".equals(constructor.descriptor()))) {
            return Optional.empty();
        }
        if (newIndex + 2 != constructorIndex
            && !(instructions.get(newIndex + 2).opcode() == 1
                || instructions.get(newIndex + 2).opcode() == 18
                || instructions.get(newIndex + 2).opcode() == 19)) {
            return Optional.empty();
        }
        return Optional.of(instructions.get(newIndex).className().orElseThrow());
    }

    private static Optional<Integer> directThrowLocal(
        final List<Instruction> instructions,
        final int throwIndex,
        final List<Integer> caughtAliases
    ) {
        if (throwIndex == 0) {
            return Optional.empty();
        }
        final int local = aloadLocalIndex(instructions.get(throwIndex - 1));
        return local >= 0 && !caughtAliases.contains(Integer.valueOf(local))
            ? Optional.of(Integer.valueOf(local))
            : Optional.empty();
    }

    private static boolean directReplacementValue(final List<Instruction> instructions, final int throwIndex) {
        if (throwIndex == 0) {
            return false;
        }
        final Instruction source = instructions.get(throwIndex - 1);
        return (source.opcode() == 178 || source.opcode() == 180) && source.fieldRef().isPresent()
            || (source.opcode() >= 182 && source.opcode() <= 185) && source.methodRef().isPresent();
    }

    private static boolean duplicatesFreshAllocation(final List<Instruction> instructions, final int index) {
        return index > 0 && instructions.get(index - 1).opcode() == 187;
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

    private static boolean addEdge(
        final List<AnalysisState> states,
        final List<List<Integer>> edges,
        final java.util.Map<AnalysisState, Integer> indexes,
        final int source,
        final AnalysisState target
    ) {
        final int targetIndex = addState(states, edges, indexes, target);
        if (targetIndex < 0) {
            return false;
        }
        edges.get(source).add(Integer.valueOf(targetIndex));
        return true;
    }

    private static int addState(
        final List<AnalysisState> states,
        final List<List<Integer>> edges,
        final java.util.Map<AnalysisState, Integer> indexes,
        final AnalysisState state
    ) {
        if (state.aliases().size() > MAX_THROWABLE_ALIASES
            || state.throwableStackCopies() > MAX_TRACKED_STACK_COPIES) {
            return -1;
        }
        final Integer existing = indexes.get(state);
        if (existing != null) {
            return existing.intValue();
        }
        if (states.size() >= MAX_ANALYSIS_STATES) {
            return -1;
        }
        final int index = states.size();
        states.add(state);
        edges.add(new ArrayList<>());
        indexes.put(state, Integer.valueOf(index));
        return index;
    }

    private static boolean hasCycle(final List<List<Integer>> edges) {
        final int[] indexes = new int[edges.size()];
        final int[] lowLinks = new int[edges.size()];
        for (int node = 0; node < indexes.length; node++) {
            indexes[node] = -1;
        }
        final List<Integer> stack = new ArrayList<>();
        final Set<Integer> onStack = new HashSet<>();
        final int[] nextIndex = {0};
        for (int node = 0; node < edges.size(); node++) {
            if (indexes[node] < 0 && hasCycle(node, edges, indexes, lowLinks, stack, onStack, nextIndex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCycle(
        final int node,
        final List<List<Integer>> edges,
        final int[] indexes,
        final int[] lowLinks,
        final List<Integer> stack,
        final Set<Integer> onStack,
        final int[] nextIndex
    ) {
        indexes[node] = nextIndex[0];
        lowLinks[node] = nextIndex[0]++;
        stack.add(Integer.valueOf(node));
        onStack.add(Integer.valueOf(node));
        for (final int successor : edges.get(node)) {
            if (indexes[successor] < 0) {
                if (hasCycle(successor, edges, indexes, lowLinks, stack, onStack, nextIndex)) {
                    return true;
                }
                lowLinks[node] = Math.min(lowLinks[node], lowLinks[successor]);
            } else if (onStack.contains(Integer.valueOf(successor))) {
                lowLinks[node] = Math.min(lowLinks[node], indexes[successor]);
            }
        }
        if (lowLinks[node] != indexes[node]) {
            return false;
        }
        int members = 0;
        int member;
        do {
            member = stack.removeLast().intValue();
            onStack.remove(Integer.valueOf(member));
            members++;
        } while (member != node);
        return members > 1 || edges.get(node).contains(Integer.valueOf(node));
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

    /** A validated catch-all finally flow with exact constructed or declared-local replacements. */
    public record FinallyFlow(
        Set<ReplacementThrow> replacements,
        Set<Integer> replacementLocals,
        Set<Integer> replacementValueOffsets,
        Set<Integer> handlerOffsets
    ) {
        public FinallyFlow {
            replacements = Set.copyOf(replacements == null ? Set.of() : replacements);
            replacementLocals = Set.copyOf(replacementLocals == null ? Set.of() : replacementLocals);
            replacementValueOffsets = Set.copyOf(replacementValueOffsets == null ? Set.of() : replacementValueOffsets);
            handlerOffsets = Set.copyOf(handlerOffsets == null ? Set.of() : handlerOffsets);
        }
    }

    private record AnalysisState(int instructionIndex, List<Integer> aliases, int throwableStackCopies) {
    }

}
