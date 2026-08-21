package javan.ir;

import java.util.List;

/**
 * Closed-world metadata for one declared Java method.
 *
 * @param declaringJvmName JVM name of the declaring class or interface
 * @param name Java method name
 * @param parameterDescriptors exact JVM parameter descriptors in declaration order
 * @param returnDescriptor exact JVM return descriptor
 * @param modifiers classfile method access flags
 */
public record IrMethodMetadata(
    String declaringJvmName,
    String name,
    List<String> parameterDescriptors,
    String returnDescriptor,
    int modifiers
) {
    /** Copies parameter metadata so the lowered program remains immutable. */
    public IrMethodMetadata {
        parameterDescriptors = List.copyOf(parameterDescriptors);
    }
}
