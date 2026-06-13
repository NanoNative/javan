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
    private static final int ACC_STATIC = 0x0008;
    private static final int ACC_NATIVE = 0x0100;

    /**
     * Returns true when this method is {@code public static void main(String[])}.
     *
     * @return true for the Java main method shape
     */
    public boolean isPublicStaticMain() {
        return "main".equals(name)
            && "([Ljava/lang/String;)V".equals(descriptor)
            && (accessFlags & ACC_PUBLIC) != 0
            && (accessFlags & ACC_STATIC) != 0;
    }

    /**
     * Returns true when the method is static.
     *
     * @return true when static
     */
    public boolean isStatic() {
        return (accessFlags & ACC_STATIC) != 0;
    }

    /**
     * Returns true when the method is native.
     *
     * @return true when native
     */
    public boolean isNative() {
        return (accessFlags & ACC_NATIVE) != 0;
    }
}
