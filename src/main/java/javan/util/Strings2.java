package javan.util;

import java.util.Objects;

/**
 * Small string helpers used by the CLI without adding dependencies.
 */
public final class Strings2 {
    private Strings2() {
    }

    /**
     * Returns true when the value is null, empty, or only ASCII whitespace.
     *
     * @param value input value
     * @return true when the value is blank
     */
    public static boolean isBlank(final String value) {
        if (value == null || value.length() == 0) {
            return true;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!asciiWhitespace(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts a class name or path fragment into a conservative executable name.
     *
     * @param value raw value
     * @return normalized executable name
     */
    public static String executableName(final String value) {
        final String fallback = isBlank(value) ? "app" : value;
        final int start = lastDot(fallback) + 1;
        final String normalized = executableToken(fallback, start, fallback.length());
        return isBlank(normalized) ? "app" : normalized;
    }

    private static String executableToken(final String value, final int start, final int end) {
        final StringBuilder result = new StringBuilder();
        boolean previousReplacement = false;
        for (int index = start; index < end; index++) {
            final char ch = asciiLower(value.charAt(index));
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '.' || ch == '_' || ch == '-') {
                result.append(ch);
                previousReplacement = false;
            } else if (!previousReplacement) {
                result.append('-');
                previousReplacement = true;
            }
        }
        return result.toString();
    }

    /**
     * Trims ASCII whitespace from both sides.
     *
     * @param value raw value
     * @return trimmed value
     */
    public static String trimAscii(final String value) {
        Objects.requireNonNull(value, "value");
        int start = 0;
        int end = value.length();
        while (start < end && asciiWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && asciiWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return copyRange(value, start, end);
    }

    /**
     * Removes ASCII whitespace from the end.
     *
     * @param value raw value
     * @return value without trailing ASCII whitespace
     */
    public static String stripTrailingAscii(final String value) {
        Objects.requireNonNull(value, "value");
        int end = value.length();
        while (end > 0 && asciiWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return copyRange(value, 0, end);
    }

    /**
     * Copies a character range.
     *
     * @param value raw value
     * @param start inclusive start
     * @param end exclusive end
     * @return copied range
     */
    public static String slice(final String value, final int start, final int end) {
        Objects.requireNonNull(value, "value");
        if (start < 0 || end < start || end > value.length()) {
            throw new IllegalArgumentException("Invalid string range");
        }
        return copyRange(value, start, end);
    }

    /**
     * Replaces one character with another without relying on JDK String.replace.
     *
     * @param value raw value
     * @param oldChar replaced character
     * @param newChar replacement character
     * @return replaced value
     */
    public static String replaceChar(final String value, final char oldChar, final char newChar) {
        Objects.requireNonNull(value, "value");
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch == oldChar) {
                result.append(newChar);
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Converts ASCII letters to upper case.
     *
     * @param value raw value
     * @return ASCII-uppercase value
     */
    public static String toAsciiUpperCase(final String value) {
        Objects.requireNonNull(value, "value");
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch >= 'a' && ch <= 'z') {
                result.append((char) (ch - ('a' - 'A')));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Converts ASCII letters to lower case.
     *
     * @param value raw value
     * @return ASCII-lowercase value
     */
    public static String toAsciiLowerCase(final String value) {
        Objects.requireNonNull(value, "value");
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            result.append(asciiLower(value.charAt(index)));
        }
        return result.toString();
    }

    /**
     * Compares ASCII command tokens without locale-sensitive case conversion.
     *
     * @param left first value
     * @param right second value
     * @return true when both values match ignoring ASCII case
     * @throws NullPointerException when either value is null
     */
    public static boolean equalsAsciiIgnoreCase(final String left, final String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.length() != right.length()) {
            return false;
        }
        for (int index = 0; index < left.length(); index++) {
            if (asciiLower(left.charAt(index)) != asciiLower(right.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares strings by their character values without locale rules.
     *
     * @param left first value
     * @param right second value
     * @return negative, zero, or positive comparison result
     */
    public static int compareAscii(final String left, final String right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        final int length = Math.min(left.length(), right.length());
        for (int index = 0; index < length; index++) {
            final int difference = left.charAt(index) - right.charAt(index);
            if (difference != 0) {
                return difference;
            }
        }
        return left.length() - right.length();
    }

    /**
     * Returns true when the value can be represented by the current native string subset.
     *
     * @param value input value
     * @return true when every character is non-NUL ASCII
     */
    public static boolean isRuntimeAsciiStringConstant(final String value) {
        Objects.requireNonNull(value, "value");
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '\0' || current > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static char asciiLower(final char value) {
        if (value >= 'A' && value <= 'Z') {
            return (char) (value + ('a' - 'A'));
        }
        return value;
    }

    private static boolean asciiWhitespace(final char value) {
        if (value == ' ') {
            return true;
        }
        if (value == '\t') {
            return true;
        }
        if (value == '\n') {
            return true;
        }
        if (value == '\r') {
            return true;
        }
        if (value == '\f') {
            return true;
        }
        return false;
    }

    private static int lastDot(final String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (value.charAt(index) == '.') {
                return index;
            }
        }
        return -1;
    }

    private static String copyRange(final String value, final int start, final int end) {
        final StringBuilder result = new StringBuilder();
        for (int index = start; index < end; index++) {
            result.append(value.charAt(index));
        }
        return result.toString();
    }

    /**
     * Converts JVM internal names into source-style names.
     *
     * @param internalName JVM internal class name
     * @return dot separated class name
     */
    public static String externalClassName(final String internalName) {
        return replaceChar(internalName, '/', '.');
    }

    /**
     * Converts source-style names into JVM internal names.
     *
     * @param externalName dot separated class name
     * @return JVM internal class name
     */
    public static String internalClassName(final String externalName) {
        return replaceChar(externalName, '.', '/');
    }

    /**
     * Formats a long value as lowercase hexadecimal using Java's unsigned two's-complement representation.
     *
     * @param value long value
     * @return lowercase hexadecimal text
     */
    public static String hexLong(final long value) {
        if (value == 0L) {
            return "0";
        }
        final StringBuilder result = new StringBuilder();
        boolean started = false;
        for (int shift = 60; shift >= 0; shift -= 4) {
            final int digit = (int) ((value >>> shift) & 15L);
            if (digit != 0 || started) {
                result.append(hexDigit(digit));
                started = true;
            }
        }
        return result.toString();
    }

    private static char hexDigit(final int value) {
        return (char) (value < 10 ? '0' + value : 'a' + (value - 10));
    }
}
