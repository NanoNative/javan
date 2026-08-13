package javan.toolchain;

import javan.util.Strings2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the native Javan executable currently running this process.
 *
 * <p>Native Javan records its own {@code argv[0]} before entering Java code. The environment
 * fallback is intentionally only for JVM-hosted development and package-test launches.</p>
 */
public final class JavanExecutable {
    public static final String PROPERTY = "javan.executable";
    public static final String ENVIRONMENT = "JAVAN_EXECUTABLE";

    private JavanExecutable() {
    }

    /**
     * Resolves the active native executable without searching {@code PATH}.
     *
     * @return executable path when the process can identify a real Javan launcher
     */
    public static Optional<Path> resolve() {
        final Optional<Path> property = path(System.getProperty(PROPERTY));
        if (property.isPresent()) {
            return property;
        }
        return path(System.getenv(ENVIRONMENT));
    }

    /**
     * Resolves the active native executable or explains why installation cannot continue.
     *
     * @return resolved native executable
     */
    public static Path require() {
        return resolve().orElseThrow(() -> new IllegalStateException(
            "javan install requires the packaged native javan executable; run the extracted bin/javan command"
        ));
    }

    private static Optional<Path> path(final String value) {
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        final Path candidate = Path.of(Strings2.trimAscii(value)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }
}
