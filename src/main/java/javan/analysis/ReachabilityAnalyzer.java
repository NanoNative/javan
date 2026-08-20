package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.FunctionLambdaUse;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactoryCall;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.classfile.RecordObjectMethodsCall;
import javan.compat.ExactMethodSupport;
import javan.compat.JdkCallSupport;
import javan.compat.NetworkApiSupport;
import javan.compat.JavanNativeSubstitutions;
import javan.util.Strings2;
import javan.verify.Diagnostic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a small call graph for reachable methods in the scanned closed world.
 */
public final class ReachabilityAnalyzer {
    private static final MethodRef RUNNABLE_RUN = new MethodRef("java/lang/Runnable", "run", "()V");

    /**
     * Analyzes reachability from a main class.
     *
     * @param classes parsed closed-world classes
     * @param mainClass JVM internal main class
     * @return call graph
     */
    public CallGraph analyze(final Map<String, ClassFile> classes, final String mainClass) {
        return analyze(classes, mainClass, List.of());
    }

    /**
     * Analyzes reachability from a main class with declared external leaves.
     *
     * @param classes parsed closed-world classes
     * @param mainClass JVM internal main class
     * @param declaredExternalLeaves exact declared external method identities
     * @return call graph
     */
    public CallGraph analyze(
        final Map<String, ClassFile> classes,
        final String mainClass,
        final List<EntryPoint> declaredExternalLeaves
    ) {
        final EntryPoint entry = new EntryPoint(mainClass, "main", "([Ljava/lang/String;)V");
        return analyze(classes, List.of(entry), declaredExternalLeaves);
    }

    /**
     * Analyzes reachability from explicit entry points.
     *
     * @param classes parsed closed-world classes
     * @param entries entry points
     * @return call graph
     */
    public CallGraph analyze(final Map<String, ClassFile> classes, final List<EntryPoint> entries) {
        return analyze(classes, entries, List.of());
    }

    /**
     * Analyzes reachability from explicit entry points with declared external leaves.
     *
     * @param classes parsed closed-world classes
     * @param entries entry points
     * @param declaredExternalLeaves exact declared external method identities
     * @return call graph
     */
    public CallGraph analyze(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> entries,
        final List<EntryPoint> declaredExternalLeaves
    ) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Reachability requires at least one entry point");
        }
        InstantiatedTypeAnalysis.Result instantiatedTypes = new InstantiatedTypeAnalysis.Result(List.of(), true);
        CallGraph graph;
        while (true) {
            graph = analyzePass(classes, entries, declaredExternalLeaves, instantiatedTypes, false);
            final InstantiatedTypeAnalysis.Result discovered =
                InstantiatedTypeAnalysis.analyze(classes, graph.reachableMethods(), entries);
            if (discovered.equals(instantiatedTypes)) {
                return analyzePass(classes, entries, declaredExternalLeaves, instantiatedTypes, true);
            }
            instantiatedTypes = discovered;
        }
    }

    private static CallGraph analyzePass(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> entries,
        final List<EntryPoint> declaredExternalLeaves,
        final InstantiatedTypeAnalysis.Result instantiatedTypes,
        final boolean complete
    ) {
        final MethodRefFactsCache methodRefFacts = new MethodRefFactsCache(classes);
        final EntryPointPool entryPoints = new EntryPointPool();
        final List<EntryPoint> roots = new ArrayList<>(entries.size());
        for (final EntryPoint entry : entries) {
            roots.add(entryPoints.entry(entry.className(), entry.methodName(), entry.descriptor()));
        }
        final List<EntryPoint> reachable = new ArrayList<>();
        final EntryPointMembership reachableSet = new EntryPointMembership();
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<PendingCallbackUse> pendingCallbackUses = new ArrayList<>();
        final CallEdgeTracker callEdges = new CallEdgeTracker();
        final List<EntryPoint> work = new ArrayList<>(roots);
        final EntryPointMembership workSet = new EntryPointMembership();
        for (final EntryPoint root : roots) {
            workSet.add(root);
        }
        for (final EntryPoint root : roots) {
            enqueueClassInitializer(classes, root.className(), work, workSet, root, callEdges, entryPoints);
        }
        final List<MethodRef> materializedLambdaMethods = new ArrayList<>();
        int workIndex = 0;

        while (true) {
            while (workIndex < work.size()) {
                final EntryPoint current = work.get(workIndex);
                workIndex++;
                if (!reachableSet.add(current)) {
                    continue;
                }
                reachable.add(current);
                final Optional<MethodInfo> method = method(classes, current);
                if (method.isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                        "JAVAN011",
                        "reachable method cannot be resolved",
                        current.className(),
                        new StringBuilder(current.methodName()).append(current.descriptor()).toString(),
                        current.display(),
                        "Closed-world analysis requires every reachable method to be known.",
                        "Compile application classes and provide the complete dependency classpath before running javan."
                    ));
                    continue;
                }
                if (isUnsupportedEnumSyntheticEntry(classes, current)) {
                    diagnostics.add(unsupportedEnumValueOfDiagnostic(current, current.display()));
                    continue;
                }
                final Optional<CodeAttribute> code = method.orElseThrow().code();
                if (code.isPresent()) {
                    if (ExactMethodSupport.isExactCatchNullEnumLookupMethod(classes.get(current.className()), method.orElseThrow())) {
                        enqueueEnumInitializers(classes, work, workSet, current, callEdges, entryPoints);
                        continue;
                    }
                    if (ExactMethodSupport.isExactLoweredMethod(classes.get(current.className()), method.orElseThrow())) {
                        continue;
                    }
                    for (final Instruction instruction : code.orElseThrow().instructions()) {
                        enqueueRecordReferenceObjectMethodTargets(
                            classes,
                            classes.get(current.className()),
                            method.orElseThrow(),
                            instruction,
                            work,
                            workSet,
                            current,
                            callEdges,
                            entryPoints
                        );
                        enqueueClassInitializer(classes, instruction, work, workSet, current, callEdges, entryPoints);
                        enqueueClassForNameInitializers(
                            classes,
                            instruction,
                            work,
                            workSet,
                            current,
                            callEdges,
                            entryPoints
                        );
                        enqueueLambdaApplicationCall(
                            classes,
                            method.orElseThrow(),
                            instruction,
                            work,
                            workSet,
                            current,
                            callEdges,
                            materializedLambdaMethods,
                            entryPoints
                        );
                        enqueueApplicationCall(
                            classes,
                            instruction,
                            work,
                            workSet,
                            diagnostics,
                            pendingCallbackUses,
                            current,
                            callEdges,
                            materializedLambdaMethods,
                            entryPoints,
                            methodRefFacts,
                            instantiatedTypes
                        );
                    }
                }
            }
            if (!enqueueRunnableThreadTargets(classes, reachable, reachableSet, work, workSet, callEdges, entryPoints, methodRefFacts)) {
                break;
            }
        }
        final List<EntryPoint> closedReachable = List.copyOf(reachable);
        final FunctionValueFlow.Result functionValueFlow = complete
            ? FunctionValueFlow.analyze(classes, closedReachable, declaredExternalLeaves)
            : FunctionValueFlow.Result.unavailable();
        return new CallGraph(
            roots.getFirst(),
            closedReachable,
            complete
                ? resolveCallbackDiagnostics(diagnostics, pendingCallbackUses, functionValueFlow)
                : List.of(),
            callEdges.snapshot(),
            functionValueFlow,
            instantiatedTypes
        );
    }

    private static List<Diagnostic> resolveCallbackDiagnostics(
        final List<Diagnostic> diagnostics,
        final List<PendingCallbackUse> pending,
        final FunctionValueFlow.Result flow
    ) {
        final List<Diagnostic> result = new ArrayList<>();
        result.addAll(diagnostics);
        for (final PendingCallbackUse use : pending) {
            final FunctionValueFlow.ValueKind kind = use.kind() == CallbackKind.FUNCTION
                ? flow.functionKind(
                    use.current().className(),
                    use.current().methodName(),
                    use.current().descriptor(),
                    use.offset()
                )
                : flow.supplierKind(
                    use.current().className(),
                    use.current().methodName(),
                    use.current().descriptor(),
                    use.offset()
                );
            final boolean concrete = kind == FunctionValueFlow.ValueKind.CONCRETE
                && use.closedWorldTargets();
            final boolean mixedSupplier =
                use.kind() == CallbackKind.SUPPLIER
                    && kind == FunctionValueFlow.ValueKind.MATERIALIZED_AND_CONCRETE
                    && use.closedWorldTargets();
            if (kind == FunctionValueFlow.ValueKind.MATERIALIZED || concrete || mixedSupplier) {
                continue;
            }
            result.add(use.diagnostic());
        }
        return List.copyOf(result);
    }

    private static Diagnostic functionApplyDiagnostic(
        final EntryPoint current,
        final MethodRef target
    ) {
        return Diagnostic.error(
            "JAVAN012",
            "unsupported reachable application method call",
            current.className(),
            methodSubject(current),
            target.display(),
            "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target.",
            "Provide a reachable Function implementation class or keep this exact function dispatch on the JVM until broader receiver support lands."
        );
    }

    private static String methodSubject(final EntryPoint entryPoint) {
        return new StringBuilder(entryPoint.methodName()).append(entryPoint.descriptor()).toString();
    }

    private static void enqueueRecordReferenceObjectMethodTargets(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        if (instruction.dynamicRef().isEmpty()) {
            return;
        }
        final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow();
        final boolean hashCode = "hashCode".equals(method.name());
        final Optional<RecordObjectMethodsCall> recordCall = hashCode
            ? RecordObjectMethodsCall.resolveHashCode(classFile, method, dynamicRef)
            : RecordObjectMethodsCall.resolve(classFile, method, dynamicRef);
        if (recordCall.isEmpty()) {
            return;
        }
        for (final RecordObjectMethodsCall.Component component : recordCall.orElseThrow().components()) {
            enqueueRecordReferenceObjectMethodTarget(
                classes,
                component.shape(),
                hashCode,
                RecordObjectMethodsCall.ReferenceContext.DIRECT_COMPONENT,
                work,
                workSet,
                current,
                callEdges,
                entryPoints
            );
        }
    }

    private static void enqueueRecordReferenceObjectMethodTarget(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final boolean hashCode,
        final RecordObjectMethodsCall.ReferenceContext context,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        if (!shape.valid() || shape.isArray() || shape.referenceOwner().isEmpty()) {
            return;
        }
        if (shape.isList()) {
            enqueueRecordReferenceObjectMethodTarget(
                classes,
                shape.listElement().orElseThrow(),
                hashCode,
                RecordObjectMethodsCall.ReferenceContext.LIST_ELEMENT,
                work,
                workSet,
                current,
                callEdges,
                entryPoints
            );
            return;
        }
        final String declaredOwner = shape.referenceOwner().orElseThrow();
        if ("java/lang/String".equals(declaredOwner)) {
            return;
        }
        final Optional<RecordObjectMethodsCall.DirectReferencePlan> plan =
            RecordObjectMethodsCall.referencePlan(classes, shape, hashCode, context);
        if (plan.isEmpty()) {
            return;
        }
        for (final RecordObjectMethodsCall.ReferenceTarget target : plan.orElseThrow().targets()) {
            enqueueRecordReferenceObjectMethodTarget(
                target,
                hashCode,
                work,
                workSet,
                current,
                callEdges,
                entryPoints
            );
        }
    }

    private static void enqueueRecordReferenceObjectMethodTarget(
        final RecordObjectMethodsCall.ReferenceTarget target,
        final boolean hashCode,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        if (target.identity()) {
            return;
        }
        final String methodName = hashCode ? "hashCode" : "equals";
        final String descriptor = hashCode ? "()I" : "(Ljava/lang/Object;)Z";
        final EntryPoint callee = entryPoints.entry(
            target.executableOwner(),
            methodName,
            descriptor
        );
        enqueue(work, workSet, callee);
        addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
    }

    private static boolean sameEntry(final EntryPoint left, final EntryPoint right) {
        if (left == right) {
            return true;
        }
        return left.className().equals(right.className())
            && left.methodName().equals(right.methodName())
            && left.descriptor().equals(right.descriptor());
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entryPoint) {
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return Optional.empty();
        }
        return classFile.method(entryPoint.methodName(), entryPoint.descriptor());
    }

    private static final class MethodRefFactsCache {
        private final Map<String, ClassFile> classes;
        private final Map<String, List<MethodRefFacts>> ownerBuckets = new HashMap<>();

        private MethodRefFactsCache(final Map<String, ClassFile> classes) {
            this.classes = classes;
        }

        private MethodRefFacts resolve(final MethodRef original) {
            final List<MethodRefFacts> bucket = ownerBucket(original.owner());
            for (final MethodRefFacts facts : bucket) {
                if (facts.methodName().equals(original.name()) && facts.descriptor().equals(original.descriptor())) {
                    return facts;
                }
            }
            final MethodRef target = JdkCallSupport.normalizeInheritedSupportedJdkCall(classes, original).orElse(original);
            final MethodRefFacts facts = new MethodRefFacts(
                original.name(),
                original.descriptor(),
                target,
                isImmediateThreadDispatchTarget(target),
                isReachableThreadStartTarget(target),
                isThreadTargetCarrierTarget(target)
            );
            bucket.add(facts);
            return facts;
        }

        private List<MethodRefFacts> ownerBucket(final String owner) {
            final List<MethodRefFacts> existing = existingOwnerBucket(owner);
            if (existing != null) {
                return existing;
            }
            final List<MethodRefFacts> bucket = new ArrayList<>();
            ownerBuckets.put(owner, bucket);
            return bucket;
        }

        private List<MethodRefFacts> existingOwnerBucket(final String owner) {
            return ownerBuckets.get(owner);
        }
    }

    private record MethodRefFacts(
        String methodName,
        String descriptor,
        MethodRef target,
        boolean immediateThreadDispatchTarget,
        boolean reachableThreadStartTarget,
        boolean threadTargetCarrierTarget
    ) {
    }

    private record PendingCallbackUse(
        CallbackKind kind,
        EntryPoint current,
        int offset,
        boolean closedWorldTargets,
        Diagnostic diagnostic
    ) {
    }

    private enum CallbackKind {
        FUNCTION,
        SUPPLIER
    }

    private static void enqueueApplicationCall(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final List<Diagnostic> diagnostics,
        final List<PendingCallbackUse> pendingCallbackUses,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final List<MethodRef> materializedLambdaMethods,
        final EntryPointPool entryPoints,
        final MethodRefFactsCache methodRefFacts,
        final InstantiatedTypeAnalysis.Result instantiatedTypes
    ) {
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return;
        }
        final MethodRefFacts facts = methodRefFacts.resolve(methodRef.orElseThrow());
        final MethodRef target = facts.target();
        final ClassFile targetOwner = classes.get(target.owner());
        final EnumCallKind enumCallKind = enumCallKind(targetOwner, target);
        if ((enumCallKind == EnumCallKind.INTRINSIC || enumCallKind == EnumCallKind.SUPPORTED_SYNTHETIC)
            || isSupportedArrayClone(target)) {
            return;
        }
        if (facts.immediateThreadDispatchTarget()) {
            final List<EntryPoint> targets = virtualThreadTargets(classes, current, entryPoints, methodRefFacts);
            if (!targets.isEmpty()) {
                enqueueAll(work, workSet, targets);
                addEdges(callEdges, current, targets, CallEdge.Kind.THREAD_START_TASK);
            }
            return;
        }
        if (instruction.opcode() == 185 && isIteratorForEachRemaining(target)) {
            final MethodRef consumerAccept = new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, consumerAccept, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, consumerAccept) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Iterator.forEachRemaining requires either a closed-world Consumer implementation class or a supported materialized Consumer lambda target.",
                "Provide a reachable Consumer implementation class or keep this exact iterator bulk-consumer flow on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (instruction.opcode() == 185 && isIterableForEach(target)) {
            final MethodRef consumerAccept = new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, consumerAccept, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, consumerAccept) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Iterable.forEach requires either a closed-world Consumer implementation class or a supported materialized Consumer lambda target.",
                "Provide a reachable Consumer implementation class or keep this exact iterable bulk-consumer flow on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (isCollectionRemoveIf(target)) {
            final MethodRef predicateTest = new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, predicateTest, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectPredicateLambda(classes, current, instruction)
                || containsMethodRef(materializedLambdaMethods, predicateTest)
                || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Collection.removeIf requires either a closed-world Predicate implementation class or a supported inline Predicate lambda at the call site.",
                "Provide a reachable Predicate implementation class, keep the Predicate lambda inline at the removeIf call, or keep this exact collection predicate-removal flow on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (instruction.opcode() == 185 && isMapForEach(target)) {
            final MethodRef biConsumerAccept = new MethodRef("java/util/function/BiConsumer", "accept", "(Ljava/lang/Object;Ljava/lang/Object;)V");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, biConsumerAccept, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, biConsumerAccept) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Map.forEach requires either a closed-world BiConsumer implementation class or a supported materialized BiConsumer lambda target.",
                "Provide a reachable BiConsumer implementation class or keep this exact map bulk-callback flow on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (isMapComputeIfAbsent(target)) {
            final MethodRef functionApply = new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, functionApply, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectFunctionLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.FUNCTION,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Map.computeIfAbsent requires a closed-world Function implementation class or a supported direct function lambda target.",
                    "Provide a reachable Function implementation class or keep this exact map compute-if-absent flow on the JVM until broader callback support lands."
                )
            ));
            return;
        }
        if (isOptionalFilter(target)) {
            final MethodRef predicateTest = new MethodRef("java/util/function/Predicate", "test", "(Ljava/lang/Object;)Z");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, predicateTest, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectPredicateLambda(classes, current, instruction) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Optional.filter requires a closed-world Predicate implementation class or a supported direct predicate lambda target.",
                "Provide a reachable Predicate implementation class or keep this exact optional filter flow on the JVM until broader callback support lands."
            ));
            return;
        }
        if (isOptionalMap(target)) {
            final MethodRef functionApply = new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, functionApply, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectFunctionLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.FUNCTION,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Optional.map requires a closed-world Function implementation class or a supported direct function lambda target.",
                    "Provide a reachable Function implementation class or keep this exact optional mapping flow on the JVM until broader callback support lands."
                )
            ));
            return;
        }
        if (isOptionalFlatMap(target)) {
            final MethodRef functionApply = new MethodRef("java/util/function/Function", "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, functionApply, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectFunctionLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.FUNCTION,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Optional.flatMap requires a closed-world Function implementation class or a supported direct function lambda target.",
                    "Provide a reachable Function implementation class or keep this exact optional flat-mapping flow on the JVM until broader callback support lands."
                )
            ));
            return;
        }
        if (isOptionalIfPresent(target)) {
            final MethodRef consumerAccept = new MethodRef("java/util/function/Consumer", "accept", "(Ljava/lang/Object;)V");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, consumerAccept, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, consumerAccept) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Optional.ifPresent requires either a closed-world Consumer implementation class or a supported materialized Consumer lambda target.",
                "Provide a reachable Consumer implementation class or keep this exact optional callback flow on the JVM until broader callback support lands."
            ));
            return;
        }
        if (isOptionalOr(target)) {
            final MethodRef supplierGet = new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, supplierGet, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectSupplierLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.SUPPLIER,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Optional.or requires a closed-world Supplier implementation class or a supported direct supplier lambda target.",
                    "Provide a reachable Supplier implementation class or keep this exact optional fallback-optional flow on the JVM until broader callback support lands."
                )
            ));
            return;
        }
        if (isOptionalOrElseGet(target)) {
            final MethodRef supplierGet = new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, supplierGet, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectSupplierLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.SUPPLIER,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Optional.orElseGet requires a closed-world Supplier implementation class or a supported direct supplier lambda target.",
                    "Provide a reachable Supplier implementation class or keep this exact optional fallback flow on the JVM until broader callback support lands."
                )
            ));
            return;
        }
        if (isObjectsRequireNonNullElseGet(target)) {
            final MethodRef supplierGet = new MethodRef("java/util/function/Supplier", "get", "()Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, supplierGet, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectSupplierLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.SUPPLIER,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Objects.requireNonNullElseGet requires a closed-world Supplier implementation class or a supported direct supplier lambda target.",
                    "Provide a reachable Supplier implementation class or keep this exact null-fallback flow on the JVM until broader callback support lands."
                )
            ));
            return;
        }
        if (isMapComputeIfPresent(target) || isMapCompute(target) || isMapMerge(target)) {
            final MethodRef biFunctionApply = new MethodRef("java/util/function/BiFunction", "apply", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            final List<EntryPoint> targetMethods = interfaceTargets(classes, biFunctionApply, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, biFunctionApply) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                isMapCompute(target)
                    ? "Map.compute requires either a closed-world BiFunction implementation class or a supported materialized BiFunction lambda target."
                    : isMapMerge(target)
                    ? "Map.merge requires either a closed-world BiFunction implementation class or a supported materialized BiFunction lambda target."
                    : "Map.computeIfPresent requires either a closed-world BiFunction implementation class or a supported materialized BiFunction lambda target.",
                isMapCompute(target)
                    ? "Provide a reachable BiFunction implementation class or keep this exact map compute flow on the JVM until broader receiver support lands."
                    : isMapMerge(target)
                    ? "Provide a reachable BiFunction implementation class or keep this exact map merge flow on the JVM until broader receiver support lands."
                    : "Provide a reachable BiFunction implementation class or keep this exact map compute-if-present flow on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (instruction.opcode() == 185 && isPredicateTest(target)) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectPredicateLambda(classes, current, instruction)
                || containsMethodRef(materializedLambdaMethods, target)
                || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Predicate.test requires either a closed-world Predicate implementation class or a supported materialized Predicate lambda target.",
                "Provide a reachable Predicate implementation class or keep this exact predicate dispatch on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (instruction.opcode() == 185 && isConsumerAccept(target)) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, target) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Consumer.accept requires either a closed-world Consumer implementation class or a supported materialized Consumer lambda target.",
                "Provide a reachable Consumer implementation class or keep this exact consumer dispatch on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (instruction.opcode() == 185 && isBiConsumerAccept(target)) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, target) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "BiConsumer.accept requires either a closed-world BiConsumer implementation class or a supported materialized BiConsumer lambda target.",
                "Provide a reachable BiConsumer implementation class or keep this exact bi-consumer dispatch on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (instruction.opcode() == 185 && isSupplierGet(target)) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectSupplierLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.SUPPLIER,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Supplier.get requires either a closed-world Supplier implementation class or a supported materialized Supplier lambda target.",
                    "Provide a reachable Supplier implementation class or keep this exact supplier dispatch on the JVM until broader receiver support lands."
                )
            ));
            return;
        }
        if (instruction.opcode() == 185 && isFunctionApply(target)) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (hasInlineDirectFunctionLambda(classes, current, instruction)) {
                return;
            }
            pendingCallbackUses.add(new PendingCallbackUse(
                CallbackKind.FUNCTION,
                current,
                instruction.offset(),
                !targetMethods.isEmpty(),
                functionApplyDiagnostic(current, target)
            ));
            return;
        }
        if (instruction.opcode() == 185 && isBiFunctionApply(target)) {
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, target) || !targetMethods.isEmpty()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "BiFunction.apply requires either a closed-world BiFunction implementation class or a supported materialized BiFunction lambda target.",
                "Provide a reachable BiFunction implementation class or keep this exact bi-function dispatch on the JVM until broader receiver support lands."
            ));
            return;
        }
        if (enumCallKind == EnumCallKind.UNSUPPORTED_SYNTHETIC) {
            diagnostics.add(unsupportedEnumValueOfDiagnostic(current, target.display()));
            return;
        }
        if (isJdkCall(target) || NetworkApiSupport.isNetworkCall(target)) {
            return;
        }
        if (JavanNativeSubstitutions.isSubstitutedCall(target)) {
            return;
        }
        if (instruction.opcode() == 185) {
            final Optional<EntryPoint> defaultTarget = hasInstantiatedReceiver(classes, target.owner(), instantiatedTypes)
                || containsOwner(materializedLambdaMethods, target.owner())
                ? defaultInterfaceTarget(classes, target, entryPoints)
                : Optional.empty();
            if (defaultTarget.isPresent()) {
                final EntryPoint callee = defaultTarget.orElseThrow();
                enqueue(work, workSet, callee);
                addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
                if (isCatchNullFunctionalInterfaceCall(classes, target)) {
                    final MethodRef implementationTarget = new MethodRef(target.owner(), "applyWithException", target.descriptor());
                    final List<EntryPoint> targetMethods = interfaceTargets(classes, implementationTarget, entryPoints, instantiatedTypes);
                    enqueueAll(work, workSet, targetMethods);
                    addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
                }
            }
            if (isCatchNullFunctionalInterfaceCall(classes, target)) {
                final MethodRef implementationTarget = new MethodRef(target.owner(), "applyWithException", target.descriptor());
                final List<EntryPoint> targetMethods = interfaceTargets(classes, implementationTarget, entryPoints, instantiatedTypes);
                if (!targetMethods.isEmpty()) {
                    enqueueAll(work, workSet, targetMethods);
                    addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
                }
                if (containsMethodRef(materializedLambdaMethods, target)
                    || !targetMethods.isEmpty()
                    || defaultTarget.isPresent()) {
                    return;
                }
                diagnostics.add(Diagnostic.error(
                    "JAVAN012",
                    "unsupported reachable application method call",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Catch-null functional-interface dispatch requires either a closed-world implementation class or a supported materialized lambda target.",
                    "Provide a reachable implementation class or keep this exact catch-null functional flow on the JVM until broader interface receiver support lands."
                ));
                return;
            }
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targetMethods.isEmpty()) {
                enqueueAll(work, workSet, targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
            }
            if (containsMethodRef(materializedLambdaMethods, target)
                || !targetMethods.isEmpty()
                || defaultTarget.isPresent()) {
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                methodSubject(current),
                target.display(),
                "Interface dispatch requires at least one concrete implementation in the closed world.",
                "Add an implementation class or remove the unreachable interface call."
            ));
            return;
        }
        if (targetOwner == null) {
            if (!target.owner().startsWith("java/")
                && !target.owner().startsWith("jdk/")
                && !target.owner().startsWith("sun/")
                && !NetworkApiSupport.isNetworkCall(target)) {
                diagnostics.add(Diagnostic.error(
                    "JAVAN011",
                    "reachable call target cannot be resolved",
                    current.className(),
                    methodSubject(current),
                    target.display(),
                    "Closed-world analysis requires every reachable non-JDK call target to be known.",
                    "Add the class to the project classes or dependency classpath."
                ));
            }
            return;
        }
        if (instruction.opcode() == 184) {
            final String owner = ClassInitializationGraph.staticMethodOwner(classes, target).orElse(target.owner());
            final EntryPoint callee = entryPoints.entry(owner, target.name(), target.descriptor());
            enqueue(work, workSet, callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 183 && "<init>".equals(target.name())) {
            final EntryPoint callee = entryPoints.entry(target.owner(), target.name(), target.descriptor());
            enqueue(work, workSet, callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 183) {
            final EntryPoint callee = entryPoints.entry(target.owner(), target.name(), target.descriptor());
            enqueue(work, workSet, callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 182 && isConcreteExactCallTarget(classes, target.owner())) {
            final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, target.owner(), target, entryPoints);
            if (resolved.isPresent()) {
                final EntryPoint callee = resolved.orElseThrow();
                enqueue(work, workSet, callee);
                addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
                return;
            }
        }
        if (instruction.opcode() == 182) {
            final List<EntryPoint> targets = virtualTargets(classes, target, entryPoints, instantiatedTypes);
            if (!targets.isEmpty()) {
                enqueueAll(work, workSet, targets);
                addEdges(callEdges, current, targets, CallEdge.Kind.CALL);
                return;
            }
        }
        diagnostics.add(Diagnostic.error(
            "JAVAN012",
            "unsupported reachable application method call",
            current.className(),
            methodSubject(current),
            target.display(),
            "The current native profile could not resolve a closed-world dispatch target.",
            "Make sure at least one concrete application class implements the invoked method."
        ));
    }

    private static void enqueueLambdaApplicationCall(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final List<MethodRef> materializedLambdaMethods,
        final EntryPointPool entryPoints
    ) {
        if (instruction.dynamicRef().isEmpty()) {
            return;
        }
        final Optional<LambdaMetafactoryCall> lambdaCall = LambdaMetafactoryCall.resolve(instruction.dynamicRef().orElseThrow());
        if (lambdaCall.isEmpty()) {
            return;
        }
        final LambdaMetafactoryCall resolved = lambdaCall.orElseThrow();
        final boolean materializedFunction = resolved.isMaterializedFunctionLambda(classes)
            && FunctionLambdaUse.requiresMaterialization(method, instruction)
            && !FunctionLambdaUse.isProvablyDiscardedZeroCapture(resolved, method, instruction);
        final boolean materializedSupplier = resolved.isMaterializedSupplierLambda()
            && shouldMaterializeSupplierLambda(classes, current, instruction);
        final boolean materializedBoundCustom = resolved.isMaterializedBoundCustomObjectLambda(classes);
        final boolean materializedStaticLongCustom =
            resolved.isZeroCaptureMaterializedLongObjectLambda(classes);
        final boolean materializedCapturedLongCustom =
            resolved.isMaterializedCapturedLongObjectLambda(classes);
        if (resolved.isZeroCaptureMaterializedObjectLambda()
            || materializedStaticLongCustom
            || materializedCapturedLongCustom
            || resolved.isZeroCaptureMaterializedBooleanLambda()
            || resolved.isMaterializedBiFunctionLambda()
            || resolved.isMaterializedVoidLambda()
            || materializedFunction
            || materializedSupplier
            || materializedBoundCustom) {
            final MethodRef interfaceMethod = new MethodRef(
                resolved.interfaceOwner(),
                resolved.interfaceMethodName(),
                resolved.samMethodDescriptor()
            );
            if (!containsMethodRef(materializedLambdaMethods, interfaceMethod)) {
                materializedLambdaMethods.add(interfaceMethod);
            }
        }
        if (!resolved.isDirectlyLowerable(classes)
            && !resolved.isZeroCaptureMaterializedObjectLambda()
            && !materializedStaticLongCustom
            && !materializedCapturedLongCustom
            && !resolved.isZeroCaptureMaterializedBooleanLambda()
            && !resolved.isMaterializedBiFunctionLambda()
            && !resolved.isMaterializedVoidLambda()
            && !materializedFunction
            && !materializedSupplier
            && !materializedBoundCustom) {
            return;
        }
        final MethodRef implementation = resolved.implementation();
        if (JdkCallSupport.isJdkCall(implementation) || NetworkApiSupport.isNetworkCall(implementation)) {
            return;
        }
        if (!classes.containsKey(implementation.owner())) {
            return;
        }
        final EntryPoint callee = entryPoints.entry(implementation.owner(), implementation.name(), implementation.descriptor());
        enqueue(work, workSet, callee);
        addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
        enqueueClassInitializer(classes, implementation.owner(), work, workSet, current, callEdges, entryPoints);
    }

    private static Optional<EntryPoint> defaultInterfaceTarget(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final EntryPointPool entryPoints
    ) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isInterface()) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(target.name(), target.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entryPoints.entry(target.owner(), target.name(), target.descriptor()));
    }

    private static boolean isCatchNullFunctionalInterfaceCall(final Map<String, ClassFile> classes, final MethodRef target) {
        if (!isSingleReferenceInputReferenceReturnDescriptor(target.descriptor())) {
            return false;
        }
        if (!"apply".equals(target.name()) && !"applyWithException".equals(target.name())) {
            return false;
        }
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isInterface()) {
            return false;
        }
        final Optional<MethodInfo> apply = owner.method("apply", target.descriptor());
        final Optional<MethodInfo> fallibleApply = owner.method("applyWithException", target.descriptor());
        return apply.isPresent()
            && fallibleApply.isPresent()
            && ExactMethodSupport.isExactCatchNullFunctionOrNullApplyMethod(owner, apply.orElseThrow());
    }

    private static boolean isSingleReferenceInputReferenceReturnDescriptor(final String descriptor) {
        if (descriptor == null || !descriptor.startsWith("(")) {
            return false;
        }
        final int separator = descriptor.indexOf(')');
        if (separator < 0 || separator + 1 >= descriptor.length()) {
            return false;
        }
        final String parameterDescriptor = descriptor.substring(1, separator);
        final String returnDescriptor = descriptor.substring(separator + 1);
        return isReferenceDescriptor(parameterDescriptor) && isReferenceDescriptor(returnDescriptor);
    }

    private static boolean isReferenceDescriptor(final String descriptor) {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }

    private static boolean containsMethodRef(final List<MethodRef> values, final MethodRef target) {
        for (final MethodRef value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsOwner(final List<MethodRef> values, final String owner) {
        for (final MethodRef value : values) {
            if (value.owner().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInlineDirectPredicateLambda(
        final Map<String, ClassFile> classes,
        final EntryPoint current,
        final Instruction instruction
    ) {
        return hasInlineDirectLambda(classes, current, instruction, InlineLambdaKind.PREDICATE);
    }

    private static boolean hasInlineDirectFunctionLambda(
        final Map<String, ClassFile> classes,
        final EntryPoint current,
        final Instruction instruction
    ) {
        return hasInlineDirectLambda(classes, current, instruction, InlineLambdaKind.FUNCTION);
    }

    private static boolean hasInlineDirectSupplierLambda(
        final Map<String, ClassFile> classes,
        final EntryPoint current,
        final Instruction instruction
    ) {
        return hasInlineDirectLambda(classes, current, instruction, InlineLambdaKind.SUPPLIER);
    }

    private static boolean shouldMaterializeSupplierLambda(
        final Map<String, ClassFile> classes,
        final EntryPoint current,
        final Instruction instruction
    ) {
        final Optional<MethodInfo> method = method(classes, current);
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return true;
        }
        final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
        for (int index = 0; index + 1 < instructions.size(); index++) {
            if (instructions.get(index).offset() != instruction.offset()) {
                continue;
            }
            final Optional<MethodRef> consumer = instructions.get(index + 1).methodRef();
            return consumer.isEmpty() || !isInlineSupplierConsumer(consumer.orElseThrow());
        }
        return true;
    }

    private static boolean isInlineSupplierConsumer(final MethodRef target) {
        if (isSupplierGet(target)) {
            return true;
        }
        if ("java/util/Optional".equals(target.owner())) {
            return ("or".equals(target.name()) && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(target.descriptor()))
                || ("orElseGet".equals(target.name()) && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(target.descriptor()))
                || JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(target);
        }
        return isObjectsRequireNonNullElseGet(target);
    }

    private static boolean hasInlineDirectLambda(
        final Map<String, ClassFile> classes,
        final EntryPoint current,
        final Instruction instruction,
        final InlineLambdaKind lambdaKind
    ) {
        final Optional<MethodInfo> method = method(classes, current);
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() != instruction.offset()) {
                continue;
            }
            final int producerIndex = inlineCallbackProducerIndex(instructions, index, instruction);
            return isSupportedInlineLambdaProducer(
                classes,
                method.orElseThrow(),
                instructions,
                producerIndex,
                lambdaKind
            );
        }
        return false;
    }

    private static int inlineCallbackProducerIndex(
        final List<Instruction> instructions,
        final int callIndex,
        final Instruction instruction
    ) {
        if (callIndex < 1 || instruction.methodRef().isEmpty()) {
            return -1;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        if (!isFunctionApply(target) && !isPredicateTest(target)) {
            return callIndex - 1;
        }
        final int argumentStart = simpleReferenceProducerStart(instructions, callIndex - 1);
        return argumentStart < 1 ? -1 : argumentStart - 1;
    }

    private static int simpleReferenceProducerStart(
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        if (producerIndex < 0 || producerIndex >= instructions.size()) {
            return -1;
        }
        final Instruction producer = instructions.get(producerIndex);
        if (producer.opcode() == 192) {
            return simpleReferenceProducerStart(instructions, producerIndex - 1);
        }
        if (producer.opcode() == 1
            || producer.opcode() == 18
            || producer.opcode() == 19
            || localLoadSlot(producer) >= 0) {
            return producerIndex;
        }
        if (producer.opcode() == 178
            && producer.fieldRef().isPresent()
            && isReferenceDescriptor(producer.fieldRef().orElseThrow().descriptor())) {
            return producerIndex;
        }
        return -1;
    }

    private static boolean isSupportedInlineLambdaProducer(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final List<Instruction> instructions,
        final int producerIndex,
        final InlineLambdaKind lambdaKind
    ) {
        final int transparentIndex = transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentIndex);
        if (producer.opcode() == 186 && producer.dynamicRef().isPresent()) {
            return isSupportedInlineLambda(classes, producer.dynamicRef().orElseThrow(), lambdaKind);
        }
        final int local = localLoadSlot(producer);
        if (local < 0 || method.code().orElseThrow().exceptionTableLength() != 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(
            instructions,
            transparentIndex - 1,
            local
        );
        if (storeIndex < 1 || hasInlineLambdaControlFlow(instructions, storeIndex + 1, transparentIndex)) {
            return false;
        }
        final int sourceIndex = transparentReferenceProducerIndex(instructions, storeIndex - 1);
        if (sourceIndex < 0) {
            return false;
        }
        final Instruction source = instructions.get(sourceIndex);
        return source.opcode() == 186
            && source.dynamicRef().isPresent()
            && isSupportedInlineLambda(classes, source.dynamicRef().orElseThrow(), lambdaKind);
    }

    private static boolean isSupportedInlineLambda(
        final Map<String, ClassFile> classes,
        final DynamicRef dynamicRef,
        final InlineLambdaKind lambdaKind
    ) {
        final Optional<LambdaMetafactoryCall> lambdaCall = LambdaMetafactoryCall.resolve(dynamicRef);
        return lambdaCall.isPresent()
            && matchesInlineLambdaKind(lambdaCall.orElseThrow(), lambdaKind)
            && lambdaCall.orElseThrow().isDirectlyLowerable(classes);
    }

    private static int transparentReferenceProducerIndex(
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        int result = producerIndex;
        while (result >= 0
            && result < instructions.size()
            && instructions.get(result).opcode() == 192) {
            result--;
        }
        return result;
    }

    private static boolean hasInlineLambdaControlFlow(
        final List<Instruction> instructions,
        final int start,
        final int end
    ) {
        for (int index = start; index < end; index++) {
            final int opcode = instructions.get(index).opcode();
            if ((opcode >= 153 && opcode <= 177)
                || (opcode >= 198 && opcode <= 201)
                || opcode == 191
                || opcode == 196) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesInlineLambdaKind(final LambdaMetafactoryCall lambdaCall, final InlineLambdaKind lambdaKind) {
        if (lambdaKind == InlineLambdaKind.FUNCTION) {
            return lambdaCall.isFunction();
        }
        if (lambdaKind == InlineLambdaKind.PREDICATE) {
            return lambdaCall.isPredicate();
        }
        return lambdaCall.isSupplier();
    }

    private enum InlineLambdaKind {
        FUNCTION,
        PREDICATE,
        SUPPLIER
    }

    private static boolean isBiFunctionApply(final MethodRef target) {
        return "java/util/function/BiFunction".equals(target.owner())
            && "apply".equals(target.name())
            && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor());
    }

    private static boolean isFunctionApply(final MethodRef target) {
        return "java/util/function/Function".equals(target.owner())
            && "apply".equals(target.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor());
    }

    private static boolean isSupplierGet(final MethodRef target) {
        return "java/util/function/Supplier".equals(target.owner())
            && "get".equals(target.name())
            && "()Ljava/lang/Object;".equals(target.descriptor());
    }

    private static boolean isConsumerAccept(final MethodRef target) {
        return "java/util/function/Consumer".equals(target.owner())
            && "accept".equals(target.name())
            && "(Ljava/lang/Object;)V".equals(target.descriptor());
    }

    private static boolean isBiConsumerAccept(final MethodRef target) {
        return "java/util/function/BiConsumer".equals(target.owner())
            && "accept".equals(target.name())
            && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals(target.descriptor());
    }

    private static boolean isMapComputeIfAbsent(final MethodRef target) {
        if (!"computeIfAbsent".equals(target.name())
            || !"(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(target.descriptor())) {
            return false;
        }
        return "java/util/Map".equals(target.owner())
            || "java/util/HashMap".equals(target.owner())
            || "java/util/LinkedHashMap".equals(target.owner())
            || "java/util/TreeMap".equals(target.owner());
    }

    private static boolean isOptionalFilter(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && "filter".equals(target.name())
            && "(Ljava/util/function/Predicate;)Ljava/util/Optional;".equals(target.descriptor());
    }

    private static boolean isOptionalMap(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && "map".equals(target.name())
            && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(target.descriptor());
    }

    private static boolean isOptionalFlatMap(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && "flatMap".equals(target.name())
            && "(Ljava/util/function/Function;)Ljava/util/Optional;".equals(target.descriptor());
    }

    private static boolean isOptionalIfPresent(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && "ifPresent".equals(target.name())
            && "(Ljava/util/function/Consumer;)V".equals(target.descriptor());
    }

    private static boolean isOptionalOr(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && "or".equals(target.name())
            && "(Ljava/util/function/Supplier;)Ljava/util/Optional;".equals(target.descriptor());
    }

    private static boolean isOptionalOrElseGet(final MethodRef target) {
        return "java/util/Optional".equals(target.owner())
            && "orElseGet".equals(target.name())
            && "(Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(target.descriptor());
    }

    private static boolean isObjectsRequireNonNullElseGet(final MethodRef target) {
        return "java/util/Objects".equals(target.owner())
            && "requireNonNullElseGet".equals(target.name())
            && "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;".equals(target.descriptor());
    }

    private static boolean isMapComputeIfPresent(final MethodRef target) {
        if (!"computeIfPresent".equals(target.name())
            || !"(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;".equals(target.descriptor())) {
            return false;
        }
        return "java/util/Map".equals(target.owner())
            || "java/util/HashMap".equals(target.owner())
            || "java/util/LinkedHashMap".equals(target.owner())
            || "java/util/TreeMap".equals(target.owner());
    }

    private static boolean isMapMerge(final MethodRef target) {
        if (!"merge".equals(target.name())
            || !"(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;".equals(target.descriptor())) {
            return false;
        }
        return "java/util/Map".equals(target.owner())
            || "java/util/HashMap".equals(target.owner())
            || "java/util/LinkedHashMap".equals(target.owner())
            || "java/util/TreeMap".equals(target.owner());
    }

    private static boolean isMapCompute(final MethodRef target) {
        if (!"compute".equals(target.name())
            || !"(Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;".equals(target.descriptor())) {
            return false;
        }
        return "java/util/Map".equals(target.owner())
            || "java/util/HashMap".equals(target.owner())
            || "java/util/LinkedHashMap".equals(target.owner())
            || "java/util/TreeMap".equals(target.owner());
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        if (instruction.opcode() == 178 || instruction.opcode() == 179) {
            final Optional<FieldRef> fieldRef = instruction.fieldRef();
            if (fieldRef.isPresent()) {
                final FieldRef field = fieldRef.orElseThrow();
                final String owner = ClassInitializationGraph.staticFieldOwner(classes, field).orElse(field.owner());
                enqueueClassInitializer(classes, owner, work, workSet, current, callEdges, entryPoints);
            }
            return;
        }
        if (instruction.opcode() == 184) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isPresent()) {
                final MethodRef method = methodRef.orElseThrow();
                final String owner = ClassInitializationGraph.staticMethodOwner(classes, method).orElse(method.owner());
                enqueueClassInitializer(classes, owner, work, workSet, current, callEdges, entryPoints);
            }
            return;
        }
        if (instruction.opcode() == 187) {
            final Optional<String> className = instruction.className();
            if (className.isPresent()) {
                enqueueClassInitializer(classes, className.orElseThrow(), work, workSet, current, callEdges, entryPoints);
            }
        }
    }

    private static void enqueueClassForNameInitializers(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        if (instruction.methodRef().isEmpty()) {
            return;
        }
        final MethodRef method = instruction.methodRef().orElseThrow();
        if (!"java/lang/Class".equals(method.owner())
            || !"forName".equals(method.name())
            || !"(Ljava/lang/String;)Ljava/lang/Class;".equals(method.descriptor())) {
            return;
        }
        final List<String> owners = new ArrayList<>(classes.keySet());
        for (int index = 1; index < owners.size(); index++) {
            final String owner = owners.get(index);
            int insertion = index;
            while (insertion > 0 && Strings2.compareAscii(owners.get(insertion - 1), owner) > 0) {
                owners.set(insertion, owners.get(insertion - 1));
                insertion--;
            }
            owners.set(insertion, owner);
        }
        for (final String owner : owners) {
            enqueueClassInitializer(classes, owner, work, workSet, current, callEdges, entryPoints);
        }
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final String owner,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        for (final String initializerOwner : ClassInitializationGraph.initializerOwners(classes, owner)) {
            final ClassFile classFile = classes.get(initializerOwner);
            final Optional<MethodInfo> method = classFile.method("<clinit>", "()V");
            if (method.isEmpty()) {
                continue;
            }
            final MethodInfo classInitializer = method.orElseThrow();
            final EntryPoint callee = entryPoints.entry(initializerOwner, classInitializer.name(), classInitializer.descriptor());
            enqueue(work, workSet, callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CLASS_INITIALIZER);
        }
    }

    private static void enqueueEnumInitializers(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final EntryPoint current,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints
    ) {
        final List<String> owners = new ArrayList<>();
        for (final ClassFile classFile : classes.values()) {
            if (classFile.isEnum()) {
                int index = 0;
                while (index < owners.size() && Strings2.compareAscii(owners.get(index), classFile.name()) < 0) {
                    index++;
                }
                owners.add(index, classFile.name());
            }
        }
        for (final String owner : owners) {
            enqueueClassInitializer(classes, owner, work, workSet, current, callEdges, entryPoints);
        }
    }

    private static boolean isConcreteExactCallTarget(final Map<String, ClassFile> classes, final String owner) {
        final ClassFile target = classes.get(owner);
        if (target == null || target.isInterface()) {
            return false;
        }
        if (target.isFinal()) {
            return true;
        }
        for (final ClassFile candidate : classes.values()) {
            if (owner.equals(candidate.superName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUnsupportedEnumSyntheticEntry(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile owner = classes.get(entry.className());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"valueOf".equals(entry.methodName())) {
            return false;
        }
        return enumValueOfDescriptor(entry.className()).equals(entry.descriptor());
    }

    private static Diagnostic unsupportedEnumValueOfDiagnostic(final EntryPoint current, final String subject) {
        return Diagnostic.error(
            "JAVAN015",
            "unsupported reachable enum synthetic method",
            current.className(),
            methodSubject(current),
            subject,
            "Enum.valueOf(String) requires deterministic enum lookup lowering, which is not implemented yet.",
            "Use direct enum constants, values(), ordinal(), name(), toString(), or enum switch until valueOf lowering is implemented."
        );
    }

    private static EnumCallKind enumCallKind(final ClassFile owner, final MethodRef target) {
        if (owner == null || !owner.isEnum()) {
            return EnumCallKind.NONE;
        }
        if ("ordinal".equals(target.name()) && "()I".equals(target.descriptor())) {
            return EnumCallKind.SUPPORTED_SYNTHETIC;
        }
        if ("values".equals(target.name()) && target.descriptor().equals(enumValuesDescriptor(target.owner()))) {
            return EnumCallKind.SUPPORTED_SYNTHETIC;
        }
        if ("()Ljava/lang/String;".equals(target.descriptor())) {
            if ("name".equals(target.name()) || "toString".equals(target.name())) {
                return EnumCallKind.INTRINSIC;
            }
        }
        if ("valueOf".equals(target.name()) && enumValueOfDescriptor(target.owner()).equals(target.descriptor())) {
            return EnumCallKind.UNSUPPORTED_SYNTHETIC;
        }
        return EnumCallKind.NONE;
    }

    private static String enumValuesDescriptor(final String owner) {
        return new StringBuilder("()[L").append(owner).append(';').toString();
    }

    private static String enumValueOfDescriptor(final String owner) {
        return new StringBuilder("(Ljava/lang/String;)L").append(owner).append(';').toString();
    }

    private static boolean isJdkCall(final MethodRef target) {
        if (target.owner().startsWith("java/")) {
            return true;
        }
        if (target.owner().startsWith("jdk/")) {
            return true;
        }
        if (target.owner().startsWith("sun/")) {
            return true;
        }
        return false;
    }

    private static boolean isSupportedArrayClone(final MethodRef target) {
        if (!target.owner().startsWith("[")) {
            return false;
        }
        if (!"clone".equals(target.name())) {
            return false;
        }
        if (!"()Ljava/lang/Object;".equals(target.descriptor())) {
            return false;
        }
        return true;
    }

    private static boolean isThreadStart(final MethodRef target) {
        return "java/lang/Thread".equals(target.owner())
            && "start".equals(target.name())
            && "()V".equals(target.descriptor());
    }

    private static boolean isVirtualThreadStart(final MethodRef target) {
        return "java/lang/Thread".equals(target.owner())
            && "startVirtualThread".equals(target.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(target.descriptor());
    }

    private static boolean isVirtualThreadBuilderStart(final MethodRef target) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(target);
    }

    private static boolean isVirtualThreadBuilderUnstarted(final MethodRef target) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualUnstarted(target);
    }

    private static boolean isVirtualThreadBuilderFactory(final MethodRef target) {
        return VirtualThreadInvokePatterns.isThreadBuilderVirtualFactory(target);
    }

    private static boolean isThreadFactoryNewThread(final MethodRef target) {
        return VirtualThreadInvokePatterns.isThreadFactoryNewThread(target);
    }

    private static boolean isExecutorsNewVirtualThreadPerTaskExecutor(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(target);
    }

    private static boolean isExecutorsNewThreadPerTaskExecutor(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(target);
    }

    private static boolean isExecutorExecute(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorExecute(target);
    }

    private static boolean isExecutorSubmit(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorServiceSubmit(target);
    }

    private static boolean isScheduledThreadPoolExecutorSchedule(final MethodRef target) {
        return VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorSchedule(target);
    }

    private static boolean isScheduledThreadPoolExecutorScheduleAtFixedRate(final MethodRef target) {
        return VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleAtFixedRate(target);
    }

    private static boolean isScheduledThreadPoolExecutorScheduleWithFixedDelay(final MethodRef target) {
        return VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleWithFixedDelay(target);
    }

    private static boolean isScheduledExecutorServiceSchedule(final MethodRef target) {
        return VirtualThreadInvokePatterns.isScheduledExecutorServiceSchedule(target);
    }

    private static boolean isScheduledExecutorServiceScheduleAtFixedRate(final MethodRef target) {
        return VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleAtFixedRate(target);
    }

    private static boolean isScheduledExecutorServiceScheduleWithFixedDelay(final MethodRef target) {
        return VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleWithFixedDelay(target);
    }

    private static boolean isExecutorServiceShutdown(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorServiceShutdown(target);
    }

    private static boolean isExecutorServiceClose(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorServiceClose(target);
    }

    private static boolean isRunnableThreadConstructor(final MethodRef target) {
        return "java/lang/Thread".equals(target.owner())
            && "<init>".equals(target.name())
            && ("(Ljava/lang/Runnable;)V".equals(target.descriptor())
            || "(Ljava/lang/Runnable;Ljava/lang/String;)V".equals(target.descriptor()));
    }

    private static boolean isIteratorForEachRemaining(final MethodRef target) {
        return "java/util/Iterator".equals(target.owner())
            && "forEachRemaining".equals(target.name())
            && "(Ljava/util/function/Consumer;)V".equals(target.descriptor());
    }

    private static boolean isIterableForEach(final MethodRef target) {
        return "java/lang/Iterable".equals(target.owner())
            && "forEach".equals(target.name())
            && "(Ljava/util/function/Consumer;)V".equals(target.descriptor());
    }

    private static boolean isCollectionRemoveIf(final MethodRef target) {
        return ("java/util/Collection".equals(target.owner())
            || "java/util/List".equals(target.owner())
            || "java/util/ArrayList".equals(target.owner())
            || "java/util/Set".equals(target.owner())
            || "java/util/HashSet".equals(target.owner())
            || "java/util/LinkedHashSet".equals(target.owner()))
            && "removeIf".equals(target.name())
            && "(Ljava/util/function/Predicate;)Z".equals(target.descriptor());
    }

    private static boolean isMapForEach(final MethodRef target) {
        return "java/util/Map".equals(target.owner())
            && "forEach".equals(target.name())
            && "(Ljava/util/function/BiConsumer;)V".equals(target.descriptor());
    }

    private static boolean isPredicateTest(final MethodRef target) {
        return "java/util/function/Predicate".equals(target.owner())
            && "test".equals(target.name())
            && "(Ljava/lang/Object;)Z".equals(target.descriptor());
    }

    private static List<EntryPoint> interfaceTargets(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final EntryPointPool entryPoints,
        final InstantiatedTypeAnalysis.Result instantiatedTypes
    ) {
        final List<EntryPoint> targets = new ArrayList<>();
        for (final InstantiatedTypeAnalysis.Fact fact : instantiatedTypes.facts()) {
            final ClassFile candidate = classes.get(fact.type());
            if (candidate == null || candidate.isInterface()) {
                continue;
            }
            if (!isAssignableTo(classes, candidate.name(), target.owner())) {
                continue;
            }
            final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(
                classes,
                candidate.name(),
                target,
                entryPoints
            );
            if (resolved.isPresent() && !targets.contains(resolved.orElseThrow())) {
                targets.add(resolved.orElseThrow());
            }
        }
        return List.copyOf(targets);
    }

    private static boolean hasInstantiatedReceiver(
        final Map<String, ClassFile> classes,
        final String declaredType,
        final InstantiatedTypeAnalysis.Result instantiatedTypes
    ) {
        for (final InstantiatedTypeAnalysis.Fact fact : instantiatedTypes.facts()) {
            if (isAssignableTo(classes, fact.type(), declaredType)) {
                return true;
            }
        }
        return false;
    }

    private static List<EntryPoint> virtualTargets(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final EntryPointPool entryPoints,
        final InstantiatedTypeAnalysis.Result instantiatedTypes
    ) {
        final List<EntryPoint> targets = new ArrayList<>();
        final EntryPointMembership targetSet = new EntryPointMembership();
        for (final InstantiatedTypeAnalysis.Fact fact : instantiatedTypes.facts()) {
            final ClassFile candidate = classes.get(fact.type());
            if (candidate == null || candidate.isInterface()) {
                continue;
            }
            if (!isSubtypeOf(classes, candidate.name(), target.owner())) {
                continue;
            }
            final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(classes, candidate.name(), target, entryPoints);
            if (resolved.isPresent()) {
                final EntryPoint entryPoint = resolved.orElseThrow();
                if (targetSet.add(entryPoint)) {
                    targets.add(entryPoint);
                }
            }
        }
        return List.copyOf(targets);
    }

    private static boolean enqueueRunnableThreadTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final EntryPointMembership reachableSet,
        final List<EntryPoint> work,
        final EntryPointMembership workSet,
        final CallEdgeTracker callEdges,
        final EntryPointPool entryPoints,
        final MethodRefFactsCache methodRefFacts
    ) {
        final List<EntryPoint> targets = runnableThreadTargets(classes, reachable, entryPoints, methodRefFacts);
        final List<EntryPoint> starters = threadStartMethods(classes, reachable);
        boolean added = false;
        for (final EntryPoint target : targets) {
            if (!reachableSet.contains(target) && workSet.add(target)) {
                work.add(target);
                added = true;
            }
        }
        for (final EntryPoint starter : starters) {
            addEdges(callEdges, starter, targets, CallEdge.Kind.THREAD_START_TASK);
        }
        return added;
    }

    private static List<EntryPoint> virtualThreadTargets(
        final Map<String, ClassFile> classes,
        final EntryPoint current,
        final EntryPointPool entryPoints,
        final MethodRefFactsCache methodRefFacts
    ) {
        final Optional<MethodInfo> method = method(classes, current);
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return List.of();
        }
        final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
        final List<EntryPoint> result = new ArrayList<>();
        final EntryPointMembership resultSet = new EntryPointMembership();
        boolean unknownRunnableTarget = false;
        for (int index = 0; index < instructions.size(); index++) {
            final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
            if (methodRef.isEmpty()) {
                continue;
            }
            if (!methodRefFacts.resolve(methodRef.orElseThrow()).threadTargetCarrierTarget()) {
                continue;
            }
            final Optional<EntryPoint> inferredTarget = inferVirtualThreadTarget(classes, instructions, index, entryPoints);
            if (inferredTarget.isPresent()) {
                final EntryPoint entryPoint = inferredTarget.orElseThrow();
                if (resultSet.add(entryPoint)) {
                    result.add(entryPoint);
                }
            } else {
                unknownRunnableTarget = true;
            }
        }
        if (unknownRunnableTarget) {
            for (final EntryPoint target : allRunnableThreadTargets(classes, entryPoints)) {
                if (resultSet.add(target)) {
                    result.add(target);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Optional<EntryPoint> inferVirtualThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final EntryPointPool entryPoints
    ) {
        if (startIndex < 1) {
            return Optional.empty();
        }
        final Optional<String> runnableOwner = supportedRunnableOwner(classes, instructions, startIndex - 1);
        if (runnableOwner.isEmpty()) {
            return Optional.empty();
        }
        final Optional<MethodRef> startRef = instructions.get(startIndex).methodRef();
        if (startRef.isPresent()) {
            if (isVirtualThreadBuilderStart(startRef.orElseThrow())
                && !supportedVirtualThreadBuilderReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if (isVirtualThreadBuilderUnstarted(startRef.orElseThrow())
                && !supportedVirtualThreadBuilderReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if (isThreadFactoryNewThread(startRef.orElseThrow())
                && !supportedVirtualThreadFactoryReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if ((isExecutorExecute(startRef.orElseThrow()) || isExecutorSubmit(startRef.orElseThrow()))
                && !supportedVirtualThreadExecutorReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
        }
        return lowerableResolvedVirtualTarget(classes, runnableOwner.orElseThrow(), RUNNABLE_RUN, entryPoints);
    }

    private static Optional<String> supportedRunnableOwner(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        if (producerIndex < 0 || producerIndex >= instructions.size()) {
            return Optional.empty();
        }
        final Instruction producer = instructions.get(producerIndex);
        final Optional<MethodRef> constructorRef = producer.methodRef();
        if (constructorRef.isPresent()) {
            final MethodRef target = constructorRef.orElseThrow();
            if ("<init>".equals(target.name())
                && isAssignableTo(classes, target.owner(), RUNNABLE_RUN.owner())
                && !isAssignableTo(classes, target.owner(), "java/lang/Thread")
                && producerIndex >= 2
                && instructions.get(producerIndex - 1).opcode() == 89) {
                final Instruction allocation = instructions.get(producerIndex - 2);
                if (allocation.opcode() == 187
                    && allocation.className().isPresent()
                    && allocation.className().orElseThrow().equals(target.owner())) {
                    return Optional.of(target.owner());
                }
            }
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return Optional.empty();
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, producerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return Optional.empty();
        }
        return supportedRunnableOwner(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedVirtualThreadBuilderReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, startIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedVirtualThreadBuilderProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (isThreadOfVirtual(methodRef.orElseThrow())) {
                return true;
            }
            if (producer.opcode() == 184
                && VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(classes, methodRef.orElseThrow())) {
                return true;
            }
            if (isThreadBuilderOfVirtualName(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(
                    classes,
                    instructions,
                    transparentProducerIndex - virtualThreadBuilderNameProducerOffset(methodRef.orElseThrow())
                );
            }
        }
        if (transparentProducerIndex < 2) {
            return false;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedVirtualThreadFactoryReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, startIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedVirtualThreadFactoryProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (isVirtualThreadBuilderFactory(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(classes, instructions, transparentProducerIndex - 1);
            }
            if (producer.opcode() == 184
                && VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(classes, methodRef.orElseThrow())) {
                return true;
            }
        }
        if (transparentProducerIndex < 2) {
            return false;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedVirtualThreadExecutorReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex
    ) {
        final Optional<MethodRef> methodRef = instructions.get(instructionIndex).methodRef();
        if (methodRef.isPresent()
            && (isExecutorServiceShutdown(methodRef.orElseThrow()) || isExecutorServiceClose(methodRef.orElseThrow()))) {
            return supportedVirtualThreadExecutorProducer(classes, instructions, instructionIndex - 1);
        }
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, instructionIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadExecutorProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedVirtualThreadExecutorProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (isExecutorsNewVirtualThreadPerTaskExecutor(methodRef.orElseThrow())) {
                return true;
            }
            if (isExecutorsNewThreadPerTaskExecutor(methodRef.orElseThrow())) {
                return supportedVirtualThreadFactoryProducer(classes, instructions, transparentProducerIndex - 1);
            }
        }
        if (transparentProducerIndex < 2) {
            return false;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadExecutorProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean isThreadOfVirtual(final MethodRef target) {
        return VirtualThreadInvokePatterns.isThreadOfVirtual(target);
    }

    private static boolean isThreadBuilderOfVirtualName(final MethodRef target) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(target);
    }

    private static int virtualThreadBuilderNameProducerOffset(final MethodRef target) {
        return VirtualThreadInvokePatterns.virtualThreadBuilderNameProducerOffset(target);
    }

    private static int localLoadSlot(final Instruction instruction) {
        return VirtualThreadInvokePatterns.localLoadSlot(instruction);
    }

    private static int localStoreSlot(final Instruction instruction) {
        return VirtualThreadInvokePatterns.localStoreSlot(instruction);
    }

    private static List<EntryPoint> threadStartMethods(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<EntryPoint> result = new ArrayList<>();
        final EntryPointMembership resultSet = new EntryPointMembership();
        for (final EntryPoint reachableMethod : reachable) {
            final Optional<MethodInfo> method = method(classes, reachableMethod);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<MethodRef> methodRef = instruction.methodRef();
                if (methodRef.isPresent() && (isThreadStart(methodRef.orElseThrow())
                    || isVirtualThreadStart(methodRef.orElseThrow())
                    || isVirtualThreadBuilderStart(methodRef.orElseThrow()))) {
                    if (resultSet.add(reachableMethod)) {
                        result.add(reachableMethod);
                    }
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addEdges(
        final CallEdgeTracker callEdges,
        final EntryPoint caller,
        final List<EntryPoint> callees,
        final CallEdge.Kind kind
    ) {
        for (final EntryPoint callee : callees) {
            addEdge(callEdges, caller, callee, kind);
        }
    }

    private static void addEdge(
        final CallEdgeTracker callEdges,
        final EntryPoint caller,
        final EntryPoint callee,
        final CallEdge.Kind kind
    ) {
        callEdges.add(caller, callee, kind);
    }

    private static List<EntryPoint> runnableThreadTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final EntryPointPool entryPoints,
        final MethodRefFactsCache methodRefFacts
    ) {
        if (!containsReachableThreadStart(classes, reachable, methodRefFacts)) {
            return List.of();
        }
        boolean sawRunnableThreadConstruction = false;
        boolean unknownRunnableTarget = false;
        final List<EntryPoint> exactTargets = new ArrayList<>();
        final EntryPointMembership exactTargetSet = new EntryPointMembership();
        for (final EntryPoint reachableMethod : reachable) {
            final Optional<MethodInfo> method = method(classes, reachableMethod);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
                if (methodRef.isEmpty() || (!isRunnableThreadConstructor(methodRef.orElseThrow())
                    && !isVirtualThreadBuilderUnstarted(methodRef.orElseThrow())
                    && !isThreadFactoryNewThread(methodRef.orElseThrow()))) {
                    continue;
                }
                sawRunnableThreadConstruction = true;
                final Optional<EntryPoint> inferredTarget = inferRunnableThreadTarget(classes, instructions, index, entryPoints);
                if (inferredTarget.isPresent()) {
                    final EntryPoint entryPoint = inferredTarget.orElseThrow();
                    if (exactTargetSet.add(entryPoint)) {
                        exactTargets.add(entryPoint);
                    }
                } else {
                    unknownRunnableTarget = true;
                }
            }
        }
        if (!sawRunnableThreadConstruction) {
            return List.of();
        }
        if (!unknownRunnableTarget && !exactTargets.isEmpty()) {
            return List.copyOf(exactTargets);
        }
        return allRunnableThreadTargets(classes, entryPoints);
    }

    private static boolean containsReachableThreadStart(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final MethodRefFactsCache methodRefFacts
    ) {
        for (final EntryPoint reachableMethod : reachable) {
            final Optional<MethodInfo> method = method(classes, reachableMethod);
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<MethodRef> methodRef = instruction.methodRef();
                if (methodRef.isPresent() && methodRefFacts.resolve(methodRef.orElseThrow()).reachableThreadStartTarget()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isImmediateThreadDispatchTarget(final MethodRef target) {
        return isVirtualThreadStart(target)
            || isVirtualThreadBuilderStart(target)
            || isExecutorExecute(target)
            || isExecutorSubmit(target)
            || isScheduledThreadPoolExecutorSchedule(target)
            || isScheduledThreadPoolExecutorScheduleAtFixedRate(target)
            || isScheduledThreadPoolExecutorScheduleWithFixedDelay(target)
            || isScheduledExecutorServiceSchedule(target)
            || isScheduledExecutorServiceScheduleAtFixedRate(target)
            || isScheduledExecutorServiceScheduleWithFixedDelay(target);
    }

    private static boolean isReachableThreadStartTarget(final MethodRef target) {
        return isThreadStart(target) || isImmediateThreadDispatchTarget(target);
    }

    private static boolean isThreadTargetCarrierTarget(final MethodRef target) {
        return isImmediateThreadDispatchTarget(target)
            || isThreadFactoryNewThread(target);
    }

    private static Optional<EntryPoint> inferRunnableThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int threadConstructorIndex,
        final EntryPointPool entryPoints
    ) {
        final Optional<MethodRef> targetRef = instructions.get(threadConstructorIndex).methodRef();
        if (targetRef.isPresent() && (isThreadFactoryNewThread(targetRef.orElseThrow())
            || isVirtualThreadBuilderUnstarted(targetRef.orElseThrow()))) {
            return inferVirtualThreadTarget(classes, instructions, threadConstructorIndex, entryPoints);
        }
        final int runnableConstructorOffset = targetRef.isPresent()
            && "(Ljava/lang/Runnable;Ljava/lang/String;)V".equals(targetRef.orElseThrow().descriptor())
            ? 2
            : 1;
        final int runnableConstructorIndex = threadConstructorIndex - runnableConstructorOffset;
        if (runnableConstructorIndex < 2) {
            return Optional.empty();
        }
        final Instruction runnableConstructor = instructions.get(runnableConstructorIndex);
        final Optional<MethodRef> runnableConstructorRef = runnableConstructor.methodRef();
        if (runnableConstructorRef.isEmpty()) {
            return Optional.empty();
        }
        final MethodRef target = runnableConstructorRef.orElseThrow();
        if (!"<init>".equals(target.name())
            || !isAssignableTo(classes, target.owner(), RUNNABLE_RUN.owner())
            || isAssignableTo(classes, target.owner(), "java/lang/Thread")) {
            return Optional.empty();
        }
        if (instructions.get(runnableConstructorIndex - 1).opcode() != 89) {
            return Optional.empty();
        }
        final Instruction allocation = instructions.get(runnableConstructorIndex - 2);
        final Optional<String> className = allocation.className();
        if (allocation.opcode() != 187
            || className.isEmpty()
            || !className.orElseThrow().equals(target.owner())) {
            return Optional.empty();
        }
        return lowerableResolvedVirtualTarget(classes, target.owner(), RUNNABLE_RUN, entryPoints);
    }

    private static List<EntryPoint> allRunnableThreadTargets(
        final Map<String, ClassFile> classes,
        final EntryPointPool entryPoints
    ) {
        final List<EntryPoint> targets = new ArrayList<>();
        final EntryPointMembership targetSet = new EntryPointMembership();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()
                || !isAssignableTo(classes, candidate.name(), RUNNABLE_RUN.owner())
                || isAssignableTo(classes, candidate.name(), "java/lang/Thread")) {
                continue;
            }
            final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(classes, candidate.name(), RUNNABLE_RUN, entryPoints);
            if (resolved.isPresent()) {
                final EntryPoint entryPoint = resolved.orElseThrow();
                if (targetSet.add(entryPoint)) {
                    targets.add(entryPoint);
                }
            }
        }
        return List.copyOf(targets);
    }

    private static Optional<EntryPoint> resolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target,
        final EntryPointPool entryPoints
    ) {
        String current = receiver;
        while (classes.containsKey(current)) {
            final ClassFile classFile = classes.get(current);
            if (classFile.method(target.name(), target.descriptor()).isPresent()) {
                return Optional.of(entryPoints.entry(current, target.name(), target.descriptor()));
            }
            current = classFile.superName();
        }
        return Optional.empty();
    }

    private static Optional<EntryPoint> lowerableResolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target,
        final EntryPointPool entryPoints
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, target, entryPoints);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = method(classes, resolved.orElseThrow());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return resolved;
    }

    private static boolean isSubtypeOf(final Map<String, ClassFile> classes, final String candidate, final String expectedSuper) {
        String current = candidate;
        while (classes.containsKey(current)) {
            if (current.equals(expectedSuper)) {
                return true;
            }
            current = classes.get(current).superName();
        }
        return current.equals(expectedSuper);
    }

    private static boolean isAssignableTo(final Map<String, ClassFile> classes, final String candidate, final String expected) {
        String current = candidate;
        final List<String> visitedClasses = new ArrayList<>();
        while (current != null && !current.isEmpty()) {
            if (current.equals(expected)) {
                return true;
            }
            if (visitedClasses.contains(current)) {
                return false;
            }
            visitedClasses.add(current);
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                return current.equals(expected);
            }
            if (hasInterface(classes, classFile, expected, new ArrayList<>())) {
                return true;
            }
            current = classFile.superName();
        }
        return false;
    }

    private static boolean hasInterface(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final String expected,
        final List<String> visited
    ) {
        for (final String interfaceName : classFile.interfaces()) {
            if (interfaceName.equals(expected)) {
                return true;
            }
            if (visited.contains(interfaceName)) {
                continue;
            }
            visited.add(interfaceName);
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null && hasInterface(classes, interfaceClass, expected, visited)) {
                return true;
            }
        }
        return false;
    }

    private enum EnumCallKind {
        NONE,
        INTRINSIC,
        SUPPORTED_SYNTHETIC,
        UNSUPPORTED_SYNTHETIC
    }

    private static final class CallEdgeTracker {
        private final List<CallEdge> edges = new ArrayList<>();
        private final Map<String, List<CallerBucket>> ownerBuckets = new HashMap<>();

        private void add(final EntryPoint caller, final EntryPoint callee, final CallEdge.Kind kind) {
            final List<CallEdge> bucket = bucket(caller);
            for (final CallEdge edge : bucket) {
                if (edge.kind() == kind && sameEntry(edge.callee(), callee)) {
                    return;
                }
            }
            final CallEdge edge = new CallEdge(caller, callee, kind);
            bucket.add(edge);
            edges.add(edge);
        }

        private List<CallEdge> snapshot() {
            return List.copyOf(edges);
        }

        private List<CallEdge> bucket(final EntryPoint caller) {
            final List<CallerBucket> ownerBucket = ownerBucket(caller.className());
            for (final CallerBucket bucket : ownerBucket) {
                if (sameEntry(bucket.caller(), caller)) {
                    return bucket.edges();
                }
            }
            final CallerBucket bucket = new CallerBucket(caller, new ArrayList<>());
            ownerBucket.add(bucket);
            return bucket.edges();
        }

        private List<CallerBucket> ownerBucket(final String owner) {
            final List<CallerBucket> existing = ownerBuckets.get(owner);
            if (existing != null) {
                return existing;
            }
            final List<CallerBucket> bucket = new ArrayList<>();
            ownerBuckets.put(owner, bucket);
            return bucket;
        }
    }

    private static void enqueue(final List<EntryPoint> work, final EntryPointMembership workSet, final EntryPoint entryPoint) {
        if (workSet.add(entryPoint)) {
            work.add(entryPoint);
        }
    }

    private static void enqueueAll(final List<EntryPoint> work, final EntryPointMembership workSet, final List<EntryPoint> entries) {
        for (final EntryPoint entry : entries) {
            enqueue(work, workSet, entry);
        }
    }

    private static final class EntryPointPool {
        private final Map<String, List<EntryPoint>> buckets = new HashMap<>();

        private EntryPoint entry(final String className, final String methodName, final String descriptor) {
            final List<EntryPoint> ownerBucket = ownerBucket(className);
            for (final EntryPoint entry : ownerBucket) {
                if (entry.methodName().equals(methodName) && entry.descriptor().equals(descriptor)) {
                    return entry;
                }
            }
            final EntryPoint entry = new EntryPoint(className, methodName, descriptor);
            ownerBucket.add(entry);
            return entry;
        }

        private List<EntryPoint> ownerBucket(final String owner) {
            final List<EntryPoint> existing = buckets.get(owner);
            if (existing != null) {
                return existing;
            }
            final List<EntryPoint> bucket = new ArrayList<>();
            buckets.put(owner, bucket);
            return bucket;
        }
    }

    private static final class EntryPointMembership {
        private final Map<String, List<EntryPoint>> buckets = new HashMap<>();

        private boolean add(final EntryPoint entryPoint) {
            final List<EntryPoint> ownerBucket = ownerBucket(entryPoint.className());
            for (final EntryPoint existing : ownerBucket) {
                if (sameEntry(existing, entryPoint)) {
                    return false;
                }
            }
            ownerBucket.add(entryPoint);
            return true;
        }

        private boolean contains(final EntryPoint entryPoint) {
            final List<EntryPoint> ownerBucket = existingOwnerBucket(entryPoint.className());
            if (ownerBucket == null) {
                return false;
            }
            for (final EntryPoint existing : ownerBucket) {
                if (sameEntry(existing, entryPoint)) {
                    return true;
                }
            }
            return false;
        }

        private List<EntryPoint> ownerBucket(final String owner) {
            final List<EntryPoint> existing = existingOwnerBucket(owner);
            if (existing != null) {
                return existing;
            }
            final List<EntryPoint> bucket = new ArrayList<>();
            buckets.put(owner, bucket);
            return bucket;
        }

        private List<EntryPoint> existingOwnerBucket(final String owner) {
            return buckets.get(owner);
        }
    }

    private record CallerBucket(EntryPoint caller, List<CallEdge> edges) {
    }

}
