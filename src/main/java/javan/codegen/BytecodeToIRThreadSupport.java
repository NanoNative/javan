package javan.codegen;

import javan.analysis.EntryPoint;
import javan.analysis.FunctionValueFlow;
import javan.analysis.VirtualThreadInvokePatterns;
import javan.classfile.ClassFile;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;
import javan.ir.IrDispatch;
import javan.ir.IrExpression;
import javan.ir.IrInstruction;
import javan.ir.IrLocal;
import javan.ir.IrType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static javan.codegen.BytecodeToIR.*;
import static javan.codegen.BytecodeToIRCollectionSupport.*;
import static javan.codegen.BytecodeToIRDynamicSupport.*;
import static javan.codegen.BytecodeToIRInvokeSupport.*;
import static javan.codegen.BytecodeToIRMetadataSupport.*;

final class BytecodeToIRThreadSupport {
    private static final MethodRef RUNNABLE_RUN = new MethodRef("java/lang/Runnable", "run", "()V");

    private BytecodeToIRThreadSupport() {
    }

    static boolean lowerJdkThreadInstanceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final Map<String, IrDispatch> dispatches,
        final SourceLineIndex sourceLines
    ) {
        if (!"java/lang/Thread".equals(methodRef.owner())) {
            return false;
        }
        if ("setDaemon".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression daemon = popInt(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_thread_set_daemon",
                List.of(receiver, daemon)
            ));
            return true;
        }
        if ("setPriority".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression priority = popInt(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_priority", List.of(receiver, priority)));
            return true;
        }
        if ("setName".equals(methodRef.name()) && "(Ljava/lang/String;)V".equals(methodRef.descriptor())) {
            final IrExpression name = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_thread_set_name", List.of(receiver, name)));
            return true;
        }
        if ("join".equals(methodRef.name()) && "(J)V".equals(methodRef.descriptor())) {
            final IrExpression millis = popLong(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                false,
                IrExpression.objectNull(),
                "javan_thread_join_millis_interruptible",
                List.of(receiver, millis)
            );
            return true;
        }
        if ("join".equals(methodRef.name()) && "(JI)V".equals(methodRef.descriptor())) {
            final IrExpression nanos = popInt(classFile, method, stack);
            final IrExpression millis = popLong(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                false,
                IrExpression.objectNull(),
                "javan_thread_join_millis_nanos_interruptible",
                List.of(receiver, millis, nanos)
            );
            return true;
        }
        if ("join".equals(methodRef.name()) && "(Ljava/time/Duration;)Z".equals(methodRef.descriptor())) {
            final List<StackValue> preservedPrefix = new ArrayList<>();
            final int preservedPrefixSize = Math.max(0, stack.size() - 2);
            for (int index = 0; index < preservedPrefixSize; index++) {
                preservedPrefix.add(stack.get(index));
            }
            final IrExpression duration = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                false,
                IrExpression.objectNull(),
                "javan_thread_join_millis_interruptible",
                List.of(receiver, IrExpression.longCall("javan_duration_to_millis", List.of(duration)))
            );
            stack.addAll(preservedPrefix);
            stack.add(StackValue.intExpression(IrExpression.intLiteral(1)));
            return true;
        }
        final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
        if ("interrupt".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_interrupt", List.of(receiver)));
            return true;
        }
        if ("isDaemon".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_daemon", List.of(receiver));
            return true;
        }
        if ("getPriority".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_get_priority", List.of(receiver));
            return true;
        }
        if ("isInterrupted".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_interrupted", List.of(receiver));
            return true;
        }
        if ("isAlive".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_alive", List.of(receiver));
            return true;
        }
        if ("isVirtual".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_thread_is_virtual", List.of(receiver));
            return true;
        }
        if ("getName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_thread_get_name", List.of(receiver));
            return true;
        }
        if ("getId".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_thread_get_id", List.of(receiver))));
            return true;
        }
        if ("threadId".equals(methodRef.name()) && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall("javan_thread_get_id", List.of(receiver))));
            return true;
        }
        if ("start".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_start", List.of(receiver)));
            return true;
        }
        if ("join".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            lowerInterruptAwareThreadWait(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                pendingExceptionHandlerStacks,
                sourceLines,
                false,
                IrExpression.objectNull(),
                "javan_thread_join_interruptible",
                List.of(receiver)
            );
            return true;
        }
        return false;
    }

    static MethodRef runnableRunMethodRef() {
        return RUNNABLE_RUN;
    }

    static List<EntryPoint> runnableThreadTargets(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachableMethods
    ) {
        if (!containsReachableThreadStart(classes, reachableMethods)) {
            return List.of();
        }
        boolean sawRunnableThreadConstruction = false;
        boolean unknownRunnableTarget = false;
        final List<EntryPoint> result = new ArrayList<>();
        for (final EntryPoint reachable : reachableMethods) {
            final ClassFile owner = classes.get(reachable.className());
            if (owner == null) {
                continue;
            }
            final Optional<MethodInfo> method = owner.method(reachable.methodName(), reachable.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            final List<Instruction> instructions = method.orElseThrow().code().orElseThrow().instructions();
            for (int index = 0; index < instructions.size(); index++) {
                final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
                if (methodRef.isPresent()) {
                    final MethodRef target = JdkCallSupport.normalizeInheritedSupportedJdkCall(classes, methodRef.orElseThrow())
                        .orElse(methodRef.orElseThrow());
                    if (isVirtualThreadStart(target)
                        || isVirtualThreadBuilderStart(target)
                        || isVirtualThreadBuilderUnstarted(target)
                        || isVirtualThreadFactoryNewThread(target)
                        || VirtualThreadInvokePatterns.isExecutorExecute(target)
                        || VirtualThreadInvokePatterns.isExecutorServiceSubmit(target)
                        || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorSchedule(target)
                        || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleAtFixedRate(target)
                        || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleWithFixedDelay(target)
                        || VirtualThreadInvokePatterns.isScheduledExecutorServiceSchedule(target)
                        || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleAtFixedRate(target)
                        || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleWithFixedDelay(target)) {
                        sawRunnableThreadConstruction = true;
                        final Optional<EntryPoint> resolved = inferVirtualThreadTarget(classes, instructions, index);
                        if (resolved.isPresent()) {
                            final EntryPoint entryPoint = resolved.orElseThrow();
                            if (!result.contains(entryPoint)) {
                                result.add(entryPoint);
                            }
                        } else {
                            unknownRunnableTarget = true;
                        }
                        continue;
                    }
                }
                if (methodRef.isEmpty() || (!isRunnableThreadConstructor(methodRef.orElseThrow())
                    && !isVirtualThreadBuilderUnstarted(methodRef.orElseThrow()))) {
                    continue;
                }
                sawRunnableThreadConstruction = true;
                final Optional<EntryPoint> resolved = inferRunnableThreadTarget(classes, instructions, index);
                if (resolved.isPresent()) {
                    final EntryPoint entryPoint = resolved.orElseThrow();
                    if (!result.contains(entryPoint)) {
                        result.add(entryPoint);
                    }
                } else {
                    unknownRunnableTarget = true;
                }
            }
        }
        if (!sawRunnableThreadConstruction) {
            return List.of();
        }
        if (!unknownRunnableTarget && !result.isEmpty()) {
            return List.copyOf(result);
        }
        return allRunnableThreadTargets(classes);
    }

    static boolean containsReachableThreadStart(final Map<String, ClassFile> classes, final List<EntryPoint> reachableMethods) {
        for (final EntryPoint reachable : reachableMethods) {
            final ClassFile owner = classes.get(reachable.className());
            if (owner == null) {
                continue;
            }
            final Optional<MethodInfo> method = owner.method(reachable.methodName(), reachable.descriptor());
            if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
                continue;
            }
            for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
                final Optional<MethodRef> methodRef = instruction.methodRef();
                if (methodRef.isPresent()) {
                    final MethodRef target = JdkCallSupport.normalizeInheritedSupportedJdkCall(classes, methodRef.orElseThrow())
                        .orElse(methodRef.orElseThrow());
                    if (isThreadStart(target)
                        || isVirtualThreadStart(target)
                        || isVirtualThreadBuilderStart(target)
                        || VirtualThreadInvokePatterns.isExecutorExecute(target)
                        || VirtualThreadInvokePatterns.isExecutorServiceSubmit(target)
                        || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorSchedule(target)
                        || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleAtFixedRate(target)
                        || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleWithFixedDelay(target)
                        || VirtualThreadInvokePatterns.isScheduledExecutorServiceSchedule(target)
                        || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleAtFixedRate(target)
                        || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleWithFixedDelay(target)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isThreadStart(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "start".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }

    static boolean isVirtualThreadStart(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "startVirtualThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    static boolean isVirtualThreadBuilderStart(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "start".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    static boolean isVirtualThreadBuilderUnstarted(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "unstarted".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    static boolean isVirtualThreadBuilderOwner(final String owner) {
        return "java/lang/Thread$Builder".equals(owner)
            || "java/lang/Thread$Builder$OfVirtual".equals(owner);
    }

    static boolean isRunnableThreadConstructor(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "<init>".equals(methodRef.name())
            && ("(Ljava/lang/Runnable;)V".equals(methodRef.descriptor())
            || "(Ljava/lang/Runnable;Ljava/lang/String;)V".equals(methodRef.descriptor()));
    }

    static Optional<EntryPoint> inferVirtualThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        if (startIndex < 3) {
            return Optional.empty();
        }
        final Instruction runnableConstructor = instructions.get(startIndex - 1);
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
        if (instructions.get(startIndex - 2).opcode() != 89) {
            return Optional.empty();
        }
        final Instruction allocation = instructions.get(startIndex - 3);
        final Optional<String> className = allocation.className();
        if (allocation.opcode() != 187
            || className.isEmpty()
            || !className.orElseThrow().equals(target.owner())) {
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
            if (isVirtualThreadFactoryNewThread(startRef.orElseThrow())
                && !supportedVirtualThreadFactoryReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
            if ((VirtualThreadInvokePatterns.isExecutorExecute(startRef.orElseThrow())
                || VirtualThreadInvokePatterns.isExecutorServiceSubmit(startRef.orElseThrow()))
                && !supportedVirtualThreadExecutorReceiver(classes, instructions, startIndex)) {
                return Optional.empty();
            }
        }
        return lowerableResolvedVirtualTarget(classes, target.owner(), RUNNABLE_RUN);
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
                    transparentProducerIndex - VirtualThreadInvokePatterns.virtualThreadBuilderNameProducerOffset(methodRef.orElseThrow())
                );
            }
            if (isVirtualThreadBuilderInheritInheritableThreadLocals(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(classes, instructions, transparentProducerIndex - 2);
            }
        }
        if (transparentProducerIndex < 1) {
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
        if (methodRef.isPresent() && isVirtualThreadBuilderFactory(methodRef.orElseThrow())) {
            return supportedVirtualThreadBuilderProducer(classes, instructions, transparentProducerIndex - 1);
        }
        if (methodRef.isPresent()
            && producer.opcode() == 184
            && VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(classes, methodRef.orElseThrow())) {
            return true;
        }
        if (transparentProducerIndex < 1) {
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
            if (VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(methodRef.orElseThrow())) {
                return true;
            }
            if (VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(methodRef.orElseThrow())) {
                return supportedVirtualThreadFactoryProducer(classes, instructions, transparentProducerIndex - 1);
            }
        }
        if (transparentProducerIndex < 1) {
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

    static boolean supportedVirtualThreadFactoryStaticField(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final FieldRef fieldRef
    ) {
        if (!"Ljava/util/concurrent/ThreadFactory;".equals(fieldRef.descriptor()) || method.code().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        final int loadIndex = currentInstructionIndex(instructions, instruction.offset());
        if (loadIndex < 0) {
            return false;
        }
        for (int index = loadIndex - 1; index >= 0; index--) {
            final Instruction candidate = instructions.get(index);
            if (candidate.opcode() != 179 || candidate.fieldRef().isEmpty() || !fieldRef.equals(candidate.fieldRef().orElseThrow())) {
                continue;
            }
            return supportedVirtualThreadFactoryProducer(classes, instructions, index - 1);
        }
        return false;
    }

    static boolean supportedVirtualThreadExecutorStaticField(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final FieldRef fieldRef
    ) {
        if (!"Ljava/util/concurrent/ExecutorService;".equals(fieldRef.descriptor()) || method.code().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        final int loadIndex = currentInstructionIndex(instructions, instruction.offset());
        if (loadIndex < 0) {
            return false;
        }
        for (int index = loadIndex - 1; index >= 0; index--) {
            final Instruction candidate = instructions.get(index);
            if (candidate.opcode() != 179 || candidate.fieldRef().isEmpty() || !fieldRef.equals(candidate.fieldRef().orElseThrow())) {
                continue;
            }
            return supportedVirtualThreadExecutorProducer(classes, instructions, index - 1);
        }
        return false;
    }

    private static int currentInstructionIndex(final List<Instruction> instructions, final int offset) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() == offset) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isThreadOfVirtual(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "ofVirtual".equals(methodRef.name())
            && "()Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    private static boolean isThreadBuilderOfVirtualName(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(methodRef);
    }

    private static boolean isVirtualThreadBuilderInheritInheritableThreadLocals(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualInheritInheritableThreadLocals(methodRef);
    }

    private static int localLoadSlot(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 25 -> instruction.operands()[0] & 0xFF;
            case 42 -> 0;
            case 43 -> 1;
            case 44 -> 2;
            case 45 -> 3;
            default -> -1;
        };
    }

    static Optional<EntryPoint> inferRunnableThreadTarget(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int threadConstructorIndex
    ) {
        final Optional<MethodRef> targetRef = instructions.get(threadConstructorIndex).methodRef();
        if (targetRef.isPresent() && isVirtualThreadBuilderUnstarted(targetRef.orElseThrow())) {
            return inferVirtualThreadTarget(classes, instructions, threadConstructorIndex);
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
        return lowerableResolvedVirtualTarget(classes, target.owner(), RUNNABLE_RUN);
    }

    static Optional<EntryPoint> defaultInterfaceTarget(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef
    ) {
        final ClassFile owner = classes.get(methodRef.owner());
        if (owner == null || !owner.isInterface()) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(methodRef.name(), methodRef.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new EntryPoint(methodRef.owner(), methodRef.name(), methodRef.descriptor()));
    }

    static List<EntryPoint> allRunnableThreadTargets(final Map<String, ClassFile> classes) {
        final List<EntryPoint> result = new ArrayList<>();
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()
                || !isAssignableTo(classes, candidate.name(), RUNNABLE_RUN.owner())
                || isAssignableTo(classes, candidate.name(), "java/lang/Thread")) {
                continue;
            }
            final Optional<EntryPoint> resolved = lowerableResolvedVirtualTarget(classes, candidate.name(), RUNNABLE_RUN);
            if (resolved.isPresent() && !result.contains(resolved.orElseThrow())) {
                result.add(resolved.orElseThrow());
            }
        }
        return List.copyOf(result);
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
        final EntryPoint entryPoint = resolved.orElseThrow();
        final ClassFile owner = classes.get(entryPoint.className());
        if (owner == null) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(entryPoint.methodName(), entryPoint.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return resolved;
    }

    static boolean lowerJdkNetworkInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/net/InetAddress".equals(methodRef.owner())) {
            if (!"getHostAddress".equals(methodRef.name())
                && !"getHostName".equals(methodRef.name())
                && !"getCanonicalHostName".equals(methodRef.name())
                && !"getAddress".equals(methodRef.name())) {
                return false;
            }
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            if ("getAddress".equals(methodRef.name()) && "()[B".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_address", List.of(receiver));
                return true;
            }
            if ("getHostAddress".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_host_address", List.of(receiver));
                return true;
            }
            if ("getHostName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_host_name", List.of(receiver));
                return true;
            }
            if ("getCanonicalHostName".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_inet_address_get_canonical_host_name", List.of(receiver));
                return true;
            }
            return false;
        }
        if (!"java/net/InetSocketAddress".equals(methodRef.owner())) {
            return lowerJdkTcpSocketInstanceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations);
        }
        if (!"getPort".equals(methodRef.name())
            && !"getHostString".equals(methodRef.name())
            && !"getAddress".equals(methodRef.name())
            && !"toString".equals(methodRef.name())) {
            return false;
        }
        final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
        if ("getPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_inet_socket_address_get_port", List.of(receiver));
            return true;
        }
        if ("getHostString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_inet_socket_address_get_host_string", List.of(receiver));
            return true;
        }
        if ("getAddress".equals(methodRef.name()) && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_inet_socket_address_get_address", List.of(receiver));
            return true;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_inet_socket_address_to_string", List.of(receiver));
            return true;
        }
        return false;
    }

    static boolean lowerJdkTcpSocketInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if ("java/net/Socket".equals(methodRef.owner())) {
            if (!"isConnected".equals(methodRef.name())
                && !"isClosed".equals(methodRef.name())
                && !"isBound".equals(methodRef.name())
                && !"isInputShutdown".equals(methodRef.name())
                && !"isOutputShutdown".equals(methodRef.name())
                && !"connect".equals(methodRef.name())
                && !"getPort".equals(methodRef.name())
                && !"getLocalPort".equals(methodRef.name())
                && !"getSoTimeout".equals(methodRef.name())
                && !"setSoTimeout".equals(methodRef.name())
                && !"getSoLinger".equals(methodRef.name())
                && !"setSoLinger".equals(methodRef.name())
                && !"getOOBInline".equals(methodRef.name())
                && !"setOOBInline".equals(methodRef.name())
                && !"getTrafficClass".equals(methodRef.name())
                && !"setTrafficClass".equals(methodRef.name())
                && !"getTcpNoDelay".equals(methodRef.name())
                && !"setTcpNoDelay".equals(methodRef.name())
                && !"getKeepAlive".equals(methodRef.name())
                && !"setKeepAlive".equals(methodRef.name())
                && !"getReuseAddress".equals(methodRef.name())
                && !"setReuseAddress".equals(methodRef.name())
                && !"getReceiveBufferSize".equals(methodRef.name())
                && !"setReceiveBufferSize".equals(methodRef.name())
                && !"getSendBufferSize".equals(methodRef.name())
                && !"setSendBufferSize".equals(methodRef.name())
                && !"getLocalAddress".equals(methodRef.name())
                && !"getInetAddress".equals(methodRef.name())
                && !"getLocalSocketAddress".equals(methodRef.name())
                && !"getRemoteSocketAddress".equals(methodRef.name())
                && !"getChannel".equals(methodRef.name())
                && !"getInputStream".equals(methodRef.name())
                && !"getOutputStream".equals(methodRef.name())
                && !"shutdownInput".equals(methodRef.name())
                && !"shutdownOutput".equals(methodRef.name())
                && !"close".equals(methodRef.name())) {
                return false;
            }
            if ("setTcpNoDelay".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
                final IrExpression enabled = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_tcp_no_delay", List.of(receiver, enabled)));
                return true;
            }
            if ("setKeepAlive".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
                final IrExpression enabled = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_keep_alive", List.of(receiver, enabled)));
                return true;
            }
            if ("setReuseAddress".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
                final IrExpression enabled = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_reuse_address", List.of(receiver, enabled)));
                return true;
            }
            if ("setReceiveBufferSize".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
                final IrExpression size = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_receive_buffer_size", List.of(receiver, size)));
                return true;
            }
            if ("setSendBufferSize".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
                final IrExpression size = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_send_buffer_size", List.of(receiver, size)));
                return true;
            }
            if ("setSoTimeout".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
                final IrExpression timeout = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_so_timeout", List.of(receiver, timeout)));
                return true;
            }
            if ("setSoLinger".equals(methodRef.name()) && "(ZI)V".equals(methodRef.descriptor())) {
                final IrExpression lingerSeconds = popInt(classFile, method, stack);
                final IrExpression enabled = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_so_linger", List.of(receiver, enabled, lingerSeconds)));
                return true;
            }
            if ("setOOBInline".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
                final IrExpression enabled = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_oob_inline", List.of(receiver, enabled)));
                return true;
            }
            if ("setTrafficClass".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
                final IrExpression trafficClass = popInt(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_set_traffic_class", List.of(receiver, trafficClass)));
                return true;
            }
            if ("connect".equals(methodRef.name()) && "(Ljava/net/SocketAddress;)V".equals(methodRef.descriptor())) {
                final IrExpression address = popObject(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_connect_socket_address", List.of(receiver, address)));
                return true;
            }
            if ("connect".equals(methodRef.name()) && "(Ljava/net/SocketAddress;I)V".equals(methodRef.descriptor())) {
                final IrExpression timeout = popInt(classFile, method, stack);
                final IrExpression address = popObject(classFile, method, stack);
                final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
                instructions.add(IrInstruction.callStaticVoid("javan_socket_connect_socket_address_timeout", List.of(receiver, address, timeout)));
                return true;
            }
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            if ("isConnected".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_connected", List.of(receiver));
                return true;
            }
            if ("isClosed".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_closed", List.of(receiver));
                return true;
            }
            if ("isBound".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_bound", List.of(receiver));
                return true;
            }
            if ("isInputShutdown".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_input_shutdown", List.of(receiver));
                return true;
            }
            if ("isOutputShutdown".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_is_output_shutdown", List.of(receiver));
                return true;
            }
            if ("getPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_port", List.of(receiver));
                return true;
            }
            if ("getLocalPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_local_port", List.of(receiver));
                return true;
            }
            if ("getSoTimeout".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_so_timeout", List.of(receiver));
                return true;
            }
            if ("getSoLinger".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_so_linger", List.of(receiver));
                return true;
            }
            if ("getOOBInline".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_oob_inline", List.of(receiver));
                return true;
            }
            if ("getTrafficClass".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_traffic_class", List.of(receiver));
                return true;
            }
            if ("getTcpNoDelay".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_tcp_no_delay", List.of(receiver));
                return true;
            }
            if ("getKeepAlive".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_keep_alive", List.of(receiver));
                return true;
            }
            if ("getReuseAddress".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_reuse_address", List.of(receiver));
                return true;
            }
            if ("getReceiveBufferSize".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_receive_buffer_size", List.of(receiver));
                return true;
            }
            if ("getSendBufferSize".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
                pushIntCall(instructions, stack, localDeclarations, "javan_socket_get_send_buffer_size", List.of(receiver));
                return true;
            }
            if ("getLocalAddress".equals(methodRef.name()) && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_socket_get_local_address", List.of(receiver));
                return true;
            }
            if ("getInetAddress".equals(methodRef.name()) && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_socket_get_inet_address", List.of(receiver));
                return true;
            }
            if ("getLocalSocketAddress".equals(methodRef.name()) && "()Ljava/net/SocketAddress;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_socket_get_local_socket_address", List.of(receiver));
                return true;
            }
            if ("getRemoteSocketAddress".equals(methodRef.name()) && "()Ljava/net/SocketAddress;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_socket_get_remote_socket_address", List.of(receiver));
                return true;
            }
            if ("getChannel".equals(methodRef.name()) && "()Ljava/nio/channels/SocketChannel;".equals(methodRef.descriptor())) {
                pushObjectCall(instructions, stack, localDeclarations, "javan_socket_get_channel", List.of(receiver));
                return true;
            }
            if ("getInputStream".equals(methodRef.name()) && "()Ljava/io/InputStream;".equals(methodRef.descriptor())) {
                final String localName = "object" + localDeclarations.size();
                localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
                instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_socket_input_stream", List.of(receiver))));
                stack.add(StackValue.socketInputStream(IrExpression.objectLocal(localName)));
                return true;
            }
            if ("getOutputStream".equals(methodRef.name()) && "()Ljava/io/OutputStream;".equals(methodRef.descriptor())) {
                final String localName = "object" + localDeclarations.size();
                localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
                instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_socket_output_stream", List.of(receiver))));
                stack.add(StackValue.socketOutputStream(IrExpression.objectLocal(localName)));
                return true;
            }
            if ("shutdownInput".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_socket_shutdown_input", List.of(receiver)));
                return true;
            }
            if ("shutdownOutput".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_socket_shutdown_output", List.of(receiver)));
                return true;
            }
            if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
                instructions.add(IrInstruction.callStaticVoid("javan_socket_close", List.of(receiver)));
                return true;
            }
            return false;
        }
        if (!"java/net/ServerSocket".equals(methodRef.owner())) {
            return false;
        }
        if (!"bind".equals(methodRef.name())
            && !"isBound".equals(methodRef.name())
            && !"isClosed".equals(methodRef.name())
            && !"getInetAddress".equals(methodRef.name())
            && !"getLocalPort".equals(methodRef.name())
            && !"getSoTimeout".equals(methodRef.name())
            && !"setSoTimeout".equals(methodRef.name())
            && !"getReuseAddress".equals(methodRef.name())
            && !"setReuseAddress".equals(methodRef.name())
            && !"getReceiveBufferSize".equals(methodRef.name())
            && !"setReceiveBufferSize".equals(methodRef.name())
            && !"getLocalSocketAddress".equals(methodRef.name())
            && !"getChannel".equals(methodRef.name())
            && !"accept".equals(methodRef.name())
            && !"close".equals(methodRef.name())) {
            return false;
        }
        if ("bind".equals(methodRef.name()) && "(Ljava/net/SocketAddress;)V".equals(methodRef.descriptor())) {
            final IrExpression address = popObject(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_bind_socket_address", List.of(receiver, address)));
            return true;
        }
        if ("bind".equals(methodRef.name()) && "(Ljava/net/SocketAddress;I)V".equals(methodRef.descriptor())) {
            final IrExpression backlog = popInt(classFile, method, stack);
            final IrExpression address = popObject(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_bind_socket_address_backlog", List.of(receiver, address, backlog)));
            return true;
        }
        if ("setSoTimeout".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression timeout = popInt(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_set_so_timeout", List.of(receiver, timeout)));
            return true;
        }
        if ("setReuseAddress".equals(methodRef.name()) && "(Z)V".equals(methodRef.descriptor())) {
            final IrExpression enabled = popInt(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_set_reuse_address", List.of(receiver, enabled)));
            return true;
        }
        if ("setReceiveBufferSize".equals(methodRef.name()) && "(I)V".equals(methodRef.descriptor())) {
            final IrExpression size = popInt(classFile, method, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_set_receive_buffer_size", List.of(receiver, size)));
            return true;
        }
        final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
        if ("isBound".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_is_bound", List.of(receiver));
            return true;
        }
        if ("isClosed".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_is_closed", List.of(receiver));
            return true;
        }
        if ("getInetAddress".equals(methodRef.name()) && "()Ljava/net/InetAddress;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_server_socket_get_inet_address", List.of(receiver));
            return true;
        }
        if ("getLocalPort".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_get_local_port", List.of(receiver));
            return true;
        }
        if ("getSoTimeout".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_get_so_timeout", List.of(receiver));
            return true;
        }
        if ("getReuseAddress".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_get_reuse_address", List.of(receiver));
            return true;
        }
        if ("getReceiveBufferSize".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            pushIntCall(instructions, stack, localDeclarations, "javan_server_socket_get_receive_buffer_size", List.of(receiver));
            return true;
        }
        if ("getLocalSocketAddress".equals(methodRef.name()) && "()Ljava/net/SocketAddress;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_server_socket_get_local_socket_address", List.of(receiver));
            return true;
        }
        if ("getChannel".equals(methodRef.name()) && "()Ljava/nio/channels/ServerSocketChannel;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_server_socket_get_channel", List.of(receiver));
            return true;
        }
        if ("accept".equals(methodRef.name()) && "()Ljava/net/Socket;".equals(methodRef.descriptor())) {
            pushObjectCall(instructions, stack, localDeclarations, "javan_server_socket_accept", List.of(receiver));
            return true;
        }
        if ("close".equals(methodRef.name()) && "()V".equals(methodRef.descriptor())) {
            instructions.add(IrInstruction.callStaticVoid("javan_server_socket_close", List.of(receiver)));
            return true;
        }
        return false;
    }

    static boolean lowerJdkFileInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if ("java/nio/file/attribute/FileTime".equals(methodRef.owner())
            && "toMillis".equals(methodRef.name())
            && "()J".equals(methodRef.descriptor())) {
            stack.add(StackValue.longExpression(IrExpression.longCall(
                "javan_file_time_to_millis",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if (isDirectoryStreamClose(methodRef)) {
            popObjectForJdkCall(classFile, method, instruction, stack);
            return true;
        }
        if (isDirectoryStreamIterator(methodRef)) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_list_iterator",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        return false;
    }

    static boolean isDirectoryStreamClose(final MethodRef methodRef) {
        if (!"java/nio/file/DirectoryStream".equals(methodRef.owner())) {
            return false;
        }
        if (!"close".equals(methodRef.name())) {
            return false;
        }
        return "()V".equals(methodRef.descriptor());
    }

    static boolean isDirectoryStreamIterator(final MethodRef methodRef) {
        if (!isIterableOrDirectoryStream(methodRef.owner())) {
            return false;
        }
        if (!"iterator".equals(methodRef.name())) {
            return false;
        }
        return "()Ljava/util/Iterator;".equals(methodRef.descriptor());
    }

    static boolean isIterableOrDirectoryStream(final String owner) {
        if ("java/lang/Iterable".equals(owner)) {
            return true;
        }
        return "java/nio/file/DirectoryStream".equals(owner);
    }

    static boolean lowerJdkPathInstanceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<StackValue> stack
    ) {
        if (!"java/nio/file/Path".equals(methodRef.owner())) {
            return false;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(popObjectForJdkCall(classFile, method, instruction, stack)));
            return true;
        }
        if ("toAbsolutePath".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_to_absolute",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("normalize".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_normalize",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getParent".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_get_parent",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getFileName".equals(methodRef.name()) && "()Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_get_file_name",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("isAbsolute".equals(methodRef.name()) && "()Z".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_path_is_absolute",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getNameCount".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            stack.add(StackValue.intExpression(IrExpression.intCall(
                "javan_path_get_name_count",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack))
            )));
            return true;
        }
        if ("getName".equals(methodRef.name()) && "(I)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final IrExpression index = popInt(classFile, method, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall(
                "javan_path_get_name",
                List.of(popObjectForJdkCall(classFile, method, instruction, stack), index)
            )));
            return true;
        }
        if ("equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_equals", List.of(receiver, other))));
            return true;
        }
        if ("startsWith".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Z".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.intExpression(IrExpression.intCall("javan_path_starts_with", List.of(receiver, other))));
            return true;
        }
        if ("relativize".equals(methodRef.name()) && "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(methodRef.descriptor())) {
            final IrExpression other = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_relativize", List.of(receiver, other))));
            return true;
        }
        if ("resolve".equals(methodRef.name())
            && ("(Ljava/lang/String;)Ljava/nio/file/Path;".equals(methodRef.descriptor())
            || "(Ljava/nio/file/Path;)Ljava/nio/file/Path;".equals(methodRef.descriptor()))) {
            final IrExpression child = popObjectForJdkCall(classFile, method, instruction, stack);
            final IrExpression receiver = popObjectForJdkCall(classFile, method, instruction, stack);
            stack.add(StackValue.objectExpression(IrExpression.objectCall("javan_path_resolve", List.of(receiver, child))));
            return true;
        }
        return false;
    }

    static IrExpression popObjectForJdkCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() != StackKind.OBJECT) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
        }
        return value.expression().orElseThrow();
    }

    static IrExpression popPrintableObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        return printableObjectExpression(classFile, method, instruction, pop(stack));
    }

    private static IrExpression printableObjectExpression(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final StackValue value
    ) {
        if (value.kind() == StackKind.OBJECT) {
            return value.expression().orElseThrow();
        }
        if (value.kind() == StackKind.VIRTUAL_THREAD_BUILDER) {
            return IrExpression.objectCall("javan_virtual_thread_builder_to_string", List.of(value.expression().orElse(IrExpression.objectNull())));
        }
        if (value.kind() == StackKind.VIRTUAL_THREAD_FACTORY) {
            return IrExpression.objectCall("javan_virtual_thread_factory_to_string", List.of(value.expression().orElse(IrExpression.objectNull())));
        }
        if (value.kind() == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            return IrExpression.objectCall("javan_virtual_thread_executor_to_string", List.of(value.expression().orElse(IrExpression.objectNull())));
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
    }

    private static IrExpression popVirtualThreadComparableObject(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "An object value was expected on the bytecode stack.");
        }
        final StackValue value = pop(stack);
        if (value.kind() == StackKind.OBJECT
            || value.kind() == StackKind.VIRTUAL_THREAD_BUILDER
            || value.kind() == StackKind.VIRTUAL_THREAD_FACTORY
            || value.kind() == StackKind.VIRTUAL_THREAD_EXECUTOR) {
            return value.expression().orElseThrow();
        }
        throw invalidStack(classFile, method, instruction, wrongStackTypeReason("object", value.kind()));
    }

    static void lowerInterfaceCall(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final Map<MethodRef, MaterializedLambdaDispatchKind> materializedLambdaMethods,
        final FunctionValueFlow.Result functionValueFlow
    ) {
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (lowerVirtualThreadObservationInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerVirtualThreadBuilderInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerScheduledThreadPoolExecutorCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerVirtualThreadExecutorInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkPathInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkFileInstanceCall(classFile, method, instruction, methodRef, stack)) {
            return;
        }
        if (lowerJdkHttpInterfaceCall(classFile, method, instruction, methodRef, instructions, stack, localDeclarations)) {
            return;
        }
        if (lowerJdkCollectionInstanceCall(
            classes,
            classFile,
            method,
            instruction,
            dispatches,
            materializedLambdaMethods,
            functionValueFlow,
            methodRef,
            instructions,
            stack,
            localDeclarations
        )) {
            return;
        }
        if (isPredicateTest(methodRef) && hasPredicateLambdaReceiverOnStack(stack)) {
            lowerPredicateTestLambdaCall(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations
            );
            return;
        }
        if (isSupplierGet(methodRef) && hasTopStackKind(stack, StackKind.LAMBDA_SUPPLIER)) {
            lowerSupplierGetLambdaCall(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations
            );
            return;
        }
        if (isSupplierGet(methodRef)
            && materializedLambdaMethods.get(methodRef) == MaterializedLambdaDispatchKind.SUPPLIER) {
            final IrExpression supplier = popObject(classFile, method, stack);
            final String resultLocal = newObjectLocal(localDeclarations);
            lowerSupplierGetCall(
                classes,
                classFile,
                method,
                instruction,
                instructions,
                dispatches,
                materializedLambdaMethods,
                supplier,
                resultLocal
            );
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return;
        }
        if (isFunctionApply(methodRef) && hasFunctionLambdaReceiverOnStack(stack)) {
            lowerFunctionApplyLambdaCall(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations
            );
            return;
        }
        final List<EntryPoint> targets = interfaceTargets(classes, methodRef);
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final List<IrExpression> arguments = new ArrayList<>(popArguments(classFile, method, stack, descriptor));
        final IrExpression receiver = popObject(classFile, method, stack);
        final Optional<EntryPoint> defaultTarget = defaultInterfaceTarget(classes, methodRef);
        final boolean materializedFunctionReceiver = isFunctionApply(methodRef)
            && functionValueFlow.isMaterializedFunction(
                classFile.name(),
                method.name(),
                method.descriptor(),
                instruction.offset()
            );
        final MaterializedLambdaDispatchKind dispatchKind = isFunctionApply(methodRef)
            && !materializedFunctionReceiver
            ? null
            : materializedLambdaMethods.get(methodRef);
        if (materializedFunctionReceiver) {
            if (dispatchKind != MaterializedLambdaDispatchKind.OBJECT) {
                throw unsupported(classFile, method, instruction);
            }
            lowerMaterializedInterfaceCall(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                dispatchKind,
                descriptor,
                receiver,
                arguments
            );
            return;
        }
        if (dispatchKind != null
            && dispatchKind != MaterializedLambdaDispatchKind.SUPPLIER
            && (!targets.isEmpty() || defaultTarget.isPresent())) {
            lowerMixedMaterializedInterfaceCall(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                dispatches,
                methodRef,
                targets,
                defaultTarget,
                dispatchKind,
                descriptor,
                receiver,
                arguments
            );
            return;
        }
        if (targets.size() > 1) {
            arguments.addFirst(receiver);
            final String dispatchSymbol = dispatchSymbol(methodRef);
            dispatches.putIfAbsent(dispatchSymbol, dispatch(dispatchSymbol, descriptor, targets));
            appendCallResult(instructions, stack, localDeclarations, descriptor.returnType(), dispatchSymbol, arguments);
            return;
        }
        if (!targets.isEmpty()) {
            final EntryPoint target = targets.getFirst();
            arguments.addFirst(receiver);
            final String symbol = symbol(target);
            appendCallResult(instructions, stack, localDeclarations, descriptor.returnType(), symbol, arguments);
            return;
        }
        if (defaultTarget.isPresent()) {
            arguments.addFirst(receiver);
            appendCallResult(
                instructions,
                stack,
                localDeclarations,
                descriptor.returnType(),
                symbol(defaultTarget.orElseThrow()),
                arguments
            );
            return;
        }
        if (dispatchKind != null) {
            lowerMaterializedInterfaceCall(
                classFile,
                method,
                instruction,
                instructions,
                stack,
                localDeclarations,
                dispatchKind,
                descriptor,
                receiver,
                arguments
            );
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerMaterializedInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final MaterializedLambdaDispatchKind dispatchKind,
        final MethodDescriptor descriptor,
        final IrExpression receiver,
        final List<IrExpression> arguments
    ) {
        final String helper = materializedInterfaceHelper(classFile, method, instruction, dispatchKind, descriptor);
        final List<IrExpression> callArguments = materializedInterfaceArguments(receiver, arguments);
        if (descriptor.returnType() == IrType.OBJECT) {
            final String resultLocal = newObjectLocal(localDeclarations);
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(helper, callArguments)
            ));
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
            return;
        }
        if (descriptor.returnType() == IrType.INT) {
            final String resultLocal = newIntLocal(localDeclarations);
            instructions.add(IrInstruction.assignInt(
                resultLocal,
                IrExpression.intCall(helper, callArguments)
            ));
            stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
            return;
        }
        if (descriptor.returnType() == IrType.VOID) {
            instructions.add(IrInstruction.callStaticVoid(helper, callArguments));
            return;
        }
        throw unsupported(classFile, method, instruction);
    }

    private static void lowerMixedMaterializedInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<String, IrDispatch> dispatches,
        final MethodRef methodRef,
        final List<EntryPoint> targets,
        final Optional<EntryPoint> defaultTarget,
        final MaterializedLambdaDispatchKind dispatchKind,
        final MethodDescriptor descriptor,
        final IrExpression receiver,
        final List<IrExpression> arguments
    ) {
        final String materializedHelper =
            materializedInterfaceHelper(classFile, method, instruction, dispatchKind, descriptor);
        final String resultLocal;
        if (descriptor.returnType() == IrType.OBJECT) {
            resultLocal = newObjectLocal(localDeclarations);
        } else if (descriptor.returnType() == IrType.INT) {
            resultLocal = newIntLocal(localDeclarations);
        } else if (descriptor.returnType() == IrType.VOID) {
            resultLocal = "void" + localDeclarations.size();
        } else {
            throw unsupported(classFile, method, instruction);
        }
        final String materializedLabel = "label_interface_materialized_" + instruction.offset() + "_" + resultLocal;
        final String endLabel = "label_interface_end_" + instruction.offset() + "_" + resultLocal;
        instructions.add(IrInstruction.branchIf(
            materializedLabel,
            IrExpression.intCall(MATERIALIZED_LAMBDA_IS_INSTANCE_SYMBOL, List.of(receiver))
        ));
        final List<IrExpression> concreteArguments = new ArrayList<>(arguments);
        concreteArguments.addFirst(receiver);
        final String concreteSymbol;
        if (targets.size() > 1) {
            concreteSymbol = dispatchSymbol(methodRef);
            dispatches.putIfAbsent(
                concreteSymbol,
                dispatch(concreteSymbol, descriptor, targets)
            );
        } else if (!targets.isEmpty()) {
            concreteSymbol = symbol(targets.getFirst());
        } else {
            concreteSymbol = symbol(defaultTarget.orElseThrow());
        }
        if (descriptor.returnType() == IrType.OBJECT) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(concreteSymbol, concreteArguments)
            ));
        } else if (descriptor.returnType() == IrType.INT) {
            instructions.add(IrInstruction.assignInt(
                resultLocal,
                IrExpression.intCall(concreteSymbol, concreteArguments)
            ));
        } else {
            instructions.add(IrInstruction.callStaticVoid(concreteSymbol, concreteArguments));
        }
        instructions.add(IrInstruction.jump(endLabel));
        instructions.add(IrInstruction.label(materializedLabel));
        final List<IrExpression> materializedArguments = materializedInterfaceArguments(receiver, arguments);
        if (descriptor.returnType() == IrType.OBJECT) {
            instructions.add(IrInstruction.assignObject(
                resultLocal,
                IrExpression.objectCall(materializedHelper, materializedArguments)
            ));
        } else if (descriptor.returnType() == IrType.INT) {
            instructions.add(IrInstruction.assignInt(
                resultLocal,
                IrExpression.intCall(materializedHelper, materializedArguments)
            ));
        } else {
            instructions.add(IrInstruction.callStaticVoid(materializedHelper, materializedArguments));
        }
        instructions.add(IrInstruction.label(endLabel));
        if (descriptor.returnType() == IrType.OBJECT) {
            stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
        } else if (descriptor.returnType() == IrType.INT) {
            stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
        }
    }

    private static List<IrExpression> materializedInterfaceArguments(
        final IrExpression receiver,
        final List<IrExpression> arguments
    ) {
        final List<IrExpression> result = new ArrayList<>(arguments);
        result.addFirst(receiver);
        return List.copyOf(result);
    }

    private static String materializedInterfaceHelper(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MaterializedLambdaDispatchKind dispatchKind,
        final MethodDescriptor descriptor
    ) {
        final List<IrType> parameterTypes = descriptor.parameterTypes();
        if (dispatchKind == MaterializedLambdaDispatchKind.OBJECT
            && descriptor.returnType() == IrType.OBJECT
            && allObjectParameters(parameterTypes)) {
            if (parameterTypes.size() == 1) {
                return MATERIALIZED_LAMBDA_OBJECT_APPLY_SYMBOL;
            }
            if (parameterTypes.size() == 2) {
                return MATERIALIZED_LAMBDA_OBJECT2_APPLY_SYMBOL;
            }
        }
        if (dispatchKind == MaterializedLambdaDispatchKind.LONG_OBJECT
            && descriptor.returnType() == IrType.OBJECT
            && parameterTypes.size() == 1
            && parameterTypes.getFirst() == IrType.LONG) {
            return MATERIALIZED_LAMBDA_LONG_OBJECT_APPLY_SYMBOL;
        }
        if (dispatchKind == MaterializedLambdaDispatchKind.BOOLEAN
            && descriptor.returnType() == IrType.INT
            && parameterTypes.size() == 1
            && parameterTypes.getFirst() == IrType.OBJECT) {
            return MATERIALIZED_LAMBDA_BOOLEAN_APPLY_SYMBOL;
        }
        if (dispatchKind == MaterializedLambdaDispatchKind.VOID
            && descriptor.returnType() == IrType.VOID
            && allObjectParameters(parameterTypes)) {
            if (parameterTypes.size() == 1) {
                return MATERIALIZED_LAMBDA_VOID_APPLY_SYMBOL;
            }
            if (parameterTypes.size() == 2) {
                return MATERIALIZED_LAMBDA_VOID2_APPLY_SYMBOL;
            }
        }
        throw unsupported(classFile, method, instruction);
    }

    private static boolean allObjectParameters(final List<IrType> parameterTypes) {
        for (final IrType parameterType : parameterTypes) {
            if (parameterType != IrType.OBJECT) {
                return false;
            }
        }
        return true;
    }

    private static void lowerFunctionApplyLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final IrExpression argument = popObject(classFile, method, stack);
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_FUNCTION, "function lambda");
        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(
            resultLocal,
            invokeFunctionLambdaExpression(lambda, argument)
        ));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerSupplierGetLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_SUPPLIER, "supplier lambda");
        final String resultLocal = newObjectLocal(localDeclarations);
        instructions.add(IrInstruction.assignObject(resultLocal, invokeSupplierLambdaExpression(lambda)));
        stack.add(StackValue.objectExpression(IrExpression.objectLocal(resultLocal)));
    }

    private static void lowerPredicateTestLambdaCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        final IrExpression argument = popObject(classFile, method, stack);
        final DynamicLambda lambda = popDynamicLambda(classFile, method, instruction, stack, StackKind.LAMBDA_PREDICATE, "predicate lambda");
        final String resultLocal = newIntLocal(localDeclarations);
        instructions.add(IrInstruction.assignInt(
            resultLocal,
            invokePredicateLambdaExpression(lambda, argument)
        ));
        stack.add(StackValue.intExpression(IrExpression.intLocal(resultLocal)));
    }

    private static boolean lowerVirtualThreadBuilderInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (isVirtualThreadBuilderName(methodRef)) {
            final IrExpression name = popObject(classFile, method, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(
                "javan_virtual_thread_builder_name",
                List.of(builder.expression().orElse(IrExpression.objectNull()), name)
            )));
            return true;
        }
        if (isVirtualThreadBuilderNameCounter(methodRef)) {
            final IrExpression start = popLong(classFile, method, stack);
            final IrExpression prefix = popObject(classFile, method, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(
                "javan_virtual_thread_builder_name_counter",
                List.of(builder.expression().orElse(IrExpression.objectNull()), prefix, start)
            )));
            return true;
        }
        if (isVirtualThreadBuilderInheritInheritableThreadLocals(methodRef)) {
            final IrExpression enabled = popInt(classFile, method, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadBuilder(IrExpression.objectCall(
                "javan_virtual_thread_builder_inherit_inheritable_thread_locals",
                List.of(builder.expression().orElse(IrExpression.objectNull()), enabled)
            )));
            return true;
        }
        if (isVirtualThreadBuilderFactory(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            stack.add(StackValue.virtualThreadFactory(IrExpression.objectCall(
                "javan_virtual_thread_builder_factory",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            )));
            return true;
        }
        if (isVirtualThreadFactoryNewThread(methodRef)) {
            final IrExpression runnable = popObject(classFile, method, instruction, stack);
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_factory_new_thread",
                List.of(factory.expression().orElse(IrExpression.objectNull()), runnable)
            );
            return true;
        }
        if (!isVirtualThreadBuilderStart(methodRef) && !isVirtualThreadBuilderUnstarted(methodRef)) {
            return false;
        }
        final IrExpression runnable = popObject(classFile, method, instruction, stack);
        final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
        pushObjectCall(
            instructions,
            stack,
            localDeclarations,
            isVirtualThreadBuilderStart(methodRef)
                ? "javan_virtual_thread_builder_start"
                : "javan_virtual_thread_builder_unstarted",
            List.of(builder.expression().orElse(IrExpression.objectNull()), runnable)
        );
        return true;
    }

    private static boolean lowerVirtualThreadExecutorInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (VirtualThreadInvokePatterns.isExecutorExecute(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.VIRTUAL_THREAD_EXECUTOR)) {
            final IrExpression runnable = popObject(classFile, method, instruction, stack);
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_virtual_thread_executor_execute",
                List.of(executor.expression().orElse(IrExpression.objectNull()), runnable)
            ));
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceSubmit(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.VIRTUAL_THREAD_EXECUTOR)) {
            final IrExpression runnable = popObject(classFile, method, instruction, stack);
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushThreadFutureCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_executor_submit",
                List.of(executor.expression().orElse(IrExpression.objectNull()), runnable)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceShutdown(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.VIRTUAL_THREAD_EXECUTOR)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_virtual_thread_executor_shutdown",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            ));
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceAwaitTermination(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.VIRTUAL_THREAD_EXECUTOR)) {
            final IrExpression unit = popObject(classFile, method, stack);
            final IrExpression timeout = popLong(classFile, method, stack);
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_executor_await_termination",
                List.of(executor.expression().orElse(IrExpression.objectNull()), timeout, unit)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceShutdownNow(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.VIRTUAL_THREAD_EXECUTOR)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_executor_shutdown_now",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isExecutorServiceClose(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.VIRTUAL_THREAD_EXECUTOR)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_virtual_thread_executor_close",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            ));
            return true;
        }
        return false;
    }

    static boolean lowerScheduledThreadPoolExecutorCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorSchedule(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceSchedule(methodRef)) {
            final IrExpression unit = popObject(classFile, method, stack);
            final IrExpression delay = popLong(classFile, method, stack);
            final IrExpression runnable = popObject(classFile, method, stack);
            final StackValue executor = popScheduledThreadPoolExecutor(classFile, method, instruction, stack);
            pushThreadFutureCall(
                instructions,
                stack,
                localDeclarations,
                "javan_scheduled_thread_pool_executor_schedule",
                List.of(executor.expression().orElse(IrExpression.objectNull()), runnable, delay, unit)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleAtFixedRate(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleAtFixedRate(methodRef)) {
            final IrExpression unit = popObject(classFile, method, stack);
            final IrExpression period = popLong(classFile, method, stack);
            final IrExpression initialDelay = popLong(classFile, method, stack);
            final IrExpression runnable = popObject(classFile, method, stack);
            final StackValue executor = popScheduledThreadPoolExecutor(classFile, method, instruction, stack);
            pushThreadFutureCall(
                instructions,
                stack,
                localDeclarations,
                "javan_scheduled_thread_pool_executor_schedule_at_fixed_rate",
                List.of(executor.expression().orElse(IrExpression.objectNull()), runnable, initialDelay, period, unit)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleWithFixedDelay(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleWithFixedDelay(methodRef)) {
            final IrExpression unit = popObject(classFile, method, stack);
            final IrExpression delay = popLong(classFile, method, stack);
            final IrExpression initialDelay = popLong(classFile, method, stack);
            final IrExpression runnable = popObject(classFile, method, stack);
            final StackValue executor = popScheduledThreadPoolExecutor(classFile, method, instruction, stack);
            pushThreadFutureCall(
                instructions,
                stack,
                localDeclarations,
                "javan_scheduled_thread_pool_executor_schedule_with_fixed_delay",
                List.of(executor.expression().orElse(IrExpression.objectNull()), runnable, initialDelay, delay, unit)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorShutdown(methodRef)
            || (VirtualThreadInvokePatterns.isExecutorServiceShutdown(methodRef)
                && hasReceiverKind(stack, methodRef, StackKind.SCHEDULED_THREAD_POOL_EXECUTOR))
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceShutdown(methodRef)) {
            final StackValue executor = popScheduledThreadPoolExecutor(classFile, method, instruction, stack);
            instructions.add(IrInstruction.callStaticVoid(
                "javan_scheduled_thread_pool_executor_shutdown",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            ));
            return true;
        }
        if ((VirtualThreadInvokePatterns.isExecutorServiceAwaitTermination(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.SCHEDULED_THREAD_POOL_EXECUTOR))
            || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorAwaitTermination(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceAwaitTermination(methodRef)) {
            final IrExpression unit = popObject(classFile, method, stack);
            final IrExpression timeout = popLong(classFile, method, stack);
            final StackValue executor = popScheduledThreadPoolExecutor(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_scheduled_thread_pool_executor_await_termination",
                List.of(executor.expression().orElse(IrExpression.objectNull()), timeout, unit)
            );
            return true;
        }
        if ((VirtualThreadInvokePatterns.isExecutorServiceShutdownNow(methodRef)
            && hasReceiverKind(stack, methodRef, StackKind.SCHEDULED_THREAD_POOL_EXECUTOR))
            || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorShutdownNow(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceShutdownNow(methodRef)) {
            final StackValue executor = popScheduledThreadPoolExecutor(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_scheduled_thread_pool_executor_shutdown_now",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        return false;
    }

    private static boolean hasReceiverKind(
        final List<StackValue> stack,
        final MethodRef methodRef,
        final StackKind expectedKind
    ) {
        final MethodDescriptor descriptor = MethodDescriptor.parse(methodRef.descriptor());
        final int receiverIndex = stack.size() - 1 - descriptor.parameterTypes().size();
        return receiverIndex >= 0
            && receiverIndex < stack.size()
            && stack.get(receiverIndex).kind() == expectedKind;
    }

    private static boolean lowerVirtualThreadObservationInterfaceCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final MethodRef methodRef,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations
    ) {
        if (isVirtualThreadBuilderToString(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_builder_to_string",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadBuilderHashCode(methodRef)) {
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_hash_code",
                List.of(builder.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadBuilderEquals(methodRef)) {
            final IrExpression other = popVirtualThreadComparableObject(classFile, method, instruction, stack);
            final StackValue builder = popVirtualThreadBuilder(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_equals",
                List.of(builder.expression().orElse(IrExpression.objectNull()), other)
            );
            return true;
        }
        if (isVirtualThreadFactoryToString(methodRef)) {
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_factory_to_string",
                List.of(factory.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadFactoryHashCode(methodRef)) {
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_hash_code",
                List.of(factory.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadFactoryEquals(methodRef)) {
            final IrExpression other = popVirtualThreadComparableObject(classFile, method, instruction, stack);
            final StackValue factory = popVirtualThreadFactory(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_equals",
                List.of(factory.expression().orElse(IrExpression.objectNull()), other)
            );
            return true;
        }
        if (isVirtualThreadExecutorToString(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushObjectCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_executor_to_string",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadExecutorHashCode(methodRef)) {
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_hash_code",
                List.of(executor.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (isVirtualThreadExecutorEquals(methodRef)) {
            final IrExpression other = popVirtualThreadComparableObject(classFile, method, instruction, stack);
            final StackValue executor = popVirtualThreadExecutor(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_virtual_thread_object_equals",
                List.of(executor.expression().orElse(IrExpression.objectNull()), other)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isFutureCancel(methodRef)) {
            final IrExpression mayInterruptIfRunning = popInt(classFile, method, stack);
            final StackValue future = popThreadFuture(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_future_cancel",
                List.of(future.expression().orElse(IrExpression.objectNull()), mayInterruptIfRunning)
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isFutureIsDone(methodRef)) {
            final StackValue future = popThreadFuture(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_future_is_done",
                List.of(future.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        if (VirtualThreadInvokePatterns.isFutureIsCancelled(methodRef)) {
            final StackValue future = popThreadFuture(classFile, method, instruction, stack);
            pushIntCall(
                instructions,
                stack,
                localDeclarations,
                "javan_future_is_cancelled",
                List.of(future.expression().orElse(IrExpression.objectNull()))
            );
            return true;
        }
        return false;
    }

    static void startVirtualThread(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression runnable
    ) {
        newVirtualThread(instructions, stack, localDeclarations, runnable, IrExpression.objectNull(), true);
    }

    static void startVirtualThread(
        final ClassFile classFile,
        final MethodInfo method,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression runnable,
        final IrExpression name
    ) {
        newVirtualThread(instructions, stack, localDeclarations, runnable, name, true);
    }

    private static void newVirtualThread(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final IrExpression runnable,
        final IrExpression name,
        final boolean start
    ) {
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        final IrExpression thread = IrExpression.objectLocal(localName);
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall("javan_thread_new_virtual", List.of())));
        instructions.add(IrInstruction.callStaticVoid("javan_thread_set_name_nullable", List.of(thread, name)));
        instructions.add(IrInstruction.callStaticVoid("javan_thread_set_target", List.of(thread, runnable)));
        if (start) {
            instructions.add(IrInstruction.callStaticVoid("javan_thread_start", List.of(thread)));
        }
        stack.add(StackValue.objectExpression(thread));
    }

    private static StackValue popVirtualThreadBuilder(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A virtual-thread builder receiver was expected on the bytecode stack.");
        }
        final StackValue builder = pop(stack);
        if (builder.kind() != StackKind.VIRTUAL_THREAD_BUILDER) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("virtual-thread builder receiver", builder.kind()));
        }
        return builder;
    }

    static StackValue popVirtualThreadFactory(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A virtual-thread factory receiver was expected on the bytecode stack.");
        }
        final StackValue factory = pop(stack);
        if (factory.kind() != StackKind.VIRTUAL_THREAD_FACTORY) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("virtual-thread factory receiver", factory.kind()));
        }
        return factory;
    }

    private static StackValue popVirtualThreadExecutor(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A virtual-thread executor receiver was expected on the bytecode stack.");
        }
        final StackValue executor = pop(stack);
        if (executor.kind() != StackKind.VIRTUAL_THREAD_EXECUTOR) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("virtual-thread executor receiver", executor.kind()));
        }
        return executor;
    }

    private static StackValue popScheduledThreadPoolExecutor(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A scheduled thread pool executor receiver was expected on the bytecode stack.");
        }
        final StackValue executor = pop(stack);
        if (executor.kind() != StackKind.SCHEDULED_THREAD_POOL_EXECUTOR) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("scheduled thread pool executor receiver", executor.kind()));
        }
        return executor;
    }

    private static StackValue popThreadFuture(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<StackValue> stack
    ) {
        if (stack.isEmpty()) {
            throw invalidStack(classFile, method, instruction, "A thread-backed Future receiver was expected on the bytecode stack.");
        }
        final StackValue future = pop(stack);
        if (future.kind() != StackKind.THREAD_FUTURE) {
            throw invalidStack(classFile, method, instruction, wrongStackTypeReason("thread-backed Future receiver", future.kind()));
        }
        return future;
    }

    private static boolean isVirtualThreadBuilderName(final MethodRef methodRef) {
        if (!isVirtualThreadBuilderOwner(methodRef.owner())) {
            return false;
        }
        if (!"name".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Thread$Builder".equals(methodRef.owner())) {
            return "(Ljava/lang/String;)Ljava/lang/Thread$Builder;".equals(methodRef.descriptor());
        }
        return "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderNameCounter(final MethodRef methodRef) {
        if (!isVirtualThreadBuilderOwner(methodRef.owner())) {
            return false;
        }
        if (!"name".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Thread$Builder".equals(methodRef.owner())) {
            return "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;".equals(methodRef.descriptor());
        }
        return "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderFactory(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "factory".equals(methodRef.name())
            && "()Ljava/util/concurrent/ThreadFactory;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryNewThread(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "newThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderToString(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderHashCode(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "hashCode".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadBuilderEquals(final MethodRef methodRef) {
        return isVirtualThreadBuilderOwner(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryToString(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryHashCode(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "hashCode".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryEquals(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorToString(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "toString".equals(methodRef.name())
            && "()Ljava/lang/String;".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorHashCode(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "hashCode".equals(methodRef.name())
            && "()I".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorEquals(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "equals".equals(methodRef.name())
            && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    static void pushThreadFutureCall(
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        final String localName = "object" + localDeclarations.size();
        localDeclarations.put(Integer.MIN_VALUE + localDeclarations.size(), new IrLocal(IrType.OBJECT, localName));
        instructions.add(IrInstruction.assignObject(localName, IrExpression.objectCall(symbol, arguments)));
        stack.add(StackValue.threadFuture(IrExpression.objectLocal(localName)));
    }

    static void lowerInterruptAwareThreadWait(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final List<IrInstruction> instructions,
        final List<StackValue> stack,
        final Map<Integer, IrLocal> localDeclarations,
        final Map<Integer, StackValue> pendingExceptionHandlerStacks,
        final SourceLineIndex sourceLines,
        final boolean precheckInterrupt,
        final IrExpression interruptedMessage,
        final String symbol,
        final List<IrExpression> arguments
    ) {
        final String continueLabel = "label_thread_wait_continue_" + instruction.offset() + "_" + localDeclarations.size();
        final String interruptedLabel = "label_thread_wait_interrupted_" + instruction.offset() + "_" + localDeclarations.size();
        if (precheckInterrupt) {
            final int localIndex = localDeclarations.size();
            final String interruptedLocalName = "int" + localIndex;
            localDeclarations.put(Integer.MIN_VALUE + localIndex, new IrLocal(IrType.INT, interruptedLocalName));
            instructions.add(IrInstruction.assignInt(
                interruptedLocalName,
                IrExpression.intCall("javan_thread_interrupted", List.of())
            ));
            instructions.add(IrInstruction.branchIf(
                continueLabel,
                IrExpression.intComparison("==", IrExpression.intLocal(interruptedLocalName), IrExpression.intLiteral(0))
            ));
            instructions.add(IrInstruction.jump(interruptedLabel));
        }
        instructions.add(IrInstruction.label(continueLabel));
        final int interruptedResultLocalIndex = localDeclarations.size();
        final String interruptedResultLocalName = "int" + interruptedResultLocalIndex;
        localDeclarations.put(Integer.MIN_VALUE + interruptedResultLocalIndex, new IrLocal(IrType.INT, interruptedResultLocalName));
        instructions.add(IrInstruction.assignInt(
            interruptedResultLocalName,
            IrExpression.intCall(symbol, arguments)
        ));
        final String successLabel = "label_thread_wait_success_" + instruction.offset() + "_" + interruptedResultLocalIndex;
        instructions.add(IrInstruction.branchIf(
            successLabel,
            IrExpression.intComparison("==", IrExpression.intLocal(interruptedResultLocalName), IrExpression.intLiteral(0))
        ));
        instructions.add(IrInstruction.label(interruptedLabel));
        routePendingPlatformException(
            classFile,
            method,
            instruction,
            instructions,
            stack,
            pendingExceptionHandlerStacks,
            sourceLines,
            "java/lang/InterruptedException",
            interruptedMessage
        );
        instructions.add(IrInstruction.label(successLabel));
    }
}
