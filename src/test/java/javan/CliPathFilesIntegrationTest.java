package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliPathFilesIntegrationTest extends CliIntegrationSupport {
    @Test
    void pathResolveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("path-resolve");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Path.of("data").resolve("message.txt").toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-resolve").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void pathsGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("paths-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;
            import java.nio.file.Paths;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Path path = Paths.get("data", "message.txt");
                    System.out.println(path.toString());
                    System.out.println(path.getFileName().toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/paths-get").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void pathOperationsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("path-operations");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Path path = Path.of("data", "message.txt");
                    System.out.println(path.getFileName().toString());
                    System.out.println(path.getParent().toString());
                    System.out.println(path.getNameCount());
                    System.out.println(path.getName(0).toString());
                    System.out.println(path.startsWith(Path.of("data")));
                    System.out.println(Path.of("data").relativize(path).toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-operations").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void pathNormalizeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("path-normalize");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Path.of("data", "..", "message.txt").normalize().toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-normalize").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("message.txt\n");
    }

    @Test
    void pathToAbsolutePathBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("path-to-absolute");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Path.of("data").toAbsolutePath().toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-to-absolute").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Path.of(jvmOutput.strip())).isAbsolute();
    }

    @Test
    void pathEqualsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("path-equals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Path.of("data").equals(Path.of("data")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-equals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void filesReadStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-read-string");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/message.txt"), "file-ok", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.readString(Path.of("data").resolve("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-read-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesWriteStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-write-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Files.writeString(Path.of("message.txt"), "written");
                    System.out.println(Files.readString(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.deleteIfExists(project.resolve("message.txt"));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-write-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesReadAllBytesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-read-all-bytes");
        Files.createDirectories(project.resolve("data"));
        Files.write(project.resolve("data/message.bin"), new byte[]{7, 8, 9});
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final byte[] bytes = Files.readAllBytes(Path.of("data").resolve("message.bin"));
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-read-all-bytes").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesWriteBytesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-write-bytes");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Files.write(Path.of("message.bin"), new byte[] {65, 66});
                    final byte[] bytes = Files.readAllBytes(Path.of("message.bin"));
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.deleteIfExists(project.resolve("message.bin"));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-write-bytes").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n65\n66\n");
    }

    @Test
    void filesNewDirectoryStreamBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-directory-stream");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/a.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(project.resolve("data/b.txt"), "b", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.DirectoryStream;
            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("data"));
                    int count = 0;
                    int sawA = 0;
                    int sawB = 0;
                    for (final Path child : stream) {
                        final String name = child.getFileName().toString();
                        count++;
                        if ("a.txt".equals(name)) {
                            sawA = 1;
                        }
                        if ("b.txt".equals(name)) {
                            sawB = 1;
                        }
                    }
                    stream.close();
                    System.out.println(count);
                    System.out.println(sawA);
                    System.out.println(sawB);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-directory-stream").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesCreateDirectoriesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-create-directories");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Path target = Path.of(".javan").resolve("created").resolve("child");
                    Files.createDirectories(target);
                    System.out.println(Files.isDirectory(target));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.deleteIfExists(project.resolve(".javan/created/child"));
        Files.deleteIfExists(project.resolve(".javan/created"));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-create-directories").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesExistsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-exists");
        Files.writeString(project.resolve("message.txt"), "exists", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Files.exists(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-exists").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void filesIsRegularFileBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-is-regular-file");
        Files.writeString(project.resolve("message.txt"), "regular", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Files.isRegularFile(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-is-regular-file").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void filesDeleteIfExistsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-delete-if-exists");
        Files.writeString(project.resolve("message.txt"), "delete", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.deleteIfExists(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.writeString(project.resolve("message.txt"), "delete", StandardCharsets.UTF_8);
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-delete-if-exists").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void filesIsDirectoryNoFollowLinksBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-is-directory-no-follow");
        Files.createDirectories(project.resolve("target-dir"));
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.LinkOption;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Files.isDirectory(Path.of("target-dir"), LinkOption.NOFOLLOW_LINKS));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-is-directory-no-follow").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesIsExecutableBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-is-executable");
        Files.writeString(project.resolve("plain.txt"), "plain", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Files.isExecutable(Path.of("plain.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-is-executable").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesSizeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-size");
        Files.writeString(project.resolve("message.txt"), "javan-size", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.size(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-size").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesLastModifiedTimeToMillisBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-last-modified-time");
        Files.writeString(project.resolve("message.txt"), "javan-mtime", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.getLastModifiedTime(Path.of("message.txt")).toMillis() >= 0);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-last-modified-time").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesCopyWithReplaceExistingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-copy");
        Files.writeString(project.resolve("source.txt"), "copy-ok", StandardCharsets.UTF_8);
        Files.writeString(project.resolve("target.txt"), "old", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.StandardCopyOption;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Files.copy(Path.of("source.txt"), Path.of("target.txt"), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(Files.readString(Path.of("target.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.writeString(project.resolve("target.txt"), "old", StandardCharsets.UTF_8);
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-copy").toString())).stdout()).isEqualTo(jvmOutput);
    }
}
