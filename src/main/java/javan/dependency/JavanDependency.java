package javan.dependency;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Dependency declaration from {@code javan.mod}.
 *
 * @param scope dependency scope: main, test, or tool
 * @param notation raw dependency notation
 * @param kind local or coordinate
 * @param path resolved local path when {@code kind} is local, or resolved local cache path
 *             when {@code kind} is coordinate
 * @param line source line in {@code javan.mod}
 */
public record JavanDependency(String scope, String notation, String kind, Optional<Path> path, int line) {
    /**
     * Returns whether this dependency is a local filesystem dependency.
     *
     * @return true when local
     */
    public boolean local() {
        return "local".equals(kind);
    }

    /**
     * Returns whether this dependency is a Maven-style coordinate.
     *
     * @return true when coordinate
     */
    public boolean coordinate() {
        return "coordinate".equals(kind);
    }

    /**
     * Returns whether this dependency is a main/runtime dependency.
     *
     * @return true when main scoped
     */
    public boolean mainScope() {
        return "main".equals(scope);
    }

    /**
     * Returns this dependency with a resolved local path.
     *
     * @param resolvedPath resolved path
     * @return dependency with resolved path
     */
    public JavanDependency withPath(final Path resolvedPath) {
        return new JavanDependency(scope, notation, kind, Optional.of(resolvedPath), line);
    }
}
