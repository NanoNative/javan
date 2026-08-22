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
 * @param direct whether the dependency is declared directly in {@code javan.mod}
 * @param requestedBy parent coordinate for a transitive dependency, or empty for direct dependencies
 */
public record JavanDependency(
    String scope,
    String notation,
    String kind,
    Optional<Path> path,
    int line,
    boolean direct,
    String requestedBy
) {
    /**
     * Creates a direct dependency declaration.
     *
     * @param scope dependency scope
     * @param notation raw dependency notation
     * @param kind local or coordinate
     * @param path resolved local path when available
     * @param line source line in {@code javan.mod}
     */
    public JavanDependency(
        final String scope,
        final String notation,
        final String kind,
        final Optional<Path> path,
        final int line
    ) {
        this(scope, notation, kind, path, line, true, "");
    }

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
        return new JavanDependency(scope, notation, kind, Optional.of(resolvedPath), line, direct, requestedBy);
    }

    /**
     * Creates a transitive coordinate inherited from this dependency.
     *
     * @param coordinate resolved child coordinate
     * @return transitive dependency with inherited scope and source line
     */
    public JavanDependency transitive(final String coordinate) {
        return new JavanDependency(scope, coordinate, "coordinate", Optional.empty(), line, false, notation);
    }
}
