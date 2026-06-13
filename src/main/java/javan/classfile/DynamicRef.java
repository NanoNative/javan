package javan.classfile;

import java.util.List;

/**
 * Resolved invokedynamic metadata.
 *
 * @param name dynamic call name
 * @param descriptor dynamic call descriptor
 * @param bootstrapOwner bootstrap method owner
 * @param bootstrapName bootstrap method name
 * @param bootstrapDescriptor bootstrap method descriptor
 * @param bootstrapArguments normalized static bootstrap arguments
 */
public record DynamicRef(
    String name,
    String descriptor,
    String bootstrapOwner,
    String bootstrapName,
    String bootstrapDescriptor,
    List<String> bootstrapArguments
) {
}
