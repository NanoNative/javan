package javan.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Resolves the packaged javan version.
 */
public final class Version {
    private static final String FALLBACK_VERSION = "0.0.0-dev";
    private static final String RESOURCE = "javan-version.properties";
    private static final String VERSION = loadVersion();

    private Version() {
    }

    /**
     * Returns the version number.
     *
     * @return version number or a development fallback
     */
    public static String number() {
        return VERSION;
    }

    /**
     * Returns the human-readable CLI version line.
     *
     * @return version line
     */
    public static String full() {
        return "javan " + VERSION;
    }

    private static String loadVersion() {
        try (InputStream stream = Version.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return FALLBACK_VERSION;
            }
            final Properties properties = new Properties();
            properties.load(stream);
            final String version = properties.getProperty("version", FALLBACK_VERSION).trim();
            return version.isBlank() ? FALLBACK_VERSION : version;
        } catch (final IOException exception) {
            return FALLBACK_VERSION;
        }
    }
}
