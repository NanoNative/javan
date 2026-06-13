package javan;

import javan.cli.Cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

final class CliIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void helpPrintsUsage() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("javan 0.1.0");
        assertThat(run.stdout()).contains(
            "javan --version",
            "javan inspect",
            "javan check",
            "javan test",
            "javan build",
            "--profile <profile>",
            "core, service, library, or strict"
        );
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void versionPrintsProjectVersion() {
        final CliRun run = run(tempDir, "--version");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).isEqualTo("javan 0.1.0\n");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void helpListsToolchainListCommand() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).contains("javan toolchain list");
    }

    @Test
    void helpMentionsJavacWrapper() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).contains("javan javac [javac args...]");
    }

    @Test
    void javacVersionDelegatesToJavac() {
        final ProcessResult javac = process(tempDir, List.of("javac", "-version"));

        final CliRun run = run(tempDir, "javac", "-version");

        assertThat(run.exitCode()).isEqualTo(javac.exitCode());
        assertThat(run.stdout()).isEqualTo(javac.stdout());
        assertThat(run.stderr()).isEqualTo(javac.stderr());
    }

    @Test
    void javacReleaseCompilesSourceIntoCurrentDirectory() throws Exception {
        final Path source = tempDir.resolve("JavacWrapperSmoke.java");
        Files.writeString(source, """
            public final class JavacWrapperSmoke {
                private JavacWrapperSmoke() {
                }
            }
            """);

        final CliRun run = run(tempDir, "javac", "--release", "25", source.getFileName().toString());

        assertThat(run.exitCode()).isZero();
        assertThat(tempDir.resolve("JavacWrapperSmoke.class")).exists();
    }

    @Test
    void inspectWritesProjectReport() throws Exception {
        final Path project = project("inspect");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("inspect");
                }
            }
            """);

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: JAVAC");
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"buildTool\": \"JAVAC\"");
    }

    @Test
    void checkCompilesPlainJavaProject() throws Exception {
        final Path project = project("check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private static long sink;

                private Main() {
                }

                public static void main(final String[] args) {
                    Objects.requireNonNull(args);
                    sink = System.nanoTime();
                    sink = System.currentTimeMillis();
                    final int distance = Math.abs(args.length - 4);
                    final int value = Math.max(distance, Math.min(args.length, 10));
                    System.out.println(value);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--profile", "service");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("profile:           service", "reachable methods: 1");
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"profile\": \"service\"");
        assertThat(Files.exists(project.resolve(".javan/classes/com/acme/Main.class"))).isTrue();
        assertThat(project.resolve(".javan/reports/optimizations.json")).exists();
        assertThat(project.resolve(".javan/reports/optimizations.md")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.json")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 1}",
                "{\"name\": \"Math.abs\", \"count\": 1}",
                "{\"name\": \"Math.min\", \"count\": 1}",
                "{\"name\": \"Math.max\", \"count\": 1}",
                "{\"name\": \"System.nanoTime\", \"count\": 1}",
                "{\"name\": \"System.currentTimeMillis\", \"count\": 1}",
                "\"unsupportedJdkCallCandidateCount\":"
            );
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.md")))
            .contains("| `Objects.requireNonNull` | 1 |", "| `System.nanoTime` | 1 |", "| `System.currentTimeMillis` | 1 |");
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json")))
            .contains(
                "\"redundantNullChecks\": 0",
                "\"redundantBoundsChecks\": 0",
                "\"redundantTypeChecks\": 0",
                "\"redundantRangeChecks\": 0",
                "\"deadBranches\": 0",
                "\"specializedMethods\": 0",
                "\"skippedCandidates\": 0"
            );
    }

    @Test
    void unknownProfileFailsAtCliBoundary() {
        final CliRun run = run(tempDir, "check", "--profile", "enterprise");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("error[JAVAN900]: Unsupported profile: enterprise");
    }

    @Test
    void testDelegatesToMavenWrapperAfterBuildingClasses() throws Exception {
        final Path project = project("maven-test");
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>maven-test</artifactId>
              <version>1.0.0</version>
            </project>
            """);
        writeExecutableScript(project.resolve("mvnw"), """
            #!/bin/sh
            printf '%s\\n' "$*" >> invocations.txt
            if [ "$1" = "test" ]; then
              echo maven-test-ok
            fi
            exit 0
            """);

        final CliRun run = run(tempDir, "test", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Running tests:", "./mvnw test", "maven-test-ok");
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve("invocations.txt"))).contains(
            "-q -DskipTests compile",
            "dependency:build-classpath",
            "test"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"buildTool\": \"MAVEN\"");
    }

    @Test
    void testDelegatesToGradleWrapperAfterBuildingClasses() throws Exception {
        final Path project = project("gradle-test");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        writeExecutableScript(project.resolve("gradlew"), """
            #!/bin/sh
            printf '%s\\n' "$*" >> invocations.txt
            if [ "$1" = "test" ]; then
              echo gradle-test-ok
            fi
            exit 0
            """);

        final CliRun run = run(tempDir, "test", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Running tests:", "./gradlew test", "gradle-test-ok");
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve("invocations.txt"))).contains(
            "classes",
            "javanRuntimeClasspath",
            "test"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"buildTool\": \"GRADLE\"");
    }

    @Test
    void testFailsClearlyForPlainJavaProject() throws Exception {
        final Path project = project("plain-test");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("plain");
                }
            }
            """);

        final CliRun run = run(tempDir, "test", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN900]", "No configured test runner for JAVAC projects");
        assertThat(Files.exists(project.resolve(".javan/classes/com/acme/Main.class"))).isTrue();
    }

    @Test
    void buildCreatesNativeExecutable() throws Exception {
        final Path project = project("hello");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("Hello from javan");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final Path binary = project.resolve(".javan/bin/hello");
        assertThat(binary).exists();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("Hello from javan\n");
    }

    @Test
    void staticLibraryExportedIntMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-add");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Math.add",
            "--bindings",
            "c,rust,go,python"
        );

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-add.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-add.h");
        assertThat(library).exists();
        assertThat(header).exists();
        assertThat(project.resolve(".javan/dist/bindings/rust/lib.rs")).exists();
        assertThat(project.resolve(".javan/dist/bindings/go/library_add.go")).exists();
        assertThat(project.resolve(".javan/dist/bindings/python/library_add.py")).exists();
        assertThat(project.resolve(".javan/reports/library-build.json")).exists();
        assertThat(project.resolve(".javan/reports/deduplication-plan.json")).exists();
        assertThat(project.resolve(".javan/reports/optimizations.json")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.json")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 0}",
                "\"unsupportedJdkCallCandidateCount\": 0"
            );
        final Path caller = writeC(project, "call_add.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-add.h"

            int main(void) {
                printf("%d\\n", javan_export_com_acme_Math_add_int_int(2, 5));
                return 0;
            }
            """);
        final Path binary = project.resolve("call-add");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("7\n");
        if (commandAvailable("rustc")) {
            final Path rust = project.resolve(".javan/dist/bindings/rust/lib.rs");
            final Path rustOut = project.resolve("bindings.rlib");
            assertThat(process(project, List.of("rustc", "--crate-type", "lib", rust.toString(), "-o", rustOut.toString())).exitCode()).isZero();
        }
        if (commandAvailable("go")) {
            final Path goDir = project.resolve(".javan/dist/bindings/go");
            assertThat(process(project, List.of("sh", "-c", "cd '" + goDir + "' && GO111MODULE=off CGO_ENABLED=1 go test")).exitCode()).isZero();
        }
    }

    @Test
    void staticLibraryStringInputAndOutputOwnsReturnedString() throws Exception {
        final Path project = project("library-string");
        writeJava(project, "com.acme.Text", """
            package com.acme;

            public final class Text {
                private Text() {
                }

                public static String greet(final String name) {
                    return "Hi " + name;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Text.greet");

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-string.a");
        final Path caller = writeC(project, "call_string.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-string.h"

            int main(void) {
                char* value = javan_export_com_acme_Text_greet_string("Yuna");
                puts(value);
                javan_free(value);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-string");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("Hi Yuna\n");
    }

    @Test
    void staticLibraryByteArrayInputAndOutputUsesPointerLengthAbi() throws Exception {
        final Path project = project("library-bytes");
        writeJava(project, "com.acme.Bytes", """
            package com.acme;

            public final class Bytes {
                private Bytes() {
                }

                public static byte[] echo(final byte[] data) {
                    return data;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Bytes.echo");

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-bytes.a");
        final Path caller = writeC(project, "call_bytes.c", """
            #include <stdint.h>
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-bytes.h"

            int main(void) {
                int8_t data[3] = {1, 2, 3};
                JavanByteArray input = {data, 3};
                JavanByteArray output = javan_export_com_acme_Bytes_echo_bytes(input);
                printf("%d %d\\n", output.length, output.data[1]);
                javan_free(output.data);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-bytes");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("3 2\n");
    }

    @Test
    void libraryExportsCanComeFromJavanToml() throws Exception {
        final Path project = project("library-config");
        Files.writeString(project.resolve("javan.toml"), """
            [exports]
            methods = ["com.acme.Math.add(int,int):int"]
            """);
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/liblibrary-config.a")).exists();
    }

    @Test
    void sharedLibraryBuildCreatesPlatformLibrary() throws Exception {
        final Path project = project("library-shared");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "sharedlib", "--export", "com.acme.Math.add");

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/" + sharedLibraryName("library-shared"));
        assertThat(library).exists();
        if (commandAvailable("python3")) {
            final String script = """
                import ctypes
                lib = ctypes.CDLL(r'%s')
                add = lib.javan_export_com_acme_Math_add_int_int
                add.argtypes = [ctypes.c_int, ctypes.c_int]
                add.restype = ctypes.c_int
                print(add(3, 4))
                """.formatted(library);
            assertThat(process(project, List.of("python3", "-c", script)).stdout()).isEqualTo("7\n");
        }
    }

    @Test
    void unsupportedExportSignatureFailsClearly() throws Exception {
        final Path project = project("library-bad-export");
        writeJava(project, "com.acme.Bad", """
            package com.acme;

            public final class Bad {
                private Bad() {
                }

                public static Object nope(final Object value) {
                    return value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Bad.nope");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("Unsupported export object type");
    }

    @Test
    void runExecutesNativeExecutable() throws Exception {
        final Path project = project("run");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("run-native");
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("run-native");
    }

    @Test
    void staticHelperCallBuilds() throws Exception {
        final Path project = project("helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.print();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void print() {
                    System.out.println("helper-output");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/helper").toString())).stdout()).isEqualTo("helper-output\n");
    }

    @Test
    void staticFieldsAndClassInitializerBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("static-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(State.count);
                    System.out.println(State.total);
                    System.out.println(State.label);
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public final class State {
                static int count;
                static long total;
                static String label;

                static {
                    count = 41;
                    count = count + 1;
                    total = 80L + 4L;
                    label = "ready";
                }

                private State() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/static-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n84\nready\n");
    }

    @Test
    void staticIntMethodBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(add(7, 5));
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-int").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("12\n");
    }

    @Test
    void intLocalsArithmeticAndLargeConstantsBuild() throws Exception {
        final Path project = project("int-locals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int value = calculate(40000, 9);
                    System.out.println(value);
                }

                public static int calculate(final int left, final int right) {
                    final int sum = left + right;
                    final int product = sum * 2;
                    return product - 3;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-locals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("80015\n");
    }

    @Test
    void staticVoidMethodWithIntArgumentBuilds() throws Exception {
        final Path project = project("int-void-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    print(42);
                }

                public static void print(final int value) {
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-void-helper").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void ifElseIntReturnBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("if-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(max(10, 7));
                    System.out.println(max(2, 9));
                }

                public static int max(final int left, final int right) {
                    if (left > right) {
                        return left;
                    }
                    return right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-return").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n9\n");
    }

    @Test
    void ifElsePrintBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("if-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    printSign(-3);
                    printSign(0);
                    printSign(5);
                }

                public static void printSign(final int value) {
                    if (value < 0) {
                        System.out.println(-1);
                    } else if (value == 0) {
                        System.out.println(0);
                    } else {
                        System.out.println(1);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-print").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-1\n0\n1\n");
    }

    @Test
    void ifWithAllIntComparisonOperatorsBuilds() throws Exception {
        final Path project = project("if-comparisons");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(3, 3));
                    System.out.println(score(4, 3));
                    System.out.println(score(2, 3));
                }

                public static int score(final int left, final int right) {
                    if (left == right) {
                        return 10;
                    }
                    if (left != right) {
                        if (left >= right) {
                            return 20;
                        }
                        if (left <= right) {
                            return 30;
                        }
                    }
                    return 40;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-comparisons").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n20\n30\n");
    }

    @Test
    void whileLoopPrintsAndMatchesJvmOutput() throws Exception {
        final Path project = project("while-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int index = 0;
                    while (index < 3) {
                        System.out.println(index);
                        index++;
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-print").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n1\n2\n");
    }

    @Test
    void whileLoopAccumulatorMatchesJvmOutput() throws Exception {
        final Path project = project("while-sum");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(sum(5));
                    System.out.println(sum(0));
                }

                public static int sum(final int limit) {
                    int total = 0;
                    int index = 1;
                    while (index <= limit) {
                        total = total + index;
                        index++;
                    }
                    return total;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-sum").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("15\n0\n");
    }

    @Test
    void whileLoopDecrementMatchesJvmOutput() throws Exception {
        final Path project = project("while-decrement");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = 3;
                    while (value > 0) {
                        System.out.println(value);
                        value--;
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-decrement").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n2\n1\n");
    }

    @Test
    void objectConstructorFieldsAndInstanceMethodsMatchJvmOutput() throws Exception {
        final Path project = project("object-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Point point = new Point(10, 5);
                    System.out.println(point.sum());
                    System.out.println(PointOps.weighted(point, 3));
                }
            }
            """);
        writeJava(project, "com.acme.Point", """
            package com.acme;

            public final class Point {
                private final int x;
                private final int y;

                public Point(final int x, final int y) {
                    this.x = x;
                    this.y = y;
                }

                public int sum() {
                    return x + y;
                }

                public int score(final int factor) {
                    return sum() * factor;
                }
            }
            """);
        writeJava(project, "com.acme.PointOps", """
            package com.acme;

            public final class PointOps {
                private PointOps() {
                }

                public static int weighted(final Point point, final int factor) {
                    return point.score(factor);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("15\n45\n");
    }

    @Test
    void objectStringFieldReturnAndNullBranchMatchJvmOutput() throws Exception {
        final Path project = project("object-string-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("ready");
                    System.out.println(message.text());
                    System.out.println(label(null));
                    System.out.println(label(message));
                }

                public static String label(final Message message) {
                    if (message == null) {
                        return "missing";
                    }
                    return message.text();
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public final class Message {
                private final String text;

                public Message(final String text) {
                    this.text = text;
                }

                public String text() {
                    return text;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-string-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ready\nmissing\nready\n");
    }

    @Test
    void nonFinalClassWithoutKnownSubclassInstanceCallBuilds() throws Exception {
        final Path project = project("non-final-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("exact");
                    System.out.println(message.text());
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public class Message {
                private final String text;

                public Message(final String text) {
                    this.text = text;
                }

                public String text() {
                    return text;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/non-final-exact").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("exact\n");
    }

    @Test
    void simpleRecordConstructorAndAccessorBuilds() throws Exception {
        final Path project = project("simple-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("record");
                    System.out.println(message.text());
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public record Message(String text) {
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/simple-record").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("record\n");
    }

    @Test
    void objectArrayInitializerLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("object-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[]{"zero", "one"};
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\none\n");
    }

    @Test
    void intArrayInitializerLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("int-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[]{2, 3};
                    values[1] = 9;
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n9\n");
    }

    @Test
    void intArrayStaticReturnAndParameterBuilds() throws Exception {
        final Path project = project("int-array-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = values();
                    System.out.println(second(values));
                }

                public static int[] values() {
                    return new int[]{4, 8};
                }

                public static int second(final int[] values) {
                    return values[1];
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array-helper").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("8\n");
    }

    @Test
    void booleanFieldReturnBranchAndPrintBuilds() throws Exception {
        final Path project = project("boolean-basic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Flag flag = new Flag(true);
                    System.out.println(flag.value());
                    System.out.println(invert(flag.value()));
                }

                public static boolean invert(final boolean value) {
                    if (value) {
                        return false;
                    }
                    return true;
                }
            }
            """);
        writeJava(project, "com.acme.Flag", """
            package com.acme;

            public final class Flag {
                private boolean value;

                public Flag(final boolean value) {
                    this.value = value;
                }

                public boolean value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-basic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void floatArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("float-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final float[] values = new float[]{1.25f, 2.5f};
                    values[1] = 3.75f;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1.25\n3.75\n");
    }

    @Test
    void booleanArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("boolean-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] values = new boolean[]{false, true};
                    values[0] = true;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\ntrue\n");
    }

    @Test
    void byteShortAndCharArraysBuild() throws Exception {
        final Path project = project("small-primitive-arrays");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final byte[] bytes = new byte[]{-2, 3};
                    bytes[1] = -5;
                    final short[] shorts = new short[]{300, -7};
                    final char[] chars = new char[]{'A', 'B'};
                    chars[1] = 'C';
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                    System.out.println(shorts[0]);
                    System.out.println(shorts[1]);
                    System.out.println(chars[0] + 1);
                    System.out.println(chars[1] + 0);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/small-primitive-arrays").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-2\n-5\n300\n-7\n66\n67\n");
    }

    @Test
    void longArithmeticReturnAndPrintBuilds() throws Exception {
        final Path project = project("long-arithmetic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(calculate(40L, 2L));
                }

                public static long calculate(final long left, final long right) {
                    final long sum = left + right;
                    return (sum * 2L) - 4L;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-arithmetic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("80\n");
    }

    @Test
    void longFieldsConstructorAndGetterBuild() throws Exception {
        final Path project = project("long-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Counter counter = new Counter(7L);
                    System.out.println(counter.value());
                }
            }
            """);
        writeJava(project, "com.acme.Counter", """
            package com.acme;

            public final class Counter {
                private long value;

                public Counter(final long value) {
                    this.value = value;
                }

                public long value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-field").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void longParameterSlotWidthBuilds() throws Exception {
        final Path project = project("long-slots");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(pick(3L, "ignored", 4L));
                }

                public static long pick(final long first, final String ignored, final long second) {
                    return first + second;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-slots").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void floatAndDoubleArithmeticReturnAndPrintBuilds() throws Exception {
        final Path project = project("float-double-arithmetic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(scale(1.25f, 2.5f));
                    System.out.println(measure(4.0, 0.25));
                }

                public static float scale(final float left, final float right) {
                    return (left + right) * 2.0f;
                }

                public static double measure(final double left, final double right) {
                    return (left / 2.0) + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-arithmetic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7.5\n2.25\n");
    }

    @Test
    void floatAndDoubleFieldsConstructorAndGetterBuild() throws Exception {
        final Path project = project("float-double-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Reading reading = new Reading(1.25f, 2.5);
                    System.out.println(reading.ratio());
                    System.out.println(reading.total());
                }
            }
            """);
        writeJava(project, "com.acme.Reading", """
            package com.acme;

            public final class Reading {
                private float ratio;
                private double total;

                public Reading(final float ratio, final double total) {
                    this.ratio = ratio;
                    this.total = total;
                }

                public float ratio() {
                    return ratio;
                }

                public double total() {
                    return total;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-field").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void floatAndDoubleComparisonsBuild() throws Exception {
        final Path project = project("float-double-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(2.5f, 1.25f));
                    System.out.println(rank(1.0, 2.0));
                }

                public static int score(final float left, final float right) {
                    if (left > right) {
                        return 1;
                    }
                    return 0;
                }

                public static int rank(final double left, final double right) {
                    if (left < right) {
                        return -1;
                    }
                    return 0;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-compare").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n-1\n");
    }

    @Test
    void staticFloatAndDoubleFieldsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("static-float-double-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(State.ratio);
                    System.out.println(State.total);
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public final class State {
                static float ratio;
                static double total;

                static {
                    ratio = 1.25f;
                    total = 2.5;
                }

                private State() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/static-float-double-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void floatAndDoubleIndexedLocalsAndUnaryBuild() throws Exception {
        final Path project = project("float-double-indexed-locals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    printInts(args.length);
                    printLongs(0L);
                    printFloats(0.25f);
                    printDoubles(0.25);
                }

                public static void printInts(final int seed) {
                    int i0 = seed;
                    int i1 = i0 + 1;
                    int i2 = i1 + 1;
                    int i3 = i2 + 1;
                    int i4 = i3 + 1;
                    System.out.println(i4);
                }

                public static void printLongs(final long seed) {
                    long l0 = seed;
                    long l1 = l0 + 1L;
                    long l2 = l1 + 1L;
                    long l3 = l2 + 1L;
                    long l4 = l3 + 6L;
                    System.out.println(l4 % 4L);
                }

                public static void printFloats(final float seed) {
                    float f0 = seed;
                    float f1 = f0 + 1.0f;
                    float f2 = f1 + 1.0f;
                    float f3 = f2 + 1.0f;
                    float f4 = f3 + 0.5f;
                    System.out.println(-f4);
                    System.out.println(f4 - f1);
                }

                public static void printDoubles(final double seed) {
                    double d0 = seed;
                    double d1 = d0 + 1.0;
                    double d2 = d1 + 1.0;
                    double d3 = d2 + 1.0;
                    double d4 = d3 + 1.0;
                    System.out.println(-d4);
                    System.out.println(d4 - d1);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-indexed-locals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\n1\n-3.75\n2.5\n-4.25\n3.0\n");
    }

    @Test
    void multiImplementationInterfaceDispatchReturnsFloatAndDouble() throws Exception {
        final Path project = project("interface-float-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Metric metric = new FastMetric();
                    System.out.println(metric.ratio());
                    System.out.println(metric.total());
                }
            }
            """);
        writeJava(project, "com.acme.Metric", """
            package com.acme;

            public interface Metric {
                float ratio();

                double total();
            }
            """);
        writeJava(project, "com.acme.FastMetric", """
            package com.acme;

            public final class FastMetric implements Metric {
                public FastMetric() {
                }

                public float ratio() {
                    return 1.25f;
                }

                public double total() {
                    return 2.5;
                }
            }
            """);
        writeJava(project, "com.acme.SlowMetric", """
            package com.acme;

            public final class SlowMetric implements Metric {
                public SlowMetric() {
                }

                public float ratio() {
                    return 3.75f;
                }

                public double total() {
                    return 4.5;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-float-double").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void uncaughtRuntimeExceptionLiteralBuildsAsNativePanic() throws Exception {
        final Path project = project("exception-panic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new IllegalStateException("boom");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/exception-panic").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).isEqualTo("boom\n");
    }

    @Test
    void reachableExplicitThrowCatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("try-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException("boom");
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/try-catch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("boom\n");
    }

    @Test
    void broadTryCatchWithoutDirectThrowStillFailsDuringCheck() throws Exception {
        final Path project = project("try-catch-broad");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println("safe");
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN014]", "direct explicit athrow");
    }

    @Test
    void basicEnumNameBuilds() throws Exception {
        final Path project = project("enum-basic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color selected = Color.RED;
                    System.out.println(selected.name());
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-basic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("RED\n");
    }

    @Test
    void stringIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("string-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "javan";
                    final int code = value.charAt(1);
                    System.out.println(value.length());
                    System.out.println(code);
                    System.out.println(value.equals("javan"));
                    System.out.println(value.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("5\n97\ntrue\nfalse\n");
    }

    @Test
    void integerLongToStringIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("number-to-string-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.toString(123));
                    System.out.println(Integer.toString(Integer.MIN_VALUE));
                    System.out.println(Long.toString(9876543210L));
                    System.out.println(Long.toString(Long.MIN_VALUE));
                    System.out.println(Integer.toString(-7).equals("-7"));
                    System.out.println(Long.toString(-9L).length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/number-to-string-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("123\n-2147483648\n9876543210\n-9223372036854775808\ntrue\n2\n");
    }

    @Test
    void jdkMathIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("jdk-math-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.abs(-9));
                    System.out.println(Math.abs(Integer.MIN_VALUE));
                    System.out.println(Math.min(4, -7));
                    System.out.println(Math.max(4, -7));
                    System.out.println(Math.abs(-12L));
                    System.out.println(Math.abs(Long.MIN_VALUE));
                    System.out.println(Math.min(100L, -200L));
                    System.out.println(Math.max(100L, -200L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/jdk-math-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("9\n-2147483648\n-7\n4\n12\n-9223372036854775808\n-200\n100\n");
    }

    @Test
    void jdkSystemTimeIntrinsicsBuildAndReturnLongValues() throws Exception {
        final Path project = project("jdk-time-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.nanoTime());
                    System.out.println(System.currentTimeMillis());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final String nativeOutput = process(project, List.of(project.resolve(".javan/bin/jdk-time-intrinsics").toString())).stdout();
        assertThat(nativeOutput).matches("[0-9]+\\n[0-9]+\\n");
    }

    @Test
    void objectsRequireNonNullIntrinsicBuildsAndChecksNull() throws Exception {
        final Path project = project("objects-require-non-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = Objects.requireNonNull("javan");
                    System.out.println(value);
                    System.out.println(value.length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n5\n");

        final Path nullProject = project("objects-require-non-null-null");
        writeJava(nullProject, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    Objects.requireNonNull(value);
                    System.out.println("unreachable");
                }
            }
            """);
        final CliRun nullBuild = run(tempDir, "build", nullProject.toString());
        assertThat(nullBuild.exitCode()).isZero();

        final ProcessResult nullRun = process(
            nullProject,
            List.of(nullProject.resolve(".javan/bin/objects-require-non-null-null").toString())
        );
        assertThat(nullRun.exitCode()).isEqualTo(1);
        assertThat(nullRun.stdout()).isEmpty();
        assertThat(nullRun.stderr()).contains("null object");
    }

    @Test
    void systemArraycopyIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-arraycopy-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[] {1, 2, 3, 4};
                    System.arraycopy(values, 1, values, 2, 2);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                    System.out.println(values[2]);
                    System.out.println(values[3]);

                    final byte[] bytes = new byte[] {7, 8, 9};
                    final byte[] targetBytes = new byte[4];
                    System.arraycopy(bytes, 0, targetBytes, 1, 3);
                    System.out.println(targetBytes[0]);
                    System.out.println(targetBytes[1]);
                    System.out.println(targetBytes[3]);

                    final String[] names = new String[] {"a", "b", null};
                    final String[] targetNames = new String[3];
                    System.arraycopy(names, 0, targetNames, 0, 3);
                    System.out.println(targetNames[0]);
                    System.out.println(targetNames[1]);
                    System.out.println(targetNames.length);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-arraycopy-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains("{\"name\": \"System.arraycopy\", \"count\": 3}");
    }

    @Test
    void arraysCopyOfIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] ints = Arrays.copyOf(new int[] {4, 5}, 4);
                    System.out.println(ints.length);
                    System.out.println(ints[0]);
                    System.out.println(ints[2]);

                    final long[] longs = Arrays.copyOf(new long[] {8L, 9L}, 1);
                    System.out.println(longs.length);
                    System.out.println(longs[0]);

                    final byte[] bytes = Arrays.copyOf(new byte[] {1, 2}, 3);
                    System.out.println(bytes[2]);

                    final short[] shorts = Arrays.copyOf(new short[] {3, 4}, 1);
                    System.out.println(shorts[0]);

                    final char[] chars = Arrays.copyOf(new char[] {'j'}, 2);
                    System.out.println((int) chars[0]);
                    System.out.println((int) chars[1]);

                    final float[] floats = Arrays.copyOf(new float[] {1.5f}, 2);
                    System.out.println(floats[0]);
                    System.out.println(floats[1]);

                    final double[] doubles = Arrays.copyOf(new double[] {2.25d}, 2);
                    System.out.println(doubles[0]);
                    System.out.println(doubles[1]);

                    final Object[] objects = Arrays.copyOf(new Object[] {"x", "y"}, 3);
                    System.out.println(objects.length);
                    System.out.println(objects[0]);
                    System.out.println(objects[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains("{\"name\": \"Arrays.copyOf\", \"count\": 8}");
    }

    @Test
    void unsupportedJdkIntrinsicOverloadsFailClearly() throws Exception {
        final Path mathProject = project("unsupported-math-overload");
        writeJava(mathProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.abs(1.25f));
                }
            }
            """);

        final CliRun mathRun = run(tempDir, "build", mathProject.toString());

        assertThat(mathRun.exitCode()).isEqualTo(2);
        assertThat(mathRun.stderr()).contains("error[JAVAN040]", "invokestatic");
        assertThat(Files.readString(mathProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Math.abs\", \"count\": 0}",
                "{\"target\": \"java/lang/Math.abs(F)F\", \"count\": 1}"
            );

        final Path objectsProject = project("unsupported-objects-overload");
        writeJava(objectsProject, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Objects.requireNonNull(null, "message");
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun objectsRun = run(tempDir, "build", objectsProject.toString());

        assertThat(objectsRun.exitCode()).isEqualTo(2);
        assertThat(objectsRun.stderr()).contains("error[JAVAN040]", "invokestatic");
        assertThat(Files.readString(objectsProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 0}",
                "{\"target\": \"java/util/Objects.requireNonNull(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;\", \"count\": 1}"
            );

        final Path arraysProject = project("unsupported-arrays-copy-of-overload");
        writeJava(arraysProject, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] values = Arrays.copyOf(new boolean[] {true}, 2);
                    System.out.println(values[0]);
                }
            }
            """);

        final CliRun arraysRun = run(tempDir, "build", arraysProject.toString());

        assertThat(arraysRun.exitCode()).isEqualTo(2);
        assertThat(arraysRun.stderr()).contains("error[JAVAN040]", "invokestatic");
        assertThat(Files.readString(arraysProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Arrays.copyOf\", \"count\": 0}",
                "{\"target\": \"java/util/Arrays.copyOf([ZI)[Z\", \"count\": 1}"
            );

        final Path numberProject = project("unsupported-number-to-string-overload");
        writeJava(numberProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.toString(15, 16));
                    System.out.println(Long.toString(31L, 16));
                }
            }
            """);

        final CliRun numberRun = run(tempDir, "build", numberProject.toString());

        assertThat(numberRun.exitCode()).isEqualTo(2);
        assertThat(numberRun.stderr()).contains("error[JAVAN040]", "invokestatic");
        assertThat(Files.readString(numberProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Integer.toString\", \"count\": 0}",
                "{\"name\": \"Long.toString\", \"count\": 0}",
                "{\"target\": \"java/lang/Integer.toString(II)Ljava/lang/String;\", \"count\": 1}",
                "{\"target\": \"java/lang/Long.toString(JI)Ljava/lang/String;\", \"count\": 1}"
            );
    }

    @Test
    void systemArraycopyPrimitiveTypeMismatchFailsAtRuntime() throws Exception {
        final Path project = project("system-arraycopy-type-mismatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object source = new byte[] {1};
                    final Object target = new boolean[1];
                    System.arraycopy(source, 0, target, 0, 1);
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/system-arraycopy-type-mismatch").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains("array copy type mismatch");
    }

    @Test
    void longArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("long-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final long[] values = new long[]{1L, 2L};
                    values[1] = 9L;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1\n9\n");
    }

    @Test
    void polymorphicSuperclassVirtualCallBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("polymorphic-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Base value = new Child();
                    System.out.println(value.text());
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                public Base() {
                }

                public String text() {
                    return "base";
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base {
                public Child() {
                }

                public String text() {
                    return "child";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/polymorphic-call").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("child\n");
    }

    @Test
    void singleImplementationInterfaceDispatchBuilds() throws Exception {
        final Path project = project("interface-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new EnglishGreeter("Ada");
                    System.out.println(greeter.greet());
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                String greet();
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                private final String name;

                public EnglishGreeter(final String name) {
                    this.name = name;
                }

                public String greet() {
                    return name;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-dispatch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("Ada\n");
    }

    @Test
    void multiImplementationInterfaceDispatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("interface-ambiguous");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new EnglishGreeter();
                    System.out.println(greeter.greet());
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                String greet();
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                public EnglishGreeter() {
                }

                public String greet() {
                    return "hello";
                }
            }
            """);
        writeJava(project, "com.acme.GermanGreeter", """
            package com.acme;

            public final class GermanGreeter implements Greeter {
                public GermanGreeter() {
                }

                public String greet() {
                    return "hallo";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-ambiguous").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("hello\n");
    }

    @Test
    void dependencyJarStaticIntMethodBuilds() throws Exception {
        final Path dependency = dependencyJar("mathlib", "dep.MathLib", """
            package dep;

            public final class MathLib {
                private MathLib() {
                }

                public static int twice(final int value) {
                    return value * 2;
                }
            }
            """);
        final Path project = project("dependency-jar");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.MathLib;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(MathLib.twice(21));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-jar").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n");
    }

    @Test
    void dependencyJarObjectConstructorAndInstanceMethodBuilds() throws Exception {
        final Path dependency = dependencyJar("scalelib", "dep.Scale", """
            package dep;

            public final class Scale {
                private final int base;

                public Scale(final int base) {
                    this.base = base;
                }

                public int apply(final int value) {
                    return base * value;
                }
            }
            """);
        final Path project = project("dependency-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Scale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Scale scale = new Scale(7);
                    System.out.println(scale.apply(6));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n");
    }

    @Test
    void dependencyJarObjectStringReturnBuilds() throws Exception {
        final Path dependency = dependencyJar("messagelib", "dep.Message", """
            package dep;

            public final class Message {
                private final String value;

                public Message(final String value) {
                    this.value = value;
                }

                public String value() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-string-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Message;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("from-dependency");
                    System.out.println(message.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-string-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("from-dependency\n");
    }

    @Test
    void dependencyClassDirectoryObjectStringReturnBuilds() throws Exception {
        final Path dependency = dependencyClasses("messageclasses", "dep.Message", """
            package dep;

            public class Message {
                private final String value;

                public Message(final String value) {
                    this.value = value;
                }

                public String value() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-class-directory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Message;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("from-classes");
                    System.out.println(message.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-class-directory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("from-classes\n");
    }

    @Test
    void dependencyJarMainDoesNotConfuseMainDetection() throws Exception {
        final Path dependency = dependencyJar("dep-main", "dep.Tool", """
            package dep;

            public final class Tool {
                private Tool() {
                }

                public static void main(final String[] args) {
                    System.out.println("dependency-main");
                }

                public static int value() {
                    return 7;
                }
            }
            """);
        final Path project = project("dependency-main");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Tool;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Tool.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-main").toString())).stdout()).isEqualTo("7\n");
    }

    @Test
    void unreachableForbiddenApiInsideDependencyDoesNotWarn() throws Exception {
        final Path dependency = dependencyJar("dep-dead-reflection", "dep.Safe", """
            package dep;

            public final class Safe {
                private Safe() {
                }

                public static int value() {
                    return 5;
                }

                public static void dead() throws ClassNotFoundException {
                    Class.forName("dep.Plugin");
                }
            }
            """);
        final Path project = project("dependency-dead-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Safe;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Safe.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).doesNotContain("warning[JAVAN101]");
    }

    @Test
    void reachableForbiddenApiInsideDependencyFails() throws Exception {
        final Path dependency = dependencyJar("dep-live-reflection", "dep.Loader", """
            package dep;

            public final class Loader {
                private Loader() {
                }

                public static void load() throws ClassNotFoundException {
                    Class.forName("dep.Plugin");
                }
            }
            """);
        final Path project = project("dependency-live-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Loader;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws ClassNotFoundException {
                    Loader.load();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "java/lang/Class.forName");
    }

    @Test
    void reachableReflectionFails() throws Exception {
        final Path project = project("reachable-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws ClassNotFoundException {
                    Class.forName("com.acme.Plugin");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "dynamic class loading is not supported");
    }

    @Test
    void unreachableReflectionWarnsOnly() throws Exception {
        final Path project = project("unreachable-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void load() throws ClassNotFoundException {
                    Class.forName("com.acme.Plugin");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN101]");
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.txt"))).contains("warning[JAVAN101]");
    }

    @Test
    void noMainFails() throws Exception {
        final Path project = project("no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN020]");
    }

    @Test
    void multipleMainFailsWithoutGuessing() throws Exception {
        final Path project = project("multiple-main");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeJava(project, "com.acme.Tool", mainClass("tool"));

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN022]", "com.acme.Main", "com.acme.Tool");
    }

    @Test
    void explicitMainSelectsCandidate() throws Exception {
        final Path project = project("explicit-main");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeJava(project, "com.acme.Tool", mainClass("tool"));

        final CliRun run = run(tempDir, "build", project.toString(), "--main", "com.acme.Tool", "--output", "selected");

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/selected").toString())).stdout()).isEqualTo("tool\n");
    }

    @Test
    void cleanRemovesOutputDirectory() throws Exception {
        final Path project = project("clean");
        Files.createDirectories(project.resolve(".javan/reports"));
        Files.writeString(project.resolve(".javan/reports/project.json"), "{}");

        final CliRun run = run(tempDir, "clean", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan")).doesNotExist();
    }

    @Test
    void doctorReportsToolchain() {
        final CliRun run = run(tempDir, "doctor");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("javan home:", "java.home:", "java.version:", "javac:", "c compiler:", "global settings:");
    }

    @Test
    void toolchainListPrintsToolchainHeader() {
        final CliRun run = run(tempDir, "toolchain", "list");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Toolchains");
    }

    @Test
    void toolchainDoctorPrintsDoctorReport() {
        final CliRun run = run(tempDir, "toolchain", "doctor");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Toolchain");
    }

    @Test
    void compatWritesDeterministicReports() throws Exception {
        final Path project = project("compat-pass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[] {"zero", "one"};
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final CliRun run = run(tempDir, "compat", project.toString());

        final int jdk = Runtime.version().feature();
        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Compatibility:", "status:          pass", "jdk classes:");
        assertThat(project.resolve(".javan/reports/compatibility-summary.md")).exists();
        assertThat(project.resolve(".javan/reports/compatibility-summary.json")).exists();
        assertThat(project.resolve(".javan/reports/jdk-" + jdk + "-inventory.json")).exists();
        assertThat(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve(".javan/jdk-inventory/jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve(".javan/bytecode-patterns/jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve("docs/support-matrix.md")).exists();
        assertThat(project.resolve("docs/support-matrix.json")).exists();
        assertThat(project.resolve("docs/jdk-compatibility.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/compatibility-summary.json")))
            .contains("\"status\": \"pass\"", "\"unknownFatalOpcodeUses\": 0");
        assertThat(Files.readString(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")))
            .contains("\"mnemonic\": \"anewarray\"", "\"mnemonic\": \"arraylength\"", "\"support\": \"NATIVE_SUPPORTED\"");
        assertThat(Files.readString(project.resolve("docs/support-matrix.md")))
            .contains(
                "object-array",
                "long-array",
                "float-double",
                "static-fields",
                "string-concat",
                "try-catch",
                "polymorphic-virtual",
                "interface-polymorphic",
                "JDK" + jdk
            );
    }

    @Test
    void compatReportsSupportedInvokedynamicStringConcat() throws Exception {
        final Path project = project("compat-string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("value " + args.length);
                }
            }
            """);

        final CliRun run = run(tempDir, "compat", project.toString());

        final int jdk = Runtime.version().feature();
        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("status:          pass");
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/compatibility-summary.json")))
            .contains("\"status\": \"pass\"");
        assertThat(Files.readString(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")))
            .contains("\"mnemonic\": \"invokedynamic\"", "\"support\": \"NATIVE_SUPPORTED\"");
    }

    @Test
    void sourceFileInputBuilds() throws Exception {
        final Path source = tempDir.resolve("Single.java");
        Files.writeString(source, """
            public final class Single {
                private Single() {
                }

                public static void main(final String[] args) {
                    System.out.println("single");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", source.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(tempDir, List.of(tempDir.resolve(".javan/bin/single").toString())).stdout()).isEqualTo("single\n");
    }

    @Test
    void classesDirectoryInputChecks() throws Exception {
        final Path project = project("classes-input");
        writeJava(project, "com.acme.Main", mainClass("main"));
        final Path classes = project.resolve("manual-classes");
        Files.createDirectories(classes);
        assertThat(process(project, List.of("javac", "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString())).exitCode())
            .isZero();

        final CliRun run = run(tempDir, "check", classes.toString(), "--no-build");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 1");
    }

    @Test
    void jarInputBuilds() throws Exception {
        final Path project = project("jar-input");
        writeJava(project, "com.acme.Main", mainClass("main"));
        final Path classes = project.resolve("classes");
        final Path jar = project.resolve("app.jar");
        Files.createDirectories(classes);
        assertThat(process(project, List.of("javac", "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of("jar", "--create", "--file", jar.toString(), "--main-class", "com.acme.Main", "-C", classes.toString(), ".")).exitCode())
            .isZero();

        final CliRun run = run(tempDir, "build", jar.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/app").toString())).stdout()).isEqualTo("main\n");
    }

    @Test
    void jarBuildDoesNotRequireMain() throws Exception {
        final Path project = project("jar-no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-no-main.jar")).exists();
    }

    @Test
    void jarBuildIncludesResourceFile() throws Exception {
        final Path project = project("jar-resource");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);
        writeResource(project, "public/index.html", "<h1>javan</h1>\n");

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-resource.jar").toFile())) {
            assertThat(jar.getEntry("public/index.html")).isNotNull();
            assertThat(new String(jar.getInputStream(jar.getEntry("public/index.html")).readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<h1>javan</h1>\n");
        }
    }

    @Test
    void jarBuildRemovesDeletedResourceFile() throws Exception {
        final Path project = project("jar-resource-delete");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);
        final Path resource = writeResource(project, "public/stale.txt", "stale\n");
        assertThat(run(tempDir, "build", project.toString(), "--kind", "jar").exitCode()).isZero();
        Files.delete(resource);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-resource-delete.jar").toFile())) {
            assertThat(jar.getEntry("public/stale.txt")).isNull();
        }
    }

    @Test
    void jarBuildWritesMainClassManifestWhenMainExplicit() throws Exception {
        final Path project = project("jar-manifest");
        writeJava(project, "com.acme.Main", mainClass("main"));

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar", "--main", "com.acme.Main");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-manifest.jar").toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class")).isEqualTo("com.acme.Main");
        }
    }

    @Test
    void jarBuildAllowsJvmOnlyBytecode() throws Exception {
        final Path project = project("jar-jvm-only");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    synchronized (Main.class) {
                        System.out.println("jar");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar", "--main", "com.acme.Main");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-jvm-only.jar")).exists();
    }

    @Test
    void nativeBuildCopiesResourcesToDistribution() throws Exception {
        final Path project = project("native-resources");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeResource(project, "assets/logo.txt", "logo\n");

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/resources/assets/logo.txt"))).isEqualTo("logo\n");
        assertThat(Files.readString(project.resolve(".javan/dist/resources/assets/logo.txt"))).isEqualTo("logo\n");
        assertThat(Files.readString(project.resolve(".javan/reports/resources.json")))
            .contains("\"resourceCount\": 1", "\"path\": \"assets/logo.txt\"");
    }

    @Test
    void inspectMavenProjectUsesArtifactId() throws Exception {
        final Path project = project("maven-project");
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>sharp-tool</artifactId>
              <version>1.0.0</version>
            </project>
            """);

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: MAVEN", ".javan/bin/sharp-tool");
    }

    @Test
    void mavenProjectCheckFindsClassesAfterCompile() throws Exception {
        final Path project = project("maven-check");
        Files.writeString(project.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>maven-check</artifactId>
              <version>1.0.0</version>
              <properties>
                <maven.compiler.release>25</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
            </project>
            """);
        writeJava(project, "com.acme.Main", mainClass("main"));

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 1");
        assertThat(project.resolve("target/classes/com/acme/Main.class")).exists();
    }

    @Test
    void inspectGradleProjectUsesRootProjectName() throws Exception {
        final Path project = project("gradle-project");
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'blade-tool'\n");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: GRADLE", ".javan/bin/blade-tool");
    }

    @Test
    void unknownOptionFails() {
        final CliRun run = run(tempDir, "check", "--wat");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("Unknown option");
    }

    @Test
    void missingOptionValueFails() {
        final CliRun run = run(tempDir, "build", "--main");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("Missing value for --main");
    }

    @Test
    void escapedStringLiteralBuilds() throws Exception {
        final Path project = project("escaped");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("quote: \\" slash: \\\\ tab:\\t");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/escaped").toString())).stdout()).isEqualTo("quote: \" slash: \\ tab:\t\n");
    }

    @Test
    void reachableStringConcatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("value " + args.length);
                    System.out.println("long " + 42L);
                    System.out.println("float " + 1.25f);
                    System.out.println("double " + 2.5);
                    System.out.println("bool " + true);
                    System.out.println("char " + 'A');
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-concat").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value 0\nlong 42\nfloat 1.25\ndouble 2.5\nbool true\nchar A\n");
    }

    @Test
    void reachableSystemLoadFails() throws Exception {
        final Path project = project("system-load");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.loadLibrary("danger");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "loading native libraries");
    }

    @Test
    void reachableInterfaceApplicationVoidCallBuilds() throws Exception {
        final Path project = project("interface-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new ConsoleGreeter();
                    greeter.hello();
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                void hello();
            }
            """);
        writeJava(project, "com.acme.ConsoleGreeter", """
            package com.acme;

            public final class ConsoleGreeter implements Greeter {
                public ConsoleGreeter() {
                }

                public void hello() {
                    System.out.println("virtual");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-call").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("virtual\n");
    }

    @Test
    void unreachableUnsupportedBytecodeWarnsOnly() throws Exception {
        final Path project = project("unreachable-bytecode");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead(final int value) {
                    synchronized (Main.class) {
                        final int[][] matrix = new int[1][1];
                        switch (value) {
                            case 1 -> System.out.println("one " + matrix.length);
                            default -> System.out.println("other");
                        }
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN130]");
    }

    @Test
    void missingReachableClassFailsClosedWorldAnalysis() throws Exception {
        final Path project = project("missing-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Missing.call();
                }
            }
            """);
        writeJava(project, "com.acme.Missing", """
            package com.acme;

            public final class Missing {
                private Missing() {
                }

                public static void call() {
                    System.out.println("missing");
                }
            }
            """);

        final CliRun compiled = run(tempDir, "check", project.toString());
        assertThat(compiled.exitCode()).isZero();
        Files.delete(project.resolve(".javan/classes/com/acme/Missing.class"));

        final CliRun run = run(tempDir, "check", project.toString(), "--no-build");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN011]");
    }

    @Test
    void unreachableNativeMethodDeclarationDoesNotFail() throws Exception {
        final Path project = project("unreachable-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static native void dead();
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void mainArgsArrayLengthBuildsAndUsesRuntimeArgs() throws Exception {
        final Path project = project("main-args");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(args.length);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/main-args").toString(), "left", "right")).stdout()).isEqualTo("2\n");
    }

    @Test
    void duplicateStaticCallsAreAnalyzedOnce() throws Exception {
        final Path project = project("duplicate-static");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.print();
                    Helper.print();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void print() {
                    System.out.println("twice");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 2");
    }

    @Test
    void reachableJdkOwnerIsTreatedAsPlatformCode() throws Exception {
        final Path project = project("jdk-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import jdk.jfr.FlightRecorder;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    FlightRecorder.isAvailable();
                    System.out.println("ok");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void reachableSunOwnerIsTreatedAsPlatformCode() throws Exception {
        final Path project = project("sun-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import sun.misc.Unsafe;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Unsafe.getUnsafe();
                    System.out.println("ok");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void reachableNativeMethodDeclarationFails() throws Exception {
        final Path project = project("reachable-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    dead();
                }

                public static native void dead();
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN013]");
    }

    private Path project(final String name) throws Exception {
        final Path project = tempDir.resolve(name);
        Files.createDirectories(project.resolve("src/main/java"));
        return project;
    }

    private static void writeJava(final Path project, final String className, final String source) throws Exception {
        final Path file = project.resolve("src/main/java").resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static Path writeResource(final Path project, final String name, final String source) throws Exception {
        final Path file = project.resolve("src/main/resources").resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static Path writeC(final Path project, final String filename, final String source) throws Exception {
        final Path file = project.resolve(filename);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    private static void writeExecutableScript(final Path script, final String source) throws Exception {
        Files.writeString(script, source.stripIndent(), StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
    }

    private static String sharedLibraryName(final String name) {
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return name + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + name + ".dylib";
        }
        return "lib" + name + ".so";
    }

    private static boolean commandAvailable(final String command) {
        try {
            return new ProcessBuilder(command, "--version").start().waitFor() == 0;
        } catch (final Exception exception) {
            return false;
        }
    }

    private static String mainClass(final String value) {
        return """
            package com.acme;

            public final class %s {
                private %s() {
                }

                public static void main(final String[] args) {
                    System.out.println("%s");
                }
            }
            """.formatted("main".equals(value) ? "Main" : "Tool", "main".equals(value) ? "Main" : "Tool", value);
    }

    private static CliRun run(final Path cwd, final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
            new Cli().run(cwd, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), args)
        );
        return new CliRun(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static ProcessResult process(final Path cwd, final List<String> command) {
        return assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            final Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
            final int exitCode = process.waitFor();
            return new ProcessResult(
                exitCode,
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)
            );
        });
    }

    private static String runJvm(final Path project, final String mainClass) {
        return runJvm(project, mainClass, List.of());
    }

    private static String runJvm(final Path project, final String mainClass, final List<Path> classpathEntries) {
        final Path classes = project.resolve("jvm-classes");
        final Path sourceRoot = project.resolve("src/main/java");
        final List<String> compile = new java.util.ArrayList<>(List.of("javac", "-d", classes.toString()));
        if (!classpathEntries.isEmpty()) {
            compile.add("-classpath");
            compile.add(String.join(java.io.File.pathSeparator, classpathEntries.stream().map(Path::toString).toList()));
        }
        try {
            Files.createDirectories(classes);
            try (java.util.stream.Stream<Path> sources = Files.walk(sourceRoot)) {
                sources
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(Path::toString)
                    .forEach(compile::add);
            }
        } catch (final Exception exception) {
            throw new IllegalStateException("Unable to prepare JVM run", exception);
        }
        assertThat(process(project, compile).exitCode()).isZero();
        final List<Path> runtimeClasspath = new java.util.ArrayList<>();
        runtimeClasspath.add(classes);
        runtimeClasspath.addAll(classpathEntries);
        final ProcessResult run = process(project, List.of(
            "java",
            "-cp",
            String.join(java.io.File.pathSeparator, runtimeClasspath.stream().map(Path::toString).toList()),
            mainClass
        ));
        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        return run.stdout();
    }

    private Path dependencyJar(final String name, final String className, final String source) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path jar = root.resolve(name + ".jar");
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(process(root, List.of("javac", "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        assertThat(process(root, List.of("jar", "--create", "--file", jar.toString(), "-C", classes.toString(), ".")).exitCode()).isZero();
        return jar;
    }

    private Path dependencyClasses(final String name, final String className, final String source) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(process(root, List.of("javac", "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        return classes;
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
