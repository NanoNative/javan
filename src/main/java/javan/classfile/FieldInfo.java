package javan.classfile;

/**
 * Parsed Java field metadata.
 *
 * @param accessFlags field access flags
 * @param name field name
 * @param descriptor field descriptor
 */
public record FieldInfo(int accessFlags, String name, String descriptor) {
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_ENUM = 0x4000;

    /**
     * Returns true when this field is static.
     *
     * @return true when the field has ACC_STATIC
     */
    public boolean isStatic() {
        return (accessFlags & ACC_STATIC) != 0;
    }

    /**
     * Returns true when this field is an enum constant.
     *
     * @return true when the field has ACC_ENUM
     */
    public boolean isEnumConstant() {
        return (accessFlags & ACC_ENUM) != 0;
    }
}
