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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Conservatively proves which reachable callback uses receive materialized lambdas.
 */
public final class FunctionValueFlow {
    private static final int MATERIALIZED = 1;
    private static final int CONCRETE = 2;
    private static final int UNSAFE = 4;
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
     * Immutable whole-program callback-flow result.
     *
     * @param functionKinds Function value classification by exact use site
     * @param supplierKinds Supplier value classification by exact use site
     * @param complete whether whole-program callback-flow analysis was available
     */
    public record Result(
        Map<Site, ValueKind> functionKinds,
        Map<Site, ValueKind> supplierKinds,
        boolean complete
    ) {
        /**
         * Creates a complete result.
         *
         * @param functionKinds Function value classification by exact use site
         */
        public Result(final Map<Site, ValueKind> functionKinds) {
            this(functionKinds, Map.of(), true);
        }

        /**
         * Creates a result with Function facts only.
         *
         * @param functionKinds Function value classification by exact use site
         * @param complete whether whole-program callback-flow analysis was available
         */
        public Result(final Map<Site, ValueKind> functionKinds, final boolean complete) {
            this(functionKinds, Map.of(), complete);
        }

        public Result {
            functionKinds = Map.copyOf(functionKinds);
            supplierKinds = Map.copyOf(supplierKinds);
        }

        /**
         * Creates a marker for call graphs assembled without function-flow analysis.
         *
         * @return unavailable result
         */
        public static Result unavailable() {
            return new Result(Map.of(), Map.of(), false);
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
        final Map<Site, ValueKind> functionKinds = containsTrackedUse(
            classes,
            reachableMethods,
            CallbackKind.FUNCTION
        ) ? new Engine(classes, reachableMethods, declaredExternalLeaves, CallbackKind.FUNCTION).analyze() : Map.of();
        final Map<Site, ValueKind> supplierKinds = containsTrackedUse(
            classes,
            reachableMethods,
            CallbackKind.SUPPLIER
        ) ? new Engine(classes, reachableMethods, declaredExternalLeaves, CallbackKind.SUPPLIER).analyze() : Map.of();
        return new Result(functionKinds, supplierKinds, true);
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

    private static final class Engine {
        private final Map<String, ClassFile> classes;
        private final List<EntryPoint> reachableMethods;
        private final Set<EntryPoint> reachableSet;
        private final Set<EntryPoint> declaredExternalLeaves;
        private final CallbackKind callbackKind;
        private final Map<EntryPoint, int[]> parameterFacts = new LinkedHashMap<>();
        private final Map<EntryPoint, Integer> returnFacts = new LinkedHashMap<>();
        private final Set<FieldRef> finalCallbackFields = new LinkedHashSet<>();
        private final Map<FieldRef, Integer> fieldFacts = new LinkedHashMap<>();
        private final Map<Site, Integer> callbackFacts = new LinkedHashMap<>();
        private final Map<String, Boolean> callbackTypeCache = new HashMap<>();
        private boolean changed;

        private Engine(
            final Map<String, ClassFile> classes,
            final List<EntryPoint> reachableMethods,
            final List<EntryPoint> declaredExternalLeaves,
            final CallbackKind callbackKind
        ) {
            this.classes = classes;
            this.reachableMethods = List.copyOf(reachableMethods);
            this.reachableSet = Set.copyOf(reachableMethods);
            this.declaredExternalLeaves = Set.copyOf(declaredExternalLeaves);
            this.callbackKind = callbackKind;
            initializeFacts();
        }

        private Map<Site, ValueKind> analyze() {
            do {
                changed = false;
                for (final EntryPoint entryPoint : reachableMethods) {
                    simulate(entryPoint);
                }
            } while (changed);
            final Map<Site, ValueKind> result = new LinkedHashMap<>();
            for (final Map.Entry<Site, Integer> entry : callbackFacts.entrySet()) {
                result.put(entry.getKey(), kind(entry.getValue().intValue()));
            }
            return Map.copyOf(result);
        }

        private void initializeFacts() {
            for (final EntryPoint entryPoint : reachableMethods) {
                final MethodInfo method = method(entryPoint).orElse(null);
                if (method == null || method.code().isEmpty()) {
                    continue;
                }
                final int[] locals = new int[method.code().orElseThrow().maxLocals()];
                if (!method.isStatic() && locals.length > 0) {
                    locals[0] = isClosedWorldConcreteCallbackClass(entryPoint.className())
                        ? CONCRETE
                        : UNSAFE;
                }
                if (method.isPublicStaticMain()) {
                    markReferenceParameters(method, locals, UNSAFE);
                }
                parameterFacts.put(entryPoint, locals);
                returnFacts.put(entryPoint, Integer.valueOf(0));
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
            final BytecodeControlFlow.Result controlFlow = BytecodeControlFlow.analyze(method);
            if (!controlFlow.valid()) {
                markUnsafe(entryPoint, method);
                return;
            }
            final Map<Integer, Integer> indexes = instructionIndexes(bytecode);
            final State[] states = new State[bytecode.size()];
            final List<Integer> pending = new ArrayList<>();
            final boolean[] queued = new boolean[bytecode.size()];
            final FlowStatus status = new FlowStatus();
            enqueue(states, pending, queued, 0, initialState(entryPoint, code.maxLocals(), status));
            for (final javan.classfile.CodeException handler : code.exceptionTable()) {
                final Integer handlerIndex = indexes.get(Integer.valueOf(handler.handlerPc()));
                if (handlerIndex != null) {
                    final int[] locals = new int[code.maxLocals()];
                    for (int local = 0; local < locals.length; local++) {
                        locals[local] = UNSAFE;
                    }
                    enqueue(
                        states,
                        pending,
                        queued,
                        handlerIndex.intValue(),
                        new State(locals, List.of(new Slot(UNSAFE, 1)), status)
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
            final int[] source = parameterFacts.getOrDefault(entryPoint, new int[maxLocals]);
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
                state.stack().add(new Slot(UNSAFE, opcode == 9 || opcode == 10 || opcode == 14
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
                state.stack().add(new Slot(UNSAFE, opcode == 47 || opcode == 49 ? 2 : 1));
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
                state.stack().add(new Slot(UNSAFE, kind == 1 || kind == 3 ? 2 : 1));
                return next(index, bytecode);
            }
            if (opcode >= 116 && opcode <= 119) {
                final Slot value = pop(state);
                state.stack().add(new Slot(UNSAFE, value.width()));
                return next(index, bytecode);
            }
            if (opcode >= 120 && opcode <= 131) {
                pop(state);
                pop(state);
                state.stack().add(new Slot(UNSAFE, opcode % 2 == 1 ? 2 : 1));
                return next(index, bytecode);
            }
            if (opcode >= 133 && opcode <= 147) {
                pop(state);
                state.stack().add(new Slot(UNSAFE, conversionWidth(opcode)));
                return next(index, bytecode);
            }
            if (opcode >= 148 && opcode <= 152) {
                pop(state);
                pop(state);
                state.stack().add(new Slot(UNSAFE, 1));
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
                    isClosedWorldConcreteCallbackClass(className) ? CONCRETE : UNSAFE,
                    1
                ));
                return next(index, bytecode);
            }
            if (opcode == 188 || opcode == 189) {
                pop(state);
                state.stack().add(new Slot(UNSAFE, 1));
                return next(index, bytecode);
            }
            if (opcode == 190) {
                pop(state);
                state.stack().add(new Slot(UNSAFE, 1));
                return next(index, bytecode);
            }
            if (opcode == 191) {
                pop(state);
                return List.of();
            }
            if (opcode == 192) {
                return next(index, bytecode);
            }
            if (opcode == 193) {
                pop(state);
                state.stack().add(new Slot(UNSAFE, 1));
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
                state.stack().add(new Slot(UNSAFE, 1));
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
                state.stack().add(fieldValue(field, type));
                return;
            }
            if (instruction.opcode() == 179) {
                final Slot value = pop(state);
                addFieldFact(field, value.fact());
                return;
            }
            if (instruction.opcode() == 180) {
                pop(state);
                state.stack().add(fieldValue(field, type));
                return;
            }
            final Slot value = pop(state);
            pop(state);
            addFieldFact(field, value.fact());
        }

        private Slot fieldValue(final FieldRef field, final ValueType type) {
            if (!type.reference() || !finalCallbackFields.contains(field)) {
                return new Slot(UNSAFE, type.width());
            }
            return new Slot(fieldFacts.getOrDefault(field, Integer.valueOf(0)).intValue(), 1);
        }

        private void addFieldFact(final FieldRef field, final int fact) {
            if (finalCallbackFields.contains(field)) {
                addFact(fieldFacts, field, fact);
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
            final Slot receiver = instruction.opcode() == 184 ? new Slot(UNSAFE, 1) : pop(state);
            if (instruction.opcode() == 185 && directUse(callbackKind).equals(methodRef)) {
                addCallbackFact(site(caller, instruction), receiver.fact());
                pushReturn(state, descriptor.result(), UNSAFE);
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
                contributeCall(target.orElseThrow(), receiver, arguments, state);
                pushReturn(
                    state,
                    descriptor.result(),
                    returnFacts.getOrDefault(target.orElseThrow(), Integer.valueOf(0)).intValue()
                );
                return;
            }
            pushReturn(state, descriptor.result(), UNSAFE);
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
                pushReturn(state, descriptor.result(), UNSAFE);
                return;
            }
            final LambdaMetafactoryCall resolved = lambda.orElseThrow();
            contributeLambdaInvocation(resolved, captures, state);
            final boolean materialized = isMaterializedCallback(resolved, method, instruction);
            pushReturn(state, descriptor.result(), materialized ? MATERIALIZED : UNSAFE);
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
            final int[] contributions = new int[method.code().orElseThrow().maxLocals()];
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
                final int fact = captureIndex < captures.size()
                    ? captures.get(captureIndex).fact()
                    : UNSAFE;
                if (local < 0 || local >= contributions.length) {
                    state.reject();
                    return;
                }
                contributions[local] |= fact;
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
            final int[] contributions = new int[targetMethod.code().orElseThrow().maxLocals()];
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
                contributions[local] |= arguments.get(index).fact();
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

        private void addParameterFacts(final EntryPoint target, final int[] contribution) {
            final int[] current = parameterFacts.get(target);
            if (current == null) {
                return;
            }
            for (int index = 0; index < current.length && index < contribution.length; index++) {
                final int merged = current[index] | contribution[index];
                if (merged != current[index]) {
                    current[index] = merged;
                    changed = true;
                }
            }
        }

        private void addReturnFact(final EntryPoint entryPoint, final int fact) {
            addFact(returnFacts, entryPoint, fact);
        }

        private void addCallbackFact(final Site site, final int fact) {
            addFact(callbackFacts, site, fact);
        }

        private <K> void addFact(final Map<K, Integer> facts, final K key, final int fact) {
            final int current = facts.getOrDefault(key, Integer.valueOf(0)).intValue();
            final int merged = current | fact;
            if (merged != current) {
                facts.put(key, Integer.valueOf(merged));
                changed = true;
            }
        }

        private void markUnsafe(final EntryPoint entryPoint, final MethodInfo method) {
            if (referenceResult(method.descriptor())) {
                addReturnFact(entryPoint, UNSAFE);
            }
            if (method.code().isEmpty()) {
                return;
            }
            for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                if (instruction.fieldRef().isPresent()
                    && (instruction.opcode() == 179 || instruction.opcode() == 181)) {
                    addFieldFact(instruction.fieldRef().orElseThrow(), UNSAFE);
                }
                if (instruction.methodRef().isPresent()
                    && isTrackedUse(callbackKind, instruction.methodRef().orElseThrow())) {
                    addCallbackFact(site(entryPoint, instruction), UNSAFE);
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
            final int[] current = parameterFacts.get(target);
            final Optional<MethodInfo> resolvedMethod = method(target);
            if (current == null || resolvedMethod.isEmpty()) {
                return;
            }
            final int[] contribution = new int[current.length];
            final MethodInfo method = resolvedMethod.orElseThrow();
            final Optional<Descriptor> resolvedDescriptor = methodDescriptor(method.descriptor());
            if (resolvedDescriptor.isEmpty()) {
                for (int local = 0; local < contribution.length; local++) {
                    contribution[local] = UNSAFE;
                }
                addParameterFacts(target, contribution);
                return;
            }
            int local = 0;
            if (!method.isStatic()) {
                if (contribution.length == 0) {
                    return;
                }
                contribution[0] = UNSAFE;
                local = 1;
            }
            for (final ValueType parameter : resolvedDescriptor.orElseThrow().parameters()) {
                if (local < 0 || local >= contribution.length) {
                    for (int index = 0; index < contribution.length; index++) {
                        contribution[index] = UNSAFE;
                    }
                    addParameterFacts(target, contribution);
                    return;
                }
                if (parameter.reference()) {
                    contribution[local] = UNSAFE;
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
        state.stack().add(new Slot(reference ? localFact(state, local) : UNSAFE, width));
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
        state.locals()[local] = opcode == 58 || opcode >= 75 ? value.fact() : UNSAFE;
    }

    private static int localFact(final State state, final int local) {
        if (local < 0 || local >= state.locals().length) {
            state.reject();
            return UNSAFE;
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
            return new Slot(UNSAFE, 1);
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
        final int fact
    ) {
        if (result.width() > 0) {
            state.stack().add(new Slot(result.reference() ? fact : UNSAFE, result.width()));
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

    private static ValueKind kind(final int fact) {
        return switch (fact) {
            case 0 -> ValueKind.UNKNOWN;
            case MATERIALIZED -> ValueKind.MATERIALIZED;
            case CONCRETE -> ValueKind.CONCRETE;
            case UNSAFE -> ValueKind.UNSAFE;
            case MATERIALIZED | CONCRETE -> ValueKind.MATERIALIZED_AND_CONCRETE;
            default -> ValueKind.MIXED;
        };
    }

    private static void markReferenceParameters(
        final MethodInfo method,
        final int[] locals,
        final int fact
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
                    locals[local] |= fact;
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

    private record Slot(int fact, int width) {
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
        private final int[] locals;
        private final List<Slot> stack;
        private final FlowStatus status;

        private State(
            final int[] locals,
            final List<Slot> stack,
            final FlowStatus status
        ) {
            this.locals = java.util.Arrays.copyOf(locals, locals.length);
            this.stack = new ArrayList<>(stack);
            this.status = status;
        }

        private int[] locals() {
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
                final int merged = locals[index] | incoming.locals[index];
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
                final int merged = current.fact() | other.fact();
                if (merged != current.fact()) {
                    stack.set(index, new Slot(merged, current.width()));
                    changed = true;
                }
            }
            return changed;
        }
    }
}
