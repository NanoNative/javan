package javan.build;

import java.util.Arrays;
import java.util.Locale;
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
        return Arrays.stream(values())
            .filter(language -> language.name().equals(value.toUpperCase(Locale.ROOT).replace('-', '_')))
            .findFirst();
    }
}
