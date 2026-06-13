package javan.ir;

import java.util.List;

/**
 * Lowered class metadata used by generated native structs.
 *
 * @param jvmName JVM internal class name
 * @param symbol C struct symbol
 * @param fields lowered field metadata
 * @param staticFields lowered static field metadata
 */
public record IrClass(String jvmName, String symbol, List<IrField> fields, List<IrField> staticFields) {
    /**
     * Creates class metadata without static fields.
     *
     * @param jvmName JVM internal class name
     * @param symbol C struct symbol
     * @param fields lowered field metadata
     */
    public IrClass(final String jvmName, final String symbol, final List<IrField> fields) {
        this(jvmName, symbol, fields, List.of());
    }
}
