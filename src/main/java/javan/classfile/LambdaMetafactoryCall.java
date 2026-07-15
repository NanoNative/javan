package javan.classfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Normalized LambdaMetafactory call-site metadata for deterministic lowering.
 *
 * @param interfaceOwner functional interface owner returned by the call site
 * @param interfaceMethodName SAM method name at the call site
 * @param callSiteDescriptor invokedynamic call-site descriptor
 * @param samMethodDescriptor erased SAM method descriptor
 * @param implementation implementation method handle target
 * @param implementationReferenceKind JVM method-handle reference kind
 * @param instantiatedMethodDescriptor instantiated SAM descriptor
 * @param capturedParameterDescriptors captured call-site parameter descriptors
 */
public record LambdaMetafactoryCall(
    String interfaceOwner,
    String interfaceMethodName,
    String callSiteDescriptor,
    String samMethodDescriptor,
    MethodRef implementation,
    int implementationReferenceKind,
    String instantiatedMethodDescriptor,
    List<String> capturedParameterDescriptors
) {
    private static final String METAFACTORY_DESCRIPTOR =
        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
            + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
            + "Ljava/lang/invoke/CallSite;";

    /**
     * Resolves supported LambdaMetafactory metadata from a dynamic call site.
     *
     * @param dynamicRef resolved invokedynamic metadata
     * @return normalized lambda call metadata when the bootstrap shape is supported
     */
    public static Optional<LambdaMetafactoryCall> resolve(final DynamicRef dynamicRef) {
        if (!"java/lang/invoke/LambdaMetafactory".equals(dynamicRef.bootstrapOwner())) {
            return Optional.empty();
        }
        if (!"metafactory".equals(dynamicRef.bootstrapName())) {
            return Optional.empty();
        }
        if (!METAFACTORY_DESCRIPTOR.equals(dynamicRef.bootstrapDescriptor())) {
            return Optional.empty();
        }
        if (dynamicRef.bootstrapArgumentDetails().size() != 3) {
            return Optional.empty();
        }
        final List<BootstrapArgument> arguments = dynamicRef.bootstrapArgumentDetails();
        if (arguments.get(0).kind() != BootstrapArgument.Kind.METHOD_TYPE) {
            return Optional.empty();
        }
        if (arguments.get(1).kind() != BootstrapArgument.Kind.METHOD_HANDLE || arguments.get(1).methodRef().isEmpty()) {
            return Optional.empty();
        }
        if (arguments.get(2).kind() != BootstrapArgument.Kind.METHOD_TYPE) {
            return Optional.empty();
        }
        final Optional<String> returnDescriptor = returnDescriptor(dynamicRef.descriptor());
        if (returnDescriptor.isEmpty()) {
            return Optional.empty();
        }
        if (!"Ljava/util/function/Function;".equals(returnDescriptor.orElseThrow())
            && !"Ljava/util/function/Predicate;".equals(returnDescriptor.orElseThrow())
            && !"Lberlin/yuna/typemap/model/FunctionOrNull;".equals(returnDescriptor.orElseThrow())) {
            return Optional.empty();
        }
        final List<String> captured = parameterDescriptors(dynamicRef.descriptor());
        if (captured.isEmpty() && !dynamicRef.descriptor().startsWith("()")) {
            return Optional.empty();
        }
        final String interfaceOwner = objectOwner(returnDescriptor.orElseThrow()).orElse("");
        if (!"java/util/function/Function".equals(interfaceOwner)
            && !"java/util/function/Predicate".equals(interfaceOwner)
            && !"berlin/yuna/typemap/model/FunctionOrNull".equals(interfaceOwner)) {
            return Optional.empty();
        }
        return Optional.of(new LambdaMetafactoryCall(
            interfaceOwner,
            dynamicRef.name(),
            dynamicRef.descriptor(),
            arguments.get(0).text(),
            arguments.get(1).methodRef().orElseThrow(),
            arguments.get(1).referenceKind(),
            arguments.get(2).text(),
            captured
        ));
    }

    /**
     * Returns whether this is a supported {@code java.util.function.Function} shape.
     *
     * @return true when the lambda is a direct one-argument function
     */
    public boolean isFunction() {
        if (!"java/util/function/Function".equals(interfaceOwner) || !"apply".equals(interfaceMethodName)) {
            return false;
        }
        return singleObjectInput(instantiatedMethodDescriptor)
            && objectReturn(instantiatedMethodDescriptor)
            && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(samMethodDescriptor);
    }

    /**
     * Returns whether this is a supported {@code java.util.function.Predicate} shape.
     *
     * @return true when the lambda is a direct one-argument predicate
     */
    public boolean isPredicate() {
        if (!"java/util/function/Predicate".equals(interfaceOwner) || !"test".equals(interfaceMethodName)) {
            return false;
        }
        return singleObjectInput(instantiatedMethodDescriptor)
            && booleanReturn(instantiatedMethodDescriptor)
            && "(Ljava/lang/Object;)Z".equals(samMethodDescriptor);
    }

    /**
     * Returns the single instantiated input descriptor.
     *
     * @return input descriptor when this is a one-argument shape
     */
    public Optional<String> inputDescriptor() {
        final List<String> parameters = parameterDescriptors(instantiatedMethodDescriptor);
        if (parameters.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(parameters.getFirst());
    }

    /**
     * Returns whether the lambda is directly lowerable by the current profile.
     *
     * @return true for the supported function/predicate subset
     */
    public boolean isDirectlyLowerable() {
        if (!(isFunction() || isPredicate())) {
            return false;
        }
        if (implementationReferenceKind != 6 && implementationReferenceKind != 9) {
            return false;
        }
        return inputDescriptor().isPresent();
    }

    /**
     * Returns whether this is the exact zero-capture TypeMap FunctionOrNull materialization shape.
     *
     * @return true when the lambda matches the current FunctionOrNull runtime slice
     */
    public boolean isExactFunctionOrNullMaterialization() {
        if (!"berlin/yuna/typemap/model/FunctionOrNull".equals(interfaceOwner)) {
            return false;
        }
        if (!"applyWithException".equals(interfaceMethodName)) {
            return false;
        }
        if (!"(Ljava/lang/Object;)Ljava/lang/Object;".equals(samMethodDescriptor)) {
            return false;
        }
        if (!singleObjectInput(instantiatedMethodDescriptor) || !objectReturn(instantiatedMethodDescriptor)) {
            return false;
        }
        if (implementationReferenceKind != 6) {
            return false;
        }
        return capturedParameterDescriptors.isEmpty();
    }

    /**
     * Returns whether this is a zero-capture custom SAM object-return materialization.
     *
     * @return true when the current native profile can materialize the lambda as an object
     */
    public boolean isZeroCaptureMaterializedObjectLambda() {
        if (isDirectlyLowerable()) {
            return false;
        }
        if (implementationReferenceKind != 6) {
            return false;
        }
        if (!capturedParameterDescriptors.isEmpty()) {
            return false;
        }
        return singleObjectInput(instantiatedMethodDescriptor) && objectReturn(instantiatedMethodDescriptor);
    }

    /**
     * Returns whether this is a zero-capture custom SAM boolean-return materialization.
     *
     * @return true when the current native profile can materialize the lambda as an object
     */
    public boolean isZeroCaptureMaterializedBooleanLambda() {
        if (isDirectlyLowerable()) {
            return false;
        }
        if (implementationReferenceKind != 6) {
            return false;
        }
        if (!capturedParameterDescriptors.isEmpty()) {
            return false;
        }
        return singleObjectInput(instantiatedMethodDescriptor) && booleanReturn(instantiatedMethodDescriptor);
    }

    private static boolean singleObjectInput(final String descriptor) {
        final Optional<String> input = new LambdaMetafactoryCall("", "", "", "", new MethodRef("", "", ""), -1, descriptor, List.of())
            .inputDescriptor();
        return input.isPresent() && input.orElseThrow().startsWith("L");
    }

    private static boolean booleanReturn(final String descriptor) {
        final Optional<String> value = returnDescriptor(descriptor);
        return value.isPresent() && "Z".equals(value.orElseThrow());
    }

    private static boolean objectReturn(final String descriptor) {
        final Optional<String> value = returnDescriptor(descriptor);
        if (value.isEmpty()) {
            return false;
        }
        final String descriptorValue = value.orElseThrow();
        return descriptorValue.startsWith("L") || descriptorValue.startsWith("[");
    }

    private static Optional<String> objectOwner(final String descriptor) {
        if (!descriptor.startsWith("L") || !descriptor.endsWith(";")) {
            return Optional.empty();
        }
        return Optional.of(descriptor.substring(1, descriptor.length() - 1));
    }

    private static Optional<String> returnDescriptor(final String descriptor) {
        final int separator = descriptor.indexOf(')');
        if (separator < 0 || separator + 1 >= descriptor.length()) {
            return Optional.empty();
        }
        return Optional.of(descriptor.substring(separator + 1));
    }

    private static List<String> parameterDescriptors(final String descriptor) {
        if (!descriptor.startsWith("(")) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        int index = 1;
        while (index < descriptor.length() && descriptor.charAt(index) != ')') {
            final int start = index;
            final char type = descriptor.charAt(index);
            if ("BCDFIJSZ".indexOf(type) >= 0) {
                result.add(descriptor.substring(start, start + 1));
                index++;
                continue;
            }
            if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    return List.of();
                }
                result.add(descriptor.substring(start, end + 1));
                index = end + 1;
                continue;
            }
            if (type == '[') {
                index = skipArrayDescriptor(descriptor, index);
                if (index < 0) {
                    return List.of();
                }
                result.add(descriptor.substring(start, index));
                continue;
            }
            return List.of();
        }
        if (index >= descriptor.length() || descriptor.charAt(index) != ')') {
            return List.of();
        }
        return List.copyOf(result);
    }

    private static int skipArrayDescriptor(final String descriptor, final int start) {
        int index = start;
        while (index < descriptor.length() && descriptor.charAt(index) == '[') {
            index++;
        }
        if (index >= descriptor.length()) {
            return -1;
        }
        if ("BCDFIJSZ".indexOf(descriptor.charAt(index)) >= 0) {
            return index + 1;
        }
        if (descriptor.charAt(index) == 'L') {
            final int end = descriptor.indexOf(';', index);
            if (end < 0) {
                return -1;
            }
            return end + 1;
        }
        return -1;
    }
}
