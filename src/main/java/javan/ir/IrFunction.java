package javan.ir;

import java.util.List;

/**
 * Lowered function.
 *
 * @param owner JVM internal owner class
 * @param name method name
 * @param descriptor method descriptor
 * @param symbol C symbol
 * @param returnType return type
 * @param parameters function parameters
 * @param locals mutable local variables
 * @param instructions IR instructions, including generated emission barriers
 */
public record IrFunction(
    String owner,
    String name,
    String descriptor,
    String symbol,
    IrType returnType,
    List<IrParameter> parameters,
    List<IrLocal> locals,
    List<IrInstruction> instructions
) {
    /**
     * Returns source-level instructions without generated class-initialization barriers.
     *
     * @return source-level IR instructions
     */
    @Override
    public List<IrInstruction> instructions() {
        boolean containsBarrier = false;
        for (final IrInstruction instruction : instructions) {
            if (instruction.op() == IrInstruction.Op.INITIALIZE_CLASS) {
                containsBarrier = true;
                break;
            }
        }
        if (!containsBarrier) {
            return instructions;
        }
        final java.util.ArrayList<IrInstruction> result = new java.util.ArrayList<>(instructions.size());
        for (final IrInstruction instruction : instructions) {
            if (instruction.op() != IrInstruction.Op.INITIALIZE_CLASS) {
                result.add(instruction);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the complete instruction sequence used for native emission.
     *
     * @return source-level IR plus generated class-initialization barriers
     */
    public List<IrInstruction> emissionInstructions() {
        return instructions;
    }
}
