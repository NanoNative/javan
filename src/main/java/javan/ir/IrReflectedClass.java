package javan.ir;

import java.util.List;

/**
 * Closed-world method metadata for one reflected class.
 *
 * @param jvmName JVM internal class name
 * @param declaredMethods declared methods in classfile order
 * @param publicMethods public methods available through inherited lookup
 * @param arrayFamily whether public methods apply to every array class
 */
public record IrReflectedClass(
    String jvmName,
    List<IrMethodMetadata> declaredMethods,
    List<IrMethodMetadata> publicMethods,
    boolean arrayFamily
) {
    public IrReflectedClass {
        declaredMethods = List.copyOf(declaredMethods);
        publicMethods = List.copyOf(publicMethods);
        if (arrayFamily && !declaredMethods.isEmpty()) {
            throw new IllegalArgumentException("Array-family reflection metadata cannot declare methods.");
        }
    }

    /** Creates declared-method metadata without public inherited lookup metadata. */
    public IrReflectedClass(final String jvmName, final List<IrMethodMetadata> declaredMethods) {
        this(jvmName, declaredMethods, List.of(), false);
    }

    /** Creates exact-class declared and public method metadata. */
    public IrReflectedClass(
        final String jvmName,
        final List<IrMethodMetadata> declaredMethods,
        final List<IrMethodMetadata> publicMethods
    ) {
        this(jvmName, declaredMethods, publicMethods, false);
    }
}
