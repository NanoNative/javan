package javan.classfile;

import java.util.Optional;

/**
 * Parsed Java field metadata.
 *
 * @param accessFlags field access flags
 * @param name field name
 * @param descriptor field descriptor
 * @param signature optional generic field signature
 */
public record FieldInfo(int accessFlags, String name, String descriptor, Optional<String> signature) {
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_ENUM = 0x4000;

    public FieldInfo {
        if (signature == null) {
            throw new IllegalArgumentException("field signature optional is null");
        }
    }

    /**
     * Creates field metadata without a generic signature.
     *
     * @param accessFlags field access flags
     * @param name field name
     * @param descriptor field descriptor
     */
    public FieldInfo(final int accessFlags, final String name, final String descriptor) {
        this(accessFlags, name, descriptor, Optional.empty());
    }

    /**
     * Returns true when this field is static.
     *
     * @return true when the field has ACC_STATIC
     */
    public boolean isStatic() {
        if ((accessFlags & ACC_STATIC) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when this field is an enum constant.
     *
     * @return true when the field has ACC_ENUM
     */
    public boolean isEnumConstant() {
        if ((accessFlags & ACC_ENUM) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when this field has one complete legal JVM field descriptor.
     *
     * @return true when valid
     */
    public boolean hasValidDescriptor() {
        return isValidDescriptor(descriptor);
    }

    /**
     * Validates one complete JVM field descriptor.
     *
     * @param descriptor descriptor text
     * @return true when valid
     */
    public static boolean isValidDescriptor(final String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) {
            return false;
        }
        int index = 0;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index > 255 || index >= descriptor.length()) {
            return false;
        }
        final char type = descriptor.charAt(index);
        if ("BCDFIJSZ".indexOf(type) >= 0) {
            return index + 1 == descriptor.length();
        }
        if (type != 'L' || descriptor.charAt(descriptor.length() - 1) != ';') {
            return false;
        }
        return validInternalName(descriptor.substring(index + 1, descriptor.length() - 1));
    }

    /**
     * Returns the internal owner for a valid direct reference descriptor.
     *
     * @return reference owner, or empty for primitive, array, or malformed descriptors
     */
    public Optional<String> referenceOwner() {
        if (!hasValidDescriptor() || descriptor.charAt(0) != 'L') {
            return Optional.empty();
        }
        return Optional.of(descriptor.substring(1, descriptor.length() - 1));
    }

    private static boolean validInternalName(final String name) {
        if (name.isEmpty() || name.charAt(0) == '/' || name.charAt(name.length() - 1) == '/') {
            return false;
        }
        char previous = 0;
        for (int index = 0; index < name.length(); index++) {
            final char current = name.charAt(index);
            if (current == '.' || current == ';' || current == '[' || (current == '/' && previous == '/')) {
                return false;
            }
            previous = current;
        }
        return true;
    }
}
