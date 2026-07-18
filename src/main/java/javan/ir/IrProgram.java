package javan.ir;

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
 * @param enumDispatchConstants constant-specific enum implementation to constant name
 */
public record IrProgram(
    List<IrClass> classes,
    List<IrFunction> functions,
    List<IrDispatch> dispatches,
    String entryFunction,
    List<IrMaterializedLambdaTarget> materializedLambdaTargets,
    Map<String, String> enumDispatchConstants
) {
    /**
     * Creates a program without dispatch stubs.
     *
     * @param classes class metadata used by generated native structs
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrClass> classes, final List<IrFunction> functions, final String entryFunction) {
        this(classes, functions, List.of(), entryFunction, List.of(), Map.of());
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
        this(classes, functions, dispatches, entryFunction, List.of(), Map.of());
    }

    /**
     * Creates a program without object metadata.
     *
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrFunction> functions, final String entryFunction) {
        this(List.of(), functions, List.of(), entryFunction, List.of(), Map.of());
    }
}
