package javan.ir;

import java.util.List;

/**
 * Closed-world metadata for one declared Java method.
 *
 * @param declaringJvmName JVM name of the declaring class or interface
 * @param declaringNestJvmName JVM name of the declaring class's nest host
 * @param name Java method name
 * @param parameterDescriptors exact JVM parameter descriptors in declaration order
 * @param returnDescriptor exact JVM return descriptor
 * @param modifiers classfile method access flags
 * @param declaringPublic whether the declaring type is public
 * @param overrideAllowed whether this method may suppress Java access checks
 * @param protectedOverrideCallerJvmNames subclass callers allowed to suppress protected static access
 */
public record IrMethodMetadata(
    String declaringJvmName,
    String declaringNestJvmName,
    String name,
    List<String> parameterDescriptors,
    String returnDescriptor,
    int modifiers,
    boolean declaringPublic,
    boolean overrideAllowed,
    List<String> protectedOverrideCallerJvmNames
) {
    /** Copies list metadata so the lowered program remains immutable. */
    public IrMethodMetadata {
        parameterDescriptors = List.copyOf(parameterDescriptors);
        protectedOverrideCallerJvmNames = List.copyOf(protectedOverrideCallerJvmNames);
    }
}
