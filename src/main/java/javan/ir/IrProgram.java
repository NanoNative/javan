package javan.ir;

import javan.classfile.ServiceProvider;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lowered program independent of JVM bytecode.
 *
 * @param classes class metadata used by generated native structs
 * @param functions functions in generation order
 * @param dispatches closed-world dispatch stubs
 * @param entryFunction entry function C symbol
 * @param materializedLambdaTargets generated uncaptured lambda targets
 * @param classInitializationDependencies initialization owner to ordered prerequisite owners
 * @param enumDispatchConstants constant-specific enum implementation to constant name
 * @param classTypeIds stable generated type IDs by JVM class name
 * @param reflectedClasses classes whose declared methods are available to reflection
 * @param serviceUses services consumed by the application module
 * @param serviceProviders validated closed-world service providers
 */
public record IrProgram(
    List<IrClass> classes,
    List<IrFunction> functions,
    List<IrDispatch> dispatches,
    String entryFunction,
    List<IrMaterializedLambdaTarget> materializedLambdaTargets,
    Map<String, List<String>> classInitializationDependencies,
    Map<String, String> enumDispatchConstants,
    Map<String, Integer> classTypeIds,
    List<IrReflectedClass> reflectedClasses,
    List<String> serviceUses,
    Map<String, List<ServiceProvider>> serviceProviders
) {
    public IrProgram {
        reflectedClasses = List.copyOf(reflectedClasses);
        serviceUses = List.copyOf(serviceUses);
        final Map<String, List<ServiceProvider>> copiedProviders = new LinkedHashMap<>();
        for (final Map.Entry<String, List<ServiceProvider>> entry : serviceProviders.entrySet()) {
            copiedProviders.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        serviceProviders = Collections.unmodifiableMap(copiedProviders);
        final Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : classInitializationDependencies.entrySet()) {
            dependencies.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        classInitializationDependencies = Collections.unmodifiableMap(dependencies);
        final Set<Integer> assignedTypeIds = new HashSet<>();
        for (final IrClass classInfo : classes) {
            final Integer typeId = classTypeIds.get(classInfo.jvmName());
            if (typeId == null || typeId.intValue() <= 0 || !assignedTypeIds.add(typeId)) {
                throw new IllegalArgumentException("Invalid class type ID: " + classInfo.jvmName());
            }
        }
        if (classTypeIds.size() != classes.size()) {
            throw new IllegalArgumentException("Class type IDs must match generated classes");
        }
        classTypeIds = Collections.unmodifiableMap(new LinkedHashMap<>(classTypeIds));
    }

    /** Creates a program without module service-use metadata. */
    public IrProgram(
        final List<IrClass> classes,
        final List<IrFunction> functions,
        final List<IrDispatch> dispatches,
        final String entryFunction,
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets,
        final Map<String, List<String>> classInitializationDependencies,
        final Map<String, String> enumDispatchConstants,
        final Map<String, Integer> classTypeIds,
        final List<IrReflectedClass> reflectedClasses,
        final Map<String, List<ServiceProvider>> serviceProviders
    ) {
        this(classes, functions, dispatches, entryFunction, materializedLambdaTargets,
            classInitializationDependencies, enumDispatchConstants, classTypeIds, reflectedClasses, List.of(),
            serviceProviders);
    }

    /** Creates a program without service-provider metadata. */
    public IrProgram(
        final List<IrClass> classes,
        final List<IrFunction> functions,
        final List<IrDispatch> dispatches,
        final String entryFunction,
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets,
        final Map<String, List<String>> classInitializationDependencies,
        final Map<String, String> enumDispatchConstants,
        final Map<String, Integer> classTypeIds,
        final List<IrReflectedClass> reflectedClasses
    ) {
        this(classes, functions, dispatches, entryFunction, materializedLambdaTargets,
            classInitializationDependencies, enumDispatchConstants, classTypeIds, reflectedClasses, List.of(), Map.of());
    }

    /** Creates a program without external reflected-class metadata. */
    public IrProgram(
        final List<IrClass> classes,
        final List<IrFunction> functions,
        final List<IrDispatch> dispatches,
        final String entryFunction,
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets,
        final Map<String, List<String>> classInitializationDependencies,
        final Map<String, String> enumDispatchConstants,
        final Map<String, Integer> classTypeIds
    ) {
        this(
            classes,
            functions,
            dispatches,
            entryFunction,
            materializedLambdaTargets,
            classInitializationDependencies,
            enumDispatchConstants,
            classTypeIds,
            List.of(),
            Map.of()
        );
    }

    /** Creates a program with type IDs derived from class order. */
    public IrProgram(
        final List<IrClass> classes,
        final List<IrFunction> functions,
        final List<IrDispatch> dispatches,
        final String entryFunction,
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets,
        final Map<String, List<String>> classInitializationDependencies,
        final Map<String, String> enumDispatchConstants
    ) {
        this(
            classes,
            functions,
            dispatches,
            entryFunction,
            materializedLambdaTargets,
            classInitializationDependencies,
            enumDispatchConstants,
            sequentialTypeIds(classes),
            List.of(),
            Map.of()
        );
    }

    /**
     * Creates a program without dispatch stubs.
     *
     * @param classes class metadata used by generated native structs
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrClass> classes, final List<IrFunction> functions, final String entryFunction) {
        this(classes, functions, List.of(), entryFunction, List.of(), Map.of(), Map.of(), sequentialTypeIds(classes), List.of(), Map.of());
    }

    /**
     * Creates a program without FunctionOrNull lambda metadata.
     *
     * @param classes class metadata used by generated native structs
     * @param functions functions in generation order
     * @param dispatches closed-world dispatch stubs
     * @param entryFunction entry function C symbol
     */
    public IrProgram(
        final List<IrClass> classes,
        final List<IrFunction> functions,
        final List<IrDispatch> dispatches,
        final String entryFunction
    ) {
        this(classes, functions, dispatches, entryFunction, List.of(), Map.of(), Map.of(), sequentialTypeIds(classes), List.of(), Map.of());
    }

    /** Creates a program without class-initialization dependency metadata. */
    public IrProgram(
        final List<IrClass> classes,
        final List<IrFunction> functions,
        final List<IrDispatch> dispatches,
        final String entryFunction,
        final List<IrMaterializedLambdaTarget> materializedLambdaTargets,
        final Map<String, String> enumDispatchConstants
    ) {
        this(
            classes,
            functions,
            dispatches,
            entryFunction,
            materializedLambdaTargets,
            Map.of(),
            enumDispatchConstants,
            sequentialTypeIds(classes),
            List.of(),
            Map.of()
        );
    }

    /**
     * Creates a program without object metadata.
     *
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrFunction> functions, final String entryFunction) {
        this(List.of(), functions, List.of(), entryFunction, List.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of());
    }

    private static Map<String, Integer> sequentialTypeIds(final List<IrClass> classes) {
        final Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < classes.size(); index++) {
            result.put(classes.get(index).jvmName(), index + 1);
        }
        return result;
    }
}
