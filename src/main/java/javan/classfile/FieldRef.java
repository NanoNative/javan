package javan.classfile;

/**
 * Resolved field reference from the constant pool.
 *
 * @param owner JVM internal owner class
 * @param name field name
 * @param descriptor field descriptor
 */
public record FieldRef(String owner, String name, String descriptor) {
    /**
     * Returns JVM display form.
     *
     * @return owner.name descriptor
     */
    public String display() {
        return owner + "." + name + ":" + descriptor;
    }
}
