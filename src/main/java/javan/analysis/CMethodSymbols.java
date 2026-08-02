package javan.analysis;

import java.util.Objects;

/**
 * Canonical portable C symbols for Java methods.
 */
public final class CMethodSymbols {
    private CMethodSymbols() {
    }

    /**
     * Returns the stable generated C symbol for a Java method.
     *
     * @param entryPoint Java method identity
     * @return portable C symbol
     * @throws NullPointerException when {@code entryPoint} is null
     */
    public static String symbol(final EntryPoint entryPoint) {
        final EntryPoint method = Objects.requireNonNull(entryPoint, "entryPoint");
        return "javan_" + (method.className() + "_" + method.methodName() + "_" + method.descriptor())
            .replace('/', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('(', '_')
            .replace(')', '_')
            .replace(';', '_')
            .replace('[', '_')
            .replace(']', '_')
            .replace('$', '_')
            .replace('.', '_');
    }
}
