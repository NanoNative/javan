package javan.build;

import javan.detect.BuildTool;
import javan.detect.ClassOutputDiscovery;
import javan.detect.ProjectLayout;
import javan.dependency.JavanCoordinateResolver;
import javan.dependency.JavanDependency;
import javan.dependency.JavanLockWriter;
import javan.dependency.JavanModule;
import javan.dependency.JavanModuleParser;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves application class folders and dependency classpath entries.
 */
public final class ClasspathResolver {
    private final ProcessRunner processRunner;
    private final JavanModuleParser moduleParser;
    private final JavanLockWriter lockWriter;
    private final JavanCoordinateResolver coordinateResolver;

    /**
     * Creates a resolver.
     *
     * @param processRunner process runner used for build-tool classpath tasks
     */
    public ClasspathResolver(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
        this.moduleParser = new JavanModuleParser();
        this.lockWriter = new JavanLockWriter();
        this.coordinateResolver = new JavanCoordinateResolver();
    }

    ClasspathResolver(
        final ProcessRunner processRunner,
        final JavanModuleParser moduleParser,
        final JavanLockWriter lockWriter,
        final JavanCoordinateResolver coordinateResolver
    ) {
        this.processRunner = processRunner;
        this.moduleParser = moduleParser;
        this.lockWriter = lockWriter;
        this.coordinateResolver = coordinateResolver;
    }

    /**
     * Resolves dependency entries for a layout.
     *
     * @param layout detected layout
     * @return updated layout with best-effort dependency classpath
     * @throws IOException when filesystem operations fail
     * @throws InterruptedException when interrupted while running a build tool
     */
    public ProjectLayout resolve(final ProjectLayout layout) throws IOException, InterruptedException {
        final List<Path> classFolders = rediscoverClassFolders(layout);
        final List<Path> classpath = new ArrayList<>(layout.classpathEntries());
        final List<String> warnings = new ArrayList<>();
        if (layout.buildTool() == BuildTool.MAVEN && Files.exists(layout.root().resolve("pom.xml"))) {
            resolveMaven(layout, classpath, warnings);
        }
        if (layout.buildTool() == BuildTool.GRADLE) {
            resolveGradle(layout, classpath, warnings);
        }
        return layout.withClasspath(classFolders, distinctExistingOrJar(classpath), warnings);
    }

    /**
     * Applies local {@code javan.mod} main dependencies before compilation.
     *
     * @param layout detected layout
     * @return layout with declared main dependencies on the classpath
     * @throws IOException when module or lock files cannot be read or written
     */
    public ProjectLayout resolveDeclaredDependencies(final ProjectLayout layout) throws IOException {
        final JavanModule module = moduleParser.read(layout.root());
        if (!module.present()) {
            return layout;
        }
        validateModule(module);
        final JavanModule resolvedModule = coordinateResolver.resolve(module);
        lockWriter.write(layout.root(), resolvedModule);
        validateDependencies(resolvedModule);
        final List<Path> classpath = new ArrayList<>(layout.classpathEntries());
        for (final JavanDependency dependency : resolvedModule.dependencies()) {
            if (dependency.mainScope() && dependency.path().isPresent()) {
                classpath.add(dependency.path().orElseThrow());
            }
        }
        return layout.withClasspath(layout.classFolders(), distinctExistingOrJar(classpath), resolvedModule.warnings());
    }

    private static void validateModule(final JavanModule module) throws IOException {
        if (!module.warnings().isEmpty()) {
            throw new IOException("Invalid javan.mod\n" + joinWarnings(module.warnings()));
        }
    }

    private static void validateDependencies(final JavanModule module) throws IOException {
        for (final JavanDependency dependency : module.dependencies()) {
            if (!dependency.local() && !dependency.coordinate()) {
                throw new IOException(
                    "Unsupported javan.mod dependency at line "
                        + dependency.line()
                        + ": "
                        + dependency.notation()
                );
            }
            final Path path = dependency.path().orElseThrow();
            if (!Files.exists(path)) {
                throw new IOException(
                    "Missing javan.mod dependency at line "
                        + dependency.line()
                        + ": "
                        + dependency.notation()
                        + " -> "
                        + path.toString()
                );
            }
        }
    }

    private static String joinWarnings(final List<String> warnings) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < warnings.size(); index++) {
            if (index > 0) {
                result.append('\n');
            }
            result.append(warnings.get(index));
        }
        return result.toString();
    }

    /**
     * Joins classpath entries using the platform path separator.
     *
     * @param entries classpath entries
     * @return joined classpath
     */
    public static String join(final List<Path> entries) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                result.append(File.pathSeparator);
            }
            result.append(entries.get(index).toString());
        }
        return result.toString();
    }

    private void resolveMaven(final ProjectLayout layout, final List<Path> classpath, final List<String> warnings)
        throws IOException, InterruptedException {
        final Path outputFile = layout.outputDirectory().resolve("classpath.txt");
        Files.createDirectories(outputFile.getParent());
        final List<String> command = Files.exists(layout.root().resolve("mvnw"))
            ? List.of("./mvnw", "-q", "-DincludeScope=runtime", "-Dmdep.outputFile=" + outputFile.toString(), "dependency:build-classpath")
            : List.of("mvn", "-q", "-DincludeScope=runtime", "-Dmdep.outputFile=" + outputFile.toString(), "dependency:build-classpath");
        final ProcessRunner.Result result = processRunner.run(layout.root(), command);
        if (result.exitCode() != 0) {
            warnings.add("Unable to resolve Maven dependency classpath; continuing with project classes only.");
            return;
        }
        addClasspathFile(outputFile, classpath);
    }

    private void resolveGradle(final ProjectLayout layout, final List<Path> classpath, final List<String> warnings)
        throws IOException, InterruptedException {
        final Path initScript = layout.outputDirectory().resolve("javan-runtime-classpath.gradle");
        Files2.writeString(initScript, """
            allprojects {
                tasks.register('javanRuntimeClasspath') {
                    doLast {
                        def cfg = configurations.findByName('runtimeClasspath')
                        if (cfg == null) cfg = configurations.findByName('compileClasspath')
                        if (cfg != null) println cfg.files.collect { it.absolutePath }.join(File.pathSeparator)
                    }
                }
            }
            """);
        final List<String> command = Files.exists(layout.root().resolve("gradlew"))
            ? List.of("./gradlew", "-q", "-I", initScript.toString(), "javanRuntimeClasspath")
            : List.of("gradle", "-q", "-I", initScript.toString(), "javanRuntimeClasspath");
        final ProcessRunner.Result result = processRunner.run(layout.root(), command);
        if (result.exitCode() != 0 || Strings2.isBlank(result.stdout())) {
            warnings.add("Unable to resolve Gradle dependency classpath; continuing with project classes only.");
            return;
        }
        addClasspathText(result.stdout(), classpath);
    }

    private static void addClasspathFile(final Path outputFile, final List<Path> classpath) throws IOException {
        addClasspathText(Files2.readStringIfExists(outputFile), classpath);
    }

    private static void addClasspathText(final String value, final List<Path> classpath) {
        final String trimmed = Strings2.trimAscii(value);
        int start = 0;
        for (int index = 0; index <= trimmed.length(); index++) {
            if (index == trimmed.length() || trimmed.charAt(index) == File.pathSeparatorChar) {
                final String entry = Strings2.trimAscii(Strings2.slice(trimmed, start, index));
                if (!Strings2.isBlank(entry)) {
                    classpath.add(Path.of(entry));
                }
                start = index + 1;
            }
        }
    }

    private static List<Path> existing(final List<Path> paths) {
        final List<Path> result = new ArrayList<>();
        for (final Path path : paths) {
            if (Files.exists(path)) {
                result.add(path);
            }
        }
        return List.copyOf(result);
    }

    private static List<Path> rediscoverClassFolders(final ProjectLayout layout) throws IOException {
        final List<Path> result = new ArrayList<>(existing(layout.classFolders()));
        for (final Path candidate : ClassOutputDiscovery.discover(layout.root())) {
            if (!containsPath(result, candidate)) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static List<Path> distinctExistingOrJar(final List<Path> paths) {
        final List<Path> result = new ArrayList<>();
        for (final Path path : paths) {
            if (Files.exists(path) || path.getFileName().toString().endsWith(".jar")) {
                final Path normalized = path.toAbsolutePath().normalize();
                if (!containsPath(result, normalized)) {
                    result.add(normalized);
                }
            }
        }
        return List.copyOf(result);
    }

    private static boolean containsPath(final List<Path> paths, final Path target) {
        final String normalized = target.toAbsolutePath().normalize().toString();
        for (final Path path : paths) {
            if (path.toAbsolutePath().normalize().toString().equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
