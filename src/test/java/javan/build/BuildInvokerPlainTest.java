package javan.build;

import javan.cli.Command;
import javan.cli.Options;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BuildInvokerPlainTest {
    @TempDir
    private Path tempDir;

    @Test
    void ensureClassesCopiesPlainResourcesWithoutRunningJavac() throws Exception {
        final Path root = tempDir.resolve("project");
        final Path source = root.resolve("src/main/java");
        final Path resources = root.resolve("src/main/resources");
        final Path output = root.resolve(".javan");
        Files.createDirectories(source.resolve("nested"));
        Files.createDirectories(resources.resolve("nested"));
        Files.createDirectories(output.resolve("classes"));
        Files.writeString(source.resolve("nested/message.txt"), "from-source");
        Files.writeString(resources.resolve("nested/message.txt"), "from-resources");
        Files.writeString(output.resolve("classes/stale.txt"), "stale");

        final ProjectLayout updated = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                BuildTool.NONE,
                List.of(source),
                List.of(resources),
                List.of(),
                List.of(),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );

        assertThat(updated.classFolders()).contains(output.resolve("classes"));
        assertThat(Files.readString(output.resolve("classes/nested/message.txt"))).isEqualTo("from-resources");
        assertThat(output.resolve("classes/stale.txt")).doesNotExist();
    }

    @Test
    void ensureClassesReturnsResolvedLayoutWhenNoSourcesOrResourcesExist() throws Exception {
        final Path root = tempDir.resolve("project");
        final Path output = root.resolve(".javan");
        final Path classes = output.resolve("existing-classes");
        final Path jar = output.resolve("lib/dependency.jar");
        Files.createDirectories(classes);
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");

        final ProjectLayout updated = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                BuildTool.NONE,
                List.of(),
                List.of(),
                List.of(classes),
                List.of(jar),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );

        assertThat(updated.classFolders()).contains(classes.toAbsolutePath().normalize());
        assertThat(updated.classpathEntries()).contains(jar.toAbsolutePath().normalize());
    }

    @Test
    void ensureClassesReturnsJarAndClassesLayoutsWithoutBuilding() throws Exception {
        final Path root = tempDir.resolve("project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("classes");
        final Path inputJar = root.resolve("input.jar");
        Files.createDirectories(classes);
        Files.writeString(inputJar, "jar");

        final ProjectLayout classesLayout = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                classes,
                InputKind.CLASSES_DIRECTORY,
                BuildTool.CLASSES,
                List.of(),
                List.of(),
                List.of(classes),
                List.of(),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );
        final ProjectLayout jarLayout = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                inputJar,
                InputKind.JAR_FILE,
                BuildTool.JAR,
                List.of(),
                List.of(),
                List.of(),
                List.of(inputJar),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );

        assertThat(classesLayout.classFolders()).contains(classes.toAbsolutePath().normalize());
        assertThat(jarLayout.classpathEntries()).contains(inputJar.toAbsolutePath().normalize());
    }

    @Test
    void ensureClassesReturnsJarBuildToolLayoutsEvenWhenInputIsNotJarFile() throws Exception {
        final Path root = tempDir.resolve("jar-project");
        final Path output = root.resolve(".javan");
        final Path classpathJar = root.resolve("libs/app.jar");
        Files.createDirectories(classpathJar.getParent());
        Files.writeString(classpathJar, "jar");

        final ProjectLayout jarLayout = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                BuildTool.JAR,
                List.of(),
                List.of(),
                List.of(),
                List.of(classpathJar),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );

        assertThat(jarLayout.classpathEntries()).contains(classpathJar.toAbsolutePath().normalize());
    }

    @Test
    void ensureClassesRejectsUnsupportedNullBuildTool() {
        final Path root = tempDir.resolve("project");

        assertThatThrownBy(() -> new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                root.resolve(".javan"),
                "demo",
                List.of()
            ),
            options(root)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unsupported build tool");
    }

    @Test
    void ensureClassesRunsJavacBuildToolAndPrependsGeneratedClasses() throws Exception {
        final Path root = tempDir.resolve("javac-project");
        final Path source = root.resolve("src/main/java/com/acme");
        final Path output = root.resolve(".javan");
        final Path existing = root.resolve("existing-classes");
        Files.createDirectories(source);
        Files.createDirectories(existing);
        Files.writeString(source.resolve("App.java"), """
            package com.acme;
            final class App {
                static int sum() {
                    return 1 + 2;
                }
            }
            """);

        final ProjectLayout updated = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                BuildTool.JAVAC,
                List.of(root.resolve("src/main/java")),
                List.of(),
                List.of(existing),
                List.of(),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );

        assertThat(updated.classFolders().getFirst()).isEqualTo(output.resolve("classes").toAbsolutePath().normalize());
        assertThat(updated.classFolders()).contains(existing.toAbsolutePath().normalize());
        assertThat(output.resolve("classes/com/acme/App.class")).exists();
    }

    @Test
    void ensureClassesRejectsFailingPlainJavacBuild() throws Exception {
        final Path root = tempDir.resolve("broken-javac-project");
        final Path source = root.resolve("src/main/java/com/acme");
        Files.createDirectories(source);
        Files.writeString(source.resolve("Broken.java"), "package com.acme; final class Broken {");

        assertThatThrownBy(() -> new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                BuildTool.JAVAC,
                List.of(root.resolve("src/main/java")),
                List.of(),
                List.of(),
                List.of(),
                root.resolve(".javan"),
                "demo",
                List.of()
            ),
            options(root)
        ))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("javac failed");
    }

    @Test
    void deleteGeneratedResourcesReturnsForMissingDirectoryAndDeletesExistingResources() throws Exception {
        final Path missing = tempDir.resolve("missing-classes");
        deleteGeneratedResources(missing);

        final Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes.resolve("nested"));
        Files.writeString(classes.resolve("nested/message.txt"), "message");
        Files.writeString(classes.resolve("nested/config.properties"), "k=v");

        deleteGeneratedResources(classes);

        assertThat(classes.resolve("nested/message.txt")).doesNotExist();
        assertThat(classes.resolve("nested/config.properties")).doesNotExist();
    }

    @Test
    void addResourcesSkipsExcludedRootsAndNormalizesRelativePaths() throws Exception {
        final Path root = tempDir.resolve("project");
        final Path sources = root.resolve("src/main/java");
        final Path resources = root.resolve("src/main/resources");
        final Path sourceAsset = sources.resolve("nested/logo.png");
        final Path resourceAsset = resources.resolve("nested/messages.properties");
        Files.createDirectories(sourceAsset.getParent());
        Files.createDirectories(resourceAsset.getParent());
        Files.writeString(sourceAsset, "png");
        Files.writeString(resourceAsset, "hello=world");

        final List<Object> copies = new ArrayList<>();
        addResources(copies, sources, sources, List.of(resources));

        assertThat(copies).hasSize(1);
        assertThat(resourceCopyRelativePath(copies.getFirst())).isEqualTo("nested/logo.png");
    }

    @Test
    void addResourcesSkipsFilesNestedInsideExcludedRoots() throws Exception {
        final Path root = tempDir.resolve("project");
        final Path sourceRoot = root.resolve("src/main");
        final Path includedAsset = sourceRoot.resolve("java/nested/logo.png");
        final Path excludedRoot = sourceRoot.resolve("resources");
        final Path excludedAsset = excludedRoot.resolve("nested/messages.properties");
        Files.createDirectories(includedAsset.getParent());
        Files.createDirectories(excludedAsset.getParent());
        Files.writeString(includedAsset, "png");
        Files.writeString(excludedAsset, "hello=world");

        final List<Object> copies = new ArrayList<>();
        addResources(copies, sourceRoot, sourceRoot, List.of(excludedRoot));

        assertThat(copies).hasSize(1);
        assertThat(resourceCopyRelativePath(copies.getFirst())).isEqualTo("java/nested/logo.png");
    }

    @Test
    void addResourceAndAddPathDeduplicateEquivalentEntries() throws Exception {
        final List<Object> resources = new ArrayList<>();
        final Path file = tempDir.resolve("root/messages.properties");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hello");

        addResource(resources, "messages.properties", file);
        addResource(resources, "messages.properties", file);

        final List<Path> paths = new ArrayList<>();
        addPath(paths, file);
        addPath(paths, file.toAbsolutePath().normalize());

        assertThat(resources).hasSize(1);
        assertThat(paths).hasSize(1);
        assertThat(containsPath(paths, file)).isTrue();
    }

    @Test
    void addResourceAppendsDistinctRelativePathsAfterScanningExistingEntries() throws Exception {
        final List<Object> resources = new ArrayList<>();
        final Path first = tempDir.resolve("root/first.properties");
        final Path second = tempDir.resolve("root/second.properties");
        Files.createDirectories(first.getParent());
        Files.writeString(first, "first");
        Files.writeString(second, "second");

        addResource(resources, "first.properties", first);
        addResource(resources, "second.properties", second);

        assertThat(resources).hasSize(2);
        assertThat(resourceCopyRelativePath(resources.get(1))).isEqualTo("second.properties");
    }

    @Test
    void startsWithAnyDetectsExcludedRoots() throws Exception {
        final Path root = tempDir.resolve("root");
        final Path excluded = root.resolve("excluded");
        final Path included = root.resolve("included/file.txt");

        assertThat(startsWithAny(excluded.resolve("file.txt").toAbsolutePath().normalize(), List.of(excluded))).isTrue();
        assertThat(startsWithAny(included.toAbsolutePath().normalize(), List.of(excluded))).isFalse();
    }

    private static Options options(final Path root) {
        return new Options(
            Command.BUILD,
            Optional.of(root),
            Optional.empty(),
            List.of(),
            List.of(),
            Optional.empty(),
            BuildKind.APP,
            "APP",
            List.of(),
            Profile.CORE,
            List.of(),
            List.of(),
            false,
            Optional.empty(),
            List.of()
        );
    }

    private static void deleteGeneratedResources(final Path classes) throws Exception {
        final Method method = BuildInvoker.class.getDeclaredMethod("deleteGeneratedResources", Path.class);
        method.setAccessible(true);
        method.invoke(null, classes);
    }

    @SuppressWarnings("unchecked")
    private static void addResources(
        final List<Object> result,
        final Path root,
        final Path relativeRoot,
        final List<Path> excludedRoots
    ) throws Exception {
        final Method method = BuildInvoker.class.getDeclaredMethod("addResources", List.class, Path.class, Path.class, List.class);
        method.setAccessible(true);
        method.invoke(null, result, root, relativeRoot, excludedRoots);
    }

    private static void addResource(final List<Object> result, final String relativePath, final Path source) throws Exception {
        final Method method = BuildInvoker.class.getDeclaredMethod("addResource", List.class, String.class, Path.class);
        method.setAccessible(true);
        method.invoke(null, result, relativePath, source);
    }

    private static void addPath(final List<Path> values, final Path path) throws Exception {
        final Method method = BuildInvoker.class.getDeclaredMethod("addPath", List.class, Path.class);
        method.setAccessible(true);
        method.invoke(null, values, path);
    }

    private static boolean containsPath(final List<Path> values, final Path target) throws Exception {
        final Method method = BuildInvoker.class.getDeclaredMethod("containsPath", List.class, Path.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, values, target);
    }

    private static boolean startsWithAny(final Path normalized, final List<Path> roots) throws Exception {
        final Method method = BuildInvoker.class.getDeclaredMethod("startsWithAny", Path.class, List.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, normalized, roots);
    }

    private static String resourceCopyRelativePath(final Object resourceCopy) throws Exception {
        final Method method = resourceCopy.getClass().getDeclaredMethod("relativePath");
        method.setAccessible(true);
        return (String) method.invoke(resourceCopy);
    }
}
