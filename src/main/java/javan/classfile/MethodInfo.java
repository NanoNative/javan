package javan.classfile;

import java.util.Optional;

/**
 * Parsed Java method metadata.
 *
 * @param accessFlags method access flags
 * @param name method name
 * @param descriptor method descriptor
 * @param code method bytecode when present
 */
public record MethodInfo(int accessFlags, String name, String descriptor, Optional<CodeAttribute> code) {
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_SYNCHRONIZED = 0x0020;
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_NATIVE = 0x0100;

    /**
     * Returns true when this method is {@code public static void main(String[])}.
     *
     * @return true for the Java main method shape
     */
    public boolean isPublicStaticMain() {
        if (!"main".equals(name)) {
            return false;
        }
        if (!"([Ljava/lang/String;)V".equals(descriptor)) {
            return false;
        }
        if ((accessFlags & ACC_PUBLIC) == 0) {
            return false;
        }
        if ((accessFlags & ACC_STATIC) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when the method is static.
     *
     * @return true when static
     */
    public boolean isStatic() {
        if ((accessFlags & ACC_STATIC) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when the method is native.
     *
     * @return true when native
     */
    public boolean isNative() {
        if ((accessFlags & ACC_NATIVE) == 0) {
            return false;
        }
        return true;
    }

    /**
     * Returns true when the method is synchronized.
     *
     * @return true when synchronized
     */
    public boolean isSynchronized() {
        if ((accessFlags & ACC_SYNCHRONIZED) == 0) {
            return false;
        }
        return true;
    }
}
