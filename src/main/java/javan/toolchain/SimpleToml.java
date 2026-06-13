package javan.toolchain;

import java.util.Map;
import java.util.TreeMap;

final class SimpleToml {
    private SimpleToml() {
    }

    static Map<String, String> parse(final String content) {
        final Map<String, String> values = new TreeMap<>();
        String section = "";
        final String[] lines = content.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            final String line = stripComment(lines[index]).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                if (section.isEmpty()) {
                    throw new IllegalArgumentException("Empty TOML section at line " + (index + 1));
                }
                continue;
            }
            final int equals = line.indexOf('=');
            if (equals < 1) {
                throw new IllegalArgumentException("Expected key = value at line " + (index + 1));
            }
            final String key = line.substring(0, equals).trim();
            final String value = parseValue(line.substring(equals + 1).trim(), index + 1);
            final String fullKey = section.isEmpty() ? key : section + "." + key;
            values.put(fullKey, value);
        }
        return Map.copyOf(values);
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
        if (!raw.isBlank()) {
            return raw;
        }
        throw new IllegalArgumentException("Missing TOML value at line " + line);
    }
}
