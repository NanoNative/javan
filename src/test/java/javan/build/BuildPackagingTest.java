package javan.build;

import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

final class BuildPackagingTest {
    @TempDir
    private Path tempDir;

    @Test
    void resourceBundlerCollectsDeduplicatedSortedResourcesAndWritesReports() throws Exception {
        final Path classesA = tempDir.resolve("classes-a");
        final Path classesB = tempDir.resolve("classes-b");
        Files.createDirectories(classesA.resolve("nested"));
        Files.createDirectories(classesB.resolve("nested"));
        Files.writeString(classesA.resolve("nested/config.properties"), "a");
        Files.writeString(classesB.resolve("nested/config.properties"), "b");
        Files.writeString(classesB.resolve("banner.txt"), "banner");

        final ProjectLayout layout = layout(tempDir, tempDir.resolve(".javan"), classesA, classesB);

        final List<ResourceBundler.ResourceFile> resources = new ResourceBundler().bundle(layout);

        assertThat(resources).extracting(ResourceBundler.ResourceFile::path)
            .containsExactly("banner.txt", "nested/config.properties");
        assertThat(Files.readString(layout.outputDirectory().resolve("resources/nested/config.properties"))).isEqualTo("a");
        assertThat(Files.readString(layout.outputDirectory().resolve("dist/resources/banner.txt"))).isEqualTo("banner");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/resources.json"))).contains("\"resourceCount\": 2");
        assertThat(Files.readString(layout.outputDirectory().resolve("reports/resources.md"))).contains("| `banner.txt` |");
    }

    @Test
    void jarPackagerCopiesInputJarVerbatim() throws Exception {
        final Path sourceJar = tempDir.resolve("input.jar");
        Files.write(sourceJar, new byte[]{1, 2, 3, 4});
        final Path output = tempDir.resolve("out.jar");

        final Path jar = new JarPackager().packageJar(
            new ProjectLayout(
                tempDir,
                sourceJar,
                InputKind.JAR_FILE,
                BuildTool.JAR,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                tempDir.resolve(".javan"),
                "demo",
                List.of()
            ),
            output,
            Optional.of("com.acme.Main")
        );

        assertThat(jar).isEqualTo(output);
        assertThat(Files.readAllBytes(output)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void jarPackagerWritesManifestAndSkipsDuplicateManifestFromClasses() throws Exception {
        final Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes.resolve("META-INF"));
        Files.createDirectories(classes.resolve("com/acme"));
        Files.writeString(classes.resolve("META-INF/MANIFEST.MF"), "bad");
        Files.write(classes.resolve("com/acme/Main.class"), new byte[]{0, 1, 2});
        Files.writeString(classes.resolve("message.txt"), "hello");
        final Path output = tempDir.resolve("demo.jar");

        new JarPackager().packageJar(layout(tempDir, tempDir.resolve(".javan"), classes), output, Optional.of("com.acme.Main"));

        try (JarFile jar = new JarFile(output.toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class")).isEqualTo("com.acme.Main");
            assertThat(jar.getJarEntry("com/acme/Main.class")).isNotNull();
            assertThat(jar.getJarEntry("message.txt")).isNotNull();
            assertThat(jar.stream().filter(entry -> entry.getName().equals("META-INF/MANIFEST.MF")).count()).isEqualTo(1);
        }
    }

    @Test
    void jarPackagerIgnoresTopLevelBuildDirectoriesInsideClassFolder() throws Exception {
        final Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes.resolve("build/internal"));
        Files.createDirectories(classes.resolve("nested/build"));
        Files.writeString(classes.resolve("build/internal/ignored.txt"), "ignore");
        Files.writeString(classes.resolve("nested/build/kept.txt"), "keep");
        final Path output = tempDir.resolve("demo.jar");

        new JarPackager().packageJar(layout(tempDir, tempDir.resolve(".javan"), classes), output, Optional.empty());

        try (JarFile jar = new JarFile(output.toFile())) {
            assertThat(jar.getJarEntry("build/internal/ignored.txt")).isNull();
            assertThat(jar.getJarEntry("nested/build/kept.txt")).isNotNull();
        }
    }

    @Test
    void jarPackagerSkipsMissingClassFoldersAndDuplicateEntriesAcrossRoots() throws Exception {
        final Path classesA = tempDir.resolve("classes-a");
        final Path classesB = tempDir.resolve("classes-b");
        final Path missing = tempDir.resolve("missing");
        Files.createDirectories(classesA.resolve("com/acme"));
        Files.createDirectories(classesB.resolve("com/acme"));
        Files.write(classesA.resolve("com/acme/Main.class"), new byte[]{1});
        Files.write(classesB.resolve("com/acme/Main.class"), new byte[]{2});
        Files.writeString(classesB.resolve("extra.txt"), "extra");
        final Path output = tempDir.resolve("demo.jar");

        new JarPackager().packageJar(layout(tempDir, tempDir.resolve(".javan"), missing, classesA, classesB), output, Optional.empty());

        try (JarFile jar = new JarFile(output.toFile())) {
            assertThat(jar.getJarEntry("com/acme/Main.class")).isNotNull();
            assertThat(jar.stream().filter(entry -> entry.getName().equals("com/acme/Main.class")).count()).isEqualTo(1);
            assertThat(jar.getJarEntry("extra.txt")).isNotNull();
        }
    }

    @Test
    void jarPackagerKeepsUtf8FileNamesAcrossEncodingBranches() throws Exception {
        final Path classes = tempDir.resolve("classes");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("café.txt"), "latin");
        Files.writeString(classes.resolve("漢.txt"), "cjk");
        Files.writeString(classes.resolve("rocket🚀.txt"), "emoji");
        final Path output = tempDir.resolve("utf8.jar");

        new JarPackager().packageJar(layout(tempDir, tempDir.resolve(".javan"), classes), output, Optional.empty());

        try (JarFile jar = new JarFile(output.toFile())) {
            assertThat(jar.getJarEntry("café.txt")).isNotNull();
            assertThat(jar.getJarEntry("漢.txt")).isNotNull();
            assertThat(jar.getJarEntry("rocket🚀.txt")).isNotNull();
        }
    }

    private static ProjectLayout layout(final Path root, final Path outputDirectory, final Path... classFolders) {
        return new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.CLASSES,
            List.of(),
            List.of(),
            List.of(classFolders),
            List.of(),
            outputDirectory,
            "demo",
            List.of()
        );
    }
}
