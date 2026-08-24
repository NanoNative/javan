package javan.classfile;

import java.util.List;
import java.util.Optional;

/**
 * Parsed Java Code attribute.
 *
 * @param maxStack maximum JVM operand stack depth
 * @param maxLocals maximum local variables
 * @param bytecode raw bytecode
 * @param exceptionTableLength number of JVM exception handlers in this method
 * @param exceptionTable JVM exception handlers
 * @param lineNumbers source line number table entries
 * @param stackMapFrames parsed StackMapTable reference-local frames
 * @param instructions decoded instructions
 */
public record CodeAttribute(
    int maxStack,
    int maxLocals,
    byte[] bytecode,
    int exceptionTableLength,
    List<CodeException> exceptionTable,
    List<LineNumberEntry> lineNumbers,
    List<StackMapFrame> stackMapFrames,
    List<Instruction> instructions
) {
    public CodeAttribute {
        stackMapFrames = List.copyOf(stackMapFrames);
    }

    /** Creates a Code attribute without parsed stack-map metadata. */
    public CodeAttribute(
        final int maxStack,
        final int maxLocals,
        final byte[] bytecode,
        final int exceptionTableLength,
        final List<CodeException> exceptionTable,
        final List<LineNumberEntry> lineNumbers,
        final List<Instruction> instructions
    ) {
        this(maxStack, maxLocals, bytecode, exceptionTableLength, exceptionTable,
            lineNumbers, List.of(), instructions);
    }

    /**
     * Creates a Code attribute without parsed line metadata.
     *
     * @param maxStack maximum JVM operand stack depth
     * @param maxLocals maximum local variables
     * @param bytecode raw bytecode
     * @param exceptionTableLength number of JVM exception handlers
     * @param exceptionTable JVM exception handlers
     * @param instructions decoded instructions
     */
    public CodeAttribute(
        final int maxStack,
        final int maxLocals,
        final byte[] bytecode,
        final int exceptionTableLength,
        final List<CodeException> exceptionTable,
        final List<Instruction> instructions
    ) {
        this(maxStack, maxLocals, bytecode, exceptionTableLength, exceptionTable,
            List.of(), List.of(), instructions);
    }

    /**
     * Creates a Code attribute without parsed handler metadata.
     *
     * @param maxStack maximum JVM operand stack depth
     * @param maxLocals maximum local variables
     * @param bytecode raw bytecode
     * @param exceptionTableLength number of JVM exception handlers
     * @param instructions decoded instructions
     */
    public CodeAttribute(
        final int maxStack,
        final int maxLocals,
        final byte[] bytecode,
        final int exceptionTableLength,
        final List<Instruction> instructions
    ) {
        this(maxStack, maxLocals, bytecode, exceptionTableLength, List.of(), instructions);
    }

    /**
     * Returns the source line active at a bytecode offset.
     *
     * @param bytecodeOffset bytecode offset
     * @return matching line when a LineNumberTable exists
     */
    public Optional<Integer> lineForOffset(final int bytecodeOffset) {
        Optional<Integer> result = Optional.empty();
        for (final LineNumberEntry entry : lineNumbers) {
            if (entry.startPc() > bytecodeOffset) {
                break;
            }
            result = Optional.of(entry.line());
        }
        return result;
    }

    /**
     * Returns the object type declared for a local at an exact stack-map frame offset.
     *
     * @param bytecodeOffset exact bytecode frame offset
     * @param slot JVM local slot
     * @return internal class name or array descriptor when the exact frame declares an object
     */
    public Optional<String> objectLocalTypeAt(final int bytecodeOffset, final int slot) {
        for (final StackMapFrame frame : stackMapFrames) {
            if (frame.offset() == bytecodeOffset) {
                return frame.objectLocalType(slot);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the object type when an exact frame has exactly one stack entry.
     *
     * @param bytecodeOffset exact bytecode frame offset
     * @return the sole stack object's internal class name or array descriptor
     */
    public Optional<String> singleStackObjectTypeAt(final int bytecodeOffset) {
        for (final StackMapFrame frame : stackMapFrames) {
            if (frame.offset() == bytecodeOffset) {
                return frame.singleStackObjectType();
            }
        }
        return Optional.empty();
    }
}
