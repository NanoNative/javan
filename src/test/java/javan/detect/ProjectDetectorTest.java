package javan.detect;

import javan.cli.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class ProjectDetectorTest {
    @TempDir
    private Path tempDir;

    @Test
    void detectJarInputUsesJarKindAndClasspathSelfEntry() throws Exception {
        final Path jar = tempDir.resolve("demo-tool.jar");
        Files.write(jar, new byte[]{0});

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", jar.toString()}));

        assertThat(layout.inputKind()).isEqualTo(InputKind.JAR_FILE);
        assertThat(layout.buildTool()).isEqualTo(BuildTool.JAR);
        assertThat(layout.root()).isEqualTo(tempDir.toAbsolutePath().normalize());
        assertThat(layout.classpathEntries()).containsExactly(jar.toAbsolutePath().normalize());
        assertThat(layout.outputDirectory()).isEqualTo(tempDir.resolve(".javan").toAbsolutePath().normalize());
        assertThat(layout.outputName()).isEqualTo("demo-tool");
        assertThat(layout.sourceFolders()).isEmpty();
        assertThat(layout.resourceFolders()).isEmpty();
    }

    @Test
    void detectSourceFileUsesParentAsRootSourceAndResourceFolder() throws Exception {
        final Path source = tempDir.resolve("src/solo/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            class Main {
            }
            """);

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", source.toString()}));

        assertThat(layout.inputKind()).isEqualTo(InputKind.SOURCE_FILE);
        assertThat(layout.buildTool()).isEqualTo(BuildTool.JAVAC);
        assertThat(layout.root()).isEqualTo(source.getParent().toAbsolutePath().normalize());
        assertThat(layout.sourceFolders()).containsExactly(source.getParent().toAbsolutePath().normalize());
        assertThat(layout.resourceFolders()).containsExactly(source.getParent().toAbsolutePath().normalize());
        assertThat(layout.classFolders()).isEmpty();
        assertThat(layout.outputName()).isEqualTo("main");
    }

    @Test
    void detectProjectDirectoryWithPomUsesMavenArtifactIdAsOutputName() throws Exception {
        final Path project = tempDir.resolve("maven-project");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <artifactId> sharp-tool </artifactId>
            </project>
            """);
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), """
            package com.acme;
            public final class Main {
            }
            """);

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.inputKind()).isEqualTo(InputKind.PROJECT_DIRECTORY);
        assertThat(layout.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(layout.outputName()).isEqualTo("sharp-tool");
        assertThat(layout.sourceFolders()).containsExactly(project.resolve("src/main/java").toAbsolutePath().normalize());
    }

    @Test
    void detectProjectDirectoryWithoutArtifactIdFallsBackToRootName() throws Exception {
        final Path project = tempDir.resolve("fallback-name");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(layout.outputName()).isEqualTo("fallback-name");
    }

    @Test
    void detectGradleProjectUsesSettingsRootProjectNameWithWhitespace() throws Exception {
        final Path project = tempDir.resolve("gradle-project");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.createDirectories(project.resolve("resources"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name \t = \n 'Blade Tool'\n");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");
        Files.writeString(project.resolve("resources/messages.properties"), "ok=yes\n");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.buildTool()).isEqualTo(BuildTool.GRADLE);
        assertThat(layout.outputName()).isEqualTo("blade-tool");
        assertThat(layout.resourceFolders()).containsExactly(project.resolve("resources").toAbsolutePath().normalize());
    }

    @Test
    void detectGradleKtsProjectUsesQuotedRootProjectName() throws Exception {
        final Path project = tempDir.resolve("gradle-kts-project");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("settings.gradle.kts"), "rootProject.name = \"Arrow Tool\"\n");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.buildTool()).isEqualTo(BuildTool.GRADLE);
        assertThat(layout.outputName()).isEqualTo("arrow-tool");
    }

    @Test
    void detectGradleProjectWithoutQuotedNameFallsBackToRootName() throws Exception {
        final Path project = tempDir.resolve("plain-gradle");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = otherName\n");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.buildTool()).isEqualTo(BuildTool.GRADLE);
        assertThat(layout.outputName()).isEqualTo("plain-gradle");
    }

    @Test
    void detectMavenWrapperCommandMarksBuildToolAsMaven() throws Exception {
        final Path project = tempDir.resolve("maven-wrapper");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("mvnw.cmd"), "echo mvn\n");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.buildTool()).isEqualTo(BuildTool.MAVEN);
    }

    @Test
    void detectGradleWrapperBatchMarksBuildToolAsGradle() throws Exception {
        final Path project = tempDir.resolve("gradle-wrapper");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("gradlew.bat"), "@echo off\n");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.buildTool()).isEqualTo(BuildTool.GRADLE);
    }

    @Test
    void detectClassesDirectoryWithProjectMarkersStaysProjectDirectory() throws Exception {
        final Path project = tempDir.resolve("generated-project");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.createDirectories(project.resolve("classes/com/acme"));
        Files.write(project.resolve("classes/com/acme/Main.class"), new byte[]{0});
        Files.writeString(project.resolve("pom.xml"), "<project><artifactId>kept-project</artifactId></project>");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.inputKind()).isEqualTo(InputKind.PROJECT_DIRECTORY);
        assertThat(layout.buildTool()).isEqualTo(BuildTool.MAVEN);
        assertThat(layout.classFolders()).containsExactly(project.resolve("classes").toAbsolutePath().normalize());
    }

    @Test
    void detectPlainClassesDirectoryUsesParentJavanOutputDirectory() throws Exception {
        final Path classes = tempDir.resolve("classes/com/acme");
        Files.createDirectories(classes);
        Files.write(classes.resolve("Main.class"), new byte[]{0});

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", tempDir.resolve("classes").toString()}));

        assertThat(layout.inputKind()).isEqualTo(InputKind.CLASSES_DIRECTORY);
        assertThat(layout.outputDirectory()).isEqualTo(tempDir.resolve(".javan").toAbsolutePath().normalize());
    }

    @Test
    void detectExplicitOutputNameOverridesDetectedName() throws Exception {
        final Path project = tempDir.resolve("custom-name");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("pom.xml"), "<project><artifactId>ignored</artifactId></project>");
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(
            tempDir,
            Options.parse(new String[]{"inspect", project.toString(), "--output", "My Binary"})
        );

        assertThat(layout.outputName()).isEqualTo("my-binary");
    }

    @Test
    void detectExplicitClasspathResolvesRelativeEntriesAgainstCwd() throws Exception {
        final Path project = tempDir.resolve("classpath-project");
        final Path relativeJar = tempDir.resolve("libs/dependency.jar");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.createDirectories(relativeJar.getParent());
        Files.write(relativeJar, new byte[]{0});
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");

        final ProjectLayout layout = new ProjectDetector().detect(
            tempDir,
            Options.parse(new String[]{"inspect", project.toString(), "--classpath", "libs/dependency.jar"})
        );

        assertThat(layout.classpathEntries()).containsExactly(relativeJar.toAbsolutePath().normalize());
    }

    @Test
    void detectProjectWithRootJavaSourcesAvoidsDuplicateSourceFolders() throws Exception {
        final Path project = tempDir.resolve("root-sources");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");
        Files.writeString(project.resolve("Helper.java"), "final class Helper {}");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.sourceFolders()).containsExactly(project.resolve("src/main/java").toAbsolutePath().normalize());
    }

    @Test
    void detectProjectWithResourceFoldersAvoidsOverlappingEntries() throws Exception {
        final Path project = tempDir.resolve("resources-overlap");
        Files.createDirectories(project.resolve("src/main/java/com/acme"));
        Files.createDirectories(project.resolve("src/main/resources"));
        Files.createDirectories(project.resolve("resources"));
        Files.writeString(project.resolve("src/main/java/com/acme/Main.java"), "package com.acme; public final class Main {}");
        Files.writeString(project.resolve("src/main/resources/messages.properties"), "ok=yes\n");
        Files.writeString(project.resolve("resources/extra.txt"), "extra\n");

        final ProjectLayout layout = new ProjectDetector().detect(tempDir, Options.parse(new String[]{"inspect", project.toString()}));

        assertThat(layout.resourceFolders()).containsExactly(
            project.resolve("src/main/resources").toAbsolutePath().normalize(),
            project.resolve("resources").toAbsolutePath().normalize()
        );
    }
}
