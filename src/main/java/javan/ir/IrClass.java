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
 * @param enumType whether this class is an enum
 * @param assignableJvmNames transitive assignable JVM internal names including self
 */
public record IrClass(
    String jvmName,
    String symbol,
    List<IrField> fields,
    List<IrField> staticFields,
    List<String> enumConstants,
    boolean enumType,
    List<String> assignableJvmNames
) {
    /**
     * Creates class metadata without static fields.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     */
    public IrClass(final String jvmName, final String symbol, final List<IrField> fields) {
        this(jvmName, symbol, fields, List.of(), List.of(), false, List.of(jvmName));
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
        this(jvmName, symbol, fields, staticFields, List.of(), false, List.of(jvmName));
    }

    /**
     * Creates class metadata without an explicit enum-type marker.
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
        this(jvmName, symbol, fields, staticFields, enumConstants, false, List.of(jvmName));
    }

    /**
     * Creates class metadata with explicit enum flag but without extra assignability metadata.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     * @param staticFields lowered static field metadata
     * @param enumConstants enum constant names in declaration order
     * @param enumType whether this class is an enum
     */
    public IrClass(
        final String jvmName,
        final String symbol,
        final List<IrField> fields,
        final List<IrField> staticFields,
        final List<String> enumConstants,
        final boolean enumType
    ) {
        this(jvmName, symbol, fields, staticFields, enumConstants, enumType, List.of(jvmName));
    }
}
