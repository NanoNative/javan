package javan.ir;

import java.util.List;

/**
 * Lowered class metadata used by generated native structs.
 *
 * @param jvmName JVM internal class name
 * @param superName JVM internal superclass name, or an empty string for {@code java.lang.Object}
 * @param symbol C struct symbol
 * @param fields complete root-to-leaf instance layout for generated objects
 * @param staticFields lowered static field metadata
 * @param enumConstants enum constant names in declaration order
 * @param enumClass whether this class is declared as an enum, including empty enums
 * @param cloneable whether instances may be cloned through Object.clone
 * @param interfaces directly declared JVM interfaces used for runtime type checks
 */
public record IrClass(
    String jvmName,
    String superName,
    String symbol,
    List<IrField> fields,
    List<IrField> staticFields,
    List<String> enumConstants,
    boolean enumClass,
    boolean cloneable,
    List<String> interfaces
) {
    public IrClass {
        interfaces = List.copyOf(interfaces);
    }

    /**
     * Creates class metadata without static fields.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     */
    public IrClass(final String jvmName, final String symbol, final List<IrField> fields) {
        this(jvmName, "java/lang/Object", symbol, fields, List.of(), List.of(), false, false, List.of());
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
        this(jvmName, "java/lang/Object", symbol, fields, staticFields, List.of(), false, false, List.of());
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
        this(jvmName, "java/lang/Object", symbol, fields, staticFields, enumConstants, !enumConstants.isEmpty(), false, List.of());
    }

    /**
     * Creates compatibility class metadata, deriving enum state from declaration constants.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     * @param staticFields lowered static field metadata
     * @param enumConstants enum constant names in declaration order
     * @param cloneable whether instances may be cloned through Object.clone
     */
    public IrClass(
        final String jvmName,
        final String symbol,
        final List<IrField> fields,
        final List<IrField> staticFields,
        final List<String> enumConstants,
        final boolean cloneable
    ) {
        this(jvmName, "java/lang/Object", symbol, fields, staticFields, enumConstants, !enumConstants.isEmpty(), cloneable, List.of());
    }

    /** Creates compatibility metadata without explicit superclass information. */
    public IrClass(
        final String jvmName,
        final String symbol,
        final List<IrField> fields,
        final List<IrField> staticFields,
        final List<String> enumConstants,
        final boolean enumClass,
        final boolean cloneable
    ) {
        this(jvmName, "java/lang/Object", symbol, fields, staticFields, enumConstants, enumClass, cloneable, List.of());
    }

    /**
     * Creates compatibility metadata with directly declared interfaces.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered instance fields
     * @param staticFields lowered static field metadata
     * @param enumConstants enum constant names in declaration order
     * @param enumClass whether this class is declared as an enum
     * @param cloneable whether instances may be cloned through Object.clone
     * @param interfaces directly declared JVM interfaces
     */
    public IrClass(
        final String jvmName,
        final String symbol,
        final List<IrField> fields,
        final List<IrField> staticFields,
        final List<String> enumConstants,
        final boolean enumClass,
        final boolean cloneable,
        final List<String> interfaces
    ) {
        this(jvmName, "java/lang/Object", symbol, fields, staticFields, enumConstants, enumClass, cloneable, interfaces);
    }

    /**
     * Creates full class metadata without explicit interface information.
     *
     * @param jvmName JVM internal class name
     * @param superName JVM internal superclass name
     * @param symbol C struct symbol
     * @param fields lowered instance fields
     * @param staticFields lowered static field metadata
     * @param enumConstants enum constant names in declaration order
     * @param enumClass whether this class is declared as an enum
     * @param cloneable whether instances may be cloned through Object.clone
     */
    public IrClass(
        final String jvmName,
        final String superName,
        final String symbol,
        final List<IrField> fields,
        final List<IrField> staticFields,
        final List<String> enumConstants,
        final boolean enumClass,
        final boolean cloneable
    ) {
        this(jvmName, superName, symbol, fields, staticFields, enumConstants, enumClass, cloneable, List.of());
    }
}
