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
 * @param instructions IR instructions
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
}
