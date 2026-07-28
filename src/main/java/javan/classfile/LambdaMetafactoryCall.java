package javan.classfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            && !"Ljava/util/function/BiFunction;".equals(returnDescriptor.orElseThrow())
            && !"Ljava/util/function/Predicate;".equals(returnDescriptor.orElseThrow())
            && !"Ljava/util/function/Supplier;".equals(returnDescriptor.orElseThrow())
            && !"Ljava/util/function/Consumer;".equals(returnDescriptor.orElseThrow())
            && !"Ljava/util/function/BiConsumer;".equals(returnDescriptor.orElseThrow())
            && !returnDescriptor.orElseThrow().startsWith("L")) {
            return Optional.empty();
        }
        final List<String> captured = parameterDescriptors(dynamicRef.descriptor());
        if (captured.isEmpty() && !dynamicRef.descriptor().startsWith("()")) {
            return Optional.empty();
        }
        final String interfaceOwner = objectOwner(returnDescriptor.orElseThrow()).orElse("");
        if (!"java/util/function/Function".equals(interfaceOwner)
            && !"java/util/function/BiFunction".equals(interfaceOwner)
            && !"java/util/function/Predicate".equals(interfaceOwner)
            && !"java/util/function/Supplier".equals(interfaceOwner)
            && !"java/util/function/Consumer".equals(interfaceOwner)
            && !"java/util/function/BiConsumer".equals(interfaceOwner)
            && interfaceOwner.isEmpty()) {
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
     * Returns whether this is a supported {@code java.util.function.BiFunction} shape.
     *
     * @return true when the lambda is a direct two-argument object-return function
     */
    public boolean isBiFunction() {
        if (!"java/util/function/BiFunction".equals(interfaceOwner) || !"apply".equals(interfaceMethodName)) {
            return false;
        }
        return objectInputs(instantiatedMethodDescriptor, 2)
            && objectReturn(instantiatedMethodDescriptor)
            && "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;".equals(samMethodDescriptor);
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
     * Returns whether this is a supported {@code java.util.function.Supplier} shape.
     *
     * @return true when the lambda is a direct zero-argument object supplier
     */
    public boolean isSupplier() {
        if (!"java/util/function/Supplier".equals(interfaceOwner) || !"get".equals(interfaceMethodName)) {
            return false;
        }
        return noInputs(instantiatedMethodDescriptor)
            && objectReturn(instantiatedMethodDescriptor)
            && "()Ljava/lang/Object;".equals(samMethodDescriptor);
    }

    /**
     * Returns whether this is a supported {@code java.util.function.Consumer} shape.
     *
     * @return true when the lambda is a direct one-argument consumer
     */
    public boolean isConsumer() {
        if (!"java/util/function/Consumer".equals(interfaceOwner) || !"accept".equals(interfaceMethodName)) {
            return false;
        }
        return singleObjectInput(instantiatedMethodDescriptor)
            && voidReturn(instantiatedMethodDescriptor)
            && "(Ljava/lang/Object;)V".equals(samMethodDescriptor);
    }

    /**
     * Returns whether this is a supported {@code java.util.function.BiConsumer} shape.
     *
     * @return true when the lambda is a direct two-argument consumer
     */
    public boolean isBiConsumer() {
        if (!"java/util/function/BiConsumer".equals(interfaceOwner) || !"accept".equals(interfaceMethodName)) {
            return false;
        }
        return objectInputs(instantiatedMethodDescriptor, 2)
            && voidReturn(instantiatedMethodDescriptor)
            && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals(samMethodDescriptor);
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
     * @return true for the supported function, predicate, and supplier subset
     */
    public boolean isDirectlyLowerable() {
        if (!(isFunction() || isPredicate() || isSupplier())) {
            return false;
        }
        if (implementationReferenceKind != 6 && implementationReferenceKind != 9) {
            return false;
        }
        if (isSupplier()) {
            return noInputs(instantiatedMethodDescriptor);
        }
        return inputDescriptor().isPresent();
    }

    /**
     * Returns whether the lambda is directly lowerable with the parsed class hierarchy.
     *
     * @param classes parsed closed-world classes
     * @return true when direct invocation preserves the lambda method-handle semantics
     */
    public boolean isDirectlyLowerable(final Map<String, ClassFile> classes) {
        if (isDirectlyLowerable()) {
            return true;
        }
        final ClassFile implementationClass = classes.get(implementation.owner());
        return implementationClass != null
            && implementationClass.isFinal()
            && (isBoundInstanceSupplierLambda() || isBoundInstancePredicateLambda());
    }

    private boolean isBoundInstanceSupplierLambda() {
        if (!isSupplier()
            || implementationReferenceKind != 5
            || !objectReturn(implementation.descriptor())
            || capturedParameterDescriptors.isEmpty()
            || !("L" + implementation.owner() + ";").equals(capturedParameterDescriptors.getFirst())) {
            return false;
        }
        final List<String> parameters = parameterDescriptors(implementation.descriptor());
        if (!parametersMatchDescriptor(implementation.descriptor(), parameters)
            || parameters.size() != capturedParameterDescriptors.size() - 1) {
            return false;
        }
        for (int index = 1; index < capturedParameterDescriptors.size(); index++) {
            if (!capturedParameterDescriptors.get(index).equals(parameters.get(index - 1))) {
                return false;
            }
        }
        return true;
    }

    private boolean isBoundInstancePredicateLambda() {
        if (!isPredicate()
            || implementationReferenceKind != 5
            || !booleanReturn(implementation.descriptor())
            || capturedParameterDescriptors.isEmpty()
            || !("L" + implementation.owner() + ";").equals(capturedParameterDescriptors.getFirst())) {
            return false;
        }
        final List<String> parameters = parameterDescriptors(implementation.descriptor());
        if (!parametersMatchDescriptor(implementation.descriptor(), parameters)
            || parameters.size() != capturedParameterDescriptors.size()) {
            return false;
        }
        for (int index = 1; index < capturedParameterDescriptors.size(); index++) {
            if (!sameOrBoundPredicateCompatible(capturedParameterDescriptors.get(index), parameters.get(index - 1))) {
                return false;
            }
        }
        final Optional<String> input = inputDescriptor();
        return input.isPresent() && sameOrBoundPredicateCompatible(input.orElseThrow(), parameters.getLast());
    }

    private static boolean sameOrBoundPredicateCompatible(final String source, final String target) {
        return source.equals(target)
            || ("Ljava/lang/Object;".equals(target) && (source.startsWith("L") || source.startsWith("[")));
    }

    /**
     * Returns whether this is a materializable {@code Consumer} lambda with object captures only.
     *
     * @return true when the current runtime can materialize the consumer as a lambda object
     */
    public boolean isMaterializedConsumerLambda() {
        if (!isConsumer()) {
            return false;
        }
        if (implementationReferenceKind != 6) {
            return false;
        }
        for (final String capture : capturedParameterDescriptors) {
            if (!capture.startsWith("L") && !capture.startsWith("[")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this is a materializable {@code BiConsumer} lambda with object captures only.
     *
     * @return true when the current runtime can materialize the bi-consumer as a lambda object
     */
    public boolean isMaterializedBiConsumerLambda() {
        if (!isBiConsumer()) {
            return false;
        }
        if (implementationReferenceKind != 6) {
            return false;
        }
        for (final String capture : capturedParameterDescriptors) {
            if (!capture.startsWith("L") && !capture.startsWith("[")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this is a materializable {@code BiFunction} lambda with object captures only.
     *
     * @return true when the current runtime can materialize the bi-function as a lambda object
     */
    public boolean isMaterializedBiFunctionLambda() {
        if (!isBiFunction()) {
            return false;
        }
        if (implementationReferenceKind != 6) {
            return false;
        }
        for (final String capture : capturedParameterDescriptors) {
            if (!capture.startsWith("L") && !capture.startsWith("[")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this {@code Supplier} can be represented by the captured-lambda runtime.
     *
     * @return true for application static or instance implementations with object captures
     */
    public boolean isMaterializedSupplierLambda() {
        if (!isSupplier()
            || (implementationReferenceKind != 5 && implementationReferenceKind != 6)
            || !objectReturn(implementation.descriptor())
            || !hasObjectCaptures()) {
            return false;
        }
        final List<String> implementationParameters = parameterDescriptors(implementation.descriptor());
        if (implementationReferenceKind == 6) {
            if (implementationParameters.size() != capturedParameterDescriptors.size()) {
                return false;
            }
            for (int index = 0; index < capturedParameterDescriptors.size(); index++) {
                if (!sameOrObjectCompatible(capturedParameterDescriptors.get(index), implementationParameters.get(index))) {
                    return false;
                }
            }
            return true;
        }
        if (capturedParameterDescriptors.isEmpty()
            || !("L" + implementation.owner() + ";").equals(capturedParameterDescriptors.getFirst())
            || implementationParameters.size() != capturedParameterDescriptors.size() - 1) {
            return false;
        }
        for (int index = 1; index < capturedParameterDescriptors.size(); index++) {
            if (!sameOrObjectCompatible(capturedParameterDescriptors.get(index), implementationParameters.get(index - 1))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this is a materializable void-return lambda with object captures only.
     *
     * @return true when the current runtime can materialize the callable as a lambda object
     */
    public boolean isMaterializedVoidLambda() {
        return isMaterializedConsumerLambda() || isMaterializedBiConsumerLambda();
    }

    private boolean hasObjectCaptures() {
        for (final String capture : capturedParameterDescriptors) {
            if (!capture.startsWith("L") && !capture.startsWith("[")) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameOrObjectCompatible(final String source, final String target) {
        return source.equals(target)
            || ("Ljava/lang/Object;".equals(target) && (source.startsWith("L") || source.startsWith("[")));
    }

    /**
     * Returns whether this is the exact zero-capture catch-null functional-interface materialization shape.
     *
     * @return true when the lambda matches the current catch-null functional-interface runtime slice
     */
    public boolean isExactFunctionOrNullMaterialization() {
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
        return (singleObjectInput(instantiatedMethodDescriptor) || objectInputs(instantiatedMethodDescriptor, 2))
            && objectReturn(instantiatedMethodDescriptor);
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

    private static boolean noInputs(final String descriptor) {
        return descriptor.startsWith("()");
    }

    private static boolean objectInputs(final String descriptor, final int count) {
        final List<String> parameters = parameterDescriptors(descriptor);
        if (parameters.size() != count) {
            return false;
        }
        for (final String parameter : parameters) {
            if (!parameter.startsWith("L") && !parameter.startsWith("[")) {
                return false;
            }
        }
        return true;
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
        if (descriptorValue.startsWith("L")) {
            return descriptorValue.length() > 2
                && descriptorValue.indexOf(';') == descriptorValue.length() - 1;
        }
        return descriptorValue.startsWith("[")
            && skipArrayDescriptor(descriptorValue, 0) == descriptorValue.length();
    }

    private static boolean voidReturn(final String descriptor) {
        final Optional<String> value = returnDescriptor(descriptor);
        return value.isPresent() && "V".equals(value.orElseThrow());
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
                if (end <= index + 1) {
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

    private static boolean parametersMatchDescriptor(
        final String descriptor,
        final List<String> parameters
    ) {
        final int separator = descriptor.indexOf(')');
        if (!descriptor.startsWith("(") || separator < 1) {
            return false;
        }
        return String.join("", parameters).equals(descriptor.substring(1, separator));
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
            if (end <= index + 1) {
                return -1;
            }
            return end + 1;
        }
        return -1;
    }
}
