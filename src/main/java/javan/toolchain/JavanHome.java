package javan.toolchain;

import javan.util.Strings2;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Resolves the global javan home directory.
 */
public final class JavanHome {
    public static final String PROPERTY = "javan.home";
    public static final String ENVIRONMENT = "JAVAN_HOME";

    private JavanHome() {
    }

    /**
     * Resolves the current process javan home.
     *
     * @return resolved absolute home path
     */
    public static Path resolve() {
        final String property = clean(System.getProperty(PROPERTY));
        if (property != null) {
            return normalize(Path.of(property));
        }
        final String environment = clean(System.getenv(ENVIRONMENT));
        if (environment != null) {
            return normalize(Path.of(environment));
        }
        return normalize(Path.of(System.getProperty("user.home", ".")).resolve(".javan"));
    }

    /**
     * Resolves javan home from explicit inputs.
     *
     * @param environment environment variables
     * @param properties system properties
     * @param userHome user home directory fallback
     * @return resolved absolute home path
     */
    public static Path resolve(
        final Map<String, String> environment,
        final Properties properties,
        final Path userHome
    ) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(userHome, "userHome");

        final String property = property(properties);
        if (property != null) {
            return normalize(Path.of(property));
        }
        final String environmentValue = environment(environment);
        if (environmentValue != null) {
            return normalize(Path.of(environmentValue));
        }
        return normalize(userHome.resolve(".javan"));
    }

    private static String property(final Properties properties) {
        return clean(properties.getProperty(PROPERTY));
    }

    private static String environment(final Map<String, String> environment) {
        return clean(environment.get(ENVIRONMENT));
    }

    private static String clean(final String value) {
        if (Strings2.isBlank(value)) {
            return null;
        }
        return Strings2.trimAscii(value);
    }

    private static Path normalize(final Path path) {
        return path.toAbsolutePath().normalize();
    }
}
