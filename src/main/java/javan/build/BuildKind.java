package javan.build;

import javan.util.Strings2;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Artifact shape produced by {@code javan build}.
 */
public enum BuildKind {
    APP,
    JAR,
    LIBRARY,
    STATICLIB,
    SHAREDLIB;

    /**
     * Parses a build kind.
     *
     * @param value raw value
     * @return parsed kind when known
     */
    public static Optional<BuildKind> parse(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiUpperCase(value), '-', '_');
        if ("LIB".equals(normalized)) {
            return Optional.of(LIBRARY);
        }
        for (final BuildKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns true when this build produces a library.
     *
     * @return true for static and shared libraries
     */
    public boolean library() {
        if (this == LIBRARY) {
            return true;
        }
        if (this == STATICLIB) {
            return true;
        }
        if (this == SHAREDLIB) {
            return true;
        }
        return false;
    }

    /**
     * Computes the artifact path.
     *
     * @param outputDirectory javan output directory
     * @param outputName logical artifact name
     * @return artifact path
     */
    public Path artifactPath(final Path outputDirectory, final String outputName) {
        if (this == APP) {
            return outputDirectory.resolve("bin").resolve(outputName);
        }
        if (this == JAR) {
            return outputDirectory.resolve("dist").resolve(outputName + ".jar");
        }
        if (this == LIBRARY) {
            return outputDirectory.resolve("dist").resolve("lib").resolve(outputName);
        }
        if (this == STATICLIB) {
            return outputDirectory.resolve("dist").resolve("lib" + outputName + ".a");
        }
        if (this == SHAREDLIB) {
            return outputDirectory.resolve("dist").resolve(sharedLibraryName(outputName));
        }
        throw new IllegalStateException("Unsupported build kind");
    }

    private static String sharedLibraryName(final String outputName) {
        final String os = Strings2.toAsciiLowerCase(System.getProperty("os.name", ""));
        if (os.contains("win")) {
            return outputName + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + outputName + ".dylib";
        }
        return "lib" + outputName + ".so";
    }
}
