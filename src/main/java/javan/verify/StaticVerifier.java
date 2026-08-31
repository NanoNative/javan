package javan.verify;

import javan.analysis.CaughtThrowableRethrowAnalysis;
import javan.analysis.ThrowableReturnAnalysis;
import javan.analysis.EntryPoint;
import javan.analysis.GeneratedObjectCloneSupport;
import javan.analysis.VirtualThreadInvokePatterns;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.FieldRef;
import javan.classfile.FunctionLambdaUse;
import javan.classfile.Instruction;
import javan.classfile.LambdaMetafactoryCall;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.classfile.RecordObjectMethodsCall;
import javan.codegen.MethodDescriptor;
import javan.compat.BytecodeSupport;
import javan.compat.ExactMethodSupport;
import javan.compat.JdkCallSupport;
import javan.compat.JavanHostOnlyMethods;
import javan.compat.JavanNativeSubstitutions;
import javan.compat.NetworkApiSupport;
import javan.ir.IrType;
import javan.util.Strings2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        return verify(classes, reachable, List.of());
    }

    /**
     * Verifies parsed classes with exact configured native imports.
     *
     * @param classes parsed classes
     * @param reachable reachable method identities
     * @param nativeEntryPoints exact configured native method identities
     * @return diagnostics
     */
    public List<Diagnostic> verify(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final List<EntryPoint> nativeEntryPoints
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>(verifyConfiguredNativeImports(classes, nativeEntryPoints));
        final ReachableEntries reachableEntries = new ReachableEntries(reachable);
        final ReachableEntries configuredNativeEntries = new ReachableEntries(nativeEntryPoints);
        final MethodRefFactsCache methodRefFacts = new MethodRefFactsCache(classes);
        for (final ClassFile classFile : classes.values()) {
            for (final MethodInfo method : classFile.methods()) {
                final int isReachable = reachableEntries.contains(classFile.name(), method.name(), method.descriptor()) ? 1 : 0;
                diagnostics.addAll(verifyMethod(
                    classes,
                    classFile,
                    method,
                    isReachable,
                    reachableEntries,
                    methodRefFacts,
                    configuredNativeEntries
                ));
            }
        }
        return List.copyOf(diagnostics);
    }

    /**
     * Verifies only exact configured native declarations without analyzing unrelated unreachable code.
     *
     * @param classes parsed classes
     * @param nativeEntryPoints exact configured native method identities
     * @return configured-native diagnostics in declaration order
     * @throws NullPointerException when an argument or configured entry is null
     * @throws IllegalArgumentException when a configured entry no longer resolves to a native method
     */
    public List<Diagnostic> verifyConfiguredNativeImports(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> nativeEntryPoints
    ) {
        Objects.requireNonNull(classes, "classes");
        final List<EntryPoint> configured = List.copyOf(
            Objects.requireNonNull(nativeEntryPoints, "nativeEntryPoints")
        );
        final List<Diagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            final EntryPoint entryPoint = configured.get(index);
            final ClassFile classFile = classes.get(entryPoint.className());
            if (classFile == null) {
                throw invalidConfiguredNative(entryPoint);
            }
            final MethodInfo method = classFile.method(entryPoint.methodName(), entryPoint.descriptor()).orElse(null);
            if (method == null || !method.isNative()) {
                throw invalidConfiguredNative(entryPoint);
            }
            diagnostics.addAll(configuredNativeDiagnostics(classFile, method, entryPoint));
        }
        return List.copyOf(diagnostics);
    }

    private List<Diagnostic> configuredNativeDiagnostics(
        final ClassFile classFile,
        final MethodInfo method,
        final EntryPoint nativeMethod
    ) {
        if (!method.isStatic()) {
            return List.of(error(
                classFile,
                method,
                "JAVAN013",
                "native import must be static",
                nativeMethod.display(),
                "Direct external symbols cannot receive a Java object receiver.",
                "Declare the native method static."
            ));
        }
        if (!supportedNativeImportAbi(method.descriptor())) {
            return List.of(error(
                classFile,
                method,
                "JAVAN013",
                "native import ABI is not supported",
                nativeMethod.display(),
                "Native imports support void/int/long/float/double returns and int/long/float/double or non-null borrowed byte[] parameters.",
                "Use only the supported native import ABI."
            ));
        }
        return List.of();
    }

    private static IllegalArgumentException invalidConfiguredNative(final EntryPoint entryPoint) {
        return new IllegalArgumentException("Configured native method cannot be verified: " + entryPoint.display());
    }

    private static boolean supportedNativeImportAbi(final String descriptor) {
        if (descriptor.length() < 3 || descriptor.charAt(0) != '(') {
            return false;
        }
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final char parameter = descriptor.charAt(index);
            if (parameter == 'I' || parameter == 'J' || parameter == 'F' || parameter == 'D') {
                index++;
            } else if (parameter == '['
                && index + 1 < descriptor.length()
                && descriptor.charAt(index + 1) == 'B') {
                index += 2;
            } else {
                return false;
            }
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            return false;
        }
        index++;
        if (index != descriptor.length() - 1) {
            return false;
        }
        final char result = descriptor.charAt(index);
        return result == 'V' || result == 'I' || result == 'J' || result == 'F' || result == 'D';
    }

    private static final class ReachableEntries {
        private final Map<String, List<EntryPoint>> ownerBuckets = new HashMap<>();

        private ReachableEntries(final List<EntryPoint> entries) {
            for (final EntryPoint entry : entries) {
                ownerBucket(entry.className()).add(entry);
            }
        }

        private boolean contains(final String owner, final String methodName, final String descriptor) {
            final List<EntryPoint> bucket = existingOwnerBucket(owner);
            if (bucket == null) {
                return false;
            }
            for (final EntryPoint entry : bucket) {
                if (entry.methodName().equals(methodName) && entry.descriptor().equals(descriptor)) {
                    return true;
                }
            }
            return false;
        }

        private boolean containsOwner(final String owner) {
            return existingOwnerBucket(owner) != null;
        }

        private List<EntryPoint> ownerBucket(final String owner) {
            final List<EntryPoint> existing = existingOwnerBucket(owner);
            if (existing != null) {
                return existing;
            }
            final List<EntryPoint> bucket = new ArrayList<>();
            ownerBuckets.put(owner, bucket);
            return bucket;
        }

        private List<EntryPoint> existingOwnerBucket(final String owner) {
            return ownerBuckets.get(owner);
        }
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
                JdkCallSupport.isJdkCall(target),
                JdkCallSupport.isSupported(target)
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
        boolean jdkCall,
        boolean supported
    ) {
    }

    private List<Diagnostic> verifyMethod(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final int reachable,
        final ReachableEntries reachableEntries,
        final MethodRefFactsCache methodRefFacts,
        final ReachableEntries configuredNativeEntries
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (reachable == 1 && method.isNative()) {
            final EntryPoint nativeMethod = new EntryPoint(classFile.name(), method.name(), method.descriptor());
            final boolean configuredNative = configuredNativeEntries.contains(
                nativeMethod.className(), nativeMethod.methodName(), nativeMethod.descriptor()
            );
            if (!configuredNative && !method.isStatic()) {
                diagnostics.add(error(
                    classFile,
                    method,
                    "JAVAN013",
                    "native import must be static",
                    nativeMethod.display(),
                    "Direct external symbols cannot receive a Java object receiver.",
                    "Declare the native method static."
                ));
            } else if (!configuredNative) {
                diagnostics.add(error(
                    classFile,
                    method,
                    "JAVAN013",
                    "native import is not declared",
                    nativeMethod.display(),
                    "Reachable native methods require an exact configured import declaration.",
                    "Declare the exact native method in [native].imports."
                ));
            }
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
            if (ExactMethodSupport.isExactLoweredMethod(classFile, method)) {
                return diagnostics;
            }
            diagnostics.addAll(configuredNativeMethodReferenceDiagnostics(
                classFile,
                method,
                methodCode,
                reachable,
                reachableEntries,
                configuredNativeEntries
            ));
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
            if (unsupportedExceptionHandlers(classes, method, reachableEntries)
                && !supportedSyntheticSwitchMapClass(classFile, method, methodCode)
                && !supportedSyntheticSwitchTableMethod(classes, classFile, method, methodCode)) {
                diagnostics.add(exceptionHandlerDiagnostic(classFile, method, methodCode.exceptionTableLength(), reachable));
            }
            diagnostics.addAll(unsupportedThreadLifecycleDiagnostics(classFile, method, methodCode, reachable));
            diagnostics.addAll(blockingWaitDiagnostics(classFile, method, methodCode, reachable));
            final int application = classFile.application() ? 1 : 0;
            final int unsupportedStringConstant = containsUnsupportedRuntimeStringConstant(methodCode) ? 1 : 0;
            final List<Instruction> instructions = methodCode.instructions();
            diagnostics.addAll(boundedOptionalOrElseThrowDiagnostics(
                classes,
                classFile,
                method,
                instructions,
                reachable
            ));
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
                    exactVirtualThreadWrapperMethod,
                    methodRefFacts
                ));
            }
            diagnostics.addAll(provableStraightLineRiskDiagnostics(classFile, method, methodCode, instructions, reachable));
        }
        return diagnostics;
    }

    private static List<Diagnostic> configuredNativeMethodReferenceDiagnostics(
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code,
        final int reachable,
        final ReachableEntries reachableEntries,
        final ReachableEntries configuredNativeEntries
    ) {
        if (reachable == 0) {
            return List.of();
        }
        final List<Diagnostic> diagnostics = new ArrayList<>();
        for (final Instruction instruction : code.instructions()) {
            if (instruction.dynamicRef().isEmpty()) {
                continue;
            }
            final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(
                instruction.dynamicRef().orElseThrow()
            );
            if (resolved.isEmpty()) {
                continue;
            }
            final LambdaMetafactoryCall lambda = resolved.orElseThrow();
            final MethodRef implementation = lambda.implementation();
            final EntryPoint target = new EntryPoint(
                implementation.owner(),
                implementation.name(),
                implementation.descriptor()
            );
            if (!configuredNativeEntries.contains(target.className(), target.methodName(), target.descriptor())
                || !reachableEntries.contains(target.className(), target.methodName(), target.descriptor())
                || lambda.instantiatedMethodDescriptor().equals(implementation.descriptor())) {
                continue;
            }
            diagnostics.add(error(
                classFile,
                method,
                "JAVAN013",
                "configured native method reference requires exact descriptors",
                target.display(),
                "Configured native method references require identical instantiated SAM and implementation descriptors: "
                    + lambda.instantiatedMethodDescriptor() + " != " + implementation.descriptor(),
                "Use a method reference whose instantiated SAM descriptor exactly matches the configured native method."
            ));
        }
        return List.copyOf(diagnostics);
    }

    private static List<Diagnostic> provableStraightLineRiskDiagnostics(
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute methodCode,
        final List<Instruction> instructions,
        final int reachable
    ) {
        if (methodCode.exceptionTableLength() != 0) {
            return List.of();
        }
        final boolean[] nullLocals = new boolean[methodCode.maxLocals()];
        final int[] arrayLengths = new int[methodCode.maxLocals()];
        final int[] stringLengths = new int[methodCode.maxLocals()];
        clearStraightLineFacts(nullLocals, arrayLengths, stringLengths);
        final List<Diagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            if (isStraightLineFactControlFlowBoundary(instruction)) {
                clearStraightLineFacts(nullLocals, arrayLengths, stringLengths);
                continue;
            }
            final int storedLocal = astoreLocalIndex(instruction);
            if (storedLocal >= 0 && storedLocal < nullLocals.length) {
                nullLocals[storedLocal] = previousValueIsNull(instructions, index, nullLocals);
                arrayLengths[storedLocal] = arrayLengthBeforeStore(instructions, index, arrayLengths);
                stringLengths[storedLocal] = stringLengthBeforeStore(instructions, index, stringLengths);
                continue;
            }
            if (isNullReceiverOperation(instruction) && previousValueIsNull(instructions, index, nullLocals)) {
                diagnostics.add(nullReceiverDiagnostic(classFile, method, instruction, reachable));
            }
            if (isArrayLoadInstruction(instruction) && index >= 2) {
                final int arrayLocal = aloadLocalIndex(instructions.get(index - 2));
                final Optional<Integer> arrayIndex = instructions.get(index - 1).intValue();
                final int arrayLength = arrayLocal >= 0 && arrayLocal < arrayLengths.length ? arrayLengths[arrayLocal] : -1;
                if (arrayLength >= 0 && arrayIndex.isPresent()) {
                    final int literalIndex = arrayIndex.orElseThrow();
                    if (literalIndex < 0 || literalIndex >= arrayLength) {
                        diagnostics.add(arrayIndexDiagnostic(classFile, method, arrayLength, literalIndex, reachable));
                    }
                }
            }
            if (isStringCharAtInstruction(instruction) && index >= 2) {
                final int stringLength = stringLengthBeforeCall(instructions, index, 1, stringLengths);
                final Optional<Integer> stringIndex = instructions.get(index - 1).intValue();
                if (stringLength >= 0 && stringIndex.isPresent()) {
                    final int literalIndex = stringIndex.orElseThrow();
                    if (literalIndex < 0 || literalIndex >= stringLength) {
                        diagnostics.add(stringCharAtIndexDiagnostic(classFile, method, stringLength, literalIndex, reachable));
                    }
                }
            }
            final int substringArgumentCount = stringSubstringArgumentCount(instruction);
            if (substringArgumentCount > 0 && index >= substringArgumentCount + 1) {
                final int stringLength = stringLengthBeforeCall(instructions, index, substringArgumentCount, stringLengths);
                final Optional<Integer> start = instructions.get(index - substringArgumentCount).intValue();
                final Optional<Integer> end = substringArgumentCount == 2
                    ? instructions.get(index - 1).intValue()
                    : Optional.empty();
                if (stringLength >= 0 && start.isPresent() && (substringArgumentCount == 1 || end.isPresent())) {
                    final int literalStart = start.orElseThrow();
                    final int literalEnd = end.orElse(stringLength);
                    if (literalStart < 0 || literalStart > literalEnd || literalEnd > stringLength) {
                        diagnostics.add(stringSubstringIndexDiagnostic(
                            classFile,
                            method,
                            stringLength,
                            literalStart,
                            end,
                            reachable
                        ));
                    }
                }
            }
        }
        return List.copyOf(diagnostics);
    }

    private static int arrayLengthBeforeStore(
        final List<Instruction> instructions,
        final int index,
        final int[] arrayLengths
    ) {
        if (index == 0) {
            return -1;
        }
        final Instruction previous = instructions.get(index - 1);
        final int copiedLocal = aloadLocalIndex(previous);
        if (copiedLocal >= 0 && copiedLocal < arrayLengths.length) {
            return arrayLengths[copiedLocal];
        }
        if ((previous.opcode() != 188 && previous.opcode() != 189) || index < 2) {
            return -1;
        }
        return instructions.get(index - 2).intValue().orElse(-1);
    }

    private static int stringLengthBeforeStore(
        final List<Instruction> instructions,
        final int index,
        final int[] stringLengths
    ) {
        if (index == 0) {
            return -1;
        }
        final Instruction previous = instructions.get(index - 1);
        final int copiedLocal = aloadLocalIndex(previous);
        if (copiedLocal >= 0 && copiedLocal < stringLengths.length) {
            return stringLengths[copiedLocal];
        }
        return runtimeAsciiStringLength(previous);
    }

    private static boolean isArrayLoadInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        return opcode >= 46 && opcode <= 53;
    }

    private static int stringLengthBeforeCall(
        final List<Instruction> instructions,
        final int index,
        final int argumentCount,
        final int[] stringLengths
    ) {
        final Instruction receiver = instructions.get(index - argumentCount - 1);
        final int local = aloadLocalIndex(receiver);
        if (local >= 0 && local < stringLengths.length) {
            return stringLengths[local];
        }
        return runtimeAsciiStringLength(receiver);
    }

    private static boolean isStringCharAtInstruction(final Instruction instruction) {
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        return "java/lang/String".equals(methodRef.owner())
            && "charAt".equals(methodRef.name())
            && "(I)C".equals(methodRef.descriptor());
    }

    private static int stringSubstringArgumentCount(final Instruction instruction) {
        if (instruction.methodRef().isEmpty()) {
            return 0;
        }
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (!"java/lang/String".equals(methodRef.owner()) || !"substring".equals(methodRef.name())) {
            return 0;
        }
        return switch (methodRef.descriptor()) {
            case "(I)Ljava/lang/String;" -> 1;
            case "(II)Ljava/lang/String;" -> 2;
            default -> 0;
        };
    }

    private static int runtimeAsciiStringLength(final Instruction instruction) {
        if (instruction.stringValue().isEmpty()) {
            return -1;
        }
        final String literal = instruction.stringValue().orElseThrow();
        return Strings2.isRuntimeAsciiStringConstant(literal) ? literal.length() : -1;
    }

    private static boolean previousValueIsNull(
        final List<Instruction> instructions,
        final int index,
        final boolean[] nullLocals
    ) {
        if (index == 0) {
            return false;
        }
        final Instruction previous = instructions.get(index - 1);
        if (previous.opcode() == 1) {
            return true;
        }
        final int loadedLocal = aloadLocalIndex(previous);
        return loadedLocal >= 0 && loadedLocal < nullLocals.length && nullLocals[loadedLocal];
    }

    private static boolean isNullReceiverOperation(final Instruction instruction) {
        if (instruction.opcode() == 180 || instruction.opcode() == 190) {
            return true;
        }
        if (instruction.opcode() != 182 && instruction.opcode() != 183 && instruction.opcode() != 185) {
            return false;
        }
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef methodRef = instruction.methodRef().orElseThrow();
        if (defersToSpecializedStreamReceiverDiagnostic(methodRef)) {
            return false;
        }
        return MethodDescriptor.parse(methodRef.descriptor()).parameterTypes().isEmpty();
    }

    private static boolean defersToSpecializedStreamReceiverDiagnostic(final MethodRef methodRef) {
        return "java/io/InputStream".equals(methodRef.owner()) || "java/io/OutputStream".equals(methodRef.owner());
    }

    private static boolean isStraightLineFactControlFlowBoundary(final Instruction instruction) {
        final int opcode = instruction.opcode();
        return opcode >= 153 && opcode <= 177
            || opcode == 191
            || opcode >= 198 && opcode <= 201;
    }

    private static void clearStraightLineFacts(
        final boolean[] nullLocals,
        final int[] arrayLengths,
        final int[] stringLengths
    ) {
        for (int index = 0; index < nullLocals.length; index++) {
            nullLocals[index] = false;
            arrayLengths[index] = -1;
            stringLengths[index] = -1;
        }
    }

    private static Diagnostic nullReceiverDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String subject = instruction.methodRef()
            .map(MethodRef::display)
            .or(() -> instruction.fieldRef().map(FieldRef::display))
            .orElse(instruction.mnemonic());
        final String reason = "This straight-line receiver is exactly the literal null, so native execution would only reproduce a runtime NullPointerException.";
        final String fix = "Replace the null value before this receiver operation.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN070", "provable null receiver", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN170", "provable null receiver in unreachable code", subject, reason, fix);
    }

    private static Diagnostic arrayIndexDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final int arrayLength,
        final int arrayIndex,
        final int reachable
    ) {
        final String subject = "array length " + arrayLength + " at index " + arrayIndex;
        final String reason = "This straight-line array read uses a literal index outside the literal array length, so native execution would only reproduce an ArrayIndexOutOfBoundsException.";
        final String fix = "Use an index from 0 (inclusive) to the array length (exclusive), or create an array large enough for this index.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN071", "provable array index out of bounds", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN171", "provable array index out of bounds in unreachable code", subject, reason, fix);
    }

    private static Diagnostic stringCharAtIndexDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final int stringLength,
        final int stringIndex,
        final int reachable
    ) {
        final String subject = "String length " + stringLength + " at index " + stringIndex;
        final String reason = "This straight-line String.charAt call uses a literal index outside the literal ASCII string length, so native execution would only reproduce a StringIndexOutOfBoundsException.";
        final String fix = "Use an index from 0 (inclusive) to String.length() (exclusive), or use a string long enough for this index.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN072", "provable String.charAt index out of bounds", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN172", "provable String.charAt index out of bounds in unreachable code", subject, reason, fix);
    }

    private static Diagnostic stringSubstringIndexDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final int stringLength,
        final int start,
        final Optional<Integer> end,
        final int reachable
    ) {
        final String subject = end.isPresent()
            ? "String length " + stringLength + " at start " + start + " and end " + end.orElseThrow()
            : "String length " + stringLength + " at start " + start;
        final String reason = "This straight-line String.substring call uses literal bounds outside the literal ASCII string length, so native execution would only reproduce a StringIndexOutOfBoundsException.";
        final String fix = "Use a start from 0 to String.length() (inclusive), and an end from start to String.length() (inclusive).";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN073", "provable String.substring index out of bounds", subject, reason, fix);
        }
        return warning(classFile, method, "JAVAN173", "provable String.substring index out of bounds in unreachable code", subject, reason, fix);
    }

    private static List<Diagnostic> boundedOptionalOrElseThrowDiagnostics(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final List<Instruction> instructions,
        final int reachable
    ) {
        for (int index = 0; index < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            if (instruction.methodRef().isPresent()
                && JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(
                    instruction.methodRef().orElseThrow()
                )
                && !isImmediateOptionalOrElseThrowSupplier(classes, instructions, index)) {
                return List.of(boundedOptionalOrElseThrowDiagnostic(
                    classFile,
                    method,
                    instruction,
                    reachable
                ));
            }
        }
        return List.of();
    }

    private static boolean isImmediateOptionalOrElseThrowSupplier(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int terminalIndex
    ) {
        final Instruction terminal = instructions.get(terminalIndex);
        if (terminal.opcode() != 182
            || !"invokevirtual".equals(terminal.mnemonic())
            || terminal.methodRef().isEmpty()
            || !JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(
                terminal.methodRef().orElseThrow()
            )) {
            return false;
        }
        final int dynamicIndex = terminalIndex - 1;
        if (dynamicIndex < 0) {
            return false;
        }
        final Instruction dynamicInstruction = instructions.get(dynamicIndex);
        if (dynamicInstruction.opcode() != 186
            || !"invokedynamic".equals(dynamicInstruction.mnemonic())
            || dynamicInstruction.dynamicRef().isEmpty()) {
            return false;
        }
        final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(
            dynamicInstruction.dynamicRef().orElseThrow()
        );
        if (resolved.isEmpty()) {
            return false;
        }
        final LambdaMetafactoryCall lambda = resolved.orElseThrow();
        final Optional<String> suppliedThrowableType =
            exactPlatformThrowableSupplierType(lambda);
        if (!lambda.isSupplier()
            || lambda.implementationReferenceKind() != 6
            || suppliedThrowableType.isEmpty()) {
            return false;
        }
        final int firstCaptureIndex = dynamicIndex - lambda.capturedParameterDescriptors().size();
        final int receiverIndex = firstCaptureIndex - 1;
        if (receiverIndex < 0
            || !isImmediateOptionalReceiver(classes, instructions.get(receiverIndex))
            || hasOptionalSupplierControlFlowEntry(instructions, receiverIndex, terminalIndex)) {
            return false;
        }
        for (int index = firstCaptureIndex; index < dynamicIndex; index++) {
            if (aloadLocalIndex(instructions.get(index)) < 0) {
                return false;
            }
        }
        final ClassFile implementationOwner = classes.get(lambda.implementation().owner());
        if (implementationOwner == null || !implementationOwner.application()) {
            return false;
        }
        final Optional<MethodInfo> implementation = implementationOwner.method(
            lambda.implementation().name(),
            lambda.implementation().descriptor()
        );
        return implementation.isPresent()
            && implementation.orElseThrow().isStatic()
            && implementation.orElseThrow().code().isPresent()
            && hasExactPlatformThrowableReturns(
                classes,
                implementationOwner,
                implementation.orElseThrow(),
                suppliedThrowableType.orElseThrow(),
                Set.of()
            );
    }

    private static Optional<String> exactPlatformThrowableSupplierType(
        final LambdaMetafactoryCall lambda
    ) {
        final String instantiated = lambda.instantiatedMethodDescriptor();
        if (!instantiated.startsWith("()L") || !instantiated.endsWith(";")) {
            return Optional.empty();
        }
        final String returnDescriptor = instantiated.substring(2);
        final String throwableType = returnDescriptor.substring(1, returnDescriptor.length() - 1);
        if (!JdkCallSupport.isPlatformThrowable(throwableType)) {
            return Optional.empty();
        }
        final StringBuilder expected = new StringBuilder("(");
        for (final String capture : lambda.capturedParameterDescriptors()) {
            if (!isExactReferenceOrArrayDescriptor(capture)) {
                return Optional.empty();
            }
            expected.append(capture);
        }
        expected.append(')').append(returnDescriptor);
        if (!expected.toString().equals(lambda.implementation().descriptor())) {
            return Optional.empty();
        }
        return Optional.of(throwableType);
    }

    private static boolean hasExactPlatformThrowableReturns(
        final Map<String, ClassFile> classes,
        final ClassFile owner,
        final MethodInfo method,
        final String expectedType,
        final Set<MethodRef> visiting
    ) {
        final MethodRef current = new MethodRef(owner.name(), method.name(), method.descriptor());
        if (visiting.contains(current) || method.code().isEmpty()) {
            return false;
        }
        final Set<MethodRef> path = new HashSet<>(visiting);
        path.add(current);
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        boolean returnsValue = false;
        for (final Instruction instruction : instructions) {
            if (instruction.opcode() == 192) {
                return false;
            }
            if (instruction.opcode() == 176) {
                returnsValue = true;
            }
        }
        if (returnsValue) {
            for (final Instruction instruction : instructions) {
                final int opcode = instruction.opcode();
                if (opcode >= 153 && opcode <= 171 || opcode >= 198 && opcode <= 201) {
                    return false;
                }
            }
        }
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).opcode() != 176) {
                continue;
            }
            if (index == 0
                || !isExactPlatformThrowableReturn(
                    classes,
                    instructions.get(index - 1),
                    expectedType,
                    path
                )) {
                return false;
            }
        }
        if (!returnsValue) {
            for (final Instruction instruction : instructions) {
                if (!isExactStaticThrowableHelperCall(instruction, expectedType)
                    || hasExactPlatformThrowableHelper(
                        classes,
                        instruction.methodRef().orElseThrow(),
                        expectedType,
                        path
                    )) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private static boolean isExactPlatformThrowableReturn(
        final Map<String, ClassFile> classes,
        final Instruction producer,
        final String expectedType,
        final Set<MethodRef> visiting
    ) {
        if (producer.opcode() == 1 && "aconst_null".equals(producer.mnemonic())) {
            return true;
        }
        if (producer.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = producer.methodRef().orElseThrow();
        if (producer.opcode() == 183
            && "invokespecial".equals(producer.mnemonic())
            && "<init>".equals(target.name())) {
            return expectedType.equals(target.owner());
        }
        if (producer.opcode() != 184
            || !"invokestatic".equals(producer.mnemonic())
            || !target.descriptor().endsWith(")L" + expectedType + ";")) {
            return false;
        }
        final ClassFile targetOwner = classes.get(target.owner());
        if (targetOwner == null || !targetOwner.application()) {
            return false;
        }
        final Optional<MethodInfo> targetMethod = targetOwner.method(
            target.name(),
            target.descriptor()
        );
        return targetMethod.isPresent()
            && targetMethod.orElseThrow().isStatic()
            && hasExactPlatformThrowableReturns(
                classes,
                targetOwner,
                targetMethod.orElseThrow(),
                expectedType,
                visiting
            );
    }

    private static boolean isExactStaticThrowableHelperCall(
        final Instruction instruction,
        final String expectedType
    ) {
        return instruction.opcode() == 184
            && "invokestatic".equals(instruction.mnemonic())
            && instruction.methodRef().isPresent()
            && instruction.methodRef().orElseThrow().descriptor()
                .endsWith(")L" + expectedType + ";");
    }

    private static boolean hasExactPlatformThrowableHelper(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final String expectedType,
        final Set<MethodRef> visiting
    ) {
        final ClassFile targetOwner = classes.get(target.owner());
        if (targetOwner == null || !targetOwner.application()) {
            return false;
        }
        final Optional<MethodInfo> targetMethod = targetOwner.method(
            target.name(),
            target.descriptor()
        );
        return targetMethod.isPresent()
            && targetMethod.orElseThrow().isStatic()
            && hasExactPlatformThrowableReturns(
                classes,
                targetOwner,
                targetMethod.orElseThrow(),
                expectedType,
                visiting
            );
    }

    private static boolean isExactReferenceOrArrayDescriptor(final String descriptor) {
        int component = 0;
        while (component < descriptor.length() && descriptor.charAt(component) == '[') {
            component++;
        }
        if (component >= descriptor.length()) {
            return false;
        }
        if (descriptor.charAt(component) == 'L') {
            final int end = descriptor.indexOf(';', component + 1);
            return end > component + 1 && end == descriptor.length() - 1;
        }
        return component > 0
            && component == descriptor.length() - 1
            && "BCDFIJSZ".indexOf(descriptor.charAt(component)) >= 0;
    }

    private static boolean isImmediateOptionalReceiver(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        if (aloadLocalIndex(instruction) >= 0) {
            return true;
        }
        if (instruction.fieldRef().isPresent()) {
            return isExactOptionalFieldRead(classes, instruction);
        }
        if (instruction.methodRef().isEmpty()
            || !instruction.methodRef().orElseThrow().descriptor()
                .endsWith(")Ljava/util/Optional;")) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        if ("java/util/Optional".equals(target.owner())) {
            if ("empty".equals(target.name())
                || "of".equals(target.name())
                || "ofNullable".equals(target.name())) {
                return instruction.opcode() == 184
                    && "invokestatic".equals(instruction.mnemonic());
            }
            return instruction.opcode() == 182
                && "invokevirtual".equals(instruction.mnemonic());
        }
        final ClassFile owner = classes.get(target.owner());
        if (owner == null) {
            return false;
        }
        final Optional<MethodInfo> resolved = owner.method(target.name(), target.descriptor());
        if (resolved.isEmpty()) {
            return false;
        }
        if (resolved.orElseThrow().isStatic()) {
            return instruction.opcode() == 184
                && "invokestatic".equals(instruction.mnemonic());
        }
        if (owner.isInterface()) {
            return instruction.opcode() == 185
                && "invokeinterface".equals(instruction.mnemonic());
        }
        return instruction.opcode() == 182
            && "invokevirtual".equals(instruction.mnemonic());
    }

    private static boolean isExactOptionalFieldRead(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        final FieldRef field = instruction.fieldRef().orElseThrow();
        if (!"Ljava/util/Optional;".equals(field.descriptor())) {
            return false;
        }
        final ClassFile owner = classes.get(field.owner());
        if (owner == null) {
            return false;
        }
        for (final javan.classfile.FieldInfo candidate : owner.fields()) {
            if (!candidate.name().equals(field.name())
                || !candidate.descriptor().equals(field.descriptor())) {
                continue;
            }
            if (candidate.isStatic()) {
                return instruction.opcode() == 178
                    && "getstatic".equals(instruction.mnemonic());
            }
            return instruction.opcode() == 180
                && "getfield".equals(instruction.mnemonic());
        }
        return false;
    }

    private static boolean hasOptionalSupplierControlFlowEntry(
        final List<Instruction> instructions,
        final int startIndex,
        final int terminalIndex
    ) {
        final int startOffset = instructions.get(startIndex).offset();
        final int endOffset = nextInstructionOffset(instructions.get(terminalIndex));
        for (int index = 0; index < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            final int opcode = instruction.opcode();
            final boolean inExpression = index >= startIndex && index <= terminalIndex;
            if (inExpression
                && (opcode == 168 || opcode == 169 || opcode == 170 || opcode == 171
                    || opcode == 200 || opcode == 201)) {
                return true;
            }
            final int target = boundedBranchTarget(instruction);
            if (target >= startOffset && target < endOffset) {
                return true;
            }
            if (inExpression && target >= 0) {
                return true;
            }
            final int wideTarget = wideBranchTarget(instruction);
            if (wideTarget >= startOffset && wideTarget < endOffset) {
                return true;
            }
            if (switchTargetsRange(instruction, startOffset, endOffset)) {
                return true;
            }
        }
        return false;
    }

    private static int wideBranchTarget(final Instruction instruction) {
        if ((instruction.opcode() != 200 && instruction.opcode() != 201)
            || instruction.operands().length != 4) {
            return -1;
        }
        return instruction.offset() + int32(instruction.operands(), 0);
    }

    private static boolean switchTargetsRange(
        final Instruction instruction,
        final int startOffset,
        final int endOffset
    ) {
        if (instruction.opcode() != 170 && instruction.opcode() != 171) {
            return false;
        }
        final byte[] operands = instruction.operands();
        final int padding = switchPadding(instruction.offset());
        if (padding + 8 > operands.length) {
            return true;
        }
        if (relativeTargetInRange(
            instruction,
            int32(operands, padding),
            startOffset,
            endOffset
        )) {
            return true;
        }
        if (instruction.opcode() == 170) {
            if (padding + 12 > operands.length) {
                return true;
            }
            final int low = int32(operands, padding + 4);
            final int high = int32(operands, padding + 8);
            final long entries = (long) high - low + 1L;
            if (entries < 0L || entries > (operands.length - padding - 12) / 4L) {
                return true;
            }
            int offset = padding + 12;
            for (long index = 0; index < entries; index++) {
                if (relativeTargetInRange(
                    instruction,
                    int32(operands, offset),
                    startOffset,
                    endOffset
                )) {
                    return true;
                }
                offset += 4;
            }
            return false;
        }
        final int pairs = int32(operands, padding + 4);
        if (pairs < 0 || pairs > (operands.length - padding - 8) / 8) {
            return true;
        }
        int offset = padding + 8;
        for (int index = 0; index < pairs; index++) {
            if (relativeTargetInRange(
                instruction,
                int32(operands, offset + 4),
                startOffset,
                endOffset
            )) {
                return true;
            }
            offset += 8;
        }
        return false;
    }

    private static boolean relativeTargetInRange(
        final Instruction instruction,
        final int relativeTarget,
        final int startOffset,
        final int endOffset
    ) {
        final int target = instruction.offset() + relativeTarget;
        return target >= startOffset && target < endOffset;
    }

    private static int switchPadding(final int offset) {
        int cursor = offset + 1;
        while (cursor % 4 != 0) {
            cursor++;
        }
        return cursor - offset - 1;
    }

    private static int int32(final byte[] operands, final int offset) {
        return ((operands[offset] & 0xFF) << 24)
            | ((operands[offset + 1] & 0xFF) << 16)
            | ((operands[offset + 2] & 0xFF) << 8)
            | (operands[offset + 3] & 0xFF);
    }

    private static Diagnostic boundedOptionalOrElseThrowDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final MethodRef subject = instruction.methodRef().orElseThrow();
        final String reason = "The native Optional subset requires an immediate Optional receiver, contiguous reference captures, a direct LambdaMetafactory Supplier backed by application-owned static code, and a platform Throwable result.";
        final String fix = "Keep Optional.orElseThrow(Supplier) as one direct expression with a static application exception supplier.";
        if (reachable == 1) {
            return error(
                classFile,
                method,
                "JAVAN031",
                "unsupported reachable JDK call",
                subject.display(),
                reason,
                fix
            );
        }
        return warning(
            classFile,
            method,
            "JAVAN131",
            "unsupported JDK call in unreachable code",
            subject.display(),
            reason,
            fix
        );
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
            final Optional<String> threadSleepWait = threadSleepWaitSubject(instruction);
            if (threadSleepWait.isPresent()) {
                diagnostics.add(blockingWaitDiagnostic(
                    classFile,
                    method,
                    threadSleepWait.orElseThrow(),
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
            final Optional<String> threadJoinWait = threadJoinWaitSubject(instruction);
            if (threadJoinWait.isPresent()
                && !blockingJoinCoveredByLifecycleGuard(instructions, index)) {
                diagnostics.add(blockingWaitDiagnostic(
                    classFile,
                    method,
                    threadJoinWait.orElseThrow(),
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

    private static boolean supportedSyntheticSwitchTableMethod(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final CodeAttribute code
    ) {
        if (!method.isStatic() || !method.isSynthetic()) {
            return false;
        }
        if (!method.name().startsWith("$SWITCH_TABLE$") || !"()[I".equals(method.descriptor())) {
            return false;
        }
        if (code.exceptionTableLength() == 0 || !hasMatchingSwitchTableField(classFile, method.name())) {
            return false;
        }
        for (final CodeException handler : code.exceptionTable()) {
            if (!supportedEnumSwitchMapHandler(classes, code, handler)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasMatchingSwitchTableField(final ClassFile classFile, final String name) {
        for (final javan.classfile.FieldInfo field : classFile.fields()) {
            if (field.isStatic() && field.name().equals(name) && "[I".equals(field.descriptor())) {
                return true;
            }
        }
        return false;
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
        final int exactVirtualThreadWrapperMethod,
        final MethodRefFactsCache methodRefFacts
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (reachable == 0 && application == 0) {
            return diagnostics;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isPresent()) {
            final MethodRefFacts facts = methodRefFacts.resolve(methodRef.orElseThrow());
            final MethodRef target = facts.target();
            final GeneratedObjectCloneSupport.Status objectCloneStatus =
                GeneratedObjectCloneSupport.isObjectClone(target)
                    ? GeneratedObjectCloneSupport.invocationStatus(classes, classFile)
                    : GeneratedObjectCloneSupport.Status.SUPPORTED;
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
            final boolean unsupportedCustomThrowableCauseConstructor =
                unsupportedCustomThrowableCauseConstructor(classFile, method, target);
            final boolean supportedCaughtThrowableCauseConstructor =
                supportedCaughtThrowableCauseConstructor(method, instructions, instructionIndex, target);
            if (forbiddenReason.isPresent()) {
                diagnostics.add(apiDiagnostic(classFile, method, target, forbiddenReason.orElseThrow(), reachable));
            }
            if (unsupportedMonitorMethod == 1) {
                diagnostics.add(monitorMethodDiagnostic(classFile, method, target, reachable));
            }
            if (unsupportedConcurrencyApi == 1) {
                diagnostics.add(concurrencyRuntimeDiagnostic(classFile, method, target, reachable));
            }
            if (objectCloneStatus != GeneratedObjectCloneSupport.Status.SUPPORTED) {
                diagnostics.add(objectCloneDiagnostic(classFile, method, target, objectCloneStatus, reachable));
            }
            if (NetworkApiSupport.isNetworkCall(target) && !facts.supported()) {
                diagnostics.add(networkCallDiagnostic(classFile, method, target, reachable));
            } else if (unsupportedMonitorMethod == 0
                && unsupportedConcurrencyApi == 0
                && facts.jdkCall()
                && (!facts.supported() || unsupportedCustomThrowableCauseConstructor)
                && !supportedCaughtThrowableCauseConstructor
                && !ignoredGeneratedEnumValueOfCall(classFile, method, target, reachable)) {
                diagnostics.add(jdkCallDiagnostic(classFile, method, target, reachable));
            }
        }
        if (unsupportedNewArrayType(instruction)) {
            diagnostics.add(newArrayDiagnostic(classFile, method, instruction, reachable));
        }
        final Optional<String> unsupportedRecordComponent =
            unsupportedRecordComponentDescriptor(classes, classFile, method, instruction);
        if (reachable == 1 && unsupportedRecordComponent.isPresent()) {
            diagnostics.add(recordComponentDiagnostic(
                classFile,
                method,
                unsupportedRecordComponent.orElseThrow()
            ));
        } else if (unsupportedInvokedynamic(classes, classFile, method, instruction)
            && !ignoredUnreachableRecordObjectMethod(classFile, method, instruction, reachable)) {
            diagnostics.add(invokedynamicDiagnostic(classFile, method, instruction, reachable));
        }
        if (unsupportedStringConstant == 1 && unsupportedRuntimeStringSemanticCall(instruction)) {
            diagnostics.add(stringConstantDiagnostic(classFile, method, instruction, reachable));
        }
        if (unsupportedCheckcastTarget(instruction)) {
            diagnostics.add(checkcastTargetDiagnostic(classFile, method, instruction, reachable));
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

    private static Diagnostic objectCloneDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef target,
        final GeneratedObjectCloneSupport.Status status,
        final int reachable
    ) {
        if (reachable == 1) {
            return error(
                classFile,
                method,
                "JAVAN050",
                "Object.clone requires a supported Cloneable class",
                target.display(),
                GeneratedObjectCloneSupport.reason(status),
                GeneratedObjectCloneSupport.fix(status)
            );
        }
        return warning(
            classFile,
            method,
            "JAVAN150",
            "unsupported Object.clone in unreachable code",
            target.display(),
            GeneratedObjectCloneSupport.reason(status),
            GeneratedObjectCloneSupport.fix(status)
        );
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
        if ("toLowerCase".equals(target.name())) {
            return "(Ljava/util/Locale;)Ljava/lang/String;".equals(target.descriptor());
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

    private static boolean unsupportedExceptionHandlers(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final ReachableEntries reachableEntries
    ) {
        final CodeAttribute code = method.code().orElseThrow();
        if (code.exceptionTableLength() == 0) {
            return false;
        }
        if (normalBranchEntersHandlerBody(code)) {
            return true;
        }
        if (boundedTypedHandlerSetCandidate(code)
            && hasUnsupportedIndividualHandler(classes, method, code, reachableEntries)) {
            return !supportedBoundedTypedHandlerSet(classes, method, code);
        }
        for (final CodeException handler : code.exceptionTable()) {
            if (!supportedExceptionHandler(classes, method, code, handler, reachableEntries)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnsupportedIndividualHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final ReachableEntries reachableEntries
    ) {
        for (final CodeException handler : code.exceptionTable()) {
            if (!supportedExceptionHandler(classes, method, code, handler, reachableEntries)) {
                return true;
            }
        }
        return false;
    }

    private static boolean boundedTypedHandlerSetCandidate(final CodeAttribute code) {
        final List<CodeException> handlers = code.exceptionTable();
        if (handlers.size() < 2 || code.exceptionTableLength() != handlers.size()) {
            return false;
        }
        int previousEnd = -1;
        for (final CodeException handler : handlers) {
            if (handler.catchType().isEmpty()
                || !JdkCallSupport.isPlatformThrowableAssignable(
                    handler.catchType().orElseThrow(),
                    "java/lang/RuntimeException"
                )
                || handler.startPc() >= handler.endPc()
                || handler.startPc() < previousEnd) {
                return false;
            }
            previousEnd = handler.endPc();
        }
        return true;
    }

    private static boolean supportedBoundedTypedHandlerSet(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code
    ) {
        final List<CodeException> handlers = code.exceptionTable();
        if (handlers.size() < 2 || code.exceptionTableLength() != handlers.size()
            || code.maxStack() > 64 || code.maxLocals() > 256
            || !boundedHandlerEntriesAreIsolated(code, handlers)) {
            return false;
        }
        int previousEnd = -1;
        for (final CodeException handler : handlers) {
            if (!supportedBoundedTypedHandlerShape(code, handlers, handler, previousEnd)
                || !boundedProtectedRangeEntryIsIsolated(code, handler)
                || !supportedBoundedProtectedRange(classes, method, code, handler)
                || !boundedRangeTransportsToHandler(classes, method, code, handler)) {
                return false;
            }
            previousEnd = handler.endPc();
        }
        return true;
    }

    private static boolean supportedBoundedTypedHandlerShape(
        final CodeAttribute code,
        final List<CodeException> handlers,
        final CodeException handler,
        final int previousEnd
    ) {
        final Optional<Instruction> handlerInstruction = instructionAtOffset(code, handler.handlerPc());
        if (handler.catchType().isEmpty()
            || !JdkCallSupport.isPlatformThrowableAssignable(
                handler.catchType().orElseThrow(),
                "java/lang/RuntimeException"
            )
            || handler.startPc() >= handler.endPc()
            || handler.startPc() < previousEnd
            || instructionAtOffset(code, handler.startPc()).isEmpty()
            || !boundedEndBoundary(code, handler.endPc())
            || handlerInstruction.isEmpty()
            || astoreLocalIndex(handlerInstruction.orElseThrow()) < 0) {
            return false;
        }
        for (final CodeException candidate : handlers) {
            if (handler.handlerPc() >= candidate.startPc() && handler.handlerPc() < candidate.endPc()) {
                return false;
            }
        }
        return true;
    }

    private static boolean boundedEndBoundary(final CodeAttribute code, final int offset) {
        return offset == code.bytecode().length || instructionAtOffset(code, offset).isPresent();
    }

    private static boolean boundedHandlerEntriesAreIsolated(
        final CodeAttribute code,
        final List<CodeException> handlers
    ) {
        final List<Instruction> instructions = code.instructions();
        for (final Instruction instruction : instructions) {
            if (instruction.opcode() == 170 || instruction.opcode() == 171) {
                return false;
            }
        }
        for (final CodeException handler : handlers) {
            final int handlerIndex = instructionIndex(instructions, handler.handlerPc());
            if (handlerIndex < 0) {
                return false;
            }
            final int handlerEnd = boundedHandlerBodyEndOffset(code, handler, handlerIndex);
            if (handlerEnd < 0) {
                return false;
            }
            for (final Instruction instruction : instructions) {
                final int target = boundedBranchTarget(instruction);
                if ((instruction.offset() < handler.handlerPc() || instruction.offset() >= handlerEnd)
                    && target >= handler.handlerPc() && target < handlerEnd) {
                    return false;
                }
            }
            if (handlerIndex == 0) {
                continue;
            }
            final Instruction predecessor = instructions.get(handlerIndex - 1);
            if (nextInstructionOffset(predecessor) == handler.handlerPc()
                && !boundedControlTransferEndsBlock(predecessor.opcode())) {
                return false;
            }
        }
        return true;
    }

    private static boolean normalBranchEntersHandlerBody(final CodeAttribute code) {
        final List<Instruction> instructions = code.instructions();
        for (final CodeException handler : code.exceptionTable()) {
            final int handlerIndex = instructionIndex(instructions, handler.handlerPc());
            if (handlerIndex < 0) {
                continue;
            }
            final int handlerEnd = boundedHandlerBodyEndOffset(
                code,
                handler,
                handlerIndex
            );
            if (handlerEnd < 0) {
                continue;
            }
            for (final Instruction instruction : instructions) {
                final int target = boundedBranchTarget(instruction);
                if ((instruction.offset() < handler.handlerPc()
                    || instruction.offset() >= handlerEnd)
                    && target >= handler.handlerPc()
                    && target < handlerEnd) {
                    final Optional<CaughtThrowableRethrowAnalysis.FinallyFlow> finallyFlow =
                        CaughtThrowableRethrowAnalysis.analyzeFinally(code, handler);
                    if (finallyFlow.isPresent()
                        && finallyFlow.orElseThrow().handlerOffsets().contains(Integer.valueOf(instruction.offset()))) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static int boundedHandlerBodyEndOffset(
        final CodeAttribute code,
        final CodeException handler,
        final int handlerIndex
    ) {
        final int terminal = boundedHandlerTerminalOffset(
            code.instructions(),
            handlerIndex
        );
        if (terminal < 0) {
            return -1;
        }
        final Optional<Instruction> normalExit = instructionAtOffset(
            code,
            handler.endPc()
        );
        if (normalExit.isPresent()) {
            final int target = boundedBranchTarget(normalExit.orElseThrow());
            final int firstBodyOffset = nextInstructionOffset(
                code.instructions().get(handlerIndex)
            );
            if (target > firstBodyOffset && target <= terminal) {
                return target;
            }
        }
        return terminal;
    }

    private static int boundedHandlerTerminalOffset(
        final List<Instruction> instructions,
        final int handlerIndex
    ) {
        for (int index = handlerIndex; index < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            if (boundedControlTransferEndsBlock(instruction.opcode())) {
                return instruction.offset();
            }
        }
        return -1;
    }

    private static boolean boundedProtectedRangeEntryIsIsolated(
        final CodeAttribute code,
        final CodeException handler
    ) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() >= handler.startPc() && instruction.offset() < handler.endPc()) {
                continue;
            }
            final int target = boundedBranchTarget(instruction);
            if (target > handler.startPc() && target < handler.endPc()) {
                return false;
            }
        }
        return true;
    }

    private static boolean supportedBoundedProtectedRange(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler
    ) {
        int protectedInstructions = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            protectedInstructions++;
            if (protectedInstructions > 64
                || !supportedBoundedProtectedInstruction(classes, method, code, handler, instruction)) {
                return false;
            }
        }
        return protectedInstructions > 0;
    }

    private static boolean supportedBoundedProtectedInstruction(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler,
        final Instruction instruction
    ) {
        final int opcode = instruction.opcode();
        final int branchTarget = boundedBranchTarget(instruction);
        if (branchTarget >= 0) {
            return branchTarget > instruction.offset()
                && branchTarget >= handler.startPc()
                && branchTarget <= handler.endPc()
                && boundedEndBoundary(code, branchTarget);
        }
        if (opcode == 168 || opcode == 169 || opcode == 170 || opcode == 171
            || opcode >= 194 && opcode <= 197 || opcode == 200 || opcode == 201) {
            return false;
        }
        if (instruction.methodRef().isPresent()) {
            return supportedBoundedProtectedCall(classes, instruction);
        }
        if (opcode == 186) {
            final Optional<DynamicRef> dynamic = instruction.dynamicRef();
            return dynamic.isPresent()
                && (supportedStringConcat(dynamic.orElseThrow())
                    || supportedLambdaMetafactory(classes, method, instruction, dynamic.orElseThrow()));
        }
        if (opcode == 187 || opcode == 192 || opcode == 193) {
            final Optional<String> className = instruction.className();
            return className.isPresent() && classes.containsKey(className.orElseThrow());
        }
        if (opcode == 191) {
            return false;
        }
        return boundedNonThrowingOpcode(opcode);
    }

    private static boolean supportedBoundedProtectedCall(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        final MethodRef target = instruction.methodRef().orElseThrow();
        if (JdkCallSupport.isJdkCall(target)) {
            return JdkCallSupport.isSupported(target);
        }
        final ClassFile owner = classes.get(target.owner());
        if (owner == null) {
            return supportedLambdaThrowableCall(classes, target);
        }
        if (owner.isInterface()) {
            return false;
        }
        final Optional<MethodInfo> candidate = owner.method(target.name(), target.descriptor());
        return candidate.isPresent() && candidate.orElseThrow().code().isPresent();
    }

    private static boolean boundedRangeTransportsToHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler
    ) {
        final String catchType = handler.catchType().orElseThrow();
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (boundedCallTransportsTo(classes, instruction, catchType)
                || boundedDynamicTransportsTo(classes, method, instruction, catchType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean boundedCallTransportsTo(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final String catchType
    ) {
        return supportedTransportedThrowableCall(classes, instruction, catchType);
    }

    private static boolean boundedDynamicTransportsTo(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final String catchType
    ) {
        if (instruction.opcode() != 186 || instruction.dynamicRef().isEmpty()
            || !supportedLambdaMetafactory(classes, method, instruction, instruction.dynamicRef().orElseThrow())) {
            return false;
        }
        final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(
            instruction.dynamicRef().orElseThrow()
        );
        if (resolved.isEmpty()) {
            return false;
        }
        for (final String throwableType : escapingPlatformExceptionTypes(
            classes,
            resolved.orElseThrow().implementation(),
            new HashSet<>()
        )) {
            if (isThrowableAssignable(classes, throwableType, catchType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean boundedNonThrowingOpcode(final int opcode) {
        if (opcode >= 0 && opcode <= 45 || opcode >= 54 && opcode <= 78
            || opcode >= 87 && opcode <= 107 || opcode >= 116 && opcode <= 152
            || opcode == 198 || opcode == 199) {
            return BytecodeSupport.classify(opcode) == BytecodeSupport.Status.NATIVE_SUPPORTED;
        }
        return false;
    }

    private static boolean boundedControlTransferEndsBlock(final int opcode) {
        return opcode == 167 || opcode >= 172 && opcode <= 177 || opcode == 191;
    }

    private static int boundedBranchTarget(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (!(opcode >= 153 && opcode <= 167 || opcode == 198 || opcode == 199)
            || instruction.operands().length != 2) {
            return -1;
        }
        final int encoded = ((instruction.operands()[0] & 0xFF) << 8)
            | instruction.operands()[1] & 0xFF;
        return instruction.offset() + (short) encoded;
    }

    private static int instructionIndex(final List<Instruction> instructions, final int offset) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index).offset() == offset) {
                return index;
            }
        }
        return -1;
    }

    private static boolean supportedExceptionHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler,
        final ReachableEntries reachableEntries
    ) {
        if (supportedEnumSwitchMapHandler(classes, code, handler)) {
            return true;
        }
        if (supportedSynchronizedMonitorHandler(code, handler)) {
            return true;
        }
        if (supportedInterruptedWaitHandler(code, handler)) {
            return true;
        }
        if (supportedArraysFillHandler(code, handler)) {
            return true;
        }
        if (supportedStringLocaleCaseHandler(code, handler)) {
            return true;
        }
        if (supportedMathExactHandler(code, handler)) {
            return true;
        }
        if (supportedIntegralArithmeticHandler(code, handler)) {
            return true;
        }
        if (supportedNegativeArraySizeHandler(code, handler)) {
            return true;
        }
        if (supportedArrayAccessHandler(code, handler)) {
            return true;
        }
        if (supportedSingleApplicationThrowableTransportHandler(classes, code, handler)) {
            return true;
        }
        if (supportedFinallyHandler(classes, method, code, handler)) {
            return true;
        }
        if (supportsImmediateOptionalOrElseThrowSupplierHandler(
            classes,
            method,
            code,
            handler
        )) {
            return true;
        }
        if (handler.catchType().isEmpty()) {
            return false;
        }
        if (!isThrowable(classes, handler.catchType().orElseThrow())) {
            return false;
        }
        if (supportsEntryAnchoredStraightLineTypedHandler(
            classes,
            method,
            handler,
            reachableEntries
        )) {
            return true;
        }
        final String catchType = handler.catchType().orElseThrow();
        int hasThrowableTransport = 0;
        final List<Instruction> instructions = code.instructions();
        final boolean reflectiveInvocationRange = containsMethodInvocation(instructions, handler);
        for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++) {
            final Instruction instruction = instructions.get(instructionIndex);
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (instruction.opcode() == 191) {
                hasThrowableTransport = 1;
            }
            if (supportedCheckcastThrowable(instruction, catchType)) {
                hasThrowableTransport = 1;
            }
            if (supportedGeneratedThrowableCall(classes, instruction)) {
                hasThrowableTransport = 1;
            }
            if (supportedTransportedThrowableCall(classes, instruction, catchType)) {
                hasThrowableTransport = 1;
            }
            if (!supportedInterruptedWaitProtectedInstruction(instruction)
                && !supportedApplicationThrowableInstruction(classes, instruction)
                && !supportedCheckcastThrowable(instruction, catchType)
                && !supportedGeneratedThrowableCall(classes, instruction)
                && !supportedTransportedThrowableCall(classes, instruction, catchType)
                && !supportedThrowableFieldPreparation(classes, instruction)
                && !supportedOptionalFactoryPreparation(instruction)
                && !supportedThrowableWrapRangeInstruction(instruction)
                && !(reflectiveInvocationRange && supportedMethodInvocationPreparationInstruction(instruction))
                && !supportedCaughtThrowableCauseConstructor(code, instructions, instructionIndex, instruction)
                && !supportedProtectedFinallyRethrowInstruction(classes, code, instruction)) {
                return false;
            }
        }
        if (hasThrowableTransport == 1) {
            return true;
        }
        return false;
    }

    private static boolean containsMethodInvocation(
        final List<Instruction> instructions,
        final CodeException handler
    ) {
        for (final Instruction instruction : instructions) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()
                || instruction.methodRef().isEmpty()) {
                continue;
            }
            final MethodRef target = instruction.methodRef().orElseThrow();
            if ("java/lang/reflect/Method".equals(target.owner())
                && "invoke".equals(target.name())
                && "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor())) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportsEntryAnchoredStraightLineTypedHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeException handler,
        final ReachableEntries reachableEntries
    ) {
        final CodeAttribute code = method.code().orElseThrow();
        if (code.exceptionTableLength() != 1
            || handler.catchType().isEmpty()
            || !isThrowable(classes, handler.catchType().orElseThrow())
            || handler.startPc() != 0
            || handler.endPc() <= handler.startPc()
            || handler.handlerPc() <= handler.endPc()
            || code.maxStack() > 64
            || code.maxLocals() > 256
            || instructionAtOffset(code, handler.startPc()).isEmpty()
            || instructionAtOffset(code, handler.endPc()).isEmpty()
            || instructionAtOffset(code, handler.handlerPc()).isEmpty()
            || !entryAnchoredNormalExit(code, handler)
            || hasEntryAnchoredInboundTarget(code, handler)) {
            return false;
        }
        final Instruction handlerEntry = instructionAtOffset(code, handler.handlerPc()).orElseThrow();
        final int catchLocal = astoreLocalIndex(handlerEntry);
        if (catchLocal < 0 || catchLocal >= code.maxLocals()
            || !hasSingleEntryAnchoredCatchStore(code, handler, catchLocal)) {
            return false;
        }
        final Optional<List<IrType>> initialLocals = entryAnchoredLocals(method, code.maxLocals());
        if (initialLocals.isEmpty()) {
            return false;
        }
        int lastTypedSite = -1;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() >= handler.startPc() && instruction.offset() < handler.endPc()
                && entryAnchoredTypedSite(classes, instruction, reachableEntries)) {
                lastTypedSite = instruction.offset();
            }
        }
        if (lastTypedSite < 0) {
            return false;
        }
        final List<IrType> locals = new ArrayList<>(initialLocals.orElseThrow());
        final List<IrType> stack = new ArrayList<>();
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (entryAnchoredControlFlow(instruction.opcode())
                || entryAnchoredStoreType(instruction.opcode()).isPresent()
                    && instruction.offset() < lastTypedSite
                || !entryAnchoredTypedSite(classes, instruction, reachableEntries)
                    && !entryAnchoredNonThrowingInstruction(instruction)
                || !applyEntryAnchoredStackEffect(instruction, locals, stack)
                || entryAnchoredStackWords(stack) > code.maxStack()) {
                return false;
            }
        }
        return entryAnchoredNormalStack(code, handler, stack);
    }

    private static boolean entryAnchoredTypedSite(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final ReachableEntries reachableEntries
    ) {
        if (instruction.opcode() == 192) {
            return instruction.className().isPresent()
                && supportedRuntimeCheckcastTarget(instruction.className().orElseThrow());
        }
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        if (entryAnchoredDecimalParser(target)) {
            return instruction.opcode() == 184 && "invokestatic".equals(instruction.mnemonic());
        }
        if (entryAnchoredMapGet(classes, target, reachableEntries)) {
            return ("java/util/Map".equals(target.owner()) && instruction.opcode() == 185)
                || (!"java/util/Map".equals(target.owner()) && instruction.opcode() == 182);
        }
        return entryAnchoredApplicationCall(classes, instruction.opcode(), target);
    }

    private static boolean entryAnchoredDecimalParser(final MethodRef target) {
        return "java/lang/Integer".equals(target.owner())
                && "parseInt".equals(target.name())
                && "(Ljava/lang/String;)I".equals(target.descriptor())
            || "java/lang/Long".equals(target.owner())
                && "parseLong".equals(target.name())
                && "(Ljava/lang/String;)J".equals(target.descriptor());
    }

    private static boolean entryAnchoredMapGet(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final ReachableEntries reachableEntries
    ) {
        return ("java/util/Map".equals(target.owner())
                || "java/util/HashMap".equals(target.owner())
                || "java/util/LinkedHashMap".equals(target.owner())
                || "java/util/TreeMap".equals(target.owner())
                || "java/util/concurrent/ConcurrentHashMap".equals(target.owner()))
            && "get".equals(target.name())
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(target.descriptor())
            && JdkCallSupport.supportedCall(target).isPresent()
            && !hasReachableEntryAnchoredApplicationMap(classes, reachableEntries);
    }

    private static boolean hasReachableEntryAnchoredApplicationMap(
        final Map<String, ClassFile> classes,
        final ReachableEntries reachableEntries
    ) {
        for (final ClassFile candidate : classes.values()) {
            if (!candidate.application()
                || candidate.isInterface()
                || !reachableEntries.containsOwner(candidate.name())) {
                continue;
            }
            if (entryAnchoredApplicationMapType(classes, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean entryAnchoredApplicationMapType(
        final Map<String, ClassFile> classes,
        final ClassFile candidate
    ) {
        String current = candidate.name();
        final List<String> visited = new ArrayList<>();
        while (current != null && !current.isEmpty() && !containsString(visited, current)) {
            if (entryAnchoredJdkMapType(current)) {
                return true;
            }
            visited.add(current);
            final ClassFile currentClass = classes.get(current);
            if (currentClass == null) {
                return false;
            }
            if (hasEntryAnchoredMapInterface(classes, currentClass, new ArrayList<>())) {
                return true;
            }
            current = currentClass.superName();
        }
        return false;
    }

    private static boolean hasEntryAnchoredMapInterface(
        final Map<String, ClassFile> classes,
        final ClassFile candidate,
        final List<String> visited
    ) {
        for (final String interfaceName : candidate.interfaces()) {
            if (entryAnchoredJdkMapType(interfaceName)) {
                return true;
            }
            if (containsString(visited, interfaceName)) {
                continue;
            }
            visited.add(interfaceName);
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null
                && hasEntryAnchoredMapInterface(classes, interfaceClass, visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean entryAnchoredJdkMapType(final String name) {
        return "java/util/Map".equals(name)
            || "java/util/SequencedMap".equals(name)
            || "java/util/AbstractMap".equals(name)
            || "java/util/HashMap".equals(name)
            || "java/util/LinkedHashMap".equals(name)
            || "java/util/TreeMap".equals(name)
            || "java/util/SortedMap".equals(name)
            || "java/util/NavigableMap".equals(name)
            || "java/util/concurrent/ConcurrentMap".equals(name)
            || "java/util/concurrent/ConcurrentHashMap".equals(name);
    }

    private static boolean entryAnchoredApplicationCall(
        final Map<String, ClassFile> classes,
        final int opcode,
        final MethodRef target
    ) {
        if ("<init>".equals(target.name())) {
            return false;
        }
        final ClassFile owner = classes.get(target.owner());
        if (owner == null || !owner.application()) {
            return false;
        }
        if (opcode == 184) {
            final Optional<MethodInfo> candidate = owner.method(target.name(), target.descriptor());
            return candidate.isPresent()
                && candidate.orElseThrow().isStatic()
                && candidate.orElseThrow().code().isPresent();
        }
        if (opcode == 182) {
            final Optional<MethodInfo> candidate = owner.method(target.name(), target.descriptor());
            return owner.isFinal()
                && !owner.isInterface()
                && candidate.isPresent()
                && !candidate.orElseThrow().isStatic()
                && candidate.orElseThrow().code().isPresent();
        }
        if (opcode != 185 || !owner.isInterface()) {
            return false;
        }
        int targets = 0;
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface() || !candidate.interfaces().contains(target.owner())) {
                continue;
            }
            final Optional<MethodInfo> implementation = candidate.method(target.name(), target.descriptor());
            if (!candidate.application()
                || implementation.isEmpty()
                || implementation.orElseThrow().isStatic()
                || implementation.orElseThrow().code().isEmpty()) {
                return false;
            }
            targets++;
        }
        return targets > 0;
    }

    private static boolean supportedRuntimeCheckcastTarget(final String target) {
        return "java/lang/String".equals(target)
            || "java/lang/Object".equals(target);
    }

    private static boolean entryAnchoredNormalExit(
        final CodeAttribute code,
        final CodeException handler
    ) {
        final Instruction normalExit = instructionAtOffset(code, handler.endPc()).orElseThrow();
        if (normalExit.opcode() >= 172 && normalExit.opcode() <= 177) {
            return true;
        }
        final int target = boundedBranchTarget(normalExit);
        return target > handler.handlerPc() && instructionAtOffset(code, target).isPresent();
    }

    private static boolean entryAnchoredNormalStack(
        final CodeAttribute code,
        final CodeException handler,
        final List<IrType> stack
    ) {
        Instruction exit = instructionAtOffset(code, handler.endPc()).orElseThrow();
        if (exit.opcode() == 167) {
            final Optional<Instruction> target = instructionAtOffset(
                code,
                boundedBranchTarget(exit)
            );
            if (target.isEmpty()) {
                return false;
            }
            exit = target.orElseThrow();
        }
        if (exit.opcode() == 177) {
            return stack.isEmpty();
        }
        if (stack.size() != 1) {
            return false;
        }
        if (exit.opcode() == 172) {
            return stack.getFirst() == IrType.INT;
        }
        if (exit.opcode() == 173) {
            return stack.getFirst() == IrType.LONG;
        }
        if (exit.opcode() == 174) {
            return stack.getFirst() == IrType.FLOAT;
        }
        if (exit.opcode() == 175) {
            return stack.getFirst() == IrType.DOUBLE;
        }
        return exit.opcode() == 176 && stack.getFirst() == IrType.OBJECT;
    }

    private static boolean hasEntryAnchoredInboundTarget(
        final CodeAttribute code,
        final CodeException handler
    ) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() >= handler.startPc() && instruction.offset() < handler.endPc()) {
                continue;
            }
            final int target = boundedBranchTarget(instruction);
            if (target >= handler.startPc() && target < handler.endPc()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSingleEntryAnchoredCatchStore(
        final CodeAttribute code,
        final CodeException handler,
        final int catchLocal
    ) {
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() == handler.handlerPc()) {
                continue;
            }
            if (entryAnchoredStoreLocalIndex(instruction) == catchLocal) {
                return false;
            }
        }
        return true;
    }

    private static boolean entryAnchoredControlFlow(final int opcode) {
        return opcode >= 153 && opcode <= 177
            || opcode == 191
            || opcode >= 194 && opcode <= 201;
    }

    private static boolean entryAnchoredNonThrowingInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 18 || opcode == 19 || opcode == 20) {
            return entryAnchoredLiteralType(instruction).isPresent();
        }
        return opcode == 0
            || opcode == 1
            || opcode >= 2 && opcode <= 17
            || opcode >= 21 && opcode <= 78
            || opcode == 87
            || opcode == 88
            || opcode == 89
            || opcode == 117
            || opcode == 142;
    }

    private static boolean applyEntryAnchoredStackEffect(
        final Instruction instruction,
        final List<IrType> locals,
        final List<IrType> stack
    ) {
        final int opcode = instruction.opcode();
        if (opcode == 0) {
            return true;
        }
        if (opcode == 1) {
            return addEntryAnchoredType(stack, IrType.OBJECT);
        }
        if (opcode == 18 || opcode == 19 || opcode == 20) {
            final Optional<IrType> type = entryAnchoredLiteralType(instruction);
            return type.isPresent() && addEntryAnchoredType(stack, type.orElseThrow());
        }
        if (opcode >= 2 && opcode <= 8 || opcode == 16 || opcode == 17) {
            return addEntryAnchoredType(stack, IrType.INT);
        }
        if (opcode == 9 || opcode == 10) {
            return addEntryAnchoredType(stack, IrType.LONG);
        }
        if (opcode >= 11 && opcode <= 13) {
            return addEntryAnchoredType(stack, IrType.FLOAT);
        }
        if (opcode == 14 || opcode == 15) {
            return addEntryAnchoredType(stack, IrType.DOUBLE);
        }
        final Optional<IrType> loaded = entryAnchoredLoadType(opcode);
        if (loaded.isPresent()) {
            final int local = entryAnchoredLoadLocalIndex(instruction);
            return local >= 0 && local < locals.size() && locals.get(local) == loaded.orElseThrow()
                && addEntryAnchoredType(stack, loaded.orElseThrow());
        }
        final Optional<IrType> stored = entryAnchoredStoreType(opcode);
        if (stored.isPresent()) {
            final int local = entryAnchoredStoreLocalIndex(instruction);
            final IrType type = stored.orElseThrow();
            if (local < 0 || local >= locals.size() || !popEntryAnchoredType(stack, type)
                || type.slotWidth() == 2 && local + 1 >= locals.size()) {
                return false;
            }
            locals.set(local, type);
            if (type.slotWidth() == 2) {
                locals.set(local + 1, IrType.VOID);
            }
            return true;
        }
        if (opcode == 87) {
            return popEntryAnchoredCategoryOne(stack);
        }
        if (opcode == 88) {
            if (stack.isEmpty()) {
                return false;
            }
            final IrType top = stack.removeLast();
            return top.slotWidth() == 2 || popEntryAnchoredCategoryOne(stack);
        }
        if (opcode == 89) {
            if (stack.isEmpty() || stack.getLast().slotWidth() == 2) {
                return false;
            }
            stack.add(stack.getLast());
            return true;
        }
        if (opcode == 117) {
            return popEntryAnchoredType(stack, IrType.LONG)
                && addEntryAnchoredType(stack, IrType.LONG);
        }
        if (opcode == 142) {
            return popEntryAnchoredType(stack, IrType.DOUBLE)
                && addEntryAnchoredType(stack, IrType.INT);
        }
        if (opcode == 192) {
            return popEntryAnchoredType(stack, IrType.OBJECT)
                && addEntryAnchoredType(stack, IrType.OBJECT);
        }
        return instruction.methodRef().isPresent()
            && applyEntryAnchoredCallStackEffect(
                instruction.opcode(),
                instruction.methodRef().orElseThrow(),
                stack
            );
    }

    private static Optional<IrType> entryAnchoredLiteralType(final Instruction instruction) {
        if (instruction.className().isPresent()
            || instruction.methodRef().isPresent()
            || instruction.fieldRef().isPresent()
            || instruction.dynamicRef().isPresent()) {
            return Optional.empty();
        }
        final List<IrType> types = new ArrayList<>();
        if (instruction.intValue().isPresent()) {
            types.add(IrType.INT);
        }
        if (instruction.longValue().isPresent()) {
            types.add(IrType.LONG);
        }
        if (instruction.floatValue().isPresent()) {
            types.add(IrType.FLOAT);
        }
        if (instruction.doubleValue().isPresent()) {
            types.add(IrType.DOUBLE);
        }
        if (instruction.stringValue().isPresent()
            && Strings2.isRuntimeAsciiStringConstant(instruction.stringValue().orElseThrow())) {
            types.add(IrType.OBJECT);
        }
        if (types.size() != 1) {
            return Optional.empty();
        }
        final IrType type = types.getFirst();
        if (instruction.opcode() == 20) {
            return type.slotWidth() == 2 ? Optional.of(type) : Optional.empty();
        }
        return type.slotWidth() == 2 ? Optional.empty() : Optional.of(type);
    }

    private static boolean applyEntryAnchoredCallStackEffect(
        final int opcode,
        final MethodRef target,
        final List<IrType> stack
    ) {
        final MethodDescriptor descriptor = MethodDescriptor.parse(target.descriptor());
        for (int index = descriptor.parameterTypes().size() - 1; index >= 0; index--) {
            if (!popEntryAnchoredType(stack, descriptor.parameterTypes().get(index))) {
                return false;
            }
        }
        if (opcode != 184 && !popEntryAnchoredType(stack, IrType.OBJECT)) {
            return false;
        }
        return descriptor.returnType() == IrType.VOID
            || addEntryAnchoredType(stack, descriptor.returnType());
    }

    private static Optional<List<IrType>> entryAnchoredLocals(
        final MethodInfo method,
        final int maxLocals
    ) {
        final MethodDescriptor descriptor = MethodDescriptor.parse(method.descriptor());
        final List<IrType> locals = new ArrayList<>();
        if (!method.isStatic()) {
            locals.add(IrType.OBJECT);
        }
        for (final IrType parameter : descriptor.parameterTypes()) {
            locals.add(parameter);
            if (parameter.slotWidth() == 2) {
                locals.add(IrType.VOID);
            }
        }
        if (locals.size() > maxLocals) {
            return Optional.empty();
        }
        while (locals.size() < maxLocals) {
            locals.add(IrType.VOID);
        }
        return Optional.of(List.copyOf(locals));
    }

    private static Optional<IrType> entryAnchoredLoadType(final int opcode) {
        if (opcode == 21 || opcode >= 26 && opcode <= 29) {
            return Optional.of(IrType.INT);
        }
        if (opcode == 22 || opcode >= 30 && opcode <= 33) {
            return Optional.of(IrType.LONG);
        }
        if (opcode == 23 || opcode >= 34 && opcode <= 37) {
            return Optional.of(IrType.FLOAT);
        }
        if (opcode == 24 || opcode >= 38 && opcode <= 41) {
            return Optional.of(IrType.DOUBLE);
        }
        return opcode == 25 || opcode >= 42 && opcode <= 45
            ? Optional.of(IrType.OBJECT)
            : Optional.empty();
    }

    private static Optional<IrType> entryAnchoredStoreType(final int opcode) {
        if (opcode == 54 || opcode >= 59 && opcode <= 62) {
            return Optional.of(IrType.INT);
        }
        if (opcode == 55 || opcode >= 63 && opcode <= 66) {
            return Optional.of(IrType.LONG);
        }
        if (opcode == 56 || opcode >= 67 && opcode <= 70) {
            return Optional.of(IrType.FLOAT);
        }
        if (opcode == 57 || opcode >= 71 && opcode <= 74) {
            return Optional.of(IrType.DOUBLE);
        }
        return opcode == 58 || opcode >= 75 && opcode <= 78
            ? Optional.of(IrType.OBJECT)
            : Optional.empty();
    }

    private static int entryAnchoredLoadLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 21 && opcode <= 25) {
            return instruction.operands().length == 1 ? instruction.operands()[0] & 0xFF : -1;
        }
        if (opcode >= 26 && opcode <= 29) {
            return opcode - 26;
        }
        if (opcode >= 30 && opcode <= 33) {
            return opcode - 30;
        }
        if (opcode >= 34 && opcode <= 37) {
            return opcode - 34;
        }
        if (opcode >= 38 && opcode <= 41) {
            return opcode - 38;
        }
        return opcode >= 42 && opcode <= 45 ? opcode - 42 : -1;
    }

    private static int entryAnchoredStoreLocalIndex(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 54 && opcode <= 58) {
            return instruction.operands().length == 1 ? instruction.operands()[0] & 0xFF : -1;
        }
        if (opcode >= 59 && opcode <= 62) {
            return opcode - 59;
        }
        if (opcode >= 63 && opcode <= 66) {
            return opcode - 63;
        }
        if (opcode >= 67 && opcode <= 70) {
            return opcode - 67;
        }
        if (opcode >= 71 && opcode <= 74) {
            return opcode - 71;
        }
        return opcode >= 75 && opcode <= 78 ? opcode - 75 : -1;
    }

    private static boolean popEntryAnchoredType(
        final List<IrType> stack,
        final IrType expected
    ) {
        if (stack.isEmpty() || stack.getLast() != expected) {
            return false;
        }
        stack.removeLast();
        return true;
    }

    private static boolean popEntryAnchoredCategoryOne(final List<IrType> stack) {
        if (stack.isEmpty() || stack.getLast().slotWidth() == 2) {
            return false;
        }
        stack.removeLast();
        return true;
    }

    private static boolean addEntryAnchoredType(
        final List<IrType> stack,
        final IrType type
    ) {
        if (type == IrType.VOID) {
            return false;
        }
        stack.add(type);
        return true;
    }

    private static int entryAnchoredStackWords(final List<IrType> stack) {
        int words = 0;
        for (final IrType type : stack) {
            words += type.slotWidth();
        }
        return words;
    }

    private static boolean supportsImmediateOptionalOrElseThrowSupplierHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler
    ) {
        if (handler.catchType().isEmpty()
            || !JdkCallSupport.isPlatformThrowable(handler.catchType().orElseThrow())
            || code.maxStack() > 64
            || code.maxLocals() > 256
            || handler.startPc() >= handler.endPc()
            || !boundedEndBoundary(code, handler.endPc())
            || !boundedProtectedRangeEntryIsIsolated(code, handler)
            || !supportedOptionalSupplierProtectedRange(classes, method, code, handler)) {
            return false;
        }
        final Optional<Instruction> handlerEntry = instructionAtOffset(
            code,
            handler.handlerPc()
        );
        if (handlerEntry.isEmpty() || astoreLocalIndex(handlerEntry.orElseThrow()) < 0) {
            return false;
        }
        final List<Instruction> instructions = code.instructions();
        int optionalCalls = 0;
        for (int index = 0; index < instructions.size(); index++) {
            final Instruction instruction = instructions.get(index);
            if (instruction.offset() < handler.startPc()
                || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (instruction.methodRef().isPresent()
                && JdkCallSupport.isContextLimitedOptionalOrElseThrowCall(
                    instruction.methodRef().orElseThrow()
                )) {
                optionalCalls++;
                if (optionalCalls > 1
                    || !isImmediateOptionalOrElseThrowSupplier(classes, instructions, index)) {
                    return false;
                }
            }
        }
        return optionalCalls == 1;
    }

    private static boolean supportedOptionalSupplierProtectedRange(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler
    ) {
        int protectedInstructions = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()
                || instruction.offset() >= handler.endPc()) {
                continue;
            }
            protectedInstructions++;
            if (protectedInstructions > 64) {
                return false;
            }
            if (instruction.opcode() == 192
                && JdkCallSupport.isPlatformThrowableAssignable(
                    "java/lang/ClassCastException",
                    handler.catchType().orElseThrow()
                )) {
                if (!supportedCheckcastThrowable(
                    instruction,
                    handler.catchType().orElseThrow()
                )) {
                    return false;
                }
                continue;
            }
            if (supportedBoundedProtectedInstruction(
                classes,
                method,
                code,
                handler,
                instruction
            )) {
                continue;
            }
            if (instruction.opcode() == 178 && instruction.fieldRef().isPresent()) {
                continue;
            }
            if (instruction.opcode() == 180
                && instruction.fieldRef().isPresent()
                && isExactOptionalFieldRead(classes, instruction)
                && !JdkCallSupport.isPlatformThrowableAssignable(
                    "java/lang/NullPointerException",
                    handler.catchType().orElseThrow()
                )) {
                continue;
            }
            if (instruction.opcode() == 192
                && instruction.className().isPresent()
                && !"java/util/Locale".equals(instruction.className().orElseThrow())) {
                continue;
            }
            return false;
        }
        return protectedInstructions > 0;
    }

    private static boolean supportedCheckcastThrowable(
        final Instruction instruction,
        final String catchType
    ) {
        return instruction.opcode() == 192
            && instruction.className().isPresent()
            && supportedRuntimeCheckcastTarget(instruction.className().orElseThrow())
            && JdkCallSupport.isPlatformThrowableAssignable(
                "java/lang/ClassCastException",
                catchType
            );
    }

    private static boolean supportedTransportedThrowableCall(
        final Map<String, ClassFile> classes,
        final Instruction instruction,
        final String catchType
    ) {
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        for (final String throwableType : JdkCallSupport.transportedPlatformThrowableTypes(target)) {
            if (isThrowableAssignable(classes, throwableType, catchType)) {
                return true;
            }
        }
        for (final String throwableType : escapingPlatformExceptionTypes(classes, target, new HashSet<>())) {
            if (isThrowableAssignable(classes, throwableType, catchType)) {
                return true;
            }
        }
        for (final String throwableType : escapingLambdaPlatformExceptionTypes(classes, target, new HashSet<>())) {
            if (isThrowableAssignable(classes, throwableType, catchType)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportedOptionalFactoryPreparation(final Instruction instruction) {
        if (instruction.opcode() != 184 || instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        return "java/util/Optional".equals(target.owner())
            && (("empty".equals(target.name())
                && "()Ljava/util/Optional;".equals(target.descriptor()))
                || (("of".equals(target.name()) || "ofNullable".equals(target.name()))
                    && "(Ljava/lang/Object;)Ljava/util/Optional;".equals(target.descriptor())));
    }

    private static boolean supportedThrowableFieldPreparation(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        if ((instruction.opcode() != 178 && instruction.opcode() != 180) || instruction.fieldRef().isEmpty()) {
            return false;
        }
        final String descriptor = instruction.fieldRef().orElseThrow().descriptor();
        if (descriptor.length() < 3 || descriptor.charAt(0) != 'L' || descriptor.charAt(descriptor.length() - 1) != ';') {
            return false;
        }
        return isThrowable(classes, descriptor.substring(1, descriptor.length() - 1));
    }

    private static boolean supportedGeneratedThrowableCall(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        if (instruction.opcode() < 182 || instruction.opcode() > 185 || instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        final ClassFile targetClass = classes.get(target.owner());
        if (targetClass == null) {
            return supportedLambdaThrowableCall(classes, target);
        }
        final Optional<MethodInfo> targetMethod = targetClass.method(target.name(), target.descriptor());
        if (targetMethod.isEmpty() || targetMethod.orElseThrow().code().isEmpty()) {
            return supportedLambdaThrowableCall(classes, target);
        }
        return !escapingPlatformExceptionTypes(classes, target, new HashSet<>()).isEmpty()
            || staticallyNonThrowingGeneratedMethod(targetMethod.orElseThrow());
    }

    private static boolean supportedLambdaThrowableCall(
        final Map<String, ClassFile> classes,
        final MethodRef target
    ) {
        if (!escapingLambdaPlatformExceptionTypes(classes, target, new HashSet<>()).isEmpty()) {
            return true;
        }
        for (final ClassFile classFile : classes.values()) {
            for (final MethodInfo method : classFile.methods()) {
                if (method.code().isEmpty()) {
                    continue;
                }
                for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                    if (instruction.dynamicRef().isEmpty()) {
                        continue;
                    }
                    final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow();
                    final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(dynamicRef);
                    if (resolved.isEmpty()) {
                        continue;
                    }
                    final LambdaMetafactoryCall lambda = resolved.orElseThrow();
                    if (!matchesSupportedLambdaCall(
                        classes,
                        target,
                        method,
                        instruction,
                        dynamicRef,
                        lambda
                    )) {
                        continue;
                    }
                    final ClassFile implementationClass = classes.get(lambda.implementation().owner());
                    if (implementationClass == null) {
                        continue;
                    }
                    final Optional<MethodInfo> implementation = implementationClass.method(
                        lambda.implementation().name(),
                        lambda.implementation().descriptor()
                    );
                    if (implementation.isEmpty() || implementation.orElseThrow().code().isEmpty()) {
                        continue;
                    }
                    if (staticallyNonThrowingGeneratedMethod(implementation.orElseThrow())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Set<String> escapingLambdaPlatformExceptionTypes(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final Set<MethodRef> visiting
    ) {
        final Set<String> result = new HashSet<>();
        for (final ClassFile classFile : classes.values()) {
            for (final MethodInfo method : classFile.methods()) {
                if (method.code().isEmpty()) {
                    continue;
                }
                for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                    if (instruction.dynamicRef().isEmpty()) {
                        continue;
                    }
                    final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow();
                    final Optional<LambdaMetafactoryCall> resolved = LambdaMetafactoryCall.resolve(dynamicRef);
                    if (resolved.isEmpty()) {
                        continue;
                    }
                    final LambdaMetafactoryCall lambda = resolved.orElseThrow();
                    if (matchesSupportedLambdaCall(
                        classes,
                        target,
                        method,
                        instruction,
                        dynamicRef,
                        lambda
                    )) {
                        result.addAll(escapingPlatformExceptionTypes(
                            classes,
                            lambda.implementation(),
                            visiting
                        ));
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean matchesSupportedLambdaCall(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final MethodInfo method,
        final Instruction instruction,
        final DynamicRef dynamicRef,
        final LambdaMetafactoryCall lambda
    ) {
        return target.owner().equals(lambda.interfaceOwner())
            && target.name().equals(lambda.interfaceMethodName())
            && target.descriptor().equals(lambda.samMethodDescriptor())
            && supportedLambdaMetafactory(classes, method, instruction, dynamicRef);
    }

    private static boolean staticallyNonThrowingGeneratedMethod(final MethodInfo method) {
        for (final Instruction instruction : method.code().orElseThrow().instructions()) {
            final int opcode = instruction.opcode();
            if (opcode == 0 || opcode == 1 || (opcode >= 2 && opcode <= 20)
                || (opcode >= 21 && opcode <= 45) || (opcode >= 54 && opcode <= 78)
                || (opcode >= 87 && opcode <= 95) || (opcode >= 116 && opcode <= 152)
                || (opcode >= 153 && opcode <= 177)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean supportedThrowableWrapRangeInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 25 || (opcode >= 42 && opcode <= 45)
            || (opcode >= 54 && opcode <= 78)
            || opcode == 167) {
            return true;
        }
        if (opcode == 186) {
            return instruction.dynamicRef().isPresent()
                && supportedStringConcat(instruction.dynamicRef().orElseThrow());
        }
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        return JdkCallSupport.isPlatformThrowable(target.owner())
            && "getMessage".equals(target.name())
            && "()Ljava/lang/String;".equals(target.descriptor());
    }

    private static boolean supportedMethodInvocationPreparationInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if ((opcode >= 1 && opcode <= 20)
            || (opcode >= 46 && opcode <= 53)
            || opcode == 83
            || (opcode >= 87 && opcode <= 95)) {
            return true;
        }
        if (opcode == 189) {
            return instruction.className().isPresent()
                && "java/lang/Object".equals(instruction.className().orElseThrow());
        }
        if (instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        return "valueOf".equals(target.name())
            && switch (target.owner()) {
                case "java/lang/Boolean" -> "(Z)Ljava/lang/Boolean;".equals(target.descriptor());
                case "java/lang/Byte" -> "(B)Ljava/lang/Byte;".equals(target.descriptor());
                case "java/lang/Short" -> "(S)Ljava/lang/Short;".equals(target.descriptor());
                case "java/lang/Character" -> "(C)Ljava/lang/Character;".equals(target.descriptor());
                case "java/lang/Integer" -> "(I)Ljava/lang/Integer;".equals(target.descriptor());
                case "java/lang/Long" -> "(J)Ljava/lang/Long;".equals(target.descriptor());
                case "java/lang/Float" -> "(F)Ljava/lang/Float;".equals(target.descriptor());
                case "java/lang/Double" -> "(D)Ljava/lang/Double;".equals(target.descriptor());
                default -> false;
            };
    }

    private static boolean supportedCaughtThrowableCauseConstructor(
        final MethodInfo method,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef target
    ) {
        if (method.code().isEmpty() || !JdkCallSupport.isPlatformThrowableCauseConstructor(target)) {
            return false;
        }
        return caughtThrowableCauseLocal(method.code().orElseThrow(), instructions, instructionIndex).isPresent();
    }

    private static boolean supportedCaughtThrowableCauseConstructor(
        final CodeAttribute code,
        final List<Instruction> instructions,
        final int instructionIndex,
        final Instruction instruction
    ) {
        if (instruction.methodRef().isEmpty()
            || !JdkCallSupport.isPlatformThrowableCauseConstructor(instruction.methodRef().orElseThrow())) {
            return false;
        }
        return caughtThrowableCauseLocal(code, instructions, instructionIndex).isPresent();
    }

    private static Optional<Integer> caughtThrowableCauseLocal(
        final CodeAttribute code,
        final List<Instruction> instructions,
        final int instructionIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return Optional.empty();
        }
        final Instruction constructor = instructions.get(instructionIndex);
        final int causeLocal = aloadLocalIndex(instructions.get(instructionIndex - 1));
        if (causeLocal < 0) {
            return Optional.empty();
        }
        for (final CodeException handler : code.exceptionTable()) {
            if (handler.catchType().isEmpty()
                || !isPlatformThrowable(handler.catchType().orElseThrow())
                || handler.handlerPc() >= constructor.offset()) {
                continue;
            }
            final Optional<Instruction> handlerInstruction = instructionAtOffset(code, handler.handlerPc());
            if (handlerInstruction.isEmpty()
                || astoreLocalIndex(handlerInstruction.orElseThrow()) != causeLocal
                || !caughtThrowableCauseFlowsStraight(
                    code,
                    instructions,
                    handlerInstruction.orElseThrow().offset(),
                    instructionIndex,
                    causeLocal
                )) {
                continue;
            }
            return Optional.of(causeLocal);
        }
        return Optional.empty();
    }

    private static boolean caughtThrowableCauseFlowsStraight(
        final CodeAttribute code,
        final List<Instruction> instructions,
        final int handlerOffset,
        final int constructorIndex,
        final int causeLocal
    ) {
        int handlerIndex = -1;
        for (int index = 0; index < constructorIndex; index++) {
            if (instructions.get(index).offset() == handlerOffset) {
                handlerIndex = index;
                break;
            }
        }
        if (handlerIndex < 0) {
            return false;
        }
        final int constructorOffset = instructions.get(constructorIndex).offset();
        for (final CodeException handler : code.exceptionTable()) {
            if (handler.handlerPc() != handlerOffset
                && handler.handlerPc() > handlerOffset
                && handler.handlerPc() < constructorOffset) {
                return false;
            }
        }
        for (int index = handlerIndex + 1; index < constructorIndex; index++) {
            final Instruction instruction = instructions.get(index);
            if (astoreLocalIndex(instruction) == causeLocal
                || caughtThrowableCauseControlBoundary(instruction.opcode())) {
                return false;
            }
        }
        return true;
    }

    private static boolean caughtThrowableCauseControlBoundary(final int opcode) {
        if (opcode == 191) {
            return true;
        }
        if (opcode >= 153 && opcode <= 177) {
            return true;
        }
        return opcode >= 198 && opcode <= 201;
    }

    private static Set<String> escapingPlatformExceptionTypes(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final Set<MethodRef> visiting
    ) {
        if (!visiting.add(target)) {
            return Set.of();
        }
        final ClassFile targetClass = classes.get(target.owner());
        if (targetClass == null) {
            visiting.remove(target);
            return Set.of();
        }
        final Optional<MethodInfo> targetMethod = targetClass.method(target.name(), target.descriptor());
        if (targetMethod.isEmpty() || targetMethod.orElseThrow().code().isEmpty()) {
            visiting.remove(target);
            return Set.of();
        }
        final CodeAttribute code = targetMethod.orElseThrow().code().orElseThrow();
        final Set<String> result = new HashSet<>();
        result.addAll(finallyReplacementThrowableTypes(classes, code));
        final Set<String> allocatedTypes = new HashSet<>();
        final Set<String> throwableParameters = applicationThrowableParameters(
            classes,
            targetMethod.orElseThrow()
        );
        for (final Instruction instruction : code.instructions()) {
            if (BytecodeSupport.isIntegralDivisionOrRemainder(instruction.opcode())
                && !caughtByThrowableHandler(classes, code, instruction.offset(), "java/lang/ArithmeticException")) {
                result.add("java/lang/ArithmeticException");
            }
            if (BytecodeSupport.isSingleDimensionArrayAllocation(instruction.opcode())
                && !caughtByThrowableHandler(
                    classes,
                    code,
                    instruction.offset(),
                    "java/lang/NegativeArraySizeException"
                )) {
                result.add("java/lang/NegativeArraySizeException");
            }
            if (BytecodeSupport.isArrayReferenceAccess(instruction.opcode())
                && !caughtByThrowableHandler(
                    classes,
                    code,
                    instruction.offset(),
                    "java/lang/NullPointerException"
                )) {
                result.add("java/lang/NullPointerException");
            }
            if (BytecodeSupport.isIndexedArrayAccess(instruction.opcode())
                && !caughtByThrowableHandler(
                    classes,
                    code,
                    instruction.offset(),
                    "java/lang/ArrayIndexOutOfBoundsException"
                )) {
                result.add("java/lang/ArrayIndexOutOfBoundsException");
            }
            if (instruction.methodRef().isPresent()) {
                final MethodRef called = instruction.methodRef().orElseThrow();
                for (final String throwableType : JdkCallSupport.transportedPlatformThrowableTypes(called)) {
                    if (!caughtByThrowableHandler(classes, code, instruction.offset(), throwableType)) {
                        result.add(throwableType);
                    }
                }
                for (final String throwableType : escapingPlatformExceptionTypes(classes, called, visiting)) {
                    if (!caughtByThrowableHandler(classes, code, instruction.offset(), throwableType)) {
                        result.add(throwableType);
                    }
                }
                for (final String throwableType : escapingLambdaPlatformExceptionTypes(classes, called, visiting)) {
                    if (!caughtByThrowableHandler(classes, code, instruction.offset(), throwableType)) {
                        result.add(throwableType);
                    }
                }
            }
            if (instruction.opcode() == 187
                && instruction.className().isPresent()
                && isThrowable(classes, instruction.className().orElseThrow())) {
                allocatedTypes.add(instruction.className().orElseThrow());
                continue;
            }
            if (instruction.opcode() != 191) {
                continue;
            }
            allocatedTypes.addAll(throwableParameters);
            if (!allocatedTypes.isEmpty()
                && !caughtByThrowableHandler(classes, code, instruction.offset(), "java/lang/NullPointerException")) {
                result.add("java/lang/NullPointerException");
            }
            for (final String throwableType : allocatedTypes) {
                if (!caughtByThrowableHandler(classes, code, instruction.offset(), throwableType)) {
                    result.add(throwableType);
                }
            }
            allocatedTypes.clear();
        }
        visiting.remove(target);
        return Set.copyOf(result);
    }

    private static Set<String> finallyReplacementThrowableTypes(
        final Map<String, ClassFile> classes,
        final CodeAttribute code
    ) {
        final Set<String> result = new HashSet<>();
        final List<Instruction> instructions = code.instructions();
        for (final CodeException handler : code.exceptionTable()) {
            if (handler.catchType().isPresent()) {
                continue;
            }
            final Optional<CaughtThrowableRethrowAnalysis.FinallyFlow> flow =
                CaughtThrowableRethrowAnalysis.analyzeFinally(code, handler);
            if (flow.isEmpty()) {
                continue;
            }
            for (final CaughtThrowableRethrowAnalysis.ReplacementThrow replacement : flow.orElseThrow().replacements()) {
                result.add(replacement.throwableType());
            }
            for (final int local : flow.orElseThrow().replacementLocals()) {
                final Optional<String> type = code.objectLocalTypeAt(handler.handlerPc(), local);
                if (type.isPresent() && isThrowable(classes, type.orElseThrow())) {
                    result.add(type.orElseThrow());
                }
            }
            if (!flow.orElseThrow().replacementLocals().isEmpty()) {
                result.addAll(throwableValueTypesBefore(classes, instructions, handler.handlerPc()));
            }
            for (final int throwOffset : flow.orElseThrow().replacementValueOffsets()) {
                final Optional<String> type = directThrowableValueType(classes, instructions, throwOffset);
                if (type.isPresent()) {
                    result.add(type.orElseThrow());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Set<String> throwableValueTypesBefore(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int endOffset
    ) {
        final Set<String> result = new HashSet<>();
        for (final Instruction instruction : instructions) {
            if (instruction.offset() >= endOffset) {
                break;
            }
            if (instruction.className().isPresent()
                && isThrowable(classes, instruction.className().orElseThrow())) {
                result.add(instruction.className().orElseThrow());
            }
            if (instruction.fieldRef().isPresent()) {
                final Optional<String> fieldType = throwableDescriptorType(
                    classes,
                    instruction.fieldRef().orElseThrow().descriptor()
                );
                if (fieldType.isPresent()) {
                    result.add(fieldType.orElseThrow());
                }
            }
            if (instruction.methodRef().isPresent()) {
                final Optional<ThrowableReturnAnalysis.Result> returned = ThrowableReturnAnalysis.analyze(
                    classes,
                    instruction.methodRef().orElseThrow(),
                    instruction.opcode() == 183 || instruction.opcode() == 184
                );
                if (returned.isPresent()) {
                    result.addAll(returned.orElseThrow().possibleTypes());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static Optional<String> directThrowableValueType(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int throwOffset
    ) {
        for (int index = 1; index < instructions.size(); index++) {
            if (instructions.get(index).offset() != throwOffset) {
                continue;
            }
            final Instruction source = instructions.get(index - 1);
            if (source.fieldRef().isPresent()) {
                return throwableDescriptorType(classes, source.fieldRef().orElseThrow().descriptor());
            }
            if (source.methodRef().isPresent()) {
                final String descriptor = source.methodRef().orElseThrow().descriptor();
                return throwableDescriptorType(classes, descriptor.substring(descriptor.indexOf(')') + 1));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Set<String> applicationThrowableParameters(
        final Map<String, ClassFile> classes,
        final MethodInfo method
    ) {
        final Set<String> result = new HashSet<>();
        for (final String candidate : method.referencedParameterTypes()) {
            if (isThrowable(classes, candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private static boolean caughtByThrowableHandler(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final int offset,
        final String throwableType
    ) {
        for (final CodeException handler : code.exceptionTable()) {
            if (offset < handler.startPc() || offset >= handler.endPc()) {
                continue;
            }
            if (handler.catchType().isEmpty()
                || isThrowableAssignable(classes, throwableType, handler.catchType().orElseThrow())) {
                return !handlerMayThrow(code, handler);
            }
        }
        return false;
    }

    private static boolean handlerMayThrow(final CodeAttribute code, final CodeException handler) {
        // Handlers may jump backward to shared throw code. Over-reporting only widens verification.
        for (final Instruction instruction : code.instructions()) {
            if (instruction.opcode() == 191) {
                return true;
            }
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

    private static boolean supportedArraysFillHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()) {
            return false;
        }
        final String catchType = handler.catchType().orElseThrow();
        int hasCaughtFillCall = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (isArraysFillCall(instruction)) {
                if (arraysFillCanThrowTo(instruction.methodRef().orElseThrow(), catchType)) {
                    hasCaughtFillCall = 1;
                }
                continue;
            }
            if (instruction.opcode() == 1) {
                continue;
            }
            if (instruction.opcode() == 192
                && instruction.className().isPresent()
                && "[B".equals(instruction.className().orElseThrow())) {
                continue;
            }
            if (!supportedInterruptedWaitProtectedInstruction(instruction)) {
                return false;
            }
        }
        return hasCaughtFillCall == 1;
    }

    private static boolean isArraysFillCall(final Instruction instruction) {
        if (instruction.opcode() != 184 || instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        if (!"java/util/Arrays".equals(target.owner()) || !"fill".equals(target.name())) {
            return false;
        }
        return "([BB)V".equals(target.descriptor()) || "([BIIB)V".equals(target.descriptor());
    }

    private static boolean arraysFillCanThrowTo(final MethodRef target, final String catchType) {
        if (JdkCallSupport.isPlatformThrowableAssignable("java/lang/NullPointerException", catchType)) {
            return true;
        }
        if (!"([BIIB)V".equals(target.descriptor())) {
            return false;
        }
        if (JdkCallSupport.isPlatformThrowableAssignable("java/lang/IllegalArgumentException", catchType)) {
            return true;
        }
        return JdkCallSupport.isPlatformThrowableAssignable("java/lang/ArrayIndexOutOfBoundsException", catchType);
    }

    private static boolean supportedStringLocaleCaseHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()
            || !JdkCallSupport.isPlatformThrowableAssignable(
                "java/lang/NullPointerException",
                handler.catchType().orElseThrow()
            )) {
            return false;
        }
        int hasLocaleCaseCall = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (isStringLocaleCaseCall(instruction)) {
                hasLocaleCaseCall = 1;
                continue;
            }
            if (isSupportedStringCaseLocaleField(instruction)) {
                continue;
            }
            if (!supportedInterruptedWaitProtectedInstruction(instruction)) {
                return false;
            }
        }
        return hasLocaleCaseCall == 1;
    }

    private static boolean isStringLocaleCaseCall(final Instruction instruction) {
        if (instruction.opcode() != 182 || instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        return "java/lang/String".equals(target.owner())
            && "toLowerCase".equals(target.name())
            && "(Ljava/util/Locale;)Ljava/lang/String;".equals(target.descriptor());
    }

    private static boolean isSupportedStringCaseLocaleField(final Instruction instruction) {
        if (instruction.opcode() != 178 || instruction.fieldRef().isEmpty()) {
            return false;
        }
        final FieldRef target = instruction.fieldRef().orElseThrow();
        return "java/util/Locale".equals(target.owner())
            && ("ROOT".equals(target.name()) || "ENGLISH".equals(target.name()))
            && "Ljava/util/Locale;".equals(target.descriptor());
    }

    private static boolean supportedMathExactHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()
            || !JdkCallSupport.isPlatformThrowableAssignable(
                "java/lang/ArithmeticException",
                handler.catchType().orElseThrow()
            )) {
            return false;
        }
        int hasExactCall = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc()) {
                continue;
            }
            if (instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (isSupportedMathExactCall(instruction)) {
                hasExactCall = 1;
                continue;
            }
            if (!supportedMathExactProtectedInstruction(instruction)) {
                return false;
            }
        }
        return hasExactCall == 1;
    }

    private static boolean supportedIntegralArithmeticHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()
            || !JdkCallSupport.isPlatformThrowableAssignable(
                "java/lang/ArithmeticException",
                handler.catchType().orElseThrow()
            )) {
            return false;
        }
        int arithmeticInstructionCount = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (BytecodeSupport.isIntegralDivisionOrRemainder(instruction.opcode())) {
                arithmeticInstructionCount++;
                continue;
            }
            if (!boundedNonThrowingOpcode(instruction.opcode())) {
                return false;
            }
        }
        return arithmeticInstructionCount == 1;
    }

    private static boolean supportedNegativeArraySizeHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()
            || !JdkCallSupport.isPlatformThrowableAssignable(
                "java/lang/NegativeArraySizeException",
                handler.catchType().orElseThrow()
            )) {
            return false;
        }
        int arrayAllocationCount = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (BytecodeSupport.isSingleDimensionArrayAllocation(instruction.opcode())) {
                arrayAllocationCount++;
                continue;
            }
            if (!boundedNonThrowingOpcode(instruction.opcode())) {
                return false;
            }
        }
        return arrayAllocationCount == 1;
    }

    private static boolean supportedArrayAccessHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty()) {
            return false;
        }
        final String catchType = handler.catchType().orElseThrow();
        int arrayAccessCount = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (BytecodeSupport.isArrayReferenceAccess(instruction.opcode())) {
                if (arrayAccessCanThrowTo(instruction.opcode(), catchType)) {
                    arrayAccessCount++;
                }
                continue;
            }
            if (!boundedNonThrowingOpcode(instruction.opcode())) {
                return false;
            }
        }
        return arrayAccessCount == 1;
    }

    private static boolean arrayAccessCanThrowTo(final int opcode, final String catchType) {
        if (JdkCallSupport.isPlatformThrowableAssignable("java/lang/NullPointerException", catchType)) {
            return true;
        }
        return BytecodeSupport.isIndexedArrayAccess(opcode)
            && JdkCallSupport.isPlatformThrowableAssignable("java/lang/ArrayIndexOutOfBoundsException", catchType);
    }

    private static boolean supportedSingleApplicationThrowableTransportHandler(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler
    ) {
        if (handler.catchType().isEmpty() || !isThrowable(classes, handler.catchType().orElseThrow())) {
            return false;
        }
        final String catchType = handler.catchType().orElseThrow();
        int transportingCallCount = 0;
        for (final Instruction instruction : code.instructions()) {
            if (instruction.offset() < handler.startPc() || instruction.offset() >= handler.endPc()) {
                continue;
            }
            if (instruction.methodRef().isPresent()
                && classes.containsKey(instruction.methodRef().orElseThrow().owner())
                && supportedTransportedThrowableCall(classes, instruction, catchType)) {
                transportingCallCount++;
                continue;
            }
            if (!boundedNonThrowingOpcode(instruction.opcode())) {
                return false;
            }
        }
        return transportingCallCount == 1;
    }

    private static boolean isSupportedMathExactCall(final Instruction instruction) {
        if (instruction.opcode() != 184 || instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        if (!"java/lang/Math".equals(target.owner())) {
            return false;
        }
        if ("addExact".equals(target.name())
            && ("(II)I".equals(target.descriptor()) || "(JJ)J".equals(target.descriptor()))) {
            return true;
        }
        if ("subtractExact".equals(target.name())
            && ("(II)I".equals(target.descriptor()) || "(JJ)J".equals(target.descriptor()))) {
            return true;
        }
        if (("incrementExact".equals(target.name())
            || "decrementExact".equals(target.name())
            || "negateExact".equals(target.name()))
            && ("(I)I".equals(target.descriptor()) || "(J)J".equals(target.descriptor()))) {
            return true;
        }
        if ("toIntExact".equals(target.name()) && "(J)I".equals(target.descriptor())) {
            return true;
        }
        return "multiplyExact".equals(target.name())
            && ("(II)I".equals(target.descriptor())
                || "(JI)J".equals(target.descriptor())
                || "(JJ)J".equals(target.descriptor()));
    }

    private static boolean supportedMathExactProtectedInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode >= 2 && opcode <= 10) {
            return true;
        }
        if (opcode >= 16 && opcode <= 22) {
            return true;
        }
        if (opcode >= 26 && opcode <= 33) {
            return true;
        }
        return opcode == 88 || opcode == 133;
    }

    private static boolean supportedFinallyHandler(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final CodeAttribute code,
        final CodeException handler
    ) {
        if (handler.catchType().isPresent()) {
            return false;
        }
        final Optional<CaughtThrowableRethrowAnalysis.FinallyFlow> flow =
            CaughtThrowableRethrowAnalysis.analyzeFinally(code, handler);
        if (flow.isEmpty()) {
            return false;
        }
        final CaughtThrowableRethrowAnalysis.FinallyFlow result = flow.orElseThrow();
        for (final CaughtThrowableRethrowAnalysis.ReplacementThrow replacement : result.replacements()) {
            if (!isThrowable(classes, replacement.throwableType())) {
                return false;
            }
        }
        for (final int local : result.replacementLocals()) {
            if (!declaredThrowableParameter(classes, method, local)
                && !handlerThrowableLocal(classes, code, handler, local)) {
                return false;
            }
        }
        for (final int offset : result.replacementValueOffsets()) {
            if (!directThrowableValue(classes, code, offset)) {
                return false;
            }
        }
        final Optional<Instruction> first = instructionAtOffset(code, handler.handlerPc());
        final int throwableLocal = astoreLocalIndex(first.orElseThrow());
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
            if (!supportedExplicitThrowRangeInstruction(instruction)
                && !supportedApplicationThrowableInstruction(classes, instruction)
                && !supportedGeneratedThrowableCall(classes, instruction)
                && !supportedTransportedThrowableCall(classes, instruction, "java/lang/Throwable")
                && !supportedInterruptedWaitProtectedInstruction(instruction)
                && !supportedFinallyProtectedLocalInstruction(instruction)
                && !supportedFinallyProtectedControlInstruction(instruction)
                && !supportedFinallyCleanupInstruction(classes, instruction)
                && !supportedProtectedFinallyRethrowInstruction(classes, code, handler, throwableLocal, instruction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean supportedFinallyHandler(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler
    ) {
        final Optional<CaughtThrowableRethrowAnalysis.FinallyFlow> flow =
            CaughtThrowableRethrowAnalysis.analyzeFinally(code, handler);
        if (flow.isEmpty()) {
            return false;
        }
        final CaughtThrowableRethrowAnalysis.FinallyFlow result = flow.orElseThrow();
        if (!result.replacementValueOffsets().isEmpty()) {
            return false;
        }
        for (final int local : result.replacementLocals()) {
            if (!handlerThrowableLocal(classes, code, handler, local)) {
                return false;
            }
        }
        for (final CaughtThrowableRethrowAnalysis.ReplacementThrow replacement : result.replacements()) {
            if (!isThrowable(classes, replacement.throwableType())) {
                return false;
            }
        }
        return true;
    }

    private static boolean handlerThrowableLocal(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler,
        final int local
    ) {
        final Optional<String> type = code.objectLocalTypeAt(handler.handlerPc(), local);
        final Optional<String> handlerStack = code.singleStackObjectTypeAt(handler.handlerPc());
        return type.isPresent()
            && handlerStack.isPresent()
            && isThrowable(classes, type.orElseThrow())
            && isThrowable(classes, handlerStack.orElseThrow());
    }

    private static boolean declaredThrowableParameter(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final int local
    ) {
        int slot = method.isStatic() ? 0 : 1;
        final String descriptor = method.descriptor();
        for (int index = 1; descriptor.charAt(index) != ')';) {
            final int start = index;
            while (descriptor.charAt(index) == '[') {
                index++;
            }
            final char type = descriptor.charAt(index);
            if (type == 'L') {
                index = descriptor.indexOf(';', index);
                if (index < 0) {
                    return false;
                }
                index++;
            } else {
                index++;
            }
            if (slot == local) {
                return descriptor.charAt(start) == 'L'
                    && isThrowable(classes, descriptor.substring(start + 1, index - 1));
            }
            slot += type == 'J' || type == 'D' ? 2 : 1;
        }
        return false;
    }

    private static boolean directThrowableValue(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final int throwOffset
    ) {
        final List<Instruction> instructions = code.instructions();
        for (int index = 1; index < instructions.size(); index++) {
            if (instructions.get(index).offset() != throwOffset) {
                continue;
            }
            final Instruction source = instructions.get(index - 1);
            if (source.fieldRef().isPresent()) {
                return throwableDescriptor(classes, source.fieldRef().orElseThrow().descriptor());
            }
            if (source.methodRef().isPresent()) {
                final String descriptor = source.methodRef().orElseThrow().descriptor();
                return throwableDescriptor(classes, descriptor.substring(descriptor.indexOf(')') + 1));
            }
            return false;
        }
        return false;
    }

    private static boolean throwableDescriptor(final Map<String, ClassFile> classes, final String descriptor) {
        return throwableDescriptorType(classes, descriptor).isPresent();
    }

    private static Optional<String> throwableDescriptorType(
        final Map<String, ClassFile> classes,
        final String descriptor
    ) {
        if (descriptor.length() <= 2 || descriptor.charAt(0) != 'L'
            || descriptor.charAt(descriptor.length() - 1) != ';') {
            return Optional.empty();
        }
        final String type = descriptor.substring(1, descriptor.length() - 1);
        return isThrowable(classes, type) ? Optional.of(type) : Optional.empty();
    }

    private static boolean supportedFinallyProtectedLocalInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        return (opcode >= 21 && opcode <= 45) || (opcode >= 54 && opcode <= 78);
    }

    private static boolean supportedFinallyProtectedControlInstruction(final Instruction instruction) {
        final int opcode = instruction.opcode();
        return (opcode >= 153 && opcode <= 167) || opcode == 198 || opcode == 199 || opcode == 200;
    }

    private static boolean handlerRethrowsCaughtThrowable(
        final CodeAttribute code,
        final CodeException handler
    ) {
        return caughtThrowableRethrowOffset(code, handler).isPresent();
    }

    private static Optional<Integer> caughtThrowableRethrowOffset(
        final CodeAttribute code,
        final CodeException handler
    ) {
        return CaughtThrowableRethrowAnalysis.rethrowOffset(code, handler);
    }

    private static boolean supportedProtectedFinallyRethrowInstruction(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final Instruction instruction
    ) {
        for (final CodeException handler : code.exceptionTable()) {
            if (handler.catchType().isPresent() || !supportedFinallyHandler(classes, code, handler)) {
                continue;
            }
            final Optional<Instruction> first = instructionAtOffset(code, handler.handlerPc());
            if (first.isEmpty()) {
                continue;
            }
            final int throwableLocal = astoreLocalIndex(first.orElseThrow());
            if (throwableLocal < 0) {
                continue;
            }
            if (supportedProtectedFinallyRethrowInstruction(classes, code, handler, throwableLocal, instruction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportedProtectedFinallyRethrowInstruction(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler,
        final int throwableLocal,
        final Instruction instruction
    ) {
        final Optional<Integer> throwOffset = finallyThrowOffset(classes, code, handler, throwableLocal);
        if (throwOffset.isEmpty()) {
            return false;
        }
        if (instruction.offset() < handler.handlerPc() || instruction.offset() > throwOffset.orElseThrow()) {
            return false;
        }
        if (supportedFinallyProtectedLocalInstruction(instruction)) {
            return true;
        }
        if (supportedFinallyProtectedControlInstruction(instruction)) {
            return true;
        }
        if (instruction.opcode() == 191) {
            return true;
        }
        return supportedExplicitThrowRangeInstruction(instruction)
            || supportedApplicationThrowableInstruction(classes, instruction)
            || supportedInterruptedWaitProtectedInstruction(instruction)
            || supportedFinallyCleanupInstruction(classes, instruction)
            || supportedGeneratedThrowableCall(classes, instruction);
    }

    private static Optional<Integer> finallyThrowOffset(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler,
        final int throwableLocal
    ) {
        final Optional<Instruction> first = instructionAtOffset(code, handler.handlerPc());
        if (first.isEmpty() || astoreLocalIndex(first.orElseThrow()) != throwableLocal) {
            return Optional.empty();
        }
        final Optional<Integer> rethrow = caughtThrowableRethrowOffset(code, handler);
        return rethrow.isPresent()
            ? rethrow
            : supportedReplacementThrowOffset(classes, code, handler);
    }

    private static Optional<Integer> supportedReplacementThrowOffset(
        final Map<String, ClassFile> classes,
        final CodeAttribute code,
        final CodeException handler
    ) {
        final Optional<CaughtThrowableRethrowAnalysis.ReplacementThrow> replacement =
            CaughtThrowableRethrowAnalysis.replacementThrow(code, handler);
        if (replacement.isEmpty()
            || !isThrowable(classes, replacement.orElseThrow().throwableType())) {
            return Optional.empty();
        }
        return Optional.of(replacement.orElseThrow().offset());
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
        return threadSleepWaitSubject(instruction).isPresent();
    }

    private static Optional<String> threadSleepWaitSubject(final Instruction instruction) {
        if (instruction.opcode() != 184) {
            return Optional.empty();
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return Optional.empty();
        }
        final MethodRef target = methodRef.orElseThrow();
        if (!"java/lang/Thread".equals(target.owner()) || !"sleep".equals(target.name())) {
            return Optional.empty();
        }
        if ("(J)V".equals(target.descriptor())) {
            return Optional.of("Thread.sleep(long)");
        }
        if ("(JI)V".equals(target.descriptor())) {
            return Optional.of("Thread.sleep(long,int)");
        }
        return Optional.empty();
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
        return threadLifecycleSubject(instruction, lifecycleMethod).isPresent();
    }

    private static Optional<String> threadLifecycleSubject(final Instruction instruction, final String lifecycleMethod) {
        if (instruction.opcode() != 182) {
            return Optional.empty();
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isEmpty()) {
            return Optional.empty();
        }
        final MethodRef target = methodRef.orElseThrow();
        if (!"java/lang/Thread".equals(target.owner()) || !lifecycleMethod.equals(target.name())) {
            return Optional.empty();
        }
        if ("start".equals(lifecycleMethod) && "()V".equals(target.descriptor())) {
            return Optional.of("Thread.start()");
        }
        if ("join".equals(lifecycleMethod)) {
            if ("()V".equals(target.descriptor())) {
                return Optional.of("Thread.join()");
            }
            if ("(J)V".equals(target.descriptor())) {
                return Optional.of("Thread.join(long)");
            }
            if ("(JI)V".equals(target.descriptor())) {
                return Optional.of("Thread.join(long,int)");
            }
            if ("(Ljava/time/Duration;)Z".equals(target.descriptor())) {
                return Optional.of("Thread.join(Duration)");
            }
        }
        return Optional.empty();
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
        if (opcode == 1) {
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
        if (opcode == 87 || opcode == 88) {
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
        if (instruction.opcode() >= 21 && instruction.opcode() <= 45) {
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
                        || "(D)V".equals(target.descriptor())
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

    private static boolean supportedFinallyCleanupInstruction(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        if (instruction.opcode() == 187 && instruction.className().isPresent()) {
            return classes.containsKey(instruction.className().orElseThrow());
        }
        if (instruction.opcode() == 183 && instruction.methodRef().isPresent()) {
            final MethodRef target = instruction.methodRef().orElseThrow();
            return classes.containsKey(target.owner()) && "<init>".equals(target.name());
        }
        if ((instruction.opcode() == 178 || instruction.opcode() == 179
            || instruction.opcode() == 180 || instruction.opcode() == 181)
            && instruction.fieldRef().isPresent()
            && classes.containsKey(instruction.fieldRef().orElseThrow().owner())) {
            return true;
        }
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
        if (instruction.opcode() == 184) {
            return threadSleepWaitSubject(instruction).isPresent();
        }
        if (instruction.opcode() == 182) {
            final Optional<MethodRef> methodRef = instruction.methodRef();
            if (methodRef.isEmpty()) {
                return false;
            }
            final MethodRef target = methodRef.orElseThrow();
            if (isObjectWaitMethod(target)) {
                return true;
            }
            return threadJoinWaitSubject(instruction).isPresent();
        }
        return false;
    }

    private static Optional<String> threadJoinWaitSubject(final Instruction instruction) {
        return threadLifecycleSubject(instruction, "join");
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
        if (isSupportedDirectScheduledThreadPoolExecutorFlow(classes, instructions, instructionIndex, methodRef)) {
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
            return !isSupportedThreadLocalRuntimeCall(methodRef);
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
            || isThreadBuilderVirtualInheritInheritableThreadLocals(methodRef)
            || isThreadBuilderVirtualFactory(methodRef);
    }

    private static boolean isSupportedThreadLocalRuntimeCall(final MethodRef methodRef) {
        if (!"java/lang/ThreadLocal".equals(methodRef.owner())
            && !"java/lang/InheritableThreadLocal".equals(methodRef.owner())) {
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
        if (isThreadBuilderVirtualInheritInheritableThreadLocals(methodRef)) {
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
        if (isExecutorExecute(methodRef) || isExecutorServiceSubmit(methodRef)) {
            return supportsVirtualThreadExecutorTaskSubmission(classes, instructions, instructionIndex);
        }
        if (isExecutorServiceShutdown(methodRef)
            || isExecutorServiceShutdownNow(methodRef)
            || isExecutorServiceAwaitTermination(methodRef)
            || isExecutorServiceClose(methodRef)) {
            return supportedVirtualThreadExecutorReceiver(classes, instructions, instructionIndex);
        }
        if (isFutureCancel(methodRef) || isFutureIsDone(methodRef) || isFutureIsCancelled(methodRef)) {
            return supportedVirtualThreadFutureReceiver(classes, instructions, instructionIndex, methodRef);
        }
        if (isVirtualThreadExecutorObservationMethod(methodRef)) {
            return supportedVirtualThreadExecutorObservationReceiver(classes, instructions, instructionIndex, methodRef);
        }
        return false;
    }

    private static boolean isSupportedDirectScheduledThreadPoolExecutorFlow(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        if (isExecutorServiceShutdown(methodRef)
            || isExecutorServiceShutdownNow(methodRef)
            || isExecutorServiceAwaitTermination(methodRef)) {
            return supportedScheduledThreadPoolExecutorReceiver(classes, instructions, instructionIndex, methodRef);
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
        if (isThreadBuilderVirtualInheritInheritableThreadLocals(rootMethodRef.orElseThrow())) {
            return supportedVirtualThreadBuilderProducer(classes, instructions, rootProducerIndex - 2, -1);
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

    private static boolean supportsVirtualThreadExecutorTaskSubmission(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex
    ) {
        if (instructionIndex < 1 || instructionIndex >= instructions.size()) {
            return false;
        }
        final Optional<MethodRef> executeRef = instructions.get(instructionIndex).methodRef();
        if (executeRef.isEmpty()
            || (!isExecutorExecute(executeRef.orElseThrow()) && !isExecutorServiceSubmit(executeRef.orElseThrow()))) {
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
            if (isThreadBuilderVirtualInheritInheritableThreadLocals(methodRef.orElseThrow())) {
                return supportedVirtualThreadBuilderProducer(
                    classes,
                    instructions,
                    transparentProducerIndex - 2,
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
        if (methodRef.isEmpty()
            || (!isThreadBuilderVirtualName(methodRef.orElseThrow())
            && !isThreadBuilderVirtualInheritInheritableThreadLocals(methodRef.orElseThrow()))) {
            return false;
        }
        final int receiverIndex = isThreadBuilderVirtualName(methodRef.orElseThrow())
            ? rootProducerIndex - virtualThreadBuilderNameProducerOffset(methodRef.orElseThrow())
            : rootProducerIndex - 2;
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
        if (producer.opcode() == 178
            && producer.fieldRef().isPresent()
            && supportedVirtualThreadFactoryStaticField(
                classes,
                instructions,
                transparentProducerIndex,
                producer.fieldRef().orElseThrow(),
                rootProducerIndex
            )) {
            return true;
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
        if (methodRef.isPresent()) {
            final MethodRef target = methodRef.orElseThrow();
            if (isExecutorServiceShutdown(target)
                || isExecutorServiceShutdownNow(target)
                || isExecutorServiceClose(target)) {
                return supportedVirtualThreadExecutorProducer(classes, instructions, instructionIndex - 1);
            }
            if (isExecutorServiceAwaitTermination(target)) {
                return instructionIndex >= 3
                    && supportedVirtualThreadExecutorProducer(classes, instructions, instructionIndex - 3);
            }
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

    private static boolean supportedVirtualThreadFutureReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        final int receiverIndex;
        if (isFutureCancel(methodRef)) {
            receiverIndex = instructionIndex - 2;
        } else if (isFutureIsDone(methodRef) || isFutureIsCancelled(methodRef)) {
            receiverIndex = instructionIndex - 1;
        } else {
            return false;
        }
        if (receiverIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFutureProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedScheduledThreadPoolExecutorReceiver(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        final int receiverIndex;
        if (isExecutorServiceShutdown(methodRef) || isExecutorServiceShutdownNow(methodRef)) {
            receiverIndex = instructionIndex - 1;
        } else if (isExecutorServiceAwaitTermination(methodRef)) {
            receiverIndex = instructionIndex - 3;
        } else {
            return false;
        }
        if (receiverIndex < 0) {
            return false;
        }
        return supportedScheduledThreadPoolExecutorProducer(classes, instructions, receiverIndex);
    }

    private static boolean supportedScheduledThreadPoolExecutorProducer(
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
        if (methodRef.isPresent()
            && producer.opcode() == 183
            && isSupportedScheduledThreadPoolExecutorConstructor(classes, methodRef.orElseThrow())
            && supportedScheduledThreadPoolExecutorConstruction(classes, instructions, transparentProducerIndex)) {
            return true;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedScheduledThreadPoolExecutorProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedScheduledThreadPoolExecutorConstruction(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int constructorIndex
    ) {
        int sawDuplicateReceiver = 0;
        for (int index = constructorIndex - 1; index >= 0; index--) {
            final Instruction candidate = instructions.get(index);
            if (candidate.opcode() == 89) {
                sawDuplicateReceiver = 1;
                continue;
            }
            if (candidate.opcode() == 187) {
                return sawDuplicateReceiver == 1
                    && isAssignableTo(classes, candidate.className().orElse(""), "java/util/concurrent/ScheduledThreadPoolExecutor");
            }
        }
        return false;
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
        if (producer.opcode() == 178
            && producer.fieldRef().isPresent()
            && supportedVirtualThreadExecutorStaticField(
                classes,
                instructions,
                transparentProducerIndex,
                producer.fieldRef().orElseThrow()
            )) {
            return true;
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

    private static boolean supportedVirtualThreadFutureProducer(
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
        if (methodRef.isPresent()
            && isExecutorServiceSubmit(methodRef.orElseThrow())
            && supportsVirtualThreadExecutorTaskSubmission(classes, instructions, transparentProducerIndex)) {
            return true;
        }
        if (methodRef.isPresent()
            && (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorSchedule(methodRef.orElseThrow())
                || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleAtFixedRate(methodRef.orElseThrow())
                || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleWithFixedDelay(methodRef.orElseThrow())
                || VirtualThreadInvokePatterns.isScheduledExecutorServiceSchedule(methodRef.orElseThrow())
                || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleAtFixedRate(methodRef.orElseThrow())
                || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleWithFixedDelay(methodRef.orElseThrow()))
            && supportedScheduledFutureProducer(classes, instructions, transparentProducerIndex, methodRef.orElseThrow())) {
            return true;
        }
        final int loadSlot = localLoadSlot(producer);
        if (loadSlot < 0) {
            return false;
        }
        final int storeIndex = VirtualThreadInvokePatterns.previousLocalStoreIndex(instructions, transparentProducerIndex - 1, loadSlot);
        if (storeIndex < 0) {
            return false;
        }
        return supportedVirtualThreadFutureProducer(classes, instructions, storeIndex - 1);
    }

    private static boolean supportedScheduledFutureProducer(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int instructionIndex,
        final MethodRef methodRef
    ) {
        final int runnableProducerIndex;
        if (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorSchedule(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceSchedule(methodRef)) {
            runnableProducerIndex = instructionIndex - 3;
        } else if (VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleAtFixedRate(methodRef)
            || VirtualThreadInvokePatterns.isScheduledThreadPoolExecutorScheduleWithFixedDelay(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleAtFixedRate(methodRef)
            || VirtualThreadInvokePatterns.isScheduledExecutorServiceScheduleWithFixedDelay(methodRef)) {
            runnableProducerIndex = instructionIndex - 4;
        } else {
            return false;
        }
        final int runnableWidth = VirtualThreadInvokePatterns.runnableProducerInstructionWidth(instructions, runnableProducerIndex);
        final int receiverIndex = runnableProducerIndex - runnableWidth;
        if (receiverIndex < 0) {
            return false;
        }
        return supportedScheduledThreadPoolExecutorProducer(classes, instructions, receiverIndex)
            && supportedRunnableProducer(classes, instructions, runnableProducerIndex);
    }

    private static boolean supportedVirtualThreadFactoryStaticField(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int loadIndex,
        final FieldRef fieldRef,
        final int rootProducerIndex
    ) {
        if (!"Ljava/util/concurrent/ThreadFactory;".equals(fieldRef.descriptor())) {
            return false;
        }
        for (int index = loadIndex - 1; index >= 0; index--) {
            final Instruction candidate = instructions.get(index);
            if (candidate.opcode() != 179 || candidate.fieldRef().isEmpty() || !fieldRef.equals(candidate.fieldRef().orElseThrow())) {
                continue;
            }
            return supportedVirtualThreadFactoryProducer(classes, instructions, index - 1, rootProducerIndex);
        }
        return false;
    }

    private static boolean supportedVirtualThreadExecutorStaticField(
        final Map<String, ClassFile> classes,
        final List<Instruction> instructions,
        final int loadIndex,
        final FieldRef fieldRef
    ) {
        if (!"Ljava/util/concurrent/ExecutorService;".equals(fieldRef.descriptor())) {
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

    private static boolean isThreadBuilderVirtualInheritInheritableThreadLocals(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isThreadBuilderOfVirtualInheritInheritableThreadLocals(methodRef);
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

    private static boolean isExecutorServiceSubmit(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceSubmit(methodRef);
    }

    private static boolean isExecutorServiceShutdown(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceShutdown(methodRef);
    }

    private static boolean isExecutorServiceClose(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceClose(methodRef);
    }

    private static boolean isExecutorServiceAwaitTermination(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceAwaitTermination(methodRef);
    }

    private static boolean isExecutorServiceShutdownNow(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isExecutorServiceShutdownNow(methodRef);
    }

    private static boolean isFutureCancel(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isFutureCancel(methodRef);
    }

    private static boolean isFutureIsDone(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isFutureIsDone(methodRef);
    }

    private static boolean isFutureIsCancelled(final MethodRef methodRef) {
        return VirtualThreadInvokePatterns.isFutureIsCancelled(methodRef);
    }

    private static boolean isScheduledThreadPoolExecutorConstructor(final MethodRef methodRef) {
        if (!"java/util/concurrent/ScheduledThreadPoolExecutor".equals(methodRef.owner())) {
            return false;
        }
        if (!"<init>".equals(methodRef.name())) {
            return false;
        }
        return "(I)V".equals(methodRef.descriptor())
            || "(ILjava/util/concurrent/ThreadFactory;Ljava/util/concurrent/RejectedExecutionHandler;)V"
            .equals(methodRef.descriptor());
    }

    private static boolean isSupportedScheduledThreadPoolExecutorConstructor(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef
    ) {
        if (isScheduledThreadPoolExecutorConstructor(methodRef)) {
            return true;
        }
        return "<init>".equals(methodRef.name())
            && isAssignableTo(classes, methodRef.owner(), "java/util/concurrent/ScheduledThreadPoolExecutor");
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
            if ("inheritInheritableThreadLocals".equals(methodRef.name())) {
                return "Thread.Builder.inheritInheritableThreadLocals(boolean)";
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
            if ("inheritInheritableThreadLocals".equals(methodRef.name())) {
                return "Thread.Builder.OfVirtual.inheritInheritableThreadLocals(boolean)";
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
            if ("submit".equals(methodRef.name())) {
                return "ExecutorService.submit(Runnable)";
            }
            if ("shutdown".equals(methodRef.name())) {
                return "ExecutorService.shutdown()";
            }
            if ("awaitTermination".equals(methodRef.name())) {
                return "ExecutorService.awaitTermination(long,TimeUnit)";
            }
            if ("shutdownNow".equals(methodRef.name())) {
                return "ExecutorService.shutdownNow()";
            }
            if ("close".equals(methodRef.name())) {
                return "ExecutorService.close()";
            }
        }
        if ("java/util/concurrent/Future".equals(owner)) {
            if ("cancel".equals(methodRef.name())) {
                return "Future.cancel(boolean)";
            }
            if ("isDone".equals(methodRef.name())) {
                return "Future.isDone()";
            }
            if ("isCancelled".equals(methodRef.name())) {
                return "Future.isCancelled()";
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
        if (JdkCallSupport.isPlatformThrowableCauseConstructor(methodRef)) {
            return JdkCallSupport.isMatchExceptionCauseConstructor(methodRef);
        }
        if ("()V".equals(methodRef.descriptor())) {
            return true;
        }
        return "(Ljava/lang/String;)V".equals(methodRef.descriptor());
    }

    private static boolean unsupportedCustomThrowableCauseConstructor(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef target
    ) {
        return "<init>".equals(method.name())
            && JdkCallSupport.isPlatformThrowable(classFile.superName())
            && JdkCallSupport.isPlatformThrowableCauseConstructor(target);
    }

    private static boolean isPlatformThrowable(final String owner) {
        return JdkCallSupport.isPlatformThrowable(owner);
    }

    private static boolean isThrowable(final Map<String, ClassFile> classes, final String owner) {
        return JdkCallSupport.isPlatformThrowable(owner)
            || classes.containsKey(owner) && isThrowableAssignable(classes, owner, "java/lang/Throwable");
    }

    private static boolean isThrowableAssignable(
        final Map<String, ClassFile> classes,
        final String candidate,
        final String expected
    ) {
        String current = candidate;
        final Set<String> visited = new HashSet<>();
        while (current != null && !current.isEmpty() && visited.add(current)) {
            if (JdkCallSupport.isPlatformThrowableAssignable(current, expected)
                || current.equals(expected)) {
                return true;
            }
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                return false;
            }
            current = classFile.superName();
        }
        return false;
    }

    private static boolean supportedApplicationThrowableInstruction(
        final Map<String, ClassFile> classes,
        final Instruction instruction
    ) {
        if (instruction.opcode() == 187 && instruction.className().isPresent()) {
            return classes.containsKey(instruction.className().orElseThrow())
                && isThrowable(classes, instruction.className().orElseThrow());
        }
        if (instruction.opcode() != 183 || instruction.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = instruction.methodRef().orElseThrow();
        return "<init>".equals(target.name())
            && classes.containsKey(target.owner())
            && isThrowable(classes, target.owner());
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

    private static boolean unsupportedCheckcastTarget(final Instruction instruction) {
        return instruction.opcode() == 192
            && instruction.className().isPresent()
            && ("java/util/Locale".equals(instruction.className().orElseThrow())
            || "java/nio/charset/Charset".equals(instruction.className().orElseThrow()));
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
        if (JdkCallSupport.builtinInstanceOfTargetId(target).isPresent()) {
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
        if ("java/lang/Boolean".equals(target)) {
            return true;
        }
        if ("java/lang/Byte".equals(target)) {
            return true;
        }
        if ("java/lang/Short".equals(target)) {
            return true;
        }
        return "java/lang/Character".equals(target);
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

    private static boolean unsupportedInvokedynamic(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
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
        if (supportedLambdaMetafactory(classes, method, instruction, dynamicRef.orElseThrow())) {
            return false;
        }
        if (supportedRecordObjectMethodsDynamic(classes, classFile, method, instruction)) {
            return false;
        }
        return true;
    }

    private static boolean supportedRecordObjectMethodsDynamic(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        final Optional<DynamicRef> dynamicRef = instruction.dynamicRef();
        if (dynamicRef.isEmpty()) {
            return false;
        }
        return recordObjectMethodsCall(classFile, method, instruction).isPresent()
            && unsupportedRecordComponentDescriptor(classes, classFile, method, instruction).isEmpty();
    }

    private static Optional<String> unsupportedRecordComponentDescriptor(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        if (instruction.opcode() != 186 || instruction.dynamicRef().isEmpty()) {
            return Optional.empty();
        }
        final Optional<RecordObjectMethodsCall> recordCall =
            recordObjectMethodsCall(classFile, method, instruction);
        return recordCall.isEmpty()
            ? Optional.empty()
            : unsupportedRecordComponentDescriptor(
                classes,
                recordCall.orElseThrow(),
                "hashCode".equals(method.name())
            );
    }

    private static Optional<RecordObjectMethodsCall> recordObjectMethodsCall(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction
    ) {
        final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow();
        final Optional<RecordObjectMethodsCall> equalsCall =
            RecordObjectMethodsCall.resolve(classFile, method, dynamicRef);
        return equalsCall.isPresent()
            ? equalsCall
            : RecordObjectMethodsCall.resolveHashCode(classFile, method, dynamicRef);
    }

    private static Optional<String> unsupportedRecordComponentDescriptor(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall recordCall,
        final boolean hashCode
    ) {
        for (final RecordObjectMethodsCall.Component component : recordCall.components()) {
            if (!supportedRecordComponentShape(classes, component.shape(), hashCode)) {
                return Optional.of(component.diagnosticType());
            }
        }
        return Optional.empty();
    }

    private static boolean supportedRecordComponentShape(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final boolean hashCode
    ) {
        return supportedRecordComponentShape(classes, shape, hashCode, true);
    }

    private static boolean supportedRecordComponentShape(
        final Map<String, ClassFile> classes,
        final RecordObjectMethodsCall.Shape shape,
        final boolean hashCode,
        final boolean directRecordComponent
    ) {
        if (!shape.valid()) {
            return false;
        }
        if (shape.isArray() || shape.referenceOwner().isEmpty()) {
            return true;
        }
        if (shape.isStringMap()) {
            return directRecordComponent;
        }
        if (shape.isList()) {
            return supportedRecordComponentShape(
                classes,
                shape.listElement().orElseThrow(),
                hashCode,
                false
            );
        }
        final String owner = shape.referenceOwner().orElseThrow();
        if ("java/lang/String".equals(owner) || isRecordBoxedPrimitive(owner)) {
            return true;
        }
        if (directRecordComponent) {
            return RecordObjectMethodsCall.directReferencePlan(classes, shape, hashCode).isPresent();
        }
        final ClassFile componentClass = classes.get(owner);
        return componentClass != null
            && !componentClass.isInterface()
            && componentClass.isFinal()
            && hasSupportedRecordReferenceObjectMethod(classes, componentClass, hashCode);
    }

    private static boolean isRecordBoxedPrimitive(final String owner) {
        return "java/lang/Boolean".equals(owner)
            || "java/lang/Byte".equals(owner)
            || "java/lang/Character".equals(owner)
            || "java/lang/Short".equals(owner)
            || "java/lang/Integer".equals(owner)
            || "java/lang/Long".equals(owner)
            || "java/lang/Float".equals(owner)
            || "java/lang/Double".equals(owner);
    }

    private static boolean hasSupportedRecordReferenceObjectMethod(
        final Map<String, ClassFile> classes,
        final ClassFile componentClass,
        final boolean hashCode
    ) {
        if (componentClass.isEnum()) {
            return true;
        }
        final String methodName = hashCode ? "hashCode" : "equals";
        final String descriptor = hashCode ? "()I" : "(Ljava/lang/Object;)Z";
        String current = componentClass.name();
        final Set<String> visited = new HashSet<>();
        while (classes.containsKey(current) && visited.add(current)) {
            if ("java/lang/Object".equals(current)) {
                return true;
            }
            final ClassFile currentClass = classes.get(current);
            final Optional<MethodInfo> target = currentClass.method(methodName, descriptor);
            if (target.isPresent()) {
                return !target.orElseThrow().isStatic()
                    && target.orElseThrow().code().isPresent();
            }
            current = currentClass.superName();
        }
        return "java/lang/Object".equals(current);
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

    private static boolean supportedLambdaMetafactory(
        final Map<String, ClassFile> classes,
        final MethodInfo method,
        final Instruction instruction,
        final DynamicRef dynamicRef
    ) {
        final Optional<LambdaMetafactoryCall> lambdaCall = LambdaMetafactoryCall.resolve(dynamicRef);
        if (lambdaCall.isEmpty()) {
            return false;
        }
        final LambdaMetafactoryCall lambda = lambdaCall.orElseThrow();
        if (lambda.isFunction()
            && FunctionLambdaUse.hasUnsupportedDupX2Use(lambda, method, instruction)) {
            return false;
        }
        if (lambda.isFunction()
            && FunctionLambdaUse.requiresMaterialization(method, instruction)
            && !FunctionLambdaUse.isProvablyDiscardedZeroCapture(lambda, method, instruction)) {
            return lambda.isMaterializedFunctionLambda(classes);
        }
        if (lambda.isSupplier()) {
            final ClassFile implementationClass = classes.get(lambda.implementation().owner());
            if (implementationClass == null || !implementationClass.application()) {
                return false;
            }
        }
        return lambda.isDirectlyLowerable(classes)
            || lambda.isZeroCaptureMaterializedObjectLambda()
            || lambda.isZeroCaptureMaterializedLongObjectLambda(classes)
            || lambda.isMaterializedCapturedLongObjectLambda(classes)
            || lambda.isZeroCaptureMaterializedBooleanLambda()
            || lambda.isMaterializedBiFunctionLambda()
            || lambda.isMaterializedVoidLambda()
            || lambda.isMaterializedSupplierLambda()
            || lambda.isMaterializedBoundCustomObjectLambda(classes);
    }

    private static boolean supportedStringConcatParameters(final String descriptor) {
        if (!descriptor.startsWith("(")) {
            return false;
        }
        return supportedStringConcatParameters(descriptor, 1);
    }

    private static boolean supportedStringConcatParameters(final String descriptor, final int index) {
        if (index >= descriptor.length()) {
            return false;
        }
        if (descriptor.charAt(index) == ')') {
            return true;
        }
        final int next = supportedStringConcatParameterEnd(descriptor, index);
        if (next < 0) {
            return false;
        }
        return supportedStringConcatParameters(descriptor, next);
    }

    private static int supportedStringConcatParameterEnd(final String descriptor, final int index) {
        final char type = descriptor.charAt(index);
        if ("BCDFIJSZ".indexOf(type) >= 0) {
            return index + 1;
        }
        if (type == 'L') {
            final int end = descriptor.indexOf(';', index);
            return end < 0 ? -1 : end + 1;
        }
        if (type != '[') {
            return -1;
        }
        return supportedStringConcatArrayEnd(descriptor, index + 1);
    }

    private static int supportedStringConcatArrayEnd(final String descriptor, final int index) {
        if (index >= descriptor.length()) {
            return -1;
        }
        if (descriptor.charAt(index) == '[') {
            return supportedStringConcatArrayEnd(descriptor, index + 1);
        }
        return supportedStringConcatParameterEnd(descriptor, index);
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
        final boolean resourceUrl = resourceUrlLookup(methodRef);
        final String reason = resourceUrl
            ? "URL-shaped resource lookup is not supported by the closed-world native resource table."
            : "This reachable JDK method has no native intrinsic, substitution, or supported runtime model yet.";
        final String fix = resourceUrl
            ? "Use Class.getResourceAsStream(String), ClassLoader.getResourceAsStream(String), or ClassLoader.getSystemResourceAsStream(String)."
            : "Use a currently supported intrinsic or add a deterministic JDK substitution before native code generation.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN031", "unsupported reachable JDK call", methodRef.display(), reason, fix);
        }
        return warning(classFile, method, "JAVAN131", "unsupported JDK call in unreachable code", methodRef.display(), reason, fix);
    }

    private static boolean resourceUrlLookup(final MethodRef methodRef) {
        return ("java/lang/Class".equals(methodRef.owner()) || "java/lang/ClassLoader".equals(methodRef.owner()))
            && ("getResource".equals(methodRef.name()) || "getResources".equals(methodRef.name()));
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
        final String reason =
            "Only direct explicit athrow ranges or generated calls that transport platform exceptions are supported.";
        final String fix =
            "Keep try/catch limited to supported platform exception types and generated calls without monitor-held exits.";
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
        final String support =
            "Only StringConcatFactory string concatenation, exact record ObjectMethods equals/hashCode, exact LambdaMetafactory Function/Predicate shapes, "
                + "the exact Supplier subset (zero-argument reference-return invocation directly lowered to admitted application-static "
                + "implementations or final implementation-owner bound instance targets, plus application static/instance-target "
                + "materialization with reference-only captures and reference "
                + "returns), the current "
                + "Consumer/BiConsumer object-capture materialization slice, and the current custom-SAM materialization subset are implemented.";
        final String reason = invokedynamicReason(instruction, support);
        final String fix =
            "Keep invokedynamic limited to supported javac string concatenation, exact supported record equals/hashCode, or the admitted LambdaMetafactory subset.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN030", "unsupported reachable bytecode", instruction.mnemonic(), reason, fix);
        }
        return warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", instruction.mnemonic(), reason, fix);
    }

    private static String invokedynamicReason(final Instruction instruction, final String support) {
        if (instruction.dynamicRef().isEmpty()) {
            return support;
        }
        final DynamicRef ref = instruction.dynamicRef().orElseThrow();
        final StringBuilder reason = new StringBuilder(support)
            .append(" Observed bootstrap: ")
            .append(ref.bootstrapOwner())
            .append('.')
            .append(ref.bootstrapName())
            .append(ref.descriptor())
            .append(", arguments=")
            .append(ref.bootstrapArguments().size());
        if (isStringConcatWithConstants(ref) && !ref.bootstrapArguments().isEmpty()) {
            reason.append(", recipeLength=")
                .append(ref.bootstrapArguments().getFirst().length())
                .append(", constantPlaceholder=")
                .append(ref.bootstrapArguments().getFirst().indexOf(2) >= 0);
        }
        return reason.append('.').toString();
    }

    private static boolean isStringConcatWithConstants(final DynamicRef ref) {
        return "java/lang/invoke/StringConcatFactory".equals(ref.bootstrapOwner())
            && "makeConcatWithConstants".equals(ref.bootstrapName());
    }

    private static Diagnostic recordComponentDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final String descriptor
    ) {
        return error(
            classFile,
            method,
            "JAVAN030",
            "unsupported record component type",
            descriptor,
            "Record equals/hashCode only admits closed reference shapes with complete native semantics.",
            "Use String, a boxed primitive, an array, an exact List/ArrayList element shape, exact Map<String, String>, an enum, a final closed-world class with a reachable equals/hashCode implementation, or a direct sealed interface with a complete supported final non-enum permitted set."
        );
    }

    private static Diagnostic checkcastTargetDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String target = instruction.className().orElse("unknown");
        final String reason = "The current runtime cannot perform a deterministic checkcast to this built-in singleton type or transport the required ClassCastException.";
        final String fix = "Keep built-in singleton values statically typed and pass the supported constants directly.";
        if (reachable == 1) {
            return error(classFile, method, "JAVAN045", "unsupported checkcast target", target, reason, fix);
        }
        return warning(classFile, method, "JAVAN145", "unsupported checkcast target in unreachable code", target, reason, fix);
    }

    private static Diagnostic instanceOfTargetDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final int reachable
    ) {
        final String target = instruction.className().orElse("unknown");
        final String reason = "The current runtime only has deterministic instanceof support for application classes, supported boxed primitive wrappers, primitive arrays, Object[], and the built-in Collection/Map runtime objects.";
        final String fix = "Keep instanceof targets to application classes/interfaces, Object, primitive arrays, Object[], supported wrappers, or the currently admitted Collection/Map runtime targets.";
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
