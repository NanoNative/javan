package javan.ir;


/**
 * Lowered field metadata used by generated native structs.
 *
 * @param type field type
 * @param name JVM field name
 * @param symbol C field symbol
 * @param initialIntegralValue static integral initial value
 */
public record IrField(IrType type, String name, String symbol, long initialIntegralValue) {
    /**
     * Creates a field initialized to zero.
     *
     * @param type field type
     * @param name JVM field name
     * @param symbol C field symbol
     */
    public IrField(final IrType type, final String name, final String symbol) {
        this(type, name, symbol, 0);
    }
}
