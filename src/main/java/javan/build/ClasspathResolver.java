package javan.build;

import javan.detect.BuildTool;
import javan.detect.ProjectLayout;
import javan.util.Files2;
import javan.util.ProcessRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Resolves application class folders and dependency classpath entries.
 */
public final class ClasspathResolver {
    private static final List<String> COMMON_CLASS_FOLDERS = List.of(
        "target/classes",
        "build/classes/java/main",
        "build/classes/kotlin/main",
        "out/production/classes",
        "bin",
        "classes",
        ".javan/classes"
    );

    private final ProcessRunner processRunner;

    /**
     * Creates a resolver.
     *
     * @param processRunner process runner used for build-tool classpath tasks
     */
    public ClasspathResolver(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
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
     * Joins classpath entries using the platform path separator.
     *
     * @param entries classpath entries
     * @return joined classpath
     */
    public static String join(final List<Path> entries) {
        return String.join(File.pathSeparator, entries.stream().map(Path::toString).toList());
    }

    private void resolveMaven(final ProjectLayout layout, final List<Path> classpath, final List<String> warnings)
        throws IOException, InterruptedException {
        final Path outputFile = layout.outputDirectory().resolve("classpath.txt");
        Files.createDirectories(outputFile.getParent());
        final List<String> command = Files.exists(layout.root().resolve("mvnw"))
            ? List.of("./mvnw", "-q", "-DincludeScope=runtime", "-Dmdep.outputFile=" + outputFile, "dependency:build-classpath")
            : List.of("mvn", "-q", "-DincludeScope=runtime", "-Dmdep.outputFile=" + outputFile, "dependency:build-classpath");
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
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            warnings.add("Unable to resolve Gradle dependency classpath; continuing with project classes only.");
            return;
        }
        for (final String entry : result.stdout().trim().split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                classpath.add(Path.of(entry));
            }
        }
    }

    private static void addClasspathFile(final Path outputFile, final List<Path> classpath) throws IOException {
        final String value = Files2.readStringIfExists(outputFile).trim();
        if (value.isBlank()) {
            return;
        }
        for (final String entry : value.split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                classpath.add(Path.of(entry));
            }
        }
    }

    private static List<Path> existing(final List<Path> paths) {
        return paths.stream().filter(Files::exists).toList();
    }

    private static List<Path> rediscoverClassFolders(final ProjectLayout layout) throws IOException {
        final List<Path> result = new ArrayList<>(existing(layout.classFolders()));
        for (final String folder : COMMON_CLASS_FOLDERS) {
            final Path candidate = layout.root().resolve(folder).normalize();
            if (Files.isDirectory(candidate) && Files2.containsClassFile(candidate) && !result.contains(candidate)) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static List<Path> distinctExistingOrJar(final List<Path> paths) {
        final LinkedHashSet<Path> result = new LinkedHashSet<>();
        for (final Path path : paths) {
            if (Files.exists(path) || path.getFileName().toString().endsWith(".jar")) {
                result.add(path.toAbsolutePath().normalize());
            }
        }
        return List.copyOf(result);
    }
}
