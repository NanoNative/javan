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
import javan.compat.JdkCallSupport;
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
    private static final int PLATFORM = 8;
    private static final int BOUNDED = 16;
    private static final int REMOVAL_REJECTING = 32;
    private static final int ALWAYS_EMPTY = 64;
    private static final int LIST_ADDITION_NO_EQUALITY = 128;
    private static final int FRESH_EMPTY_SET = 256;
    private static final int ENTRY_VALUE = 512;
    private static final int INLINE_ENTRY_ARRAY = 1024;
    private static final int COLLECTION_MUST_FACTS = REMOVAL_REJECTING | ALWAYS_EMPTY | LIST_ADDITION_NO_EQUALITY
        | FRESH_EMPTY_SET | ENTRY_VALUE | INLINE_ENTRY_ARRAY;
    private static final Fact NONE_FACT = new Fact(0, List.of(), false);
    private static final Fact MATERIALIZED_FACT = new Fact(MATERIALIZED, List.of(), false);
    private static final Fact UNSAFE_FACT = new Fact(UNSAFE, List.of(), true);
    private static final Fact PLATFORM_FACT = new Fact(PLATFORM, List.of(), false);
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
        return analyze(classes, reachableMethods, declaredExternalLeaves, new MethodFlowCache());
    }

    static Result analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods,
        final List<EntryPoint> declaredExternalLeaves,
        final MethodFlowCache methodFlows
    ) {
        final FlowResult function = containsTrackedUse(
            classes,
            reachableMethods,
            CallbackKind.FUNCTION
        ) ? new Engine(classes, reachableMethods, declaredExternalLeaves, List.of(), List.of(), CallbackKind.FUNCTION, methodFlows).analyze() : FlowResult.empty();
        final FlowResult supplier = containsTrackedUse(
            classes,
            reachableMethods,
            CallbackKind.SUPPLIER
        ) ? new Engine(classes, reachableMethods, declaredExternalLeaves, List.of(), List.of(), CallbackKind.SUPPLIER, methodFlows).analyze() : FlowResult.empty();
        return new Result(
            function.kinds(),
            supplier.kinds(),
            function.provenance(),
            supplier.provenance(),
            true
        );
    }

    record CollectionQuery(List<String> types, boolean bounded, boolean unknown) {
        CollectionQuery {
            final Provenance normalized = new Provenance(types, unknown);
            types = normalized.types();
            unknown = normalized.unknown();
        }

        static CollectionQuery unavailable() {
            return new CollectionQuery(List.of(), false, true);
        }
    }

    static Map<String, CollectionQuery> analyzeCollectionQueries(
        final Map<String, ClassFile> classes,
        final CallGraph graph,
        final List<EntryPoint> declaredExternalLeaves,
        final List<EntryPoint> roots,
        final MethodFlowCache methodFlows
    ) {
        final List<EntryPoint> reachableMethods = graph.reachableMethods();
        if (!containsTrackedUse(classes, reachableMethods, CallbackKind.COLLECTION_QUERY)) {
            return Map.of();
        }
        final Map<String, CollectionQuery> result = new LinkedHashMap<>(
            new Engine(classes, reachableMethods, declaredExternalLeaves, roots, graph.callEdges(),
                CallbackKind.COLLECTION_QUERY, methodFlows).analyzeQueries()
        );
        for (final EntryPoint entry : reachableMethods) {
            final ClassFile owner = classes.get(entry.className());
            final Optional<MethodInfo> method = owner == null ? Optional.empty()
                : owner.method(entry.methodName(), entry.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                if (instruction.methodRef().isPresent()
                    && (JdkCallSupport.isCollectionEqualitySearch(instruction.methodRef().orElseThrow())
                        || JdkCallSupport.isMapKeyOperation(instruction.methodRef().orElseThrow())
                        || JdkCallSupport.isMapMultiEntryFactory(instruction.methodRef().orElseThrow())
                        || JdkCallSupport.isMapEntryArrayFactory(instruction.methodRef().orElseThrow())
                        || JdkCallSupport.isCollectionContainsAll(instruction.methodRef().orElseThrow())
                        || JdkCallSupport.isCollectionFilterAll(instruction.methodRef().orElseThrow()))) {
                    if (JdkCallSupport.isMapMultiEntryFactory(instruction.methodRef().orElseThrow())) {
                        final int count = JdkCallSupport.mapFixedEntryCount(instruction.methodRef().orElseThrow());
                        for (int key = 0; key < count; key++) {
                            result.putIfAbsent(mapKeySiteKey(entry, instruction, key), CollectionQuery.unavailable());
                        }
                    } else {
                        result.putIfAbsent(collectionSiteKey(entry, instruction), CollectionQuery.unavailable());
                    }
                }
            }
        }
        return Map.copyOf(result);
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

    static final class MethodFlowCache {
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
        private final EntryPointMembership externalRoots;
        private final CallbackKind callbackKind;
        private final Map<String, Fact[]> parameterFacts = new LinkedHashMap<>();
        private final Map<String, Fact> returnFacts = new LinkedHashMap<>();
        private final Set<FieldRef> finalCallbackFields = new LinkedHashSet<>();
        private final Map<FieldRef, Fact> fieldFacts = new LinkedHashMap<>();
        private final Map<Site, Fact> callbackFacts = new LinkedHashMap<>();
        private final Map<String, Fact> collectionFacts = new LinkedHashMap<>();
        private final Map<String, List<EntryPoint>> unknownDynamicTargets = new HashMap<>();
        private final Map<String, List<EntryPoint>> dynamicCallEdges = new HashMap<>();
        private final Map<String, Boolean> callbackTypeCache = new HashMap<>();
        private final MethodFlowCache methodFlows;
        private final Map<String, List<EntryPoint>> callers = new HashMap<>();
        private final Map<FieldRef, List<EntryPoint>> fieldReaders = new HashMap<>();
        private final List<EntryPoint> pendingMethods = new ArrayList<>();
        private final EntryPointMembership queuedMethods = new EntryPointMembership(List.of());

        private Engine(
            final Map<String, ClassFile> classes,
            final List<EntryPoint> reachableMethods,
            final List<EntryPoint> declaredExternalLeaves,
            final List<EntryPoint> externalRoots,
            final List<CallEdge> callEdges,
            final CallbackKind callbackKind,
            final MethodFlowCache methodFlows
        ) {
            this.classes = classes;
            this.reachableMethods = List.copyOf(reachableMethods);
            this.reachableSet = new EntryPointMembership(reachableMethods);
            this.declaredExternalLeaves = new EntryPointMembership(declaredExternalLeaves);
            this.externalRoots = new EntryPointMembership(externalRoots);
            this.callbackKind = callbackKind;
            this.methodFlows = methodFlows;
            for (final CallEdge edge : callEdges) {
                final EntryPoint callee = edge.callee();
                addDependency(dynamicCallEdges,
                    edge.caller().display() + "#" + callee.methodName() + callee.descriptor(), callee);
            }
            initializeFacts();
        }

        private FlowResult analyze() {
            runAnalysis();
            final Map<Site, ValueKind> kinds = new LinkedHashMap<>();
            final Map<Site, Provenance> provenance = new LinkedHashMap<>();
            for (final Map.Entry<Site, Fact> entry : callbackFacts.entrySet()) {
                kinds.put(entry.getKey(), kind(entry.getValue()));
                provenance.put(entry.getKey(), entry.getValue().provenance());
            }
            return new FlowResult(Map.copyOf(kinds), Map.copyOf(provenance));
        }

        private Map<String, CollectionQuery> analyzeQueries() {
            runAnalysis();
            final Map<String, CollectionQuery> result = new LinkedHashMap<>();
            for (final Map.Entry<String, Fact> entry : collectionFacts.entrySet()) {
                final Fact fact = entry.getValue();
                result.put(entry.getKey(), new CollectionQuery(fact.types(),
                    (fact.kinds() & BOUNDED) != 0, fact.unknown() || fact.kinds() == 0));
            }
            return Map.copyOf(result);
        }

        private void runAnalysis() {
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
        }

        private void initializeFacts() {
            for (final EntryPoint entryPoint : reachableMethods) {
                final MethodInfo method = method(entryPoint).orElse(null);
                if (method == null || method.code().isEmpty()) {
                    continue;
                }
                final Fact[] locals = facts(method.code().orElseThrow().maxLocals());
                if (!method.isStatic() && locals.length > 0) {
                    locals[0] = Fact.unsafe();
                    if (callbackKind != CallbackKind.COLLECTION_QUERY
                        && isClosedWorldConcreteCallbackClass(entryPoint.className())) {
                        locals[0] = Fact.concrete(entryPoint.className());
                    }
                }
                if (method.isPublicStaticMain() || externalRoots.contains(entryPoint)) {
                    markReferenceParameters(method, locals, Fact.unsafe());
                }
                parameterFacts.put(entryPoint.display(), locals);
                returnFacts.put(entryPoint.display(), Fact.none());
            }
            if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                return;
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
            Fact[] source = parameterFacts.get(entryPoint.display());
            if (source == null) {
                source = facts(maxLocals);
            }
            final Fact[] initial = java.util.Arrays.copyOf(source, maxLocals);
            final Optional<MethodInfo> entryMethod = method(entryPoint);
            if (callbackKind == CallbackKind.COLLECTION_QUERY && entryMethod.isPresent()) {
                final MethodInfo entry = entryMethod.orElseThrow();
                final String descriptor = entry.descriptor();
                int index = 1;
                int local = entry.isStatic() ? 0 : 1;
                while (index < descriptor.length() && descriptor.charAt(index) != ')') {
                    final Optional<ParsedType> parsed = parseType(descriptor, index, false);
                    if (parsed.isEmpty()) {
                        status.reject();
                        break;
                    }
                    final ParsedType parameter = parsed.orElseThrow();
                    final String parameterDescriptor = descriptor.substring(index, parameter.nextIndex());
                    if (parameter.type().reference() && local < initial.length) {
                        if (platformValue(parameterDescriptor)) {
                            initial[local] = Fact.platform();
                        } else if (parameterDescriptor.startsWith("L")) {
                            final String type = parameterDescriptor.substring(1, parameterDescriptor.length() - 1);
                            final ClassFile exact = classes.get(type);
                            if (exact != null && exact.isFinal() && !type.startsWith("java/")) {
                                initial[local] = Fact.concrete(type);
                            }
                        }
                    }
                    local += parameter.type().width();
                    index = parameter.nextIndex();
                }
            }
            return new State(initial, List.of(), status);
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
            // Only track fresh arrays while they remain on a straight-line operand stack.
            // Any escape, control-flow split or opaque stack manipulation loses the fact.
            if (callbackKind == CallbackKind.COLLECTION_QUERY
                && (opcode >= 54 && opcode <= 78 || opcode >= 87 && opcode <= 88
                    || opcode >= 90 && opcode <= 95 || opcode >= 153 && opcode <= 181
                    || opcode == 186 || opcode == 190 || opcode >= 191 && opcode <= 201)) {
                state.clearInlineEntryArrays();
            }
            if (opcode == 0 || opcode == 132) {
                return next(index, bytecode);
            }
            if (opcode >= 1 && opcode <= 20) {
                final boolean platformValue = callbackKind == CallbackKind.COLLECTION_QUERY
                    && (opcode == 1 || instruction.stringValue().isPresent());
                state.stack().add(new Slot(platformValue ? Fact.platform() : Fact.unsafe(), opcode == 9 || opcode == 10 || opcode == 14
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
                if (callbackKind == CallbackKind.COLLECTION_QUERY) state.clearFreshEmptySets();
                final Slot value = pop(state);
                pop(state);
                final Slot array = pop(state);
                if (callbackKind == CallbackKind.COLLECTION_QUERY && opcode == 83
                    && (array.fact().kinds() & INLINE_ENTRY_ARRAY) != 0
                    && (value.fact().kinds() & INLINE_ENTRY_ARRAY) == 0) {
                    final Fact key = (value.fact().kinds() & ENTRY_VALUE) != 0
                        && value.fact().elements() != null ? value.fact().elements() : Fact.unsafe();
                    state.updateInlineEntryArray(array.fact(), key);
                } else if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                    state.clearInlineEntryArrays();
                }
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
                if (callbackKind == CallbackKind.COLLECTION_QUERY) state.clearFreshEmptySets();
                dynamic(entryPoint, method, instruction, state);
                return next(index, bytecode);
            }
            if (opcode == 187) {
                if (instruction.className().isEmpty()) {
                    state.reject();
                    return List.of();
                }
                final String className = instruction.className().orElseThrow();
                final Fact platform;
                if ("java/util/ArrayList".equals(className)) {
                    platform = Fact.platform().withListAdditionNoEquality();
                } else if ("java/util/HashSet".equals(className) || "java/util/LinkedHashSet".equals(className)) {
                    platform = Fact.platform().withFreshEmptySet();
                } else {
                    platform = Fact.platform();
                }
                state.stack().add(new Slot(
                    callbackKind == CallbackKind.COLLECTION_QUERY && className.startsWith("java/")
                        ? platform
                        : isClosedWorldConcreteCallbackClass(className)
                        ? Fact.concrete(className)
                        : Fact.unsafe(),
                    1
                ));
                return next(index, bytecode);
            }
            if (opcode == 188 || opcode == 189) {
                pop(state);
                final boolean multiEntryArray = callbackKind == CallbackKind.COLLECTION_QUERY && opcode == 189
                    && instruction.className().isPresent()
                    && "java/util/Map$Entry".equals(instruction.className().orElseThrow())
                    && index > 0 && inlineArrayLength(bytecode.get(index - 1)) > 1;
                state.stack().add(new Slot(multiEntryArray
                    ? new Fact(PLATFORM | INLINE_ENTRY_ARRAY, List.of(), false, null)
                    : callbackKind == CallbackKind.COLLECTION_QUERY ? Fact.platform() : Fact.unsafe(), 1));
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
                state.stack().add(new Slot(callbackKind == CallbackKind.COLLECTION_QUERY
                    ? Fact.platform() : Fact.unsafe(), 1));
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
                if (callbackKind == CallbackKind.COLLECTION_QUERY) state.clearFreshEmptySets();
                final Slot value = pop(state);
                addFieldFact(field, value.fact());
                return;
            }
            if (instruction.opcode() == 180) {
                pop(state);
                state.stack().add(fieldValue(entryPoint, field, type));
                return;
            }
            if (callbackKind == CallbackKind.COLLECTION_QUERY) state.clearFreshEmptySets();
            final Slot value = pop(state);
            pop(state);
            addFieldFact(field, value.fact());
        }

        private Slot fieldValue(final EntryPoint reader, final FieldRef field, final ValueType type) {
            if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                return new Slot(type.reference() && platformValue(field.descriptor())
                    ? Fact.platform() : Fact.unsafe(), type.width());
            }
            if (!type.reference() || !finalCallbackFields.contains(field)) {
                return new Slot(Fact.unsafe(), type.width());
            }
            addDependency(fieldReaders, field, reader);
            return new Slot(fieldFacts.getOrDefault(field, Fact.none()), 1);
        }

        private void addFieldFact(final FieldRef field, final Fact fact) {
            if (callbackKind == CallbackKind.COLLECTION_QUERY || !finalCallbackFields.contains(field)) {
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
            Slot receiver = instruction.opcode() == 184 ? new Slot(Fact.unsafe(), 1) : pop(state);
            if (callbackKind == CallbackKind.COLLECTION_QUERY && !JdkCallSupport.isMapEntryArrayFactory(methodRef)) {
                boolean arrayEscapes = (receiver.fact().kinds() & INLINE_ENTRY_ARRAY) != 0;
                for (final Slot argument : arguments) arrayEscapes |= (argument.fact().kinds() & INLINE_ENTRY_ARRAY) != 0;
                if (arrayEscapes) {
                    state.clearInlineEntryArrays();
                    if ((receiver.fact().kinds() & INLINE_ENTRY_ARRAY) != 0) receiver = new Slot(Fact.platform(), receiver.width());
                    for (int index = 0; index < arguments.size(); index++) {
                        final Slot argument = arguments.get(index);
                        if ((argument.fact().kinds() & INLINE_ENTRY_ARRAY) != 0) {
                            arguments.set(index, new Slot(Fact.platform(), argument.width()));
                        }
                    }
                }
            }
            boolean freshSetEscapes = false;
            for (final Slot argument : arguments) {
                freshSetEscapes |= (argument.fact().kinds() & FRESH_EMPTY_SET) != 0;
            }
            final boolean emptySetConstructor = instruction.opcode() == 183 && "<init>".equals(methodRef.name())
                && "()V".equals(methodRef.descriptor()) && ("java/util/HashSet".equals(methodRef.owner())
                    || "java/util/LinkedHashSet".equals(methodRef.owner()));
            freshSetEscapes |= (receiver.fact().kinds() & FRESH_EMPTY_SET) != 0 && !emptySetConstructor;
            if (callbackKind == CallbackKind.COLLECTION_QUERY && freshSetEscapes) {
                state.clearFreshEmptySets();
                for (int index = 0; index < arguments.size(); index++) {
                    final Slot argument = arguments.get(index);
                    final Fact cleared = argument.fact().withoutFreshEmptySet();
                    if (cleared != argument.fact()) arguments.set(index, new Slot(cleared, argument.width()));
                }
            }
            if (callbackKind != CallbackKind.COLLECTION_QUERY
                && instruction.opcode() == 185 && directUse(callbackKind).equals(methodRef)) {
                addCallbackFact(site(caller, instruction), receiver.fact());
                pushReturn(state, descriptor.result(), Fact.unsafe());
                return;
            }
            if (isIndirectUse(callbackKind, methodRef) && !arguments.isEmpty()) {
                if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                    if (JdkCallSupport.isMapEntryArrayFactory(methodRef)) {
                        final Fact array = arguments.getFirst().fact();
                        final boolean trackedArray = (array.kinds() & INLINE_ENTRY_ARRAY) != 0;
                        addCollectionFact(collectionSiteKey(caller, instruction), trackedArray ? Fact.platform() : Fact.unsafe());
                        if (trackedArray) {
                            Fact key = array.elements();
                            int index = 0;
                            while (key != null) {
                                addCollectionFact(mapKeySiteKey(caller, instruction, index), key.withElements(null));
                                key = key.elements();
                                index++;
                            }
                        }
                    } else if (JdkCallSupport.isMapMultiEntryFactory(methodRef)) {
                        for (int key = 0; key < arguments.size() / 2; key++) {
                            addCollectionFact(mapKeySiteKey(caller, instruction, key), arguments.get(key * 2).fact());
                        }
                    } else {
                        final Fact argument = JdkCallSupport.isMapKeyOperation(methodRef)
                            ? arguments.getFirst().fact() : arguments.getLast().fact();
                        final Fact source = JdkCallSupport.isCollectionFilterAll(methodRef) ? receiver.fact() : argument;
                        final Fact query = JdkCallSupport.isCollectionContainsAll(methodRef) || JdkCallSupport.isCollectionFilterAll(methodRef)
                            ? (source.elements() == null ? Fact.unsafe() : source.elements()) : source;
                        final boolean noEquality = ((JdkCallSupport.isCollectionObjectRemoval(methodRef)
                            || JdkCallSupport.isCollectionFilterAll(methodRef))
                            && (receiver.fact().kinds() & REMOVAL_REJECTING) != 0)
                            || (JdkCallSupport.isCollectionFilterAll(methodRef) && (argument.kinds() & ALWAYS_EMPTY) != 0)
                            || ((JdkCallSupport.isCollectionEqualitySearch(methodRef) || JdkCallSupport.isCollectionContainsAll(methodRef))
                            && (receiver.fact().kinds() & ALWAYS_EMPTY) != 0)
                            || (JdkCallSupport.isCollectionObjectAddition(methodRef)
                            && (receiver.fact().kinds() & (LIST_ADDITION_NO_EQUALITY | REMOVAL_REJECTING | FRESH_EMPTY_SET)) != 0);
                        addCollectionFact(collectionSiteKey(caller, instruction), noEquality ? Fact.platform() : query);
                    }
                } else {
                    addCallbackFact(site(caller, instruction), arguments.getLast().fact());
                }
            }
            if (callbackKind == CallbackKind.COLLECTION_QUERY && instruction.opcode() == 184
                && JdkCallSupport.isMapSingleEntryFactory(methodRef) && "entry".equals(methodRef.name())) {
                pushReturn(state, descriptor.result(), new Fact(PLATFORM | ENTRY_VALUE, List.of(), false,
                    arguments.getFirst().fact().withElements(null)));
                return;
            }
            if (callbackKind == CallbackKind.COLLECTION_QUERY && instruction.opcode() == 184) {
                final String owner = methodRef.owner();
                final String name = methodRef.name();
                final String descriptorText = methodRef.descriptor();
                if ((("java/util/List".equals(owner) || "java/util/Set".equals(owner)) && "of".equals(name)
                    && !descriptorText.startsWith("(["))
                    || ("java/util/Collections".equals(owner)
                        && ("singletonList".equals(name) || "singleton".equals(name)
                            || "emptyList".equals(name) || "emptySet".equals(name)))) {
                    Fact elements = Fact.platform();
                    for (final Slot argument : arguments) {
                        elements = elements.merge(argument.fact().withElements(null));
                    }
                    Fact collection = Fact.unsafe().withElements(elements);
                    if (!"java/util/Collections".equals(owner)) {
                        collection = collection.withRemovalRejecting();
                    }
                    if (arguments.isEmpty()) {
                        collection = collection.withAlwaysEmpty();
                    }
                    pushReturn(state, descriptor.result(), collection);
                    return;
                }
                if ((("java/util/List".equals(owner) || "java/util/Set".equals(owner)) && "copyOf".equals(name)
                    || ("java/util/Collections".equals(owner)
                        && ("unmodifiableList".equals(name) || "unmodifiableSet".equals(name)
                            || "unmodifiableCollection".equals(name)))) && arguments.size() == 1) {
                    final Fact original = arguments.getFirst().fact();
                    Fact collection = Fact.unsafe().withElements(original.elements()).withRemovalRejecting();
                    if ((original.kinds() & ALWAYS_EMPTY) != 0) {
                        collection = collection.withAlwaysEmpty();
                    }
                    pushReturn(state, descriptor.result(), collection);
                    return;
                }
            }
            if (isRequireNonNull(methodRef) && !arguments.isEmpty()) {
                pushReturn(state, descriptor.result(), arguments.getFirst().fact());
                return;
            }
            final Optional<EntryPoint> target = exactTarget(instruction, methodRef);
            if (target.isPresent()) {
                addDependency(callers, target.orElseThrow().display(), caller);
                contributeCall(target.orElseThrow(), receiver, arguments, state);
                final Fact returned = resultFact(methodRef,
                    returnFacts.getOrDefault(target.orElseThrow().display(), Fact.none()));
                pushReturn(state, descriptor.result(), returned);
                return;
            }
            if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                final String key = instruction.opcode() + ":" + methodRef.display();
                List<EntryPoint> possibleTargets = unknownDynamicTargets.get(key);
                if (possibleTargets == null) {
                    possibleTargets = unsafeCallTargets(instruction, methodRef);
                    unknownDynamicTargets.put(key, possibleTargets);
                }
                for (final EntryPoint possible : possibleTargets) {
                    markParametersUnsafe(possible);
                }
                markDynamicCallEdgesUnsafe(caller, methodRef);
            }
            pushReturn(state, descriptor.result(), resultFact(methodRef, Fact.unsafe()));
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
                pushReturn(state, descriptor.result(), callbackKind == CallbackKind.COLLECTION_QUERY
                    && platformValue(dynamicRef.descriptor().substring(dynamicRef.descriptor().indexOf(')') + 1))
                    ? Fact.platform() : Fact.unsafe());
                return;
            }
            final LambdaMetafactoryCall resolved = lambda.orElseThrow();
            contributeLambdaInvocation(resolved, captures, state);
            final boolean materialized = callbackKind != CallbackKind.COLLECTION_QUERY
                && isMaterializedCallback(resolved, method, instruction);
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
            final Fact[] current = parameterFacts.get(target.display());
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
            final Fact current = returnFacts.getOrDefault(entryPoint.display(), Fact.none());
            final Fact merged = current.merge(fact);
            if (merged != current) {
                returnFacts.put(entryPoint.display(), merged);
                enqueueMethods(callers.get(entryPoint.display()));
            }
        }

        private void addCallbackFact(final Site site, final Fact fact) {
            final Fact current = callbackFacts.getOrDefault(site, Fact.none());
            final Fact merged = current.merge(fact);
            if (merged != current) {
                callbackFacts.put(site, merged);
            }
        }

        private void addCollectionFact(final String site, final Fact fact) {
            final Fact current = collectionFacts.getOrDefault(site, Fact.none());
            final Fact merged = current.merge(fact);
            if (merged != current) {
                collectionFacts.put(site, merged);
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
            for (final EntryPoint method : methods) {
                if (method.className().equals(dependent.className())
                    && method.methodName().equals(dependent.methodName())
                    && method.descriptor().equals(dependent.descriptor())) {
                    return;
                }
            }
            methods.add(dependent);
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
                    if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                        addCollectionFact(collectionSiteKey(entryPoint, instruction), Fact.unsafe());
                    } else {
                        addCallbackFact(site(entryPoint, instruction), Fact.unsafe());
                    }
                }
                if (instruction.methodRef().isPresent()) {
                    final MethodRef called = instruction.methodRef().orElseThrow();
                    for (final EntryPoint target : unsafeCallTargets(instruction, called)) {
                        markParametersUnsafe(target);
                    }
                    if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                        markDynamicCallEdgesUnsafe(entryPoint, called);
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

        private void markDynamicCallEdgesUnsafe(final EntryPoint caller, final MethodRef methodRef) {
            final List<EntryPoint> edges = dynamicCallEdges.get(
                caller.display() + "#" + methodRef.name() + methodRef.descriptor());
            if (edges != null) {
                for (final EntryPoint target : edges) {
                    markParametersUnsafe(target);
                }
            }
        }

        private void markParametersUnsafe(final EntryPoint target) {
            final Fact[] current = parameterFacts.get(target.display());
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
            if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                final String descriptor = target.startsWith("[") ? target : "L" + target + ";";
                if (platformValue(descriptor)) {
                    return Fact.platform();
                }
                final ClassFile exact = classes.get(target);
                if (exact != null && exact.isFinal() && !target.startsWith("java/")) {
                    return Fact.concrete(target);
                }
            }
            if (fact.unknown() || fact.types().isEmpty() || (fact.kinds() & BOUNDED) != 0) {
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

        private boolean platformValue(final String descriptor) {
            if (knownPlatformValue(descriptor)) {
                return true;
            }
            if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
                return false;
            }
            final ClassFile type = classes.get(descriptor.substring(1, descriptor.length() - 1));
            return type != null && type.isEnum();
        }

        private Fact resultFact(final MethodRef methodRef, final Fact fallback) {
            if (callbackKind != CallbackKind.COLLECTION_QUERY) {
                return fallback;
            }
            final String result = methodRef.descriptor().substring(methodRef.descriptor().indexOf(')') + 1);
            if (platformValue(result)) {
                return Fact.platform();
            }
            if ("Ljava/nio/file/Path;".equals(result)) {
                return Fact.bounded("java/nio/file/Path");
            }
            return fallback;
        }

        private boolean isCallbackValueDescriptor(final String descriptor) {
            if (callbackKind == CallbackKind.COLLECTION_QUERY) {
                return descriptor.startsWith("L") || descriptor.startsWith("[");
            }
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
                && (callbackKind == CallbackKind.COLLECTION_QUERY || isAssignableToCallback(className));
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

    private static int inlineArrayLength(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 3 && opcode <= 8) return opcode - 3;
        if (opcode == 16 && instruction.operands().length > 0) return instruction.operands()[0];
        if (opcode == 17 && instruction.operands().length > 1) {
            return (short) (((instruction.operands()[0] & 255) << 8) | (instruction.operands()[1] & 255));
        }
        return instruction.intValue().orElse(Integer.valueOf(-1)).intValue();
    }

    private static int conversionWidth(final int opcode) {
        return switch (opcode) {
            case 133, 135, 140, 141 -> 2;
            case 134, 136, 137, 139, 142, 144, 145, 146, 147 -> 1;
            case 138, 143 -> 2;
            default -> 1;
        };
    }

    private static boolean knownPlatformValue(final String descriptor) {
        if (descriptor.startsWith("[")) {
            return true;
        }
        return switch (descriptor) {
            case "Ljava/lang/String;", "Ljava/lang/Class;", "Ljava/lang/Boolean;", "Ljava/lang/Byte;",
                "Ljava/lang/Short;", "Ljava/lang/Character;", "Ljava/lang/Integer;", "Ljava/lang/Long;",
                "Ljava/lang/Float;", "Ljava/lang/Double;" -> true;
            default -> false;
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
        return callbackKind == CallbackKind.COLLECTION_QUERY ? (JdkCallSupport.isCollectionEqualitySearch(methodRef)
            || JdkCallSupport.isMapKeyOperation(methodRef)
            || JdkCallSupport.isMapMultiEntryFactory(methodRef) || JdkCallSupport.isMapEntryArrayFactory(methodRef)
            || JdkCallSupport.isCollectionContainsAll(methodRef) || JdkCallSupport.isCollectionFilterAll(methodRef))
            : directUse(callbackKind).equals(methodRef) || isIndirectUse(callbackKind, methodRef);
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
        if (callbackKind == CallbackKind.COLLECTION_QUERY) {
            return JdkCallSupport.isCollectionEqualitySearch(methodRef) || JdkCallSupport.isCollectionContainsAll(methodRef)
                || JdkCallSupport.isCollectionFilterAll(methodRef) || JdkCallSupport.isMapKeyOperation(methodRef)
                || JdkCallSupport.isMapMultiEntryFactory(methodRef) || JdkCallSupport.isMapEntryArrayFactory(methodRef);
        }
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

    static String collectionSiteKey(final EntryPoint entryPoint, final Instruction instruction) {
        return entryPoint.display() + "#" + instruction.offset();
    }

    static String mapKeySiteKey(final EntryPoint entryPoint, final Instruction instruction, final int key) {
        return collectionSiteKey(entryPoint, instruction) + ":" + key;
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

    // Collection elements are merged facts. For inline entry arrays they form a linked list of key facts.
    private record Fact(int kinds, List<String> types, boolean unknown, Fact elements) {
        private Fact(final int kinds, final List<String> types, final boolean unknown) {
            this(kinds, types, unknown, null);
        }

        private Fact {
            types = List.copyOf(types);
        }

        private static Fact none() {
            return NONE_FACT;
        }

        private static Fact materialized() {
            return MATERIALIZED_FACT;
        }

        private static Fact platform() {
            return PLATFORM_FACT;
        }

        private static Fact concrete(final String type) {
            return new Fact(CONCRETE, List.of(type), false);
        }

        private static Fact bounded(final String type) {
            return new Fact(CONCRETE | BOUNDED, List.of(type), false);
        }

        private static Fact unsafe() {
            return UNSAFE_FACT;
        }

        private Fact withElements(final Fact value) {
            return elements == value ? this : new Fact(kinds, types, unknown, value);
        }

        private Fact withRemovalRejecting() {
            return (kinds & REMOVAL_REJECTING) != 0 ? this : new Fact(kinds | REMOVAL_REJECTING, types, unknown, elements);
        }

        private Fact withAlwaysEmpty() {
            return (kinds & ALWAYS_EMPTY) != 0 ? this : new Fact(kinds | ALWAYS_EMPTY, types, unknown, elements);
        }

        private Fact withListAdditionNoEquality() {
            return (kinds & LIST_ADDITION_NO_EQUALITY) != 0 ? this
                : new Fact(kinds | LIST_ADDITION_NO_EQUALITY, types, unknown, elements);
        }

        private Fact withFreshEmptySet() {
            return (kinds & FRESH_EMPTY_SET) != 0 ? this : new Fact(kinds | FRESH_EMPTY_SET, types, unknown, elements);
        }

        private Fact withoutFreshEmptySet() {
            // Inline-array keys are cleared as they are recorded, so avoid revisiting the list.
            if ((kinds & INLINE_ENTRY_ARRAY) != 0) return this;
            final Fact clearedElements = elements == null ? null : elements.withoutFreshEmptySet();
            return (kinds & FRESH_EMPTY_SET) == 0 && clearedElements == elements ? this
                : new Fact(kinds & ~FRESH_EMPTY_SET, types, unknown, clearedElements);
        }

        private Fact merge(final Fact other) {
            if (kinds == 0 && types.isEmpty() && !unknown) {
                return other;
            }
            if (other.kinds == 0 && other.types.isEmpty() && !other.unknown) {
                return this;
            }
            final Fact mergedElements = elements == null || other.elements == null ? null : elements.merge(other.elements);
            return mergeValue(other).withElements(mergedElements);
        }

        private Fact mergeValue(final Fact other) {
            final int mergedKinds = ((kinds | other.kinds) & ~COLLECTION_MUST_FACTS)
                | (kinds & other.kinds & COLLECTION_MUST_FACTS);
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
        SUPPLIER,
        COLLECTION_QUERY
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

        private void clearInlineEntryArrays() {
            for (int index = 0; index < stack.size(); index++) {
                final Slot slot = stack.get(index);
                if ((slot.fact().kinds() & INLINE_ENTRY_ARRAY) != 0) {
                    stack.set(index, new Slot(Fact.platform(), slot.width()));
                }
            }
        }

        private void updateInlineEntryArray(final Fact array, final Fact key) {
            final Fact clearedKey = key.withoutFreshEmptySet();
            final Fact storedKey = new Fact(clearedKey.kinds(), clearedKey.types(), clearedKey.unknown(), array.elements());
            final Fact updated = new Fact(array.kinds(), array.types(), array.unknown(), storedKey);
            for (int index = 0; index < stack.size(); index++) {
                final Slot slot = stack.get(index);
                if (slot.fact() == array) stack.set(index, new Slot(updated, slot.width()));
            }
        }

        private void clearFreshEmptySets() {
            for (int index = 0; index < locals.length; index++) {
                locals[index] = locals[index].withoutFreshEmptySet();
            }
            for (int index = 0; index < stack.size(); index++) {
                final Slot slot = stack.get(index);
                final Fact cleared = slot.fact().withoutFreshEmptySet();
                if (cleared != slot.fact()) stack.set(index, new Slot(cleared, slot.width()));
            }
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
