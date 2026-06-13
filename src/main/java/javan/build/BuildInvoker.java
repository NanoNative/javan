package javan.build;

import javan.cli.Options;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.util.Files2;
import javan.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ensures Java classes exist by invoking the detected build tool or plain javac.
 */
public final class BuildInvoker {
    private final ProcessRunner processRunner;
    private final ClasspathResolver classpathResolver;

    /**
     * Creates a build invoker.
     */
    public BuildInvoker() {
        this(new ProcessRunner(), new ClasspathResolver(new ProcessRunner()));
    }

    /**
     * Creates a build invoker with explicit dependencies.
     *
     * @param processRunner process runner
     * @param classpathResolver dependency classpath resolver
     */
    public BuildInvoker(final ProcessRunner processRunner, final ClasspathResolver classpathResolver) {
        this.processRunner = processRunner;
        this.classpathResolver = classpathResolver;
    }

    /**
     * Ensures compiled classes exist for a layout.
     *
     * @param layout detected project layout
     * @param options parsed CLI options
     * @return layout updated with class folders and classpath entries
     * @throws IOException when compilation or classpath resolution fails
     * @throws InterruptedException when interrupted while waiting for a build tool
     */
    public ProjectLayout ensureClasses(final ProjectLayout layout, final Options options) throws IOException, InterruptedException {
        Files.createDirectories(layout.outputDirectory());
        if (options.noBuild() || layout.inputKind() == InputKind.JAR_FILE || layout.buildTool() == BuildTool.CLASSES) {
            return classpathResolver.resolve(layout);
        }
        return switch (layout.buildTool()) {
            case MAVEN -> buildMaven(layout);
            case GRADLE -> buildGradle(layout);
            case JAVAC, NONE -> buildPlainJavac(layout);
            case JAR, CLASSES -> classpathResolver.resolve(layout);
        };
    }

    private ProjectLayout buildMaven(final ProjectLayout layout) throws IOException, InterruptedException {
        final List<String> command = Files.exists(layout.root().resolve("mvnw"))
            ? List.of("./mvnw", "-q", "-DskipTests", "compile")
            : List.of("mvn", "-q", "-DskipTests", "compile");
        final ProcessRunner.Result result = processRunner.run(layout.root(), command);
        if (result.exitCode() != 0) {
            throw new IOException("Maven compile failed\n" + result.stderr() + result.stdout());
        }
        return classpathResolver.resolve(layout);
    }

    private ProjectLayout buildGradle(final ProjectLayout layout) throws IOException, InterruptedException {
        final List<String> command = Files.exists(layout.root().resolve("gradlew"))
            ? List.of("./gradlew", "classes")
            : List.of("gradle", "classes");
        final ProcessRunner.Result result = processRunner.run(layout.root(), command);
        if (result.exitCode() != 0) {
            throw new IOException(gradleFailureMessage(result));
        }
        return classpathResolver.resolve(layout);
    }

    static String gradleFailureMessage(final ProcessRunner.Result result) {
        final String output = result.stderr() + result.stdout();
        if (output.contains("Unsupported class file major version 69")) {
            return "Gradle classes failed\n"
                + "Detected Java 25 classfile/runtime incompatibility. Gradle requires 9.1.0 or newer to run on Java 25.\n"
                + "Fix: add or update a Gradle wrapper with Gradle 9.1.0+ and rerun javan.\n"
                + output;
        }
        return "Gradle classes failed\n" + output;
    }

    private ProjectLayout buildPlainJavac(final ProjectLayout layout) throws IOException, InterruptedException {
        final List<Path> classFolders = layout.classFolders().isEmpty()
            ? List.of(layout.outputDirectory().resolve("classes"))
            : layout.classFolders();
        final List<Path> sources = sources(layout);
        final boolean hasResources = hasPlainResources(layout);
        if (!Files2.sourcesNewerThanClasses(layout.sourceFolders(), classFolders)) {
            final Path classes = layout.outputDirectory().resolve("classes");
            deleteGeneratedResources(classes);
            if (!hasResources) {
                return classpathResolver.resolve(layout.withClasspath(classFolders, layout.classpathEntries(), List.of()));
            }
            Files.createDirectories(classes);
            copyPlainResources(layout, classes);
            final List<Path> updatedClassFolders = new ArrayList<>(classFolders);
            if (!updatedClassFolders.contains(classes)) {
                updatedClassFolders.addFirst(classes);
            }
            return classpathResolver.resolve(layout.withClasspath(updatedClassFolders, layout.classpathEntries(), List.of()));
        }
        if (sources.isEmpty() && !hasResources) {
            return classpathResolver.resolve(layout);
        }
        final Path classes = layout.outputDirectory().resolve("classes");
        Files.createDirectories(classes);
        if (!sources.isEmpty()) {
            final List<String> command = new ArrayList<>();
            command.add("javac");
            command.add("-d");
            command.add(classes.toString());
            if (!layout.classpathEntries().isEmpty()) {
                command.add("-classpath");
                command.add(ClasspathResolver.join(layout.classpathEntries()));
            }
            sources.stream().map(Path::toString).forEach(command::add);
            final ProcessRunner.Result result = processRunner.run(layout.root(), command);
            if (result.exitCode() != 0) {
                throw new IOException("javac failed\n" + result.stderr() + result.stdout());
            }
        }
        copyPlainResources(layout, classes);
        final List<Path> updatedClassFolders = new ArrayList<>(classFolders);
        if (!updatedClassFolders.contains(classes)) {
            updatedClassFolders.addFirst(classes);
        }
        return classpathResolver.resolve(layout.withClasspath(updatedClassFolders, layout.classpathEntries(), List.of()));
    }

    private static List<Path> sources(final ProjectLayout layout) throws IOException {
        if (layout.inputKind() == InputKind.SOURCE_FILE) {
            return List.of(layout.input());
        }
        final List<Path> result = new ArrayList<>();
        for (final Path sourceFolder : layout.sourceFolders()) {
            result.addAll(Files2.findJavaSources(sourceFolder));
        }
        return result.stream().distinct().toList();
    }

    private static boolean hasPlainResources(final ProjectLayout layout) throws IOException {
        return !plainResources(layout).isEmpty();
    }

    private static void copyPlainResources(final ProjectLayout layout, final Path classes) throws IOException {
        deleteGeneratedResources(classes);
        for (final Map.Entry<String, Path> resource : plainResources(layout).entrySet()) {
            final Path target = classes.resolve(resource.getKey()).normalize();
            if (!target.startsWith(classes)) {
                throw new IOException("Resource path escapes class output: " + resource.getKey());
            }
            Files.createDirectories(target.getParent());
            Files.copy(resource.getValue(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteGeneratedResources(final Path classes) throws IOException {
        if (!Files.isDirectory(classes)) {
            return;
        }
        for (final Path file : Files2.findFiles(classes, BuildInvoker::resourceFile)) {
            Files.deleteIfExists(file);
        }
    }

    private static Map<String, Path> plainResources(final ProjectLayout layout) throws IOException {
        final LinkedHashMap<String, Path> result = new LinkedHashMap<>();
        for (final Path resourceFolder : layout.resourceFolders()) {
            addResources(result, resourceFolder, resourceFolder, List.of());
        }
        for (final Path sourceFolder : layout.sourceFolders()) {
            addResources(result, sourceFolder, sourceFolder, layout.resourceFolders());
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void addResources(
        final Map<String, Path> result,
        final Path root,
        final Path relativeRoot,
        final List<Path> excludedRoots
    ) throws IOException {
        for (final Path file : Files2.findFiles(root, BuildInvoker::resourceFile)) {
            final Path normalized = file.toAbsolutePath().normalize();
            if (excludedRoots.stream().map(Path::toAbsolutePath).map(Path::normalize).anyMatch(normalized::startsWith)) {
                continue;
            }
            final String relative = relativeRoot.relativize(file).toString().replace(java.io.File.separatorChar, '/');
            result.putIfAbsent(relative, file);
        }
    }

    private static boolean resourceFile(final Path file) {
        final String name = file.getFileName().toString();
        return !name.endsWith(".java") && !name.endsWith(".class");
    }
}
