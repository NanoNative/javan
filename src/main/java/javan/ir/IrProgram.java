package javan.ir;

import java.util.List;

/**
 * Lowered program independent of JVM bytecode.
 *
 * @param classes class metadata used by generated native structs
 * @param functions functions in generation order
 * @param dispatches closed-world dispatch stubs
 * @param entryFunction entry function C symbol
 * @param materializedLambdaTargets generated uncaptured lambda targets
 */
public record IrProgram(
    List<IrClass> classes,
    List<IrFunction> functions,
    List<IrDispatch> dispatches,
    String entryFunction,
    List<IrMaterializedLambdaTarget> materializedLambdaTargets
) {
    /**
     * Creates a program without dispatch stubs.
     *
     * @param classes class metadata used by generated native structs
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrClass> classes, final List<IrFunction> functions, final String entryFunction) {
        this(classes, functions, List.of(), entryFunction, List.of());
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
        this(classes, functions, dispatches, entryFunction, List.of());
    }

    /**
     * Creates a program without object metadata.
     *
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrFunction> functions, final String entryFunction) {
        this(List.of(), functions, List.of(), entryFunction, List.of());
    }
}
