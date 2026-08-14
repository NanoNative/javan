package javan.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public record IrProgram(
    List<IrClass> classes,
    List<IrFunction> functions,
    List<IrDispatch> dispatches,
    String entryFunction,
    List<IrMaterializedLambdaTarget> materializedLambdaTargets,
    Map<String, List<String>> classInitializationDependencies,
    Map<String, String> enumDispatchConstants
) {
    public IrProgram {
        final Map<String, List<String>> dependencies = new LinkedHashMap<>();
        for (final Map.Entry<String, List<String>> entry : classInitializationDependencies.entrySet()) {
            dependencies.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        classInitializationDependencies = Collections.unmodifiableMap(dependencies);
    }

    /**
     * Creates a program without dispatch stubs.
     *
     * @param classes class metadata used by generated native structs
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrClass> classes, final List<IrFunction> functions, final String entryFunction) {
        this(classes, functions, List.of(), entryFunction, List.of(), Map.of(), Map.of());
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
        this(classes, functions, dispatches, entryFunction, List.of(), Map.of(), Map.of());
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
        this(classes, functions, dispatches, entryFunction, materializedLambdaTargets, Map.of(), enumDispatchConstants);
    }

    /**
     * Creates a program without object metadata.
     *
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrFunction> functions, final String entryFunction) {
        this(List.of(), functions, List.of(), entryFunction, List.of(), Map.of(), Map.of());
    }
}
