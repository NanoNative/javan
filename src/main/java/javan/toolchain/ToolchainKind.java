package javan.toolchain;

import java.util.Locale;
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
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ToolchainKind.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (final IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /**
     * Returns the stable config value.
     *
     * @return stable value
     */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
