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
final class CliMathMultiplyExactLongIntIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("math-multiply-exact-long-int");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int trace;

                private Main() {
                }

                private static long left() {
                    trace = trace * 10 + 1;
                    return 6_000_000_000L;
                }

                private static int right() {
                    trace = trace * 10 + 2;
                    return 7;
                }

                private static long positiveOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE, 2);
                    } catch (final ArithmeticException ignored) {
                        return 41;
                    }
                }

                private static long negativeOverflow() {
                    try {
                        return Math.multiplyExact(Long.MIN_VALUE, -1);
                    } catch (final ArithmeticException ignored) {
                        return -41;
                    }
                }

                private static long positiveTimesNegativeOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE, -2);
                    } catch (final ArithmeticException ignored) {
                        return 42;
                    }
                }

                private static long negativeTimesPositiveOverflow() {
                    try {
                        return Math.multiplyExact(Long.MIN_VALUE, 2);
                    } catch (final ArithmeticException ignored) {
                        return -42;
                    }
                }

                private static long positiveThresholdOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE / 3 + 1, 3);
                    } catch (final ArithmeticException ignored) {
                        return 43;
                    }
                }

                private static long negativeThresholdOverflow() {
                    try {
                        return Math.multiplyExact(Long.MIN_VALUE / 3 - 1, 3);
                    } catch (final ArithmeticException ignored) {
                        return -43;
                    }
                }

                private static long minimumMultiplierOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE, Integer.MIN_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return 44;
                    }
                }

                private static long loadedOperands(final long left, final int right) {
                    try {
                        return Math.multiplyExact(left, right);
                    } catch (final ArithmeticException ignored) {
                        return 45;
                    }
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive" : args[0];
                    if ("negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(-3_000_000_000L, 7));
                    } else if ("positive-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(3_000_000_000L, -7));
                    } else if ("negative-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(-3_000_000_000L, -7));
                    } else if ("zero".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MIN_VALUE, 0));
                    } else if ("maximum".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MAX_VALUE, 1));
                    } else if ("minimum".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MIN_VALUE, 1));
                    } else if ("positive-threshold".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MAX_VALUE / 3, 3));
                    } else if ("negative-threshold".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MIN_VALUE / 3, 3));
                    } else if ("minimum-multiplier".equals(scenario)) {
                        System.out.println(Math.multiplyExact(1L, Integer.MIN_VALUE));
                    } else if ("order".equals(scenario)) {
                        System.out.println(Math.multiplyExact(left(), right()));
                        System.out.println(trace);
                    } else if ("caught-positive-overflow".equals(scenario)) {
                        System.out.println(positiveOverflow());
                    } else if ("caught-negative-overflow".equals(scenario)) {
                        System.out.println(negativeOverflow());
                    } else if ("caught-positive-negative-overflow".equals(scenario)) {
                        System.out.println(positiveTimesNegativeOverflow());
                    } else if ("caught-negative-positive-overflow".equals(scenario)) {
                        System.out.println(negativeTimesPositiveOverflow());
                    } else if ("caught-positive-threshold-overflow".equals(scenario)) {
                        System.out.println(positiveThresholdOverflow());
                    } else if ("caught-negative-threshold-overflow".equals(scenario)) {
                        System.out.println(negativeThresholdOverflow());
                    } else if ("caught-minimum-multiplier-overflow".equals(scenario)) {
                        System.out.println(minimumMultiplierOverflow());
                    } else if ("caught-loaded-overflow".equals(scenario)) {
                        System.out.println(loadedOperands(Long.MAX_VALUE, 2));
                    } else if ("uncaught-overflow".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MAX_VALUE, 2));
                    } else {
                        System.out.println(Math.multiplyExact(3_000_000_000L, 7));
                    }
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/math-multiply-exact-long-int");
    }

    @Test
    void positiveProductMatchesJvm() {
        assertThat(nativeRun("positive")).isEqualTo(jvmRun("positive"));
    }

    @Test
    void negativeProductMatchesJvm() {
        assertThat(nativeRun("negative")).isEqualTo(jvmRun("negative"));
    }

    @Test
    void positiveTimesNegativeProductMatchesJvm() {
        assertThat(nativeRun("positive-negative")).isEqualTo(jvmRun("positive-negative"));
    }

    @Test
    void negativeTimesNegativeProductMatchesJvm() {
        assertThat(nativeRun("negative-negative")).isEqualTo(jvmRun("negative-negative"));
    }

    @Test
    void zeroProductMatchesJvm() {
        assertThat(nativeRun("zero")).isEqualTo(jvmRun("zero"));
    }

    @Test
    void maximumSafeProductMatchesJvm() {
        assertThat(nativeRun("maximum")).isEqualTo(jvmRun("maximum"));
    }

    @Test
    void minimumSafeProductMatchesJvm() {
        assertThat(nativeRun("minimum")).isEqualTo(jvmRun("minimum"));
    }

    @Test
    void positiveThresholdProductMatchesJvm() {
        assertThat(nativeRun("positive-threshold")).isEqualTo(jvmRun("positive-threshold"));
    }

    @Test
    void negativeThresholdProductMatchesJvm() {
        assertThat(nativeRun("negative-threshold")).isEqualTo(jvmRun("negative-threshold"));
    }

    @Test
    void minimumIntMultiplierProductMatchesJvm() {
        assertThat(nativeRun("minimum-multiplier")).isEqualTo(jvmRun("minimum-multiplier"));
    }

    @Test
    void operandsAreEvaluatedOnceInOrder() {
        assertThat(nativeRun("order")).isEqualTo(jvmRun("order"));
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
    void positiveTimesNegativeOverflowCanBeCaught() {
        assertThat(nativeRun("caught-positive-negative-overflow"))
            .isEqualTo(jvmRun("caught-positive-negative-overflow"));
    }

    @Test
    void negativeTimesPositiveOverflowCanBeCaught() {
        assertThat(nativeRun("caught-negative-positive-overflow"))
            .isEqualTo(jvmRun("caught-negative-positive-overflow"));
    }

    @Test
    void positiveThresholdAdjacentOverflowCanBeCaught() {
        assertThat(nativeRun("caught-positive-threshold-overflow"))
            .isEqualTo(jvmRun("caught-positive-threshold-overflow"));
    }

    @Test
    void negativeThresholdAdjacentOverflowCanBeCaught() {
        assertThat(nativeRun("caught-negative-threshold-overflow"))
            .isEqualTo(jvmRun("caught-negative-threshold-overflow"));
    }

    @Test
    void minimumIntMultiplierOverflowCanBeCaught() {
        assertThat(nativeRun("caught-minimum-multiplier-overflow"))
            .isEqualTo(jvmRun("caught-minimum-multiplier-overflow"));
    }

    @Test
    void loadedOperandOverflowCanBeCaught() {
        assertThat(nativeRun("caught-loaded-overflow")).isEqualTo(jvmRun("caught-loaded-overflow"));
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
