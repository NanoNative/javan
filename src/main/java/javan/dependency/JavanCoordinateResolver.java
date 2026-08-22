package javan.dependency;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
     * Resolves direct coordinates and their local compile/runtime POM closure.
     *
     * @param module parsed module
     * @return module with resolved paths, transitives, and mediation warnings
     * @throws IOException when a coordinate or required local POM value is invalid
     */
    public JavanModule resolve(final JavanModule module) throws IOException {
        if (!module.present()) {
            return module;
        }
        final List<JavanDependency> dependencies = new ArrayList<>();
        final List<List<String>> exclusions = new ArrayList<>();
        for (final JavanDependency dependency : module.dependencies()) {
            dependencies.add(resolve(dependency));
            exclusions.add(List.of());
        }
        final List<MavenCoordinate> selected = directCoordinates(dependencies);
        final List<String> warnings = new ArrayList<>(module.warnings());
        int cursor = 0;
        while (cursor < dependencies.size()) {
            final JavanDependency parent = dependencies.get(cursor);
            final List<String> inheritedExclusions = exclusions.get(cursor++);
            for (final PomDependency child : transitiveDependencies(parent)) {
                final MavenCoordinate childCoordinate = parse(parent.transitive(child.coordinate()));
                final String childKey = key(childCoordinate);
                final Optional<MavenCoordinate> existing = selected(selected, childKey);
                if (!inheritedExclusions.contains(childKey) && existing.isEmpty()) {
                    selected.add(childCoordinate);
                    dependencies.add(resolve(parent.transitive(child.coordinate())));
                    exclusions.add(merged(inheritedExclusions, child.exclusions()));
                } else if (existing.isPresent()
                    && !existing.orElseThrow().version().equals(childCoordinate.version())) {
                    warnings.add(
                        "Dependency mediation kept " + text(existing.orElseThrow()) + " and omitted " + child.coordinate()
                            + " requested by " + parent.notation()
                    );
                }
            }
        }
        return new JavanModule(
            module.present(),
            module.moduleName(),
            module.javaVersion(),
            List.copyOf(dependencies),
            List.copyOf(warnings)
        );
    }

    /**
     * Resolves one coordinate dependency to the first local repository candidate without traversing its POM.
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

    private static List<MavenCoordinate> directCoordinates(final List<JavanDependency> dependencies) throws IOException {
        final List<MavenCoordinate> result = new ArrayList<>();
        for (final JavanDependency dependency : dependencies) {
            if (dependency.coordinate()) {
                final MavenCoordinate coordinate = parse(dependency);
                final Optional<MavenCoordinate> existing = selected(result, key(coordinate));
                if (existing.isPresent()) {
                    throw new IOException(
                        "Duplicate javan.mod coordinate family: " + text(existing.orElseThrow()) + " and " + text(coordinate)
                    );
                }
                result.add(coordinate);
            }
        }
        return result;
    }

    private static Optional<MavenCoordinate> selected(final List<MavenCoordinate> coordinates, final String key) {
        for (final MavenCoordinate coordinate : coordinates) {
            if (key(coordinate).equals(key)) {
                return Optional.of(coordinate);
            }
        }
        return Optional.empty();
    }

    private static String text(final MavenCoordinate coordinate) {
        return coordinate.groupId() + ":" + coordinate.artifactId() + ":" + coordinate.version();
    }

    private static String key(final MavenCoordinate coordinate) {
        return coordinate.groupId() + ":" + coordinate.artifactId();
    }

    private static List<String> merged(final List<String> inherited, final List<String> declared) {
        final List<String> result = new ArrayList<>(inherited);
        for (final String exclusion : declared) {
            if (!result.contains(exclusion)) {
                result.add(exclusion);
            }
        }
        return List.copyOf(result);
    }

    private static List<PomDependency> transitiveDependencies(final JavanDependency dependency) throws IOException {
        if (!dependency.coordinate() || dependency.path().isEmpty()) {
            return List.of();
        }
        final Path jar = dependency.path().orElseThrow();
        final Path fileName = jar.getFileName();
        if (fileName == null || !fileName.toString().endsWith(".jar")) {
            return List.of();
        }
        final Path parent = jar.getParent();
        if (parent == null) {
            return List.of();
        }
        final Path pom = parent.resolve(
            fileName.toString().substring(0, fileName.toString().length() - ".jar".length()) + ".pom"
        );
        if (!Files.isRegularFile(pom)) {
            return List.of();
        }
        return pomDependencies(Files.readString(pom), dependency);
    }

    private static List<PomDependency> pomDependencies(final String xml, final JavanDependency owner) throws IOException {
        final String pom = withoutComments(xml, owner);
        final List<PomDependency> result = new ArrayList<>();
        int cursor = 0;
        while (true) {
            final int sectionStart = pom.indexOf("<dependencies>", cursor);
            if (sectionStart < 0) {
                return List.copyOf(result);
            }
            final int sectionEnd = pom.indexOf("</dependencies>", sectionStart);
            if (sectionEnd < 0) {
                throw invalidPom(owner, "unterminated dependencies section");
            }
            if (!nestedIn(pom, sectionStart, "dependencyManagement")
                && !nestedIn(pom, sectionStart, "build")
                && !nestedIn(pom, sectionStart, "profiles")
                && !nestedIn(pom, sectionStart, "reporting")) {
                addPomDependencies(result, pom.substring(sectionStart, sectionEnd), pom, owner);
            }
            cursor = sectionEnd + "</dependencies>".length();
        }
    }

    private static String withoutComments(final String xml, final JavanDependency owner) throws IOException {
        final StringBuilder result = new StringBuilder(xml.length());
        int cursor = 0;
        while (cursor < xml.length()) {
            final int start = xml.indexOf("<!--", cursor);
            if (start < 0) {
                result.append(xml.substring(cursor));
                return result.toString();
            }
            result.append(xml.substring(cursor, start));
            final int end = xml.indexOf("-->", start + 4);
            if (end < 0) {
                throw invalidPom(owner, "unterminated XML comment");
            }
            cursor = end + 3;
        }
        return result.toString();
    }

    private static void addPomDependencies(
        final List<PomDependency> result,
        final String dependencies,
        final String pom,
        final JavanDependency owner
    ) throws IOException {
        int cursor = 0;
        while (true) {
            final int start = dependencies.indexOf("<dependency>", cursor);
            if (start < 0) {
                return;
            }
            final int end = dependencies.indexOf("</dependency>", start);
            if (end < 0) {
                throw invalidPom(owner, "unterminated dependency");
            }
            final String block = dependencies.substring(start, end);
            final String scope = resolved(tag(block, "scope").orElse("compile"), pom, owner);
            final boolean optional = "true".equals(resolved(tag(block, "optional").orElse("false"), pom, owner));
            if (!optional && ("compile".equals(scope) || "runtime".equals(scope))) {
                final String groupId = resolved(requiredTag(block, "groupId", owner), pom, owner);
                final String artifactId = resolved(requiredTag(block, "artifactId", owner), pom, owner);
                final String version = resolvedVersion(block, pom, groupId, artifactId, owner);
                final String type = resolved(tag(block, "type").orElse("jar"), pom, owner);
                final String classifier = resolved(tag(block, "classifier").orElse(""), pom, owner);
                if (!"jar".equals(type) || !Strings2.isBlank(classifier)) {
                    throw invalidPom(owner, "unsupported artifact " + groupId + ":" + artifactId + " type=" + type);
                }
                result.add(new PomDependency(
                    groupId + ":" + artifactId + ":" + version,
                    exclusions(block, pom, owner)
                ));
            }
            cursor = end + "</dependency>".length();
        }
    }

    private static String resolvedVersion(
        final String dependency,
        final String pom,
        final String groupId,
        final String artifactId,
        final JavanDependency owner
    ) throws IOException {
        final Optional<String> declared = tag(dependency, "version");
        if (declared.isPresent()) {
            return resolved(declared.orElseThrow(), pom, owner);
        }
        final Optional<String> managed = managedVersion(pom, groupId, artifactId, owner);
        if (managed.isPresent()) {
            return managed.orElseThrow();
        }
        throw invalidPom(owner, "dependency is missing version for " + groupId + ":" + artifactId);
    }

    private static Optional<String> managedVersion(
        final String pom,
        final String groupId,
        final String artifactId,
        final JavanDependency owner
    ) throws IOException {
        final Optional<String> management = tag(pom, "dependencyManagement");
        if (management.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> dependencies = tag(management.orElseThrow(), "dependencies");
        if (dependencies.isEmpty()) {
            return Optional.empty();
        }
        final String xml = dependencies.orElseThrow();
        int cursor = 0;
        while (true) {
            final int start = xml.indexOf("<dependency>", cursor);
            if (start < 0) {
                return Optional.empty();
            }
            final int end = xml.indexOf("</dependency>", start);
            if (end < 0) {
                throw invalidPom(owner, "unterminated managed dependency");
            }
            final String block = xml.substring(start, end);
            final String managedGroup = resolved(requiredTag(block, "groupId", owner), pom, owner);
            final String managedArtifact = resolved(requiredTag(block, "artifactId", owner), pom, owner);
            if (groupId.equals(managedGroup) && artifactId.equals(managedArtifact)) {
                return Optional.of(resolved(requiredTag(block, "version", owner), pom, owner));
            }
            cursor = end + "</dependency>".length();
        }
    }

    private static List<String> exclusions(
        final String dependency,
        final String pom,
        final JavanDependency owner
    ) throws IOException {
        final Optional<String> section = tag(dependency, "exclusions");
        if (section.isEmpty()) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        final String xml = section.orElseThrow();
        int cursor = 0;
        while (true) {
            final int start = xml.indexOf("<exclusion>", cursor);
            if (start < 0) {
                return List.copyOf(result);
            }
            final int end = xml.indexOf("</exclusion>", start);
            if (end < 0) {
                throw invalidPom(owner, "unterminated exclusion");
            }
            final String block = xml.substring(start, end);
            final String groupId = resolved(requiredTag(block, "groupId", owner), pom, owner);
            final String artifactId = resolved(requiredTag(block, "artifactId", owner), pom, owner);
            final String key = groupId + ":" + artifactId;
            if (!result.contains(key)) {
                result.add(key);
            }
            cursor = end + "</exclusion>".length();
        }
    }

    private static String resolved(
        final String raw,
        final String pom,
        final JavanDependency owner
    ) throws IOException {
        String result = raw;
        for (int attempt = 0; attempt < 16; attempt++) {
            final int start = result.indexOf("${");
            if (start < 0) {
                return result;
            }
            final int end = result.indexOf('}', start + 2);
            if (end < 0) {
                throw invalidPom(owner, "unterminated property in " + raw);
            }
            final String name = result.substring(start + 2, end);
            final Optional<String> value = property(pom, name);
            if (value.isEmpty()) {
                throw invalidPom(owner, "unresolved property ${" + name + "}");
            }
            result = result.substring(0, start) + value.orElseThrow() + result.substring(end + 1);
        }
        throw invalidPom(owner, "cyclic property in " + raw);
    }

    private static Optional<String> property(final String pom, final String name) {
        final Optional<String> properties = tag(pom, "properties");
        if (properties.isPresent()) {
            final Optional<String> declared = tag(properties.orElseThrow(), name);
            if (declared.isPresent()) {
                return declared;
            }
        }
        final String project = withoutBlock(pom, "parent");
        if ("project.groupId".equals(name) || "pom.groupId".equals(name)) {
            final Optional<String> declared = tag(project, "groupId");
            return declared.isPresent() ? declared : tag(pom, "groupId");
        }
        if ("project.version".equals(name) || "pom.version".equals(name)) {
            final Optional<String> declared = tag(project, "version");
            return declared.isPresent() ? declared : tag(pom, "version");
        }
        return Optional.empty();
    }

    private static String withoutBlock(final String xml, final String name) {
        final int start = xml.indexOf("<" + name + ">");
        if (start < 0) {
            return xml;
        }
        final int end = xml.indexOf("</" + name + ">", start);
        if (end < 0) {
            return xml;
        }
        return xml.substring(0, start) + xml.substring(end + ("</" + name + ">").length());
    }

    private static boolean nestedIn(final String xml, final int position, final String tag) {
        return xml.lastIndexOf("<" + tag, position) > xml.lastIndexOf("</" + tag + ">", position);
    }

    private static String requiredTag(
        final String block,
        final String name,
        final JavanDependency owner
    ) throws IOException {
        final Optional<String> value = tag(block, name);
        if (value.isEmpty()) {
            throw invalidPom(owner, "dependency is missing " + name);
        }
        return value.orElseThrow();
    }

    private static Optional<String> tag(final String xml, final String name) {
        final String open = "<" + name + ">";
        final String close = "</" + name + ">";
        final int start = xml.indexOf(open);
        if (start < 0) {
            return Optional.empty();
        }
        final int valueStart = start + open.length();
        final int end = xml.indexOf(close, valueStart);
        if (end < 0) {
            return Optional.empty();
        }
        final String value = Strings2.trimAscii(xml.substring(valueStart, end));
        return Strings2.isBlank(value) ? Optional.empty() : Optional.of(value);
    }

    private static IOException invalidPom(final JavanDependency dependency, final String reason) {
        return new IOException(
            "Invalid local Maven metadata for " + dependency.notation() + ": " + reason
                + "\nFix: Correct the local POM or declare an explicit supported coordinate in javan.mod."
        );
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

    static String repositoryOrigin(final JavanDependency dependency) throws IOException {
        if (!dependency.coordinate() || dependency.path().isEmpty()) {
            return "";
        }
        final MavenCoordinate coordinate = parse(dependency);
        Path repository = dependency.path().orElseThrow().toAbsolutePath().normalize().getParent();
        final int groupSegments = segmentCount(coordinate.groupId());
        for (int index = 0; index < groupSegments + 2 && repository != null; index++) {
            repository = repository.getParent();
        }
        return repository == null ? "" : repository.toString();
    }

    private static int segmentCount(final String value) {
        int result = 1;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '.') {
                result++;
            }
        }
        return result;
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

    private record PomDependency(String coordinate, List<String> exclusions) {
    }
}
