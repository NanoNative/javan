package javan.verify;

import javan.analysis.EntryPoint;
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
        final Optional<CodeAttribute> code = method.code();
        if (code.isPresent()) {
            final CodeAttribute methodCode = code.orElseThrow();
            if (unsupportedExceptionHandlers(classes, methodCode) && !supportedSyntheticSwitchMapClass(classFile, method, methodCode)) {
                diagnostics.add(exceptionHandlerDiagnostic(classFile, method, methodCode.exceptionTableLength(), reachable));
            }
            final int application = classFile.application() ? 1 : 0;
            final int unsupportedStringConstant = containsUnsupportedRuntimeStringConstant(methodCode) ? 1 : 0;
            for (final Instruction instruction : methodCode.instructions()) {
                diagnostics.addAll(verifyInstruction(
                    classes,
                    classFile,
                    method,
                    instruction,
                    reachable,
                    application,
                    unsupportedStringConstant
                ));
            }
        }
        return diagnostics;
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
        final Instruction instruction,
        final int reachable,
        final int application,
        final int unsupportedStringConstant
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (reachable == 0 && application == 0) {
            return diagnostics;
        }
        final Optional<MethodRef> methodRef = instruction.methodRef();
        if (methodRef.isPresent()) {
            final MethodRef target = methodRef.orElseThrow();
            final Optional<String> forbiddenReason = forbiddenApiRules.forbiddenReason(target);
            if (forbiddenReason.isPresent()) {
                diagnostics.add(apiDiagnostic(classFile, method, target, forbiddenReason.orElseThrow(), reachable));
            }
            if (NetworkApiSupport.isNetworkCall(target) && !JdkCallSupport.isSupported(target)) {
                diagnostics.add(networkCallDiagnostic(classFile, method, target, reachable));
            } else if (unsupportedJdkCall(target) && !ignoredGeneratedEnumValueOfCall(classFile, method, target, reachable)) {
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
