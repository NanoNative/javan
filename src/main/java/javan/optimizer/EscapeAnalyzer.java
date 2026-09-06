package javan.optimizer;

import javan.ir.IrClass;
import javan.ir.IrExpression;
import javan.ir.IrField;
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
    private static final int MAX_STACK_ALLOCATION_BYTES = 4_096;
    private static final int CONSERVATIVE_HEADER_BYTES = 32;
    private static final int NO_ESCAPE_RANK = 0;
    private static final int ARGUMENT_ESCAPE_RANK = 1;
    private static final int GLOBAL_ESCAPE_RANK = 2;
    private static final int[] NO_SUCCESSORS = new int[0];

    /**
     * Classifies all managed allocations in a lowered program.
     *
     * @param program lowered program
     * @return allocation sites in deterministic function and instruction order
     */
    public Analysis analyze(final IrProgram program) {
        final Map<String, int[]> parameterEscapes = parameterEscapes(program);
        final List<AllocationSite> sites = new ArrayList<>();
        for (final IrFunction function : program.functions()) {
            sites.addAll(analyze(function, parameterEscapes));
        }
        return new Analysis(sites);
    }

    private static Map<String, int[]> parameterEscapes(final IrProgram program) {
        final List<IrFunction> functions = program.functions();
        final Map<String, Integer> indexes = new HashMap<>();
        final Map<String, int[]> summaries = new HashMap<>();
        for (int index = 0; index < functions.size(); index++) {
            final IrFunction function = functions.get(index);
            indexes.put(function.symbol(), index);
            summaries.put(function.symbol(), emptyParameterEscapes(function));
        }
        final List<List<Integer>> callers = lists(functions.size());
        for (int caller = 0; caller < functions.size(); caller++) {
            for (final IrInstruction instruction : functions.get(caller).instructions()) {
                if (instruction.expression().isPresent()) {
                    collectCallers(instruction.expression().orElseThrow(), caller, indexes, callers);
                }
            }
        }
        final List<Integer> work = new ArrayList<>();
        final boolean[] queued = new boolean[functions.size()];
        for (int index = 0; index < functions.size(); index++) {
            work.add(index);
            queued[index] = true;
        }
        int cursor = 0;
        while (cursor < work.size()) {
            final int index = work.get(cursor);
            cursor++;
            queued[index] = false;
            final IrFunction function = functions.get(index);
            final int[] previous = summaries.get(function.symbol());
            final int[] next = merge(previous, summarize(function, summaries));
            if (!sameEscapes(next, previous)) {
                summaries.put(function.symbol(), next);
                for (final int caller : callers.get(index)) {
                    if (!queued[caller]) {
                        work.add(caller);
                        queued[caller] = true;
                    }
                }
            }
        }
        return Map.copyOf(summaries);
    }

    private static List<List<Integer>> lists(final int size) {
        final List<List<Integer>> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    private static void collectCallers(
        final IrExpression expression,
        final int caller,
        final Map<String, Integer> indexes,
        final List<List<Integer>> callers
    ) {
        if (expression.kind() == IrExpression.Kind.CALL) {
            final Integer callee = indexes.get(expression.value());
            if (callee != null && !callers.get(callee).contains(caller)) {
                callers.get(callee).add(caller);
            }
        }
        for (final IrExpression argument : expression.arguments()) {
            collectCallers(argument, caller, indexes, callers);
        }
    }

    private static int[] emptyParameterEscapes(final IrFunction function) {
        return new int[function.parameters().size()];
    }

    private static int[] merge(final int[] previous, final int[] next) {
        final int[] result = new int[previous.length];
        for (int index = 0; index < previous.length; index++) {
            result[index] = Math.max(previous[index], next[index]);
        }
        return result;
    }

    private static boolean sameEscapes(final int[] first, final int[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int index = 0; index < first.length; index++) {
            if (first[index] != second[index]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Selects the bounded allocation sites whose lifetime and layout are safe for function stack storage.
     *
     * @param program lowered program used by {@code analysis}
     * @param analysis conservative escape classifications for {@code program}
     * @param release whether release optimizations are enabled
     * @return immutable stack-allocation plan; empty for debug builds or unsupported shapes
     */
    public StackAllocationPlan planStackAllocations(
        final IrProgram program,
        final Analysis analysis,
        final boolean release
    ) {
        if (!release || analysis.sites().isEmpty()) {
            return new StackAllocationPlan(List.of());
        }
        final List<StackAllocationSite> result = new ArrayList<>();
        for (final IrFunction function : program.functions()) {
            int remainingStackBytes = MAX_STACK_ALLOCATION_BYTES;
            int[][] controlFlow = null;
            int[] repeatMarkers = null;
            int[] repeatWork = null;
            for (int index = 0; index < function.instructions().size(); index++) {
                final IrInstruction instruction = function.instructions().get(index);
                if (instruction.op() != IrInstruction.Op.ASSIGN_OBJECT || instruction.expression().isEmpty()) {
                    continue;
                }
                final IrExpression expression = instruction.expression().orElseThrow();
                final StackShape shape = stackShape(program, expression);
                if (shape == null || shape.bytes() > remainingStackBytes) {
                    continue;
                }
                final AllocationSite site = siteAt(analysis, function, index, expression.kind());
                if (site == null || site.escape() != Escape.NO_ESCAPE) {
                    continue;
                }
                if (controlFlow == null) {
                    controlFlow = controlFlow(function.instructions());
                    repeatMarkers = new int[function.instructions().size()];
                    repeatWork = new int[function.instructions().size()];
                }
                if (!repeats(index, controlFlow, repeatMarkers, repeatWork)) {
                    result.add(new StackAllocationSite(
                        function.owner(), function.name(), function.descriptor(), index,
                        expression.kind(), shape.length()
                    ));
                    remainingStackBytes -= (int) shape.bytes();
                }
            }
        }
        return new StackAllocationPlan(result);
    }

    private static boolean repeats(
        final int allocationIndex,
        final int[][] controlFlow,
        final int[] markers,
        final int[] work
    ) {
        final int marker = allocationIndex + 1;
        int cursor = 0;
        int workSize = 0;
        for (final int successor : controlFlow[allocationIndex]) {
            if (successor == allocationIndex) {
                return true;
            }
            if (markers[successor] != marker) {
                markers[successor] = marker;
                work[workSize] = successor;
                workSize++;
            }
        }
        while (cursor < workSize) {
            final int index = work[cursor];
            cursor++;
            for (final int successor : controlFlow[index]) {
                if (successor == allocationIndex) {
                    return true;
                }
                if (markers[successor] != marker) {
                    markers[successor] = marker;
                    work[workSize] = successor;
                    workSize++;
                }
            }
        }
        return false;
    }

    private static boolean primitiveArrayAllocation(final IrExpression expression) {
        return switch (expression.kind()) {
            case INT_ARRAY_ALLOCATION, LONG_ARRAY_ALLOCATION, FLOAT_ARRAY_ALLOCATION,
                 DOUBLE_ARRAY_ALLOCATION, BYTE_ARRAY_ALLOCATION, BOOLEAN_ARRAY_ALLOCATION,
                 SHORT_ARRAY_ALLOCATION, CHAR_ARRAY_ALLOCATION -> true;
            default -> false;
        };
    }

    private static StackShape stackShape(final IrProgram program, final IrExpression expression) {
        if (expression.kind() == IrExpression.Kind.OBJECT_ALLOCATION) {
            for (final IrClass classInfo : program.classes()) {
                if (classInfo.jvmName().equals(expression.value())) {
                    return new StackShape(stackObjectBytes(classInfo), 0);
                }
            }
            return null;
        }
        if (!primitiveArrayAllocation(expression) || expression.arguments().isEmpty()) {
            return null;
        }
        final IrExpression lengthExpression = expression.arguments().getFirst();
        if (lengthExpression.kind() != IrExpression.Kind.INT_LITERAL) {
            return null;
        }
        final int length = Integer.parseInt(lengthExpression.value());
        if (length < 0) {
            return null;
        }
        return new StackShape(stackArrayBytes(expression.kind(), length), length);
    }

    private static long stackObjectBytes(final IrClass classInfo) {
        long bytes = CONSERVATIVE_HEADER_BYTES;
        for (final IrField field : classInfo.fields()) {
            bytes += 8;
        }
        return bytes;
    }

    private static long stackArrayBytes(final IrExpression.Kind kind, final int length) {
        final int elementBytes = switch (kind) {
            case LONG_ARRAY_ALLOCATION, DOUBLE_ARRAY_ALLOCATION -> 8;
            case INT_ARRAY_ALLOCATION, FLOAT_ARRAY_ALLOCATION -> 4;
            case SHORT_ARRAY_ALLOCATION, CHAR_ARRAY_ALLOCATION -> 2;
            case BYTE_ARRAY_ALLOCATION, BOOLEAN_ARRAY_ALLOCATION -> 1;
            default -> MAX_STACK_ALLOCATION_BYTES;
        };
        return (long) CONSERVATIVE_HEADER_BYTES + ((long) length * elementBytes);
    }

    private static AllocationSite siteAt(
        final Analysis analysis,
        final IrFunction function,
        final int instructionIndex,
        final IrExpression.Kind kind
    ) {
        for (final AllocationSite site : analysis.sites()) {
            if (site.owner().equals(function.owner())
                && site.method().equals(function.name())
                && site.descriptor().equals(function.descriptor())
                && site.instructionIndex() == instructionIndex
                && site.kind() == kind) {
                return site;
            }
        }
        return null;
    }

    private static int[] summarize(
        final IrFunction function,
        final Map<String, int[]> parameterEscapes
    ) {
        final Map<String, Integer> parameterIds = new HashMap<>();
        for (final IrParameter parameter : function.parameters()) {
            if (parameter.type() == IrType.OBJECT) {
                parameterIds.put(parameter.name(), parameterIds.size());
            }
        }
        if (parameterIds.isEmpty()) {
            return emptyParameterEscapes(function);
        }
        final List<List<ExpressionId>> ids = new ArrayList<>();
        for (int index = 0; index < function.instructions().size(); index++) {
            ids.add(List.of());
        }
        final int[] escapes = new int[parameterIds.size()];
        final Map<String, Integer> locals = objectLocals(function);
        final long words = Math.max(1, (escapes.length + Long.SIZE - 1) / Long.SIZE);
        final long stateCells = (long) function.instructions().size() * Math.max(1, locals.size()) * words;
        if (stateCells > MAX_FUNCTION_STATE_CELLS || !flow(
            function.instructions(), locals, ids, escapes, parameterEscapes, parameterIds
        )) {
            fill(escapes, GLOBAL_ESCAPE_RANK);
        }
        final int[] result = new int[function.parameters().size()];
        for (int index = 0; index < function.parameters().size(); index++) {
            final IrParameter parameter = function.parameters().get(index);
            final Integer id = parameterIds.get(parameter.name());
            result[index] = id == null ? NO_ESCAPE_RANK : escapes[id];
        }
        return result;
    }

    private static List<AllocationSite> analyze(
        final IrFunction function,
        final Map<String, int[]> parameterEscapes
    ) {
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
        final int[] escapes = new int[sites.size()];
        if (stateCells > MAX_FUNCTION_STATE_CELLS || !flow(
            instructions, locals, ids, escapes, parameterEscapes, Map.of()
        )) {
            fill(escapes, GLOBAL_ESCAPE_RANK);
        }
        final List<AllocationSite> classified = new ArrayList<>();
        for (int index = 0; index < sites.size(); index++) {
            final AllocationSite site = sites.get(index);
            Escape classification = Escape.GLOBAL_ESCAPE;
            if (escapes[index] == NO_ESCAPE_RANK) {
                classification = Escape.NO_ESCAPE;
            } else if (escapes[index] == ARGUMENT_ESCAPE_RANK) {
                classification = Escape.ARGUMENT_ESCAPE;
            }
            classified.add(new AllocationSite(
                site.owner(), site.method(), site.descriptor(), site.instructionIndex(), site.bytecodeOffset(),
                site.kind(), classification
            ));
        }
        return List.copyOf(classified);
    }

    private static void fill(final int[] escapes, final int value) {
        for (int index = 0; index < escapes.length; index++) {
            escapes[index] = value;
        }
    }

    private static boolean flow(
        final List<IrInstruction> instructions,
        final Map<String, Integer> locals,
        final List<List<ExpressionId>> ids,
        final int[] escapes,
        final Map<String, int[]> parameterEscapes,
        final Map<String, Integer> initialValues
    ) {
        if (instructions.isEmpty()) {
            return true;
        }
        final int[][] controlFlow = controlFlow(instructions);
        final State[] incoming = new State[instructions.size()];
        final int maxVisits = instructions.size() * MAX_VISITS_PER_INSTRUCTION;
        final int[] work = new int[maxVisits];
        incoming[0] = State.seeded(locals, escapes.length, initialValues);
        work[0] = 0;
        int cursor = 0;
        int workSize = 1;
        while (cursor < workSize) {
            final int index = work[cursor++];
            final State outgoing = transfer(
                incoming[index], instructions.get(index), locals, ids.get(index), escapes, parameterEscapes
            );
            for (final int successor : controlFlow[index]) {
                State merged = outgoing;
                if (incoming[successor] != null) {
                    merged = incoming[successor].merge(outgoing);
                }
                if (!merged.same(incoming[successor])) {
                    incoming[successor] = merged;
                    if (workSize >= maxVisits) {
                        return false;
                    }
                    work[workSize] = successor;
                    workSize++;
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
        final int[] escapes,
        final Map<String, int[]> parameterEscapes
    ) {
        if (instruction.expression().isEmpty()) {
            return state;
        }
        final IrExpression expression = instruction.expression().orElseThrow();
        final State result = state.copy();
        switch (instruction.op()) {
            case ASSIGN_OBJECT -> result.set(
                locals.get(instruction.value().orElseThrow()),
                value(expression, state, ids, escapes, parameterEscapes)
            );
            case RETURN_OBJECT, ASSIGN_STATIC_FIELD_OBJECT ->
                consume(expression, GLOBAL_ESCAPE_RANK, state, ids, escapes, parameterEscapes);
            case PRINTLN_OBJECT, PRINTLN_ERROR_OBJECT, PRINT_OBJECT, PRINT_ERROR_OBJECT,
                 PANIC, SET_PENDING, SET_PENDING_OBJECT, THROW_PENDING ->
                consume(expression, ARGUMENT_ESCAPE_RANK, state, ids, escapes, parameterEscapes);
            default -> value(expression, state, ids, escapes, parameterEscapes);
        }
        return result;
    }

    private static long[] value(
        final IrExpression expression,
        final State state,
        final List<ExpressionId> ids,
        final int[] escapes,
        final Map<String, int[]> parameterEscapes
    ) {
        if (isAllocation(expression)) {
            int argumentUse = NO_ESCAPE_RANK;
            if (expression.kind() == IrExpression.Kind.STRING_CONCAT) {
                argumentUse = ARGUMENT_ESCAPE_RANK;
            }
            for (final IrExpression argument : expression.arguments()) {
                consume(argument, argumentUse, state, ids, escapes, parameterEscapes);
            }
            final long[] result = empty(escapes.length);
            final int id = id(ids, expression);
            if (id >= 0) {
                set(result, id);
            } else if (!ids.isEmpty()) {
                throw new IllegalStateException("Allocation expression is not indexed");
            }
            return result;
        }
        if (expression.kind() == IrExpression.Kind.LOCAL) {
            return state.get(expression.value());
        }
        if (expression.kind() == IrExpression.Kind.CALL) {
            final int[] uses = parameterEscapes.get(expression.value());
            for (int index = 0; index < expression.arguments().size(); index++) {
                final int use = uses != null && index < uses.length
                    ? uses[index] : ARGUMENT_ESCAPE_RANK;
                consume(expression.arguments().get(index), use, state, ids, escapes, parameterEscapes);
            }
            return empty(escapes.length);
        }
        if (expression.kind() == IrExpression.Kind.FIELD_ASSIGN_OBJECT) {
            value(expression.arguments().getFirst(), state, ids, escapes, parameterEscapes);
            consume(
                expression.arguments().getLast(), GLOBAL_ESCAPE_RANK, state, ids, escapes, parameterEscapes
            );
            return empty(escapes.length);
        }
        if (expression.kind() == IrExpression.Kind.ARRAY_ASSIGN_OBJECT) {
            value(expression.arguments().get(0), state, ids, escapes, parameterEscapes);
            value(expression.arguments().get(1), state, ids, escapes, parameterEscapes);
            consume(expression.arguments().get(2), GLOBAL_ESCAPE_RANK, state, ids, escapes, parameterEscapes);
            return empty(escapes.length);
        }
        for (final IrExpression argument : expression.arguments()) {
            value(argument, state, ids, escapes, parameterEscapes);
        }
        return empty(escapes.length);
    }

    private static void consume(
        final IrExpression expression,
        final int escape,
        final State state,
        final List<ExpressionId> ids,
        final int[] escapes,
        final Map<String, int[]> parameterEscapes
    ) {
        final long[] values = value(expression, state, ids, escapes, parameterEscapes);
        for (int site = 0; site < escapes.length; site++) {
            if (contains(values, site) && escape > escapes[site]) {
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
        return -1;
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

    private static int[][] controlFlow(final List<IrInstruction> instructions) {
        final Map<String, Integer> labels = labels(instructions);
        final int[][] result = new int[instructions.size()][];
        for (int index = 0; index < instructions.size(); index++) {
            final IrInstruction instruction = instructions.get(index);
            if (instruction.op() == IrInstruction.Op.JUMP) {
                result[index] = new int[]{labels.get(instruction.value().orElseThrow()).intValue()};
            } else if (instruction.op() == IrInstruction.Op.BRANCH_IF) {
                final int target = labels.get(instruction.value().orElseThrow()).intValue();
                result[index] = index + 1 < instructions.size() ? new int[]{target, index + 1} : new int[]{target};
            } else if (terminal(instruction)) {
                result[index] = NO_SUCCESSORS;
            } else if (index + 1 < instructions.size()) {
                result[index] = new int[]{index + 1};
            } else {
                result[index] = NO_SUCCESSORS;
            }
        }
        return result;
    }

    private static boolean terminal(final IrInstruction instruction) {
        return switch (instruction.op()) {
            case RETURN_VOID, RETURN_INT, RETURN_LONG, RETURN_FLOAT, RETURN_DOUBLE, RETURN_OBJECT,
                 PANIC, THROW_PENDING, PROPAGATE_PENDING -> true;
            default -> false;
        };
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

    /**
     * A bounded allocation selected for function stack storage.
     *
     * @param owner JVM internal owner
     * @param method method name
     * @param descriptor JVM descriptor
     * @param instructionIndex lowered instruction index
     * @param kind allocation kind
     * @param length constant primitive-array length, or zero for an object
     */
    public record StackAllocationSite(
        String owner,
        String method,
        String descriptor,
        int instructionIndex,
        IrExpression.Kind kind,
        int length
    ) {
    }

    /**
     * Immutable release stack-allocation plan.
     *
     * @param sites selected sites in deterministic lowered order
     */
    public record StackAllocationPlan(List<StackAllocationSite> sites) {
        public StackAllocationPlan {
            sites = List.copyOf(sites);
        }
    }

    private record ExpressionId(IrExpression expression, int id) {
    }

    private record StackShape(long bytes, int length) {
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

        private static State seeded(
            final Map<String, Integer> indexes,
            final int sites,
            final Map<String, Integer> initialValues
        ) {
            final int words = (sites + Long.SIZE - 1) / Long.SIZE;
            final long[][] locals = new long[indexes.size()][];
            for (int index = 0; index < locals.length; index++) {
                locals[index] = new long[words];
            }
            final State result = new State(indexes, locals, words);
            for (final Map.Entry<String, Integer> entry : initialValues.entrySet()) {
                final long[] value = empty(sites);
                EscapeAnalyzer.set(value, entry.getValue());
                result.set(indexes.get(entry.getKey()), value);
            }
            return result;
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
