package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliResourceRuntimeIntegrationTest extends CliIntegrationSupport {
    @Test
    void nativeBuildReadsResourceRelativeToClass() throws Exception {
        final Path project = project("resource-relative");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InputStream stream = Main.class.getResourceAsStream("message.txt");
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                    stream.close();
                }
            }
            """);
        writeResource(project, "com/acme/message.txt", "OK");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-relative").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n79\n75\n");
    }

    @Test
    void nativeBuildReadsResourceFromAbsoluteClasspathPath() throws Exception {
        final Path project = project("resource-absolute");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InputStream stream = Main.class.getResourceAsStream("/assets/banner.txt");
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                    System.out.println(bytes[2]);
                    stream.close();
                }
            }
            """);
        writeResource(project, "assets/banner.txt", "hey");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-absolute").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n104\n101\n121\n");
    }

    @Test
    void missingResourceReturnsNullBeforeUse() throws Exception {
        final Path project = project("resource-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var stream = Main.class.getResourceAsStream("missing.txt");
                    System.out.println(stream == null ? "missing" : "present");
                }
            }
            """);

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-missing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("missing\n");
    }

    @Test
    void nativeBuildReadsResourceFromSystemClassLoader() throws Exception {
        final Path project = project("resource-system-loader");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InputStream stream = ClassLoader.getSystemResourceAsStream("assets/banner.txt");
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                    System.out.println(bytes[2]);
                    stream.close();
                }
            }
            """);
        writeResource(project, "assets/banner.txt", "hey");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-system-loader").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n104\n101\n121\n");
    }

    @Test
    void systemClassLoaderLeadingSlashReturnsNull() throws Exception {
        final Path project = project("resource-system-loader-leading-slash");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var stream = ClassLoader.getSystemResourceAsStream("/assets/banner.txt");
                    System.out.println(stream == null ? "missing" : "present");
                }
            }
            """);
        writeResource(project, "assets/banner.txt", "hey");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-system-loader-leading-slash").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("missing\n");
    }

    @Test
    void nativeBuildReadsResourceFromSystemClassLoaderInstance() throws Exception {
        final Path project = project("resource-loader-instance");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ClassLoader loader = ClassLoader.getSystemClassLoader();
                    final InputStream stream = loader.getResourceAsStream("assets/banner.txt");
                    System.out.println(stream.available());
                    System.out.println(stream.skip(1));
                    System.out.println(stream.available());
                    System.out.println(stream.markSupported());
                    stream.mark(8);
                    final byte[] prefix = stream.readNBytes(1);
                    stream.reset();
                    final byte[] resetPrefix = stream.readNBytes(1);
                    final byte[] target = new byte[4];
                    final int count = stream.readNBytes(target, 1, 3);
                    System.out.println(loader != null);
                    System.out.println(prefix.length);
                    System.out.println(prefix[0]);
                    System.out.println(resetPrefix[0]);
                    System.out.println(count);
                    System.out.println(target[1]);
                    System.out.println(stream.readNBytes(1).length);
                    stream.close();
                }
            }
            """);
        writeResource(project, "assets/banner.txt", "hey");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-loader-instance").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n1\n2\ntrue\ntrue\n1\n101\n101\n1\n121\n0\n");
    }

    @Test
    void systemClassLoaderInstanceLeadingSlashReturnsNull() throws Exception {
        final Path project = project("resource-loader-instance-leading-slash");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ClassLoader loader = ClassLoader.getSystemClassLoader();
                    final var stream = loader.getResourceAsStream("/assets/banner.txt");
                    System.out.println(stream == null ? "missing" : "present");
                }
            }
            """);
        writeResource(project, "assets/banner.txt", "hey");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-loader-instance-leading-slash").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("missing\n");
    }

    @Test
    void systemClassLoaderBehavesLikeClassLoaderInstance() throws Exception {
        final Path project = project("resource-loader-instance-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ClassLoader loader = ClassLoader.getSystemClassLoader();
                    System.out.println(ClassLoader.class.isInstance(loader));
                    System.out.println(loader.getClass().getName());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final String nativeOutput = process(project, List.of(project.resolve(".javan/bin/resource-loader-instance-class").toString())).stdout();

        assertThat(nativeOutput).startsWith("true\n");
        assertThat(nativeOutput).contains("ClassLoader");
    }

    private String runJvmWithResources(final Path project, final String mainClass) throws Exception {
        final Path output = project.resolve("jvm-classes");
        final Path sourceRoot = project.resolve("src/main/java");
        final List<String> compile = new ArrayList<>(List.of(CliTestHarness.currentJavacCommand(), "-d", output.toString()));
        Files.createDirectories(output);
        try (var sources = Files.walk(sourceRoot)) {
            sources
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".java"))
                .map(Path::toString)
                .forEach(compile::add);
        }
        assertThat(process(project, compile).exitCode()).isZero();
        final Path resources = project.resolve("src/main/resources");
        if (Files.isDirectory(resources)) {
            try (var files = Files.walk(resources)) {
                for (final Path file : files.toList()) {
                    final Path relative = resources.relativize(file);
                    final Path target = output.resolve(relative.toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        final ProcessResult run = process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            output.toString(),
            mainClass
        ));
        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        return run.stdout();
    }
}
