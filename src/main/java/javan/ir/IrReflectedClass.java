package javan.ir;

import java.util.List;

/**
 * Closed-world method metadata for one reflected class.
 *
 * @param jvmName JVM internal class name
 * @param declaredMethods declared methods in classfile order
 */
public record IrReflectedClass(String jvmName, List<IrMethodMetadata> declaredMethods) {
    public IrReflectedClass {
        declaredMethods = List.copyOf(declaredMethods);
    }
}
