package javan.toolchain;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        return resolve(System.getenv(), System.getProperties(), Path.of(System.getProperty("user.home", ".")));
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

        return property(properties)
            .or(() -> environment(environment))
            .map(Path::of)
            .map(JavanHome::normalize)
            .orElseGet(() -> normalize(userHome.resolve(".javan")));
    }

    private static Optional<String> property(final Properties properties) {
        return clean(properties.getProperty(PROPERTY));
    }

    private static Optional<String> environment(final Map<String, String> environment) {
        return clean(environment.get(ENVIRONMENT));
    }

    private static Optional<String> clean(final String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private static Path normalize(final Path path) {
        return path.toAbsolutePath().normalize();
    }
}
