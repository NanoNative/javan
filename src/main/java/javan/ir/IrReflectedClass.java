package javan.ir;

import java.util.List;

/**
 * Closed-world member metadata for one reflected class.
 *
 * @param jvmName JVM internal class name
 * @param declaredMethods declared methods in classfile order
 * @param publicMethods public methods available through inherited lookup
 * @param arrayFamily whether public methods apply to every array class
 * @param declaredConstructors declared constructors available for lookup
 * @param abstractClass whether reflective construction must reject the class
 * @param enumClass whether reflective construction must reject enum constants
 */
public record IrReflectedClass(
    String jvmName,
    List<IrMethodMetadata> declaredMethods,
    List<IrMethodMetadata> publicMethods,
    boolean arrayFamily,
    List<IrMethodMetadata> declaredConstructors,
    boolean abstractClass,
    boolean enumClass
) {
    public IrReflectedClass {
        declaredMethods = List.copyOf(declaredMethods);
        publicMethods = List.copyOf(publicMethods);
        declaredConstructors = List.copyOf(declaredConstructors);
        if (arrayFamily && !declaredMethods.isEmpty()) {
            throw new IllegalArgumentException("Array-family reflection metadata cannot declare methods.");
        }
    }

    /** Creates method metadata without constructor metadata. */
    public IrReflectedClass(
        final String jvmName,
        final List<IrMethodMetadata> declaredMethods,
        final List<IrMethodMetadata> publicMethods,
        final boolean arrayFamily
    ) {
        this(jvmName, declaredMethods, publicMethods, arrayFamily, List.of(), false, false);
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
