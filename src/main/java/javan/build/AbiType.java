package javan.build;

/**
 * Exported C ABI type.
 */
public enum AbiType {
    VOID,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    BYTE_ARRAY;

    /**
     * Returns the C type used in generated headers.
     *
     * @return C type
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
        if (this == STRING) {
            return "const char*";
        }
        if (this == BYTE_ARRAY) {
            return "JavanByteArray";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }

    /**
     * Returns the C return type used in generated headers.
     *
     * @return C return type
     */
    public String cReturnName() {
        return this == STRING ? "char*" : cName();
    }

    /**
     * Returns the suffix used in stable export names.
     *
     * @return suffix token
     */
    public String suffix() {
        if (this == VOID) {
            return "void";
        }
        if (this == INT) {
            return "int";
        }
        if (this == LONG) {
            return "long";
        }
        if (this == FLOAT) {
            return "float";
        }
        if (this == DOUBLE) {
            return "double";
        }
        if (this == STRING) {
            return "string";
        }
        if (this == BYTE_ARRAY) {
            return "bytes";
        }
        throw new IllegalStateException("Unsupported ABI type");
    }
}
