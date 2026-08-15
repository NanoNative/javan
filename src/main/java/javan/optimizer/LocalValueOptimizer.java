package javan.optimizer;

import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;
import javan.ir.IrSourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Propagates conservative local facts through lowered control flow and applies proven release rewrites.
 */
public final class LocalValueOptimizer {
    private static final Fact UNKNOWN = new Fact(Nullness.UNKNOWN, null, null, null, null);

    /**
     * Analyzes a lowered program and optionally applies proof-backed release rewrites.
     *
     * @param program lowered program
     * @param release whether proven rewrites may change the program
     * @return analyzed program, counters, facts, and deterministic proof records
     */
    public Result optimize(final IrProgram program, final boolean release) {
        final List<IrFunction> functions = new ArrayList<>();
        final List<Proof> proofs = new ArrayList<>();
        final MutableSummary summary = new MutableSummary();
        long removedNullChecks = 0;
        long deadBranches = 0;
        for (final IrFunction function : program.functions()) {
            final FunctionResult result = analyze(function, release);
            functions.add(result.function());
            proofs.addAll(result.proofs());
            summary.add(result.facts());
            removedNullChecks += result.removedNullChecks();
            deadBranches += result.deadBranches();
        }
        final IrProgram optimized = release ? copy(program, functions) : program;
        return new Result(
            optimized,
            new OptimizationReport(removedNullChecks, 0, 0, 0, deadBranches, 0, release ? 0 : proofs.size()),
            List.copyOf(proofs),
            summary.freeze()
        );
    }

    private static FunctionResult analyze(final IrFunction function, final boolean release) {
        final List<IrInstruction> instructions = function.instructions();
        if (instructions.isEmpty()) {
            return new FunctionResult(function, List.of(), FactSummary.empty(), 0, 0);
        }
        final Map<String, Integer> labels = labels(instructions);
        final State[] incoming = new State[instructions.size()];
        final List<Integer> work = new ArrayList<>();
        int cursor = 0;
        incoming[0] = State.empty();
        work.add(0);
        while (cursor < work.size()) {
            final int index = work.get(cursor);
            cursor++;
            final IrInstruction instruction = instructions.get(index);
            final State outgoing = transfer(incoming[index], instruction);
            if (instruction.op() == IrInstruction.Op.BRANCH_IF) {
                final Boolean decision = booleanValue(evaluate(instruction.expression().orElseThrow(), outgoing));
                final int target = labels.get(instruction.value().orElseThrow());
                if (decision == null || decision) {
                    enqueue(incoming, work, target, refine(outgoing, instruction.expression().orElseThrow(), true));
                }
                if ((decision == null || !decision) && index + 1 < instructions.size()) {
                    enqueue(incoming, work, index + 1, refine(outgoing, instruction.expression().orElseThrow(), false));
                }
            } else {
                for (final int successor : successors(index, instructions, labels)) {
                    enqueue(incoming, work, successor, outgoing);
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
            deadBranches
        );
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

    private static State transfer(final State state, final IrInstruction instruction) {
        return switch (instruction.op()) {
            case ASSIGN_INT, ASSIGN_OBJECT -> state.with(
                instruction.value().orElseThrow(),
                evaluate(instruction.expression().orElseThrow(), state)
            );
            case ASSIGN_LONG, ASSIGN_FLOAT, ASSIGN_DOUBLE -> state.with(instruction.value().orElseThrow(), UNKNOWN);
            default -> state;
        };
    }

    private static void enqueue(
        final State[] incoming,
        final List<Integer> work,
        final int successor,
        final State state
    ) {
        final State merged = incoming[successor] == null ? state : merge(incoming[successor], state);
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
            case INT_BINARY -> fact = integerBinary(expression, state);
            case INT_COMPARE -> fact = comparison(expression, state, false);
            case OBJECT_COMPARE -> fact = comparison(expression, state, true);
            case CALL -> fact = call(expression, state);
            default -> {
            }
        }
        return fact == null ? UNKNOWN : fact;
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

    private static State merge(final State left, final State right) {
        final Map<String, Fact> facts = new LinkedHashMap<>();
        for (final Map.Entry<String, Fact> entry : left.facts().entrySet()) {
            final Fact other = right.facts().get(entry.getKey());
            if (other != null) {
                final Fact merged = merge(entry.getValue(), other);
                if (!isUnknown(merged)) {
                    facts.put(entry.getKey(), merged);
                }
            }
        }
        return new State(facts);
    }

    private static Fact merge(final Fact left, final Fact right) {
        return new Fact(
            left.nullness() == right.nullness() ? left.nullness() : Nullness.UNKNOWN,
            union(left.integerRange(), right.integerRange()),
            sameText(left.exactType(), right.exactType()) ? left.exactType() : null,
            union(left.arrayLength(), right.arrayLength()),
            union(left.stringLength(), right.stringLength())
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
        if (left == null || right == null || left.facts().size() != right.facts().size()) {
            return false;
        }
        for (final Map.Entry<String, Fact> entry : left.facts().entrySet()) {
            if (!same(entry.getValue(), right.facts().get(entry.getKey()))) {
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
            for (final Fact fact : state.facts().values()) {
                summary.observe(fact);
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
            program.classInitializationDependencies(), program.enumDispatchConstants()
        );
    }

    /**
     * Complete optimizer result.
     *
     * @param program original debug IR or rewritten release IR
     * @param report optimization counters
     * @param proofs deterministic reasons for every available rewrite
     * @param facts fact observations across reachable instruction entries
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
     * Number of facts available at analyzed instruction entries.
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
        long deadBranches
    ) {
    }

    private record State(Map<String, Fact> facts) {
        private static State empty() {
            return new State(Map.of());
        }

        private Fact fact(final String name) {
            final Fact fact = facts.get(name);
            return fact == null ? UNKNOWN : fact;
        }

        private State with(final String name, final Fact fact) {
            final Map<String, Fact> changed = new LinkedHashMap<>(facts);
            if (isUnknown(fact)) {
                changed.remove(name);
            } else {
                changed.put(name, fact);
            }
            return new State(changed);
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
