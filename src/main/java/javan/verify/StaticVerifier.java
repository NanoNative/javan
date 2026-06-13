package javan.verify;

import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.CodeAttribute;
import javan.classfile.CodeException;
import javan.classfile.DynamicRef;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.BytecodeSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public List<Diagnostic> verify(final Map<String, ClassFile> classes, final Set<EntryPoint> reachable) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        for (final ClassFile classFile : classes.values()) {
            for (final MethodInfo method : classFile.methods()) {
                final boolean isReachable = reachable.contains(new EntryPoint(classFile.name(), method.name(), method.descriptor()));
                diagnostics.addAll(verifyMethod(classFile, method, isReachable));
            }
        }
        return List.copyOf(diagnostics);
    }

    private List<Diagnostic> verifyMethod(final ClassFile classFile, final MethodInfo method, final boolean reachable) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (reachable && method.isNative()) {
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
        method.code().ifPresent(code -> {
            if (unsupportedExceptionHandlers(code)) {
                diagnostics.add(exceptionHandlerDiagnostic(classFile, method, code.exceptionTableLength(), reachable));
            }
            code.instructions().forEach(instruction ->
                diagnostics.addAll(verifyInstruction(classFile, method, instruction, reachable, classFile.application()))
            );
        });
        return diagnostics;
    }

    private List<Diagnostic> verifyInstruction(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final boolean reachable,
        final boolean application
    ) {
        final List<Diagnostic> diagnostics = new ArrayList<>();
        if (!reachable && !application) {
            return diagnostics;
        }
        instruction.methodRef().flatMap(forbiddenApiRules::forbiddenReason).ifPresent(reason ->
            diagnostics.add(apiDiagnostic(classFile, method, instruction.methodRef().orElseThrow(), reason, reachable))
        );
        if (unsupportedNewArrayType(instruction)) {
            diagnostics.add(newArrayDiagnostic(classFile, method, instruction, reachable));
        }
        if (unsupportedInvokedynamic(instruction)) {
            diagnostics.add(invokedynamicDiagnostic(classFile, method, instruction, reachable));
        }
        if (BytecodeSupport.classify(instruction.opcode()) != BytecodeSupport.Status.NATIVE_SUPPORTED) {
            diagnostics.add(opcodeDiagnostic(classFile, method, instruction, reachable));
        }
        return diagnostics;
    }

    private static boolean unsupportedNewArrayType(final Instruction instruction) {
        return instruction.opcode() == 188 && (instruction.operands().length == 0 || !supportedNewArrayType(instruction.operands()[0] & 0xFF));
    }

    private static boolean unsupportedExceptionHandlers(final CodeAttribute code) {
        return code.exceptionTableLength() > 0 && !code.exceptionTable().stream().allMatch(handler -> supportedExceptionHandler(code, handler));
    }

    private static boolean supportedExceptionHandler(final CodeAttribute code, final CodeException handler) {
        if (handler.catchType().isEmpty() || !isPlatformThrowable(handler.catchType().orElseThrow())) {
            return false;
        }
        final List<Instruction> protectedInstructions = code.instructions().stream()
            .filter(instruction -> instruction.offset() >= handler.startPc() && instruction.offset() < handler.endPc())
            .toList();
        return protectedInstructions.stream().anyMatch(instruction -> instruction.opcode() == 191)
            && protectedInstructions.stream().allMatch(StaticVerifier::supportedExplicitThrowRangeInstruction);
    }

    private static boolean supportedExplicitThrowRangeInstruction(final Instruction instruction) {
        if (instruction.opcode() == 183) {
            return instruction.methodRef().filter(StaticVerifier::isSupportedExceptionConstructor).isPresent();
        }
        return switch (instruction.opcode()) {
            case 0, 18, 19, 20, 87, 89, 187, 191 -> true;
            default -> false;
        };
    }

    private static boolean isSupportedExceptionConstructor(final MethodRef methodRef) {
        return "<init>".equals(methodRef.name()) && isPlatformThrowable(methodRef.owner());
    }

    private static boolean isPlatformThrowable(final String owner) {
        return "java/lang/Throwable".equals(owner) || owner.endsWith("Exception") || owner.endsWith("Error");
    }

    private static boolean unsupportedInvokedynamic(final Instruction instruction) {
        return instruction.opcode() == 186 && instruction.dynamicRef().filter(StaticVerifier::supportedStringConcat).isEmpty();
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
        return index < descriptor.length() && descriptor.charAt(index) == ')';
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
            return end < 0 ? -1 : end + 1;
        }
        return -1;
    }

    private static boolean supportedNewArrayType(final int atype) {
        return atype >= 4 && atype <= 11;
    }

    private static Diagnostic apiDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final MethodRef methodRef,
        final String reason,
        final boolean reachable
    ) {
        final String fix = "Use direct static references or a future build-time registry.";
        return reachable
            ? error(classFile, method, "JAVAN001", "unsupported reachable API", methodRef.display(), reason, fix)
            : warning(classFile, method, "JAVAN101", "unsupported API in unreachable code", methodRef.display(), reason, fix);
    }

    private static Diagnostic opcodeDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final boolean reachable
    ) {
        final String reason = "The current native profile does not implement this bytecode.";
        final String fix = "Remove the construct from reachable code or wait for profile expansion.";
        return reachable
            ? error(classFile, method, "JAVAN030", "unsupported reachable bytecode", instruction.mnemonic(), reason, fix)
            : warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", instruction.mnemonic(), reason, fix);
    }

    private static Diagnostic exceptionHandlerDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final int handlers,
        final boolean reachable
    ) {
        final String reason = "Only direct explicit athrow ranges with platform exception catch types are supported.";
        final String fix = "Keep try/catch limited to catching a directly thrown platform exception in the same method.";
        final String subject = handlers + " handler" + (handlers == 1 ? "" : "s");
        return reachable
            ? error(classFile, method, "JAVAN014", "exception handlers are not supported", subject, reason, fix)
            : warning(classFile, method, "JAVAN114", "exception handlers in unreachable code", subject, reason, fix);
    }

    private static Diagnostic newArrayDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final boolean reachable
    ) {
        final String subject = "newarray " + newArrayTypeName(instruction.operands().length == 0 ? -1 : instruction.operands()[0] & 0xFF);
        final String reason = "Only primitive one-dimensional newarray allocation is implemented in the current native profile.";
        final String fix = "Use a supported primitive array type or wait for broader array profile expansion.";
        return reachable
            ? error(classFile, method, "JAVAN030", "unsupported reachable bytecode", subject, reason, fix)
            : warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", subject, reason, fix);
    }

    private static Diagnostic invokedynamicDiagnostic(
        final ClassFile classFile,
        final MethodInfo method,
        final Instruction instruction,
        final boolean reachable
    ) {
        final String reason = "Only StringConcatFactory makeConcat and makeConcatWithConstants without secondary constants are implemented.";
        final String fix = "Keep invokedynamic limited to javac string concatenation or wait for dynamic-call expansion.";
        return reachable
            ? error(classFile, method, "JAVAN030", "unsupported reachable bytecode", instruction.mnemonic(), reason, fix)
            : warning(classFile, method, "JAVAN130", "unsupported bytecode in unreachable code", instruction.mnemonic(), reason, fix);
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
}
