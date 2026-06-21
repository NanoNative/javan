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
}
