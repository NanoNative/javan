package javan;

import javan.testing.TestSuite.NativeTest;

import javan.cli.Cli;
import javan.cli.Version;
import javan.reporting.RuntimeFootprintReports;
import javan.util.Files2;
import javan.util.Json;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliIntegrationTest extends CliIntegrationSupport {
    @Test
    void mainPrintsVersionFromChildJvm() throws Exception {
        final Path root = Path.of("").toAbsolutePath();
        final Path classes = root.resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();

        final ProcessResult run = process(root, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            classes.toString(),
            "javan.Main",
            "--version"
        ), Duration.ofSeconds(30));

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).isEqualTo("javan " + Version.number() + "\n");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void mainUnknownOptionFailsAtProcessBoundary() throws Exception {
        final Path root = Path.of("").toAbsolutePath();
        final Path classes = root.resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();

        final ProcessResult run = process(root, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            classes.toString(),
            "javan.Main",
            "check",
            "--wat"
        ));

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("error[JAVAN900]: Unknown option: --wat");
        assertThat(run.stderr()).doesNotContain("Exception", "at javan.");
    }

    @Test
    void mainUsesProcessWorkingDirectoryForRelativeTarget() throws Exception {
        final Path root = Path.of("").toAbsolutePath();
        final Path classes = root.resolve("target/classes");
        final Path project = project("main-child-cwd");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("cwd");
                }
            }
            """);
        assertThat(classes.resolve("javan/Main.class")).exists();

        final ProcessResult run = process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            classes.toString(),
            "javan.Main",
            "inspect",
            "."
        ));

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(project.resolve(".javan/reports/project.json")).exists();
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
    void inspectPrintsMultipleExplicitClassFoldersInOrder() throws Exception {
        final Path project = project("inspect-class-folders");
        final Path firstClasses = project.resolve("first-classes");
        final Path secondClasses = project.resolve("second-classes");
        Files.createDirectories(firstClasses);
        Files.createDirectories(secondClasses);

        final CliRun run = run(
            tempDir,
            "inspect",
            project.toString(),
            "--classes",
            firstClasses.toString(),
            "--classes",
            secondClasses.toString()
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).contains("Classes: [" + firstClasses.toAbsolutePath().normalize() + ", "
            + secondClasses.toAbsolutePath().normalize() + "]");
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
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "{\"name\": \"project\", \"status\": \"present\"",
            "{\"name\": \"runtime-features\", \"status\": \"present\"",
            "{\"name\": \"intrinsics\", \"status\": \"present\"",
            "{\"name\": \"optimizations\", \"status\": \"present\""
        );
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
    void checkWritesReachableJdkLedgerBreakdownForSupportedCalls() throws Exception {
        final Path project = project("check-reachable-jdk-ledger");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(1);
                    Thread.currentThread();
                    System.out.println(List.of("x").getFirst());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "\"runtimeCallSiteCount\": 5",
                "\"supportedDirectJdkCallSiteCount\": 0",
                "\"supportedJdkCallSiteCount\": 5",
                "{\"name\": \"PrintStream.println\", \"count\": 2}",
                "{\"name\": \"Thread.currentThread\", \"count\": 1}",
                "{\"name\": \"List.getFirst\", \"count\": 1}",
                "{\"name\": \"List.of\", \"count\": 1}",
                "\"unsupportedJdkCallCandidateCount\": 0"
            );
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.md")))
            .contains(
                "Supported reachable JDK call sites: `5`",
                "Runtime-registry reachable call sites: `5`",
                "Supported-direct reachable call sites: `0`",
                "Unsupported reachable call sites: `0`"
            );
    }

    @Test
    void generatedRuntimeHelperFailureBuildsAsReadableNativePanic() throws Exception {
        final Path project = project("helper-panic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new int[-1].length);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/helper-panic").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains(
            "[JAVAN-RUNTIME-PANIC] runtime helper failure",
            "Where:",
            "com.acme.Main.main([Ljava/lang/String;)V(Main.java:",
            "Code:",
            "System.out.println(new int[-1].length);",
            "^ here",
            "Why:",
            "detail: negative array length",
            "Fix:"
        );
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
    void applicationRuntimeExceptionExactCatchPreservesObjectIdentityAndState() throws Exception {
        final Path project = project("application-exception-exact-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Problem expected = new Problem("boom", 7);
                    try {
                        throw expected;
                    } catch (final Problem actual) {
                        System.out.println((actual == expected) + ":" + actual.getMessage() + ":" + actual.code());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                private final int code;

                public Problem(final String message, final int code) {
                    super(message);
                    this.code = code;
                }

                public int code() {
                    return code;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/application-exception-exact-catch").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true:boom:7\n");
    }

    @Test
    void applicationRuntimeExceptionMatchesRuntimeExceptionAndThrowableCatches() throws Exception {
        final Path project = project("application-exception-super-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        fail("runtime");
                    } catch (final RuntimeException exception) {
                        System.out.println("runtime:" + exception.getMessage());
                    }
                    try {
                        fail("throwable");
                    } catch (final Throwable throwable) {
                        System.out.println("throwable:" + throwable.getMessage());
                    }
                }

                private static void fail(final String message) {
                    throw new Problem(message, 1);
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                private final int code;

                public Problem(final String message, final int code) {
                    super(message);
                    this.code = code;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/application-exception-super-catch").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("runtime:runtime\nthrowable:throwable\n");
    }

    @Test
    void applicationRuntimeExceptionPropagatesAndRethrowsAcrossMethods() throws Exception {
        final Path project = project("application-exception-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Problem expected = new Problem("propagated", 9);
                    try {
                        middle(expected);
                    } catch (final Problem caught) {
                        try {
                            throw caught;
                        } catch (final Throwable rethrown) {
                            System.out.println((rethrown == expected) + ":" + rethrown.getMessage());
                        }
                    }
                }

                private static void middle(final Problem problem) {
                    leaf(problem);
                }

                private static void leaf(final Problem problem) {
                    throw problem;
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                private final int code;

                public Problem(final String message, final int code) {
                    super(message);
                    this.code = code;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/application-exception-rethrow").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true:propagated\n");
    }

    @Test
    void applicationExceptionValuesKeepJavaSemanticsAcrossObjectBoundaries() throws Exception {
        final Path project = project("application-exception-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static Problem staticProblem;
                private final Problem instanceProblem;

                private Main(final Problem problem) {
                    instanceProblem = problem;
                }

                public static void main(final String[] args) {
                    final Problem expected = new Problem();
                    final Main holder = new Main(expected);
                    staticProblem = expected;
                    try {
                        throw holder.instanceProblem;
                    } catch (final Problem caught) {
                        printCaught(caught, expected, "instance");
                    }
                    try {
                        throw staticProblem;
                    } catch (final Problem caught) {
                        printCaught(caught, expected, "static");
                    }
                    try {
                        throw returned(expected);
                    } catch (final Problem caught) {
                        printCaught(caught, expected, "return");
                    }
                    try {
                        throwValue(null);
                    } catch (final NullPointerException caught) {
                        System.out.println("null");
                    }
                }

                private static Problem returned(final Problem problem) {
                    return problem;
                }

                private static void printCaught(final Problem caught, final Problem expected, final String label) {
                    System.out.println(label + ":" + (caught == expected) + ":" + caught.getMessage());
                }

                private static void throwValue(final Problem problem) {
                    throw problem;
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                public Problem() {
                    super();
                    new RuntimeException("separate");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/application-exception-values").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("instance:true:null\nstatic:true:null\nreturn:true:null\nnull\n");
    }

    @Test
    void applicationExceptionCauseConstructorFailsAtVerification() throws Exception {
        final Path project = project("application-exception-cause");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new Problem(new RuntimeException("cause"));
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                public Problem(final Throwable cause) {
                    super(cause);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "JAVAN031",
            "java/lang/RuntimeException.<init>(Ljava/lang/Throwable;)V",
            "has no native intrinsic, substitution, or supported runtime model yet"
        );
    }

    @Test
    void uncaughtApplicationRuntimeExceptionReportsTypeAndMessage() throws Exception {
        final Path project = project("application-exception-uncaught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new Problem("uncaught", 11);
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                private final int code;

                public Problem(final String message, final int code) {
                    super(message);
                    this.code = code;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/application-exception-uncaught").toString())
        );
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains(
            "[JAVAN-RUNTIME-PANIC] uncaught Java exception (com/acme/Problem)",
            "com.acme.Main.main([Ljava/lang/String;)V(Main.java:",
            "detail: uncaught"
        );
    }

    @Test
    void derivedApplicationExceptionKeepsItsDynamicTypeThroughBaseReference() throws Exception {
        final Path project = project("application-exception-inheritance");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final BaseProblem expected = new SpecificProblem("derived");
                    try {
                        throwBase(expected);
                    } catch (final BaseProblem actual) {
                        System.out.println((actual == expected) + ":" + (actual instanceof SpecificProblem) + ":" + actual.getMessage());
                    }
                }

                private static void throwBase(final BaseProblem problem) {
                    throw problem;
                }
            }
            """);
        writeJava(project, "com.acme.BaseProblem", """
            package com.acme;

            public class BaseProblem extends RuntimeException {
                public BaseProblem(final String message) {
                    super(message);
                }
            }
            """);
        writeJava(project, "com.acme.SpecificProblem", """
            package com.acme;

            public final class SpecificProblem extends BaseProblem {
                public SpecificProblem(final String message) {
                    super(message);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/application-exception-inheritance").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true:true:derived\n");
    }

    @Test
    void uncaughtApplicationExceptionRethrowKeepsOriginalSource() throws Exception {
        final Path project = project("application-exception-uncaught-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new Problem("rethrown");
                    } catch (final Problem problem) {
                        throw problem;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Problem", """
            package com.acme;

            public final class Problem extends RuntimeException {
                public Problem(final String message) {
                    super(message);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/application-exception-uncaught-rethrow").toString())
        );
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stderr()).contains(
            "uncaught Java exception (com/acme/Problem)",
            "com.acme.Main.main([Ljava/lang/String;)V(Main.java:9)",
            "detail: rethrown"
        );
    }

    @Test
    void reachableExplicitThrowFinallyCatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("try-finally");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            throw new IllegalStateException("boom");
                        } finally {
                            System.out.println("finally");
                        }
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/try-finally").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("finally\nboom\n");
    }

    @Test
    void typedCatchSkipsNonMatchingSpecificHandler() throws Exception {
        final Path project = project("typed-catch-specific-miss");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new Error("typed");
                    } catch (final IllegalStateException exception) {
                        System.out.println("wrong");
                    } catch (final Throwable throwable) {
                        System.out.println("right:" + throwable.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-specific-miss").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("right:typed\n");
    }

    @Test
    void typedCatchMatchesRuntimeExceptionSuperclass() throws Exception {
        final Path project = project("typed-catch-runtime-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException("runtime");
                    } catch (final RuntimeException exception) {
                        System.out.println("runtime:" + exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-runtime-superclass").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("runtime:runtime\n");
    }

    @Test
    void typedCatchMatchesIoExceptionSuperclass() throws Exception {
        final Path project = project("typed-catch-io-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.FileNotFoundException;
            import java.io.IOException;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new FileNotFoundException("missing");
                    } catch (final IOException exception) {
                        System.out.println("io:" + exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-io-superclass").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("io:missing\n");
    }

    @Test
    void typedCatchMatchesUtilRuntimeExceptionSuperclass() throws Exception {
        final Path project = project("typed-catch-util-runtime-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.NoSuchElementException;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new NoSuchElementException("empty");
                    } catch (final RuntimeException exception) {
                        System.out.println("runtime:" + exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-util-runtime-superclass").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("runtime:empty\n");
    }

    @Test
    void typedCatchDoesNotMatchErrorAsException() throws Exception {
        final Path project = project("typed-catch-error-not-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new Error("error");
                    } catch (final Exception exception) {
                        System.out.println("wrong");
                    } catch (final Throwable throwable) {
                        System.out.println("throwable:" + throwable.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-error-not-exception").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("throwable:error\n");
    }

    @Test
    void defaultConstructedExceptionMessageIsNullWhenCaught() throws Exception {
        final Path project = project("exception-default-message-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException();
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage() == null);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/exception-default-message-null").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void defaultConstructedUncaughtExceptionPanicIsDeterministic() throws Exception {
        final Path project = project("exception-default-panic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new IllegalStateException();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/exception-default-panic").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains(
            "[JAVAN-RUNTIME-PANIC] uncaught Java exception",
            "Where:",
            "com.acme.Main.main([Ljava/lang/String;)V(Main.java:",
            "detail: javan panic",
            "Fix:"
        );
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
    void throwableConstructorWithCauseFailsDuringCheck() throws Exception {
        final Path project = project("exception-cause-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException("outer", new RuntimeException("cause"));
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN014]");
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
    void constantSpecificEnumVirtualDispatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-constant-specific-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    for (final Kind kind : Kind.values()) {
                        System.out.println(kind.label());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Kind", """
            package com.acme;

            public enum Kind {
                FIRST {
                    @Override
                    String label() {
                        return "first";
                    }
                },
                SECOND {
                    @Override
                    String label() {
                        return "second";
                    }
                };

                abstract String label();
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/enum-constant-specific-dispatch").toString()));
        assertThat(nativeRun.stdout())
            .as("exit=%s stderr=%s", nativeRun.exitCode(), nativeRun.stderr())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("first\nsecond\n");
    }

    @Test
    void enumValueOfFailsDuringCheck() throws Exception {
        final Path project = project("enum-value-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Color.valueOf("RED").name());
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

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN015]", "Enum.valueOf");
    }

    @Test
    void enumOrdinalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-ordinal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color selected = Color.BLUE;
                    System.out.println(selected.ordinal());
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
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-ordinal").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void enumValuesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color[] values = Color.values();
                    System.out.println(values.length);
                    System.out.println(values[1].name());
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
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-values").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\nBLUE\n");
    }

    @Test
    void enumIdentityComparisonBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<Holder> values = new ArrayList<>();
                    values.add(new Holder(Kind.OBJECT));
                    final Holder removed = values.removeLast();
                    System.out.println(removed.kind() == Kind.OBJECT);
                }
            }
            """);
        writeJava(project, "com.acme.Kind", """
            package com.acme;

            public enum Kind {
                INT,
                LONG,
                FLOAT,
                OBJECT
            }
            """);
        writeJava(project, "com.acme.Holder", """
            package com.acme;

            public record Holder(Kind kind) {
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-identity").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void enumIdentityDisjunctionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-identity-disjunction");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<Holder> values = new ArrayList<>();
                    values.add(new Holder(Kind.OBJECT));
                    final Holder removed = values.removeLast();
                    System.out.println(isObjectLike(removed.kind()));
                }

                private static boolean isObjectLike(final Kind kind) {
                    return kind == Kind.OBJECT
                        || kind == Kind.PRINT_STREAM
                        || kind == Kind.ERROR_PRINT_STREAM;
                }
            }
            """);
        writeJava(project, "com.acme.Kind", """
            package com.acme;

            public enum Kind {
                INT,
                LONG,
                FLOAT,
                OBJECT,
                PRINT_STREAM,
                ERROR_PRINT_STREAM
            }
            """);
        writeJava(project, "com.acme.Holder", """
            package com.acme;

            public record Holder(Kind kind) {
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-identity-disjunction").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void enumSwitchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-switch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color selected = Color.BLUE;
                    switch (selected) {
                        case RED -> System.out.println("red");
                        case BLUE -> System.out.println("blue");
                    }
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
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-switch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("blue\n");
    }

    @Test
    void enumSwitchExpressionRecordBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-switch-expression-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                enum Kind {
                    PRINT_STREAM,
                    ERROR_PRINT_STREAM,
                    OBJECT
                }

                record Value(Kind kind, Object expression) {
                }

                private Main() {
                }

                static Value map(final Value value) {
                    return switch (value.kind()) {
                        case PRINT_STREAM -> new Value(Kind.OBJECT, "out");
                        case ERROR_PRINT_STREAM -> new Value(Kind.OBJECT, "err");
                        default -> value;
                    };
                }

                public static void main(final String[] args) {
                    System.out.println(map(new Value(Kind.PRINT_STREAM, null)).expression() != null);
                    System.out.println(map(new Value(Kind.ERROR_PRINT_STREAM, null)).expression());
                    System.out.println(map(new Value(Kind.OBJECT, "same")).expression());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-switch-expression-record").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nerr\nsame\n");
    }

    @Test
    void denseIntSwitchExpressionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("dense-int-switch-expression");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                static String map(final int value) {
                    return switch (value) {
                        case 1 -> "one";
                        case 2 -> "two";
                        case 3 -> "three";
                        default -> "other";
                    };
                }

                public static void main(final String[] args) {
                    System.out.println(map(1));
                    System.out.println(map(2));
                    System.out.println(map(3));
                    System.out.println(map(9));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dense-int-switch-expression").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("one\ntwo\nthree\nother\n");
    }

    @Test
    void denseIntSwitchExpressionOutOfOrderBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("dense-int-switch-expression-out-of-order");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                static String map(final int value) {
                    return switch (value) {
                        case 3 -> "three";
                        case 1 -> "one";
                        case 2 -> "two";
                        default -> "other";
                    };
                }

                public static void main(final String[] args) {
                    System.out.println(map(3));
                    System.out.println(map(1));
                    System.out.println(map(2));
                    System.out.println(map(9));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dense-int-switch-expression-out-of-order").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("three\none\ntwo\nother\n");
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
    void utf8StringLiteralMatchesJvmOutput() throws Exception {
        assertUtf8LiteralMatchesJvm(
            "utf8-string-literal",
            "\\u0661\\u20AC\\uD83D\\uDE80"
        );
    }

    @Test
    void longUtf8StringLiteralAcrossCChunksMatchesJvmOutput() throws Exception {
        assertUtf8LiteralMatchesJvm(
            "utf8-string-literal-chunks",
            "a".repeat(117) + "\\u0661\\\\\\\"\\n7"
        );
    }

    @Test
    void malformedSurrogateLiteralMatchesRuntimeConstructedString() throws Exception {
        assertNativeMatchesJvm(
            "malformed-surrogate-literal",
            """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("\\uD800".equals(new String(new char[] {'\\uD800'})));
                }
            }
            """
        );
    }

    private void assertUtf8LiteralMatchesJvm(
        final String projectName,
        final String escapedLiteral
    ) throws Exception {
        assertNativeMatchesJvm(projectName, """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("%s");
                }
            }
            """.formatted(escapedLiteral));
    }

    private void assertNativeMatchesJvm(final String projectName, final String source) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source);
        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        List<Object> outcome = List.of(build.exitCode(), build.stderr(), -1, "", "");
        if (build.exitCode() == 0) {
            final ProcessResult nativeRun = process(
                project,
                List.of(project.resolve(".javan/bin").resolve(projectName).toString())
            );
            outcome = List.of(
                build.exitCode(),
                build.stderr(),
                nativeRun.exitCode(),
                nativeRun.stderr(),
                nativeRun.stdout()
            );
        }

        assertThat(outcome).containsExactly(0, "", 0, "", jvmOutput);
    }

    @Test
    void nestedShortCircuitBooleanMatchesJvm() throws Exception {
        assertNativeMatchesJvm("nested-short-circuit-boolean", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(select(true, true, false, false));
                    System.out.println(select(true, false, true, true));
                    System.out.println(select(true, false, true, false));
                    System.out.println(select(false, false, false, false));
                }

                private static boolean select(
                    final boolean first,
                    final boolean second,
                    final boolean third,
                    final boolean fourth
                ) {
                    return first && second || third && fourth;
                }
            }
            """);
    }

    @Test
    void intToCharNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 65537 + args.length;
                    final char value = (char) raw;
                    System.out.println((int) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-char").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void intToFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 42 + args.length;
                    final float value = raw;
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-float").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42.0\n");
    }

    @Test
    void intToDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 42 + args.length;
                    final double value = raw;
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-double").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42.0\n");
    }

    @Test
    void longToIntNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-to-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final long raw = 4_294_967_299L + args.length;
                    final int value = (int) raw;
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-to-int").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n");
    }

    @Test
    void intToByteNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-byte");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 130 + args.length;
                    final byte value = (byte) raw;
                    System.out.println((int) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-byte").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-126\n");
    }

    @Test
    void intToShortNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-short");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 32769 + args.length;
                    final short value = (short) raw;
                    System.out.println((int) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-short").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-32767\n");
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
    void dynamicByteShortStringConcatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-concat-byte-short");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final byte left = (byte) args.length;
                    final short right = (short) (left + 2);
                    System.out.println("values " + left + ":" + right);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-concat-byte-short").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("values 0:2\n");
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
    void applicationEntryCanBeInvokedAsJavaMethod() throws Exception {
        final Path project = project("method-invocation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.lang.reflect.Method;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    if (args.length > 0) {
                        System.out.println(args[0]);
                        return;
                    }
                    final Method method = Main.class.getDeclaredMethod("main", String[].class);
                    method.invoke(null, (Object) new String[] {"nested"});
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/method-invocation").toString())).stdout())
            .isEqualTo("nested\n");
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

        final CliRun run = run(tempDir, "check", project.resolve(".javan/classes").toString());

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
    void explicitClassesCheckIgnoresEmbeddedTestResourceProjectOutputs() throws Exception {
        final Path project = project("explicit-classes-ignore-test-resource-outputs");
        Files.writeString(project.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>explicit-classes-ignore-test-resource-outputs</artifactId>
              <version>1.0.0</version>
            </project>
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.help();
                    System.out.println("ok");
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void help() {
                }
            }
            """);
        final Path fixtureSource = project.resolve("src/test/resources/projects/probe/src/main/java/com/acme/Helper.java");
        Files.createDirectories(fixtureSource.getParent());
        Files.writeString(fixtureSource, """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void shadow() {
                }
            }
            """);
        final Path fixtureClasses = project.resolve("src/test/resources/projects/probe/.javan/classes");
        Files.createDirectories(fixtureClasses);
        final ProcessResult compileFixture = process(project, List.of(
            CliTestHarness.currentJavacCommand(),
            "-d",
            fixtureClasses.toString(),
            fixtureSource.toString()
        ));
        assertThat(compileFixture.exitCode()).as(compileFixture.stderr()).isZero();

        final CliRun run = run(tempDir,
            "check",
            project.toString(),
            "--classes",
            project.resolve("target/classes").toString(),
            "--main",
            "com.acme.Main"
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
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
    void objectArrayCloneBuildsAndRuns() throws Exception {
        final Path project = project("object-array-clone");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[1];
                    values[0] = "left";
                    final String[] copy = values.clone();
                    values[0] = "right";
                    System.out.println(copy[0]);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-clone").toString())).stdout()).isEqualTo("left\n");
    }

    @Test
    void intArrayCloneBuildsAndRuns() throws Exception {
        final Path project = project("int-array-clone");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[1];
                    values[0] = 7;
                    final int[] copy = values.clone();
                    values[0] = 9;
                    System.out.println(copy[0]);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array-clone").toString())).stdout()).isEqualTo("7\n");
    }

    @Test
    void everyArrayCloneKindBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("all-array-clones");
        writeJava(project, "com.acme.Payload", """
            package com.acme;

            public final class Payload {
                final int value;

                Payload(final int value) {
                    this.value = value;
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] booleans = new boolean[]{true};
                    final byte[] bytes = new byte[]{2};
                    final char[] chars = new char[]{'A'};
                    final short[] shorts = new short[]{3};
                    final int[] ints = new int[]{4};
                    final long[] longs = new long[]{5L};
                    final float[] floats = new float[]{6.5f};
                    final double[] doubles = new double[]{7.25d};
                    final boolean[] booleanCopy = booleans.clone();
                    final byte[] byteCopy = bytes.clone();
                    final char[] charCopy = chars.clone();
                    final short[] shortCopy = shorts.clone();
                    final int[] intCopy = ints.clone();
                    final long[] longCopy = longs.clone();
                    final float[] floatCopy = floats.clone();
                    final double[] doubleCopy = doubles.clone();

                    booleans[0] = false;
                    bytes[0] = 9;
                    chars[0] = 'Z';
                    shorts[0] = 9;
                    ints[0] = 9;
                    longs[0] = 9L;
                    floats[0] = 9.5f;
                    doubles[0] = 9.25d;

                    System.out.println(booleans != booleanCopy);
                    System.out.println(booleanCopy[0]);
                    System.out.println(bytes != byteCopy);
                    System.out.println(byteCopy[0]);
                    System.out.println(chars != charCopy);
                    System.out.println(charCopy[0]);
                    System.out.println(shorts != shortCopy);
                    System.out.println(shortCopy[0]);
                    System.out.println(ints != intCopy);
                    System.out.println(intCopy[0]);
                    System.out.println(longs != longCopy);
                    System.out.println(longCopy[0]);
                    System.out.println(floats != floatCopy);
                    System.out.println(floatCopy[0]);
                    System.out.println(doubles != doubleCopy);
                    System.out.println(doubleCopy[0]);

                    final Payload payload = new Payload(8);
                    final Payload[] objects = new Payload[]{payload, null};
                    final Payload[] objectCopy = objects.clone();
                    objects[0] = new Payload(9);
                    System.out.println(objects != objectCopy);
                    System.out.println(objectCopy[0] == payload);
                    System.out.println(objectCopy[1] == null);

                    final int[][] nested = new int[][]{new int[]{10}, null};
                    final int[][] nestedCopy = nested.clone();
                    nested[0][0] = 11;
                    System.out.println(nested != nestedCopy);
                    System.out.println(nested[0] == nestedCopy[0]);
                    System.out.println(nestedCopy[0][0]);
                    System.out.println(nestedCopy[1] == null);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/all-array-clones").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void nullArrayCloneBuildsAndFailsClearly() throws Exception {
        final Path project = project("null-array-clone");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = null;
                    values.clone();
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/null-array-clone").toString())
        );
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stderr()).contains("null array");
    }

    @Test
    void objectReferenceCompareBuildsAndRuns() throws Exception {
        final Path project = project("object-reference-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    if (args == args) {
                        System.out.println("same");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-reference-compare").toString())).stdout()).isEqualTo("same\n");
    }

    @Test
    void denseIntSwitchBuildsAndRuns() throws Exception {
        final Path project = project("dense-int-switch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    switch (args.length) {
                        case 1 -> System.out.println("one");
                        case 2 -> System.out.println("two");
                        case 3 -> System.out.println("three");
                        default -> System.out.println("other");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dense-int-switch").toString(), "a", "b")).stdout()).isEqualTo("two\n");
    }

    @Test
    void denseIntSwitchDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("dense-int-switch-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    switch (args.length) {
                        case 1 -> System.out.println("one");
                        case 2 -> System.out.println("two");
                        case 3 -> System.out.println("three");
                        default -> System.out.println("other");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dense-int-switch-default").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("other\n");
    }

    @Test
    void sparseIntSwitchBuildsAndRuns() throws Exception {
        final Path project = project("sparse-int-switch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    switch (args.length) {
                        case 1 -> System.out.println("one");
                        case 1000 -> System.out.println("many");
                        default -> System.out.println("other");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/sparse-int-switch").toString(), "a")).stdout()).isEqualTo("one\n");
    }

    @Test
    void sparseIntSwitchDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("sparse-int-switch-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    switch (args.length) {
                        case 1 -> System.out.println("one");
                        case 1000 -> System.out.println("many");
                        default -> System.out.println("other");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/sparse-int-switch-default").toString(), "a", "b")).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("other\n");
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
    void reachableUnsupportedJdkOwnerFailsAsUnsupportedJdkCall() throws Exception {
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

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN031]", "jdk/jfr/FlightRecorder.isAvailable()Z");
        assertThat(run.stderr()).doesNotContain("JAVAN011", "JAVAN012");
    }

    @Test
    void reachableUnsupportedSunOwnerFailsAsUnsupportedJdkCall() throws Exception {
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

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN031]", "sun/misc/Unsafe.getUnsafe()Lsun/misc/Unsafe;");
        assertThat(run.stderr()).doesNotContain("JAVAN011", "JAVAN012");
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

}
