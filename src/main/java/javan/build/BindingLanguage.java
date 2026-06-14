package javan.build;

import javan.util.Strings2;

import java.util.Optional;

/**
 * Binding languages generated for native libraries.
 */
public enum BindingLanguage {
    C,
    RUST,
    GO,
    PYTHON;

    /**
     * Parses a binding language.
     *
     * @param value raw value
     * @return parsed language when known
     */
    public static Optional<BindingLanguage> parse(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiUpperCase(value), '-', '_');
        for (final BindingLanguage language : values()) {
            if (language.name().equals(normalized)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
