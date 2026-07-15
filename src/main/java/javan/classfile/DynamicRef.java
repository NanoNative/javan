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
 * @param bootstrapArgumentDetails resolved bootstrap argument metadata
 */
public record DynamicRef(
    String name,
    String descriptor,
    String bootstrapOwner,
    String bootstrapName,
    String bootstrapDescriptor,
    List<String> bootstrapArguments,
    List<BootstrapArgument> bootstrapArgumentDetails
) {
    /**
     * Backward-compatible constructor for tests and existing string-only callers.
     *
     * @param name dynamic call name
     * @param descriptor dynamic call descriptor
     * @param bootstrapOwner bootstrap method owner
     * @param bootstrapName bootstrap method name
     * @param bootstrapDescriptor bootstrap method descriptor
     * @param bootstrapArguments normalized static bootstrap arguments
     */
    public DynamicRef(
        final String name,
        final String descriptor,
        final String bootstrapOwner,
        final String bootstrapName,
        final String bootstrapDescriptor,
        final List<String> bootstrapArguments
    ) {
        this(
            name,
            descriptor,
            bootstrapOwner,
            bootstrapName,
            bootstrapDescriptor,
            List.copyOf(bootstrapArguments),
            unknownArguments(bootstrapArguments)
        );
    }

    private static List<BootstrapArgument> unknownArguments(final List<String> bootstrapArguments) {
        final java.util.ArrayList<BootstrapArgument> result = new java.util.ArrayList<>();
        for (final String bootstrapArgument : bootstrapArguments) {
            result.add(BootstrapArgument.unknown(bootstrapArgument));
        }
        return List.copyOf(result);
    }
}
