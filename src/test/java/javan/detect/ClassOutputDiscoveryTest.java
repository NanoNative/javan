package javan.detect;

import javan.cli.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class ClassOutputDiscoveryTest {
    @TempDir
    private Path tempDir;

    @Test
    void discoversMavenModuleClassOutput() throws Exception {
        final Path classes = tempDir.resolve("service/target/classes/com/acme");
        Files.createDirectories(classes);
        Files.write(classes.resolve("Main.class"), new byte[]{0});

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve("service/target/classes").toAbsolutePath().normalize());
    }

    @Test
    void discoversGradleSubprojectClassOutput() throws Exception {
        final Path classes = tempDir.resolve("service/build/classes/java/main/com/acme");
        Files.createDirectories(classes);
        Files.write(classes.resolve("Main.class"), new byte[]{0});

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve("service/build/classes/java/main").toAbsolutePath().normalize());
    }

    @Test
    void projectDetectorResolvesRelativeClassesAgainstCwd() throws Exception {
        final Path project = tempDir.resolve("project");
        final Path classes = tempDir.resolve("classes/com/acme");
        Files.createDirectories(project);
        Files.createDirectories(classes);
        Files.write(classes.resolve("Main.class"), new byte[]{0});

        final ProjectLayout layout = new ProjectDetector().detect(
            tempDir,
            Options.parse(new String[]{"inspect", project.toString(), "--classes", "classes"})
        );

        assertThat(layout.classFolders()).containsExactly(tempDir.resolve("classes").toAbsolutePath().normalize());
    }
}
