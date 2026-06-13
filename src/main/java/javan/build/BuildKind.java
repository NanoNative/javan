package javan.build;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Artifact shape produced by {@code javan build}.
 */
public enum BuildKind {
    APP,
    JAR,
    STATICLIB,
    SHAREDLIB;

    /**
     * Parses a build kind.
     *
     * @param value raw value
     * @return parsed kind when known
     */
    public static Optional<BuildKind> parse(final String value) {
        return Arrays.stream(values())
            .filter(kind -> kind.name().equals(value.toUpperCase(Locale.ROOT).replace('-', '_')))
            .findFirst();
    }

    /**
     * Returns true when this build produces a library.
     *
     * @return true for static and shared libraries
     */
    public boolean library() {
        return this == STATICLIB || this == SHAREDLIB;
    }

    /**
     * Computes the artifact path.
     *
     * @param outputDirectory javan output directory
     * @param outputName logical artifact name
     * @return artifact path
     */
    public Path artifactPath(final Path outputDirectory, final String outputName) {
        return switch (this) {
            case APP -> outputDirectory.resolve("bin").resolve(outputName);
            case JAR -> outputDirectory.resolve("dist").resolve(outputName + ".jar");
            case STATICLIB -> outputDirectory.resolve("dist").resolve("lib" + outputName + ".a");
            case SHAREDLIB -> outputDirectory.resolve("dist").resolve(sharedLibraryName(outputName));
        };
    }

    private static String sharedLibraryName(final String outputName) {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return outputName + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + outputName + ".dylib";
        }
        return "lib" + outputName + ".so";
    }
}
