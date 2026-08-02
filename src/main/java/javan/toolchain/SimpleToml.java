package javan.toolchain;

import javan.util.Strings2;

import java.util.Map;
import java.util.TreeMap;

/**
 * Small deterministic TOML reader for the limited javan configuration shape.
 */
public final class SimpleToml {
    private SimpleToml() {
    }

    /**
     * Parses supported TOML values into section-qualified keys.
     *
     * @param content TOML content
     * @return parsed key/value map
     * @throws NullPointerException when {@code content} is null
     * @throws IllegalArgumentException when TOML syntax is invalid or a fully-qualified key repeats
     */
    public static Map<String, String> parse(final String content) {
        final Map<String, String> values = new TreeMap<>();
        String section = "";
        String multilineArrayKey = null;
        StringBuilder multilineArray = null;
        int multilineArrayLastLine = 0;
        int lineNumber = 1;
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index < content.length() && content.charAt(index) != '\n') {
                continue;
            }
            int end = index;
            if (end > start && content.charAt(end - 1) == '\r') {
                end--;
            }
            final String line = Strings2.trimAscii(stripComment(Strings2.slice(content, start, end)));
            if (multilineArray != null) {
                multilineArray.append('\n').append(line);
                if (!line.isEmpty()) {
                    multilineArrayLastLine = lineNumber;
                }
                if (arrayComplete(multilineArray)) {
                    final String value = parseValue(multilineArray.toString(), lineNumber);
                    if (values.containsKey(multilineArrayKey)) {
                        throw new IllegalArgumentException("Duplicate TOML key: " + multilineArrayKey + " at line " + lineNumber);
                    }
                    values.put(multilineArrayKey, value);
                    multilineArrayKey = null;
                    multilineArray = null;
                } else if (index == content.length()) {
                    throw new IllegalArgumentException("Unterminated TOML array at line " + multilineArrayLastLine);
                }
                lineNumber++;
                start = index + 1;
                continue;
            }
            if (line.isEmpty()) {
                lineNumber++;
                start = index + 1;
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = Strings2.trimAscii(line.substring(1, line.length() - 1));
                if (section.isEmpty()) {
                    throw new IllegalArgumentException("Empty TOML section at line " + lineNumber);
                }
                lineNumber++;
                start = index + 1;
                continue;
            }
            final int equals = line.indexOf('=');
            if (equals < 1) {
                throw new IllegalArgumentException("Expected key = value at line " + lineNumber);
            }
            final String key = Strings2.trimAscii(line.substring(0, equals));
            final String fullKey = section.isEmpty() ? key : section + "." + key;
            final String rawValue = Strings2.trimAscii(line.substring(equals + 1));
            if (rawValue.startsWith("[") && !arrayComplete(rawValue)) {
                multilineArrayKey = fullKey;
                multilineArray = new StringBuilder(rawValue);
                multilineArrayLastLine = lineNumber;
                if (index == content.length()) {
                    throw new IllegalArgumentException("Unterminated TOML array at line " + lineNumber);
                }
                lineNumber++;
                start = index + 1;
                continue;
            }
            final String value = parseValue(rawValue, lineNumber);
            if (values.containsKey(fullKey)) {
                throw new IllegalArgumentException("Duplicate TOML key: " + fullKey + " at line " + lineNumber);
            }
            values.put(fullKey, value);
            lineNumber++;
            start = index + 1;
        }
        return Map.copyOf(values);
    }

    private static boolean arrayComplete(final CharSequence value) {
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '"' && !escaped) {
                quoted = !quoted;
            }
            if (character == ']' && !quoted) {
                return true;
            }
            escaped = character == '\\' && !escaped;
        }
        return false;
    }

    private static String stripComment(final String line) {
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            final char value = line.charAt(index);
            if (value == '"') {
                quoted = !quoted;
            }
            if (value == '#' && !quoted) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static String parseValue(final String raw, final int line) {
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        if ("true".equals(raw) || "false".equals(raw)) {
            return raw;
        }
        if (!Strings2.isBlank(raw)) {
            return raw;
        }
        throw new IllegalArgumentException("Missing TOML value at line " + line);
    }
}
