package javan.ir;

import java.util.List;

/**
 * Lowered class metadata used by generated native structs.
 *
 * @param jvmName JVM internal class name
 * @param symbol C struct symbol
 * @param fields complete root-to-leaf instance layout for generated objects
 * @param staticFields lowered static field metadata
 * @param enumConstants enum constant names in declaration order
 * @param cloneable whether instances may be cloned through Object.clone
 */
public record IrClass(
    String jvmName,
    String symbol,
    List<IrField> fields,
    List<IrField> staticFields,
    List<String> enumConstants,
    boolean cloneable
) {
    /**
     * Creates class metadata without static fields.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     */
    public IrClass(final String jvmName, final String symbol, final List<IrField> fields) {
        this(jvmName, symbol, fields, List.of(), List.of(), false);
    }

    /**
     * Creates class metadata without enum constants.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     * @param staticFields lowered static field metadata
     */
    public IrClass(final String jvmName, final String symbol, final List<IrField> fields, final List<IrField> staticFields) {
        this(jvmName, symbol, fields, staticFields, List.of(), false);
    }

    /**
     * Creates class metadata without cloneability.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     * @param staticFields lowered static field metadata
     * @param enumConstants enum constant names in declaration order
     */
    public IrClass(
        final String jvmName,
        final String symbol,
        final List<IrField> fields,
        final List<IrField> staticFields,
        final List<String> enumConstants
    ) {
        this(jvmName, symbol, fields, staticFields, enumConstants, false);
    }
}
