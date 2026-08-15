package javan.optimizer;

import javan.ir.IrDispatch;
import javan.ir.IrDispatchTarget;
import javan.ir.IrExpression;
import javan.ir.IrFunction;
import javan.ir.IrInstruction;
import javan.ir.IrProgram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes conservative direct and transitive effects for lowered methods.
 */
public final class MethodEffectAnalyzer {
    private static final Effect NONE = new Effect(false, false, false, false, false);
    private static final Effect UNKNOWN = new Effect(true, false, false, false, true);

    /**
     * Analyzes all lowered functions to a fixed point.
     *
     * @param program lowered program
     * @return effects in program function order
     */
    public Analysis analyze(final IrProgram program) {
        final List<IrFunction> functions = program.functions();
        final Map<String, Integer> indexes = new HashMap<>();
        for (int index = 0; index < functions.size(); index++) {
            indexes.put(functions.get(index).symbol(), index);
        }
        final Map<String, List<Integer>> targets = new HashMap<>();
        for (final Map.Entry<String, Integer> entry : indexes.entrySet()) {
            targets.put(entry.getKey(), List.of(entry.getValue()));
        }
        for (final IrDispatch dispatch : program.dispatches()) {
            final List<Integer> dispatchTargets = new ArrayList<>();
            boolean complete = !dispatch.targets().isEmpty();
            for (final IrDispatchTarget target : dispatch.targets()) {
                final Integer index = indexes.get(target.functionSymbol());
                if (index == null) {
                    complete = false;
                } else if (!dispatchTargets.contains(index)) {
                    dispatchTargets.add(index);
                }
            }
            if (complete) {
                targets.put(dispatch.symbol(), List.copyOf(dispatchTargets));
            }
        }

        final List<List<Integer>> callees = lists(functions.size());
        final List<List<Integer>> callers = lists(functions.size());
        final Effect[] direct = new Effect[functions.size()];
        final Effect[] effects = new Effect[functions.size()];
        for (int index = 0; index < functions.size(); index++) {
            final Scan scan = scan(functions.get(index), targets);
            direct[index] = scan.effect();
            effects[index] = scan.effect();
            callees.get(index).addAll(scan.callees());
            for (final int callee : scan.callees()) {
                callers.get(callee).add(index);
            }
        }

        final List<Integer> work = new ArrayList<>();
        for (int index = 0; index < functions.size(); index++) {
            work.add(index);
        }
        int cursor = 0;
        while (cursor < work.size()) {
            final int function = work.get(cursor);
            cursor++;
            Effect merged = direct[function];
            for (final int callee : callees.get(function)) {
                merged = merged.merge(effects[callee]);
            }
            if (!merged.equals(effects[function])) {
                effects[function] = merged;
                work.addAll(callers.get(function));
            }
        }

        final List<MethodEffect> result = new ArrayList<>();
        for (int index = 0; index < functions.size(); index++) {
            final IrFunction function = functions.get(index);
            result.add(new MethodEffect(
                function.owner(),
                function.name(),
                function.descriptor(),
                function.symbol(),
                effects[index]
            ));
        }
        return new Analysis(result);
    }

    private static List<List<Integer>> lists(final int size) {
        final List<List<Integer>> result = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            result.add(new ArrayList<>());
        }
        return result;
    }

    private static Scan scan(final IrFunction function, final Map<String, List<Integer>> targets) {
        Effect effect = NONE;
        final List<Integer> callees = new ArrayList<>();
        for (final IrInstruction instruction : function.instructions()) {
            effect = effect.merge(instructionEffect(instruction));
            if (instruction.op() == IrInstruction.Op.CALL_STATIC_VOID && instruction.expression().isEmpty()) {
                effect = effect.merge(call(instruction.value().orElseThrow(), targets, callees));
            }
            if (instruction.expression().isPresent()) {
                effect = effect.merge(expressionEffect(instruction.expression().orElseThrow(), targets, callees));
            }
        }
        return new Scan(effect, List.copyOf(callees));
    }

    private static Effect instructionEffect(final IrInstruction instruction) {
        return switch (instruction.op()) {
            case PRINTLN_LITERAL,
                 PRINTLN_INT, PRINTLN_ERROR_INT,
                 PRINTLN_LONG, PRINTLN_ERROR_LONG,
                 PRINTLN_FLOAT, PRINTLN_ERROR_FLOAT,
                 PRINTLN_DOUBLE, PRINTLN_ERROR_DOUBLE,
                 PRINTLN_BOOLEAN, PRINTLN_ERROR_BOOLEAN,
                 PRINTLN_OBJECT, PRINTLN_ERROR_OBJECT,
                 PRINT_OBJECT, PRINT_ERROR_OBJECT -> NONE.writing();
            case INITIALIZE_CLASS -> UNKNOWN.writing();
            case ASSIGN_FIELD_INT, ASSIGN_FIELD_LONG, ASSIGN_FIELD_FLOAT, ASSIGN_FIELD_DOUBLE, ASSIGN_FIELD_OBJECT,
                 ASSIGN_STATIC_FIELD_INT, ASSIGN_STATIC_FIELD_LONG, ASSIGN_STATIC_FIELD_FLOAT,
                 ASSIGN_STATIC_FIELD_DOUBLE, ASSIGN_STATIC_FIELD_OBJECT -> NONE.writing();
            case ASSIGN_ARRAY_OBJECT, ASSIGN_ARRAY_INT, ASSIGN_ARRAY_LONG, ASSIGN_ARRAY_FLOAT,
                 ASSIGN_ARRAY_DOUBLE, ASSIGN_ARRAY_BYTE, ASSIGN_ARRAY_SHORT, ASSIGN_ARRAY_CHAR ->
                NONE.writing().throwing();
            case PANIC, SET_PENDING, THROW_PENDING, PROPAGATE_PENDING -> NONE.throwing();
            default -> NONE;
        };
    }

    private static Effect expressionEffect(
        final IrExpression expression,
        final Map<String, List<Integer>> targets,
        final List<Integer> callees
    ) {
        Effect effect = switch (expression.kind()) {
            case CALL -> call(expression.value(), targets, callees);
            case STRING_CONCAT,
                 OBJECT_ALLOCATION,
                 OBJECT_ARRAY_ALLOCATION,
                 INT_ARRAY_ALLOCATION,
                 LONG_ARRAY_ALLOCATION,
                 FLOAT_ARRAY_ALLOCATION,
                 DOUBLE_ARRAY_ALLOCATION,
                 BYTE_ARRAY_ALLOCATION,
                 BOOLEAN_ARRAY_ALLOCATION,
                 SHORT_ARRAY_ALLOCATION,
                 CHAR_ARRAY_ALLOCATION -> NONE.allocating().throwing();
            case OBJECT_ARRAY_LOAD,
                 INT_ARRAY_LOAD,
                 LONG_ARRAY_LOAD,
                 FLOAT_ARRAY_LOAD,
                 DOUBLE_ARRAY_LOAD,
                 BYTE_ARRAY_LOAD,
                 SHORT_ARRAY_LOAD,
                 CHAR_ARRAY_LOAD,
                 ARRAY_LENGTH,
                 FIELD_INT,
                 FIELD_LONG,
                 FIELD_FLOAT,
                 FIELD_DOUBLE,
                 FIELD_OBJECT -> NONE.reading().throwing();
            case STATIC_FIELD_INT,
                 STATIC_FIELD_LONG,
                 STATIC_FIELD_FLOAT,
                 STATIC_FIELD_DOUBLE,
                 STATIC_FIELD_OBJECT -> NONE.reading();
            case FIELD_ASSIGN_INT,
                 FIELD_ASSIGN_LONG,
                 FIELD_ASSIGN_FLOAT,
                 FIELD_ASSIGN_DOUBLE,
                 FIELD_ASSIGN_OBJECT,
                 ARRAY_ASSIGN_OBJECT,
                 ARRAY_ASSIGN_INT,
                 ARRAY_ASSIGN_LONG,
                 ARRAY_ASSIGN_FLOAT,
                 ARRAY_ASSIGN_DOUBLE,
                 ARRAY_ASSIGN_BYTE,
                 ARRAY_ASSIGN_SHORT,
                 ARRAY_ASSIGN_CHAR -> NONE.writing().throwing();
            default -> NONE;
        };
        for (final IrExpression argument : expression.arguments()) {
            effect = effect.merge(expressionEffect(argument, targets, callees));
        }
        return effect;
    }

    private static Effect call(
        final String symbol,
        final Map<String, List<Integer>> targets,
        final List<Integer> callees
    ) {
        final List<Integer> resolved = targets.get(symbol);
        if (resolved != null) {
            for (final int index : resolved) {
                if (!callees.contains(index)) {
                    callees.add(index);
                }
            }
            return NONE;
        }
        return intrinsic(symbol);
    }

    private static Effect intrinsic(final String symbol) {
        return switch (symbol) {
            case "javan_i2b", "javan_i2s", "javan_l2i", "javan_i2l", "javan_i2f", "javan_i2d",
                 "javan_l2d", "javan_f2d", "javan_d2f", "javan_lcmp",
                 "javan_double_to_int", "javan_double_to_long" -> NONE;
            case "javan_objects_require_non_null" -> NONE.throwing();
            case "javan_string_length" -> NONE.reading().throwing();
            case "javan_system_out", "javan_system_err" -> NONE.reading();
            case "javan_pending_clear" -> NONE.writing();
            default -> UNKNOWN;
        };
    }

    /**
     * Per-method effect analysis and exact-symbol lookup.
     *
     * @param methods effects in lowered function order
     */
    public static final class Analysis {
        private final List<MethodEffect> methods;
        private final Map<String, Effect> effects;

        /**
         * Creates an immutable exact-symbol lookup.
         *
         * @param methods effects in deterministic report order
         */
        public Analysis(final List<MethodEffect> methods) {
            this.methods = List.copyOf(methods);
            final Map<String, Effect> indexed = new HashMap<>();
            for (final MethodEffect method : methods) {
                indexed.put(method.symbol(), method.effect());
            }
            effects = Map.copyOf(indexed);
        }

        /** @return effects in deterministic lowered-function order */
        public List<MethodEffect> methods() {
            return methods;
        }

        /**
         * Finds an exact lowered method effect.
         *
         * @param symbol lowered function symbol
         * @return known effect, or conservative unknown for an external symbol
         */
        public Effect effect(final String symbol) {
            return effects.getOrDefault(symbol, UNKNOWN);
        }

        /**
         * Reports whether a call is proven not to mutate existing memory.
         *
         * @param symbol lowered function symbol
         * @return true only for a known non-writing call
         */
        public boolean preservesMemoryFacts(final String symbol) {
            final Effect effect = effect(symbol);
            return !effect.writes() && !effect.unknown();
        }
    }

    /**
     * Method identity paired with its transitive effect.
     *
     * @param owner JVM internal owner
     * @param name JVM method name
     * @param descriptor JVM method descriptor
     * @param symbol lowered function symbol
     * @param effect transitive conservative effect
     */
    public record MethodEffect(String owner, String name, String descriptor, String symbol, Effect effect) {
    }

    /**
     * Monotonic method-effect lattice.
     *
     * @param mayThrow may complete by Java exception or runtime panic
     * @param allocates allocates managed memory
     * @param reads reads existing heap or static state
     * @param writes mutates heap, static, runtime, or external state
     * @param unknown contains behavior outside the admitted effect model
     */
    public record Effect(boolean mayThrow, boolean allocates, boolean reads, boolean writes, boolean unknown) {
        /** @return true when the method has no modeled side effect; throwing remains explicit */
        public boolean pure() {
            return !allocates && !reads && !writes && !unknown;
        }

        private Effect merge(final Effect other) {
            final boolean mergedThrow = mayThrow || other.mayThrow;
            final boolean mergedAllocate = allocates || other.allocates;
            final boolean mergedRead = reads || other.reads;
            final boolean mergedWrite = writes || other.writes;
            final boolean mergedUnknown = unknown || other.unknown;
            return mergedThrow == mayThrow
                && mergedAllocate == allocates
                && mergedRead == reads
                && mergedWrite == writes
                && mergedUnknown == unknown
                ? this
                : new Effect(mergedThrow, mergedAllocate, mergedRead, mergedWrite, mergedUnknown);
        }

        private Effect throwing() {
            return mayThrow ? this : new Effect(true, allocates, reads, writes, unknown);
        }

        private Effect allocating() {
            return allocates ? this : new Effect(mayThrow, true, reads, writes, unknown);
        }

        private Effect reading() {
            return reads ? this : new Effect(mayThrow, allocates, true, writes, unknown);
        }

        private Effect writing() {
            return writes ? this : new Effect(mayThrow, allocates, reads, true, unknown);
        }
    }

    private record Scan(Effect effect, List<Integer> callees) {
    }
}
