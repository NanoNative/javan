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
 * @param instructions decoded instructions
 */
public record CodeAttribute(
    int maxStack,
    int maxLocals,
    byte[] bytecode,
    int exceptionTableLength,
    List<CodeException> exceptionTable,
    List<LineNumberEntry> lineNumbers,
    List<Instruction> instructions
) {
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
        this(maxStack, maxLocals, bytecode, exceptionTableLength, exceptionTable, List.of(), instructions);
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
}
