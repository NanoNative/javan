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

    @Test
    void ignoresClassOutputsInsideGitDirectory() throws Exception {
        writeClassFile(tempDir.resolve(".git/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void ignoresClassOutputsInsideIdeaDirectory() throws Exception {
        writeClassFile(tempDir.resolve(".idea/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void ignoresClassOutputsInsideGradleMetadataDirectory() throws Exception {
        writeClassFile(tempDir.resolve(".gradle/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void ignoresClassOutputsInsideMavenMetadataDirectory() throws Exception {
        writeClassFile(tempDir.resolve(".mvn/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void ignoresClassOutputsInsideNodeModulesDirectory() throws Exception {
        writeClassFile(tempDir.resolve("node_modules/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void ignoresEmptySupportedClassesDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("classes"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void ignoresSupportedDirectoryWithoutClassFiles() throws Exception {
        final Path classes = tempDir.resolve("classes/com/acme");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("Main.txt"), "not a class");

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).isEmpty();
    }

    @Test
    void returnsClassFolderOnceWhenItContainsMultipleClassFiles() throws Exception {
        writeClassFile(tempDir.resolve("classes/com/acme/Main.class"));
        writeClassFile(tempDir.resolve("classes/com/acme/Support.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve("classes").toAbsolutePath().normalize());
    }

    @Test
    void discoversJavanClassesOutput() throws Exception {
        writeClassFile(tempDir.resolve(".javan/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve(".javan/classes").toAbsolutePath().normalize());
    }

    @Test
    void discoversOutProductionClassesOutput() throws Exception {
        writeClassFile(tempDir.resolve("out/production/classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(
            tempDir.resolve("out/production/classes").toAbsolutePath().normalize()
        );
    }

    @Test
    void discoversBinOutput() throws Exception {
        writeClassFile(tempDir.resolve("bin/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve("bin").toAbsolutePath().normalize());
    }

    @Test
    void discoversPlainClassesOutput() throws Exception {
        writeClassFile(tempDir.resolve("classes/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve("classes").toAbsolutePath().normalize());
    }

    @Test
    void discoversGradleKotlinMainClassOutput() throws Exception {
        writeClassFile(tempDir.resolve("service/build/classes/kotlin/main/com/acme/Main.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(
            tempDir.resolve("service/build/classes/kotlin/main").toAbsolutePath().normalize()
        );
    }

    @Test
    void discoversOnlyOuterSupportedDirectoryOnceForNestedClassLayouts() throws Exception {
        writeClassFile(tempDir.resolve("module/classes/com/acme/Main.class"));
        writeClassFile(tempDir.resolve("module/classes/nested/More.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(tempDir.resolve("module/classes").toAbsolutePath().normalize());
    }

    @Test
    void returnsDiscoveredOutputsInDeterministicAsciiOrder() throws Exception {
        writeClassFile(tempDir.resolve("zeta/bin/com/acme/Zeta.class"));
        writeClassFile(tempDir.resolve("alpha/classes/com/acme/Alpha.class"));
        writeClassFile(tempDir.resolve("beta/out/production/classes/com/acme/Beta.class"));

        final List<Path> discovered = ClassOutputDiscovery.discover(tempDir);

        assertThat(discovered).containsExactly(
            tempDir.resolve("alpha/classes").toAbsolutePath().normalize(),
            tempDir.resolve("beta/out/production/classes").toAbsolutePath().normalize(),
            tempDir.resolve("zeta/bin").toAbsolutePath().normalize()
        );
    }

    private static void writeClassFile(final Path classFile) throws Exception {
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[]{0});
    }
}
