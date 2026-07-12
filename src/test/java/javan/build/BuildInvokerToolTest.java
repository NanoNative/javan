package javan.build;

import javan.cli.Command;
import javan.cli.Options;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import javan.dependency.JavanCoordinateResolver;
import javan.dependency.JavanLockWriter;
import javan.dependency.JavanModuleParser;
import javan.profile.Profile;
import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class BuildInvokerToolTest {
    @TempDir
    private Path tempDir;

    @Test
    void ensureClassesBuildsMavenProjectThroughWrapper() throws Exception {
        final Path root = tempDir.resolve("maven-project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("target/classes");
        final Path dep = root.resolve("repo/runtime.jar");
        Files.createDirectories(root);
        Files.createDirectories(dep.getParent());
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(dep, "jar");
        Files.writeString(root.resolve("mvnw"), """
            #!/bin/sh
            case "$*" in
              *" -DskipTests compile"*)
                mkdir -p "$PWD/target/classes"
                printf x > "$PWD/target/classes/App.class"
                exit 0
                ;;
              *build-classpath*)
                out=""
                for arg in "$@"; do
                  case "$arg" in
                    -Dmdep.outputFile=*) out=${arg#-Dmdep.outputFile=} ;;
                  esac
                done
                printf '%s' "$PWD/repo/runtime.jar" > "$out"
                exit 0
                ;;
            esac
            exit 11
            """);
        root.resolve("mvnw").toFile().setExecutable(true);

        final ProjectLayout updated = new BuildInvoker().ensureClasses(layout(root, output, BuildTool.MAVEN), options(root));

        assertThat(updated.classFolders()).contains(classes.toAbsolutePath().normalize());
        assertThat(updated.classpathEntries())
            .extracting(path -> path.getFileName().toString())
            .containsExactly("runtime.jar");
    }

    @Test
    void ensureClassesRejectsFailingMavenBuild() throws Exception {
        final Path root = tempDir.resolve("maven-project");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(root.resolve("mvnw"), "#!/bin/sh\necho boom >&2\nexit 4\n");
        root.resolve("mvnw").toFile().setExecutable(true);

        assertThatThrownBy(() -> new BuildInvoker().ensureClasses(layout(root, root.resolve(".javan"), BuildTool.MAVEN), options(root)))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Maven compile failed")
            .hasMessageContaining("boom");
    }

    @Test
    void ensureClassesRejectsFailingGradleBuild() throws Exception {
        final Path root = tempDir.resolve("gradle-project");
        Files.createDirectories(root);
        Files.writeString(root.resolve("gradlew"), "#!/bin/sh\necho fail >&2\nexit 5\n");
        root.resolve("gradlew").toFile().setExecutable(true);

        assertThatThrownBy(() -> new BuildInvoker().ensureClasses(layout(root, root.resolve(".javan"), BuildTool.GRADLE), options(root)))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Gradle classes failed")
            .hasMessageContaining("fail");
    }

    @Test
    void ensureClassesBuildsMavenProjectThroughSystemMavenWhenWrapperMissing() throws Exception {
        final Path root = tempDir.resolve("maven-system-project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("target/classes");
        final Path dep = root.resolve("repo/runtime.jar");
        Files.createDirectories(root);
        Files.createDirectories(dep.getParent());
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        Files.writeString(dep, "jar");

        final ScriptedProcessRunner runner = new ScriptedProcessRunner(root);
        final BuildInvoker invoker = new BuildInvoker(
            runner,
            new ClasspathResolver(runner, new JavanModuleParser(), new JavanLockWriter(), new JavanCoordinateResolver(List.of()))
        );

        final ProjectLayout updated = invoker.ensureClasses(layout(root, output, BuildTool.MAVEN), options(root));

        assertThat(updated.classFolders()).contains(classes.toAbsolutePath().normalize());
        assertThat(updated.classpathEntries()).contains(dep.toAbsolutePath().normalize());
        assertThat(runner.commands()).extracting(command -> command.getFirst()).containsExactly("mvn", "mvn");
    }

    @Test
    void ensureClassesBuildsGradleProjectThroughSystemGradleWhenWrapperMissing() throws Exception {
        final Path root = tempDir.resolve("gradle-system-project");
        final Path output = root.resolve(".javan");
        final Path classes = root.resolve("build/classes/java/main");
        final Path dep = root.resolve("repo/runtime.jar");
        Files.createDirectories(root);
        Files.createDirectories(dep.getParent());
        Files.writeString(root.resolve("build.gradle"), "plugins { id 'java' }\n");
        Files.writeString(dep, "jar");

        final ScriptedProcessRunner runner = new ScriptedProcessRunner(root);
        final BuildInvoker invoker = new BuildInvoker(
            runner,
            new ClasspathResolver(runner, new JavanModuleParser(), new JavanLockWriter(), new JavanCoordinateResolver(List.of()))
        );

        final ProjectLayout updated = invoker.ensureClasses(layout(root, output, BuildTool.GRADLE), options(root));

        assertThat(updated.classFolders()).contains(classes.toAbsolutePath().normalize());
        assertThat(updated.classpathEntries()).contains(dep.toAbsolutePath().normalize());
        assertThat(runner.commands()).extracting(command -> command.getFirst()).containsExactly("gradle", "gradle");
    }

    @Test
    void ensureClassesResolvesProjectDirectoryMarkedAsJarBuildTool() throws Exception {
        final Path root = tempDir.resolve("jar-layout-project");
        final Path output = root.resolve(".javan");
        final Path jar = root.resolve("repo/app.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");

        final ProjectLayout updated = new BuildInvoker().ensureClasses(
            new ProjectLayout(
                root,
                root,
                InputKind.PROJECT_DIRECTORY,
                BuildTool.JAR,
                List.of(),
                List.of(),
                List.of(),
                List.of(jar),
                output,
                "demo",
                List.of()
            ),
            options(root)
        );

        assertThat(updated.classpathEntries()).contains(jar.toAbsolutePath().normalize());
    }

    private static ProjectLayout layout(final Path root, final Path output, final BuildTool buildTool) {
        return new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            buildTool,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            output,
            "demo",
            List.of()
        );
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

    private static final class ScriptedProcessRunner extends ProcessRunner {
        private final Path root;
        private final List<List<String>> commands = new ArrayList<>();

        private ScriptedProcessRunner(final Path root) {
            this.root = root;
        }

        @Override
        public Result run(final Path workingDirectory, final List<String> command) throws IOException {
            commands.add(List.copyOf(command));
            if ("mvn".equals(command.getFirst()) && command.contains("compile")) {
                Files.createDirectories(root.resolve("target/classes"));
                Files.writeString(root.resolve("target/classes/App.class"), "x");
                return new Result(0, "", "");
            }
            if ("mvn".equals(command.getFirst()) && command.contains("dependency:build-classpath")) {
                final Path outputFile = outputFile(command, "-Dmdep.outputFile=");
                Files.createDirectories(outputFile.getParent());
                Files.writeString(outputFile, root.resolve("repo/runtime.jar").toString());
                return new Result(0, "", "");
            }
            if ("gradle".equals(command.getFirst()) && command.contains("classes")) {
                Files.createDirectories(root.resolve("build/classes/java/main"));
                Files.writeString(root.resolve("build/classes/java/main/App.class"), "x");
                return new Result(0, "", "");
            }
            if ("gradle".equals(command.getFirst()) && command.contains("javanRuntimeClasspath")) {
                return new Result(0, root.resolve("repo/runtime.jar").toString(), "");
            }
            throw new IOException("Unexpected command: " + command);
        }

        private List<List<String>> commands() {
            return List.copyOf(commands);
        }

        private static Path outputFile(final List<String> command, final String prefix) {
            for (final String argument : command) {
                if (argument.startsWith(prefix)) {
                    return Path.of(argument.substring(prefix.length()));
                }
            }
            throw new IllegalStateException("Missing output file argument in command: " + command);
        }
    }
}
