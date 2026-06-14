package javan.profile;

import javan.util.Strings2;

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
        final String normalized = Strings2.toAsciiUpperCase(Strings2.trimAscii(value));
        for (final Profile profile : values()) {
            if (profile.name().equals(normalized)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the stable CLI spelling.
     *
     * @return lowercase profile name
     */
    public String cliName() {
        return Strings2.toAsciiLowerCase(name());
    }
}
