package javan.toolchain;

import javan.util.Strings2;

import java.util.Optional;

/**
 * Installed toolchain kind.
 */
public enum ToolchainKind {
    JDK;

    /**
     * Parses a toolchain kind.
     *
     * @param value kind text
     * @return parsed kind when recognized
     */
    public static Optional<ToolchainKind> parse(final String value) {
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        final String normalized = Strings2.toAsciiUpperCase(Strings2.trimAscii(value));
        for (final ToolchainKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the stable config value.
     *
     * @return stable value
     */
    public String value() {
        return Strings2.toAsciiLowerCase(name());
    }
}
