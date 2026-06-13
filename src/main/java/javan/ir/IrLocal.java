package javan.ir;

/**
 * Mutable function-local variable in javan IR.
 *
 * @param type local type
 * @param name local name
 */
public record IrLocal(IrType type, String name) {
}
