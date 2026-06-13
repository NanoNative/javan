package javan.compat;

/**
 * Decoded bytecode instruction metadata for compatibility reports.
 *
 * @param offset bytecode offset
 * @param opcode unsigned opcode
 * @param mnemonic stable mnemonic
 * @param operandLength operand byte count
 * @param support native profile support status
 */
public record InstructionMetadata(
    int offset,
    int opcode,
    String mnemonic,
    int operandLength,
    BytecodeSupport.Status support
) {
}
