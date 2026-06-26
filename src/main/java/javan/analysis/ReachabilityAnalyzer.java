package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.NetworkApiSupport;
import javan.compat.JavanNativeSubstitutions;
import javan.verify.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a small closed-world call graph for reachable application methods.
 */
public final class ReachabilityAnalyzer {
    private static final MethodRef RUNNABLE_RUN = new MethodRef("java/lang/Runnable", "run", "()V");

    /**
     * Analyzes reachability from a main class.
     *
     * @param classes parsed application classes
     * @param mainClass JVM internal main class
     * @return call graph
     */
    public CallGraph analyze(final Map<String, ClassFile> classes, final String mainClass) {
        final EntryPoint entry = new EntryPoint(mainClass, "main", "([Ljava/lang/String;)V");
        return analyze(classes, List.of(entry));
    }

    /**
     * Analyzes reachability from explicit entry points.
     *
     * @param classes parsed application classes
     * @param entries entry points
     * @return call graph
     */
    public CallGraph analyze(final Map<String, ClassFile> classes, final List<EntryPoint> entries) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Reachability requires at least one entry point");
        }
        final List<EntryPoint> reachable = new ArrayList<>();
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<CallEdge> callEdges = new ArrayList<>();
        final List<EntryPoint> work = new ArrayList<>(entries);
        int workIndex = 0;

        while (true) {
            while (workIndex < work.size()) {
                final EntryPoint current = work.get(workIndex);
                workIndex++;
                if (containsEntry(reachable, current)) {
                    continue;
                }
                reachable.add(current);
                final Optional<MethodInfo> method = method(classes, current);
                if (method.isEmpty()) {
                    diagnostics.add(Diagnostic.error(
                        "JAVAN011",
                        "reachable method cannot be resolved",
                        current.className(),
                        current.methodName() + current.descriptor(),
                        current.display(),
                        "Closed-world analysis requires every reachable application method to be known.",
                        "Compile all application classes before running javan."
                    ));
                    continue;
                }
                if (isUnsupportedEnumSyntheticEntry(classes, current)) {
                    diagnostics.add(unsupportedEnumValueOfDiagnostic(current, current.display()));
                    continue;
                }
                final Optional<CodeAttribute> code = method.orElseThrow().code();
                if (code.isPresent()) {
                    for (final Instruction instruction : code.orElseThrow().instructions()) {
                        enqueueClassInitializer(classes, instruction, work, current, callEdges);
                        enqueueApplicationCall(classes, instruction, work, diagnostics, current, callEdges);
                    }
                }
            }
            if (!enqueueRunnableThreadTargets(classes, reachable, work, callEdges)) {
                break;
            }
        }
        return new CallGraph(entries.getFirst(), List.copyOf(reachable), List.copyOf(diagnostics), List.copyOf(callEdges));
    }

    private static boolean containsEntry(final List<EntryPoint> entries, final EntryPoint target) {
        for (final EntryPoint entry : entries) {
            if (sameEntry(entry, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameEntry(final EntryPoint left, final EntryPoint right) {
        if (!left.className().equals(right.className())) {
            return false;
        }
        if (!left.methodName().equals(right.methodName())) {
            return false;
        }
        if (!left.descriptor().equals(right.descriptor())) {
            return false;
        }
        return true;
    }

    private static Optional<MethodInfo> method(final Map<String, ClassFile> classes, final EntryPoint entryPoint) {
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return Optional.empty();
        }
        return classFile.method(entryPoint.methodName(), entryPoint.descriptor());
    }

    private static void enqueueApplicationCall(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<EntryPoint> work,
        final List<Diagnostic> diagnostics,
        final EntryPoint current,
        final List<CallEdge> callEdges
    ) {
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return;
        }
        final MethodRef target = methodRef.orElseThrow();
        if (isEnumIntrinsic(classes, target) || isSupportedEnumSynthetic(classes, target) || isSupportedArrayClone(target)) {
            return;
        }
        if (isVirtualThreadStart(target) || isVirtualThreadBuilderStart(target) || isExecutorExecute(target)) {
            final List<EntryPoint> targets = virtualThreadTargets(classes, current);
            if (!targets.isEmpty()) {
                work.addAll(targets);
                addEdges(callEdges, current, targets, CallEdge.Kind.THREAD_START_TASK);
            }
            return;
        }
        if (isUnsupportedEnumSynthetic(classes, target)) {
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
            final List<EntryPoint> targetMethods = interfaceTargets(classes, target);
            if (!targetMethods.isEmpty()) {
                work.addAll(targetMethods);
                addEdges(callEdges, current, targetMethods, CallEdge.Kind.CALL);
                return;
            }
            diagnostics.add(Diagnostic.error(
                "JAVAN012",
                "unsupported reachable application method call",
                current.className(),
                current.methodName() + current.descriptor(),
                target.display(),
                "Interface dispatch requires at least one concrete implementation in the closed world.",
                "Add an implementation class or remove the unreachable interface call."
            ));
            return;
        }
        if (!classes.containsKey(target.owner())) {
            if (!target.owner().startsWith("java/")
                && !target.owner().startsWith("jdk/")
                && !target.owner().startsWith("sun/")
                && !NetworkApiSupport.isNetworkCall(target)) {
                diagnostics.add(Diagnostic.error(
                    "JAVAN011",
                    "reachable call target cannot be resolved",
                    current.className(),
                    current.methodName() + current.descriptor(),
                    target.display(),
                    "Closed-world analysis requires every reachable non-JDK call target to be known.",
                    "Add the class to the project classes or dependency classpath."
                ));
            }
            return;
        }
        if (instruction.opcode() == 184) {
            final EntryPoint callee = new EntryPoint(target.owner(), target.name(), target.descriptor());
            work.add(callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 183 && "<init>".equals(target.name())) {
            final EntryPoint callee = new EntryPoint(target.owner(), target.name(), target.descriptor());
            work.add(callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 183) {
            final EntryPoint callee = new EntryPoint(target.owner(), target.name(), target.descriptor());
            work.add(callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 182 && isConcreteExactCallTarget(classes, target.owner())) {
            final EntryPoint callee = new EntryPoint(target.owner(), target.name(), target.descriptor());
            work.add(callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CALL);
            return;
        }
        if (instruction.opcode() == 182) {
            final List<EntryPoint> targets = virtualTargets(classes, target);
            if (!targets.isEmpty()) {
                work.addAll(targets);
                addEdges(callEdges, current, targets, CallEdge.Kind.CALL);
                return;
            }
        }
        diagnostics.add(Diagnostic.error(
            "JAVAN012",
            "unsupported reachable application method call",
            current.className(),
            current.methodName() + current.descriptor(),
            target.display(),
            "The current native profile could not resolve a closed-world dispatch target.",
            "Make sure at least one concrete application class implements the invoked method."
        ));
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final List<EntryPoint> work,
        final EntryPoint current,
        final List<CallEdge> callEdges
    ) {
        if (instruction.opcode() == 178 || instruction.opcode() == 179) {
            final Optional<FieldRef> fieldRef = instruction.fieldRef();
            if (fieldRef.isPresent()) {
                enqueueClassInitializer(classes, fieldRef.orElseThrow().owner(), work, current, callEdges);
            }
            return;
        }
        if (instruction.opcode() == 184) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isPresent()) {
                enqueueClassInitializer(classes, methodRef.orElseThrow().owner(), work, current, callEdges);
            }
            return;
        }
        if (instruction.opcode() == 187) {
            final Optional<String> className = instruction.className();
            if (className.isPresent()) {
                enqueueClassInitializer(classes, className.orElseThrow(), work, current, callEdges);
            }
        }
    }

    private static void enqueueClassInitializer(
        final Map<String, ClassFile> classes,
        final String owner,
        final List<EntryPoint> work,
        final EntryPoint current,
        final List<CallEdge> callEdges
    ) {
        final ClassFile classFile = classes.get(owner);
        if (classFile == null || classFile.isEnum()) {
            return;
        }
        final Optional<MethodInfo> method = classFile.method("<clinit>", "()V");
        if (method.isPresent()) {
            final MethodInfo classInitializer = method.orElseThrow();
            final EntryPoint callee = new EntryPoint(owner, classInitializer.name(), classInitializer.descriptor());
            work.add(callee);
            addEdge(callEdges, current, callee, CallEdge.Kind.CLASS_INITIALIZER);
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

    private static boolean isEnumIntrinsic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"()Ljava/lang/String;".equals(target.descriptor())) {
            return false;
        }
        if ("name".equals(target.name())) {
            return true;
        }
        return "toString".equals(target.name());
    }

    private static boolean isUnsupportedEnumSynthetic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"valueOf".equals(target.name())) {
            return false;
        }
        return ("(Ljava/lang/String;)L" + target.owner() + ";").equals(target.descriptor());
    }

    private static boolean isUnsupportedEnumSyntheticEntry(final Map<String, ClassFile> classes, final EntryPoint entry) {
        final ClassFile owner = classes.get(entry.className());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if (!"valueOf".equals(entry.methodName())) {
            return false;
        }
        return ("(Ljava/lang/String;)L" + entry.className() + ";").equals(entry.descriptor());
    }

    private static Diagnostic unsupportedEnumValueOfDiagnostic(final EntryPoint current, final String subject) {
        return Diagnostic.error(
            "JAVAN015",
            "unsupported reachable enum synthetic method",
            current.className(),
            current.methodName() + current.descriptor(),
            subject,
            "Enum.valueOf(String) requires deterministic enum lookup lowering, which is not implemented yet.",
            "Use direct enum constants, values(), ordinal(), name(), toString(), or enum switch until valueOf lowering is implemented."
        );
    }

    private static boolean isSupportedEnumSynthetic(final Map<String, ClassFile> classes, final MethodRef target) {
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.isEnum()) {
            return false;
        }
        if ("ordinal".equals(target.name()) && "()I".equals(target.descriptor())) {
            return true;
        }
        if (!"values".equals(target.name())) {
            return false;
        }
        if (!target.descriptor().equals("()[L" + target.owner() + ";")) {
            return false;
        }
        return true;
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
        if ("[Z".equals(target.owner())) {
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

    private static boolean isExecutorServiceShutdown(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorServiceShutdown(target);
    }

    private static boolean isExecutorServiceClose(final MethodRef target) {
        return VirtualThreadInvokePatterns.isExecutorServiceClose(target);
    }

    private static boolean isRunnableThreadConstructor(final MethodRef target) {
        return "java/lang/Thread".equals(target.owner())
            && "<init>".equals(target.name())
            && "(Ljava/lang/Runnable;)V".equals(target.descriptor());
    }

    private static List<EntryPoint> interfaceTargets(final Map<String, ClassFile> classes, final MethodRef target) {
        final List<EntryPoint> targets = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()) {
                continue;
            }
            if (!candidate.interfaces().contains(target.owner())) {
                continue;
            }
            if (candidate.method(target.name(), target.descriptor()).isPresent()) {
                targets.add(new EntryPoint(candidate.name(), target.name(), target.descriptor()));
            }
        }
        return List.copyOf(targets);
    }

    private static List<EntryPoint> virtualTargets(final Map<String, ClassFile> classes, final MethodRef target) {
        final List<EntryPoint> targets = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()) {
                continue;
            }
            if (!isSubtypeOf(classes, candidate.name(), target.owner())) {
                continue;
            }
            final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, candidate.name(), target);
            if (resolved.isPresent()) {
                final EntryPoint entryPoint = resolved.orElseThrow();
                if (!containsEntry(targets, entryPoint)) {
                    targets.add(entryPoint);
                }
            }
        }
        return List.copyOf(targets);
    }

    private static boolean enqueueRunnableThreadTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final List<EntryPoint> work,
        final List<CallEdge> callEdges
    ) {
        final List<EntryPoint> targets = runnableThreadTargets(classes, reachable);
        final List<EntryPoint> starters = threadStartMethods(classes, reachable);
        boolean added = false;
        for (final EntryPoint target : targets) {
            if (!containsEntry(reachable, target) && !containsEntry(work, target)) {
                work.add(target);
                added = true;
            }
        }
        for (final EntryPoint starter : starters) {
            addEdges(callEdges, starter, targets, CallEdge.Kind.THREAD_START_TASK);
        }
        return added;
    }

    private static List<EntryPoint> virtualThreadTargets(final Map<String, ClassFile> classes, final EntryPoint current) {
        final Optional<MethodInfo> method = method(classes, current);
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return List.of();
        }
        final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
        final List<EntryPoint> result = new ArrayList<>();
        boolean unknownRunnableTarget = false;
        for (int index = 0; index < instructions.size(); index++) {
            final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
            if (methodRef.isEmpty()
                || (!isVirtualThreadStart(methodRef.orElseThrow())
                && !isVirtualThreadBuilderStart(methodRef.orElseThrow())
                && !isThreadFactoryNewThread(methodRef.orElseThrow())
                && !isExecutorExecute(methodRef.orElseThrow()))) {
                continue;
            }
            final Optional<EntryPoint> inferredTarget = inferVirtualThreadTarget(classes, instructions, index);
            if (inferredTarget.isPresent()) {
                final EntryPoint entryPoint = inferredTarget.orElseThrow();
                if (!containsEntry(result, entryPoint)) {
                    result.add(entryPoint);
                }
            } else {
                unknownRunnableTarget = true;
            }
        }
        if (unknownRunnableTarget) {
            for (final EntryPoint target : allRunnableThreadTargets(classes)) {
                if (!containsEntry(result, target)) {
                    result.add(target);
                }
            }
        }
        return List.copyOf(result);
    }

    private static Optional<EntryPoint> inferVirtualThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
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
            if (isExecutorExecute(startRef.orElseThrow())
                && !supportedVirtualThreadExecutorReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
        }
        return lowerableResolvedVirtualTarget(classes, runnableOwner.orElseThrow(), RUNNABLE_RUN);
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
                    if (!containsEntry(result, reachableMethod)) {
                        result.add(reachableMethod);
                    }
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addEdges(
        final List<CallEdge> callEdges,
        final EntryPoint caller,
        final List<EntryPoint> callees,
        final CallEdge.Kind kind
    ) {
        for (final EntryPoint callee : callees) {
            addEdge(callEdges, caller, callee, kind);
        }
    }

    private static void addEdge(
        final List<CallEdge> callEdges,
        final EntryPoint caller,
        final EntryPoint callee,
        final CallEdge.Kind kind
    ) {
        final CallEdge edge = new CallEdge(caller, callee, kind);
        if (!callEdges.contains(edge)) {
            callEdges.add(edge);
        }
    }

    private static List<EntryPoint> runnableThreadTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable
    ) {
        if (!containsReachableThreadStart(classes, reachable)) {
            return List.of();
        }
        boolean sawRunnableThreadConstruction = false;
        boolean unknownRunnableTarget = false;
        final List<EntryPoint> exactTargets = new ArrayList<>();
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
                final Optional<EntryPoint> inferredTarget = inferRunnableThreadTarget(classes, instructions, index);
                if (inferredTarget.isPresent()) {
                    final EntryPoint entryPoint = inferredTarget.orElseThrow();
                    if (!containsEntry(exactTargets, entryPoint)) {
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
        return allRunnableThreadTargets(classes);
    }

    private static boolean containsReachableThreadStart(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
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
                    return true;
                }
            }
        }
        return false;
    }

    private static Optional<EntryPoint> inferRunnableThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int threadConstructorIndex
    ) {
        final Optional<MethodRef> targetRef = instructions.get(threadConstructorIndex).methodRef();
        if (targetRef.isPresent() && (isThreadFactoryNewThread(targetRef.orElseThrow())
            || isVirtualThreadBuilderUnstarted(targetRef.orElseThrow()))) {
            return inferVirtualThreadTarget(classes, instructions, threadConstructorIndex);
        }
        if (threadConstructorIndex < 3) {
            return Optional.empty();
        }
        final Instruction runnableConstructor = instructions.get(threadConstructorIndex - 1);
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
        if (instructions.get(threadConstructorIndex - 2).opcode() != 89) {
            return Optional.empty();
        }
        final Instruction allocation = instructions.get(threadConstructorIndex - 3);
        final Optional<String> className = allocation.className();
        if (allocation.opcode() != 187
            || className.isEmpty()
            || !className.orElseThrow().equals(target.owner())) {
            return Optional.empty();
        }
        return lowerableResolvedVirtualTarget(classes, target.owner(), RUNNABLE_RUN);
    }

    private static List<EntryPoint> allRunnableThreadTargets(final Map<String, ClassFile> classes) {
        final List<EntryPoint> targets = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()
                || !isAssignableTo(classes, candidate.name(), RUNNABLE_RUN.owner())
                || isAssignableTo(classes, candidate.name(), "java/lang/Thread")) {
                continue;
            }
            final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(classes, candidate.name(), RUNNABLE_RUN);
            if (resolved.isPresent()) {
                final EntryPoint entryPoint = resolved.orElseThrow();
                if (!containsEntry(targets, entryPoint)) {
                    targets.add(entryPoint);
                }
            }
        }
        return List.copyOf(targets);
    }

    private static Optional<EntryPoint> resolvedVirtualTarget(final Map<String, ClassFile> classes, final String receiver, final MethodRef target) {
        String current = receiver;
        while (classes.containsKey(current)) {
            final ClassFile classFile = classes.get(current);
            if (classFile.method(target.name(), target.descriptor()).isPresent()) {
                return Optional.of(new EntryPoint(current, target.name(), target.descriptor()));
            }
            current = classFile.superName();
        }
        return Optional.empty();
    }

    private static Optional<EntryPoint> lowerableResolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, target);
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

}
