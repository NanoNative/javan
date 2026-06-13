package javan.classfile;

import java.util.Optional;

/**
 * Decoded JVM bytecode instruction.
 *
 * @param offset bytecode offset
 * @param opcode unsigned opcode value
 * @param mnemonic JVM mnemonic
 * @param operands raw operand bytes
 * @param methodRef resolved method reference when the instruction has one
 * @param fieldRef resolved field reference when the instruction has one
 * @param className resolved class name when the instruction has one
 * @param stringValue resolved string literal when the instruction loads one
 * @param intValue resolved int literal when the instruction loads one
 * @param longValue resolved long literal when the instruction loads one
 * @param floatValue resolved float literal when the instruction loads one
 * @param doubleValue resolved double literal when the instruction loads one
 * @param dynamicRef resolved invokedynamic metadata when present
 */
public record Instruction(
    int offset,
    int opcode,
    String mnemonic,
    byte[] operands,
    Optional<MethodRef> methodRef,
    Optional<FieldRef> fieldRef,
    Optional<String> className,
    Optional<String> stringValue,
    Optional<Integer> intValue,
    Optional<Long> longValue,
    Optional<Float> floatValue,
    Optional<Double> doubleValue,
    Optional<DynamicRef> dynamicRef
) {
    /**
     * Creates an instruction without float/double literal metadata.
     *
     * @param offset bytecode offset
     * @param opcode unsigned opcode value
     * @param mnemonic JVM mnemonic
     * @param operands raw operand bytes
     * @param methodRef resolved method reference when present
     * @param fieldRef resolved field reference when present
     * @param className resolved class name when present
     * @param stringValue resolved string literal when present
     * @param intValue resolved int literal when present
     * @param longValue resolved long literal when present
     * @param dynamicRef resolved invokedynamic metadata when present
     */
    public Instruction(
        final int offset,
        final int opcode,
        final String mnemonic,
        final byte[] operands,
        final Optional<MethodRef> methodRef,
        final Optional<FieldRef> fieldRef,
        final Optional<String> className,
        final Optional<String> stringValue,
        final Optional<Integer> intValue,
        final Optional<Long> longValue,
        final Optional<DynamicRef> dynamicRef
    ) {
        this(
            offset,
            opcode,
            mnemonic,
            operands,
            methodRef,
            fieldRef,
            className,
            stringValue,
            intValue,
            longValue,
            Optional.empty(),
            Optional.empty(),
            dynamicRef
        );
    }
}
