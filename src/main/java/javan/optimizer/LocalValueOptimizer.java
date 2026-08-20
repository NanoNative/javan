package javan.optimizer;

import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.ir.IrSourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Propagates conservative local facts through lowered control flow and applies proven release rewrites.
 * Analysis tracks only values that influence a candidate and stops conservatively at fixed resource bounds.
 */
public final class LocalValueOptimizer {
    private static final String FIELD_SLOT_PREFIX = "@field:";
    private static final long MAX_PROGRAM_STATE_CELLS = 20_000;
    private static final int MAX_VISITS_PER_INSTRUCTION = 8;
    private static final Fact UNKNOWN = new Fact(Nullness.UNKNOWN, null, null, null, null);

    /**
     * Analyzes a lowered program and optionally applies proof-backed release rewrites.
     *
     * @param program lowered program
     * @param release whether proven rewrites may change the program
     * @param effects transitive method effects used to invalidate mutable facts
     * @return analyzed program, counters, facts, and deterministic proof records
     */
    public Result optimize(
        final IrProgram program,
        final boolean release,
        final MethodEffectAnalyzer.Analysis effects
    ) {
        final List<IrFunction> functions = new ArrayList<>();
        final List<Proof> proofs = new ArrayList<>();
        final MutableSummary summary = new MutableSummary();
        long removedNullChecks = 0;
        long deadBranches = 0;
        long skippedCandidates = 0;
        long remainingStateCells = MAX_PROGRAM_STATE_CELLS;
        for (final IrFunction function : program.functions()) {
            final String[] slots = trackedSlots(function);
            final boolean candidate = hasCandidate(function.instructions());
            final long cost = analysisCost(function, slots.length);
            final FunctionResult result;
            if (candidate && cost > remainingStateCells) {
                result = skipped(function);
            } else {
                result = analyze(function, release, slots, effects);
                if (candidate) {
                    remainingStateCells -= Math.min(cost, remainingStateCells);
                }
            }
            functions.add(result.function());
            proofs.addAll(result.proofs());
            summary.add(result.facts());
            removedNullChecks += result.removedNullChecks();
            deadBranches += result.deadBranches();
            skippedCandidates += result.skippedCandidates();
        }
        final IrProgram optimized = release ? copy(program, functions) : program;
        return new Result(
            optimized,
            new OptimizationReport(
                removedNullChecks,
                0,
                0,
                0,
                deadBranches,
                0,
                skippedCandidates + (release ? 0 : proofs.size())
            ),
            List.copyOf(proofs),
            summary.freeze()
        );
    }

    private static FunctionResult analyze(
        final IrFunction function,
        final boolean release,
        final String[] slots,
        final MethodEffectAnalyzer.Analysis effects
    ) {
        final List<IrInstruction> instructions = function.instructions();
        if (instructions.isEmpty() || !hasCandidate(instructions)) {
            return new FunctionResult(function, List.of(), FactSummary.empty(), 0, 0, 0);
        }
        final Map<String, Integer> labels = labels(instructions);
        final State[] incoming = new State[instructions.size()];
        final List<Integer> work = new ArrayList<>();
        int cursor = 0;
        long visits = 0;
        final long maxVisits = (long) instructions.size() * MAX_VISITS_PER_INSTRUCTION;
        incoming[0] = State.empty(slots);
        work.add(0);
        while (cursor < work.size()) {
            if (visits >= maxVisits) {
                return skipped(function);
            }
            visits++;
            final int index = work.get(cursor);
            cursor++;
            final IrInstruction instruction = instructions.get(index);
            final State outgoing = transfer(incoming[index], instruction, effects);
            if (instruction.op() == IrInstruction.Op.BRANCH_IF) {
                final Boolean decision = booleanValue(evaluate(instruction.expression().orElseThrow(), outgoing));
                final int target = labels.get(instruction.value().orElseThrow());
                if (decision == null || decision) {
                    enqueue(
                        incoming,
                        work,
                        target,
                        refine(outgoing, instruction.expression().orElseThrow(), true),
                        target <= index
                    );
                }
                if ((decision == null || !decision) && index + 1 < instructions.size()) {
                    enqueue(
                        incoming,
                        work,
                        index + 1,
                        refine(outgoing, instruction.expression().orElseThrow(), false),
                        false
                    );
                }
            } else {
                for (final int successor : successors(index, instructions, labels)) {
                    enqueue(incoming, work, successor, outgoing, successor <= index);
                }
            }
        }

        final List<Proof> proofs = new ArrayList<>();
        final List<IrInstruction> rewritten = new ArrayList<>();
        long removedNullChecks = 0;
        long deadBranches = 0;
        for (int index = 0; index < instructions.size(); index++) {
            final IrInstruction instruction = instructions.get(index);
            final State state = incoming[index];
            if (state == null) {
                continue;
            }
            if (isNullCheck(instruction) && evaluate(instruction.expression().orElseThrow().arguments().getFirst(), state).nullness() == Nullness.NON_NULL) {
                proofs.add(proof(function, instruction, "null-check", "receiver is proven non-null"));
                if (release) {
                    removedNullChecks++;
                    continue;
                }
            }
            if (instruction.op() == IrInstruction.Op.BRANCH_IF) {
                final Boolean decision = booleanValue(evaluate(instruction.expression().orElseThrow(), state));
                if (decision != null) {
                    proofs.add(proof(
                        function,
                        instruction,
                        "branch",
                        decision ? "condition is proven true" : "condition is proven false"
                    ));
                    if (release) {
                        deadBranches++;
                        if (decision) {
                            rewritten.add(new IrInstruction(
                                IrInstruction.Op.JUMP,
                                instruction.value(),
                                Optional.empty(),
                                instruction.sourceLocation()
                            ));
                        }
                        continue;
                    }
                }
            }
            rewritten.add(instruction);
        }
        final List<IrInstruction> output = release ? reachable(rewritten) : instructions;
        return new FunctionResult(
            copy(function, output),
            List.copyOf(proofs),
            summarize(incoming),
            removedNullChecks,
            deadBranches,
            0
        );
    }

    private static long analysisCost(final IrFunction function, final int slotCount) {
        return function.instructions().size() * Math.max(1L, slotCount);
    }

    private static FunctionResult skipped(final IrFunction function) {
        return new FunctionResult(
            function,
            List.of(),
            FactSummary.empty(),
            0,
            0,
            candidateCount(function.instructions())
        );
    }

    private static long candidateCount(final List<IrInstruction> instructions) {
        long count = 0;
        for (final IrInstruction instruction : instructions) {
            count += instruction.op() == IrInstruction.Op.BRANCH_IF || isNullCheck(instruction) ? 1 : 0;
        }
        return count;
    }

    private static boolean hasCandidate(final List<IrInstruction> instructions) {
        for (final IrInstruction instruction : instructions) {
            if (instruction.op() == IrInstruction.Op.BRANCH_IF || isNullCheck(instruction)) {
                return true;
            }
        }
        return false;
    }

    private static String[] trackedSlots(final IrFunction function) {
        final List<String> tracked = new ArrayList<>();
        for (final IrInstruction instruction : function.instructions()) {
            if (instruction.op() == IrInstruction.Op.BRANCH_IF || isNullCheck(instruction)) {
                collectSlots(instruction.expression().orElseThrow(), tracked);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (final IrInstruction instruction : function.instructions()) {
                if ((instruction.op() == IrInstruction.Op.ASSIGN_INT
                    || instruction.op() == IrInstruction.Op.ASSIGN_OBJECT)
                    && tracked.contains(instruction.value().orElseThrow())) {
                    changed |= collectSlots(instruction.expression().orElseThrow(), tracked);
                } else if (isFieldWrite(instruction)) {
                    final String field = fieldAssignmentKey(instruction);
                    if (field != null && tracked.contains(field)) {
                        changed |= collectSlots(instruction.expression().orElseThrow(), tracked);
                    }
                }
            }
        } while (changed);
        final String[] slots = new String[tracked.size()];
        for (int index = 0; index < tracked.size(); index++) {
            slots[index] = tracked.get(index);
        }
        return slots;
    }

    private static boolean collectSlots(final IrExpression expression, final List<String> slots) {
        boolean changed = false;
        if (expression.kind() == IrExpression.Kind.LOCAL && !slots.contains(expression.value())) {
            slots.add(expression.value());
            changed = true;
        }
        final String field = fieldKey(expression);
        if (field != null && !slots.contains(field)) {
            slots.add(field);
            changed = true;
        }
        for (final IrExpression argument : expression.arguments()) {
            changed |= collectSlots(argument, slots);
        }
        return changed;
    }

    private static String fieldAssignmentKey(final IrInstruction instruction) {
        final IrExpression assignment = instruction.expression().orElseThrow();
        if (assignment.arguments().isEmpty()) {
            return null;
        }
        final IrExpression receiver = assignment.arguments().getFirst();
        return receiver.kind() == IrExpression.Kind.LOCAL
            ? fieldKey(receiver.value(), instruction.value().orElseThrow())
            : null;
    }

    private static String fieldKey(final IrExpression expression) {
        if (!isFieldRead(expression) || expression.arguments().isEmpty()) {
            return null;
        }
        final IrExpression receiver = expression.arguments().getFirst();
        return receiver.kind() == IrExpression.Kind.LOCAL
            ? fieldKey(receiver.value(), expression.value())
            : null;
    }

    private static String fieldKey(final String receiver, final String field) {
        return FIELD_SLOT_PREFIX + receiver + '#' + field;
    }

    private static boolean isFieldRead(final IrExpression expression) {
        return switch (expression.kind()) {
            case FIELD_INT, FIELD_LONG, FIELD_FLOAT, FIELD_DOUBLE, FIELD_OBJECT -> true;
            default -> false;
        };
    }

    private static Map<String, Integer> labels(final List<IrInstruction> instructions) {
        final Map<String, Integer> labels = new HashMap<>();
        for (int index = 0; index < instructions.size(); index++) {
            final IrInstruction instruction = instructions.get(index);
            if (instruction.op() == IrInstruction.Op.LABEL) {
                labels.put(instruction.value().orElseThrow(), index);
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
            return index + 1 < instructions.size() ? List.of(target, index + 1) : List.of(target);
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
        return index + 1 < instructions.size() ? List.of(index + 1) : List.of();
    }

    private static State transfer(
        final State state,
        final IrInstruction instruction,
        final MethodEffectAnalyzer.Analysis effects
    ) {
        final State afterCalls = invalidateCalls(state, instruction, effects);
        if (isFieldWrite(instruction)) {
            final IrExpression assignment = instruction.expression().orElseThrow();
            final Fact value = evaluate(assignment.arguments().get(assignment.arguments().size() - 1), afterCalls);
            final String field = fieldAssignmentKey(instruction);
            final State cleared = afterCalls.clearFields();
            return field == null ? cleared : cleared.with(field, value);
        }
        if (isMemoryWrite(instruction)) {
            return afterCalls.clearFields();
        }
        return switch (instruction.op()) {
            case ASSIGN_INT -> afterCalls.with(
                instruction.value().orElseThrow(),
                evaluate(instruction.expression().orElseThrow(), afterCalls)
            );
            case ASSIGN_OBJECT -> afterCalls.clearFields().with(
                instruction.value().orElseThrow(),
                evaluate(instruction.expression().orElseThrow(), afterCalls)
            );
            case ASSIGN_LONG, ASSIGN_FLOAT, ASSIGN_DOUBLE ->
                afterCalls.with(instruction.value().orElseThrow(), UNKNOWN);
            default -> afterCalls;
        };
    }

    private static State invalidateCalls(
        final State state,
        final IrInstruction instruction,
        final MethodEffectAnalyzer.Analysis effects
    ) {
        if (instruction.op() == IrInstruction.Op.CALL_STATIC_VOID
            && instruction.expression().isEmpty()
            && !effects.preservesMemoryFacts(instruction.value().orElseThrow())) {
            return state.clearFields();
        }
        return instruction.expression().isPresent()
            && hasMutatingCall(instruction.expression().orElseThrow(), effects)
            ? state.clearFields()
            : state;
    }

    private static boolean hasMutatingCall(
        final IrExpression expression,
        final MethodEffectAnalyzer.Analysis effects
    ) {
        if (expression.kind() == IrExpression.Kind.CALL && !effects.preservesMemoryFacts(expression.value())) {
            return true;
        }
        for (final IrExpression argument : expression.arguments()) {
            if (hasMutatingCall(argument, effects)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFieldWrite(final IrInstruction instruction) {
        return switch (instruction.op()) {
            case ASSIGN_FIELD_INT, ASSIGN_FIELD_LONG, ASSIGN_FIELD_FLOAT, ASSIGN_FIELD_DOUBLE, ASSIGN_FIELD_OBJECT -> true;
            default -> false;
        };
    }

    private static boolean isMemoryWrite(final IrInstruction instruction) {
        return switch (instruction.op()) {
            case ASSIGN_STATIC_FIELD_INT, ASSIGN_STATIC_FIELD_LONG, ASSIGN_STATIC_FIELD_FLOAT,
                 ASSIGN_STATIC_FIELD_DOUBLE, ASSIGN_STATIC_FIELD_OBJECT,
                 ASSIGN_ARRAY_OBJECT, ASSIGN_ARRAY_INT, ASSIGN_ARRAY_LONG, ASSIGN_ARRAY_FLOAT,
                 ASSIGN_ARRAY_DOUBLE, ASSIGN_ARRAY_BYTE, ASSIGN_ARRAY_SHORT, ASSIGN_ARRAY_CHAR -> true;
            default -> false;
        };
    }

    private static void enqueue(
        final State[] incoming,
        final List<Integer> work,
        final int successor,
        final State state,
        final boolean widen
    ) {
        final State merged = incoming[successor] == null ? state : merge(incoming[successor], state, widen);
        if (!same(incoming[successor], merged)) {
            incoming[successor] = merged;
            work.add(successor);
        }
    }

    private static State refine(final State state, final IrExpression condition, final boolean truth) {
        if ((condition.kind() != IrExpression.Kind.INT_COMPARE && condition.kind() != IrExpression.Kind.OBJECT_COMPARE)
            || condition.arguments().size() != 2) {
            return state;
        }
        final IrExpression left = condition.arguments().get(0);
        final IrExpression right = condition.arguments().get(1);
        if (condition.kind() == IrExpression.Kind.OBJECT_COMPARE) {
            return refineNull(state, left, right, condition.value(), truth);
        }
        return refineInteger(state, left, right, condition.value(), truth);
    }

    private static State refineNull(
        final State state,
        final IrExpression left,
        final IrExpression right,
        final String operator,
        final boolean truth
    ) {
        final IrExpression local;
        if (!"==".equals(operator) && !"!=".equals(operator)) {
            return state;
        }
        if (left.kind() == IrExpression.Kind.LOCAL && right.kind() == IrExpression.Kind.OBJECT_NULL) {
            local = left;
        } else if (right.kind() == IrExpression.Kind.LOCAL && left.kind() == IrExpression.Kind.OBJECT_NULL) {
            local = right;
        } else {
            return state;
        }
        final boolean equal = "==".equals(operator) ? truth : "!=".equals(operator) && !truth;
        final Fact old = state.fact(local.value());
        return state.with(local.value(), new Fact(
            equal ? Nullness.NULL : Nullness.NON_NULL,
            old.integerRange(), old.exactType(), old.arrayLength(), old.stringLength()
        ));
    }

    private static State refineInteger(
        final State state,
        final IrExpression left,
        final IrExpression right,
        final String operator,
        final boolean truth
    ) {
        if (left.kind() != IrExpression.Kind.LOCAL || right.kind() != IrExpression.Kind.INT_LITERAL) {
            return state;
        }
        final int bound = Integer.parseInt(right.value());
        final Range current = state.fact(left.value()).integerRange();
        final int min = current == null ? Integer.MIN_VALUE : current.min();
        final int max = current == null ? Integer.MAX_VALUE : current.max();
        final Range refined = switch (operator) {
            case "==" -> truth ? Range.exact(bound) : current;
            case "!=" -> truth ? current : Range.exact(bound);
            case "<" -> truth && bound != Integer.MIN_VALUE
                ? bounded(min, bound - 1) : !truth ? bounded(Math.max(min, bound), max) : current;
            case "<=" -> !truth && bound != Integer.MAX_VALUE
                ? bounded(bound + 1, max) : truth ? bounded(min, Math.min(max, bound)) : current;
            case ">" -> truth && bound != Integer.MAX_VALUE
                ? bounded(bound + 1, max) : !truth ? bounded(min, Math.min(max, bound)) : current;
            case ">=" -> !truth && bound != Integer.MIN_VALUE
                ? bounded(min, bound - 1) : truth ? bounded(Math.max(min, bound), max) : current;
            default -> current;
        };
        if (refined == null) {
            return state;
        }
        final Fact old = state.fact(left.value());
        return state.with(left.value(), new Fact(
            old.nullness(), refined, old.exactType(), old.arrayLength(), old.stringLength()
        ));
    }

    private static Range bounded(final int min, final int max) {
        return min <= max ? new Range(min, max) : null;
    }

    private static Fact evaluate(final IrExpression expression, final State state) {
        Fact fact = UNKNOWN;
        switch (expression.kind()) {
            case INT_LITERAL -> fact = integer(Integer.parseInt(expression.value()));
            case OBJECT_NULL -> fact = new Fact(Nullness.NULL, null, null, null, null);
            case STRING_LITERAL -> fact = new Fact(
                Nullness.NON_NULL,
                null,
                "java/lang/String",
                null,
                Range.exact(expression.value().length())
            );
            case LOCAL -> fact = state.fact(expression.value());
            case OBJECT_ALLOCATION -> fact = new Fact(Nullness.NON_NULL, null, expression.value(), null, null);
            case OBJECT_ARRAY_ALLOCATION -> fact = array(expression, state, expression.value());
            case INT_ARRAY_ALLOCATION -> fact = array(expression, state, "[I");
            case LONG_ARRAY_ALLOCATION -> fact = array(expression, state, "[J");
            case FLOAT_ARRAY_ALLOCATION -> fact = array(expression, state, "[F");
            case DOUBLE_ARRAY_ALLOCATION -> fact = array(expression, state, "[D");
            case BYTE_ARRAY_ALLOCATION -> fact = array(expression, state, "[B");
            case BOOLEAN_ARRAY_ALLOCATION -> fact = array(expression, state, "[Z");
            case SHORT_ARRAY_ALLOCATION -> fact = array(expression, state, "[S");
            case CHAR_ARRAY_ALLOCATION -> fact = array(expression, state, "[C");
            case ARRAY_LENGTH -> fact = integerRange(evaluate(expression.arguments().getFirst(), state).arrayLength());
            case FIELD_INT, FIELD_OBJECT -> fact = fieldFact(expression, state);
            case INT_BINARY -> fact = integerBinary(expression, state);
            case INT_COMPARE -> fact = comparison(expression, state, false);
            case OBJECT_COMPARE -> fact = comparison(expression, state, true);
            case CALL -> fact = call(expression, state);
            default -> {
            }
        }
        return fact == null ? UNKNOWN : fact;
    }

    private static Fact fieldFact(final IrExpression expression, final State state) {
        final String field = fieldKey(expression);
        return field == null ? UNKNOWN : state.fact(field);
    }

    private static Fact array(final IrExpression expression, final State state, final String type) {
        final Range length = evaluate(expression.arguments().getFirst(), state).integerRange();
        return new Fact(Nullness.NON_NULL, null, type, length, null);
    }

    private static Fact call(final IrExpression expression, final State state) {
        if ("javan_string_length".equals(expression.value()) && !expression.arguments().isEmpty()) {
            return integerRange(evaluate(expression.arguments().getFirst(), state).stringLength());
        }
        return UNKNOWN;
    }

    private static Fact integerBinary(final IrExpression expression, final State state) {
        final Integer left = exact(evaluate(expression.arguments().get(0), state).integerRange());
        final Integer right = exact(evaluate(expression.arguments().get(1), state).integerRange());
        if (left == null || right == null) {
            return UNKNOWN;
        }
        return switch (expression.value()) {
            case "+" -> integer(left + right);
            case "-" -> integer(left - right);
            case "*" -> integer(left * right);
            case "/" -> right == 0 ? UNKNOWN : integer(left / right);
            case "%" -> right == 0 ? UNKNOWN : integer(left % right);
            case "&" -> integer(left & right);
            case "|" -> integer(left | right);
            case "^" -> integer(left ^ right);
            case "<<" -> integer(left << right);
            case ">>" -> integer(left >> right);
            case ">>>" -> integer(left >>> right);
            default -> UNKNOWN;
        };
    }

    private static Fact comparison(final IrExpression expression, final State state, final boolean object) {
        final Fact left = evaluate(expression.arguments().get(0), state);
        final Fact right = evaluate(expression.arguments().get(1), state);
        final Boolean result = object
            ? objectComparison(expression.value(), left, right, expression.arguments())
            : integerComparison(expression.value(), left.integerRange(), right.integerRange());
        return result == null ? UNKNOWN : integer(result ? 1 : 0);
    }

    private static Boolean objectComparison(
        final String operator,
        final Fact left,
        final Fact right,
        final List<IrExpression> arguments
    ) {
        Boolean equal = null;
        if (arguments.get(0).kind() == IrExpression.Kind.LOCAL
            && arguments.get(1).kind() == IrExpression.Kind.LOCAL
            && arguments.get(0).value().equals(arguments.get(1).value())) {
            equal = true;
        } else if ((left.nullness() == Nullness.NULL && right.nullness() == Nullness.NON_NULL)
            || (left.nullness() == Nullness.NON_NULL && right.nullness() == Nullness.NULL)) {
            equal = false;
        } else if (left.nullness() == Nullness.NULL && right.nullness() == Nullness.NULL) {
            equal = true;
        }
        if (equal == null) {
            return null;
        }
        if ("==".equals(operator)) {
            return equal;
        }
        if ("!=".equals(operator)) {
            return !equal;
        }
        return null;
    }

    private static Boolean integerComparison(final String operator, final Range left, final Range right) {
        if (left == null || right == null) {
            return null;
        }
        return switch (operator) {
            case "==" -> decision(
                left.exact() && right.exact() && left.min() == right.min(),
                left.max() < right.min() || right.max() < left.min()
            );
            case "!=" -> decision(
                left.max() < right.min() || right.max() < left.min(),
                left.exact() && right.exact() && left.min() == right.min()
            );
            case "<" -> decision(left.max() < right.min(), left.min() >= right.max());
            case "<=" -> decision(left.max() <= right.min(), left.min() > right.max());
            case ">" -> decision(left.min() > right.max(), left.max() <= right.min());
            case ">=" -> decision(left.min() >= right.max(), left.max() < right.min());
            default -> null;
        };
    }

    private static Boolean decision(final boolean provenTrue, final boolean provenFalse) {
        if (provenTrue) {
            return Boolean.TRUE;
        }
        if (provenFalse) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static State merge(final State left, final State right, final boolean widen) {
        final Fact[] facts = new Fact[left.facts().length];
        for (int index = 0; index < facts.length; index++) {
            final Fact leftFact = left.facts()[index];
            final Fact rightFact = right.facts()[index];
            if (leftFact != null && rightFact != null) {
                final Fact merged = merge(leftFact, rightFact, widen);
                facts[index] = isUnknown(merged) ? null : merged;
            }
        }
        return new State(left.slots(), facts);
    }

    private static Fact merge(final Fact left, final Fact right, final boolean widen) {
        return new Fact(
            left.nullness() == right.nullness() ? left.nullness() : Nullness.UNKNOWN,
            mergeRange(left.integerRange(), right.integerRange(), widen),
            sameText(left.exactType(), right.exactType()) ? left.exactType() : null,
            mergeRange(left.arrayLength(), right.arrayLength(), widen),
            mergeRange(left.stringLength(), right.stringLength(), widen)
        );
    }

    private static Range mergeRange(final Range left, final Range right, final boolean widen) {
        if (!widen) {
            return union(left, right);
        }
        if (left == null || right == null) {
            return null;
        }
        return new Range(
            right.min() < left.min() ? Integer.MIN_VALUE : left.min(),
            right.max() > left.max() ? Integer.MAX_VALUE : left.max()
        );
    }

    private static Range union(final Range left, final Range right) {
        if (left == null || right == null) {
            return null;
        }
        return new Range(Math.min(left.min(), right.min()), Math.max(left.max(), right.max()));
    }

    private static boolean sameText(final String left, final String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean same(final State left, final State right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.facts().length != right.facts().length) {
            return false;
        }
        for (int index = 0; index < left.facts().length; index++) {
            if (!same(left.facts()[index], right.facts()[index])) {
                return false;
            }
        }
        return true;
    }

    private static boolean same(final Fact left, final Fact right) {
        return left == right || left != null && right != null
            && left.nullness() == right.nullness()
            && same(left.integerRange(), right.integerRange())
            && sameText(left.exactType(), right.exactType())
            && same(left.arrayLength(), right.arrayLength())
            && same(left.stringLength(), right.stringLength());
    }

    private static boolean same(final Range left, final Range right) {
        return left == right || left != null && right != null && left.min() == right.min() && left.max() == right.max();
    }

    private static boolean isUnknown(final Fact fact) {
        return fact.nullness() == Nullness.UNKNOWN
            && fact.integerRange() == null
            && fact.exactType() == null
            && fact.arrayLength() == null
            && fact.stringLength() == null;
    }

    private static boolean isNullCheck(final IrInstruction instruction) {
        return instruction.op() == IrInstruction.Op.CALL_STATIC_VOID
            && instruction.expression().isPresent()
            && "javan_objects_require_non_null".equals(instruction.value().orElse(""))
            && !instruction.expression().orElseThrow().arguments().isEmpty();
    }

    private static Boolean booleanValue(final Fact fact) {
        if (fact == null) {
            return null;
        }
        final Integer value = exact(fact.integerRange());
        return value == null ? null : value != 0;
    }

    private static Integer exact(final Range range) {
        return range != null && range.exact() ? range.min() : null;
    }

    private static Fact integer(final int value) {
        return integerRange(Range.exact(value));
    }

    private static Fact integerRange(final Range range) {
        return range == null ? UNKNOWN : new Fact(Nullness.UNKNOWN, range, null, null, null);
    }

    private static List<IrInstruction> reachable(final List<IrInstruction> instructions) {
        if (instructions.isEmpty()) {
            return List.of();
        }
        final Map<String, Integer> labels = labels(instructions);
        final boolean[] seen = new boolean[instructions.size()];
        final List<Integer> work = new ArrayList<>();
        int cursor = 0;
        work.add(0);
        while (cursor < work.size()) {
            final int index = work.get(cursor);
            cursor++;
            if (seen[index]) {
                continue;
            }
            seen[index] = true;
            for (final int successor : successors(index, instructions, labels)) {
                work.add(successor);
            }
        }
        final List<IrInstruction> result = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            if (seen[index]) {
                result.add(instructions.get(index));
            }
        }
        return List.copyOf(result);
    }

    private static Proof proof(
        final IrFunction function,
        final IrInstruction instruction,
        final String kind,
        final String reason
    ) {
        final Optional<IrSourceLocation> location = instruction.sourceLocation();
        return new Proof(
            function.owner(),
            function.name(),
            function.descriptor(),
            location.isPresent() ? location.orElseThrow().bytecodeOffset() : -1,
            kind,
            reason
        );
    }

    private static FactSummary summarize(final State[] states) {
        final MutableSummary summary = new MutableSummary();
        for (final State state : states) {
            if (state == null) {
                continue;
            }
            for (final Fact fact : state.facts()) {
                if (fact != null) {
                    summary.observe(fact);
                }
            }
        }
        return summary.freeze();
    }

    private static IrFunction copy(final IrFunction function, final List<IrInstruction> instructions) {
        return new IrFunction(
            function.owner(), function.name(), function.descriptor(), function.symbol(), function.returnType(),
            function.parameters(), function.locals(), instructions
        );
    }

    private static IrProgram copy(final IrProgram program, final List<IrFunction> functions) {
        return new IrProgram(
            program.classes(), functions, program.dispatches(), program.entryFunction(), program.materializedLambdaTargets(),
            program.classInitializationDependencies(), program.enumDispatchConstants(), program.classTypeIds(),
            program.reflectedClasses()
        );
    }

    /**
     * Complete optimizer result.
     *
     * @param program original debug IR or rewritten release IR
     * @param report optimization counters
     * @param proofs deterministic reasons for every available rewrite
     * @param facts bounded observations for values that can affect an optimization candidate
     */
    public record Result(IrProgram program, OptimizationReport report, List<Proof> proofs, FactSummary facts) {
    }

    /**
     * Source-facing reason for a removable guard or dead branch.
     *
     * @param owner JVM internal owner
     * @param method method name
     * @param descriptor JVM method descriptor
     * @param bytecodeOffset source bytecode offset, or {@code -1} when unavailable
     * @param kind stable decision kind
     * @param reason human-readable proof
     */
    public record Proof(String owner, String method, String descriptor, int bytecodeOffset, String kind, String reason) {
    }

    /**
     * Number of facts available at instruction entries in functions with optimization candidates.
     *
     * @param nonNullValues proven non-null observations
     * @param nullValues proven null observations
     * @param integerConstants exact integer observations
     * @param integerRanges bounded integer observations, including constants
     * @param exactTypes exact reference-type observations
     * @param arrayLengths bounded array-length observations
     * @param stringLengths bounded string-length observations
     */
    public record FactSummary(
        long nonNullValues,
        long nullValues,
        long integerConstants,
        long integerRanges,
        long exactTypes,
        long arrayLengths,
        long stringLengths
    ) {
        private static FactSummary empty() {
            return new FactSummary(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record FunctionResult(
        IrFunction function,
        List<Proof> proofs,
        FactSummary facts,
        long removedNullChecks,
        long deadBranches,
        long skippedCandidates
    ) {
    }

    private record State(String[] slots, Fact[] facts) {
        private static State empty(final String[] slots) {
            return new State(slots, new Fact[slots.length]);
        }

        private Fact fact(final String name) {
            final int index = slot(name);
            final Fact fact = index < 0 ? null : facts[index];
            return fact == null ? UNKNOWN : fact;
        }

        private State with(final String name, final Fact fact) {
            final int slot = slot(name);
            if (slot < 0) {
                return this;
            }
            final Fact normalized = isUnknown(fact) ? null : fact;
            if (same(facts[slot], normalized)) {
                return this;
            }
            final Fact[] changed = copyFacts();
            changed[slot] = normalized;
            return new State(slots, changed);
        }

        private State clearFields() {
            Fact[] changed = null;
            for (int index = 0; index < slots.length; index++) {
                if (slots[index].startsWith(FIELD_SLOT_PREFIX) && facts[index] != null) {
                    if (changed == null) {
                        changed = copyFacts();
                    }
                    changed[index] = null;
                }
            }
            return changed == null ? this : new State(slots, changed);
        }

        private Fact[] copyFacts() {
            final Fact[] changed = new Fact[facts.length];
            for (int index = 0; index < facts.length; index++) {
                changed[index] = facts[index];
            }
            return changed;
        }

        private int slot(final String name) {
            for (int index = 0; index < slots.length; index++) {
                if (slots[index].equals(name)) {
                    return index;
                }
            }
            return -1;
        }
    }

    private record Fact(
        Nullness nullness,
        Range integerRange,
        String exactType,
        Range arrayLength,
        Range stringLength
    ) {
    }

    private record Range(int min, int max) {
        private static Range exact(final int value) {
            return new Range(value, value);
        }

        private boolean exact() {
            return min == max;
        }
    }

    private enum Nullness {
        UNKNOWN,
        NULL,
        NON_NULL
    }

    private static final class MutableSummary {
        private long nonNullValues;
        private long nullValues;
        private long integerConstants;
        private long integerRanges;
        private long exactTypes;
        private long arrayLengths;
        private long stringLengths;

        private void observe(final Fact fact) {
            nonNullValues += fact.nullness() == Nullness.NON_NULL ? 1 : 0;
            nullValues += fact.nullness() == Nullness.NULL ? 1 : 0;
            integerConstants += fact.integerRange() != null && fact.integerRange().exact() ? 1 : 0;
            integerRanges += fact.integerRange() != null ? 1 : 0;
            exactTypes += fact.exactType() != null ? 1 : 0;
            arrayLengths += fact.arrayLength() != null ? 1 : 0;
            stringLengths += fact.stringLength() != null ? 1 : 0;
        }

        private void add(final FactSummary summary) {
            nonNullValues += summary.nonNullValues();
            nullValues += summary.nullValues();
            integerConstants += summary.integerConstants();
            integerRanges += summary.integerRanges();
            exactTypes += summary.exactTypes();
            arrayLengths += summary.arrayLengths();
            stringLengths += summary.stringLengths();
        }

        private FactSummary freeze() {
            return new FactSummary(
                nonNullValues, nullValues, integerConstants, integerRanges, exactTypes, arrayLengths, stringLengths
            );
        }
    }
}
