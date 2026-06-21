package javan.build;

import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.dependency.JavanCoordinateResolver;
import javan.dependency.JavanLockWriter;
import javan.dependency.JavanModuleParser;
import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class ClasspathResolverTest {
    @TempDir
    private Path tempDir;

    @Test
    void joinUsesPlatformSeparator() {
        assertThat(ClasspathResolver.join(List.of(Path.of("a.jar"), Path.of("b.jar"))))
            .isEqualTo("a.jar" + java.io.File.pathSeparator + "b.jar");
    }

    @Test
    void resolveMavenWrapperReadsClasspathFileAndAddsWarningOnFailure() throws Exception {
        final Path root = tempDir.resolve("maven-project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("target/classes");
        final Path depA = root.resolve("repo/a.jar");
        final Path depB = root.resolve("repo/b.jar");
        Files.createDirectories(classes);
        Files.createDirectories(depA.getParent());
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(depA, "a");
        Files.writeString(depB, "b");
        Files.writeString(root.resolve("mvnw"), """
            #!/bin/sh
            case "$*" in
              *build-classpath*)
                out=""
                for arg in "$@"; do
                  case "$arg" in
                    -Dmdep.outputFile=*) out=${arg#-Dmdep.outputFile=} ;;
                  esac
                done
                printf '%s%s%s' "$PWD/repo/a.jar" ":" "$PWD/repo/b.jar" > "$out"
                exit 0
                ;;
              *)
                exit 9
                ;;
            esac
            """);
        root.resolve("mvnw").toFile().setExecutable(true);

        final ProjectLayout resolved = new ClasspathResolver(new ProcessRunner()).resolve(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.MAVEN,
            List.of(),
            List.of(),
            List.of(classes),
            List.of(depA),
            output,
            "demo",
            List.of()
        ));

        assertThat(resolved.classFolders()).contains(classes.toAbsolutePath().normalize());
        assertThat(resolved.classpathEntries())
            .extracting(path -> path.getFileName().toString())
            .contains("a.jar", "b.jar");
        assertThat(resolved.warnings()).isEmpty();

        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\nexit 7\n");
        root.resolve("mvnw").toFile().setExecutable(true);

        final ProjectLayout failed = new ClasspathResolver(new ProcessRunner()).resolve(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.MAVEN,
            List.of(),
            List.of(),
            List.of(classes),
            List.of(),
            output,
            "demo",
            List.of()
        ));

        assertThat(failed.warnings()).containsExactly("Unable to resolve Maven dependency classpath; continuing with project classes only.");
    }

    @Test
    void resolveGradleWrapperReadsStdoutAndWarnsOnBlankOutput() throws Exception {
        final Path root = tempDir.resolve("gradle-project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("build/classes/java/main");
        final Path dep = root.resolve("repo/dependency.jar");
        Files.createDirectories(classes);
        Files.createDirectories(dep.getParent());
        Files.writeString(dep, "jar");
        Files.writeString(root.resolve("gradlew"), """
            #!/bin/sh
            case "$*" in
              *javanRuntimeClasspath*)
                printf '%s' "$PWD/repo/dependency.jar"
                exit 0
                ;;
            esac
            exit 3
            """);
        root.resolve("gradlew").toFile().setExecutable(true);

        final ProjectLayout resolved = new ClasspathResolver(new ProcessRunner()).resolve(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.GRADLE,
            List.of(),
            List.of(),
            List.of(classes),
            List.of(),
            output,
            "demo",
            List.of()
        ));

        assertThat(resolved.classpathEntries())
            .extracting(path -> path.getFileName().toString())
            .containsExactly("dependency.jar");
        assertThat(resolved.warnings()).isEmpty();
        assertThat(output.resolve("javan-runtime-classpath.gradle")).exists();

        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\nexit 0\n");
        root.resolve("gradlew").toFile().setExecutable(true);

        final ProjectLayout blank = new ClasspathResolver(new ProcessRunner()).resolve(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.GRADLE,
            List.of(),
            List.of(),
            List.of(classes),
            List.of(),
            output,
            "demo",
            List.of()
        ));

        assertThat(blank.warnings()).containsExactly("Unable to resolve Gradle dependency classpath; continuing with project classes only.");
    }

    @Test
    void resolveKeepsMissingJarDropsMissingNonJarAndDeduplicatesExistingEntries() throws Exception {
        final Path root = tempDir.resolve("plain-project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("classes");
        final Path existing = root.resolve("repo/existing.jar");
        final Path missingJar = root.resolve("repo/missing.jar");
        final Path missingDirectory = root.resolve("repo/missing-dir");
        Files.createDirectories(classes);
        Files.createDirectories(existing.getParent());
        Files.writeString(existing, "jar");

        final ProjectLayout resolved = new ClasspathResolver(new ProcessRunner()).resolve(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.NONE,
            List.of(),
            List.of(),
            List.of(classes),
            List.of(existing, existing.toAbsolutePath(), missingJar, missingDirectory),
            output,
            "demo",
            List.of()
        ));

        assertThat(resolved.classpathEntries()).contains(existing.toAbsolutePath().normalize(), missingJar.toAbsolutePath().normalize());
        assertThat(resolved.classpathEntries()).doesNotContain(missingDirectory.toAbsolutePath().normalize());
        assertThat(resolved.classpathEntries().stream().filter(path -> path.equals(existing.toAbsolutePath().normalize()))).hasSize(1);
    }

    @Test
    void resolveDeclaredDependenciesAddsOnlyMainScopeAndWritesLock() throws Exception {
        final Path root = tempDir.resolve("javan-mod-project");
        final Path output = root.resolve(".javan");
        final Path main = root.resolve("libs/main.jar");
        final Path test = root.resolve("libs/test.jar");
        final Path tool = root.resolve("tools/codegen.jar");
        Files.createDirectories(main.getParent());
        Files.createDirectories(tool.getParent());
        Files.writeString(main, "main");
        Files.writeString(test, "test");
        Files.writeString(tool, "tool");
        Files.writeString(root.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main libs/main.jar
            require test libs/test.jar
            require tool tools/codegen.jar
            """);

        final ProjectLayout resolved = new ClasspathResolver(new ProcessRunner()).resolveDeclaredDependencies(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            output,
            "demo",
            List.of()
        ));

        assertThat(resolved.classpathEntries()).containsExactly(main.toAbsolutePath().normalize());
        assertThat(Files.readString(root.resolve("javan.lock"))).contains(
            "\"scope\": \"main\"",
            "\"scope\": \"test\"",
            "\"scope\": \"tool\""
        );
    }

    @Test
    void resolveDeclaredDependenciesAddsResolvedCoordinateMainScope() throws Exception {
        final Path root = tempDir.resolve("coordinate-javan-mod-project");
        final Path repository = tempDir.resolve("repo");
        final Path jar = repository.resolve("com/acme/math/1.2.3/math-1.2.3.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");
        Files.createDirectories(root);
        Files.writeString(root.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main com.acme:math:1.2.3
            """);

        final ProjectLayout resolved = resolverWithRepository(repository).resolveDeclaredDependencies(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            root.resolve(".javan"),
            "demo",
            List.of()
        ));

        assertThat(resolved.classpathEntries()).containsExactly(jar.toAbsolutePath().normalize());
        assertThat(Files.readString(root.resolve("javan.lock"))).contains(
            "\"kind\": \"coordinate\"",
            "\"status\": \"present\"",
            "\"relativePath\": " + javan.util.Json.string(jar.toAbsolutePath().normalize().toString())
        );
    }

    @Test
    void resolveDeclaredDependenciesReturnsLayoutWhenModuleIsAbsent() throws Exception {
        final Path root = tempDir.resolve("no-javan-mod-project");
        Files.createDirectories(root);
        final ProjectLayout layout = new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            root.resolve(".javan"),
            "demo",
            List.of()
        );

        final ProjectLayout resolved = new ClasspathResolver(new ProcessRunner()).resolveDeclaredDependencies(layout);

        assertThat(resolved).isSameAs(layout);
        assertThat(root.resolve("javan.lock")).doesNotExist();
    }

    @Test
    void resolveDeclaredDependenciesRejectsInvalidModuleWarnings() throws Exception {
        final Path root = tempDir.resolve("invalid-javan-mod-project");
        Files.createDirectories(root);
        Files.writeString(root.resolve("javan.mod"), """
            module com.acme app
            java
            """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClasspathResolver(new ProcessRunner()).resolveDeclaredDependencies(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            root.resolve(".javan"),
            "demo",
            List.of()
        )))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Invalid javan.mod")
            .hasMessageContaining("module expects exactly one name")
            .hasMessageContaining("java expects exactly one feature version");
    }

    @Test
    void resolveDeclaredDependenciesRejectsMissingLocalDependency() throws Exception {
        final Path root = tempDir.resolve("missing-javan-mod-project");
        Files.createDirectories(root);
        Files.writeString(root.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main libs/missing.jar
            """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ClasspathResolver(new ProcessRunner()).resolveDeclaredDependencies(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            root.resolve(".javan"),
            "demo",
            List.of()
        )))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Missing javan.mod dependency")
            .hasMessageContaining("libs/missing.jar");
    }

    @Test
    void resolveDeclaredDependenciesRejectsMissingCoordinateDependency() throws Exception {
        final Path root = tempDir.resolve("coordinate-javan-mod-project");
        final Path repository = tempDir.resolve("repo");
        Files.createDirectories(root);
        Files.writeString(root.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require org.nanonative:nano:2026.1
            """);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resolverWithRepository(repository).resolveDeclaredDependencies(new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            root.resolve(".javan"),
            "demo",
            List.of()
        )))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Missing javan.mod dependency")
            .hasMessageContaining("org.nanonative:nano:2026.1");
        assertThat(Files.readString(root.resolve("javan.lock"))).contains(
            "\"status\": \"missing-coordinate\"",
            "\"artifactKind\": \"missing-jar\""
        );
    }

    private static ClasspathResolver resolverWithRepository(final Path repository) {
        return new ClasspathResolver(
            new ProcessRunner(),
            new JavanModuleParser(),
            new JavanLockWriter(),
            new JavanCoordinateResolver(List.of(repository))
        );
    }
}
