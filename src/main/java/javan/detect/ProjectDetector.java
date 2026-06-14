package javan.detect;

import javan.cli.Options;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects Java project layout, build tool, source folders, class folders, and defaults.
 */
public final class ProjectDetector {
    private static final List<String> COMMON_SOURCE_FOLDERS = List.of("src/main/java", "src", ".");
    private static final List<String> COMMON_RESOURCE_FOLDERS = List.of("src/main/resources", "resources");

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
        final List<Path> classFolders = classFolders(cwd, input, root, inputKind, options.classFolders());
        final List<Path> classpathEntries = new ArrayList<>(absoluteAll(cwd, options.classpathEntries()));
        if (inputKind == InputKind.JAR_FILE) {
            classpathEntries.add(input);
        }
        final String outputName = selectedOutputName(options, input, root, inputKind);
        return new ProjectLayout(
            root,
            input,
            inputKind,
            buildTool,
            List.copyOf(sourceFolders),
            List.copyOf(resourceFolders),
            List.copyOf(classFolders),
            List.copyOf(classpathEntries),
            outputDirectory(root, inputKind),
            Strings2.executableName(outputName),
            List.copyOf(warnings)
        );
    }

    private static String selectedOutputName(
        final Options options,
        final Path input,
        final Path root,
        final InputKind inputKind
    ) throws IOException {
        if (options.outputName().isPresent()) {
            return options.outputName().orElseThrow();
        }
        return outputName(input, root, inputKind);
    }

    private static Path outputDirectory(final Path root, final InputKind inputKind) {
        if (inputKind == InputKind.CLASSES_DIRECTORY && root.getParent() != null) {
            return root.getParent().resolve(".javan");
        }
        return root.resolve(".javan");
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
        if (inputKind == InputKind.JAR_FILE || inputKind == InputKind.SOURCE_FILE) {
            if (input.getParent() == null) {
                return input.toAbsolutePath().getParent();
            }
            return input.getParent();
        }
        if (inputKind == InputKind.CLASSES_DIRECTORY || inputKind == InputKind.PROJECT_DIRECTORY) {
            return input;
        }
        throw new IllegalStateException("Unsupported input kind");
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
            final Path source = configuredFolder(root, folder);
            if (Files.isDirectory(source) && !Files2.findJavaSources(source).isEmpty() && !overlaps(result, source)) {
                result.add(source);
            }
        }
        return List.copyOf(result);
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
            final Path resource = configuredFolder(root, folder);
            if (Files.isDirectory(resource) && !findResourceFiles(resource).isEmpty() && !overlaps(result, resource)) {
                result.add(resource);
            }
        }
        return List.copyOf(result);
    }

    private static boolean overlaps(final List<Path> paths, final Path candidate) {
        for (final Path path : paths) {
            if (candidate.startsWith(path) || path.startsWith(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static Path configuredFolder(final Path root, final String folder) {
        if (".".equals(folder)) {
            return root.normalize();
        }
        return root.resolve(folder).normalize();
    }

    private static List<Path> findResourceFiles(final Path root) throws IOException {
        return Files2.findResourceFiles(root);
    }

    private static List<Path> classFolders(
        final Path cwd,
        final Path input,
        final Path root,
        final InputKind inputKind,
        final List<Path> explicitClassFolders
    ) throws IOException {
        if (!explicitClassFolders.isEmpty()) {
            final List<Path> result = new ArrayList<>();
            for (final Path folder : explicitClassFolders) {
                result.add((folder.isAbsolute() ? folder : cwd.resolve(folder)).toAbsolutePath().normalize());
            }
            return List.copyOf(result);
        }
        if (inputKind == InputKind.CLASSES_DIRECTORY) {
            return List.of(input);
        }
        return ClassOutputDiscovery.discover(root);
    }

    private static List<Path> absoluteAll(final Path cwd, final List<Path> paths) {
        final List<Path> result = new ArrayList<>();
        for (final Path path : paths) {
            result.add((path.isAbsolute() ? path : cwd.resolve(path)).normalize());
        }
        return List.copyOf(result);
    }

    private static String outputName(final Path input, final Path root, final InputKind inputKind) throws IOException {
        if (Files.exists(root.resolve("pom.xml"))) {
            final String artifactId = firstXmlTag(root.resolve("pom.xml"), "artifactId");
            if (!Strings2.isBlank(artifactId)) {
                return artifactId;
            }
        }
        for (final String settings : List.of("settings.gradle", "settings.gradle.kts")) {
            final Path file = root.resolve(settings);
            if (Files.exists(file)) {
                final String name = gradleRootProjectName(file);
                if (!Strings2.isBlank(name)) {
                    return name;
                }
            }
        }
        if (inputKind == InputKind.JAR_FILE || inputKind == InputKind.SOURCE_FILE) {
            final String filename = input.getFileName().toString();
            final int dot = lastIndexOf(filename, '.');
            return dot > 0 ? Strings2.slice(filename, 0, dot) : filename;
        }
        return root.getFileName() == null ? "app" : Strings2.toAsciiLowerCase(root.getFileName().toString());
    }

    private static String firstXmlTag(final Path file, final String tag) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        final String content = Files2.readStringIfExists(file);
        final String open = "<" + tag + ">";
        final String close = "</" + tag + ">";
        final int start = indexOf(content, open, 0);
        if (start < 0) {
            return "";
        }
        final int valueStart = start + open.length();
        final int end = indexOf(content, close, valueStart);
        if (end < 0) {
            return "";
        }
        return Strings2.trimAscii(Strings2.slice(content, valueStart, end));
    }

    private static String gradleRootProjectName(final Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        final String content = Files2.readStringIfExists(file);
        final String key = "rootProject.name";
        int index = indexOf(content, key, 0);
        while (index >= 0) {
            final int equals = nextNonWhitespace(content, index + key.length());
            if (equals < content.length() && content.charAt(equals) == '=') {
                final int quote = nextNonWhitespace(content, equals + 1);
                if (quote < content.length() && (content.charAt(quote) == '\'' || content.charAt(quote) == '"')) {
                    final char quoteChar = content.charAt(quote);
                    final int end = indexOfChar(content, quoteChar, quote + 1);
                    if (end > quote) {
                        return Strings2.trimAscii(Strings2.slice(content, quote + 1, end));
                    }
                }
            }
            index = indexOf(content, key, index + key.length());
        }
        return "";
    }

    private static int lastIndexOf(final String value, final char target) {
        for (int index = value.length() - 1; index >= 0; index--) {
            if (value.charAt(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfChar(final String value, final char target, final int start) {
        for (int index = start; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOf(final String value, final String target, final int start) {
        if (target.length() == 0) {
            return start;
        }
        for (int index = start; index <= value.length() - target.length(); index++) {
            if (matchesAt(value, target, index)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean matchesAt(final String value, final String target, final int start) {
        for (int index = 0; index < target.length(); index++) {
            if (value.charAt(start + index) != target.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static int nextNonWhitespace(final String value, final int start) {
        int index = start;
        while (index < value.length() && isAsciiWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isAsciiWhitespace(final char value) {
        if (value == ' ') {
            return true;
        }
        if (value == '\t') {
            return true;
        }
        if (value == '\n') {
            return true;
        }
        if (value == '\r') {
            return true;
        }
        if (value == '\f') {
            return true;
        }
        return false;
    }
}
