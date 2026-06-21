package javan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class Files2Test {
    @TempDir
    private Path tempDir;

    @Test
    void writeStringCreatesParentDirectories() throws Exception {
        final Path file = tempDir.resolve("generated/reports/out.txt");

        final Path written = Files2.writeString(file, "ok");

        assertThat(written).isEqualTo(file);
        assertThat(Files.readString(file)).isEqualTo("ok");
    }

    @Test
    void findClassFilesReturnsEmptyForMissingRoot() throws Exception {
        assertThat(Files2.findClassFiles(tempDir.resolve("missing"))).isEmpty();
    }

    @Test
    void findJavaSourcesSkipsTopLevelBuildDirectory() throws Exception {
        final Path visible = tempDir.resolve("src/main/java/com/acme/Main.java");
        final Path ignored = tempDir.resolve("target/generated/Hidden.java");
        Files2.writeString(visible, "package com.acme; public final class Main {}\n");
        Files2.writeString(ignored, "final class Hidden {}\n");

        assertThat(Files2.findJavaSources(tempDir)).containsExactly(visible);
    }

    @Test
    void findResourceFilesExcludesJavaAndClassFiles() throws Exception {
        final Path resource = tempDir.resolve("src/main/resources/app.properties");
        Files2.writeString(resource, "name=app\n");
        Files2.writeString(tempDir.resolve("src/main/resources/Ignored.java"), "final class Ignored {}\n");
        Files2.writeString(tempDir.resolve("src/main/resources/Ignored.class"), "bytecode");

        assertThat(Files2.findResourceFiles(tempDir.resolve("src/main/resources"))).containsExactly(resource);
    }

    @Test
    void sourcesNewerThanClassesReturnsFalseWhenNoSourcesExist() throws Exception {
        final Path classes = tempDir.resolve("classes/com/acme/Main.class");
        Files2.writeString(classes, "bytecode");

        assertThat(Files2.sourcesNewerThanClasses(List.of(tempDir.resolve("src")), List.of(tempDir.resolve("classes"))))
            .isFalse();
    }

    @Test
    void sourcesNewerThanClassesReturnsTrueWhenClassesAreMissing() throws Exception {
        final Path source = tempDir.resolve("src/main/java/com/acme/Main.java");
        Files2.writeString(source, "package com.acme; public final class Main {}\n");

        assertThat(Files2.sourcesNewerThanClasses(List.of(tempDir.resolve("src")), List.of(tempDir.resolve("classes"))))
            .isTrue();
    }

    @Test
    void sourcesNewerThanClassesReturnsTrueWhenSourceIsNewer() throws Exception {
        final Path source = tempDir.resolve("src/main/java/com/acme/Main.java");
        final Path compiled = tempDir.resolve("classes/com/acme/Main.class");
        Files2.writeString(source, "package com.acme; public final class Main {}\n");
        Files2.writeString(compiled, "bytecode");
        Files.setLastModifiedTime(compiled, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(source, FileTime.fromMillis(2_000));

        assertThat(Files2.sourcesNewerThanClasses(List.of(tempDir.resolve("src")), List.of(tempDir.resolve("classes"))))
            .isTrue();
    }

    @Test
    void sourcesNewerThanClassesReturnsFalseWhenClassIsCurrent() throws Exception {
        final Path source = tempDir.resolve("src/main/java/com/acme/Main.java");
        final Path compiled = tempDir.resolve("classes/com/acme/Main.class");
        Files2.writeString(source, "package com.acme; public final class Main {}\n");
        Files2.writeString(compiled, "bytecode");
        Files.setLastModifiedTime(source, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(compiled, FileTime.fromMillis(2_000));

        assertThat(Files2.sourcesNewerThanClasses(List.of(tempDir.resolve("src")), List.of(tempDir.resolve("classes"))))
            .isFalse();
    }

    @Test
    void newestModifiedIgnoresNonMatchingFiles() throws Exception {
        final Path readme = tempDir.resolve("README.md");
        Files2.writeString(readme, "text");
        Files.setLastModifiedTime(readme, FileTime.fromMillis(2_000));

        assertThat(Files2.newestModified(List.of(tempDir), ".class")).isZero();
    }

    @Test
    void deleteRecursiveDeletesNestedTree() throws Exception {
        final Path file = tempDir.resolve("a/b/c.txt");
        Files2.writeString(file, "gone");

        Files2.deleteRecursive(tempDir.resolve("a"));

        assertThat(tempDir.resolve("a")).doesNotExist();
    }
}
