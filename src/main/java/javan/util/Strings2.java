package javan.util;

import java.util.Locale;

/**
 * Small string helpers used by the CLI without adding dependencies.
 */
public final class Strings2 {
    private Strings2() {
    }

    /**
     * Returns true when the value is null, empty, or only whitespace.
     *
     * @param value input value
     * @return true when the value is blank
     */
    public static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    /**
     * Converts a class name or path fragment into a conservative executable name.
     *
     * @param value raw value
     * @return normalized executable name
     */
    public static String executableName(final String value) {
        final String fallback = isBlank(value) ? "app" : value;
        final int dot = fallback.lastIndexOf('.');
        final String simple = dot >= 0 ? fallback.substring(dot + 1) : fallback;
        final String normalized = simple.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        return normalized.isBlank() ? "app" : normalized;
    }

    /**
     * Converts JVM internal names into source-style names.
     *
     * @param internalName JVM internal class name
     * @return dot separated class name
     */
    public static String externalClassName(final String internalName) {
        return internalName.replace('/', '.');
    }

    /**
     * Converts source-style names into JVM internal names.
     *
     * @param externalName dot separated class name
     * @return JVM internal class name
     */
    public static String internalClassName(final String externalName) {
        return externalName.replace('.', '/');
    }
}
