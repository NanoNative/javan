package javan.build;

import javan.analysis.EntryPoint;
import javan.codegen.BytecodeToIR;

import java.util.List;

/**
 * Resolved native library export.
 *
 * @param entryPoint Java method entry point
 * @param symbol exported C symbol
 * @param parameterTypes exported C ABI parameter types
 * @param returnType exported C ABI return type
 */
public record ExportedMethod(
    EntryPoint entryPoint,
    String symbol,
    List<AbiType> parameterTypes,
    AbiType returnType
) {
    /**
     * Returns the internal lowered function symbol.
     *
     * @return internal C symbol
     */
    public String internalSymbol() {
        return BytecodeToIR.symbol(entryPoint);
    }

    /**
     * Returns the ABI v2 result-wrapper symbol.
     *
     * @return try wrapper symbol
     */
    public String trySymbol() {
        final String prefix = "javan_export_";
        if (symbol.startsWith(prefix)) {
            return "javan_try_" + symbol.substring(prefix.length());
        }
        return "javan_try_" + symbol;
    }

    /**
     * Returns the display name used in diagnostics.
     *
     * @return display name
     */
    public String display() {
        return entryPoint.display();
    }
}
