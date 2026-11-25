package javan;

import javan.testing.TestSuite.NativeTest;
import javan.util.Sha256;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliResourceRuntimeIntegrationTest extends CliIntegrationSupport {
    @Test
    void classResourceStreamSurvivesConcurrentCollection() throws Exception {
        checkResourceStreamDuringCollection("class-resource-gc", "Main.class.getResourceAsStream(\"/data.txt\")");
    }

    @Test
    void systemResourceStreamSurvivesConcurrentCollection() throws Exception {
        checkResourceStreamDuringCollection("system-resource-gc", "ClassLoader.getSystemResourceAsStream(\"data.txt\")");
    }

    @Test
    void classLoaderResourceStreamSurvivesConcurrentCollection() throws Exception {
        checkResourceStreamDuringCollection("loader-resource-gc", "ClassLoader.getSystemClassLoader().getResourceAsStream(\"data.txt\")");
    }

    @Test
    void resourceReadAllBytesSurvivesConcurrentCollection() throws Exception {
        checkResourceStreamDuringCollection("resource-read-all-gc", "Main.class.getResourceAsStream(\"/data.txt\")",
            "byte[] bytes = stream.readAllBytes(); if (bytes.length != 1 || bytes[0] != 65) failures.incrementAndGet();");
    }

    private void checkResourceStreamDuringCollection(final String name, final String lookup) throws Exception {
        checkResourceStreamDuringCollection(name, lookup, "if (stream.read() != 65) failures.incrementAndGet();");
    }

    private void checkResourceStreamDuringCollection(final String name, final String lookup, final String read) throws Exception {
        final Path project = project(name);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.InputStream;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger failures = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 2000; i++) {
                            String value = new String("example");
                            if (value.length() != 7) failures.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start();
                    for (int i = 0; i < 1000; i++) {
                        InputStream stream = %s;
                        %s
                        stream.close();
                    }
                    first.join(); second.join();
                    System.out.println(failures.get());
                }
            }
            """.formatted(lookup, read));
        writeResource(project, "data.txt", "A");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/" + name).toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualTo("0\n");
    }

    @Test
    void nativeBuildReadsResourceFromDependencyJar() throws Exception {
        final Path dependency = addJarResource(
            dependencyJar("resource-library", "dep.Library", """
                package dep;

                public final class Library {
                    private Library() {
                    }
                }
                """),
            "dep/message.txt",
            "dependency\n"
        );
        final Path project = project("resource-dependency");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InputStream stream = ClassLoader.getSystemResourceAsStream("dep/message.txt");
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    stream.close();
                }
            }
            """);
        Files.writeString(project.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main %s
            """.formatted(pathForMod(project, dependency)), StandardCharsets.UTF_8);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/resource-dependency").toString())).stdout())
            .isEqualTo(jvmOutput)
            .isEqualTo("11\n100\n");
        final Path bundled = project.resolve(".javan/dist/resources/dep/message.txt");
        assertThat(bundled).hasContent("dependency\n");
        assertThat(Files.readString(project.resolve(".javan/reports/resources.json")))
            .contains("\"path\": \"dep/message.txt\"", "\"sha256\": \"" + Sha256.of(bundled) + "\"");
    }

    @Test
    void urlResourceLookupFailsDuringCheckWithStreamAlternative() throws Exception {
        final Path project = project("resource-url-rejected");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Main.class.getResource("/message.txt"));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "java/lang/Class.getResource(Ljava/lang/String;)Ljava/net/URL;",
            "not supported",
            "getResourceAsStream"
        );
        assertThat(project.resolve(".javan/generated/runtime.c")).doesNotExist();
    }

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
    void systemClassLoaderReadsNestedResourceNamedAtRuntime() throws Exception {
        final Path project = project("resource-runtime-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.InputStream;

            public final class Main {
                public static void main(final String[] args) throws Exception {
                    final InputStream stream = ClassLoader.getSystemResourceAsStream(args[0]);
                    if (stream == null) {
                        System.out.println("missing");
                        return;
                    }
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    stream.close();
                }
            }
            """);
        writeResource(project, "assets/nested/message.txt", "OK!");

        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String executable = project.resolve(".javan/bin/resource-runtime-name").toString();
        final ProcessResult found = process(project, List.of(executable, "assets/nested/message.txt"));
        assertThat(found.exitCode()).as(found.stderr()).isZero();
        assertThat(found.stdout()).isEqualTo("3\n79\n");
        final ProcessResult missing = process(project, List.of(executable, "assets/nested/missing.txt"));
        assertThat(missing.exitCode()).as(missing.stderr()).isZero();
        assertThat(missing.stdout()).isEqualTo("missing\n");
    }

    @Test
    void systemClassLoaderNullResourceNameThrowsCatchableNullPointer() throws Exception {
        final Path project = project("resource-system-loader-null-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    try {
                        ClassLoader.getSystemResourceAsStream(null);
                        System.out.println("unexpected");
                    } catch (NullPointerException expected) {
                        System.out.println("null name");
                    }
                }
            }
            """);

        final String expected = runJvmWithResources(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/resource-system-loader-null-name").toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected).isEqualTo("null name\n");
    }

    @Test
    void instanceResourceLookupRejectsNullNamesAndReceivers() throws Exception {
        final Path project = project("resource-instance-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    final ClassLoader loader = ClassLoader.getSystemClassLoader();
                    final Class<?> type = Main.class;
                    final ClassLoader missingLoader = null;
                    final Class<?> missingClass = null;
                    try { loader.getResourceAsStream(null); }
                    catch (NullPointerException expected) { System.out.println("loader name"); }
                    try { type.getResourceAsStream(null); }
                    catch (NullPointerException expected) { System.out.println("class name"); }
                    try { missingLoader.getResourceAsStream("asset.txt"); }
                    catch (NullPointerException expected) { System.out.println("loader receiver"); }
                    try { missingClass.getResourceAsStream("asset.txt"); }
                    catch (NullPointerException expected) { System.out.println("class receiver"); }
                }
            }
            """);

        final String expected = runJvmWithResources(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/resource-instance-null").toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected)
            .isEqualTo("loader name\nclass name\nloader receiver\nclass receiver\n");
    }

    @Test
    void resourceLookupEvaluatesReceiverAndNameBeforeNullCheck() throws Exception {
        final Path project = project("resource-null-evaluation-order");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int trace;
                static ClassLoader loader() { trace = trace * 10 + 1; return null; }
                static String name() { trace = trace * 10 + 2; return "asset.txt"; }
                public static void main(final String[] args) {
                    try { loader().getResourceAsStream(name()); }
                    catch (NullPointerException expected) { System.out.println(trace); }
                }
            }
            """);

        final String expected = runJvmWithResources(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/resource-null-evaluation-order").toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected).isEqualTo("12\n");
    }

    @Test
    void uncaughtNullResourceNameFailsAtRuntime() throws Exception {
        final Path project = project("resource-system-loader-null-uncaught");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    ClassLoader.getSystemResourceAsStream(null);
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/resource-system-loader-null-uncaught").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("java/lang/NullPointerException");
    }

    @Test
    void classResourceLookupResolvesRelativeNameAtRuntime() throws Exception {
        final Path project = project("resource-class-runtime-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.InputStream;
            public final class Main {
                public static void main(final String[] args) throws Exception {
                    final InputStream stream = Main.class.getResourceAsStream(args[0]);
                    if (stream == null) { System.out.println("missing"); return; }
                    final byte[] bytes = stream.readAllBytes();
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    stream.close();
                }
            }
            """);
        writeResource(project, "com/acme/nested/message.txt", "OK!");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String executable = project.resolve(".javan/bin/resource-class-runtime-name").toString();
        final ProcessResult found = process(project, List.of(executable, "nested/message.txt"));
        assertThat(found.exitCode()).as(found.stderr()).isZero();
        assertThat(found.stdout()).isEqualTo("3\n79\n");
        final ProcessResult missing = process(project, List.of(executable, "nested/missing.txt"));
        assertThat(missing.exitCode()).as(missing.stderr()).isZero();
        assertThat(missing.stdout()).isEqualTo("missing\n");
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
