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
            switch (ch) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        result.append("\\u").append(String.format("%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
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
        return "[" + String.join(", ", values.stream().map(Json::string).toList()) + "]";
    }

    /**
     * Renders an int array.
     *
     * @param values ints
     * @return JSON array
     */
    public static String intList(final List<Integer> values) {
        return "[" + String.join(", ", values.stream().map(String::valueOf).toList()) + "]";
    }
}
