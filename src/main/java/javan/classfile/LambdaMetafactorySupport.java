package javan.classfile;

import javan.analysis.EntryPoint;
import javan.ir.IrType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detects narrow LambdaMetafactory sites that javan can model as synthetic closure classes.
 */
public final class LambdaMetafactorySupport {
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_FINAL = 0x0010;
    private static final int REF_INVOKE_VIRTUAL = 5;
    private static final int REF_INVOKE_STATIC = 6;
    private static final int REF_INVOKE_INTERFACE = 9;

    private LambdaMetafactorySupport() {
    }

    /**
     * Scans every method for supported LambdaMetafactory sites.
     *
     * @param classes parsed classes
     * @return registry of supported lambda closure plans
     */
    public static Registry scan(final Map<String, ClassFile> classes) {
        return scan(classes, List.of());
    }

    /**
     * Scans the selected reachable methods for supported LambdaMetafactory sites.
     *
     * @param classes parsed classes
     * @param scopedMethods methods whose bodies should be scanned; empty means all
     * @return registry of supported lambda closure plans
     */
    public static Registry scan(final Map<String, ClassFile> classes, final List<EntryPoint> scopedMethods) {
        final Map<SiteKey, LambdaClosurePlan> bySite = new LinkedHashMap<>();
        final Map<String, LambdaClosurePlan> bySyntheticOwner = new LinkedHashMap<>();
        for (final ClassFile classFile : classes.values()) {
            for (final MethodInfo method : classFile.methods()) {
                if (!inScope(scopedMethods, classFile.name(), method)) {
                    continue;
                }
                if (method.code().isEmpty()) {
                    continue;
                }
                for (final Instruction instruction : method.code().orElseThrow().instructions()) {
                    if (instruction.opcode() != 186 || instruction.dynamicRef().isEmpty()) {
                        continue;
                    }
                    final Optional<LambdaClosurePlan> plan = plan(classes, classFile, method, instruction);
                    if (plan.isEmpty()) {
                        continue;
                    }
                    final LambdaClosurePlan lambdaClosurePlan = plan.orElseThrow();
                    bySite.put(new SiteKey(classFile.name(), method.name(), method.descriptor(), instruction.offset()), lambdaClosurePlan);
                    bySyntheticOwner.put(lambdaClosurePlan.syntheticOwner(), lambdaClosurePlan);
                }
            }
        }
        return new Registry(Map.copyOf(bySite), Map.copyOf(bySyntheticOwner));
    }

    private static boolean inScope(final List<EntryPoint> scopedMethods, final String owner, final MethodInfo method) {
        if (scopedMethods.isEmpty()) {
            return true;
        }
        for (final EntryPoint scoped : scopedMethods) {
            if (scoped.className().equals(owner)
                && scoped.methodName().equals(method.name())
                && scoped.descriptor().equals(method.descriptor())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<LambdaClosurePlan> plan(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final MethodInfo enclosingMethod,
        final Instruction instruction
    ) {
        final DynamicRef dynamicRef = instruction.dynamicRef().orElseThrow();
        if (!dynamicRef.isLambdaMetafactory()) {
            return Optional.empty();
        }
        if (dynamicRef.bootstrapValues().size() < 3) {
            return Optional.empty();
        }
        final BootstrapValue erasedSam = dynamicRef.bootstrapValues().get(0);
        final BootstrapValue implementation = dynamicRef.bootstrapValues().get(1);
        final BootstrapValue instantiatedSam = dynamicRef.bootstrapValues().get(2);
        if (erasedSam.kind() != BootstrapValue.Kind.METHOD_TYPE
            || instantiatedSam.kind() != BootstrapValue.Kind.METHOD_TYPE
            || implementation.kind() != BootstrapValue.Kind.METHOD_HANDLE
            || implementation.methodRef().isEmpty()
            || implementation.referenceKind().isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> interfaceOwner = returnObjectOwner(dynamicRef.descriptor());
        if (interfaceOwner.isEmpty()) {
            return Optional.empty();
        }
        final int referenceKind = implementation.referenceKind().orElseThrow().intValue();
        if (referenceKind != REF_INVOKE_STATIC
            && referenceKind != REF_INVOKE_VIRTUAL
            && referenceKind != REF_INVOKE_INTERFACE) {
            return Optional.empty();
        }
        final MethodRef implementationTarget = implementation.methodRef().orElseThrow();
        final List<String> captureDescriptors = parameterDescriptors(dynamicRef.descriptor());
        final List<IrType> erasedSamParameters = parameterIrTypes(erasedSam.value());
        final List<IrType> instantiatedSamParameters = parameterIrTypes(instantiatedSam.value());
        if (!sameIrTypes(erasedSamParameters, instantiatedSamParameters)) {
            return Optional.empty();
        }
        final Optional<ReceiverBinding> receiverBinding = receiverBinding(referenceKind, captureDescriptors, instantiatedSamParameters);
        if (receiverBinding.isEmpty()) {
            return Optional.empty();
        }
        if (!classes.containsKey(implementationTarget.owner())
            && !supportedBridgeTarget(classes, implementationTarget, referenceKind)) {
            return Optional.empty();
        }
        final List<IrType> implementationParameters = parameterIrTypes(implementationTarget.descriptor());
        if (!implementationMatches(receiverBinding.orElseThrow(), captureDescriptors, implementationParameters, instantiatedSamParameters)) {
            return Optional.empty();
        }
        if (returnIrType(implementationTarget.descriptor()) != returnIrType(instantiatedSam.value())) {
            return Optional.empty();
        }
        final String syntheticOwner = syntheticOwner(classFile.name(), enclosingMethod.name(), instruction.offset(), dynamicRef.name());
        return Optional.of(new LambdaClosurePlan(
            syntheticOwner,
            interfaceOwner.orElseThrow(),
            dynamicRef.name(),
            erasedSam.value(),
            captureDescriptors,
            implementationTarget,
            referenceKind,
            receiverBinding.orElseThrow(),
            classFile.source(),
            classFile.application()
        ));
    }

    private static boolean implementationMatches(
        final ReceiverBinding receiverBinding,
        final List<String> captureDescriptors,
        final List<IrType> implementationParameters,
        final List<IrType> samParameters
    ) {
        final List<IrType> expected = new ArrayList<>();
        int captureStart = 0;
        int samStart = 0;
        if (receiverBinding == ReceiverBinding.CAPTURE0) {
            if (captureDescriptors.isEmpty()) {
                return false;
            }
            captureStart = 1;
        } else if (receiverBinding == ReceiverBinding.FIRST_PARAMETER) {
            if (samParameters.isEmpty()) {
                return false;
            }
            samStart = 1;
        }
        for (int index = captureStart; index < captureDescriptors.size(); index++) {
            expected.add(irType(captureDescriptors.get(index)));
        }
        for (int index = samStart; index < samParameters.size(); index++) {
            expected.add(samParameters.get(index));
        }
        return sameIrTypes(expected, implementationParameters);
    }

    private static Optional<ReceiverBinding> receiverBinding(
        final int referenceKind,
        final List<String> captureDescriptors,
        final List<IrType> samParameters
    ) {
        if (referenceKind == REF_INVOKE_STATIC) {
            return Optional.of(ReceiverBinding.NONE);
        }
        if (referenceKind != REF_INVOKE_VIRTUAL && referenceKind != REF_INVOKE_INTERFACE) {
            return Optional.empty();
        }
        if (!captureDescriptors.isEmpty()) {
            return Optional.of(ReceiverBinding.CAPTURE0);
        }
        if (!samParameters.isEmpty() && samParameters.getFirst() == IrType.OBJECT) {
            return Optional.of(ReceiverBinding.FIRST_PARAMETER);
        }
        return Optional.empty();
    }

    private static boolean supportedBridgeTarget(
        final Map<String, ClassFile> classes,
        final MethodRef implementationTarget,
        final int referenceKind
    ) {
        if (!supportedFunctionalBridgeTarget(implementationTarget)) {
            return false;
        }
        return lowerableBridgeTargetsExist(classes, implementationTarget, referenceKind);
    }

    private static boolean supportedFunctionalBridgeTarget(final MethodRef implementationTarget) {
        final String owner = implementationTarget.owner();
        final String name = implementationTarget.name();
        final String descriptor = implementationTarget.descriptor();
        if ("java/util/function/Supplier".equals(owner)) {
            return "get".equals(name) && "()Ljava/lang/Object;".equals(descriptor);
        }
        if ("java/util/function/BooleanSupplier".equals(owner)) {
            return "getAsBoolean".equals(name) && "()Z".equals(descriptor);
        }
        if ("java/util/function/Predicate".equals(owner)) {
            return "test".equals(name) && "(Ljava/lang/Object;)Z".equals(descriptor);
        }
        if ("java/util/function/Function".equals(owner)) {
            return "apply".equals(name) && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(descriptor);
        }
        if ("java/util/function/Consumer".equals(owner)) {
            return "accept".equals(name) && "(Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("java/util/function/BiConsumer".equals(owner)) {
            return "accept".equals(name) && "(Ljava/lang/Object;Ljava/lang/Object;)V".equals(descriptor);
        }
        if ("java/lang/Runnable".equals(owner)) {
            return "run".equals(name) && "()V".equals(descriptor);
        }
        return false;
    }

    private static boolean lowerableBridgeTargetsExist(
        final Map<String, ClassFile> classes,
        final MethodRef target,
        final int referenceKind
    ) {
        for (final ClassFile candidate : classes.values()) {
            if (candidate.isInterface()) {
                continue;
            }
            if (referenceKind == REF_INVOKE_INTERFACE) {
                if (!isAssignableTo(classes, candidate.name(), target.owner())) {
                    continue;
                }
                if (lowerableResolvedInterfaceTarget(classes, candidate.name(), target).isPresent()) {
                    return true;
                }
                continue;
            }
            if (referenceKind == REF_INVOKE_VIRTUAL
                && isSubtypeOf(classes, candidate.name(), target.owner())
                && lowerableResolvedInvokeVirtualTarget(classes, candidate.name(), target).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameIrTypes(final List<IrType> left, final List<IrType> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            if (left.get(index) != right.get(index)) {
                return false;
            }
        }
        return true;
    }

    private static List<IrType> parameterIrTypes(final String descriptor) {
        final List<IrType> result = new ArrayList<>();
        for (final String parameterDescriptor : parameterDescriptors(descriptor)) {
            result.add(irType(parameterDescriptor));
        }
        return List.copyOf(result);
    }

    private static IrType returnIrType(final String descriptor) {
        final int end = descriptor.indexOf(')');
        if (end < 0 || end + 1 >= descriptor.length()) {
            throw new IllegalArgumentException("Invalid descriptor: " + descriptor);
        }
        final char type = descriptor.charAt(end + 1);
        if (type == 'V') {
            return IrType.VOID;
        }
        return irType(descriptor.substring(end + 1));
    }

    private static IrType irType(final String descriptor) {
        final char type = descriptor.charAt(0);
        if ("BCISZ".indexOf(type) >= 0) {
            return IrType.INT;
        }
        if (type == 'J') {
            return IrType.LONG;
        }
        if (type == 'F') {
            return IrType.FLOAT;
        }
        if (type == 'D') {
            return IrType.DOUBLE;
        }
        if (type == 'L' || type == '[' || type == 'C') {
            return type == 'C' ? IrType.INT : IrType.OBJECT;
        }
        throw new IllegalArgumentException("Unsupported descriptor: " + descriptor);
    }

    private static Optional<String> returnObjectOwner(final String descriptor) {
        final int end = descriptor.indexOf(')');
        if (end < 0 || end + 1 >= descriptor.length()) {
            return Optional.empty();
        }
        final String value = descriptor.substring(end + 1);
        if (!value.startsWith("L") || !value.endsWith(";")) {
            return Optional.empty();
        }
        return Optional.of(value.substring(1, value.length() - 1));
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
                index++;
            } else if (type == 'L') {
                final int end = descriptor.indexOf(';', index);
                if (end < 0) {
                    return List.of();
                }
                index = end + 1;
            } else if (type == '[') {
                index = skipArrayDescriptor(descriptor, index);
                if (index < 0) {
                    return List.of();
                }
            } else {
                return List.of();
            }
            result.add(descriptor.substring(start, index));
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

    private static String syntheticOwner(final String owner, final String methodName, final int offset, final String lambdaName) {
        return owner
            + "$$javan$lambda$"
            + sanitize(methodName)
            + "$"
            + sanitize(lambdaName)
            + "$"
            + offset;
    }

    private static String sanitize(final String value) {
        return value
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }

    private static Optional<EntryPoint> resolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) {
        String current = receiver;
        while (classes.containsKey(current)) {
            final ClassFile classFile = classes.get(current);
            if (classFile.method(target.name(), target.descriptor()).isPresent()) {
                return Optional.of(new EntryPoint(current, target.name(), target.descriptor()));
            }
            current = classFile.superName();
        }
        return Optional.empty();
    }

    private static Optional<EntryPoint> lowerableResolvedVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, target);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        return lowerableMethodTarget(classes, resolved.orElseThrow());
    }

    private static Optional<EntryPoint> lowerableResolvedInvokeVirtualTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) {
        final Optional<EntryPoint> resolved = resolvedVirtualTarget(classes, receiver, target);
        if (resolved.isPresent()) {
            return lowerableMethodTarget(classes, resolved.orElseThrow());
        }
        final List<String> inspectedInterfaces = new ArrayList<>();
        for (final String interfaceName : implementedInterfaces(classes, receiver)) {
            if (hasMoreSpecificInterface(classes, inspectedInterfaces, interfaceName)) {
                continue;
            }
            inspectedInterfaces.add(interfaceName);
            final Optional<EntryPoint> interfaceDefault = defaultInterfaceTarget(classes, interfaceName, target, new ArrayList<>());
            if (interfaceDefault.isPresent()) {
                return interfaceDefault;
            }
        }
        return Optional.empty();
    }

    private static Optional<EntryPoint> lowerableMethodTarget(
        final Map<String, ClassFile> classes,
        final EntryPoint entryPoint
    ) {
        final ClassFile owner = classes.get(entryPoint.className());
        if (owner == null) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = owner.method(entryPoint.methodName(), entryPoint.descriptor());
        if (method.isEmpty() || method.orElseThrow().code().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entryPoint);
    }

    private static Optional<EntryPoint> lowerableResolvedInterfaceTarget(
        final Map<String, ClassFile> classes,
        final String receiver,
        final MethodRef target
    ) {
        final Optional<EntryPoint> concreteTarget = lowerableResolvedVirtualTarget(classes, receiver, target);
        if (concreteTarget.isPresent()) {
            return concreteTarget;
        }
        final List<String> inspectedInterfaces = new ArrayList<>();
        for (final String interfaceName : implementedInterfaces(classes, receiver)) {
            if (hasMoreSpecificInterface(classes, inspectedInterfaces, interfaceName)) {
                continue;
            }
            inspectedInterfaces.add(interfaceName);
            if (!isAssignableTo(classes, interfaceName, target.owner())) {
                continue;
            }
            final Optional<EntryPoint> resolved = defaultInterfaceTarget(classes, interfaceName, target, new ArrayList<>());
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private static boolean hasMoreSpecificInterface(
        final Map<String, ClassFile> classes,
        final List<String> inspectedInterfaces,
        final String candidate
    ) {
        for (final String inspected : inspectedInterfaces) {
            if (isAssignableTo(classes, inspected, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> implementedInterfaces(final Map<String, ClassFile> classes, final String receiver) {
        final List<String> interfaces = new ArrayList<>();
        String current = receiver;
        while (current != null && !current.isEmpty()) {
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                break;
            }
            collectInterfaceNames(classes, classFile, interfaces, new ArrayList<>());
            current = classFile.superName();
        }
        return List.copyOf(interfaces);
    }

    private static void collectInterfaceNames(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final List<String> interfaces,
        final List<String> visited
    ) {
        for (final String interfaceName : classFile.interfaces()) {
            if (visited.contains(interfaceName)) {
                continue;
            }
            visited.add(interfaceName);
            if (!interfaces.contains(interfaceName)) {
                interfaces.add(interfaceName);
            }
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null) {
                collectInterfaceNames(classes, interfaceClass, interfaces, visited);
            }
        }
    }

    private static Optional<EntryPoint> defaultInterfaceTarget(
        final Map<String, ClassFile> classes,
        final String interfaceName,
        final MethodRef target,
        final List<String> visited
    ) {
        if (visited.contains(interfaceName)) {
            return Optional.empty();
        }
        visited.add(interfaceName);
        final ClassFile interfaceClass = classes.get(interfaceName);
        if (interfaceClass == null || !interfaceClass.isInterface()) {
            return Optional.empty();
        }
        final Optional<MethodInfo> method = interfaceClass.method(target.name(), target.descriptor());
        if (method.isPresent()) {
            if (method.orElseThrow().code().isPresent()) {
                return Optional.of(new EntryPoint(interfaceName, target.name(), target.descriptor()));
            }
            return Optional.empty();
        }
        for (final String parentInterface : interfaceClass.interfaces()) {
            final Optional<EntryPoint> resolved = defaultInterfaceTarget(classes, parentInterface, target, visited);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private static boolean isSubtypeOf(final Map<String, ClassFile> classes, final String candidate, final String expectedSuper) {
        String current = candidate;
        while (classes.containsKey(current)) {
            if (current.equals(expectedSuper)) {
                return true;
            }
            current = classes.get(current).superName();
        }
        return current.equals(expectedSuper);
    }

    private static boolean isAssignableTo(final Map<String, ClassFile> classes, final String candidate, final String expected) {
        String current = candidate;
        final List<String> visitedClasses = new ArrayList<>();
        while (current != null && !current.isEmpty()) {
            if (current.equals(expected)) {
                return true;
            }
            if (visitedClasses.contains(current)) {
                return false;
            }
            visitedClasses.add(current);
            final ClassFile classFile = classes.get(current);
            if (classFile == null) {
                return current.equals(expected);
            }
            if (hasInterface(classes, classFile, expected, new ArrayList<>())) {
                return true;
            }
            current = classFile.superName();
        }
        return false;
    }

    private static boolean hasInterface(
        final Map<String, ClassFile> classes,
        final ClassFile classFile,
        final String expected,
        final List<String> visited
    ) {
        for (final String interfaceName : classFile.interfaces()) {
            if (interfaceName.equals(expected)) {
                return true;
            }
            if (visited.contains(interfaceName)) {
                continue;
            }
            visited.add(interfaceName);
            final ClassFile interfaceClass = classes.get(interfaceName);
            if (interfaceClass != null && hasInterface(classes, interfaceClass, expected, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Supported lambda closure registry.
     *
     * @param bySite supported closure plans keyed by producing bytecode site
     * @param bySyntheticOwner supported closure plans keyed by synthetic owner
     */
    public record Registry(
        Map<SiteKey, LambdaClosurePlan> bySite,
        Map<String, LambdaClosurePlan> bySyntheticOwner
    ) {
        public Optional<LambdaClosurePlan> planForSite(
            final String owner,
            final String methodName,
            final String descriptor,
            final int offset
        ) {
            return Optional.ofNullable(bySite.get(new SiteKey(owner, methodName, descriptor, offset)));
        }

        public Optional<LambdaClosurePlan> planForSyntheticOwner(final String owner) {
            return Optional.ofNullable(bySyntheticOwner.get(owner));
        }

        public Map<String, ClassFile> expandedClasses(final Map<String, ClassFile> classes) {
            final Map<String, ClassFile> result = new LinkedHashMap<>();
            for (final ClassFile classFile : classes.values()) {
                result.put(classFile.name(), classFile);
            }
            for (final LambdaClosurePlan plan : bySyntheticOwner.values()) {
                result.put(plan.syntheticOwner(), plan.syntheticClass());
            }
            return Map.copyOf(result);
        }
    }

    public enum ReceiverBinding {
        NONE,
        CAPTURE0,
        FIRST_PARAMETER
    }

    /**
     * One supported closure-lowering plan.
     *
     * @param syntheticOwner generated synthetic receiver class
     * @param interfaceOwner functional-interface owner
     * @param methodName functional-interface method name
     * @param methodDescriptor functional-interface method descriptor
     * @param captureDescriptors captured argument descriptors from the invokedynamic call site
     * @param implementationTarget implementation method-handle target
     * @param implementationReferenceKind method-handle reference kind
     * @param receiverBinding receiver source for virtual/interface method handles
     * @param source source path for the enclosing class
     * @param application whether the enclosing class belongs to the application input
     */
    public record LambdaClosurePlan(
        String syntheticOwner,
        String interfaceOwner,
        String methodName,
        String methodDescriptor,
        List<String> captureDescriptors,
        MethodRef implementationTarget,
        int implementationReferenceKind,
        ReceiverBinding receiverBinding,
        Path source,
        boolean application
    ) {
        public EntryPoint wrapperEntryPoint() {
            return new EntryPoint(syntheticOwner, methodName, methodDescriptor);
        }

        public boolean matchesSam(final MethodRef target) {
            return interfaceOwner.equals(target.owner())
                && methodName.equals(target.name())
                && methodDescriptor.equals(target.descriptor());
        }

        public ClassFile syntheticClass() {
            final List<FieldInfo> fields = new ArrayList<>();
            for (int index = 0; index < captureDescriptors.size(); index++) {
                fields.add(new FieldInfo(0, "capture" + index, captureDescriptors.get(index)));
            }
            final MethodInfo samMethod = new MethodInfo(ACC_PUBLIC, methodName, methodDescriptor, Optional.empty());
            return new ClassFile(
                69,
                syntheticOwner,
                "java/lang/Object",
                ACC_FINAL,
                List.of(interfaceOwner),
                List.copyOf(fields),
                List.of(samMethod),
                source,
                application
            );
        }
    }

    /**
     * One producing bytecode site.
     *
     * @param owner enclosing owner
     * @param methodName enclosing method name
     * @param descriptor enclosing method descriptor
     * @param offset invokedynamic bytecode offset
     */
    public record SiteKey(String owner, String methodName, String descriptor, int offset) {
    }
}
