package javan.classfile;

/**
 * Resolved method reference from the constant pool.
 *
 * @param owner JVM internal owner class
 * @param name method name
 * @param descriptor method descriptor
 */
public record MethodRef(String owner, String name, String descriptor) {
    /**
     * Returns JVM display form.
     *
     * @return owner.name descriptor
     */
    public String display() {
        return owner + "." + name + descriptor;
    }
}
