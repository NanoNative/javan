package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;
import javan.classfile.RecordObjectMethodsCall;

import java.util.List;
import java.util.Optional;

/** Identifies record key methods that cannot execute application callbacks. */
public final class RecordMapKeySupport {
    private RecordMapKeySupport() { }

    /**
     * Checks for canonical record equality and hashing over primitive and String components.
     *
     * @param type scanned application or dependency class
     * @return whether both key operations are exact, side-effect-free generated methods
     */
    public static boolean isSafe(final ClassFile type) {
        if (!type.isRecord()) return false;
        for (final var component : type.recordComponents().orElseThrow()) {
            final String descriptor = component.descriptor();
            if (!"Ljava/lang/String;".equals(descriptor)
                && (descriptor.length() != 1 || "ZBCSIJFD".indexOf(descriptor.charAt(0)) < 0)) return false;
        }
        final Optional<MethodInfo> equals = type.method("equals", "(Ljava/lang/Object;)Z");
        final Optional<MethodInfo> hash = type.method("hashCode", "()I");
        if (equals.isEmpty() || hash.isEmpty() || equals.orElseThrow().isSynchronized() || hash.orElseThrow().isSynchronized()
            || equals.orElseThrow().code().isEmpty() || hash.orElseThrow().code().isEmpty()
            || !equals.orElseThrow().code().orElseThrow().exceptionTable().isEmpty()
            || !hash.orElseThrow().code().orElseThrow().exceptionTable().isEmpty()) return false;
        final List<Instruction> equalsCode = equals.orElseThrow().code().orElseThrow().instructions();
        final List<Instruction> hashCode = hash.orElseThrow().code().orElseThrow().instructions();
        if (equalsCode.size() != 4 || equalsCode.get(0).opcode() != 42 || equalsCode.get(1).opcode() != 43
            || equalsCode.get(2).opcode() != 186 || equalsCode.get(3).opcode() != 172
            || hashCode.size() != 3 || hashCode.get(0).opcode() != 42
            || hashCode.get(1).opcode() != 186 || hashCode.get(2).opcode() != 172
            || equalsCode.get(2).dynamicRef().isEmpty() || hashCode.get(1).dynamicRef().isEmpty()) return false;
        return RecordObjectMethodsCall.resolve(type, equals.orElseThrow(), equalsCode.get(2).dynamicRef().orElseThrow()).isPresent()
            && RecordObjectMethodsCall.resolveHashCode(type, hash.orElseThrow(), hashCode.get(1).dynamicRef().orElseThrow()).isPresent();
    }
}
