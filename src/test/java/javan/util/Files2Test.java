package javan.util;

import javan.testing.TestSuite.PlatformTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class Files2Test {
    @TempDir
    private Path tempDir;

    @Test
    @PlatformTest
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void failedSourceScanClosesParentDirectoryStreams() throws Exception {
        assertFailedTraversalReleasesHandles("scan");
    }

    @Test
    @PlatformTest
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void failedTimestampScanClosesParentDirectoryStreams() throws Exception {
        assertFailedTraversalReleasesHandles("time");
    }

    @Test
    @PlatformTest
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void failedRecursiveDeleteClosesParentDirectoryStreams() throws Exception {
        assertFailedTraversalReleasesHandles("delete");
    }

    private void assertFailedTraversalReleasesHandles(final String mode) throws Exception {
        final Path root = Files.createDirectory(tempDir.resolve(mode));
        final Path blocked = Files.createDirectory(root.resolve("blocked"));
        final var permissions = Files.getPosixFilePermissions(blocked);
        try {
            Files.setPosixFilePermissions(blocked, Set.of());
            assumeTrue(!Files.isReadable(blocked), "Host bypasses directory permissions");
            final String classpath = Path.of("target/classes").toAbsolutePath() + java.io.File.pathSeparator
                + Path.of("target/test-classes").toAbsolutePath();
            final ProcessRunner.Result result = new ProcessRunner(20_000).run(tempDir, List.of(
                ProcessHandle.current().info().command().orElseThrow(), "-Xms64m", "-Xmx64m", "-cp", classpath,
                DirectoryProbe.class.getName(), root.toString(), mode
            ));
            assertThat(result.exitCode()).as(result.stderr()).isZero();
            assertThat(result.stderr()).isEmpty();
            assertThat(result.stdout()).isEqualToNormalizingNewlines("failures:32\nretained-handles:0\n");
            assertThat(root).isDirectory();
        } finally {
            Files.setPosixFilePermissions(blocked, permissions);
        }
    }

    public static final class DirectoryProbe {
        public static void main(final String[] args) throws Exception {
            final Path root = Path.of(args[0]);
            final var os = (com.sun.management.UnixOperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            for (int attempt = 0; attempt < 5; attempt++) {
                expectAccessFailure(root, args[1]);
            }
            final long before = os.getOpenFileDescriptorCount();
            for (int attempt = 0; attempt < 32; attempt++) {
                expectAccessFailure(root, args[1]);
            }
            final long retained = os.getOpenFileDescriptorCount() - before;
            System.out.println("failures:32");
            System.out.println("retained-handles:" + retained);
        }

        private static void expectAccessFailure(final Path root, final String mode) throws IOException {
            try {
                switch (mode) {
                    case "scan" -> Files2.findJavaSources(root);
                    case "time" -> Files2.newestModified(List.of(root), ".java");
                    case "delete" -> Files2.deleteRecursive(root);
                    default -> throw new AssertionError("Unknown operation: " + mode);
                }
                throw new AssertionError("Expected inaccessible child directory");
            } catch (final java.nio.file.AccessDeniedException expected) {
                if (!root.resolve("blocked").toString().equals(expected.getFile())) {
                    throw new AssertionError("Unexpected access failure", expected);
                }
            }
        }
    }

    @Test
    void writeStringCreatesParentDirectories() throws Exception {
        final Path file = tempDir.resolve("generated/reports/out.txt");

        final Path written = Files2.writeString(file, "ok");

        assertThat(written).isEqualTo(file);
        assertThat(Files.readString(file)).isEqualTo("ok");
    }

    @Test
    void createDirectoriesIfPossibleCreatesAMissingDirectoryTree() {
        final Path directory = tempDir.resolve("managed/jdks/temurin");

        assertThat(Files2.createDirectoriesIfPossible(directory)).isTrue();
        assertThat(directory).isDirectory();
    }

    @Test
    void createDirectoriesIfPossibleReturnsFalseWhenAFileBlocksTheTree() throws Exception {
        final Path file = Files.createFile(tempDir.resolve("blocked"));

        assertThat(Files2.createDirectoriesIfPossible(file.resolve("child"))).isFalse();
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

    @Test
    void deleteRecursiveDeletesSymlinkWithoutFollowingItsTarget() throws Exception {
        final Path target = tempDir.resolve("outside");
        final Path retained = Files2.writeString(target.resolve("retained.txt"), "retained");
        final Path tree = Files.createDirectories(tempDir.resolve("tree"));
        final Path link = tree.resolve("linked");
        try {
            Files.createSymbolicLink(link, target);
        } catch (final UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }

        Files2.deleteRecursive(tree);

        assertThat(tree).doesNotExist();
        assertThat(retained).hasContent("retained");
    }
}
