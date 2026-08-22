package javan.dependency;

import javan.toolchain.JavanHome;
import javan.util.Files2;
import javan.util.Sha256;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves deterministic Maven-coordinate dependencies through verified local storage.
 */
public final class JavanCoordinateResolver {
    private final List<Path> repositories;
    private final Path cache;
    private final boolean cacheEnabled;

    /**
     * Creates a resolver backed by the global Javan cache and configured local Maven repositories.
     */
    public JavanCoordinateResolver() {
        this(defaultRepositories(), JavanHome.resolve().resolve("cache/dependencies"));
    }

    /**
     * Creates a resolver with explicit local repository roots and no managed cache.
     *
     * @param repositories local Maven repository roots
     */
    public JavanCoordinateResolver(final List<Path> repositories) {
        this.repositories = normalized(repositories);
        this.cache = Path.of(".").toAbsolutePath().normalize();
        this.cacheEnabled = false;
    }

    JavanCoordinateResolver(final List<Path> repositories, final Path cache) {
        this.repositories = normalized(repositories);
        this.cache = cache.toAbsolutePath().normalize();
        this.cacheEnabled = true;
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
        final List<List<ManagedDependency>> managementPaths = new ArrayList<>();
        for (final JavanDependency dependency : module.dependencies()) {
            dependencies.add(resolve(dependency));
            exclusions.add(List.of());
            managementPaths.add(List.of());
        }
        final List<MavenCoordinate> selected = directCoordinates(dependencies);
        final List<String> warnings = new ArrayList<>(module.warnings());
        final List<PomCacheEntry> pomCache = new ArrayList<>();
        int cursor = 0;
        while (cursor < dependencies.size()) {
            final JavanDependency parent = dependencies.get(cursor);
            final List<String> inheritedExclusions = exclusions.get(cursor);
            final PomResolution pom = transitiveDependencies(parent, managementPaths.get(cursor), pomCache);
            cursor++;
            for (final PomDependency child : pom.dependencies()) {
                final MavenCoordinate childCoordinate = parse(parent.transitive(child.coordinate()));
                final String childKey = key(childCoordinate);
                final Optional<MavenCoordinate> existing = selected(selected, childKey);
                if (!inheritedExclusions.contains(childKey) && existing.isEmpty()) {
                    selected.add(childCoordinate);
                    dependencies.add(resolve(parent.transitive(child.coordinate())));
                    exclusions.add(merged(inheritedExclusions, child.exclusions()));
                    managementPaths.add(pom.management());
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

    private PomResolution transitiveDependencies(
        final JavanDependency dependency,
        final List<ManagedDependency> ancestorManagement,
        final List<PomCacheEntry> pomCache
    ) throws IOException {
        if (!dependency.coordinate() || dependency.path().isEmpty()) {
            return new PomResolution(List.of(), ancestorManagement);
        }
        final MavenCoordinate coordinate = parse(dependency);
        final Path pom = pomPath(coordinate);
        if (!Files.isRegularFile(pom)) {
            return new PomResolution(List.of(), ancestorManagement);
        }
        final PomModel model = pomModel(coordinate, dependency, List.of(), "dependency", pomCache);
        final List<ManagedDependency> effectiveManagement = mergeManagement(
            ancestorManagement,
            model.management()
        );
        final List<PomDependency> dependencies = new ArrayList<>();
        for (final PomDependency child : model.dependencies()) {
            addPomDependency(
                dependencies,
                child.source(),
                child.context(),
                model.management(),
                ancestorManagement,
                dependency
            );
        }
        return new PomResolution(List.copyOf(dependencies), effectiveManagement);
    }

    private static List<ManagedDependency> mergeManagement(
        final List<ManagedDependency> ancestors,
        final List<ManagedDependency> declared
    ) {
        final List<ManagedDependency> result = new ArrayList<>(ancestors);
        for (final ManagedDependency dependency : declared) {
            if (!containsManaged(result, dependency.groupId(), dependency.artifactId())) {
                result.add(dependency);
            }
        }
        return List.copyOf(result);
    }

    private PomModel pomModel(
        final MavenCoordinate coordinate,
        final JavanDependency owner,
        final List<String> ancestors,
        final String relation,
        final List<PomCacheEntry> pomCache
    ) throws IOException {
        final String coordinateText = text(coordinate);
        if (ancestors.contains(coordinateText)) {
            throw invalidPom(owner, "Cyclic local POM metadata at " + coordinateText);
        }
        for (final PomCacheEntry cached : pomCache) {
            if (cached.coordinate().equals(coordinateText)) {
                return cached.model();
            }
        }
        final Path path = pomPath(coordinate);
        if (!Files.isRegularFile(path)) {
            throw invalidPom(owner, "Missing local " + relation + " POM " + coordinateText);
        }
        final String pom = withoutComments(Files.readString(path), owner);
        final List<String> nextAncestors = merged(ancestors, List.of(coordinateText));
        final Optional<MavenCoordinate> parentCoordinate = parentCoordinate(pom, owner);
        final Optional<PomModel> parent;
        if (parentCoordinate.isPresent()) {
            parent = Optional.of(pomModel(parentCoordinate.orElseThrow(), owner, nextAncestors, "parent", pomCache));
        } else {
            parent = Optional.empty();
        }
        final PomContext context = context(pom, coordinate, parentCoordinate, parent, owner);
        final List<String> declaredManagement = managementBlocks(context.pom(), owner);
        final List<ManagedDependency> management = managedDependencies(
            context, parent, declaredManagement, owner, nextAncestors, pomCache
        );
        final List<PomDependency> dependencies = new ArrayList<>();
        if (parent.isPresent()) {
            for (final PomDependency inherited : parent.orElseThrow().dependencies()) {
                addPomDependency(dependencies, inherited.source(), context, management, owner);
            }
        }
        mergePomDependencies(dependencies, declaredDependencies(context, management, owner));
        final PomModel model = new PomModel(
            context,
            parent,
            declaredManagement,
            management,
            List.copyOf(dependencies)
        );
        pomCache.add(new PomCacheEntry(coordinateText, model));
        return model;
    }

    private static Optional<MavenCoordinate> parentCoordinate(
        final String pom,
        final JavanDependency owner
    ) throws IOException {
        final Optional<String> parent = topLevelTag(pom, "parent");
        if (parent.isEmpty()) {
            return Optional.empty();
        }
        final String block = parent.orElseThrow();
        final String groupId = resolved(requiredTag(block, "groupId", owner), pom, owner);
        final String artifactId = resolved(requiredTag(block, "artifactId", owner), pom, owner);
        final String version = resolved(requiredTag(block, "version", owner), pom, owner);
        return Optional.of(coordinate(owner, groupId, artifactId, version));
    }

    private static PomContext context(
        final String pom,
        final MavenCoordinate requested,
        final Optional<MavenCoordinate> parentCoordinate,
        final Optional<PomModel> parent,
        final JavanDependency owner
    ) throws IOException {
        final String groupId = topLevelTag(pom, "groupId")
            .map(Strings2::trimAscii)
            .orElseGet(() -> parent.map(model -> model.context().coordinate().groupId()).orElse(requested.groupId()));
        final String artifactId = topLevelTag(pom, "artifactId").orElse(requested.artifactId());
        final String version = topLevelTag(pom, "version")
            .map(Strings2::trimAscii)
            .orElseGet(() -> parent.map(model -> model.context().coordinate().version()).orElse(requested.version()));
        final MavenCoordinate declared = coordinate(owner, groupId, artifactId, version);
        return new PomContext(pom, declared, parentCoordinate, parent.map(PomModel::context));
    }

    private List<ManagedDependency> managedDependencies(
        final PomContext context,
        final Optional<PomModel> parent,
        final List<String> declared,
        final JavanDependency owner,
        final List<String> ancestors,
        final List<PomCacheEntry> pomCache
    ) throws IOException {
        final List<ManagedDependency> result = new ArrayList<>();
        if (parent.isPresent()) {
            result.addAll(parentManagement(parent.orElseThrow(), context, owner, ancestors, pomCache));
        }
        applyManagement(result, declared, context, owner, ancestors, pomCache);
        return List.copyOf(result);
    }

    private List<ManagedDependency> parentManagement(
        final PomModel model,
        final PomContext context,
        final JavanDependency owner,
        final List<String> ancestors,
        final List<PomCacheEntry> pomCache
    ) throws IOException {
        final List<ManagedDependency> result = new ArrayList<>();
        if (model.parent().isPresent()) {
            result.addAll(parentManagement(model.parent().orElseThrow(), context, owner, ancestors, pomCache));
        }
        applyManagement(result, model.declaredManagement(), context, owner, ancestors, pomCache);
        return List.copyOf(result);
    }

    private static List<String> managementBlocks(
        final String pom,
        final JavanDependency owner
    ) throws IOException {
        final Optional<String> section = topLevelTag(pom, "dependencyManagement");
        if (section.isEmpty()) {
            return List.of();
        }
        final Optional<String> dependencies = tag(section.orElseThrow(), "dependencies");
        if (dependencies.isEmpty()) {
            return List.of();
        }
        return dependencyBlocks(dependencies.orElseThrow(), owner);
    }

    private void applyManagement(
        final List<ManagedDependency> result,
        final List<String> blocks,
        final PomContext context,
        final JavanDependency owner,
        final List<String> ancestors,
        final List<PomCacheEntry> pomCache
    ) throws IOException {
        final List<String> importedKeys = new ArrayList<>();
        // Maven expands imports in declaration order; the first imported BOM owns duplicate keys.
        for (final String block : blocks) {
            final String type = resolved(tag(block, "type").orElse("jar"), context, owner);
            final String scope = resolved(tag(block, "scope").orElse("compile"), context, owner);
            if ("pom".equals(type) && "import".equals(scope)) {
                final MavenCoordinate imported = dependencyCoordinate(
                    block, context, result, List.of(), owner, false
                );
                final PomModel bom = pomModel(imported, owner, ancestors, "imported BOM", pomCache);
                for (final ManagedDependency managed : bom.management()) {
                    final String key = managed.groupId() + ":" + managed.artifactId();
                    if (!importedKeys.contains(key)) {
                        putManaged(result, new ManagedDependency(
                            managed.groupId(), managed.artifactId(), managed.version()
                        ));
                        importedKeys.add(key);
                    }
                }
            }
        }
        // Entries declared in this POM override both its parent and imported BOMs.
        for (final String block : blocks) {
            final String type = resolved(tag(block, "type").orElse("jar"), context, owner);
            final String scope = resolved(tag(block, "scope").orElse("compile"), context, owner);
            if (!("pom".equals(type) && "import".equals(scope))) {
                final MavenCoordinate managed = dependencyCoordinate(
                    block, context, result, List.of(), owner, false
                );
                putManaged(result, new ManagedDependency(
                    managed.groupId(),
                    managed.artifactId(),
                    managed.version()
                ));
            }
        }
    }

    private static List<PomDependency> declaredDependencies(
        final PomContext context,
        final List<ManagedDependency> management,
        final JavanDependency owner
    ) throws IOException {
        final List<PomDependency> result = new ArrayList<>();
        int cursor = 0;
        while (true) {
            final int sectionStart = context.pom().indexOf("<dependencies>", cursor);
            if (sectionStart < 0) {
                return List.copyOf(result);
            }
            final int sectionEnd = context.pom().indexOf("</dependencies>", sectionStart);
            if (sectionEnd < 0) {
                throw invalidPom(owner, "unterminated dependencies section");
            }
            if (!nestedIn(context.pom(), sectionStart, "dependencyManagement")
                && !nestedIn(context.pom(), sectionStart, "build")
                && !nestedIn(context.pom(), sectionStart, "profiles")
                && !nestedIn(context.pom(), sectionStart, "reporting")) {
                addPomDependencies(
                    result,
                    context.pom().substring(sectionStart, sectionEnd),
                    context,
                    management,
                    owner
                );
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
        final PomContext context,
        final List<ManagedDependency> management,
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
            addPomDependency(result, block, context, management, owner);
            cursor = end + "</dependency>".length();
        }
    }

    private static void addPomDependency(
        final List<PomDependency> result,
        final String block,
        final PomContext context,
        final List<ManagedDependency> management,
        final JavanDependency owner
    ) throws IOException {
        addPomDependency(result, block, context, management, List.of(), owner);
    }

    private static void addPomDependency(
        final List<PomDependency> result,
        final String block,
        final PomContext context,
        final List<ManagedDependency> management,
        final List<ManagedDependency> overridingManagement,
        final JavanDependency owner
    ) throws IOException {
        final String scope = resolved(tag(block, "scope").orElse("compile"), context, owner);
        final boolean optional = "true".equals(resolved(tag(block, "optional").orElse("false"), context, owner));
        if (optional || !("compile".equals(scope) || "runtime".equals(scope))) {
            return;
        }
        final MavenCoordinate dependency = dependencyCoordinate(
            block, context, management, overridingManagement, owner, true
        );
        final String type = resolved(tag(block, "type").orElse("jar"), context, owner);
        final String classifier = resolved(tag(block, "classifier").orElse(""), context, owner);
        if (!"jar".equals(type) || !Strings2.isBlank(classifier)) {
            throw invalidPom(
                owner,
                "unsupported artifact " + dependency.groupId() + ":" + dependency.artifactId() + " type=" + type
            );
        }
        result.add(new PomDependency(text(dependency), exclusions(block, context, owner), block, context));
    }

    private static MavenCoordinate dependencyCoordinate(
        final String dependency,
        final PomContext context,
        final List<ManagedDependency> management,
        final List<ManagedDependency> overridingManagement,
        final JavanDependency owner,
        final boolean allowManagedVersion
    ) throws IOException {
        final String groupId = resolved(requiredTag(dependency, "groupId", owner), context, owner);
        final String artifactId = resolved(requiredTag(dependency, "artifactId", owner), context, owner);
        if (allowManagedVersion) {
            final Optional<String> overridden = managedVersion(overridingManagement, groupId, artifactId);
            if (overridden.isPresent()) {
                return coordinate(owner, groupId, artifactId, overridden.orElseThrow());
            }
        }
        final Optional<String> declared = tag(dependency, "version");
        if (declared.isPresent()) {
            return coordinate(owner, groupId, artifactId, resolved(declared.orElseThrow(), context, owner));
        }
        if (allowManagedVersion) {
            final Optional<String> managed = managedVersion(management, groupId, artifactId);
            if (managed.isPresent()) {
                return coordinate(owner, groupId, artifactId, managed.orElseThrow());
            }
        }
        throw invalidPom(owner, "dependency is missing version for " + groupId + ":" + artifactId);
    }

    private static Optional<String> managedVersion(
        final List<ManagedDependency> management,
        final String groupId,
        final String artifactId
    ) {
        for (final ManagedDependency managed : management) {
            if (groupId.equals(managed.groupId()) && artifactId.equals(managed.artifactId())) {
                return Optional.of(managed.version());
            }
        }
        return Optional.empty();
    }

    private static List<String> dependencyBlocks(final String xml, final JavanDependency owner) throws IOException {
        final List<String> result = new ArrayList<>();
        int cursor = 0;
        while (true) {
            final int start = xml.indexOf("<dependency>", cursor);
            if (start < 0) {
                return List.copyOf(result);
            }
            final int end = xml.indexOf("</dependency>", start);
            if (end < 0) {
                throw invalidPom(owner, "unterminated managed dependency");
            }
            result.add(xml.substring(start, end));
            cursor = end + "</dependency>".length();
        }
    }

    private static void putManaged(final List<ManagedDependency> dependencies, final ManagedDependency dependency) {
        for (int index = 0; index < dependencies.size(); index++) {
            final ManagedDependency existing = dependencies.get(index);
            if (existing.groupId().equals(dependency.groupId())
                && existing.artifactId().equals(dependency.artifactId())) {
                dependencies.set(index, dependency);
                return;
            }
        }
        dependencies.add(dependency);
    }

    private static boolean containsManaged(
        final List<ManagedDependency> dependencies,
        final String groupId,
        final String artifactId
    ) {
        for (final ManagedDependency dependency : dependencies) {
            if (dependency.groupId().equals(groupId) && dependency.artifactId().equals(artifactId)) {
                return true;
            }
        }
        return false;
    }

    private static void mergePomDependencies(
        final List<PomDependency> dependencies,
        final List<PomDependency> declared
    ) {
        for (final PomDependency dependency : declared) {
            final int separator = dependency.coordinate().lastIndexOf(':');
            final String key = separator < 0 ? dependency.coordinate() : dependency.coordinate().substring(0, separator);
            boolean replaced = false;
            for (int index = 0; index < dependencies.size(); index++) {
                if (dependencies.get(index).coordinate().startsWith(key + ":")) {
                    dependencies.set(index, dependency);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                dependencies.add(dependency);
            }
        }
    }

    private static List<String> exclusions(
        final String dependency,
        final PomContext context,
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
            final String groupId = resolved(requiredTag(block, "groupId", owner), context, owner);
            final String artifactId = resolved(requiredTag(block, "artifactId", owner), context, owner);
            final String key = groupId + ":" + artifactId;
            if (!result.contains(key)) {
                result.add(key);
            }
            cursor = end + "</exclusion>".length();
        }
    }

    private static String resolved(
        final String raw,
        final PomContext context,
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
            final Optional<String> value = property(context, name);
            if (value.isEmpty()) {
                throw invalidPom(owner, "unresolved property ${" + name + "}");
            }
            result = result.substring(0, start) + value.orElseThrow() + result.substring(end + 1);
        }
        throw invalidPom(owner, "cyclic property in " + raw);
    }

    private static Optional<String> property(final PomContext context, final String name) {
        final Optional<String> properties = topLevelTag(context.pom(), "properties");
        if (properties.isPresent()) {
            final Optional<String> declared = tag(properties.orElseThrow(), name);
            if (declared.isPresent()) {
                return declared;
            }
        }
        if ("project.groupId".equals(name) || "pom.groupId".equals(name)) {
            return Optional.of(context.coordinate().groupId());
        }
        if ("project.artifactId".equals(name) || "pom.artifactId".equals(name)) {
            return Optional.of(context.coordinate().artifactId());
        }
        if ("project.version".equals(name) || "pom.version".equals(name)) {
            return Optional.of(context.coordinate().version());
        }
        if ("project.parent.groupId".equals(name) || "pom.parent.groupId".equals(name)) {
            return context.parentCoordinate().map(MavenCoordinate::groupId);
        }
        if ("project.parent.artifactId".equals(name) || "pom.parent.artifactId".equals(name)) {
            return context.parentCoordinate().map(MavenCoordinate::artifactId);
        }
        if ("project.parent.version".equals(name) || "pom.parent.version".equals(name)) {
            return context.parentCoordinate().map(MavenCoordinate::version);
        }
        return context.parent().flatMap(parent -> property(parent, name));
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
        final Optional<String> properties = topLevelTag(pom, "properties");
        if (properties.isPresent()) {
            final Optional<String> declared = tag(properties.orElseThrow(), name);
            if (declared.isPresent()) {
                return declared;
            }
        }
        if ("project.groupId".equals(name) || "pom.groupId".equals(name)) {
            final Optional<String> declared = topLevelTag(pom, "groupId");
            return declared.isPresent() ? declared : tag(pom, "groupId");
        }
        if ("project.version".equals(name) || "pom.version".equals(name)) {
            final Optional<String> declared = topLevelTag(pom, "version");
            return declared.isPresent() ? declared : tag(pom, "version");
        }
        return Optional.empty();
    }

    private static boolean nestedIn(final String xml, final int position, final String tag) {
        return xml.lastIndexOf("<" + tag, position) > xml.lastIndexOf("</" + tag + ">", position);
    }

    private static Optional<String> topLevelTag(final String xml, final String name) {
        final String open = "<" + name + ">";
        int cursor = 0;
        while (true) {
            final int start = xml.indexOf(open, cursor);
            if (start < 0) {
                return Optional.empty();
            }
            if (!nestedMetadata(xml, start, name)) {
                return tag(xml.substring(start), name);
            }
            cursor = start + open.length();
        }
    }

    private static boolean nestedMetadata(final String xml, final int position, final String name) {
        return (!"parent".equals(name) && nestedIn(xml, position, "parent"))
            || (!"properties".equals(name) && nestedIn(xml, position, "properties"))
            || nestedIn(xml, position, "dependencies")
            || (!"dependencyManagement".equals(name) && nestedIn(xml, position, "dependencyManagement"))
            || nestedIn(xml, position, "build")
            || nestedIn(xml, position, "profiles")
            || nestedIn(xml, position, "reporting")
            || nestedIn(xml, position, "plugins")
            || nestedIn(xml, position, "licenses")
            || nestedIn(xml, position, "developers")
            || nestedIn(xml, position, "scm")
            || nestedIn(xml, position, "distributionManagement")
            || nestedIn(xml, position, "repositories")
            || nestedIn(xml, position, "modules");
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

    private Path pathFor(final MavenCoordinate coordinate) throws IOException {
        return artifactPath(coordinate, false);
    }

    private Path pomPath(final MavenCoordinate coordinate) throws IOException {
        return artifactPath(coordinate, true);
    }

    private Path artifactPath(final MavenCoordinate coordinate, final boolean pom) throws IOException {
        final Path cached = pom ? pomPath(cache, coordinate) : pathFor(cache, coordinate);
        if (cacheEnabled && Files.isRegularFile(cached)) {
            if (Files.isRegularFile(checksumFile(cached))) {
                verifyCached(cached);
                return cached;
            }
        }
        Path first = pom
            ? pomPath(Path.of(".").toAbsolutePath().normalize(), coordinate)
            : pathFor(Path.of(".").toAbsolutePath().normalize(), coordinate);
        for (int index = 0; index < repositories.size(); index++) {
            final Path candidate = pom
                ? pomPath(repositories.get(index), coordinate)
                : pathFor(repositories.get(index), coordinate);
            if (index == 0) {
                first = candidate;
            }
            if (Files.isRegularFile(candidate)) {
                return cacheEnabled ? cache(candidate, cached) : candidate;
            }
        }
        if (cacheEnabled && Files.isRegularFile(cached)) {
            verifyCached(cached);
        }
        return cacheEnabled ? cached : first;
    }

    private static Path cache(final Path source, final Path target) throws IOException {
        if (!Files2.createDirectoriesIfPossible(target.getParent())) {
            throw new IOException(
                "Cannot write dependency cache directory: " + target.getParent()
                    + System.lineSeparator()
                    + "Fix: Make javan.home writable or set -Djavan.home to a writable directory."
            );
        }
        final String checksum = Sha256.of(source);
        final Path staging = target.getParent().resolve(target.getFileName() + "." + checksum + ".part");
        Files.copy(source, staging, StandardCopyOption.REPLACE_EXISTING);
        if (!checksum.equals(Sha256.of(staging))) {
            throw new IOException("Dependency changed while being cached: " + source);
        }
        Files.copy(staging, target, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(staging);
        Files2.writeString(checksumFile(target), checksum + System.lineSeparator());
        verifyCached(target);
        return target;
    }

    private static void verifyCached(final Path artifact) throws IOException {
        final Path checksumFile = checksumFile(artifact);
        if (!Files.isRegularFile(checksumFile)) {
            throw invalidCache(artifact, "missing SHA-256 metadata");
        }
        final String expected = Strings2.trimAscii(Files.readString(checksumFile));
        final String found = Sha256.of(artifact);
        if (!expected.equals(found)) {
            throw invalidCache(artifact, "expected " + expected + " but found " + found);
        }
    }

    private static Path checksumFile(final Path artifact) {
        return artifact.getParent().resolve(artifact.getFileName() + ".sha256");
    }

    private static IOException invalidCache(final Path artifact, final String reason) {
        return new IOException(
            "Cached dependency checksum mismatch for " + artifact + ": " + reason
                + System.lineSeparator()
                + "Fix: Delete the cached file and resolve it again from a trusted local Maven repository."
        );
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

    private static Path pomPath(final Path repository, final MavenCoordinate coordinate) {
        return repository
            .resolve(Strings2.replaceChar(coordinate.groupId(), '.', java.io.File.separatorChar))
            .resolve(coordinate.artifactId())
            .resolve(coordinate.version())
            .resolve(coordinate.artifactId() + "-" + coordinate.version() + ".pom")
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

    private record PomContext(
        String pom,
        MavenCoordinate coordinate,
        Optional<MavenCoordinate> parentCoordinate,
        Optional<PomContext> parent
    ) {
    }

    private record PomModel(
        PomContext context,
        Optional<PomModel> parent,
        List<String> declaredManagement,
        List<ManagedDependency> management,
        List<PomDependency> dependencies
    ) {
    }

    private record PomCacheEntry(String coordinate, PomModel model) {
    }

    private record PomResolution(
        List<PomDependency> dependencies,
        List<ManagedDependency> management
    ) {
    }

    private record ManagedDependency(String groupId, String artifactId, String version) {
    }

    private record PomDependency(
        String coordinate,
        List<String> exclusions,
        String source,
        PomContext context
    ) {
    }
}
