package javan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliMathAddExactLongIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("math-add-exact-long-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int order;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive" : args[0];
                    if ("positive".equals(scenario)) {
                        System.out.println(Math.addExact(3_000_000_000L, 4_000_000_000L));
                    } else if ("negative".equals(scenario)) {
                        System.out.println(Math.addExact(-3_000_000_000L, -4_000_000_000L));
                    } else if ("maximum".equals(scenario)) {
                        System.out.println(Math.addExact(Long.MAX_VALUE, 0L));
                    } else if ("minimum".equals(scenario)) {
                        System.out.println(Math.addExact(Long.MIN_VALUE, 0L));
                    } else if ("opposite".equals(scenario)) {
                        System.out.println(Math.addExact(Long.MAX_VALUE, Long.MIN_VALUE));
                    } else if ("positive-overflow".equals(scenario)) {
                        System.out.println(addOrFallback(Long.MAX_VALUE, 1L));
                    } else if ("negative-overflow".equals(scenario)) {
                        System.out.println(addOrFallback(Long.MIN_VALUE, -1L));
                    } else if ("uncaught-positive-overflow".equals(scenario)) {
                        System.out.println(Math.addExact(Long.MAX_VALUE, 1L));
                    } else if ("uncaught-negative-overflow".equals(scenario)) {
                        System.out.println(Math.addExact(Long.MIN_VALUE, -1L));
                    } else if ("order".equals(scenario)) {
                        System.out.println(Math.addExact(left(), right()));
                        System.out.println(order);
                    }
                }

                private static long addOrFallback(final long left, final long right) {
                    try {
                        return Math.addExact(left, right);
                    } catch (final ArithmeticException ignored) {
                        return 41L;
                    }
                }

                private static long left() {
                    order = order * 10 + 1;
                    return 2L;
                }

                private static long right() {
                    order = order * 10 + 2;
                    return 3L;
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/math-add-exact-long-probe");
    }

    @Test
    void positiveSumMatchesJvm() {
        assertThat(nativeRun("positive")).isEqualTo(jvmRun("positive"));
    }

    @Test
    void negativeSumMatchesJvm() {
        assertThat(nativeRun("negative")).isEqualTo(jvmRun("negative"));
    }

    @Test
    void maximumBoundaryMatchesJvm() {
        assertThat(nativeRun("maximum")).isEqualTo(jvmRun("maximum"));
    }

    @Test
    void minimumBoundaryMatchesJvm() {
        assertThat(nativeRun("minimum")).isEqualTo(jvmRun("minimum"));
    }

    @Test
    void oppositeSignsMatchJvm() {
        assertThat(nativeRun("opposite")).isEqualTo(jvmRun("opposite"));
    }

    @Test
    void positiveOverflowMatchesJvm() {
        assertThat(nativeRun("positive-overflow")).isEqualTo(jvmRun("positive-overflow"));
    }

    @Test
    void negativeOverflowMatchesJvm() {
        assertThat(nativeRun("negative-overflow")).isEqualTo(jvmRun("negative-overflow"));
    }

    @Test
    void uncaughtPositiveOverflowTerminatesWithArithmeticDiagnostic() {
        assertThat(failureShape(nativeRun("uncaught-positive-overflow")))
            .isEqualTo(new FailureShape(true, true));
    }

    @Test
    void uncaughtNegativeOverflowTerminatesWithArithmeticDiagnostic() {
        assertThat(failureShape(nativeRun("uncaught-negative-overflow")))
            .isEqualTo(new FailureShape(true, true));
    }

    @Test
    void argumentsEvaluateLeftToRight() {
        assertThat(nativeRun("order")).isEqualTo(jvmRun("order"));
    }

    private ProcessResult nativeRun(final String scenario) {
        return process(project, List.of(binary.toString(), scenario));
    }

    private ProcessResult jvmRun(final String scenario) {
        return process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            project.resolve("jvm-classes").toString(),
            MAIN_CLASS,
            scenario
        ));
    }

    private static FailureShape failureShape(final ProcessResult result) {
        return new FailureShape(
            result.exitCode() != 0,
            result.stderr().contains("java/lang/ArithmeticException")
        );
    }

    private record FailureShape(boolean failed, boolean arithmeticException) {
    }
}
