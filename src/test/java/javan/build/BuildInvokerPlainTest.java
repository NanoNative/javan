package javan.build;

import javan.cli.Command;
import javan.cli.Options;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.profile.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
