package javan.util;

import java.util.List;

/**
 * Small deterministic JSON rendering helpers.
 */
public final class Json {
    private Json() {
    }

    /**
     * Renders a string value.
     *
     * @param value string
     * @return JSON string
     */
    public static String string(final String value) {
        final StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch == '\\') {
                result.append("\\\\");
            } else if (ch == '"') {
                result.append("\\\"");
            } else if (ch == '\n') {
                result.append("\\n");
            } else if (ch == '\r') {
                result.append("\\r");
            } else if (ch == '\t') {
                result.append("\\t");
            } else if (ch < 0x20) {
                appendUnicodeEscape(result, ch);
            } else {
                result.append(ch);
            }
        }
        return result.append('"').toString();
    }

    /**
     * Renders a string array.
     *
     * @param values strings
     * @return JSON array
     */
    public static String stringList(final List<String> values) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(string(values.get(index)));
        }
        result.append(']');
        return result.toString();
    }

    /**
     * Renders an int array.
     *
     * @param values ints
     * @return JSON array
     */
    public static String intList(final List<Integer> values) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index).intValue());
        }
        result.append(']');
        return result.toString();
    }

    private static void appendUnicodeEscape(final StringBuilder result, final char value) {
        result.append("\\u");
        result.append(hex((value >>> 12) & 0xF));
        result.append(hex((value >>> 8) & 0xF));
        result.append(hex((value >>> 4) & 0xF));
        result.append(hex(value & 0xF));
    }

    private static char hex(final int value) {
        if (value < 10) {
            return (char) ('0' + value);
        }
        return (char) ('a' + (value - 10));
    }
}
