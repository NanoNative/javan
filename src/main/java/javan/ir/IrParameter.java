package javan.ir;

/**
 * Function parameter in javan IR.
 *
 * @param type parameter type
 * @param name parameter name
 */
public record IrParameter(IrType type, String name) {
}
