package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;
import javan.compat.JdkCallSupport;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Proves the runtime representation of throwable values returned by a method. */
public final class ThrowableReturnAnalysis {
    private ThrowableReturnAnalysis() {
    }

    /**
     * Returns a conservative throwable result for a method.
     *
     * <p>Application methods with a broad platform return type are accepted only when every
     * possible reference source is a directly allocated throwable using one runtime representation.
     * Other flows remain unknown instead of guessing.</p>
     *
     * @param classes parsed application classes
     * @param methodRef called method
     * @param staticallyBound whether the invocation resolves to this exact method body
     * @return proven upper bound, concrete types, and runtime representation
     */
    public static Optional<Result> analyze(
        final Map<String, ClassFile> classes,
        final MethodRef methodRef,
        final boolean staticallyBound
    ) {
        final Optional<String> declared = throwableDescriptorType(
            classes,
            methodRef.descriptor().substring(methodRef.descriptor().indexOf(')') + 1)
        );
        if (declared.isEmpty()) {
            return Optional.empty();
        }
        final String upperBound = declared.orElseThrow();
        if (classes.containsKey(upperBound)) {
            return Optional.of(Result.exact(upperBound, true));
        }
        final ClassFile owner = classes.get(methodRef.owner());
        if (owner == null) {
            return Optional.of(Result.exact(upperBound, false));
        }
        if (!staticallyBound) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(methodRef.name(), methodRef.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        if (!method.orElseThrow().code().orElseThrow().exceptionTable().isEmpty()
            || !BytecodeControlFlow.analyze(method.orElseThrow().code().orElseThrow()).structurallyValid()) {
            return Optional.empty();
        }

        final Set<String> possibleTypes = new LinkedHashSet<>();
        boolean hasGeneratedType = false;
        boolean hasPlatformType = false;
        boolean returnsValue = false;
        for (final Instruction instruction : method.orElseThrow().code().orElseThrow().instructions()) {
            if (instruction.opcode() == 176) {
                returnsValue = true;
                continue;
            }
            if (instruction.opcode() == 187) {
                if (instruction.className().isEmpty()
                    || !assignableTo(classes, instruction.className().orElseThrow(), upperBound)) {
                    return Optional.empty();
                }
                final String type = instruction.className().orElseThrow();
                final boolean generatedType = classes.containsKey(type);
                hasGeneratedType |= generatedType;
                hasPlatformType |= !generatedType;
                if (hasGeneratedType && hasPlatformType) {
                    return Optional.empty();
                }
                possibleTypes.add(type);
                continue;
            }
            if (unprovenReferenceSource(instruction)) {
                return Optional.empty();
            }
        }
        return !returnsValue || possibleTypes.isEmpty()
            ? Optional.empty()
            : result(upperBound, possibleTypes, hasGeneratedType);
    }

    private static Optional<Result> result(
        final String upperBound,
        final Set<String> possibleTypes,
        final boolean generatedObject
    ) {
        if (!generatedObject && possibleTypes.size() != 1) {
            return Optional.empty();
        }
        final List<String> types = List.copyOf(possibleTypes);
        return Optional.of(new Result(generatedObject ? upperBound : types.getFirst(), types, generatedObject));
    }

    private static boolean unprovenReferenceSource(final Instruction instruction) {
        final int opcode = instruction.opcode();
        if (opcode == 1 || opcode == 25 || opcode >= 42 && opcode <= 45 || opcode == 50 || opcode == 196
            || opcode == 188 || opcode == 189 || opcode == 192 || opcode == 197 || opcode == 186) {
            return true;
        }
        if (opcode >= 18 && opcode <= 20 && instruction.constantPoolTag().orElse(-1) == 17) {
            return true;
        }
        if ((opcode == 178 || opcode == 180) && instruction.fieldRef().isPresent()) {
            return referenceDescriptor(instruction.fieldRef().orElseThrow().descriptor());
        }
        if (instruction.methodRef().isEmpty() || "<init>".equals(instruction.methodRef().orElseThrow().name())) {
            return false;
        }
        final String descriptor = instruction.methodRef().orElseThrow().descriptor();
        return referenceDescriptor(descriptor.substring(descriptor.indexOf(')') + 1));
    }

    private static boolean referenceDescriptor(final String descriptor) {
        return !descriptor.isEmpty() && (descriptor.charAt(0) == 'L' || descriptor.charAt(0) == '[');
    }

    private static Optional<String> throwableDescriptorType(
        final Map<String, ClassFile> classes,
        final String descriptor
    ) {
        if (descriptor.length() < 3 || descriptor.charAt(0) != 'L' || descriptor.charAt(descriptor.length() - 1) != ';') {
            return Optional.empty();
        }
        final String type = descriptor.substring(1, descriptor.length() - 1);
        return isThrowable(classes, type) ? Optional.of(type) : Optional.empty();
    }

    private static boolean isThrowable(final Map<String, ClassFile> classes, final String type) {
        String current = type;
        final Set<String> visited = new LinkedHashSet<>();
        while (current != null && !current.isEmpty() && visited.add(current)) {
            if (JdkCallSupport.isPlatformThrowable(current)
                || JdkCallSupport.isPlatformThrowableAssignable(current, "java/lang/Throwable")) {
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

    private static boolean assignableTo(
        final Map<String, ClassFile> classes,
        final String candidate,
        final String expected
    ) {
        String current = candidate;
        final Set<String> visited = new LinkedHashSet<>();
        while (current != null && !current.isEmpty() && visited.add(current)) {
            if (current.equals(expected) || JdkCallSupport.isPlatformThrowableAssignable(current, expected)) {
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

    /** Proven throwable return representation. */
    public record Result(String upperBound, List<String> possibleTypes, boolean generatedObject) {
        public Result {
            possibleTypes = List.copyOf(possibleTypes);
            if (possibleTypes.isEmpty()) {
                throw new IllegalArgumentException("Throwable return needs at least one possible type");
            }
        }

        private static Result exact(final String type, final boolean generatedObject) {
            return new Result(type, List.of(type), generatedObject);
        }
    }
}
