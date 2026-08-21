package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.DynamicRef;
import javan.classfile.FieldInfo;
import javan.classfile.FieldRef;
import javan.classfile.FunctionLambdaUse;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactoryCall;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Conservatively tracks reachable callback origins and bounded concrete receiver types. */
public final class FunctionValueFlow {
    private static final int MAX_EXACT_TYPES = 4;
    private static final int MATERIALIZED = 1;
    private static final int CONCRETE = 2;
    private static final int UNSAFE = 4;
    private static final Fact NONE_FACT = new Fact(0, List.of(), false);
    private static final Fact MATERIALIZED_FACT = new Fact(MATERIALIZED, List.of(), false);
    private static final Fact UNSAFE_FACT = new Fact(UNSAFE, List.of(), true);
    private static final int ACC_FINAL = 0x0010;
    private static final int ACC_PRIVATE = 0x0002;
    private static final String FUNCTION_DESCRIPTOR = "Ljava/util/function/Function;";
    private static final String SUPPLIER_DESCRIPTOR = "Ljava/util/function/Supplier;";
    private static final MethodRef FUNCTION_APPLY = new MethodRef(
        "java/util/function/Function",
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;"
    );
    private static final MethodRef SUPPLIER_GET = new MethodRef(
        "java/util/function/Supplier",
        "get",
        "()Ljava/lang/Object;"
    );

    private FunctionValueFlow() {
    }

    /** @return maximum exact receiver types retained at one use site */
    public static int maxExactTypes() {
        return MAX_EXACT_TYPES;
    }

    /**
     * An exact reachable bytecode instruction.
     *
     * @param className JVM owner
     * @param methodName JVM method name
     * @param descriptor JVM method descriptor
     * @param offset bytecode offset
     */
    public record Site(String className, String methodName, String descriptor, int offset) {
    }

    /**
     * Conservative receiver classification.
     */
    public enum ValueKind {
        UNKNOWN,
        MATERIALIZED,
        CONCRETE,
        UNSAFE,
        MATERIALIZED_AND_CONCRETE,
        MIXED
    }

    /**
     * Bounded concrete receiver evidence at one callback use.
     *
     * @param types sorted exact JVM receiver types
     * @param unknown whether dispatch must conservatively ignore the exact set
     */
    public record Provenance(List<String> types, boolean unknown) {
        private static final Provenance UNAVAILABLE = new Provenance(List.of(), true);

        public Provenance {
            final List<String> ordered = new ArrayList<>();
            for (final String type : types) {
                int index = 0;
                while (index < ordered.size() && Strings2.compareAscii(ordered.get(index), type) < 0) {
                    index++;
                }
                if (index >= ordered.size() || !ordered.get(index).equals(type)) {
                    ordered.add(index, type);
                }
            }
            unknown = unknown || ordered.size() > MAX_EXACT_TYPES;
            types = unknown ? List.of() : List.copyOf(ordered);
        }

        /** @return explicit unknown provenance */
        public static Provenance unavailable() {
            return UNAVAILABLE;
        }
    }

    /**
     * Immutable whole-program callback-flow result.
     *
     * @param functionKinds Function value classification by exact use site
     * @param supplierKinds Supplier value classification by exact use site
     * @param functionProvenance exact Function receiver evidence by use site
     * @param supplierProvenance exact Supplier receiver evidence by use site
     * @param complete whether whole-program callback-flow analysis was available
     */
    public record Result(
        Map<Site, ValueKind> functionKinds,
        Map<Site, ValueKind> supplierKinds,
        Map<Site, Provenance> functionProvenance,
        Map<Site, Provenance> supplierProvenance,
        boolean complete
    ) {
        /**
         * Creates a complete result.
         *
         * @param functionKinds Function value classification by exact use site
         */
        public Result(final Map<Site, ValueKind> functionKinds) {
            this(functionKinds, Map.of(), Map.of(), Map.of(), true);
        }

        /**
         * Creates a result with Function facts only.
         *
         * @param functionKinds Function value classification by exact use site
         * @param complete whether whole-program callback-flow analysis was available
         */
        public Result(final Map<Site, ValueKind> functionKinds, final boolean complete) {
            this(functionKinds, Map.of(), Map.of(), Map.of(), complete);
        }

        /**
         * Creates a result without exact receiver evidence.
         *
         * @param functionKinds Function value classification by exact use site
         * @param supplierKinds Supplier value classification by exact use site
         * @param complete whether whole-program callback-flow analysis was available
         */
        public Result(
            final Map<Site, ValueKind> functionKinds,
            final Map<Site, ValueKind> supplierKinds,
            final boolean complete
        ) {
            this(functionKinds, supplierKinds, Map.of(), Map.of(), complete);
        }

        public Result {
            functionKinds = Map.copyOf(functionKinds);
            supplierKinds = Map.copyOf(supplierKinds);
            functionProvenance = Map.copyOf(functionProvenance);
            supplierProvenance = Map.copyOf(supplierProvenance);
        }

        /**
         * Creates a marker for call graphs assembled without function-flow analysis.
         *
         * @return unavailable result
         */
        public static Result unavailable() {
            return new Result(Map.of(), Map.of(), Map.of(), Map.of(), false);
        }

        /**
         * Returns the Function value classification for an exact use instruction.
         *
         * @param className JVM owner
         * @param methodName JVM method name
         * @param descriptor JVM method descriptor
         * @param offset bytecode offset
         * @return conservative Function value classification
         */
        public ValueKind functionKind(
            final String className,
            final String methodName,
            final String descriptor,
            final int offset
        ) {
            return functionKinds.getOrDefault(
                new Site(className, methodName, descriptor, offset),
                ValueKind.UNKNOWN
            );
        }

        /**
         * Returns whether an exact Function use is proven materialized.
         *
         * @param className JVM owner
         * @param methodName JVM method name
         * @param descriptor JVM method descriptor
         * @param offset bytecode offset
         * @return true only for a materialized-only origin
         */
        public boolean isMaterializedFunction(
            final String className,
            final String methodName,
            final String descriptor,
            final int offset
        ) {
            return functionKind(className, methodName, descriptor, offset) == ValueKind.MATERIALIZED;
        }

        /**
         * Returns the Supplier value classification for an exact use instruction.
         *
         * @param className JVM owner
         * @param methodName JVM method name
         * @param descriptor JVM method descriptor
         * @param offset bytecode offset
         * @return conservative Supplier value classification
         */
        public ValueKind supplierKind(
            final String className,
            final String methodName,
            final String descriptor,
            final int offset
        ) {
            return supplierKinds.getOrDefault(
                new Site(className, methodName, descriptor, offset),
                ValueKind.UNKNOWN
            );
        }

        /**
         * Returns bounded Function receiver evidence for an exact use instruction.
         *
         * @param className JVM owner
         * @param methodName JVM method name
         * @param descriptor JVM method descriptor
         * @param offset bytecode offset
         * @return exact types or explicit unknown provenance
         */
        public Provenance functionProvenance(
            final String className,
            final String methodName,
            final String descriptor,
            final int offset
        ) {
            return functionProvenance.getOrDefault(
                new Site(className, methodName, descriptor, offset),
                Provenance.unavailable()
            );
        }

        /**
         * Returns bounded Supplier receiver evidence for an exact use instruction.
         *
         * @param className JVM owner
         * @param methodName JVM method name
         * @param descriptor JVM method descriptor
         * @param offset bytecode offset
         * @return exact types or explicit unknown provenance
         */
        public Provenance supplierProvenance(
            final String className,
            final String methodName,
            final String descriptor,
            final int offset
        ) {
            return supplierProvenance.getOrDefault(
                new Site(className, methodName, descriptor, offset),
                Provenance.unavailable()
            );
        }
    }

    /**
     * Analyzes reachable methods using a monotonic value-flow fixpoint.
     *
     * @param classes parsed closed-world classes
     * @param reachableMethods reachable methods
     * @return exact callback use facts
     */
    public static Result analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        return analyze(classes, reachableMethods, List.of());
    }

    /**
     * Analyzes reachable methods with exact declared external leaves.
     *
     * @param classes parsed closed-world classes
     * @param reachableMethods reachable methods
     * @param declaredExternalLeaves exact declared external method identities
     * @return exact callback use facts
     */
    public static Result analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods,
        final List<EntryPoint> declaredExternalLeaves
    ) {
        final MethodFlowCache methodFlows = new MethodFlowCache();
        final FlowResult function = containsTrackedUse(
            classes,
            reachableMethods,
            CallbackKind.FUNCTION
        ) ? new Engine(classes, reachableMethods, declaredExternalLeaves, CallbackKind.FUNCTION, methodFlows).analyze() : FlowResult.empty();
        final FlowResult supplier = containsTrackedUse(
            classes,
            reachableMethods,
            CallbackKind.SUPPLIER
        ) ? new Engine(classes, reachableMethods, declaredExternalLeaves, CallbackKind.SUPPLIER, methodFlows).analyze() : FlowResult.empty();
        return new Result(
            function.kinds(),
            supplier.kinds(),
            function.provenance(),
            supplier.provenance(),
            true
        );
    }

    private static boolean containsTrackedUse(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods,
        final CallbackKind callbackKind
    ) {
        for (final EntryPoint entryPoint : reachableMethods) {
            final ClassFile classFile = classes.get(entryPoint.className());
            if (classFile == null) {
                continue;
            }
            final Optional<MethodInfo> method = classFile.method(
                entryPoint.methodName(),
                entryPoint.descriptor()
            );
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.methodRef().isPresent()
                    && isTrackedUse(callbackKind, instruction.methodRef().orElseThrow())) {
                    return true;
                }
            }
        }
        return false;
    }

    private record CachedMethodFlow(
        String methodName,
        String descriptor,
        BytecodeControlFlow.Result controlFlow,
        Map<Integer, Integer> instructionIndexes
    ) {
    }

    private static final class MethodFlowCache {
        private final Map<String, List<CachedMethodFlow>> byOwner = new HashMap<>();

        private CachedMethodFlow methodFlow(
            final EntryPoint entryPoint,
            final MethodInfo method,
            final List<Instruction> bytecode
        ) {
            List<CachedMethodFlow> ownerFlows = byOwner.get(entryPoint.className());
            if (ownerFlows == null) {
                ownerFlows = new ArrayList<>();
                byOwner.put(entryPoint.className(), ownerFlows);
            }
            for (final CachedMethodFlow flow : ownerFlows) {
                if (flow.methodName().equals(entryPoint.methodName())
                    && flow.descriptor().equals(entryPoint.descriptor())) {
                    return flow;
                }
            }
            final CachedMethodFlow flow = new CachedMethodFlow(
                entryPoint.methodName(),
                entryPoint.descriptor(),
                BytecodeControlFlow.analyze(method),
                instructionIndexes(bytecode)
            );
            ownerFlows.add(flow);
            return flow;
        }
    }

    private static final class EntryPointMembership {
        private final Map<String, List<EntryPoint>> byOwner = new HashMap<>();

        private EntryPointMembership(final List<EntryPoint> entries) {
            for (final EntryPoint entry : entries) {
                add(entry);
            }
        }

        private boolean add(final EntryPoint entry) {
            List<EntryPoint> bucket = byOwner.get(entry.className());
            if (bucket == null) {
                bucket = new ArrayList<>();
                byOwner.put(entry.className(), bucket);
            }
            for (final EntryPoint candidate : bucket) {
                if (candidate.methodName().equals(entry.methodName())
                    && candidate.descriptor().equals(entry.descriptor())) {
                    return false;
                }
            }
            bucket.add(entry);
            return true;
        }

        private boolean contains(final EntryPoint entry) {
            final List<EntryPoint> bucket = byOwner.get(entry.className());
            if (bucket == null) {
                return false;
            }
            for (final EntryPoint candidate : bucket) {
                if (candidate.methodName().equals(entry.methodName())
                    && candidate.descriptor().equals(entry.descriptor())) {
                    return true;
                }
            }
            return false;
        }

        private void remove(final EntryPoint entry) {
            final List<EntryPoint> bucket = byOwner.get(entry.className());
            if (bucket == null) {
                return;
            }
            for (int index = 0; index < bucket.size(); index++) {
                final EntryPoint candidate = bucket.get(index);
                if (candidate.methodName().equals(entry.methodName())
                    && candidate.descriptor().equals(entry.descriptor())) {
                    bucket.remove(index);
                    return;
                }
            }
        }
    }

    private static final class Engine {
        private final Map<String, ClassFile> classes;
        private final List<EntryPoint> reachableMethods;
        private final EntryPointMembership reachableSet;
        private final EntryPointMembership declaredExternalLeaves;
        private final CallbackKind callbackKind;
        private final Map<EntryPoint, Fact[]> parameterFacts = new LinkedHashMap<>();
        private final Map<EntryPoint, Fact> returnFacts = new LinkedHashMap<>();
        private final Set<FieldRef> finalCallbackFields = new LinkedHashSet<>();
        private final Map<FieldRef, Fact> fieldFacts = new LinkedHashMap<>();
        private final Map<Site, Fact> callbackFacts = new LinkedHashMap<>();
        private final Map<String, Boolean> callbackTypeCache = new HashMap<>();
        private final MethodFlowCache methodFlows;
        private final Map<EntryPoint, List<EntryPoint>> callers = new HashMap<>();
        private final Map<FieldRef, List<EntryPoint>> fieldReaders = new HashMap<>();
        private final List<EntryPoint> pendingMethods = new ArrayList<>();
        private final EntryPointMembership queuedMethods = new EntryPointMembership(List.of());

        private Engine(
            final Map<String, ClassFile> classes,
            final List<EntryPoint> reachableMethods,
            final List<EntryPoint> declaredExternalLeaves,
            final CallbackKind callbackKind,
            final MethodFlowCache methodFlows
        ) {
            this.classes = classes;
            this.reachableMethods = List.copyOf(reachableMethods);
            this.reachableSet = new EntryPointMembership(reachableMethods);
            this.declaredExternalLeaves = new EntryPointMembership(declaredExternalLeaves);
            this.callbackKind = callbackKind;
            this.methodFlows = methodFlows;
            initializeFacts();
        }

        private FlowResult analyze() {
            for (final EntryPoint entryPoint : reachableMethods) {
                enqueueMethod(entryPoint);
            }
            int pendingIndex = 0;
            while (pendingIndex < pendingMethods.size()) {
                final EntryPoint entryPoint = pendingMethods.get(pendingIndex);
                pendingIndex++;
                queuedMethods.remove(entryPoint);
                simulate(entryPoint);
            }
            final Map<Site, ValueKind> kinds = new LinkedHashMap<>();
            final Map<Site, Provenance> provenance = new LinkedHashMap<>();
            for (final Map.Entry<Site, Fact> entry : callbackFacts.entrySet()) {
                kinds.put(entry.getKey(), kind(entry.getValue()));
                provenance.put(entry.getKey(), entry.getValue().provenance());
            }
            return new FlowResult(Map.copyOf(kinds), Map.copyOf(provenance));
        }

        private void initializeFacts() {
            for (final EntryPoint entryPoint : reachableMethods) {
                final MethodInfo method = method(entryPoint).orElse(null);
                if (method == null || method.code().isEmpty()) {
                    continue;
                }
                final Fact[] locals = facts(method.code().orElseThrow().maxLocals());
                if (!method.isStatic() && locals.length > 0) {
                    locals[0] = isClosedWorldConcreteCallbackClass(entryPoint.className())
                        ? Fact.concrete(entryPoint.className())
                        : Fact.unsafe();
                }
                if (method.isPublicStaticMain()) {
                    markReferenceParameters(method, locals, Fact.unsafe());
                }
                parameterFacts.put(entryPoint, locals);
                returnFacts.put(entryPoint, Fact.none());
            }
            for (final ClassFile classFile : classes.values()) {
                for (final FieldInfo field : classFile.fields()) {
                    if (isCallbackValueDescriptor(field.descriptor())
                        && (field.accessFlags() & ACC_FINAL) != 0) {
                        finalCallbackFields.add(
                            new FieldRef(classFile.name(), field.name(), field.descriptor())
                        );
                    }
                }
            }
        }

        private void simulate(final EntryPoint entryPoint) {
            final Optional<MethodInfo> resolvedMethod = method(entryPoint);
            if (resolvedMethod.isEmpty() || resolvedMethod.orElseThrow().code().isEmpty()) {
                return;
            }
            final MethodInfo method = resolvedMethod.orElseThrow();
            final CodeAttribute code = method.code().orElseThrow();
            final List<Instruction> bytecode = code.instructions();
            if (bytecode.isEmpty()) {
                return;
            }
            final CachedMethodFlow methodFlow = methodFlows.methodFlow(entryPoint, method, bytecode);
            final BytecodeControlFlow.Result controlFlow = methodFlow.controlFlow();
            if (!controlFlow.valid()) {
                markUnsafe(entryPoint, method);
                return;
            }
            final Map<Integer, Integer> indexes = methodFlow.instructionIndexes();
            final State[] states = new State[bytecode.size()];
            final List<Integer> pending = new ArrayList<>();
            final boolean[] queued = new boolean[bytecode.size()];
            final FlowStatus status = new FlowStatus();
            enqueue(states, pending, queued, 0, initialState(entryPoint, code.maxLocals(), status));
            for (final javan.classfile.CodeException handler : code.exceptionTable()) {
                final Integer handlerIndex = indexes.get(Integer.valueOf(handler.handlerPc()));
                if (handlerIndex != null) {
                    final Fact[] locals = facts(code.maxLocals());
                    for (int local = 0; local < locals.length; local++) {
                        locals[local] = Fact.unsafe();
                    }
                    enqueue(
                        states,
                        pending,
                        queued,
                        handlerIndex.intValue(),
                        new State(locals, List.of(new Slot(Fact.unsafe(), 1)), status)
                    );
                } else {
                    status.reject();
                }
            }
            if (!status.supported()) {
                markUnsafe(entryPoint, method);
                return;
            }

            int pendingIndex = 0;
            while (pendingIndex < pending.size()) {
                final int index = pending.get(pendingIndex).intValue();
                pendingIndex++;
                queued[index] = false;
                final State state = states[index].copy();
                final Instruction instruction = bytecode.get(index);
                final List<Integer> successors = transfer(
                    entryPoint,
                    method,
                    instruction,
                    index,
                    bytecode,
                    controlFlow.graph(),
                    state
                );
                if (!status.supported()) {
                    markUnsafe(entryPoint, method);
                    return;
                }
                for (final Integer successor : successors) {
                    enqueue(states, pending, queued, successor.intValue(), state);
                }
                if (!status.supported()) {
                    markUnsafe(entryPoint, method);
                    return;
                }
            }
        }

        private State initialState(
            final EntryPoint entryPoint,
            final int maxLocals,
            final FlowStatus status
        ) {
            Fact[] source = parameterFacts.get(entryPoint);
            if (source == null) {
                source = facts(maxLocals);
            }
            return new State(java.util.Arrays.copyOf(source, maxLocals), List.of(), status);
        }

        private List<Integer> transfer(
            final EntryPoint entryPoint,
            final MethodInfo method,
            final Instruction instruction,
            final int index,
            final List<Instruction> bytecode,
            final BytecodeControlFlow.Graph controlFlow,
            final State state
        ) {
            final int opcode = instruction.opcode();
            if (opcode == 0 || opcode == 132) {
                return next(index, bytecode);
            }
            if (opcode >= 1 && opcode <= 20) {
                state.stack().add(new Slot(Fact.unsafe(), opcode == 9 || opcode == 10 || opcode == 14
                    || opcode == 15 || opcode == 20 ? 2 : 1));
                return next(index, bytecode);
            }
            if (opcode >= 21 && opcode <= 45) {
                loadLocal(state, instruction);
                return next(index, bytecode);
            }
            if (opcode >= 46 && opcode <= 53) {
                pop(state);
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), opcode == 47 || opcode == 49 ? 2 : 1));
                return next(index, bytecode);
            }
            if (opcode >= 54 && opcode <= 78) {
                storeLocal(state, instruction);
                return next(index, bytecode);
            }
            if (opcode >= 79 && opcode <= 86) {
                pop(state);
                pop(state);
                pop(state);
                return next(index, bytecode);
            }
            if (opcode >= 87 && opcode <= 95) {
                permuteStack(state, opcode);
                return next(index, bytecode);
            }
            if (opcode >= 96 && opcode <= 115) {
                pop(state);
                pop(state);
                final int kind = (opcode - 96) % 4;
                state.stack().add(new Slot(Fact.unsafe(), kind == 1 || kind == 3 ? 2 : 1));
                return next(index, bytecode);
            }
            if (opcode >= 116 && opcode <= 119) {
                final Slot value = pop(state);
                state.stack().add(new Slot(Fact.unsafe(), value.width()));
                return next(index, bytecode);
            }
            if (opcode >= 120 && opcode <= 131) {
                pop(state);
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), opcode % 2 == 1 ? 2 : 1));
                return next(index, bytecode);
            }
            if (opcode >= 133 && opcode <= 147) {
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), conversionWidth(opcode)));
                return next(index, bytecode);
            }
            if (opcode >= 148 && opcode <= 152) {
                pop(state);
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), 1));
                return next(index, bytecode);
            }
            if (opcode >= 153 && opcode <= 166) {
                pop(state);
                if (opcode >= 159) {
                    pop(state);
                }
                return controlFlow.successors(index);
            }
            if (opcode == 167 || opcode == 200) {
                return controlFlow.successors(index);
            }
            if (opcode == 168 || opcode == 169 || opcode == 196 || opcode == 201) {
                state.reject();
                return List.of();
            }
            if (opcode == 170 || opcode == 171) {
                pop(state);
                return controlFlow.successors(index);
            }
            if (opcode >= 172 && opcode <= 176) {
                final Slot value = pop(state);
                if (opcode == 176) {
                    addReturnFact(entryPoint, value.fact());
                }
                return List.of();
            }
            if (opcode == 177) {
                return List.of();
            }
            if (opcode >= 178 && opcode <= 181) {
                field(entryPoint, instruction, state);
                return next(index, bytecode);
            }
            if (opcode >= 182 && opcode <= 185) {
                invoke(entryPoint, instruction, state);
                return next(index, bytecode);
            }
            if (opcode == 186) {
                dynamic(entryPoint, method, instruction, state);
                return next(index, bytecode);
            }
            if (opcode == 187) {
                if (instruction.className().isEmpty()) {
                    state.reject();
                    return List.of();
                }
                final String className = instruction.className().orElseThrow();
                state.stack().add(new Slot(
                    isClosedWorldConcreteCallbackClass(className)
                        ? Fact.concrete(className)
                        : Fact.unsafe(),
                    1
                ));
                return next(index, bytecode);
            }
            if (opcode == 188 || opcode == 189) {
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), 1));
                return next(index, bytecode);
            }
            if (opcode == 190) {
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), 1));
                return next(index, bytecode);
            }
            if (opcode == 191) {
                pop(state);
                return List.of();
            }
            if (opcode == 192) {
                if (instruction.className().isPresent()) {
                    final Slot value = pop(state);
                    state.stack().add(new Slot(
                        narrow(value.fact(), instruction.className().orElseThrow()),
                        value.width()
                    ));
                }
                return next(index, bytecode);
            }
            if (opcode == 193) {
                pop(state);
                state.stack().add(new Slot(Fact.unsafe(), 1));
                return next(index, bytecode);
            }
            if (opcode == 194 || opcode == 195) {
                pop(state);
                return next(index, bytecode);
            }
            if (opcode == 197) {
                final int dimensions = unsignedOperand(instruction, 2, state);
                for (int count = 0; count < dimensions; count++) {
                    pop(state);
                }
                state.stack().add(new Slot(Fact.unsafe(), 1));
                return next(index, bytecode);
            }
            if (opcode == 198 || opcode == 199) {
                pop(state);
                return controlFlow.successors(index);
            }
            state.reject();
            return List.of();
        }

        private void field(
            final EntryPoint entryPoint,
            final Instruction instruction,
            final State state
        ) {
            if (instruction.fieldRef().isEmpty()) {
                state.reject();
                return;
            }
            final FieldRef field = instruction.fieldRef().orElseThrow();
            final Optional<ValueType> resolvedType = fieldType(field.descriptor());
            if (resolvedType.isEmpty()) {
                state.reject();
                return;
            }
            final ValueType type = resolvedType.orElseThrow();
            if (instruction.opcode() == 178) {
                state.stack().add(fieldValue(entryPoint, field, type));
                return;
            }
            if (instruction.opcode() == 179) {
                final Slot value = pop(state);
                addFieldFact(field, value.fact());
                return;
            }
            if (instruction.opcode() == 180) {
                pop(state);
                state.stack().add(fieldValue(entryPoint, field, type));
                return;
            }
            final Slot value = pop(state);
            pop(state);
            addFieldFact(field, value.fact());
        }

        private Slot fieldValue(final EntryPoint reader, final FieldRef field, final ValueType type) {
            if (!type.reference() || !finalCallbackFields.contains(field)) {
                return new Slot(Fact.unsafe(), type.width());
            }
            addDependency(fieldReaders, field, reader);
            return new Slot(fieldFacts.getOrDefault(field, Fact.none()), 1);
        }

        private void addFieldFact(final FieldRef field, final Fact fact) {
            if (!finalCallbackFields.contains(field)) {
                return;
            }
            final Fact current = fieldFacts.getOrDefault(field, Fact.none());
            final Fact merged = current.merge(fact);
            if (merged != current) {
                fieldFacts.put(field, merged);
                enqueueMethods(fieldReaders.get(field));
            }
        }

        private void invoke(
            final EntryPoint caller,
            final Instruction instruction,
            final State state
        ) {
            if (instruction.methodRef().isEmpty()) {
                state.reject();
                return;
            }
            final MethodRef methodRef = instruction.methodRef().orElseThrow();
            final Optional<Descriptor> resolvedDescriptor = methodDescriptor(methodRef.descriptor());
            if (resolvedDescriptor.isEmpty()) {
                state.reject();
                return;
            }
            final Descriptor descriptor = resolvedDescriptor.orElseThrow();
            final List<Slot> arguments = popArguments(state, descriptor.parameters().size());
            final Slot receiver = instruction.opcode() == 184 ? new Slot(Fact.unsafe(), 1) : pop(state);
            if (instruction.opcode() == 185 && directUse(callbackKind).equals(methodRef)) {
                addCallbackFact(site(caller, instruction), receiver.fact());
                pushReturn(state, descriptor.result(), Fact.unsafe());
                return;
            }
            if (isIndirectUse(callbackKind, methodRef) && !arguments.isEmpty()) {
                addCallbackFact(site(caller, instruction), arguments.getLast().fact());
            }
            if (isRequireNonNull(methodRef) && !arguments.isEmpty()) {
                pushReturn(state, descriptor.result(), arguments.getFirst().fact());
                return;
            }
            final Optional<EntryPoint> target = exactTarget(instruction, methodRef);
            if (target.isPresent()) {
                addDependency(callers, target.orElseThrow(), caller);
                contributeCall(target.orElseThrow(), receiver, arguments, state);
                pushReturn(
                    state,
                    descriptor.result(),
                    returnFacts.getOrDefault(target.orElseThrow(), Fact.none())
                );
                return;
            }
            pushReturn(state, descriptor.result(), Fact.unsafe());
        }

        private void dynamic(
            final EntryPoint caller,
            final MethodInfo method,
            final Instruction instruction,
            final State state
        ) {
            if (instruction.dynamicRef().isEmpty()) {
                state.reject();
                return;
            }
            final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow();
            final Optional<Descriptor> resolvedDescriptor = methodDescriptor(dynamicRef.descriptor());
            if (resolvedDescriptor.isEmpty()) {
                state.reject();
                return;
            }
            final Descriptor descriptor = resolvedDescriptor.orElseThrow();
            final List<Slot> captures = popArguments(state, descriptor.parameters().size());
            final Optional<LambdaMetafactoryCall> lambda = LambdaMetafactoryCall.resolve(dynamicRef);
            if (lambda.isEmpty()) {
                pushReturn(state, descriptor.result(), Fact.unsafe());
                return;
            }
            final LambdaMetafactoryCall resolved = lambda.orElseThrow();
            contributeLambdaInvocation(resolved, captures, state);
            final boolean materialized = isMaterializedCallback(resolved, method, instruction);
            pushReturn(
                state,
                descriptor.result(),
                materialized ? Fact.materialized() : Fact.unsafe()
            );
        }

        private boolean isMaterializedCallback(
            final LambdaMetafactoryCall lambda,
            final MethodInfo method,
            final Instruction instruction
        ) {
            if (callbackKind == CallbackKind.FUNCTION) {
                return lambda.isMaterializedFunctionLambda(classes)
                    && FunctionLambdaUse.requiresMaterialization(method, instruction)
                    && !FunctionLambdaUse.isProvablyDiscardedZeroCapture(lambda, method, instruction);
            }
            final ClassFile implementationClass = classes.get(lambda.implementation().owner());
            return implementationClass != null
                && implementationClass.application()
                && lambda.isMaterializedSupplierLambda()
                && shouldMaterializeSupplierLambda(method, instruction);
        }

        private boolean shouldMaterializeSupplierLambda(
            final MethodInfo method,
            final Instruction instruction
        ) {
            if (method.code().isEmpty()) {
                return true;
            }
            final List<Instruction> instructions = method.code().orElseThrow().instructions();
            for (int index = 0; index + 1 < instructions.size(); index++) {
                if (instructions.get(index).offset() != instruction.offset()) {
                    continue;
                }
                final Optional<MethodRef> consumer = instructions.get(index + 1).methodRef();
                return consumer.isEmpty() || !isInlineSupplierConsumer(consumer.orElseThrow());
            }
            return true;
        }

        private void contributeLambdaInvocation(
            final LambdaMetafactoryCall lambda,
            final List<Slot> captures,
            final State state
        ) {
            final MethodRef implementation = lambda.implementation();
            final EntryPoint target = new EntryPoint(
                implementation.owner(),
                implementation.name(),
                implementation.descriptor()
            );
            if (!reachableSet.contains(target)) {
                return;
            }
            final Optional<MethodInfo> resolvedMethod = method(target);
            if (resolvedMethod.isEmpty()) {
                state.reject();
                return;
            }
            final MethodInfo method = resolvedMethod.orElseThrow();
            if (method.code().isEmpty()) {
                if (isDeclaredExternalLeaf(target, method)) {
                    return;
                }
                state.reject();
                return;
            }
            final Optional<Descriptor> resolvedDescriptor = methodDescriptor(method.descriptor());
            if (resolvedDescriptor.isEmpty()) {
                state.reject();
                return;
            }
            final Descriptor descriptor = resolvedDescriptor.orElseThrow();
            final Fact[] contributions = facts(method.code().orElseThrow().maxLocals());
            int local = method.isStatic() ? 0 : 1;
            int captureIndex = 0;
            if (!method.isStatic() && !captures.isEmpty()) {
                if (contributions.length == 0) {
                    state.reject();
                    return;
                }
                contributions[0] = captures.getFirst().fact();
                captureIndex = 1;
            }
            for (final ValueType parameter : descriptor.parameters()) {
                final Fact fact = captureIndex < captures.size()
                    ? captures.get(captureIndex).fact()
                    : Fact.unsafe();
                if (local < 0 || local >= contributions.length) {
                    state.reject();
                    return;
                }
                contributions[local] = contributions[local].merge(fact);
                local += parameter.width();
                captureIndex++;
            }
            addParameterFacts(target, contributions);
        }

        private Optional<EntryPoint> exactTarget(
            final Instruction instruction,
            final MethodRef methodRef
        ) {
            final ClassFile owner = classes.get(methodRef.owner());
            if (owner == null || owner.method(methodRef.name(), methodRef.descriptor()).isEmpty()) {
                return Optional.empty();
            }
            final MethodInfo method = owner.method(methodRef.name(), methodRef.descriptor()).orElseThrow();
            if (instruction.opcode() != 184
                && instruction.opcode() != 183
                && !(instruction.opcode() == 182
                    && (owner.isFinal()
                        || (method.accessFlags() & (ACC_FINAL | ACC_PRIVATE)) != 0))) {
                return Optional.empty();
            }
            final EntryPoint target = new EntryPoint(methodRef.owner(), methodRef.name(), methodRef.descriptor());
            return reachableSet.contains(target) ? Optional.of(target) : Optional.empty();
        }

        private void contributeCall(
            final EntryPoint target,
            final Slot receiver,
            final List<Slot> arguments,
            final State state
        ) {
            final Optional<MethodInfo> resolvedTarget = method(target);
            if (resolvedTarget.isEmpty()) {
                state.reject();
                return;
            }
            final MethodInfo targetMethod = resolvedTarget.orElseThrow();
            if (targetMethod.code().isEmpty()) {
                if (isDeclaredExternalLeaf(target, targetMethod)) {
                    return;
                }
                state.reject();
                return;
            }
            final Optional<Descriptor> resolvedDescriptor = methodDescriptor(targetMethod.descriptor());
            if (resolvedDescriptor.isEmpty()) {
                state.reject();
                return;
            }
            final Descriptor descriptor = resolvedDescriptor.orElseThrow();
            final Fact[] contributions = facts(targetMethod.code().orElseThrow().maxLocals());
            int local = 0;
            if (!targetMethod.isStatic()) {
                if (contributions.length == 0) {
                    state.reject();
                    return;
                }
                contributions[0] = receiver.fact();
                local = 1;
            }
            for (int index = 0; index < descriptor.parameters().size(); index++) {
                if (local < 0 || local >= contributions.length) {
                    state.reject();
                    return;
                }
                contributions[local] = contributions[local].merge(arguments.get(index).fact());
                local += descriptor.parameters().get(index).width();
            }
            addParameterFacts(target, contributions);
        }

        private boolean isDeclaredExternalLeaf(final EntryPoint target, final MethodInfo method) {
            return reachableSet.contains(target)
                && declaredExternalLeaves.contains(target)
                && method.isNative()
                && method.isStatic();
        }

        private void addParameterFacts(final EntryPoint target, final Fact[] contribution) {
            final Fact[] current = parameterFacts.get(target);
            if (current == null) {
                return;
            }
            boolean updated = false;
            for (int index = 0; index < current.length && index < contribution.length; index++) {
                final Fact merged = current[index].merge(contribution[index]);
                if (merged != current[index]) {
                    current[index] = merged;
                    updated = true;
                }
            }
            if (updated) {
                enqueueMethod(target);
            }
        }

        private void addReturnFact(final EntryPoint entryPoint, final Fact fact) {
            final Fact current = returnFacts.getOrDefault(entryPoint, Fact.none());
            final Fact merged = current.merge(fact);
            if (merged != current) {
                returnFacts.put(entryPoint, merged);
                enqueueMethods(callers.get(entryPoint));
            }
        }

        private void addCallbackFact(final Site site, final Fact fact) {
            final Fact current = callbackFacts.getOrDefault(site, Fact.none());
            final Fact merged = current.merge(fact);
            if (merged != current) {
                callbackFacts.put(site, merged);
            }
        }

        private void enqueueMethod(final EntryPoint entryPoint) {
            if (queuedMethods.add(entryPoint)) {
                pendingMethods.add(entryPoint);
            }
        }

        private void enqueueMethods(final List<EntryPoint> methods) {
            if (methods == null) {
                return;
            }
            for (final EntryPoint method : methods) {
                enqueueMethod(method);
            }
        }

        private static <K> void addDependency(
            final Map<K, List<EntryPoint>> dependencies,
            final K source,
            final EntryPoint dependent
        ) {
            List<EntryPoint> methods = dependencies.get(source);
            if (methods == null) {
                methods = new ArrayList<>();
                dependencies.put(source, methods);
            }
            if (!methods.contains(dependent)) {
                methods.add(dependent);
            }
        }

        private void markUnsafe(final EntryPoint entryPoint, final MethodInfo method) {
            if (referenceResult(method.descriptor())) {
                addReturnFact(entryPoint, Fact.unsafe());
            }
            if (method.code().isEmpty()) {
                return;
            }
            for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                if (instruction.fieldRef().isPresent()
                    && (instruction.opcode() == 179 || instruction.opcode() == 181)) {
                    addFieldFact(instruction.fieldRef().orElseThrow(), Fact.unsafe());
                }
                if (instruction.methodRef().isPresent()
                    && isTrackedUse(callbackKind, instruction.methodRef().orElseThrow())) {
                    addCallbackFact(site(entryPoint, instruction), Fact.unsafe());
                }
                if (instruction.methodRef().isPresent()) {
                    for (final EntryPoint target : unsafeCallTargets(
                        instruction,
                        instruction.methodRef().orElseThrow()
                    )) {
                        markParametersUnsafe(target);
                    }
                }
                if (instruction.dynamicRef().isPresent()) {
                    final Optional<LambdaMetafactoryCall> lambda =
                        LambdaMetafactoryCall.resolve(instruction.dynamicRef().orElseThrow());
                    if (lambda.isPresent()) {
                        final MethodRef implementation = lambda.orElseThrow().implementation();
                        final EntryPoint target = new EntryPoint(
                            implementation.owner(),
                            implementation.name(),
                            implementation.descriptor()
                        );
                        if (reachableSet.contains(target)) {
                            markParametersUnsafe(target);
                        }
                    }
                }
            }
        }

        private List<EntryPoint> unsafeCallTargets(
            final Instruction instruction,
            final MethodRef methodRef
        ) {
            final Optional<EntryPoint> exact = exactTarget(instruction, methodRef);
            if (exact.isPresent()) {
                return List.of(exact.orElseThrow());
            }
            if (instruction.opcode() != 182 && instruction.opcode() != 185) {
                return List.of();
            }
            final List<EntryPoint> result = new ArrayList<>();
            for (final EntryPoint candidate : reachableMethods) {
                if (candidate.methodName().equals(methodRef.name())
                    && candidate.descriptor().equals(methodRef.descriptor())
                    && (isAssignableTo(candidate.className(), methodRef.owner())
                        || isAssignableTo(methodRef.owner(), candidate.className()))) {
                    result.add(candidate);
                }
            }
            return List.copyOf(result);
        }

        private void markParametersUnsafe(final EntryPoint target) {
            final Fact[] current = parameterFacts.get(target);
            final Optional<MethodInfo> resolvedMethod = method(target);
            if (current == null || resolvedMethod.isEmpty()) {
                return;
            }
            final Fact[] contribution = facts(current.length);
            final MethodInfo method = resolvedMethod.orElseThrow();
            final Optional<Descriptor> resolvedDescriptor = methodDescriptor(method.descriptor());
            if (resolvedDescriptor.isEmpty()) {
                for (int local = 0; local < contribution.length; local++) {
                    contribution[local] = Fact.unsafe();
                }
                addParameterFacts(target, contribution);
                return;
            }
            int local = 0;
            if (!method.isStatic()) {
                if (contribution.length == 0) {
                    return;
                }
                contribution[0] = Fact.unsafe();
                local = 1;
            }
            for (final ValueType parameter : resolvedDescriptor.orElseThrow().parameters()) {
                if (local < 0 || local >= contribution.length) {
                    for (int index = 0; index < contribution.length; index++) {
                        contribution[index] = Fact.unsafe();
                    }
                    addParameterFacts(target, contribution);
                    return;
                }
                if (parameter.reference()) {
                    contribution[local] = Fact.unsafe();
                }
                local += parameter.width();
            }
            addParameterFacts(target, contribution);
        }

        private boolean isAssignableTo(final String candidate, final String target) {
            final List<String> pending = new ArrayList<>();
            final Set<String> visited = new LinkedHashSet<>();
            pending.add(candidate);
            int index = 0;
            while (index < pending.size()) {
                final String current = pending.get(index);
                index++;
                if (!visited.add(current)) {
                    continue;
                }
                if (target.equals(current)) {
                    return true;
                }
                final ClassFile classFile = classes.get(current);
                if (classFile == null) {
                    continue;
                }
                if (!classFile.superName().isEmpty()) {
                    pending.add(classFile.superName());
                }
                pending.addAll(classFile.interfaces());
            }
            return false;
        }

        private Fact narrow(final Fact fact, final String target) {
            if (fact.unknown() || fact.types().isEmpty()) {
                return fact;
            }
            final List<String> types = new ArrayList<>();
            for (final String type : fact.types()) {
                if (isAssignableTo(type, target)) {
                    types.add(type);
                }
            }
            return types.isEmpty()
                ? new Fact(fact.kinds(), List.of(), true)
                : new Fact(fact.kinds(), List.copyOf(types), false);
        }

        private Optional<MethodInfo> method(final EntryPoint entryPoint) {
            final ClassFile classFile = classes.get(entryPoint.className());
            return classFile == null
                ? Optional.empty()
                : classFile.method(entryPoint.methodName(), entryPoint.descriptor());
        }

        private boolean isCallbackValueDescriptor(final String descriptor) {
            if (callbackDescriptor(callbackKind).equals(descriptor)) {
                return true;
            }
            return descriptor.startsWith("L")
                && descriptor.endsWith(";")
                && isAssignableToCallback(descriptor.substring(1, descriptor.length() - 1));
        }

        private boolean isClosedWorldConcreteCallbackClass(final String className) {
            final ClassFile classFile = classes.get(className);
            return classFile != null
                && !classFile.isInterface()
                && !className.startsWith("java/")
                && isAssignableToCallback(className);
        }

        private boolean isAssignableToCallback(final String candidate) {
            final Boolean cached = callbackTypeCache.get(candidate);
            if (cached != null) {
                return cached.booleanValue();
            }
            final List<String> pending = new ArrayList<>();
            final Set<String> visited = new LinkedHashSet<>();
            pending.add(candidate);
            int index = 0;
            while (index < pending.size()) {
                final String current = pending.get(index);
                index++;
                if (!visited.add(current)) {
                    continue;
                }
                if (callbackOwner(callbackKind).equals(current)) {
                    callbackTypeCache.put(candidate, Boolean.TRUE);
                    return true;
                }
                final ClassFile classFile = classes.get(current);
                if (classFile == null) {
                    continue;
                }
                if (!classFile.superName().isEmpty()) {
                    pending.add(classFile.superName());
                }
                pending.addAll(classFile.interfaces());
            }
            callbackTypeCache.put(candidate, Boolean.FALSE);
            return false;
        }
    }

    private static void enqueue(
        final State[] states,
        final List<Integer> pending,
        final boolean[] queued,
        final int index,
        final State incoming
    ) {
        if (index < 0 || index >= states.length) {
            incoming.reject();
            return;
        }
        if (states[index] == null) {
            states[index] = incoming.copy();
            if (!queued[index]) {
                pending.add(Integer.valueOf(index));
                queued[index] = true;
            }
            return;
        }
        if (states[index].merge(incoming) && !queued[index]) {
            pending.add(Integer.valueOf(index));
            queued[index] = true;
        }
    }

    private static void loadLocal(final State state, final Instruction instruction) {
        final int opcode = instruction.opcode();
        final int local = opcode <= 25
            ? unsignedOperand(instruction, 0, state)
            : opcode <= 29 ? opcode - 26
            : opcode <= 33 ? opcode - 30
            : opcode <= 37 ? opcode - 34
            : opcode <= 41 ? opcode - 38
            : opcode - 42;
        final boolean reference = opcode == 25 || opcode >= 42;
        final int width = opcode == 22 || opcode == 24 || opcode >= 30 && opcode <= 33
            || opcode >= 38 && opcode <= 41 ? 2 : 1;
        state.stack().add(new Slot(reference ? localFact(state, local) : Fact.unsafe(), width));
    }

    private static void storeLocal(final State state, final Instruction instruction) {
        final int opcode = instruction.opcode();
        final int local = opcode <= 58
            ? unsignedOperand(instruction, 0, state)
            : opcode <= 62 ? opcode - 59
            : opcode <= 66 ? opcode - 63
            : opcode <= 70 ? opcode - 67
            : opcode <= 74 ? opcode - 71
            : opcode - 75;
        final Slot value = pop(state);
        if (local < 0 || local >= state.locals().length) {
            state.reject();
            return;
        }
        state.locals()[local] = opcode == 58 || opcode >= 75 ? value.fact() : Fact.unsafe();
    }

    private static Fact localFact(final State state, final int local) {
        if (local < 0 || local >= state.locals().length) {
            state.reject();
            return Fact.unsafe();
        }
        return state.locals()[local];
    }

    private static void permuteStack(final State state, final int opcode) {
        if (opcode == 87) {
            final Slot value = pop(state);
            if (value.width() != 1) {
                state.reject();
            }
            return;
        }
        if (opcode == 88) {
            final Slot first = pop(state);
            if (first.width() == 1) {
                final Slot second = pop(state);
                if (second.width() != 1) {
                    state.reject();
                }
            }
            return;
        }
        if (opcode == 89) {
            final Slot first = popCategoryOne(state);
            state.stack().add(first);
            state.stack().add(first);
            return;
        }
        if (opcode == 90) {
            final Slot first = popCategoryOne(state);
            final Slot second = popCategoryOne(state);
            state.stack().add(first);
            state.stack().add(second);
            state.stack().add(first);
            return;
        }
        if (opcode == 91) {
            final Slot first = popCategoryOne(state);
            final Slot second = pop(state);
            if (second.width() == 2) {
                state.stack().add(first);
                state.stack().add(second);
                state.stack().add(first);
                return;
            }
            final Slot third = popCategoryOne(state);
            state.stack().add(first);
            state.stack().add(third);
            state.stack().add(second);
            state.stack().add(first);
            return;
        }
        if (opcode == 92) {
            final Slot first = pop(state);
            if (first.width() == 2) {
                state.stack().add(first);
                state.stack().add(first);
                return;
            }
            final Slot second = popCategoryOne(state);
            state.stack().add(second);
            state.stack().add(first);
            state.stack().add(second);
            state.stack().add(first);
            return;
        }
        if (opcode == 95) {
            final Slot first = popCategoryOne(state);
            final Slot second = popCategoryOne(state);
            state.stack().add(first);
            state.stack().add(second);
            return;
        }
        state.reject();
    }

    private static Slot popCategoryOne(final State state) {
        final Slot value = pop(state);
        if (value.width() != 1) {
            state.reject();
        }
        return value;
    }

    private static Slot pop(final State state) {
        if (state.stack().isEmpty()) {
            state.reject();
            return new Slot(Fact.unsafe(), 1);
        }
        return state.stack().removeLast();
    }

    private static List<Slot> popArguments(final State state, final int count) {
        final List<Slot> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.addFirst(pop(state));
        }
        return result;
    }

    private static void pushReturn(
        final State state,
        final ValueType result,
        final Fact fact
    ) {
        if (result.width() > 0) {
            state.stack().add(new Slot(result.reference() ? fact : Fact.unsafe(), result.width()));
        }
    }

    private static List<Integer> next(final int index, final List<Instruction> bytecode) {
        return index + 1 < bytecode.size() ? List.of(Integer.valueOf(index + 1)) : List.of();
    }

    private static Map<Integer, Integer> instructionIndexes(final List<Instruction> bytecode) {
        final Map<Integer, Integer> result = new HashMap<>();
        for (int index = 0; index < bytecode.size(); index++) {
            result.put(Integer.valueOf(bytecode.get(index).offset()), Integer.valueOf(index));
        }
        return result;
    }

    private static int conversionWidth(final int opcode) {
        return switch (opcode) {
            case 133, 135, 140, 141 -> 2;
            case 134, 136, 137, 139, 142, 144, 145, 146, 147 -> 1;
            case 138, 143 -> 2;
            default -> 1;
        };
    }

    private static boolean isRequireNonNull(final MethodRef methodRef) {
        if (!"java/util/Objects".equals(methodRef.owner())
            || !"requireNonNull".equals(methodRef.name())) {
            return false;
        }
        return "(Ljava/lang/Object;)Ljava/lang/Object;".equals(methodRef.descriptor())
            || "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;".equals(methodRef.descriptor());
    }

    private static boolean isTrackedUse(
        final CallbackKind callbackKind,
        final MethodRef methodRef
    ) {
        return directUse(callbackKind).equals(methodRef) || isIndirectUse(callbackKind, methodRef);
    }

    private static MethodRef directUse(final CallbackKind callbackKind) {
        return callbackKind == CallbackKind.FUNCTION ? FUNCTION_APPLY : SUPPLIER_GET;
    }

    private static String callbackDescriptor(final CallbackKind callbackKind) {
        return callbackKind == CallbackKind.FUNCTION ? FUNCTION_DESCRIPTOR : SUPPLIER_DESCRIPTOR;
    }

    private static String callbackOwner(final CallbackKind callbackKind) {
        return directUse(callbackKind).owner();
    }

    private static boolean isIndirectUse(
        final CallbackKind callbackKind,
        final MethodRef methodRef
    ) {
        return callbackKind == CallbackKind.FUNCTION
            ? isIndirectFunctionUse(methodRef)
            : isIndirectSupplierUse(methodRef);
    }

    private static boolean isIndirectFunctionUse(final MethodRef methodRef) {
        if ("computeIfAbsent".equals(methodRef.name())
            && "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(methodRef.descriptor())
            && ("java/util/Map".equals(methodRef.owner())
                || "java/util/HashMap".equals(methodRef.owner())
                || "java/util/LinkedHashMap".equals(methodRef.owner())
                || "java/util/TreeMap".equals(methodRef.owner()))) {
            return true;
        }
        return "java/util/Optional".equals(methodRef.owner())
            && ("map".equals(methodRef.name()) || "flatMap".equals(methodRef.name()))
            && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(methodRef.descriptor());
    }

    private static boolean isIndirectSupplierUse(final MethodRef methodRef) {
        if ("java/util/Optional".equals(methodRef.owner())) {
            return ("or".equals(methodRef.name())
                    && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(methodRef.descriptor()))
                || ("orElseGet".equals(methodRef.name())
                    && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(methodRef.descriptor()));
        }
        return "java/util/Objects".equals(methodRef.owner())
            && "requireNonNullElseGet".equals(methodRef.name())
            && "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(methodRef.descriptor());
    }

    private static boolean isInlineSupplierConsumer(final MethodRef methodRef) {
        return SUPPLIER_GET.equals(methodRef) || isIndirectSupplierUse(methodRef);
    }

    private static Site site(final EntryPoint entryPoint, final Instruction instruction) {
        return new Site(
            entryPoint.className(),
            entryPoint.methodName(),
            entryPoint.descriptor(),
            instruction.offset()
        );
    }

    private static ValueKind kind(final Fact fact) {
        return switch (fact.kinds()) {
            case 0 -> ValueKind.UNKNOWN;
            case MATERIALIZED -> ValueKind.MATERIALIZED;
            case CONCRETE -> ValueKind.CONCRETE;
            case UNSAFE -> ValueKind.UNSAFE;
            case MATERIALIZED | CONCRETE -> ValueKind.MATERIALIZED_AND_CONCRETE;
            default -> ValueKind.MIXED;
        };
    }

    private static Fact[] facts(final int size) {
        final Fact[] result = new Fact[size];
        for (int index = 0; index < result.length; index++) {
            result[index] = Fact.none();
        }
        return result;
    }

    private static void markReferenceParameters(
        final MethodInfo method,
        final Fact[] locals,
        final Fact fact
    ) {
        final Optional<Descriptor> resolvedDescriptor = methodDescriptor(method.descriptor());
        if (resolvedDescriptor.isEmpty()) {
            for (int local = 0; local < locals.length; local++) {
                locals[local] = fact;
            }
            return;
        }
        final Descriptor descriptor = resolvedDescriptor.orElseThrow();
        int local = method.isStatic() ? 0 : 1;
        for (final ValueType parameter : descriptor.parameters()) {
            if (parameter.reference()) {
                if (local >= 0 && local < locals.length) {
                    locals[local] = locals[local].merge(fact);
                }
            }
            local += parameter.width();
        }
    }

    private static boolean referenceResult(final String descriptor) {
        final Optional<Descriptor> resolved = methodDescriptor(descriptor);
        return resolved.isEmpty() || resolved.orElseThrow().result().reference();
    }

    private static Optional<ValueType> fieldType(final String descriptor) {
        final Optional<ParsedType> resolved = parseType(descriptor, 0, false);
        if (resolved.isEmpty() || resolved.orElseThrow().nextIndex() != descriptor.length()) {
            return Optional.empty();
        }
        return Optional.of(resolved.orElseThrow().type());
    }

    private static Optional<Descriptor> methodDescriptor(final String descriptor) {
        if (descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            return Optional.empty();
        }
        final List<ValueType> parameters = new ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final Optional<ParsedType> resolved = parseType(descriptor, index, false);
            if (resolved.isEmpty()) {
                return Optional.empty();
            }
            final ParsedType parsed = resolved.orElseThrow();
            parameters.add(parsed.type());
            index = parsed.nextIndex();
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            return Optional.empty();
        }
        final Optional<ParsedType> resolvedResult = parseType(descriptor, index + 1, true);
        if (resolvedResult.isEmpty()
            || resolvedResult.orElseThrow().nextIndex() != descriptor.length()) {
            return Optional.empty();
        }
        return Optional.of(new Descriptor(
            List.copyOf(parameters),
            resolvedResult.orElseThrow().type()
        ));
    }

    private static Optional<ParsedType> parseType(
        final String descriptor,
        final int start,
        final boolean allowVoid
    ) {
        if (start >= descriptor.length()) {
            return Optional.empty();
        }
        final char type = descriptor.charAt(start);
        if (type == 'V' && allowVoid) {
            return Optional.of(new ParsedType(new ValueType(0, false), start + 1));
        }
        if ("BCFISZ".indexOf(type) >= 0) {
            return Optional.of(new ParsedType(new ValueType(1, false), start + 1));
        }
        if (type == 'J' || type == 'D') {
            return Optional.of(new ParsedType(new ValueType(2, false), start + 1));
        }
        if (type == 'L') {
            final int end = descriptor.indexOf(';', start);
            if (end < 0) {
                return Optional.empty();
            }
            return Optional.of(new ParsedType(new ValueType(1, true), end + 1));
        }
        if (type == '[') {
            int index = start;
            while (index < descriptor.length() && descriptor.charAt(index) == '[') {
                index++;
            }
            final Optional<ParsedType> component = parseType(descriptor, index, false);
            if (component.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ParsedType(
                new ValueType(1, true),
                component.orElseThrow().nextIndex()
            ));
        }
        return Optional.empty();
    }

    private static int unsigned(final byte value) {
        return value & 0xFF;
    }

    private static int unsignedOperand(
        final Instruction instruction,
        final int index,
        final State state
    ) {
        if (index < 0 || index >= instruction.operands().length) {
            state.reject();
            return -1;
        }
        return unsigned(instruction.operands()[index]);
    }

    private record Descriptor(List<ValueType> parameters, ValueType result) {
    }

    private record ValueType(int width, boolean reference) {
    }

    private record ParsedType(ValueType type, int nextIndex) {
    }

    private record FlowResult(Map<Site, ValueKind> kinds, Map<Site, Provenance> provenance) {
        private static FlowResult empty() {
            return new FlowResult(Map.of(), Map.of());
        }
    }

    private record Fact(int kinds, List<String> types, boolean unknown) {
        private Fact {
            types = List.copyOf(types);
        }

        private static Fact none() {
            return NONE_FACT;
        }

        private static Fact materialized() {
            return MATERIALIZED_FACT;
        }

        private static Fact concrete(final String type) {
            return new Fact(CONCRETE, List.of(type), false);
        }

        private static Fact unsafe() {
            return UNSAFE_FACT;
        }

        private Fact merge(final Fact other) {
            if (kinds == 0 && types.isEmpty() && !unknown) {
                return other;
            }
            if (other.kinds == 0 && other.types.isEmpty() && !other.unknown) {
                return this;
            }
            final int mergedKinds = kinds | other.kinds;
            if (unknown || other.unknown) {
                if (mergedKinds == kinds && unknown && types.isEmpty()) {
                    return this;
                }
                return new Fact(mergedKinds, List.of(), true);
            }
            if (other.types.isEmpty()) {
                return mergedKinds == kinds ? this : new Fact(mergedKinds, types, false);
            }
            if (types.isEmpty()) {
                return mergedKinds == other.kinds ? other : new Fact(mergedKinds, other.types, false);
            }
            final List<String> mergedTypes = new ArrayList<>(types);
            boolean typesChanged = false;
            for (final String type : other.types) {
                int index = 0;
                while (index < mergedTypes.size()
                    && Strings2.compareAscii(mergedTypes.get(index), type) < 0) {
                    index++;
                }
                if (index >= mergedTypes.size() || !mergedTypes.get(index).equals(type)) {
                    mergedTypes.add(index, type);
                    typesChanged = true;
                    if (mergedTypes.size() > MAX_EXACT_TYPES) {
                        return new Fact(mergedKinds, List.of(), true);
                    }
                }
            }
            return mergedKinds == kinds && !typesChanged
                ? this
                : new Fact(mergedKinds, List.copyOf(mergedTypes), false);
        }

        private Provenance provenance() {
            return new Provenance(types, unknown || kinds == 0);
        }
    }

    private record Slot(Fact fact, int width) {
    }

    private enum CallbackKind {
        FUNCTION,
        SUPPLIER
    }

    private static final class FlowStatus {
        private boolean supported = true;

        private boolean supported() {
            return supported;
        }

        private void reject() {
            supported = false;
        }
    }

    private static final class State {
        private final Fact[] locals;
        private final List<Slot> stack;
        private final FlowStatus status;

        private State(
            final Fact[] locals,
            final List<Slot> stack,
            final FlowStatus status
        ) {
            this.locals = java.util.Arrays.copyOf(locals, locals.length);
            this.stack = new ArrayList<>(stack);
            this.status = status;
        }

        private Fact[] locals() {
            return locals;
        }

        private List<Slot> stack() {
            return stack;
        }

        private boolean supported() {
            return status.supported();
        }

        private void reject() {
            status.reject();
        }

        private State copy() {
            return new State(locals, stack, status);
        }

        private boolean merge(final State incoming) {
            if (locals.length != incoming.locals.length || stack.size() != incoming.stack.size()) {
                reject();
                return false;
            }
            boolean changed = false;
            for (int index = 0; index < locals.length; index++) {
                final Fact merged = locals[index].merge(incoming.locals[index]);
                if (merged != locals[index]) {
                    locals[index] = merged;
                    changed = true;
                }
            }
            for (int index = 0; index < stack.size(); index++) {
                final Slot current = stack.get(index);
                final Slot other = incoming.stack.get(index);
                if (current.width() != other.width()) {
                    reject();
                    return false;
                }
                final Fact merged = current.fact().merge(other.fact());
                if (merged != current.fact()) {
                    stack.set(index, new Slot(merged, current.width()));
                    changed = true;
                }
            }
            return changed;
        }
    }
}
