package javan.ir;

/**
 * Types supported by the current javan IR.
 */
public enum IrType {
    VOID("void"),
    INT("int"),
    LONG("long long"),
    FLOAT("float"),
    DOUBLE("double"),
    OBJECT("void*");

    private final String cName;

    IrType(final String cName) {
        this.cName = cName;
    }

    /**
     * Returns the matching C type name.
     *
     * @return C type name
     */
    public String cName() {
        return cName;
    }

    /**
     * Returns the JVM local-variable slot width.
     *
     * @return slot width
     */
    public int slotWidth() {
        return this == LONG || this == DOUBLE ? 2 : 1;
    }
}
