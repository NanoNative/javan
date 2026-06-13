package javan.classfile;

import java.util.List;

/**
 * Parsed Java Code attribute.
 *
 * @param maxStack maximum JVM operand stack depth
 * @param maxLocals maximum local variables
 * @param bytecode raw bytecode
 * @param exceptionTableLength number of JVM exception handlers in this method
 * @param exceptionTable JVM exception handlers
 * @param instructions decoded instructions
 */
public record CodeAttribute(
    int maxStack,
    int maxLocals,
    byte[] bytecode,
    int exceptionTableLength,
    List<CodeException> exceptionTable,
    List<Instruction> instructions
) {
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
}
