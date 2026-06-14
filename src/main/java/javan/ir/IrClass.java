package javan.ir;

import java.util.List;

/**
 * Lowered class metadata used by generated native structs.
 *
 * @param jvmName JVM internal class name
 * @param symbol C struct symbol
 * @param fields lowered field metadata
 * @param staticFields lowered static field metadata
 * @param enumConstants enum constant names in declaration order
 */
public record IrClass(
    String jvmName,
    String symbol,
    List<IrField> fields,
    List<IrField> staticFields,
    List<String> enumConstants
) {
    /**
     * Creates class metadata without static fields.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     */
    public IrClass(final String jvmName, final String symbol, final List<IrField> fields) {
        this(jvmName, symbol, fields, List.of(), List.of());
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
        this(jvmName, symbol, fields, staticFields, List.of());
    }
}
