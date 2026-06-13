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
        return switch (this) {
            case VOID -> "void";
            case INT -> "int";
            case LONG -> "long long";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case STRING -> "const char*";
            case BYTE_ARRAY -> "JavanByteArray";
        };
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
        return switch (this) {
            case VOID -> "void";
            case INT -> "int";
            case LONG -> "long";
            case FLOAT -> "float";
            case DOUBLE -> "double";
            case STRING -> "string";
            case BYTE_ARRAY -> "bytes";
        };
    }
}
