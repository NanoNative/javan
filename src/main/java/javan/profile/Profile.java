package javan.profile;

import java.util.Locale;
import java.util.Optional;

/**
 * User-selected CLI profile.
 */
public enum Profile {
    CORE,
    SERVICE,
    LIBRARY,
    STRICT;

    /**
     * Parses a profile value.
     *
     * @param value raw CLI value
     * @return parsed profile, or empty when unsupported
     */
    public static Optional<Profile> parse(final String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "core" -> Optional.of(CORE);
            case "service" -> Optional.of(SERVICE);
            case "library" -> Optional.of(LIBRARY);
            case "strict" -> Optional.of(STRICT);
            default -> Optional.empty();
        };
    }

    /**
     * Returns the stable CLI spelling.
     *
     * @return lowercase profile name
     */
    public String cliName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
