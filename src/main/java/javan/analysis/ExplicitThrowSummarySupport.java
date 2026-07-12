package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects exact leaf methods that only construct and throw a known platform throwable.
 */
public final class ExplicitThrowSummarySupport {
    private ExplicitThrowSummarySupport() {
    }

    /**
     * Returns a summary when the target method body is exactly a direct platform throwable construction plus
     * {@code athrow}, with no side effects or alternate control flow.
     *
     * @param classes parsed classes
     * @param entryPoint exact target method
     * @return exact throw summary when the method matches the supported leaf shape
     */
    public static Optional<DirectPlatformThrow> directPlatformThrow(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint
    ) {
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = classFile.method(entryPoint.methodName(), entryPoint.descriptor());
        if (method.isEmpty()) {
            return Optional.empty();
        }
        return directPlatformThrow(method.orElseThrow());
    }

    /**
     * Returns a summary when the method body is exactly a direct platform throwable construction plus
     * {@code athrow}, with no side effects or alternate control flow.
     *
     * @param method target method
     * @return exact throw summary when the method matches the supported leaf shape
     */
    public static Optional<DirectPlatformThrow> directPlatformThrow(final MethodInfo method) {
        final Optional<javan.classfile.CodeAttribute> code = method.code();
        if (code.isEmpty()) {
            return Optional.empty();
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() != 4 && instructions.size() != 5) {
            return Optional.empty();
        }
        int index = 0;
        final Instruction allocation = instructions.get(index++);
        if (allocation.opcode() != 187 || allocation.className().isEmpty()) {
            return Optional.empty();
        }
        final String throwableType = allocation.className().orElseThrow();
        if (!JdkCallSupport.isPlatformThrowable(throwableType)) {
            return Optional.empty();
        }
        if (instructions.get(index++).opcode() != 89) {
            return Optional.empty();
        }
        final Optional<String> message;
        if (instructions.size() == 5) {
            final Instruction constant = instructions.get(index++);
            if (constant.stringValue().isEmpty()) {
                return Optional.empty();
            }
            message = constant.stringValue();
        } else {
            message = Optional.empty();
        }
        final Instruction constructor = instructions.get(index++);
        if (constructor.opcode() != 183 || constructor.methodRef().isEmpty()) {
            return Optional.empty();
        }
        final MethodRef constructorRef = constructor.methodRef().orElseThrow();
        if (!throwableType.equals(constructorRef.owner())) {
            return Optional.empty();
        }
        if (!"<init>".equals(constructorRef.name())) {
            return Optional.empty();
        }
        if (message.isPresent()) {
            if (!"(Ljava/lang/String;)V".equals(constructorRef.descriptor())) {
                return Optional.empty();
            }
        } else if (!"()V".equals(constructorRef.descriptor())) {
            return Optional.empty();
        }
        if (instructions.get(index).opcode() != 191) {
            return Optional.empty();
        }
        return Optional.of(new DirectPlatformThrow(throwableType, message));
    }

    /**
     * Returns true when the method body is an exact no-op instance constructor or empty method body.
     *
     * @param classes parsed classes
     * @param entryPoint exact target method
     * @return true when the method is a verified trivial no-op
     */
    public static boolean trivialNoop(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint
    ) {
        final ClassFile classFile = classes.get(entryPoint.className());
        if (classFile == null) {
            return false;
        }
        final Optional<MethodInfo> method = classFile.method(entryPoint.methodName(), entryPoint.descriptor());
        if (method.isEmpty()) {
            return false;
        }
        final Optional<javan.classfile.CodeAttribute> code = method.orElseThrow().code();
        if (code.isEmpty()) {
            return false;
        }
        final List<Instruction> instructions = code.orElseThrow().instructions();
        if (instructions.size() == 1) {
            return instructions.getFirst().opcode() == 177;
        }
        if (instructions.size() != 3) {
            return false;
        }
        if (instructions.getFirst().opcode() != 42) {
            return false;
        }
        final Instruction invoke = instructions.get(1);
        if (invoke.opcode() != 183 || invoke.methodRef().isEmpty()) {
            return false;
        }
        final MethodRef target = invoke.methodRef().orElseThrow();
        if (!"java/lang/Object".equals(target.owner())) {
            return false;
        }
        if (!"<init>".equals(target.name()) || !"()V".equals(target.descriptor())) {
            return false;
        }
        return instructions.get(2).opcode() == 177;
    }

    public record DirectPlatformThrow(String throwableType, Optional<String> message) {
    }
}
