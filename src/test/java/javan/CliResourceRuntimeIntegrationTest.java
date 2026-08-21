package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
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
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(loader != null);
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
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-loader-instance").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n3\n104\n101\n121\n");
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

}
