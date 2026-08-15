package javan.optimizer;

import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrParameter;
import javan.ir.IrProgram;
import javan.ir.IrType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies lowered allocation sites without changing allocation behavior.
 */
public final class EscapeAnalyzer {
    private static final long MAX_FUNCTION_STATE_CELLS = 20_000;
    private static final int MAX_VISITS_PER_INSTRUCTION = 8;

    /**
     * Classifies all managed allocations in a lowered program.
     *
     * @param program lowered program
     * @return allocation sites in deterministic function and instruction order
     */
    public Analysis analyze(final IrProgram program) {
        final List<AllocationSite> sites = new ArrayList<>();
        for (final IrFunction function : program.functions()) {
            sites.addAll(analyze(function));
        }
        return new Analysis(sites);
    }

    private static List<AllocationSite> analyze(final IrFunction function) {
        final List<IrInstruction> instructions = function.instructions();
        final List<List<ExpressionId>> ids = new ArrayList<>();
        final List<AllocationSite> sites = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            final List<ExpressionId> instructionIds = new ArrayList<>();
            ids.add(instructionIds);
            if (instructions.get(index).expression().isPresent()) {
                enumerate(function, instructions.get(index), index, instructions.get(index).expression().orElseThrow(),
                    instructionIds, sites);
            }
        }
        if (sites.isEmpty()) {
            return List.of();
        }

        final Map<String, Integer> locals = objectLocals(function);
        final long words = Math.max(1, (sites.size() + Long.SIZE - 1) / Long.SIZE);
        final long stateCells = (long) instructions.size() * Math.max(1, locals.size()) * words;
        final Escape[] escapes = new Escape[sites.size()];
        fill(escapes, Escape.NO_ESCAPE);
        if (stateCells > MAX_FUNCTION_STATE_CELLS || !flow(instructions, locals, ids, escapes)) {
            fill(escapes, Escape.GLOBAL_ESCAPE);
        }
        final List<AllocationSite> classified = new ArrayList<>();
        for (int index = 0; index < sites.size(); index++) {
            final AllocationSite site = sites.get(index);
            classified.add(new AllocationSite(
                site.owner(), site.method(), site.descriptor(), site.instructionIndex(), site.bytecodeOffset(),
                site.kind(), escapes[index]
            ));
        }
        return List.copyOf(classified);
    }

    private static void fill(final Escape[] escapes, final Escape value) {
        for (int index = 0; index < escapes.length; index++) {
            escapes[index] = value;
        }
    }

    private static boolean flow(
        final List<IrInstruction> instructions,
        final Map<String, Integer> locals,
        final List<List<ExpressionId>> ids,
        final Escape[] escapes
    ) {
        if (instructions.isEmpty()) {
            return true;
        }
        final Map<String, Integer> labels = labels(instructions);
        final State[] incoming = new State[instructions.size()];
        final int maxVisits = instructions.size() * MAX_VISITS_PER_INSTRUCTION;
        final int[] work = new int[maxVisits];
        incoming[0] = State.empty(locals, escapes.length);
        work[0] = 0;
        int cursor = 0;
        int workSize = 1;
        while (cursor < workSize) {
            final int index = work[cursor++];
            final State outgoing = transfer(
                incoming[index], instructions.get(index), locals, ids.get(index), escapes
            );
            for (final int successor : successors(index, instructions, labels)) {
                State merged = outgoing;
                if (incoming[successor] != null) {
                    merged = incoming[successor].merge(outgoing);
                }
                if (!merged.same(incoming[successor])) {
                    incoming[successor] = merged;
                    if (workSize >= maxVisits) {
                        return false;
                    }
                    work[workSize++] = successor;
                }
            }
        }
        return true;
    }

    private static State transfer(
        final State state,
        final IrInstruction instruction,
        final Map<String, Integer> locals,
        final List<ExpressionId> ids,
        final Escape[] escapes
    ) {
        if (instruction.expression().isEmpty()) {
            return state;
        }
        final IrExpression expression = instruction.expression().orElseThrow();
        final State result = state.copy();
        switch (instruction.op()) {
            case ASSIGN_OBJECT -> result.set(
                locals.get(instruction.value().orElseThrow()),
                value(expression, state, ids, escapes)
            );
            case RETURN_OBJECT, ASSIGN_STATIC_FIELD_OBJECT ->
                consume(expression, Escape.GLOBAL_ESCAPE, state, ids, escapes);
            case PRINTLN_OBJECT, PRINTLN_ERROR_OBJECT, PRINT_OBJECT, PRINT_ERROR_OBJECT,
                 PANIC, SET_PENDING, THROW_PENDING ->
                consume(expression, Escape.ARGUMENT_ESCAPE, state, ids, escapes);
            default -> value(expression, state, ids, escapes);
        }
        return result;
    }

    private static long[] value(
        final IrExpression expression,
        final State state,
        final List<ExpressionId> ids,
        final Escape[] escapes
    ) {
        if (isAllocation(expression)) {
            Escape argumentUse = Escape.NO_ESCAPE;
            if (expression.kind() == IrExpression.Kind.STRING_CONCAT) {
                argumentUse = Escape.ARGUMENT_ESCAPE;
            }
            for (final IrExpression argument : expression.arguments()) {
                consume(argument, argumentUse, state, ids, escapes);
            }
            final long[] result = empty(escapes.length);
            set(result, id(ids, expression));
            return result;
        }
        if (expression.kind() == IrExpression.Kind.LOCAL) {
            return state.get(expression.value());
        }
        if (expression.kind() == IrExpression.Kind.CALL) {
            for (final IrExpression argument : expression.arguments()) {
                consume(argument, Escape.ARGUMENT_ESCAPE, state, ids, escapes);
            }
            return empty(escapes.length);
        }
        if (expression.kind() == IrExpression.Kind.FIELD_ASSIGN_OBJECT) {
            value(expression.arguments().getFirst(), state, ids, escapes);
            consume(expression.arguments().getLast(), Escape.GLOBAL_ESCAPE, state, ids, escapes);
            return empty(escapes.length);
        }
        if (expression.kind() == IrExpression.Kind.ARRAY_ASSIGN_OBJECT) {
            value(expression.arguments().get(0), state, ids, escapes);
            value(expression.arguments().get(1), state, ids, escapes);
            consume(expression.arguments().get(2), Escape.GLOBAL_ESCAPE, state, ids, escapes);
            return empty(escapes.length);
        }
        for (final IrExpression argument : expression.arguments()) {
            value(argument, state, ids, escapes);
        }
        return empty(escapes.length);
    }

    private static void consume(
        final IrExpression expression,
        final Escape escape,
        final State state,
        final List<ExpressionId> ids,
        final Escape[] escapes
    ) {
        final long[] values = value(expression, state, ids, escapes);
        for (int site = 0; site < escapes.length; site++) {
            if (contains(values, site) && escape.ordinal() > escapes[site].ordinal()) {
                escapes[site] = escape;
            }
        }
    }

    private static void enumerate(
        final IrFunction function,
        final IrInstruction instruction,
        final int instructionIndex,
        final IrExpression expression,
        final List<ExpressionId> ids,
        final List<AllocationSite> sites
    ) {
        if (isAllocation(expression)) {
            int bytecodeOffset = -1;
            if (instruction.sourceLocation().isPresent()) {
                bytecodeOffset = instruction.sourceLocation().orElseThrow().bytecodeOffset();
            }
            ids.add(new ExpressionId(expression, sites.size()));
            sites.add(new AllocationSite(
                function.owner(), function.name(), function.descriptor(), instructionIndex,
                bytecodeOffset, expression.kind(), Escape.NO_ESCAPE
            ));
        }
        for (final IrExpression argument : expression.arguments()) {
            enumerate(function, instruction, instructionIndex, argument, ids, sites);
        }
    }

    private static int id(final List<ExpressionId> ids, final IrExpression expression) {
        for (final ExpressionId id : ids) {
            if (id.expression() == expression) {
                return id.id();
            }
        }
        throw new IllegalStateException("Allocation expression is not indexed");
    }

    private static long[] empty(final int sites) {
        return new long[(sites + Long.SIZE - 1) / Long.SIZE];
    }

    private static void set(final long[] values, final int site) {
        values[site / Long.SIZE] |= 1L << (site % Long.SIZE);
    }

    private static boolean contains(final long[] values, final int site) {
        return (values[site / Long.SIZE] & (1L << (site % Long.SIZE))) != 0;
    }

    private static boolean isAllocation(final IrExpression expression) {
        return switch (expression.kind()) {
            case STRING_CONCAT, OBJECT_ALLOCATION, OBJECT_ARRAY_ALLOCATION, INT_ARRAY_ALLOCATION,
                 LONG_ARRAY_ALLOCATION, FLOAT_ARRAY_ALLOCATION, DOUBLE_ARRAY_ALLOCATION,
                 BYTE_ARRAY_ALLOCATION, BOOLEAN_ARRAY_ALLOCATION, SHORT_ARRAY_ALLOCATION,
                 CHAR_ARRAY_ALLOCATION -> true;
            default -> false;
        };
    }

    private static Map<String, Integer> objectLocals(final IrFunction function) {
        final Map<String, Integer> result = new HashMap<>();
        for (final IrParameter parameter : function.parameters()) {
            if (parameter.type() == IrType.OBJECT) {
                result.put(parameter.name(), result.size());
            }
        }
        for (final IrLocal local : function.locals()) {
            if (local.type() == IrType.OBJECT) {
                result.putIfAbsent(local.name(), result.size());
            }
        }
        for (final IrInstruction instruction : function.instructions()) {
            if (instruction.op() == IrInstruction.Op.ASSIGN_OBJECT) {
                result.putIfAbsent(instruction.value().orElseThrow(), result.size());
            }
            if (instruction.expression().isPresent()) {
                addObjectLocals(instruction.expression().orElseThrow(), result);
            }
        }
        return result;
    }

    private static void addObjectLocals(final IrExpression expression, final Map<String, Integer> locals) {
        if (expression.kind() == IrExpression.Kind.LOCAL && expression.type() == IrType.OBJECT) {
            locals.putIfAbsent(expression.value(), locals.size());
        }
        for (final IrExpression argument : expression.arguments()) {
            addObjectLocals(argument, locals);
        }
    }

    private static Map<String, Integer> labels(final List<IrInstruction> instructions) {
        final Map<String, Integer> labels = new HashMap<>();
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).op() == IrInstruction.Op.LABEL) {
                labels.put(instructions.get(index).value().orElseThrow(), index);
            }
        }
        return labels;
    }

    private static List<Integer> successors(
        final int index,
        final List<IrInstruction> instructions,
        final Map<String, Integer> labels
    ) {
        final IrInstruction instruction = instructions.get(index);
        if (instruction.op() == IrInstruction.Op.JUMP) {
            return List.of(labels.get(instruction.value().orElseThrow()));
        }
        if (instruction.op() == IrInstruction.Op.BRANCH_IF) {
            final int target = labels.get(instruction.value().orElseThrow());
            if (index + 1 < instructions.size()) {
                return List.of(target, index + 1);
            }
            return List.of(target);
        }
        if (instruction.op() == IrInstruction.Op.RETURN_VOID
            || instruction.op() == IrInstruction.Op.RETURN_INT
            || instruction.op() == IrInstruction.Op.RETURN_LONG
            || instruction.op() == IrInstruction.Op.RETURN_FLOAT
            || instruction.op() == IrInstruction.Op.RETURN_DOUBLE
            || instruction.op() == IrInstruction.Op.RETURN_OBJECT
            || instruction.op() == IrInstruction.Op.PANIC
            || instruction.op() == IrInstruction.Op.THROW_PENDING
            || instruction.op() == IrInstruction.Op.PROPAGATE_PENDING) {
            return List.of();
        }
        if (index + 1 < instructions.size()) {
            return List.of(index + 1);
        }
        return List.of();
    }

    /**
     * Increasing escape scope for a managed allocation.
     */
    public enum Escape {
        NO_ESCAPE,
        ARGUMENT_ESCAPE,
        GLOBAL_ESCAPE
    }

    /**
     * Allocation identity and its conservative escape scope.
     *
     * @param owner JVM internal owner
     * @param method method name
     * @param descriptor JVM descriptor
     * @param instructionIndex lowered instruction index
     * @param bytecodeOffset bytecode offset, or {@code -1} when absent
     * @param kind allocation expression kind
     * @param escape conservative escape scope
     */
    public record AllocationSite(
        String owner,
        String method,
        String descriptor,
        int instructionIndex,
        int bytecodeOffset,
        IrExpression.Kind kind,
        Escape escape
    ) {
    }

    /**
     * Immutable allocation-site analysis.
     *
     * @param sites sites in deterministic lowered order
     */
    public record Analysis(List<AllocationSite> sites) {
        public Analysis {
            sites = List.copyOf(sites);
        }
    }

    private record ExpressionId(IrExpression expression, int id) {
    }

    private static final class State {
        private final Map<String, Integer> indexes;
        private final long[][] locals;
        private final int words;

        private State(final Map<String, Integer> indexes, final long[][] locals, final int words) {
            this.indexes = indexes;
            this.locals = locals;
            this.words = words;
        }

        private static State empty(final Map<String, Integer> indexes, final int sites) {
            final int words = (sites + Long.SIZE - 1) / Long.SIZE;
            final long[][] locals = new long[indexes.size()][];
            for (int index = 0; index < locals.length; index++) {
                locals[index] = new long[words];
            }
            return new State(indexes, locals, words);
        }

        private State copy() {
            final long[][] result = new long[locals.length][];
            for (int index = 0; index < locals.length; index++) {
                result[index] = copy(locals[index]);
            }
            return new State(indexes, result, words);
        }

        private long[] get(final String local) {
            final Integer index = indexes.get(local);
            if (index == null) {
                final long[] unknown = new long[words];
                for (int word = 0; word < words; word++) {
                    unknown[word] = -1L;
                }
                return unknown;
            }
            return copy(locals[index]);
        }

        private void set(final Integer local, final long[] value) {
            if (local != null) {
                locals[local] = copy(value);
            }
        }

        private State merge(final State other) {
            final State merged = copy();
            for (int index = 0; index < locals.length; index++) {
                for (int word = 0; word < locals[index].length; word++) {
                    merged.locals[index][word] |= other.locals[index][word];
                }
            }
            return merged;
        }

        private boolean same(final State other) {
            if (other == null || locals.length != other.locals.length) {
                return false;
            }
            for (int index = 0; index < locals.length; index++) {
                for (int word = 0; word < locals[index].length; word++) {
                    if (locals[index][word] != other.locals[index][word]) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static long[] copy(final long[] source) {
            final long[] result = new long[source.length];
            for (int index = 0; index < source.length; index++) {
                result[index] = source[index];
            }
            return result;
        }
    }
}
