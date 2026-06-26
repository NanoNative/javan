package javan.test;

import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

final class ProjectTestRunnerTest {
    @TempDir
    private Path tempDir;

    @Test
    void runUsesMavenWrapperWhenPresent() throws Exception {
        assumeFalse(isWindows());
        final Path root = tempDir.resolve("maven-wrapper");
        Files.createDirectories(root);
        Files.writeString(root.resolve("mvnw"), """
            #!/bin/sh
            printf 'wrapper-stdout\\n'
            printf 'wrapper-stderr\\n' >&2
            exit 7
            """);
        root.resolve("mvnw").toFile().setExecutable(true);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int exitCode = new ProjectTestRunner().run(layout(root, BuildTool.MAVEN), new PrintStream(output));

        assertThat(exitCode).isEqualTo(7);
        assertThat(output.toString()).contains(
            "Running tests:",
            "./mvnw test",
            "wrapper-stdout",
            "wrapper-stderr"
        );
    }

    @Test
    void runUsesGradleWrapperWhenPresent() throws Exception {
        assumeFalse(isWindows());
        final Path root = tempDir.resolve("gradle-wrapper");
        Files.createDirectories(root);
        Files.writeString(root.resolve("gradlew"), """
            #!/bin/sh
            printf 'gradle-wrapper\\n'
            exit 0
            """);
        root.resolve("gradlew").toFile().setExecutable(true);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int exitCode = new ProjectTestRunner().run(layout(root, BuildTool.GRADLE), new PrintStream(output));

        assertThat(exitCode).isZero();
        assertThat(output.toString()).contains("Running tests:", "./gradlew test", "gradle-wrapper");
    }

    @Test
    void runUsesSystemMavenWhenWrapperIsMissing() throws Exception {
        final Path root = tempDir.resolve("maven-project");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>runner-check</artifactId>
              <version>1.0.0</version>
            </project>
            """);

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int exitCode = new ProjectTestRunner().run(layout(root, BuildTool.MAVEN), new PrintStream(output));

        assertThat(exitCode).isZero();
        assertThat(output.toString()).contains("Running tests:", "mvn test");
    }

    @Test
    void runRejectsNoneProjectsWithoutExternalTestRunner() {
        assertThatThrownBy(() -> new ProjectTestRunner().run(layout(tempDir, BuildTool.NONE), System.out))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No configured test runner for NONE projects. Add Maven or Gradle build files, or run tests directly.");
    }

    @Test
    void runRejectsJavacProjectsWithoutExternalTestRunner() {
        assertThatThrownBy(() -> new ProjectTestRunner().run(layout(tempDir, BuildTool.JAVAC), System.out))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No configured test runner for JAVAC projects. Add Maven or Gradle build files, or run tests directly.");
    }

    @Test
    void runRejectsJarProjectsWithoutExternalTestRunner() {
        assertThatThrownBy(() -> new ProjectTestRunner().run(layout(tempDir, BuildTool.JAR), System.out))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No configured test runner for JAR projects. Add Maven or Gradle build files, or run tests directly.");
    }

    @Test
    void runRejectsClassesProjectsWithoutExternalTestRunner() {
        assertThatThrownBy(() -> new ProjectTestRunner().run(layout(tempDir, BuildTool.CLASSES), System.out))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("No configured test runner for CLASSES projects. Add Maven or Gradle build files, or run tests directly.");
    }

    private static ProjectLayout layout(final Path root, final BuildTool buildTool) {
        return new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            buildTool,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            root.resolve(".javan"),
            "app",
            List.of()
        );
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
