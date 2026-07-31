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
final class CliMathSubtractExactLongIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("math-subtract-exact-long-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int order;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive" : args[0];
                    if ("positive".equals(scenario)) {
                        System.out.println(Math.subtractExact(7_000_000_000L, 3_000_000_000L));
                    } else if ("negative".equals(scenario)) {
                        System.out.println(Math.subtractExact(-7_000_000_000L, -3_000_000_000L));
                    } else if ("mixed-sign".equals(scenario)) {
                        System.out.println(Math.subtractExact(-3_000_000_000L, 4_000_000_000L));
                    } else if ("zero".equals(scenario)) {
                        System.out.println(Math.subtractExact(0L, 0L));
                    } else if ("maximum".equals(scenario)) {
                        System.out.println(Math.subtractExact(Long.MAX_VALUE, 0L));
                    } else if ("minimum".equals(scenario)) {
                        System.out.println(Math.subtractExact(Long.MIN_VALUE, 0L));
                    } else if ("positive-threshold".equals(scenario)) {
                        System.out.println(Math.subtractExact(Long.MAX_VALUE - 1L, -1L));
                    } else if ("negative-threshold".equals(scenario)) {
                        System.out.println(Math.subtractExact(Long.MIN_VALUE + 1L, 1L));
                    } else if ("caught-positive-overflow".equals(scenario)) {
                        System.out.println(subtractOrFallback(Long.MAX_VALUE, -1L, 41L));
                    } else if ("caught-negative-overflow".equals(scenario)) {
                        System.out.println(subtractOrFallback(Long.MIN_VALUE, 1L, -41L));
                    } else if ("caught-loaded-overflow".equals(scenario)) {
                        System.out.println(loadedOperands(Long.MAX_VALUE, -1L));
                    } else if ("order".equals(scenario)) {
                        System.out.println(Math.subtractExact(left(), right()));
                        System.out.println(order);
                    } else if ("uncaught-overflow".equals(scenario)) {
                        System.out.println(Math.subtractExact(Long.MAX_VALUE, -1L));
                    }
                }

                private static long subtractOrFallback(
                    final long left,
                    final long right,
                    final long fallback
                ) {
                    try {
                        return Math.subtractExact(left, right);
                    } catch (final ArithmeticException ignored) {
                        return fallback;
                    }
                }

                private static long loadedOperands(final long left, final long right) {
                    try {
                        return Math.subtractExact(left, right);
                    } catch (final ArithmeticException ignored) {
                        return 42L;
                    }
                }

                private static long left() {
                    order = order * 10 + 1;
                    return 9L;
                }

                private static long right() {
                    order = order * 10 + 2;
                    return 4L;
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/math-subtract-exact-long-probe");
    }

    @Test
    void positiveDifferenceMatchesJvm() {
        assertThat(nativeRun("positive")).isEqualTo(jvmRun("positive"));
    }

    @Test
    void negativeDifferenceMatchesJvm() {
        assertThat(nativeRun("negative")).isEqualTo(jvmRun("negative"));
    }

    @Test
    void mixedSignDifferenceMatchesJvm() {
        assertThat(nativeRun("mixed-sign")).isEqualTo(jvmRun("mixed-sign"));
    }

    @Test
    void zeroDifferenceMatchesJvm() {
        assertThat(nativeRun("zero")).isEqualTo(jvmRun("zero"));
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
    void positiveSafeThresholdMatchesJvm() {
        assertThat(nativeRun("positive-threshold")).isEqualTo(jvmRun("positive-threshold"));
    }

    @Test
    void negativeSafeThresholdMatchesJvm() {
        assertThat(nativeRun("negative-threshold")).isEqualTo(jvmRun("negative-threshold"));
    }

    @Test
    void positiveOverflowCanBeCaught() {
        assertThat(nativeRun("caught-positive-overflow")).isEqualTo(jvmRun("caught-positive-overflow"));
    }

    @Test
    void negativeOverflowCanBeCaught() {
        assertThat(nativeRun("caught-negative-overflow")).isEqualTo(jvmRun("caught-negative-overflow"));
    }

    @Test
    void loadedOperandOverflowCanBeCaught() {
        assertThat(nativeRun("caught-loaded-overflow")).isEqualTo(jvmRun("caught-loaded-overflow"));
    }

    @Test
    void argumentsEvaluateLeftToRightOnce() {
        assertThat(nativeRun("order")).isEqualTo(jvmRun("order"));
    }

    @Test
    void uncaughtOverflowFailsAtNativeBoundary() {
        assertThat(nativeRun("uncaught-overflow").exitCode()).isNotZero();
    }

    @Test
    void uncaughtOverflowProducesNoStandardOutput() {
        assertThat(nativeRun("uncaught-overflow").stdout()).isEmpty();
    }

    @Test
    void uncaughtOverflowNamesArithmeticException() {
        assertThat(nativeRun("uncaught-overflow").stderr()).contains("java/lang/ArithmeticException");
    }

    @Test
    void uncaughtOverflowNamesMessage() {
        assertThat(nativeRun("uncaught-overflow").stderr()).contains("long overflow");
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
}
