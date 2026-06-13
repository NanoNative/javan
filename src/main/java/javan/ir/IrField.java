package javan.ir;

/**
 * Lowered field metadata used by generated native structs.
 *
 * @param type field type
 * @param name JVM field name
 * @param symbol C field symbol
 */
public record IrField(IrType type, String name, String symbol) {
}
