package javan.ir;

import java.util.List;

/**
 * Lowered program independent of JVM bytecode.
 *
 * @param classes class metadata used by generated native structs
 * @param functions functions in generation order
 * @param dispatches closed-world dispatch stubs
 * @param entryFunction entry function C symbol
 */
public record IrProgram(List<IrClass> classes, List<IrFunction> functions, List<IrDispatch> dispatches, String entryFunction) {
    /**
     * Creates a program without dispatch stubs.
     *
     * @param classes class metadata used by generated native structs
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrClass> classes, final List<IrFunction> functions, final String entryFunction) {
        this(classes, functions, List.of(), entryFunction);
    }

    /**
     * Creates a program without object metadata.
     *
     * @param functions functions in generation order
     * @param entryFunction entry function C symbol
     */
    public IrProgram(final List<IrFunction> functions, final String entryFunction) {
        this(List.of(), functions, List.of(), entryFunction);
    }
}
