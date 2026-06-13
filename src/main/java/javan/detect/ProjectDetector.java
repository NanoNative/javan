package javan.detect;

import javan.cli.Options;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects Java project layout, build tool, source folders, class folders, and defaults.
 */
public final class ProjectDetector {
    private static final List<String> COMMON_CLASS_FOLDERS = List.of(
        "target/classes",
        "build/classes/java/main",
        "build/classes/kotlin/main",
        "out/production/classes",
        "bin",
        "classes",
        ".javan/classes"
    );

    private static final List<String> COMMON_SOURCE_FOLDERS = List.of("src/main/java", "src", ".");
    private static final List<String> COMMON_RESOURCE_FOLDERS = List.of("src/main/resources", "resources");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>\\s*([^<]+)\\s*</artifactId>");
    private static final Pattern GRADLE_ROOT_NAME = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");

    /**
     * Detects project metadata from CLI options and a working directory.
     *
     * @param cwd current working directory
     * @param options parsed options
     * @return detected project layout
     * @throws IOException when filesystem inspection fails
     */
    public ProjectLayout detect(final Path cwd, final Options options) throws IOException {
        final Path input = options.target().orElse(cwd).toAbsolutePath().normalize();
        final InputKind inputKind = inputKind(input);
        final Path root = root(input, inputKind);
        final BuildTool buildTool = buildTool(root, inputKind);
        final List<String> warnings = new ArrayList<>();
        final List<Path> sourceFolders = sourceFolders(input, root, inputKind);
        final List<Path> resourceFolders = resourceFolders(input, root, inputKind);
        final List<Path> classFolders = classFolders(input, root, inputKind, options.classFolders());
        final List<Path> classpathEntries = new ArrayList<>(absoluteAll(cwd, options.classpathEntries()));
        if (inputKind == InputKind.JAR_FILE) {
            classpathEntries.add(input);
        }
        final String outputName = options.outputName().orElseGet(() -> outputName(input, root, inputKind));
        return new ProjectLayout(
            root,
            input,
            inputKind,
            buildTool,
            List.copyOf(sourceFolders),
            List.copyOf(resourceFolders),
            List.copyOf(classFolders),
            List.copyOf(classpathEntries),
            root.resolve(".javan"),
            Strings2.executableName(outputName),
            List.copyOf(warnings)
        );
    }

    private static InputKind inputKind(final Path input) throws IOException {
        if (Files.isRegularFile(input) && input.getFileName().toString().endsWith(".jar")) {
            return InputKind.JAR_FILE;
        }
        if (Files.isRegularFile(input) && input.getFileName().toString().endsWith(".java")) {
            return InputKind.SOURCE_FILE;
        }
        if (Files.isDirectory(input) && Files2.containsClassFile(input) && !looksLikeProject(input)) {
            return InputKind.CLASSES_DIRECTORY;
        }
        return InputKind.PROJECT_DIRECTORY;
    }

    private static boolean looksLikeProject(final Path input) {
        return Files.exists(input.resolve("pom.xml"))
            || Files.exists(input.resolve("build.gradle"))
            || Files.exists(input.resolve("build.gradle.kts"))
            || Files.exists(input.resolve("src"))
            || Files.exists(input.resolve("src/main/java"));
    }

    private static Path root(final Path input, final InputKind inputKind) {
        return switch (inputKind) {
            case JAR_FILE, SOURCE_FILE -> input.getParent() == null ? input.toAbsolutePath().getParent() : input.getParent();
            case CLASSES_DIRECTORY, PROJECT_DIRECTORY -> input;
        };
    }

    private static BuildTool buildTool(final Path root, final InputKind inputKind) {
        if (inputKind == InputKind.JAR_FILE) {
            return BuildTool.JAR;
        }
        if (inputKind == InputKind.CLASSES_DIRECTORY) {
            return BuildTool.CLASSES;
        }
        if (Files.exists(root.resolve("gradlew"))
            || Files.exists(root.resolve("gradlew.bat"))
            || Files.exists(root.resolve("build.gradle"))
            || Files.exists(root.resolve("build.gradle.kts"))
            || Files.exists(root.resolve("settings.gradle"))
            || Files.exists(root.resolve("settings.gradle.kts"))) {
            return BuildTool.GRADLE;
        }
        if (Files.exists(root.resolve("mvnw"))
            || Files.exists(root.resolve("mvnw.cmd"))
            || Files.exists(root.resolve("pom.xml"))) {
            return BuildTool.MAVEN;
        }
        return BuildTool.JAVAC;
    }

    private static List<Path> sourceFolders(final Path input, final Path root, final InputKind inputKind) throws IOException {
        if (inputKind == InputKind.SOURCE_FILE) {
            return List.of(input.getParent());
        }
        final List<Path> result = new ArrayList<>();
        for (final String folder : COMMON_SOURCE_FOLDERS) {
            final Path source = root.resolve(folder).normalize();
            if (Files.isDirectory(source) && !Files2.findJavaSources(source).isEmpty() && !containsParent(result, source)) {
                result.add(source);
            }
        }
        return result.stream().distinct().toList();
    }

    private static List<Path> resourceFolders(final Path input, final Path root, final InputKind inputKind) throws IOException {
        if (inputKind == InputKind.JAR_FILE || inputKind == InputKind.CLASSES_DIRECTORY) {
            return List.of();
        }
        if (inputKind == InputKind.SOURCE_FILE) {
            return List.of(input.getParent());
        }
        final List<Path> result = new ArrayList<>();
        for (final String folder : COMMON_RESOURCE_FOLDERS) {
            final Path resource = root.resolve(folder).normalize();
            if (Files.isDirectory(resource) && !findResourceFiles(resource).isEmpty() && !containsParent(result, resource)) {
                result.add(resource);
            }
        }
        return result.stream().distinct().toList();
    }

    private static boolean containsParent(final List<Path> paths, final Path candidate) {
        return paths.stream().anyMatch(candidate::startsWith);
    }

    private static List<Path> findResourceFiles(final Path root) throws IOException {
        return Files2.findFiles(root, ProjectDetector::resourceFile);
    }

    private static boolean resourceFile(final Path file) {
        final String name = file.getFileName().toString();
        return !name.endsWith(".java") && !name.endsWith(".class");
    }

    private static List<Path> classFolders(
        final Path input,
        final Path root,
        final InputKind inputKind,
        final List<Path> explicitClassFolders
    ) throws IOException {
        if (!explicitClassFolders.isEmpty()) {
            return explicitClassFolders.stream().map(Path::toAbsolutePath).map(Path::normalize).toList();
        }
        if (inputKind == InputKind.CLASSES_DIRECTORY) {
            return List.of(input);
        }
        final List<Path> result = new ArrayList<>();
        for (final String folder : COMMON_CLASS_FOLDERS) {
            final Path classes = root.resolve(folder).normalize();
            if (Files.isDirectory(classes) && Files2.containsClassFile(classes)) {
                result.add(classes);
            }
        }
        result.sort(Comparator.comparingLong(ProjectDetector::newestClass).reversed());
        return result;
    }

    private static List<Path> absoluteAll(final Path cwd, final List<Path> paths) {
        return paths.stream()
            .map(path -> path.isAbsolute() ? path : cwd.resolve(path))
            .map(Path::normalize)
            .toList();
    }

    private static long newestClass(final Path folder) {
        try {
            return Files2.newestModified(List.of(folder), ".class");
        } catch (final IOException exception) {
            return 0;
        }
    }

    private static String outputName(final Path input, final Path root, final InputKind inputKind) {
        if (Files.exists(root.resolve("pom.xml"))) {
            final String artifactId = firstMatch(root.resolve("pom.xml"), ARTIFACT_ID);
            if (!artifactId.isBlank()) {
                return artifactId;
            }
        }
        for (final String settings : List.of("settings.gradle", "settings.gradle.kts")) {
            final Path file = root.resolve(settings);
            if (Files.exists(file)) {
                final String name = firstMatch(file, GRADLE_ROOT_NAME);
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        if (inputKind == InputKind.JAR_FILE || inputKind == InputKind.SOURCE_FILE) {
            final String filename = input.getFileName().toString();
            final int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        }
        return root.getFileName() == null ? "app" : root.getFileName().toString().toLowerCase(Locale.ROOT);
    }

    private static String firstMatch(final Path file, final Pattern pattern) {
        try {
            final Matcher matcher = pattern.matcher(Files2.readStringIfExists(file));
            return matcher.find() ? matcher.group(1).trim() : "";
        } catch (final IOException exception) {
            return "";
        }
    }
}
