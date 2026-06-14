package javan.ir;

/**
 * Types supported by the current javan IR.
 */
public enum IrType {
    VOID,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    OBJECT;

    /**
     * Returns the matching C type name.
     *
     * @return C type name
     */
    public String cName() {
        if (this == VOID) {
            return "void";
        }
        if (this == INT) {
            return "int";
        }
        if (this == LONG) {
            return "long long";
        }
        if (this == FLOAT) {
            return "float";
        }
        if (this == DOUBLE) {
            return "double";
        }
        return "void*";
    }

    /**
     * Returns the JVM local-variable slot width.
     *
     * @return slot width
     */
    public int slotWidth() {
        if (this == LONG) {
            return 2;
        }
        if (this == DOUBLE) {
            return 2;
        }
        return 1;
    }
}
