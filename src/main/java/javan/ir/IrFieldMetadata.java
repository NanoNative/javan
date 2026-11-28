package javan.ir;

/**
 * Closed-world metadata for one declared field.
 *
 * @param declaringJvmName JVM name of the declaring class
 * @param declaringNestJvmName JVM name of the declaring class's nest host
 * @param name Java field name
 * @param descriptor JVM field descriptor
 * @param modifiers classfile field access flags
 * @param declaringPublic whether the declaring class is public
 */
public record IrFieldMetadata(
    String declaringJvmName,
    String declaringNestJvmName,
    String name,
    String descriptor,
    int modifiers,
    boolean declaringPublic
) {
}
