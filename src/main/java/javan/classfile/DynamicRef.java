package javan.classfile;

import java.util.List;
import java.util.Optional;

/**
 * Resolved invokedynamic metadata.
 *
 * @param name dynamic call name
 * @param descriptor dynamic call descriptor
 * @param bootstrapOwner bootstrap method owner
 * @param bootstrapName bootstrap method name
 * @param bootstrapDescriptor bootstrap method descriptor
 * @param bootstrapArguments normalized static bootstrap arguments
 * @param bootstrapValues structured static bootstrap arguments
 */
public record DynamicRef(
    String name,
    String descriptor,
    String bootstrapOwner,
    String bootstrapName,
    String bootstrapDescriptor,
    List<String> bootstrapArguments,
    List<BootstrapValue> bootstrapValues
) {
    /**
     * Creates invokedynamic metadata without structured bootstrap values.
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
        this(name, descriptor, bootstrapOwner, bootstrapName, bootstrapDescriptor, bootstrapArguments, List.of());
    }

    /**
     * Returns true when this site uses the standard LambdaMetafactory bootstrap.
     *
     * @return true for LambdaMetafactory metafactory sites
     */
    public boolean isLambdaMetafactory() {
        return "java/lang/invoke/LambdaMetafactory".equals(bootstrapOwner)
            && "metafactory".equals(bootstrapName);
    }

    /**
     * Returns the implementation target from the standard LambdaMetafactory bootstrap shape when present.
     *
     * @return implementation method handle target
     */
    public Optional<MethodRef> lambdaImplementationTarget() {
        if (!isLambdaMetafactory() || bootstrapValues.size() < 2) {
            return Optional.empty();
        }
        return bootstrapValues.get(1).methodRef();
    }
}
