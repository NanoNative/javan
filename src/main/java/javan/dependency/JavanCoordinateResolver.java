package javan.dependency;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves deterministic local Maven-coordinate dependencies from {@code javan.mod}.
 */
public final class JavanCoordinateResolver {
    private final List<Path> repositories;

    /**
     * Creates a resolver that checks configured local Maven repositories.
     */
    public JavanCoordinateResolver() {
        this(defaultRepositories());
    }

    /**
     * Creates a resolver with explicit local repository roots.
     *
     * @param repositories local Maven repository roots
     */
    public JavanCoordinateResolver(final List<Path> repositories) {
        this.repositories = normalized(repositories);
    }

    /**
     * Resolves all coordinate dependencies in a module to local repository jar paths.
     *
     * @param module parsed module
     * @return module with coordinate paths filled in
     * @throws IOException when a coordinate is invalid
     */
    public JavanModule resolve(final JavanModule module) throws IOException {
        if (!module.present()) {
            return module;
        }
        final List<JavanDependency> dependencies = new ArrayList<>();
        for (final JavanDependency dependency : module.dependencies()) {
            dependencies.add(resolve(dependency));
        }
        return new JavanModule(
            module.present(),
            module.moduleName(),
            module.javaVersion(),
            List.copyOf(dependencies),
            module.warnings()
        );
    }

    /**
     * Resolves one coordinate dependency to the first local repository candidate.
     *
     * @param dependency dependency declaration
     * @return dependency with resolved path when it is a coordinate
     * @throws IOException when the coordinate form is invalid
     */
    public JavanDependency resolve(final JavanDependency dependency) throws IOException {
        if (!dependency.coordinate()) {
            return dependency;
        }
        final MavenCoordinate coordinate = parse(dependency);
        return dependency.withPath(pathFor(coordinate));
    }

    private Path pathFor(final MavenCoordinate coordinate) {
        Path first = pathFor(Path.of(".").toAbsolutePath().normalize(), coordinate);
        for (int index = 0; index < repositories.size(); index++) {
            final Path candidate = pathFor(repositories.get(index), coordinate);
            if (index == 0) {
                first = candidate;
            }
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return first;
    }

    private static Path pathFor(final Path repository, final MavenCoordinate coordinate) {
        return repository
            .resolve(Strings2.replaceChar(coordinate.groupId(), '.', java.io.File.separatorChar))
            .resolve(coordinate.artifactId())
            .resolve(coordinate.version())
            .resolve(coordinate.artifactId() + "-" + coordinate.version() + ".jar")
            .toAbsolutePath()
            .normalize();
    }

    private static MavenCoordinate parse(final JavanDependency dependency) throws IOException {
        final String notation = Strings2.trimAscii(dependency.notation());
        final int split = asciiWhitespaceIndex(notation);
        if (split >= 0) {
            final String name = Strings2.trimAscii(Strings2.slice(notation, 0, split));
            final String version = Strings2.trimAscii(Strings2.slice(notation, split + 1, notation.length()));
            final int colon = name.indexOf(':');
            if (colon > 0 && colon == name.lastIndexOf(':') && !Strings2.isBlank(version)) {
                return coordinate(
                    dependency,
                    Strings2.slice(name, 0, colon),
                    Strings2.slice(name, colon + 1, name.length()),
                    version
                );
            }
            throw invalid(dependency);
        }
        final int first = notation.indexOf(':');
        final int second = first < 0 ? -1 : notation.indexOf(':', first + 1);
        if (first > 0 && second > first + 1 && notation.indexOf(':', second + 1) < 0) {
            return coordinate(
                dependency,
                Strings2.slice(notation, 0, first),
                Strings2.slice(notation, first + 1, second),
                Strings2.slice(notation, second + 1, notation.length())
            );
        }
        throw invalid(dependency);
    }

    private static MavenCoordinate coordinate(
        final JavanDependency dependency,
        final String groupId,
        final String artifactId,
        final String version
    ) throws IOException {
        if (Strings2.isBlank(groupId) || Strings2.isBlank(artifactId) || Strings2.isBlank(version)) {
            throw invalid(dependency);
        }
        return new MavenCoordinate(groupId, artifactId, version);
    }

    private static IOException invalid(final JavanDependency dependency) {
        return new IOException(
            "Invalid javan.mod coordinate at line "
                + dependency.line()
                + ": expected group:artifact:version or group:artifact version, got "
                + dependency.notation()
        );
    }

    private static int asciiWhitespaceIndex(final String value) {
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n' || ch == '\f') {
                return index;
            }
        }
        return -1;
    }

    private static List<Path> defaultRepositories() {
        final List<Path> result = new ArrayList<>();
        addRepository(result, System.getProperty("javan.maven.localRepository", ""));
        addRepository(result, System.getProperty("maven.repo.local", ""));
        addRepository(result, Path.of(System.getProperty("user.home", ".")).resolve(".m2/repository").toString());
        return List.copyOf(result);
    }

    private static List<Path> normalized(final List<Path> paths) {
        final List<Path> result = new ArrayList<>();
        for (final Path path : paths) {
            addRepository(result, path.toString());
        }
        return List.copyOf(result);
    }

    private static void addRepository(final List<Path> result, final String value) {
        if (Strings2.isBlank(value)) {
            return;
        }
        final Path normalized = Path.of(value).toAbsolutePath().normalize();
        final String normalizedText = normalized.toString();
        for (final Path existing : result) {
            if (existing.toString().equals(normalizedText)) {
                return;
            }
        }
        result.add(normalized);
    }

    private record MavenCoordinate(String groupId, String artifactId, String version) {
    }
}
