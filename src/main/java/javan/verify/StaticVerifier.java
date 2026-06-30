package javan.verify;

import javan.analysis.EntryPoint;
import javan.analysis.VirtualThreadInvokePatterns;
import javan.analysis.VirtualThreadInvokePatterns;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.BytecodeSupport;
import javan.compat.JdkCallSupport;
import javan.compat.JavanHostOnlyMethods;
import javan.compat.JavanNativeSubstitutions;
import javan.compat.NetworkApiSupport;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verifies the static Java profile for reachable code and warns about unreachable violations.
 */
public final class StaticVerifier {
    private final ForbiddenApiRules forbiddenApiRules = new ForbiddenApiRules();

    /**
     * Verifies parsed classes against the initial native profile.
     *
     * @param classes parsed classes
     * @param reachable reachable method identities
     * @return diagnostics
     */
    public List<Diagnostic> verify(final Map<String, ClassFile> classes, final List<EntryPoint> reachable) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        for (final ClassFile classFile : classes.values()) {
            for (final MethodInfo method : classFile.methods()) {
                final int isReachable = containsEntry(reachable, new EntryPoint(classFile.name(), method.name(), method.descriptor()));
                diagnostics.addAll(verifyMethod(classes, classFile, method, isReachable));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static int containsEntry(final List<EntryPoint> entries, final EntryPoint target) {
        for (final EntryPoint entry : entries) {
            if (entry.className().equals(target.className())) {
                if (entry.methodName().equals(target.methodName())) {
                    if (entry.descriptor().equals(target.descriptor())) {
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

    private List<Diagnostic> verifyMethod(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final int reachable
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (reachable == 1 && method.isNative()) {
            diagnostics.add(error(
                classFile,
                method,
                "JAVAN013",
                "native methods are not supported",
                method.name() + method.descriptor(),
                "The current runtime cannot link arbitrary JNI/native methods.",
                "Replace the native method with Java code or a future javan runtime intrinsic."
            ));
        }
        if (reachable == 0 && JavanNativeSubstitutions.isSubstitutedFallbackMethod(classFile.name(), method)) {
            return diagnostics;
        }
        if (reachable == 0 && JavanHostOnlyMethods.isHostOnlyMethod(classFile.name(), method)) {
            return diagnostics;
        }
        if (method.isSynchronized()) {
            diagnostics.add(synchronizationDiagnostic(
                classFile,
                method,
                "synchronized method",
                "The current native runtime does not implement Java monitor enter/exit semantics, lock ownership, or the broader parallel-thread model required for synchronized methods.",
                "Remove synchronized from this method, keep this flow on the JVM, or wait until Javan's broader platform-thread and monitor support lands.",
                reachable
            ));
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isPresent()) {
            final CodeAttribute methodCode = code.orElseThrow();
            final int hasMonitorInstructions = containsMonitorInstructions(methodCode) ? 1 : 0;
            final int exactVirtualThreadWrapperMethod = isSupportedExactVirtualThreadWrapperMethod(classes, classFile, method) ? 1 : 0;
            if (hasMonitorInstructions == 1) {
                diagnostics.add(synchronizationDiagnostic(
                    classFile,
                    method,
                    "synchronized block",
                    "The current native runtime does not implement Java monitor enter/exit semantics, lock ownership, synthetic monitor-release handlers, or the broader parallel-thread model required for synchronized blocks.",
                    "Remove the synchronized block, keep this flow on the JVM, or wait until Javan's broader platform-thread and monitor support lands.",
                    reachable
                ));
            }
            if (unsupportedExceptionHandlers(classes, methodCode) && !supportedSyntheticSwitchMapClass(classFile, method, methodCode)) {
                diagnostics.add(exceptionHandlerDiagnostic(classFile, method, methodCode.exceptionTableLength(), reachable));
            }
            diagnostics.addAll(unsupportedThreadLifecycleDiagnostics(classFile, method, methodCode, reachable));
            diagnostics.addAll(blockingWaitDiagnostics(classFile, method, methodCode, reachable));
            final int application = classFile.application() ? 1 : 0;
            final int unsupportedStringConstant = containsUnsupportedRuntimeStringConstant(methodCode) ? 1 : 0;
            final List<Instruction> instructions = methodCode.instructions();
            for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++) {
                final Instruction instruction = instructions.get(instructionIndex);
                diagnostics.addAll(verifyInstruction(
                    classes,
                    classFile,
                    method,
                    instructions,
                    instructionIndex,
                    instruction,
                    reachable,
                    application,
                    unsupportedStringConstant,
                    hasMonitorInstructions,
                    exactVirtualThreadWrapperMethod
                ));
            }
        }
        return diagnostics;
    }

    private static List<Diagnostic> unsupportedThreadLifecycleDiagnostics(
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code,
        final int reachable
    ) {
        if (reachable == 0 && !classFile.application()) {
            return List.of();
        }
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<Instruction> instructions = code.instructions();
        for (int index = 0; index < instructions.size(); index++) {
            if (matchesCurrentThreadLifecycle(instructions, index, "start")) {
                diagnostics.add(threadLifecycleDiagnostic(
                    classFile,
                    method,
                    "Thread.currentThread().start()",
                    "The native runtime models the current thread as already started. Starting it again reaches the duplicate-start runtime panic instead of a supported thread runtime.",
                    "Do not call Thread.start() on Thread.currentThread(); start a separate Thread instance or keep this flow on the JVM until real parallel platform-thread support lands.",
                    reachable
                ));
            }
            final int aliasedCurrentThreadStartLocal = currentThreadLifecycleAliasLocal(instructions, index, "start");
            if (aliasedCurrentThreadStartLocal >= 0) {
                diagnostics.add(threadLifecycleDiagnostic(
                    classFile,
                    method,
                    "Thread.currentThread() alias on local " + aliasedCurrentThreadStartLocal + " then start()",
                    "The native runtime models the current thread as already started. Starting a local alias of Thread.currentThread() reaches the duplicate-start runtime panic instead of a supported thread runtime.",
                    "Do not call Thread.start() on a Thread.currentThread() alias; start a separate Thread instance or keep this flow on the JVM until real parallel platform-thread support lands.",
                    reachable
                ));
            }
            if (matchesCurrentThreadLifecycle(instructions, index, "join")) {
                diagnostics.add(threadLifecycleDiagnostic(
                    classFile,
                    method,
                    "Thread.currentThread().join()",
                    "Joining the current thread has no supported native runtime model and currently reaches the explicit self-join runtime panic.",
                    "Remove self-join logic, join a different Thread instance, or keep this flow on the JVM until broader platform-thread support lands.",
                    reachable
                ));
            }
            final int aliasedCurrentThreadJoinLocal = currentThreadLifecycleAliasLocal(instructions, index, "join");
            if (aliasedCurrentThreadJoinLocal >= 0) {
                diagnostics.add(threadLifecycleDiagnostic(
                    classFile,
                    method,
                    "Thread.currentThread() alias on local " + aliasedCurrentThreadJoinLocal + " then join()",
                    "Joining a local alias of the current thread has no supported native runtime model and currently reaches the explicit self-join runtime panic.",
                    "Remove self-join logic, join a different Thread instance, or keep this flow on the JVM until broader platform-thread support lands.",
                    reachable
                ));
            }
            final int duplicateStartLocal = duplicateStraightLineThreadStartLocal(instructions, index);
            if (duplicateStartLocal >= 0) {
                diagnostics.add(threadLifecycleDiagnostic(
                    classFile,
                    method,
                    "duplicate Thread.start() on local " + duplicateStartLocal,
                    "This method repeats Thread.start() on the same local Thread reference in one straight-line bytecode path. The current runtime rejects duplicate starts instead of pretending to support them.",
                    "Create a new Thread before the second start, or keep duplicate-start flows on the JVM until broader platform-thread lifecycle support lands.",
                    reachable
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static List<Diagnostic> blockingWaitDiagnostics(
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code,
        final int reachable
    ) {
        if (reachable == 0) {
            return List.of();
        }
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final List<Instruction> instructions = code.instructions();
        for (int index = 0; index < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            if (invokesThreadSleep(instruction)) {
                diagnostics.add(blockingWaitDiagnostic(
                    classFile,
                    method,
                    "Thread.sleep(long)",
                    "This reachable code performs an explicit blocking wait. The current thread-analysis slice can identify the wait site, but it does not yet model whether the surrounding task is tiny, CPU-bound, or a broader scalability risk.",
                    "Keep explicit sleeps intentional, prefer event-driven or bounded coordination where high concurrency matters, and inspect thread reports before moving this flow into service-heavy or future virtual-thread workloads."
                ));
                continue;
            }
            final Optional<String> lockSupportWait = lockSupportWaitSubject(instruction);
            if (lockSupportWait.isPresent()) {
                diagnostics.add(blockingWaitDiagnostic(
                    classFile,
                    method,
                    lockSupportWait.orElseThrow(),
                    "This reachable code parks the current thread until a permit, interrupt, or time boundary arrives. The current thread-analysis slice can identify the park site, but it does not yet model scheduler fairness, carrier utilization, or whether the surrounding task should block at all.",
                    "Keep parking intentional, pair it with clear unpark ownership, and inspect thread reports before scaling this flow into broader platform-thread or future virtual-thread workloads."
                ));
                continue;
            }
            if (invokesThreadLifecycle(instruction, "join")
                && !blockingJoinCoveredByLifecycleGuard(instructions, index)) {
                diagnostics.add(blockingWaitDiagnostic(
                    classFile,
                    method,
                    "Thread.join()",
                    "This reachable code performs an explicit blocking wait for another thread to finish. The current thread-analysis slice can identify the join site, but it does not yet model throughput, queueing, or whether the caller is doing avoidable waiting.",
                    "Keep joins intentional, prefer tighter task ownership or bounded coordination where high concurrency matters, and inspect thread reports before scaling this flow out."
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static boolean supportedSyntheticSwitchMapClass(
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code
    ) {
        if (!classFile.isSynthetic()) {
            return false;
        }
        if (!"<clinit>".equals(method.name())) {
            return false;
        }
        if (code.exceptionTableLength() == 0) {
            return false;
        }
        if (!hasOnlySwitchMapFields(classFile)) {
            return false;
        }
        for (final CodeException handler : code.exceptionTable()) {
            if (handler.catchType().isEmpty()) {
                return false;
            }
            if (!"java/lang/NoSuchFieldError".equals(handler.catchType().orElseThrow())) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnlySwitchMapFields(final ClassFile classFile) {
        if (classFile.fields().isEmpty()) {
            return false;
        }
        for (final javan.classfile.FieldInfo field : classFile.fields()) {
            if (!field.name().startsWith("$SwitchMap$")) {
                return false;
            }
            if (!"[I".equals(field.descriptor())) {
                return false;
            }
        }
        return true;
    }

    private List<Diagnostic> verifyInstruction(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> instructions,
        final int instructionIndex,
        final Instruction instruction,
        final int reachable,
        final int application,
        final int unsupportedStringConstant,
        final int hasMonitorInstructions,
        final int exactVirtualThreadWrapperMethod
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (reachable == 0 && application == 0) {
            return diagnostics;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isPresent()) {
            final MethodRef target = methodRef.orElseThrow();
            final int unsupportedMonitorMethod = unsupportedMonitorMethod(target) ? 1 : 0;
            final int unsupportedConcurrencyApi = unsupportedConcurrencyRuntimeApi(
                classes,
                classFile,
                method,
                instructions,
                instructionIndex,
                target,
                exactVirtualThreadWrapperMethod == 1
            ) ? 1 : 0;
            final Optional<String> forbiddenReason = forbiddenApiRules.forbiddenReason(target);
            if (forbiddenReason.isPresent()) {
                diagnostics.add(apiDiagnostic(classFile, method, target, forbiddenReason.orElseThrow(), reachable));
            }
            if (unsupportedMonitorMethod == 1) {
                diagnostics.add(monitorMethodDiagnostic(classFile, method, target, reachable));
            }
            if (unsupportedConcurrencyApi == 1) {
                diagnostics.add(concurrencyRuntimeDiagnostic(classFile, method, target, reachable));
            }
            if (NetworkApiSupport.isNetworkCall(target) && !JdkCallSupport.isSupported(target)) {
                diagnostics.add(networkCallDiagnostic(classFile, method, target, reachable));
            } else if (unsupportedMonitorMethod == 0
                && unsupportedConcurrencyApi == 0
                && unsupportedJdkCall(target)
                && !ignoredGeneratedEnumValueOfCall(classFile, method, target, reachable)) {
                diagnostics.add(jdkCallDiagnostic(classFile, method, target, reachable));
            }
        }
        if (unsupportedNewArrayType(instruction)) {
            diagnostics.add(newArrayDiagnostic(classFile, method, instruction, reachable));
        }
        if (unsupportedInvokedynamic(instruction) && !ignoredUnreachableRecordObjectMethod(classFile, method, instruction, reachable)) {
            diagnostics.add(invokedynamicDiagnostic(classFile, method, instruction, reachable));
        }
        if (unsupportedStringConstant == 1 && unsupportedRuntimeStringSemanticCall(instruction)) {
            diagnostics.add(stringConstantDiagnostic(classFile, method, instruction, reachable));
        }
        if (unsupportedInstanceOfTarget(classes, instruction)) {
            diagnostics.add(instanceOfTargetDiagnostic(classFile, method, instruction, reachable));
        }
        if (hasMonitorInstructions == 1 && isMonitorInstruction(instruction)) {
            return diagnostics;
        }
        if (BytecodeSupport.classify(instruction.opcode()) != BytecodeSupport.Status.NATIVE_SUPPORTED) {
            diagnostics.add(opcodeDiagnostic(classFile, method, instruction, reachable));
        }
        return diagnostics;
    }

    private static boolean unsupportedNewArrayType(final Instruction instruction) {
        if (instruction.opcode() != 188) {
            return false;
        }
        if (instruction.operands().length == 0) {
            return true;
        }
        return !supportedNewArrayType(instruction.operands()[0] & 0xFF);
    }

    private static boolean containsMonitorInstructions(final CodeAttribute code) {
        for (final Instruction instruction : code.instructions()) {
            if (isMonitorInstruction(instruction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMonitorInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 194 || opcode == 195) {
            return true;
        }
        return false;
    }

    private static boolean containsUnsupportedRuntimeStringConstant(final CodeAttribute code) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.stringValue().isPresent()
                && !Strings2.isRuntimeAsciiStringConstant(instruction.stringValue().orElseThrow())) {
                return true;
            }
        }
        return false;
    }

    private static boolean unsupportedRuntimeStringSemanticCall(final Instruction instruction) {
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return false;
        }
        final MethodRef target = methodRef.orElseThrow();
        if (!"java/lang/String".equals(target.owner())) {
            return false;
        }
        if ("length".equals(target.name()) && "()I".equals(target.descriptor())) {
            return true;
        }
        if ("charAt".equals(target.name()) && "(I)C".equals(target.descriptor())) {
            return true;
        }
        if ("substring".equals(target.name())
            && ("(I)Ljava/lang/String;".equals(target.descriptor()) || "(II)Ljava/lang/String;".equals(target.descriptor()))) {
            return true;
        }
        if ("indexOf".equals(target.name())) {
            return stringIndexDescriptor(target.descriptor());
        }
        if ("lastIndexOf".equals(target.name())) {
            return stringIndexDescriptor(target.descriptor());
        }
        return false;
    }

    private static boolean stringIndexDescriptor(final String descriptor) {
        if ("(I)I".equals(descriptor)) {
            return true;
        }
        if ("(II)I".equals(descriptor)) {
            return true;
        }
        if ("(Ljava/lang/String;)I".equals(descriptor)) {
            return true;
        }
        return "(Ljava/lang/String;I)I".equals(descriptor);
    }

    private static boolean unsupportedExceptionHandlers(final Map<String, ClassFile> classes, final CodeAttribute code) {
        if (code.exceptionTableLength() == 0) {
            return false;
        }
        for (final CodeException handler : code.exceptionTable()) {
            if (!supportedExceptionHandler(classes, code, handler)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportedExceptionHandler(final Map<String, ClassFile> classes, final CodeAttribute code, final CodeException handler) {
        if (supportedEnumSwitchMapHandler(classes, code, handler)) {
            return true;
        }
        if (supportedSynchronizedMonitorHandler(code, handler)) {
            return true;
        }
        if (supportedInterruptedWaitHandler(code, handler)) {
            return true;
        }
        if (supportedFinallyRethrowHandler(code, handler)) {
            return true;
        }
        if (handler.catchType().isEmpty()) {
            return false;
        }
        if (!isPlatformThrowable(handler.catchType().orElseThrow())) {
            return false;
        }
        int hasAthrow = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (instruction.opcode() == 191) {
                hasAthrow = 1;
            }
            if (!supportedExplicitThrowRangeInstruction(instruction)) {
                return false;
            }
        }
        if (hasAthrow == 1) {
            return true;
        }
        return false;
    }

    private static boolean supportedSynchronizedMonitorHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isPresent()) {
            return false;
        }
        final Optional<Instruction> first = instructionAtOffset(code, handler.handlerPc());
        if (first.isEmpty()) {
            return false;
        }
        final int throwableLocal = astoreLocalIndex(first.orElseThrow());
        if (throwableLocal < 0) {
            return false;
        }
        final Optional<Instruction> second = instructionAtOffset(code, nextInstructionOffset(first.orElseThrow()));
        if (second.isEmpty() || aloadLocalIndex(second.orElseThrow()) < 0) {
            return false;
        }
        final Optional<Instruction> third = instructionAtOffset(code, nextInstructionOffset(second.orElseThrow()));
        if (third.isEmpty() || third.orElseThrow().opcode() != 195) {
            return false;
        }
        final Optional<Instruction> fourth = instructionAtOffset(code, nextInstructionOffset(third.orElseThrow()));
        if (fourth.isEmpty() || aloadLocalIndex(fourth.orElseThrow()) != throwableLocal) {
            return false;
        }
        final Optional<Instruction> fifth = instructionAtOffset(code, nextInstructionOffset(fourth.orElseThrow()));
        if (fifth.isEmpty() || fifth.orElseThrow().opcode() != 191) {
            return false;
        }
        return true;
    }

    private static boolean supportedInterruptedWaitHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()) {
            return false;
        }
        if (!JdkCallSupport.isPlatformThrowableAssignable("java/lang/InterruptedException", handler.catchType().orElseThrow())) {
            return false;
        }
        int hasWaitCall = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (isInterruptedWaitCall(instruction)) {
                hasWaitCall = 1;
                continue;
            }
            if (!supportedInterruptedWaitProtectedInstruction(instruction)) {
                return false;
            }
        }
        return hasWaitCall == 1;
    }

    private static boolean supportedFinallyRethrowHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isPresent()) {
            return false;
        }
        final Optional<Instruction> first = instructionAtOffset(code, handler.handlerPc());
        if (first.isEmpty()) {
            return false;
        }
        final int throwableLocal = astoreLocalIndex(first.orElseThrow());
        if (throwableLocal < 0) {
            return false;
        }
        int hasAthrow = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (instruction.offset() == handler.handlerPc() && astoreLocalIndex(instruction) == throwableLocal) {
                continue;
            }
            if (instruction.opcode() == 191) {
                hasAthrow = 1;
            }
            if (!supportedExplicitThrowRangeInstruction(instruction)) {
                return false;
            }
        }
        if (hasAthrow == 0) {
            return false;
        }
        final List<Instruction> instructions = code.instructions();
        int handlerIndex = -1;
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() == handler.handlerPc()) {
                handlerIndex = index;
                break;
            }
        }
        if (handlerIndex < 0) {
            return false;
        }
        for (int index = handlerIndex + 1; index + 1 < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            if (aloadLocalIndex(instruction) == throwableLocal && instructions.get(index + 1).opcode() == 191) {
                return true;
            }
            if (!supportedFinallyCleanupInstruction(instruction)) {
                return false;
            }
        }
        return false;
    }

    private static boolean supportedEnumSwitchMapHandler(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler
    ) {
        if (handler.catchType().isEmpty()) {
            return false;
        }
        if (!"java/lang/NoSuchFieldError".equals(handler.catchType().orElseThrow())) {
            return false;
        }
        final Optional<Instruction> handlerInstruction = instructionAtOffset(code, handler.handlerPc());
        if (handlerInstruction.isEmpty()) {
            return false;
        }
        if (!isEnumSwitchMapHandlerInstruction(handlerInstruction.orElseThrow().opcode())) {
            return false;
        }
        int hasProtectedInstruction = 0;
        int hasIastore = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            hasProtectedInstruction = 1;
            if (instruction.opcode() == 79) {
                hasIastore = 1;
            }
            if (!supportedEnumSwitchMapInstruction(classes, instruction)) {
                return false;
            }
        }
        if (hasProtectedInstruction == 0) {
            return false;
        }
        if (hasIastore == 1) {
            return true;
        }
        return false;
    }

    private static Optional<Instruction> instructionAtOffset(final CodeAttribute code, final int offset) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() == offset) {
                return Optional.of(instruction);
            }
        }
        return Optional.empty();
    }

    private static int nextInstructionOffset(final Instruction instruction) {
        return instruction.offset() + 1 + instruction.operands().length;
    }

    private static boolean supportedEnumSwitchMapInstruction(final Map<String, ClassFile> classes, final Instruction instruction) {
        if (instruction.opcode() == 79) {
            return true;
        }
        if (isAload(instruction.opcode())) {
            return true;
        }
        if (instruction.opcode() >= 2 && instruction.opcode() <= 8) {
            return true;
        }
        if (instruction.opcode() == 16) {
            return true;
        }
        if (instruction.opcode() == 17) {
            return true;
        }
        if (instruction.opcode() == 178) {
            final Optional<FieldRef> fieldRef = instruction.fieldRef();
            if (fieldRef.isEmpty()) {
                return false;
            }
            final FieldRef target = fieldRef.orElseThrow();
            final ClassFile owner = classes.get(target.owner());
            if ("[I".equals(target.descriptor())) {
                return true;
            }
            if (owner == null) {
                return false;
            }
            return owner.isEnum();
        }
        if (instruction.opcode() == 182) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isEmpty()) {
                return false;
            }
            final MethodRef target = methodRef.orElseThrow();
            final ClassFile owner = classes.get(target.owner());
            if (owner == null) {
                return false;
            }
            if (!owner.isEnum()) {
                return false;
            }
            if (!"ordinal".equals(target.name())) {
                return false;
            }
            return "()I".equals(target.descriptor());
        }
        return false;
    }

    private static boolean matchesCurrentThreadLifecycle(
        final List<Instruction> instructions,
        final int index,
        final String lifecycleMethod
    ) {
        if (index < 0 || index + 1 >= instructions.size()) {
            return false;
        }
        return invokesThreadCurrentThread(instructions.get(index))
            && invokesThreadLifecycle(instructions.get(index + 1), lifecycleMethod);
    }

    private static boolean invokesThreadCurrentThread(final Instruction instruction) {
        if (instruction.opcode() != 184) {
            return false;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return false;
        }
        final MethodRef target = methodRef.orElseThrow();
        return "java/lang/Thread".equals(target.owner())
            && "currentThread".equals(target.name())
            && "()Ljava/lang/Thread;".equals(target.descriptor());
    }

    private static boolean invokesThreadSleep(final Instruction instruction) {
        if (instruction.opcode() != 184) {
            return false;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return false;
        }
        final MethodRef target = methodRef.orElseThrow();
        return "java/lang/Thread".equals(target.owner())
            && "sleep".equals(target.name())
            && "(J)V".equals(target.descriptor());
    }

    private static boolean invokesThreadStart(final List<Instruction> instructions, final int index) {
        if (index < 0 || index >= instructions.size()) {
            return false;
        }
        return invokesThreadLifecycle(instructions.get(index), "start");
    }

    private static int duplicateStraightLineThreadStartLocal(final List<Instruction> instructions, final int index) {
        if (index < 0 || index + 3 >= instructions.size()) {
            return -1;
        }
        final int firstLocal = aloadLocalIndex(instructions.get(index));
        if (firstLocal < 0 || !invokesThreadStart(instructions, index + 1)) {
            return -1;
        }
        final int secondLocal = aloadLocalIndex(instructions.get(index + 2));
        if (secondLocal < 0 || secondLocal != firstLocal || !invokesThreadStart(instructions, index + 3)) {
            return -1;
        }
        return firstLocal;
    }

    private static int currentThreadLifecycleAliasLocal(
        final List<Instruction> instructions,
        final int index,
        final String lifecycleMethod
    ) {
        if (index < 0 || index + 3 >= instructions.size()) {
            return -1;
        }
        if (!invokesThreadCurrentThread(instructions.get(index))) {
            return -1;
        }
        final int local = astoreLocalIndex(instructions.get(index + 1));
        if (local < 0) {
            return -1;
        }
        if (aloadLocalIndex(instructions.get(index + 2)) != local) {
            return -1;
        }
        if (!invokesThreadLifecycle(instructions.get(index + 3), lifecycleMethod)) {
            return -1;
        }
        return local;
    }

    private static boolean blockingJoinCoveredByLifecycleGuard(final List<Instruction> instructions, final int index) {
        if (index >= 1 && matchesCurrentThreadLifecycle(instructions, index - 1, "join")) {
            return true;
        }
        if (index >= 3 && currentThreadLifecycleAliasLocal(instructions, index - 3, "join") >= 0) {
            return true;
        }
        return false;
    }

    private static boolean invokesThreadLifecycle(final Instruction instruction, final String lifecycleMethod) {
        if (instruction.opcode() != 182) {
            return false;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return false;
        }
        final MethodRef target = methodRef.orElseThrow();
        return "java/lang/Thread".equals(target.owner())
            && lifecycleMethod.equals(target.name())
            && "()V".equals(target.descriptor());
    }

    private static boolean isAstore(final int opcode) {
        if (opcode == 58) {
            return true;
        }
        if (opcode < 75) {
            return false;
        }
        if (opcode > 78) {
            return false;
        }
        return true;
    }

    private static int aloadLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 25) {
            if (instruction.operands().length == 0) {
                return -1;
            }
            return instruction.operands()[0] & 0xFF;
        }
        if (!isAload(opcode)) {
            return -1;
        }
        return opcode - 42;
    }

    private static int astoreLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 58) {
            if (instruction.operands().length == 0) {
                return -1;
            }
            return instruction.operands()[0] & 0xFF;
        }
        if (!isAstore(opcode)) {
            return -1;
        }
        return opcode - 75;
    }

    private static boolean isAload(final int opcode) {
        if (opcode == 25) {
            return true;
        }
        if (opcode < 42) {
            return false;
        }
        if (opcode > 45) {
            return false;
        }
        return true;
    }

    private static boolean isEnumSwitchMapHandlerInstruction(final int opcode) {
        if (opcode == 87) {
            return true;
        }
        return isAstore(opcode);
    }

    private static boolean supportedExplicitThrowRangeInstruction(final Instruction instruction) {
        if (instruction.opcode() == 183) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isEmpty()) {
                return false;
            }
            return isSupportedExceptionConstructor(methodRef.orElseThrow());
        }
        final int opcode = instruction.opcode();
        if (opcode == 0) {
            return true;
        }
        if (opcode == 18) {
            return true;
        }
        if (opcode == 19) {
            return true;
        }
        if (opcode == 20) {
            return true;
        }
        if (opcode == 87) {
            return true;
        }
        if (opcode == 89) {
            return true;
        }
        if (opcode == 187) {
            return true;
        }
        if (opcode == 191) {
            return true;
        }
        return false;
    }

    private static boolean supportedInterruptedWaitProtectedInstruction(final Instruction instruction) {
        if (supportedExplicitThrowRangeInstruction(instruction)) {
            return true;
        }
        if (isAload(instruction.opcode())) {
            return true;
        }
        if (instruction.opcode() == 22) {
            return true;
        }
        if (instruction.opcode() >= 30 && instruction.opcode() <= 33) {
            return true;
        }
        if (instruction.opcode() >= 2 && instruction.opcode() <= 8) {
            return true;
        }
        if (instruction.opcode() == 16 || instruction.opcode() == 17) {
            return true;
        }
        if (instruction.opcode() >= 9 && instruction.opcode() <= 10) {
            return true;
        }
        if (instruction.opcode() == 178) {
            final Optional<FieldRef> fieldRef = instruction.fieldRef();
            if (fieldRef.isEmpty()) {
                return false;
            }
            final FieldRef target = fieldRef.orElseThrow();
            if (!"java/lang/System".equals(target.owner())) {
                return false;
            }
            if ("Ljava/io/PrintStream;".equals(target.descriptor())) {
                return "out".equals(target.name()) || "err".equals(target.name());
            }
            return false;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isPresent()) {
            final MethodRef target = methodRef.orElseThrow();
            if (instruction.opcode() == 184) {
                if ("java/lang/Thread".equals(target.owner())
                    && "currentThread".equals(target.name())
                    && "()Ljava/lang/Thread;".equals(target.descriptor())) {
                    return true;
                }
            }
            if (instruction.opcode() == 182) {
                if ("java/io/PrintStream".equals(target.owner()) && "println".equals(target.name())) {
                    return "(Ljava/lang/String;)V".equals(target.descriptor())
                        || "(Ljava/lang/Object;)V".equals(target.descriptor())
                        || "(I)V".equals(target.descriptor())
                        || "(J)V".equals(target.descriptor())
                        || "(Z)V".equals(target.descriptor());
                }
                if ("java/lang/Thread".equals(target.owner())
                    && "isInterrupted".equals(target.name())
                    && "()Z".equals(target.descriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean supportedFinallyCleanupInstruction(final Instruction instruction) {
        if (instruction.opcode() == 178) {
            final Optional<FieldRef> fieldRef = instruction.fieldRef();
            if (fieldRef.isEmpty()) {
                return false;
            }
            final FieldRef target = fieldRef.orElseThrow();
            return "java/lang/System".equals(target.owner())
                && "out".equals(target.name())
                && "Ljava/io/PrintStream;".equals(target.descriptor());
        }
        if (instruction.opcode() == 18 || instruction.opcode() == 19 || instruction.opcode() == 20) {
            return true;
        }
        if (instruction.opcode() == 182 || instruction.opcode() == 184) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isEmpty()) {
                return false;
            }
            return JdkCallSupport.supportedCall(methodRef.orElseThrow()).isPresent();
        }
        return false;
    }

    private static boolean isInterruptedWaitCall(final Instruction instruction) {
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return false;
        }
        final MethodRef target = methodRef.orElseThrow();
        if (instruction.opcode() == 184) {
            return "java/lang/Thread".equals(target.owner())
                && "sleep".equals(target.name())
                && "(J)V".equals(target.descriptor());
        }
        if (instruction.opcode() == 182) {
            if (isObjectWaitMethod(target)) {
                return true;
            }
            return "java/lang/Thread".equals(target.owner())
                && "join".equals(target.name())
                && "()V".equals(target.descriptor());
        }
        return false;
    }

    private static boolean unsupportedMonitorMethod(final MethodRef methodRef) {
        if (isObjectWaitMethod(methodRef)) {
            return true;
        }
        if (!"java/lang/Object".equals(methodRef.owner())) {
            return false;
        }
        if ("notify".equals(methodRef.name())) {
            return "()V".equals(methodRef.descriptor());
        }
        if ("notifyAll".equals(methodRef.name())) {
            return "()V".equals(methodRef.descriptor());
        }
        return false;
    }

    private static boolean isObjectWaitMethod(final MethodRef methodRef) {
        if (!"java/lang/Object".equals(methodRef.owner())) {
            return false;
        }
        if (!"wait".equals(methodRef.name())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            return true;
        }
        if ("(J)V".equals(methodRef.descriptor())) {
            return true;
        }
        return "(JI)V".equals(methodRef.descriptor());
    }

    private static Diagnostic monitorMethodDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final int reachable
    ) {
        final String reason = "The current native runtime does not implement Java monitor wait/notify semantics, ownership checks, parking, wake-up ordering, or interruption behavior for Object monitor methods.";
        final String fix = "Keep Object.wait/notify code on the JVM, or wait until Javan's broader platform-thread and monitor runtime lands.";
        return synchronizationDiagnostic(classFile, method, monitorMethodSubject(methodRef), reason, fix, reachable);
    }

    private static boolean unsupportedConcurrencyRuntimeApi(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef,
        final boolean exactVirtualThreadWrapperMethod
    ) {
        final String owner = methodRef.owner();
        if (isSupportedDirectVirtualThreadBuilderFlow(classes, instructions, instructionIndex, methodRef)) {
            return false;
        }
        if (isSupportedDirectVirtualThreadExecutorFlow(classes, instructions, instructionIndex, methodRef)) {
            return false;
        }
        if (exactVirtualThreadWrapperMethod && isVirtualThreadWrapperInternalCall(methodRef)) {
            return false;
        }
        if ("java/lang/Thread".equals(owner) && "ofVirtual".equals(methodRef.name())) {
            return true;
        }
        if ("java/lang/Thread$Builder".equals(owner)) {
            return true;
        }
        if ("java/lang/Thread$Builder$OfVirtual".equals(owner)) {
            return true;
        }
        if ("java/lang/ThreadLocal".equals(owner)) {
            return !isSupportedThreadLocalRuntimeCall(methodRef);
        }
        if ("java/lang/InheritableThreadLocal".equals(owner)) {
            return true;
        }
        if ("java/util/concurrent/Executors".equals(owner)) {
            return true;
        }
        if ("java/util/concurrent/Executor".equals(owner)) {
            return true;
        }
        if ("java/util/concurrent/ExecutorService".equals(owner)) {
            return true;
        }
        return false;
    }

    private static Optional<String> lockSupportWaitSubject(final Instruction instruction) {
        if (instruction.methodRef().isEmpty()) {
            return Optional.empty();
        }
        final MethodRef method = instruction.methodRef().orElseThrow();
        if (!"java/util/concurrent/locks/LockSupport".equals(method.owner())) {
            return Optional.empty();
        }
        if ("park".equals(method.name()) && "()V".equals(method.descriptor())) {
            return Optional.of("LockSupport.park()");
        }
        if ("parkNanos".equals(method.name()) && "(J)V".equals(method.descriptor())) {
            return Optional.of("LockSupport.parkNanos(long)");
        }
        if ("parkUntil".equals(method.name()) && "(J)V".equals(method.descriptor())) {
            return Optional.of("LockSupport.parkUntil(long)");
        }
        return Optional.empty();
    }

    private static boolean isSupportedExactVirtualThreadWrapperMethod(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method
    ) {
        return VirtualThreadInvokePatterns.isSupportedBuilderWrapperMethod(classes, classFile, method)
            || VirtualThreadInvokePatterns.isSupportedFactoryWrapperMethod(classes, classFile, method);
    }

    private static boolean isVirtualThreadWrapperInternalCall(final MethodRef methodRef) {
        return isThreadOfVirtual(methodRef)
            || isThreadBuilderVirtualName(methodRef)
            || isThreadBuilderVirtualFactory(methodRef);
    }

    private static boolean isSupportedThreadLocalRuntimeCall(final MethodRef methodRef) {
        if (!"java/lang/ThreadLocal".equals(methodRef.owner())) {
            return false;
        }
        if ("<init>".equals(methodRef.name())) {
            return "()V".equals(methodRef.descriptor());
        }
        if ("get".equals(methodRef.name())) {
            return "()Ljava/lang/Object;".equals(methodRef.descriptor());
        }
        if ("set".equals(methodRef.name())) {
            return "(Ljava/lang/Object;)V".equals(methodRef.descriptor());
        }
        if ("remove".equals(methodRef.name())) {
            return "()V".equals(methodRef.descriptor());
        }
        return false;
    }

    private static boolean isSupportedDirectVirtualThreadBuilderFlow(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        if (isThreadOfVirtual(methodRef)) {
            for (int candidateIndex = instructionIndex + 1; candidateIndex < instructions.size(); candidateIndex++) {
                if (supportsVirtualThreadBuilderStartFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadBuilderUnstartedFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadFactoryNewThreadFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadExecutorFactoryFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadBuilderObservationFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadFactoryObservationFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsDiscardedVirtualThreadBuilderFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsDiscardedVirtualThreadFactoryFromRoot(classes, instructions, candidateIndex, instructionIndex)) {
                    return true;
                }
            }
            return false;
        }
        if (isThreadBuilderVirtualName(methodRef)) {
            for (int candidateIndex = instructionIndex + 1; candidateIndex < instructions.size(); candidateIndex++) {
                if (supportsVirtualThreadBuilderStartFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadBuilderUnstartedFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadFactoryNewThreadFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadExecutorFactoryFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadBuilderObservationFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadFactoryObservationFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsDiscardedVirtualThreadBuilderFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsDiscardedVirtualThreadFactoryFromRoot(classes, instructions, candidateIndex, instructionIndex)) {
                    return true;
                }
            }
            return false;
        }
        if (isThreadBuilderVirtualFactory(methodRef)) {
            for (int candidateIndex = instructionIndex + 1; candidateIndex < instructions.size(); candidateIndex++) {
                if (supportsVirtualThreadFactoryNewThreadFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadExecutorFactoryFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsVirtualThreadFactoryObservationFromRoot(classes, instructions, candidateIndex, instructionIndex)
                    || supportsDiscardedVirtualThreadFactoryFromRoot(classes, instructions, candidateIndex, instructionIndex)) {
                    return true;
                }
            }
            return false;
        }
        if (isThreadBuilderOfVirtualStart(methodRef)) {
            return supportsVirtualThreadBuilderStart(classes, instructions, instructionIndex);
        }
        if (isThreadBuilderOfVirtualUnstarted(methodRef)) {
            return supportsVirtualThreadBuilderUnstarted(classes, instructions, instructionIndex);
        }
        if (isThreadFactoryNewThread(methodRef)) {
            return supportsVirtualThreadFactoryNewThread(classes, instructions, instructionIndex);
        }
        if (isVirtualThreadBuilderObservationMethod(methodRef)) {
            return supportedVirtualThreadBuilderObservationReceiver(classes, instructions, instructionIndex, -1, methodRef);
        }
        if (isVirtualThreadFactoryObservationMethod(methodRef)) {
            return supportedVirtualThreadFactoryObservationReceiver(classes, instructions, instructionIndex, -1, methodRef);
        }
        return false;
    }

    private static boolean isSupportedDirectVirtualThreadExecutorFlow(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        if (isExecutorsNewVirtualThreadPerTaskExecutor(methodRef)) {
            return true;
        }
        if (isExecutorsNewThreadPerTaskExecutor(methodRef)) {
            return supportsVirtualThreadExecutorFactory(classes, instructions, instructionIndex);
        }
        if (isExecutorExecute(methodRef)) {
            return supportsVirtualThreadExecutorExecute(classes, instructions, instructionIndex);
        }
        if (isExecutorServiceShutdown(methodRef) || isExecutorServiceClose(methodRef)) {
            return supportedVirtualThreadExecutorReceiver(classes, instructions, instructionIndex);
        }
        if (isVirtualThreadExecutorObservationMethod(methodRef)) {
            return supportedVirtualThreadExecutorObservationReceiver(classes, instructions, instructionIndex, methodRef);
        }
        return false;
    }

    private static boolean supportsVirtualThreadBuilderObservationFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> methodRef = instructions.get(instructionIndex).methodRef();
        return methodRef.isPresent()
            && isVirtualThreadBuilderObservationMethod(methodRef.orElseThrow())
            && supportedVirtualThreadBuilderObservationReceiver(classes, instructions, instructionIndex, rootProducerIndex, methodRef.orElseThrow());
    }

    private static boolean supportsVirtualThreadFactoryObservationFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> methodRef = instructions.get(instructionIndex).methodRef();
        return methodRef.isPresent()
            && isVirtualThreadFactoryObservationMethod(methodRef.orElseThrow())
            && supportedVirtualThreadFactoryObservationReceiver(classes, instructions, instructionIndex, rootProducerIndex, methodRef.orElseThrow());
    }

    private static boolean supportsVirtualThreadBuilderStart(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        return supportsVirtualThreadBuilderThreadCreation(classes, instructions, startIndex, true, -1);
    }

    private static boolean supportsVirtualThreadBuilderStartFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final int rootProducerIndex
    ) {
        return supportsVirtualThreadBuilderThreadCreation(classes, instructions, startIndex, true, rootProducerIndex);
    }

    private static boolean supportsVirtualThreadBuilderUnstarted(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        return supportsVirtualThreadBuilderThreadCreation(classes, instructions, startIndex, false, -1);
    }

    private static boolean supportsVirtualThreadBuilderUnstartedFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final int rootProducerIndex
    ) {
        return supportsVirtualThreadBuilderThreadCreation(classes, instructions, startIndex, false, rootProducerIndex);
    }

    private static boolean supportsVirtualThreadBuilderThreadCreation(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final boolean started,
        final int rootProducerIndex
    ) {
        if (startIndex < 4 || startIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> startRef = instructions.get(startIndex).methodRef();
        if (startRef.isEmpty()) {
            return false;
        }
        final MethodRef threadCreationRef = startRef.orElseThrow();
        if (started && !isThreadBuilderOfVirtualStart(threadCreationRef)) {
            return false;
        }
        if (!started && !isThreadBuilderOfVirtualUnstarted(threadCreationRef)) {
            return false;
        }
        if (!supportedVirtualThreadBuilderReceiver(classes, instructions, startIndex, rootProducerIndex)) {
            return false;
        }
        return supportedRunnableProducer(classes, instructions, startIndex - 1);
    }

    private static boolean supportsVirtualThreadFactoryNewThread(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        return supportsVirtualThreadFactoryNewThread(classes, instructions, startIndex, -1);
    }

    private static boolean supportsVirtualThreadFactoryNewThreadFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final int rootProducerIndex
    ) {
        return supportsVirtualThreadFactoryNewThread(classes, instructions, startIndex, rootProducerIndex);
    }

    private static boolean supportsVirtualThreadFactoryNewThread(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final int rootProducerIndex
    ) {
        if (startIndex < 4 || startIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> newThreadRef = instructions.get(startIndex).methodRef();
        if (newThreadRef.isEmpty() || !isThreadFactoryNewThread(newThreadRef.orElseThrow())) {
            return false;
        }
        if (!supportedVirtualThreadFactoryReceiver(classes, instructions, startIndex, rootProducerIndex)) {
            return false;
        }
        return supportedRunnableProducer(classes, instructions, startIndex - 1);
    }

    private static boolean supportsVirtualThreadExecutorFactory(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex
    ) {
        return supportsVirtualThreadExecutorFactoryFromRoot(classes, instructions, instructionIndex, -1);
    }

    private static boolean supportsDiscardedVirtualThreadBuilderFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size() || instructions.get(instructionIndex).opcode() != 87) {
            return false;
        }
        if (!supportedDiscardedVirtualThreadRootProducer(classes, instructions, rootProducerIndex)) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, instructionIndex - 1, rootProducerIndex);
    }

    private static boolean supportsDiscardedVirtualThreadFactoryFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size() || instructions.get(instructionIndex).opcode() != 87) {
            return false;
        }
        if (!supportedDiscardedVirtualThreadRootProducer(classes, instructions, rootProducerIndex)) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, instructionIndex - 1, rootProducerIndex);
    }

    private static boolean supportedDiscardedVirtualThreadRootProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int rootProducerIndex
    ) {
        if (rootProducerIndex < 0 || rootProducerIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> rootMethodRef = instructions.get(rootProducerIndex).methodRef();
        if (rootMethodRef.isEmpty()) {
            return false;
        }
        if (isThreadOfVirtual(rootMethodRef.orElseThrow())) {
            return true;
        }
        if (isThreadBuilderVirtualName(rootMethodRef.orElseThrow())) {
            return supportedVirtualThreadBuilderProducer(
                classes,
                instructions,
                rootProducerIndex - virtualThreadBuilderNameProducerOffset(rootMethodRef.orElseThrow()),
                -1
            );
        }
        if (isThreadBuilderVirtualFactory(rootMethodRef.orElseThrow())) {
            return supportedVirtualThreadBuilderProducer(classes, instructions, rootProducerIndex - 1, -1);
        }
        return false;
    }

    private static boolean supportsVirtualThreadExecutorFactoryFromRoot(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> methodRef = instructions.get(instructionIndex).methodRef();
        return methodRef.isPresent()
            && isExecutorsNewThreadPerTaskExecutor(methodRef.orElseThrow())
            && supportedVirtualThreadFactoryProducer(classes, instructions, instructionIndex - 1, rootProducerIndex);
    }

    private static boolean supportsVirtualThreadExecutorExecute(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> executeRef = instructions.get(instructionIndex).methodRef();
        if (executeRef.isEmpty() || !isExecutorExecute(executeRef.orElseThrow())) {
            return false;
        }
        if (!supportedVirtualThreadExecutorReceiver(classes, instructions, instructionIndex)) {
            return false;
        }
        return supportedRunnableProducer(classes, instructions, instructionIndex - 1);
    }

    private static boolean supportedVirtualThreadBuilderReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        return supportedVirtualThreadBuilderReceiver(classes, instructions, startIndex, -1);
    }

    private static boolean supportedVirtualThreadBuilderReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final int rootProducerIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, startIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, receiverIndex, rootProducerIndex);
    }

    private static boolean supportedVirtualThreadBuilderObservationReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex,
        final MethodRef methodRef
    ) {
        final int receiverIndex = observationReceiverProducerIndex(instructions, instructionIndex, methodRef);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, receiverIndex, rootProducerIndex);
    }

    private static boolean supportedVirtualThreadBuilderProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex,
        final int rootProducerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        if (rootProducerIndex >= 0 && transparentProducerIndex == rootProducerIndex) {
            return true;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (isThreadOfVirtual(methodRef.orElseThrow())) {
                return rootProducerIndex < 0;
            }
            if (producer.opcode() == 184
                && VirtualThreadInvokePatterns.isSupportedBuilderWrapperCall(classes, methodRef.orElseThrow())) {
                return rootProducerIndex < 0;
            }
            if (isThreadBuilderVirtualName(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(
                    classes,
                    instructions,
                    transparentProducerIndex - virtualThreadBuilderNameProducerOffset(methodRef.orElseThrow()),
                    rootProducerIndex
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
        if (rootProducerIndex >= 0
            && rootProducerMutatesBuilderLocal(instructions, rootProducerIndex, loadSlot, storeIndex, transparentProducerIndex)) {
            return supportedVirtualThreadBuilderProducer(classes, instructions, storeIndex - 1, -1);
        }
        return supportedVirtualThreadBuilderProducer(classes, instructions, storeIndex - 1, rootProducerIndex);
    }

    private static boolean rootProducerMutatesBuilderLocal(
        final List<Instruction> instructions,
        final int rootProducerIndex,
        final int loadSlot,
        final int storeIndex,
        final int producerIndex
    ) {
        if (rootProducerIndex <= storeIndex || rootProducerIndex >= producerIndex || rootProducerIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> methodRef = instructions.get(rootProducerIndex).methodRef();
        if (methodRef.isEmpty() || !isThreadBuilderVirtualName(methodRef.orElseThrow())) {
            return false;
        }
        final int receiverIndex = rootProducerIndex - virtualThreadBuilderNameProducerOffset(methodRef.orElseThrow());
        if (receiverIndex < 0) {
            return false;
        }
        return localLoadSlot(instructions.get(receiverIndex)) == loadSlot;
    }

    private static boolean supportedVirtualThreadFactoryReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex
    ) {
        return supportedVirtualThreadFactoryReceiver(classes, instructions, startIndex, -1);
    }

    private static boolean supportedVirtualThreadFactoryReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int startIndex,
        final int rootProducerIndex
    ) {
        final int receiverIndex = VirtualThreadInvokePatterns.virtualThreadReceiverProducerIndex(instructions, startIndex);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, receiverIndex, rootProducerIndex);
    }

    private static boolean supportedVirtualThreadFactoryObservationReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final int rootProducerIndex,
        final MethodRef methodRef
    ) {
        final int receiverIndex = observationReceiverProducerIndex(instructions, instructionIndex, methodRef);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFactoryProducer(classes, instructions, receiverIndex, rootProducerIndex);
    }

    private static boolean supportedVirtualThreadFactoryProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex,
        final int rootProducerIndex
    ) {
        final int transparentProducerIndex = VirtualThreadInvokePatterns.transparentReferenceProducerIndex(instructions, producerIndex);
        if (transparentProducerIndex < 0) {
            return false;
        }
        if (rootProducerIndex >= 0 && transparentProducerIndex == rootProducerIndex) {
            return true;
        }
        final Instruction producer = instructions.get(transparentProducerIndex);
        final Optional<MethodRef> methodRef = producer.methodRef();
        if (methodRef.isPresent()) {
            if (isThreadBuilderVirtualFactory(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(classes, instructions, transparentProducerIndex - 1, rootProducerIndex);
            }
            if (producer.opcode() == 184
                && VirtualThreadInvokePatterns.isSupportedFactoryWrapperCall(classes, methodRef.orElseThrow())) {
                return rootProducerIndex < 0;
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
        return supportedVirtualThreadFactoryProducer(classes, instructions, storeIndex - 1, rootProducerIndex);
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

    private static boolean supportedVirtualThreadExecutorObservationReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        final int receiverIndex = observationReceiverProducerIndex(instructions, instructionIndex, methodRef);
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadExecutorProducer(classes, instructions, receiverIndex);
    }

    private static int observationReceiverProducerIndex(
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return -1;
        }
        if ("()Ljava/lang/String;".equals(methodRef.descriptor()) || "()I".equals(methodRef.descriptor())) {
            return instructionIndex - 1;
        }
        if ("(Ljava/lang/Object;)Z".equals(methodRef.descriptor())) {
            return instructionIndex - 2;
        }
        return -1;
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
                return supportedVirtualThreadFactoryProducer(classes, instructions, transparentProducerIndex - 1, -1);
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

    private static boolean supportedRunnableProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int producerIndex
    ) {
        if (producerIndex < 0 || producerIndex >= instructions.size()) {
            return false;
        }
        final Instruction producer = instructions.get(producerIndex);
        final Optional<MethodRef> constructorRef = producer.methodRef();
        if (constructorRef.isPresent()) {
            final MethodRef constructor = constructorRef.orElseThrow();
            if ("<init>".equals(constructor.name())
                && isAssignableToRunnable(classes, constructor.owner())
                && !isAssignableTo(classes, constructor.owner(), "java/lang/Thread")
                && producerIndex >= 2
                && instructions.get(producerIndex - 1).opcode() == 89) {
                final Instruction allocation = instructions.get(producerIndex - 2);
                return allocation.opcode() == 187
                    && allocation.className().isPresent()
                    && allocation.className().orElseThrow().equals(constructor.owner());
            }
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, producerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedRunnableProducer(classes, instructions, storeIndex - 1);
    }

    private static int localLoadSlot(final Instruction instruction) {
        return VirtualThreadInvokePatterns.localLoadSlot(instruction);
    }

    private static int localStoreSlot(final Instruction instruction) {
        return VirtualThreadInvokePatterns.localStoreSlot(instruction);
    }

    private static boolean isThreadOfVirtual(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadOfVirtual(methodRef);
    }

    private static boolean isThreadBuilderOfVirtualStart(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualStart(methodRef);
    }

    private static boolean isThreadBuilderOfVirtualUnstarted(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualUnstarted(methodRef);
    }

    private static boolean isThreadBuilderVirtualName(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualName(methodRef);
    }

    private static int virtualThreadBuilderNameProducerOffset(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.virtualThreadBuilderNameProducerOffset(methodRef);
    }

    private static boolean isThreadBuilderVirtualFactory(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderVirtualFactory(methodRef);
    }

    private static boolean isThreadFactoryNewThread(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadFactoryNewThread(methodRef);
    }

    private static boolean isExecutorsNewVirtualThreadPerTaskExecutor(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorsNewVirtualThreadPerTaskExecutor(methodRef);
    }

    private static boolean isExecutorsNewThreadPerTaskExecutor(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorsNewThreadPerTaskExecutor(methodRef);
    }

    private static boolean isExecutorExecute(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorExecute(methodRef);
    }

    private static boolean isExecutorServiceShutdown(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceShutdown(methodRef);
    }

    private static boolean isExecutorServiceClose(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceClose(methodRef);
    }

    private static boolean isVirtualThreadBuilderOwner(final String owner) {
        return VirtualThreadInvokePatterns.isThreadBuilderVirtualOwner(owner);
    }

    private static boolean isVirtualThreadBuilderObservationMethod(final MethodRef methodRef) {
        if (!isVirtualThreadBuilderOwner(methodRef.owner())) {
            return false;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return true;
        }
        if ("hashCode".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            return true;
        }
        return "equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadFactoryObservationMethod(final MethodRef methodRef) {
        if (!"java/util/concurrent/ThreadFactory".equals(methodRef.owner())) {
            return false;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return true;
        }
        if ("hashCode".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            return true;
        }
        return "equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isVirtualThreadExecutorObservationMethod(final MethodRef methodRef) {
        if (!"java/util/concurrent/ExecutorService".equals(methodRef.owner())) {
            return false;
        }
        if ("toString".equals(methodRef.name()) && "()Ljava/lang/String;".equals(methodRef.descriptor())) {
            return true;
        }
        if ("hashCode".equals(methodRef.name()) && "()I".equals(methodRef.descriptor())) {
            return true;
        }
        return "equals".equals(methodRef.name()) && "(Ljava/lang/Object;)Z".equals(methodRef.descriptor());
    }

    private static boolean isAssignableToRunnable(final Map<String, ClassFile> classes, final String owner) {
        return isAssignableTo(classes, owner, "java/lang/Runnable");
    }

    private static Diagnostic concurrencyRuntimeDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final int reachable
    ) {
        final String reason = "The current native runtime does not implement this broader executor, scheduler, or concurrent-runtime API surface yet.";
        final String fix = "Keep this concurrency API on the JVM, or wait until Javan's broader scheduler and virtual-thread runtime lands.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN077", "unsupported reachable concurrency runtime API", concurrencyRuntimeSubject(methodRef), reason, fix);
        }
        return warning(classFile, method, "JAVAN177", "unsupported concurrency runtime API in unreachable code", concurrencyRuntimeSubject(methodRef), reason, fix);
    }

    private static String concurrencyRuntimeSubject(final MethodRef methodRef) {
        final String owner = methodRef.owner();
        if ("java/util/concurrent/Executors".equals(owner)) {
            if ("newSingleThreadExecutor".equals(methodRef.name())) {
                return "Executors.newSingleThreadExecutor()";
            }
            if ("newCachedThreadPool".equals(methodRef.name())) {
                return "Executors.newCachedThreadPool()";
            }
            if ("newVirtualThreadPerTaskExecutor".equals(methodRef.name())) {
                return "Executors.newVirtualThreadPerTaskExecutor()";
            }
            if ("newThreadPerTaskExecutor".equals(methodRef.name())) {
                return "Executors.newThreadPerTaskExecutor(ThreadFactory)";
            }
        }
        if ("java/lang/Thread".equals(owner) && "ofVirtual".equals(methodRef.name())) {
            return "Thread.ofVirtual()";
        }
        if ("java/lang/Thread$Builder".equals(owner)) {
            if ("start".equals(methodRef.name())) {
                return "Thread.Builder.start(Runnable)";
            }
            if ("unstarted".equals(methodRef.name())) {
                return "Thread.Builder.unstarted(Runnable)";
            }
            if ("name".equals(methodRef.name())) {
                return "Thread.Builder.name(...)";
            }
            if ("factory".equals(methodRef.name())) {
                return "Thread.Builder.factory()";
            }
        }
        if ("java/lang/Thread$Builder$OfVirtual".equals(owner)) {
            if ("start".equals(methodRef.name())) {
                return "Thread.Builder.OfVirtual.start(Runnable)";
            }
            if ("unstarted".equals(methodRef.name())) {
                return "Thread.Builder.OfVirtual.unstarted(Runnable)";
            }
            if ("name".equals(methodRef.name())) {
                return "Thread.Builder.OfVirtual.name(...)";
            }
            if ("factory".equals(methodRef.name())) {
                return "Thread.Builder.OfVirtual.factory()";
            }
        }
        if ("java/util/concurrent/ThreadFactory".equals(owner) && "newThread".equals(methodRef.name())) {
            return "ThreadFactory.newThread(Runnable)";
        }
        if ("java/lang/ThreadLocal".equals(owner)) {
            if ("<init>".equals(methodRef.name())) {
                return "ThreadLocal.<init>()";
            }
        }
        if ("java/lang/InheritableThreadLocal".equals(owner)) {
            if ("<init>".equals(methodRef.name())) {
                return "InheritableThreadLocal.<init>()";
            }
        }
        if ("java/util/concurrent/Executor".equals(owner) || "java/util/concurrent/ExecutorService".equals(owner)) {
            if ("execute".equals(methodRef.name())) {
                return "Executor.execute(Runnable)";
            }
        }
        if ("java/util/concurrent/ExecutorService".equals(owner)) {
            if ("shutdown".equals(methodRef.name())) {
                return "ExecutorService.shutdown()";
            }
            if ("close".equals(methodRef.name())) {
                return "ExecutorService.close()";
            }
        }
        return methodRef.display();
    }

    private static String monitorMethodSubject(final MethodRef methodRef) {
        if ("wait".equals(methodRef.name())) {
            if ("(J)V".equals(methodRef.descriptor())) {
                return "Object.wait(long)";
            }
            if ("(JI)V".equals(methodRef.descriptor())) {
                return "Object.wait(long,int)";
            }
            return "Object.wait()";
        }
        if ("notify".equals(methodRef.name())) {
            return "Object.notify()";
        }
        return "Object.notifyAll()";
    }

    private static boolean isSupportedExceptionConstructor(final MethodRef methodRef) {
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        if (!JdkCallSupport.isPlatformThrowable(methodRef.owner())) {
            return false;
        }
        if ("()V".equals(methodRef.descriptor())) {
            return true;
        }
        return "(Ljava/lang/String;)V".equals(methodRef.descriptor());
    }

    private static boolean isPlatformThrowable(final String owner) {
        return JdkCallSupport.isPlatformThrowable(owner);
    }

    private static boolean unsupportedJdkCall(final MethodRef methodRef) {
        if (!JdkCallSupport.isJdkCall(methodRef)) {
            return false;
        }
        if (JdkCallSupport.isSupported(methodRef)) {
            return false;
        }
        return true;
    }

    private static boolean ignoredGeneratedEnumValueOfCall(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final int reachable
    ) {
        if (reachable == 1) {
            return false;
        }
        if (!isGeneratedEnumValueOfMethod(classFile, method)) {
            return false;
        }
        if (!isJdkEnumValueOfCall(methodRef)) {
            return false;
        }
        final Optional<CodeAttribute> code = method.code();
        if (code.isEmpty()) {
            return false;
        }
        if (code.orElseThrow().exceptionTableLength() != 0) {
            return false;
        }
        return isGeneratedEnumValueOfBody(classFile, code.orElseThrow().instructions(), methodRef);
    }

    private static boolean isGeneratedEnumValueOfMethod(final ClassFile classFile, final MethodInfo method) {
        if (!classFile.isEnum()) {
            return false;
        }
        if (!method.isStatic()) {
            return false;
        }
        if (!"valueOf".equals(method.name())) {
            return false;
        }
        return ("(Ljava/lang/String;)L" + classFile.name() + ";").equals(method.descriptor());
    }

    private static boolean isJdkEnumValueOfCall(final MethodRef methodRef) {
        if (!"java/lang/Enum".equals(methodRef.owner())) {
            return false;
        }
        if (!"valueOf".equals(methodRef.name())) {
            return false;
        }
        return "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;".equals(methodRef.descriptor());
    }

    private static boolean isGeneratedEnumValueOfBody(
        final ClassFile classFile,
        final List<Instruction> instructions,
        final MethodRef methodRef
    ) {
        if (instructions.size() != 5) {
            return false;
        }
        if (!isClassLdc(instructions.get(0), classFile.name())) {
            return false;
        }
        if (instructions.get(1).opcode() != 42) {
            return false;
        }
        if (instructions.get(2).opcode() != 184 || instructions.get(2).methodRef().isEmpty()) {
            return false;
        }
        if (!sameMethodRef(instructions.get(2).methodRef().orElseThrow(), methodRef)) {
            return false;
        }
        if (instructions.get(3).opcode() != 192 || instructions.get(3).className().isEmpty()) {
            return false;
        }
        if (!classFile.name().equals(instructions.get(3).className().orElseThrow())) {
            return false;
        }
        return instructions.get(4).opcode() == 176;
    }

    private static boolean isClassLdc(final Instruction instruction, final String className) {
        if (instruction.opcode() != 18 && instruction.opcode() != 19) {
            return false;
        }
        if (instruction.className().isEmpty()) {
            return true;
        }
        return className.equals(instruction.className().orElseThrow());
    }

    private static boolean sameMethodRef(final MethodRef left, final MethodRef right) {
        if (!left.owner().equals(right.owner())) {
            return false;
        }
        if (!left.name().equals(right.name())) {
            return false;
        }
        return left.descriptor().equals(right.descriptor());
    }

    private static boolean unsupportedInstanceOfTarget(final Map<String, ClassFile> classes, final Instruction instruction) {
        if (instruction.opcode() != 193 || instruction.className().isEmpty()) {
            return false;
        }
        final String target = instruction.className().orElseThrow();
        if ("java/lang/Object".equals(target)) {
            return false;
        }
        if (isSupportedWrapperTarget(target)) {
            return false;
        }
        if (classes.containsKey(target)) {
            return false;
        }
        return !hasAssignableClass(classes, target);
    }

    private static boolean isSupportedWrapperTarget(final String target) {
        if ("java/lang/Integer".equals(target)) {
            return true;
        }
        if ("java/lang/Long".equals(target)) {
            return true;
        }
        if ("java/lang/Float".equals(target)) {
            return true;
        }
        if ("java/lang/Double".equals(target)) {
            return true;
        }
        return "java/lang/Boolean".equals(target);
    }

    private static boolean hasAssignableClass(final Map<String, ClassFile> classes, final String target) {
        for (final ClassFile classFile : classes.values()) {
            if (!classFile.isInterface() && isAssignableTo(classes, classFile.name(), target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssignableTo(final Map<String, ClassFile> classes, final String candidate, final String expected) {
        String current = candidate;
        final List<String> visitedClasses = new ArrayList<>();
        while (current != null && !current.isEmpty()) {
            if (current.equals(expected)) {
                return true;
            }
            if (containsString(visitedClasses, current)) {
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
            if (containsString(visited, interfaceName)) {
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

    private static boolean containsString(final List<String> values, final String target) {
        for (final String value : values) {
            if (value.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean unsupportedInvokedynamic(final Instruction instruction) {
        if (instruction.opcode() != 186) {
            return false;
        }
        final Optional<DynamicRef> dynamicRef = instruction.dynamicRef();
        if (dynamicRef.isEmpty()) {
            return true;
        }
        if (supportedStringConcat(dynamicRef.orElseThrow())) {
            return false;
        }
        return true;
    }

    private static boolean supportedStringConcat(final DynamicRef dynamicRef) {
        if (!"java/lang/invoke/StringConcatFactory".equals(dynamicRef.bootstrapOwner())) {
            return false;
        }
        final int returnStart = dynamicRef.descriptor().indexOf(')');
        if (returnStart < 0 || !"Ljava/lang/String;".equals(dynamicRef.descriptor().substring(returnStart + 1))) {
            return false;
        }
        if (!supportedStringConcatParameters(dynamicRef.descriptor())) {
            return false;
        }
        if ("makeConcat".equals(dynamicRef.bootstrapName())) {
            return true;
        }
        return "makeConcatWithConstants".equals(dynamicRef.bootstrapName())
            && !dynamicRef.bootstrapArguments().isEmpty()
            && dynamicRef.bootstrapArguments().getFirst().indexOf(2) < 0;
    }

    private static boolean supportedStringConcatParameters(final String descriptor) {
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final char type = descriptor.charAt(index);
            if ("BCDFIJSZ".indexOf(type) >= 0) {
                index++;
            } else if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    return false;
                }
                index = end + 1;
            } else if (type == '[') {
                index = skipArrayDescriptor(descriptor, index);
                if (index < 0) {
                    return false;
                }
            } else {
                return false;
            }
        }
        if (index >= descriptor.length()) {
            return false;
        }
        if (descriptor.charAt(index) != ')') {
            return false;
        }
        return true;
    }

    private static boolean ignoredUnreachableRecordObjectMethod(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        if (reachable == 1) {
            return false;
        }
        if (!"java/lang/Record".equals(classFile.superName())) {
            return false;
        }
        if (!recordObjectMethod(method)) {
            return false;
        }
        final Optional<DynamicRef> dynamicRef = instruction.dynamicRef();
        if (dynamicRef.isEmpty()) {
            return false;
        }
        final DynamicRef ref = dynamicRef.orElseThrow();
        if (!"java/lang/runtime/ObjectMethods".equals(ref.bootstrapOwner())) {
            return false;
        }
        return "bootstrap".equals(ref.bootstrapName());
    }

    private static boolean recordObjectMethod(final MethodInfo method) {
        if ("toString".equals(method.name()) && "()Ljava/lang/String;".equals(method.descriptor())) {
            return true;
        }
        if ("hashCode".equals(method.name()) && "()I".equals(method.descriptor())) {
            return true;
        }
        return "equals".equals(method.name()) && "(Ljava/lang/Object;)Z".equals(method.descriptor());
    }

    private static int skipArrayDescriptor(final String descriptor, final int start) {
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index >= descriptor.length()) {
            return -1;
        }
        if ("BCDFIJSZ".indexOf(descriptor.charAt(index)) >= 0) {
            return index + 1;
        }
        if (descriptor.charAt(index) == 'L') {
            final int end = descriptor.indexOf(';', index);
            if (end < 0) {
                return -1;
            }
            return end + 1;
        }
        return -1;
    }

    private static boolean supportedNewArrayType(final int atype) {
        if (atype < 4) {
            return false;
        }
        if (atype > 11) {
            return false;
        }
        return true;
    }

    private static Diagnostic apiDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final String reason,
        final int reachable
    ) {
        final String fix = "Use direct static references or a future build-time registry.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN001", "unsupported reachable API", methodRef.display(), reason, fix);
        }
        return warning(classFile, method, "JAVAN101", "unsupported API in unreachable code", methodRef.display(), reason, fix);
    }

    private static Diagnostic jdkCallDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final int reachable
    ) {
        final String reason = "This reachable JDK method has no native intrinsic, substitution, or supported runtime model yet.";
        final String fix = "Use a currently supported intrinsic or add a deterministic JDK substitution before native code generation.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN031", "unsupported reachable JDK call", methodRef.display(), reason, fix);
        }
        return warning(classFile, method, "JAVAN131", "unsupported JDK call in unreachable code", methodRef.display(), reason, fix);
    }

    private static Diagnostic networkCallDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final int reachable
    ) {
        final String modules = join("/", NetworkApiSupport.runtimeModules(methodRef));
        final String reason = "Reachable code needs `" + modules + "`, but the native network runtime is not implemented yet.";
        final String fix = "Keep this code on the JVM for now, or wait for the planned socket/http runtime slice before native code generation.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN061", "unsupported reachable network API", methodRef.display(), reason, fix);
        }
        return warning(classFile, method, "JAVAN161", "unsupported network API in unreachable code", methodRef.display(), reason, fix);
    }

    private static Diagnostic opcodeDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String reason = "The current native profile does not implement this bytecode.";
        final String fix = "Remove the construct from reachable code or wait for profile expansion.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN030", "unsupported reachable bytecode", instruction.mnemonic(), reason, fix);
        }
        return warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", instruction.mnemonic(), reason, fix);
    }

    private static Diagnostic exceptionHandlerDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final int handlers,
        final int reachable
    ) {
        final String reason = "Only direct explicit athrow ranges with platform exception catch types are supported.";
        final String fix = "Keep try/catch limited to catching a directly thrown platform exception in the same method.";
        String subject = handlers + " handler";
        if (handlers != 1) {
            subject = subject + "s";
        }
        if (reachable == 1) {
            return error(classFile, method, "JAVAN014", "exception handlers are not supported", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN114", "exception handlers in unreachable code", subject, reason, fix);
    }

    private static Diagnostic newArrayDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        int arrayType = -1;
        if (instruction.operands().length != 0) {
            arrayType = instruction.operands()[0] & 0xFF;
        }
        final String subject = "newarray " + newArrayTypeName(arrayType);
        final String reason = "Only primitive one-dimensional newarray allocation is implemented in the current native profile.";
        final String fix = "Use a supported primitive array type or wait for broader array profile expansion.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN030", "unsupported reachable bytecode", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", subject, reason, fix);
    }

    private static Diagnostic invokedynamicDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String reason = "Only StringConcatFactory makeConcat and makeConcatWithConstants without secondary constants are implemented.";
        final String fix = "Keep invokedynamic limited to javac string concatenation or wait for dynamic-call expansion.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN030", "unsupported reachable bytecode", instruction.mnemonic(), reason, fix);
        }
        return warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", instruction.mnemonic(), reason, fix);
    }

    private static Diagnostic instanceOfTargetDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String target = instruction.className().orElse("unknown");
        final String reason = "The current runtime only has deterministic type metadata for application classes and supported boxed primitive wrappers.";
        final String fix = "Keep instanceof targets to application classes/interfaces, Object, or supported wrappers until this runtime model expands.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN045", "unsupported instanceof target", target, reason, fix);
        }
        return warning(classFile, method, "JAVAN145", "unsupported instanceof target in unreachable code", target, reason, fix);
    }

    private static Diagnostic stringConstantDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String reason = "The current native runtime stores strings as UTF-8 C strings for the supported ASCII subset. Accepting this constant would make Java String length, indexing, substring, and ABI ownership semantics unsafe.";
        final String fix = "Use ASCII string constants for now, or keep this code on the JVM until Javan's full UTF-16 String object model is implemented.";
        final Optional<MethodRef> methodRef = instruction.methodRef();
        final String subject = methodRef.isPresent() ? methodRef.orElseThrow().display() : instruction.mnemonic();
        if (reachable == 1) {
            return error(
                classFile,
                method,
                "JAVAN046",
                "non-ASCII string constants require the UTF-16 string model",
                subject,
                reason,
                fix
            );
        }
        return warning(
            classFile,
            method,
            "JAVAN146",
            "non-ASCII string constant in unreachable code",
            subject,
            reason,
            fix
        );
    }

    private static Diagnostic threadLifecycleDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final String subject,
        final String reason,
        final String fix,
        final int reachable
    ) {
        if (reachable == 1) {
            return error(classFile, method, "JAVAN075", "unsupported reachable thread lifecycle", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN175", "unsupported thread lifecycle in unreachable code", subject, reason, fix);
    }

    private static Diagnostic synchronizationDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final String subject,
        final String reason,
        final String fix,
        final int reachable
    ) {
        if (reachable == 1) {
            return error(classFile, method, "JAVAN076", "unsupported reachable synchronization", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN176", "unsupported synchronization in unreachable code", subject, reason, fix);
    }

    private static Diagnostic blockingWaitDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final String subject,
        final String reason,
        final String fix
    ) {
        return warning(classFile, method, "JAVAN178", "reachable blocking wait", subject, reason, fix);
    }

    private static String newArrayTypeName(final int atype) {
        return "atype-" + atype;
    }

    private static Diagnostic error(
        final ClassFile classFile,
        final MethodInfo method,
        final String code,
        final String message,
        final String subject,
        final String reason,
        final String fix
    ) {
        return Diagnostic.error(code, message, classFile.name(), method.name() + method.descriptor(), subject, reason, fix);
    }

    private static Diagnostic warning(
        final ClassFile classFile,
        final MethodInfo method,
        final String code,
        final String message,
        final String subject,
        final String reason,
        final String fix
    ) {
        return Diagnostic.warning(code, message, classFile.name(), method.name() + method.descriptor(), subject, reason, fix);
    }

    private static String join(final String delimiter, final List<String> values) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(delimiter);
            }
            result.append(values.get(index));
        }
        return result.toString();
    }
}
