package javan.analysis;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.codegen.MethodDescriptor;
import javan.ir.IrType;

/**
 * Shared bytecode-shape helpers for the currently supported virtual-thread invocation slice.
 */
public final class VirtualThreadInvokePatterns {
    private VirtualThreadInvokePatterns() {
    }

    public static boolean isThreadOfVirtual(final MethodRef methodRef) {
        return "java/lang/Thread".equals(methodRef.owner())
            && "ofVirtual".equals(methodRef.name())
            && "()Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    public static boolean isThreadBuilderOfVirtualStart(final MethodRef methodRef) {
        return isThreadBuilderVirtualOwner(methodRef.owner())
            && "start".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    public static boolean isThreadBuilderOfVirtualUnstarted(final MethodRef methodRef) {
        return isThreadBuilderVirtualOwner(methodRef.owner())
            && "unstarted".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    public static boolean isThreadBuilderOfVirtualName(final MethodRef methodRef) {
        if (!isThreadBuilderVirtualOwner(methodRef.owner())) {
            return false;
        }
        if (!"name".equals(methodRef.name())) {
            return false;
        }
        if ("java/lang/Thread$Builder".equals(methodRef.owner())) {
            return "(Ljava/lang/String;)Ljava/lang/Thread$Builder;".equals(methodRef.descriptor())
                || "(Ljava/lang/String;J)Ljava/lang/Thread$Builder;".equals(methodRef.descriptor());
        }
        return "(Ljava/lang/String;)Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor())
            || "(Ljava/lang/String;J)Ljava/lang/Thread$Builder$OfVirtual;".equals(methodRef.descriptor());
    }

    public static int virtualThreadBuilderNameProducerOffset(final MethodRef methodRef) {
        return methodRef.descriptor().contains(";J)") ? 3 : 2;
    }

    public static boolean isThreadBuilderVirtualFactory(final MethodRef methodRef) {
        return isThreadBuilderVirtualOwner(methodRef.owner())
            && "factory".equals(methodRef.name())
            && "()Ljava/util/concurrent/ThreadFactory;".equals(methodRef.descriptor());
    }

    public static boolean isThreadBuilderVirtualOwner(final String owner) {
        return "java/lang/Thread$Builder".equals(owner)
            || "java/lang/Thread$Builder$OfVirtual".equals(owner);
    }

    public static boolean isThreadFactoryNewThread(final MethodRef methodRef) {
        return "java/util/concurrent/ThreadFactory".equals(methodRef.owner())
            && "newThread".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)Ljava/lang/Thread;".equals(methodRef.descriptor());
    }

    public static boolean isExecutorsNewVirtualThreadPerTaskExecutor(final MethodRef methodRef) {
        return "java/util/concurrent/Executors".equals(methodRef.owner())
            && "newVirtualThreadPerTaskExecutor".equals(methodRef.name())
            && "()Ljava/util/concurrent/ExecutorService;".equals(methodRef.descriptor());
    }

    public static boolean isExecutorsNewThreadPerTaskExecutor(final MethodRef methodRef) {
        return "java/util/concurrent/Executors".equals(methodRef.owner())
            && "newThreadPerTaskExecutor".equals(methodRef.name())
            && "(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;".equals(methodRef.descriptor());
    }

    public static boolean isExecutorExecute(final MethodRef methodRef) {
        return ("java/util/concurrent/Executor".equals(methodRef.owner())
            || "java/util/concurrent/ExecutorService".equals(methodRef.owner()))
            && "execute".equals(methodRef.name())
            && "(Ljava/lang/Runnable;)V".equals(methodRef.descriptor());
    }

    public static boolean isExecutorServiceShutdown(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "shutdown".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }

    public static boolean isExecutorServiceClose(final MethodRef methodRef) {
        return "java/util/concurrent/ExecutorService".equals(methodRef.owner())
            && "close".equals(methodRef.name())
            && "()V".equals(methodRef.descriptor());
    }

    public static int virtualThreadReceiverProducerIndex(
        final List<Instruction> instructions,
        final int callIndex
    ) {
        if (callIndex < 2 || callIndex > instructions.size()) {
            return -1;
        }
        final int runnableStackWidth = runnableArgumentStackWidth(instructions, callIndex);
        if (runnableStackWidth < 0) {
            return -1;
        }
        return callIndex - 1 - runnableStackWidth;
    }

    public static int runnableArgumentStackWidth(
        final List<Instruction> instructions,
        final int callIndex
    ) {
        if (callIndex < 1 || callIndex > instructions.size()) {
            return -1;
        }
        final Instruction producer = instructions.get(callIndex - 1);
        if (localLoadSlot(producer) >= 0) {
            return 1;
        }
        final Optional<MethodRef> constructorRef = producer.methodRef();
        if (constructorRef.isPresent()
            && "<init>".equals(constructorRef.orElseThrow().name())
            && callIndex >= 3
            && instructions.get(callIndex - 2).opcode() == 89
            && instructions.get(callIndex - 3).opcode() == 187) {
            return 3;
        }
        return -1;
    }

    public static int previousLocalStoreIndex(
        final List<Instruction> instructions,
        final int startIndex,
        final int slot
    ) {
        for (int index = startIndex; index >= 0; index--) {
            if (localStoreSlot(instructions.get(index)) == slot) {
                return index;
            }
        }
        return -1;
    }

    public static int transparentReferenceProducerIndex(
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        int index = producerIndex;
        while (index >= 0 && index < instructions.size() && instructions.get(index).opcode() == 192) {
            index--;
        }
        return index;
    }

    public static int localLoadSlot(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 25 -> instruction.operands()[0] & 0xFF;
            case 42 -> 0;
            case 43 -> 1;
            case 44 -> 2;
            case 45 -> 3;
            default -> -1;
        };
    }

    public static int localStoreSlot(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 58 -> instruction.operands()[0] & 0xFF;
            case 75 -> 0;
            case 76 -> 1;
            case 77 -> 2;
            case 78 -> 3;
            default -> -1;
        };
    }

    public static boolean isSupportedBuilderWrapperMethod(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method
    ) {
        if (!method.isStatic() || !returnsVirtualThreadBuilder(method.descriptor())) {
            return false;
        }
        return exactVirtualThreadReturnChain(classes, classFile, method, false);
    }

    public static boolean isSupportedFactoryWrapperMethod(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method
    ) {
        if (!method.isStatic() || !returnsThreadFactory(method.descriptor())) {
            return false;
        }
        return exactVirtualThreadReturnChain(classes, classFile, method, true);
    }

    public static boolean isSupportedBuilderWrapperCall(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef
    ) {
        final Optional<ResolvedMethod> resolved = resolvedMethod(classes, methodRef);
        if (resolved.isEmpty()) {
            return false;
        }
        return isSupportedBuilderWrapperMethod(
            classes,
            resolved.orElseThrow().classFile(),
            resolved.orElseThrow().method()
        );
    }

    public static boolean isSupportedFactoryWrapperCall(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef
    ) {
        final Optional<ResolvedMethod> resolved = resolvedMethod(classes, methodRef);
        if (resolved.isEmpty()) {
            return false;
        }
        return isSupportedFactoryWrapperMethod(
            classes,
            resolved.orElseThrow().classFile(),
            resolved.orElseThrow().method()
        );
    }

    private static Optional<ResolvedMethod> resolvedMethod(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef
    ) {
        final ClassFile classFile = classes.get(methodRef.owner());
        if (classFile == null) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = classFile.method(methodRef.name(), methodRef.descriptor());
        if (method.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedMethod(classFile, method.orElseThrow()));
    }

    private static boolean exactVirtualThreadReturnChain(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final boolean requireFactory
    ) {
        if (method.code().isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        if (instructions.size() < 2
            || method.code().orElseThrow().exceptionTableLength() != 0
            || instructions.getLast().opcode() != 176) {
            return false;
        }
        int index = 0;
        if (!isBuilderRootProducer(classes, instructions, index)) {
            return false;
        }
        index++;
        while (index < instructions.size() - 1) {
            final NameStep nameStep = nameStepAt(method, instructions, index);
            if (nameStep != NameStep.NONE) {
                index += nameStep.width;
                continue;
            }
            if (requireFactory
                && index == instructions.size() - 2
                && instructions.get(index).methodRef().isPresent()
                && isThreadBuilderVirtualFactory(instructions.get(index).methodRef().orElseThrow())) {
                return true;
            }
            return false;
        }
        return !requireFactory;
    }

    private static boolean isBuilderRootProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int index
    ) {
        if (index < 0 || index >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> methodRef = instructions.get(index).methodRef();
        if (methodRef.isEmpty()) {
            return false;
        }
        if (isThreadOfVirtual(methodRef.orElseThrow())) {
            return true;
        }
        return instructions.get(index).opcode() == 184
            && isSupportedBuilderWrapperCall(classes, methodRef.orElseThrow());
    }

    private static NameStep nameStepAt(
        final MethodInfo method,
        final List<Instruction> instructions,
        final int index
    ) {
        if (index >= instructions.size()) {
            return NameStep.NONE;
        }
        if (!isStringConstant(instructions.get(index))) {
            if (isParameterObjectLoad(method, instructions.get(index))
                && index + 1 < instructions.size()
                && instructions.get(index + 1).methodRef().isPresent()
                && isThreadBuilderOfVirtualName(instructions.get(index + 1).methodRef().orElseThrow())
                && !instructions.get(index + 1).methodRef().orElseThrow().descriptor().contains(";J)")) {
                return NameStep.PARAMETER_STRING;
            }
            if (isParameterObjectLoad(method, instructions.get(index))
                && index + 2 < instructions.size()
                && isParameterLongLoad(method, instructions.get(index + 1))
                && instructions.get(index + 2).methodRef().isPresent()
                && isThreadBuilderOfVirtualName(instructions.get(index + 2).methodRef().orElseThrow())
                && instructions.get(index + 2).methodRef().orElseThrow().descriptor().contains(";J)")) {
                return NameStep.PARAMETER_STRING_LONG;
            }
            return NameStep.NONE;
        }
        if (index + 1 < instructions.size()
            && instructions.get(index + 1).methodRef().isPresent()
            && isThreadBuilderOfVirtualName(instructions.get(index + 1).methodRef().orElseThrow())
            && !instructions.get(index + 1).methodRef().orElseThrow().descriptor().contains(";J)")) {
            return NameStep.STRING;
        }
        if (index + 2 < instructions.size()
            && isLongConstant(instructions.get(index + 1))
            && instructions.get(index + 2).methodRef().isPresent()
            && isThreadBuilderOfVirtualName(instructions.get(index + 2).methodRef().orElseThrow())
            && instructions.get(index + 2).methodRef().orElseThrow().descriptor().contains(";J)")) {
            return NameStep.STRING_LONG;
        }
        return NameStep.NONE;
    }

    private static boolean isStringConstant(final Instruction instruction) {
        return instruction.stringValue().isPresent() || instruction.opcode() == 18 || instruction.opcode() == 19;
    }

    private static boolean isLongConstant(final Instruction instruction) {
        return instruction.opcode() == 9 || instruction.opcode() == 10 || instruction.opcode() == 20;
    }

    private static boolean isParameterObjectLoad(final MethodInfo method, final Instruction instruction) {
        final int slot = localLoadSlot(instruction);
        return slot >= 0 && parameterTypeAtSlot(method, slot) == IrType.OBJECT;
    }

    private static boolean isParameterLongLoad(final MethodInfo method, final Instruction instruction) {
        final int slot = longLoadSlot(instruction);
        return slot >= 0 && parameterTypeAtSlot(method, slot) == IrType.LONG;
    }

    private static IrType parameterTypeAtSlot(final MethodInfo method, final int slot) {
        int localSlot = 0;
        for (final IrType parameterType : MethodDescriptor.parse(method.descriptor()).parameterTypes()) {
            if (localSlot == slot) {
                return parameterType;
            }
            localSlot += switch (parameterType) {
                case LONG, DOUBLE -> 2;
                default -> 1;
            };
        }
        return null;
    }

    private static int longLoadSlot(final Instruction instruction) {
        return switch (instruction.opcode()) {
            case 22 -> instruction.operands()[0] & 0xFF;
            case 30 -> 0;
            case 31 -> 1;
            case 32 -> 2;
            case 33 -> 3;
            default -> -1;
        };
    }

    private static boolean returnsVirtualThreadBuilder(final String descriptor) {
        return descriptor.endsWith(")Ljava/lang/Thread$Builder;")
            || descriptor.endsWith(")Ljava/lang/Thread$Builder$OfVirtual;");
    }

    private static boolean returnsThreadFactory(final String descriptor) {
        return descriptor.endsWith(")Ljava/util/concurrent/ThreadFactory;");
    }

    private record ResolvedMethod(ClassFile classFile, MethodInfo method) {
    }

    private enum NameStep {
        NONE(0),
        STRING(2),
        STRING_LONG(3),
        PARAMETER_STRING(2),
        PARAMETER_STRING_LONG(3);

        private final int width;

        NameStep(final int width) {
            this.width = width;
        }
    }
}
