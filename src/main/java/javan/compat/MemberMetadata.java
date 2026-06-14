package javan.compat;

import java.util.List;

/**
 * Field, method, or constructor metadata.
 *
 * @param accessFlags JVM access flags
 * @param name member name
 * @param descriptor JVM descriptor
 * @param attributes attribute names
 * @param instructions decoded instructions for methods with code
 */
public record MemberMetadata(
    int accessFlags,
    String name,
    String descriptor,
    List<String> attributes,
    List<InstructionMetadata> instructions
) {
    private static final int ACC_SYNTHETIC = 0x1000;

    /**
     * Returns true when the member is synthetic.
     *
     * @return true for synthetic members
     */
    public boolean synthetic() {
        if ((accessFlags & ACC_SYNTHETIC) != 0) {
            return true;
        }
        if (attributes.contains("Synthetic")) {
            return true;
        }
        return false;
    }

    /**
     * Returns true when the member is deprecated.
     *
     * @return true when deprecated
     */
    public boolean deprecated() {
        if (attributes.contains("Deprecated")) {
            return true;
        }
        return false;
    }
}
