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
final class CliMathMultiplyExactIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("math-multiply-exact");
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

                private static int intLeft() {
                    trace = trace * 10 + 1;
                    return 60_000;
                }

                private static int intRight() {
                    trace = trace * 10 + 2;
                    return 7;
                }

                private static long longLeft() {
                    trace = trace * 10 + 1;
                    return 2_000_000_000L;
                }

                private static long longRight() {
                    trace = trace * 10 + 2;
                    return 3_000_000_000L;
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

                private static int intPositiveOverflow() {
                    try {
                        return Math.multiplyExact(Integer.MAX_VALUE, 2);
                    } catch (final ArithmeticException ignored) {
                        return 61;
                    }
                }

                private static int intNegativeOverflow() {
                    try {
                        return Math.multiplyExact(Integer.MIN_VALUE, -1);
                    } catch (final ArithmeticException ignored) {
                        return -61;
                    }
                }

                private static int intPositiveTimesNegativeOverflow() {
                    try {
                        return Math.multiplyExact(Integer.MAX_VALUE, -2);
                    } catch (final ArithmeticException ignored) {
                        return 62;
                    }
                }

                private static int intNegativeTimesPositiveOverflow() {
                    try {
                        return Math.multiplyExact(Integer.MIN_VALUE, 2);
                    } catch (final ArithmeticException ignored) {
                        return -62;
                    }
                }

                private static int intPositiveThresholdOverflow() {
                    try {
                        return Math.multiplyExact(Integer.MAX_VALUE / 3 + 1, 3);
                    } catch (final ArithmeticException ignored) {
                        return 63;
                    }
                }

                private static int intNegativeThresholdOverflow() {
                    try {
                        return Math.multiplyExact(Integer.MIN_VALUE / 3 - 1, 3);
                    } catch (final ArithmeticException ignored) {
                        return -63;
                    }
                }

                private static int loadedIntOperands(final int left, final int right) {
                    try {
                        return Math.multiplyExact(left, right);
                    } catch (final ArithmeticException ignored) {
                        return 64;
                    }
                }

                private static long longLongPositiveOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE, 2L);
                    } catch (final ArithmeticException ignored) {
                        return 51;
                    }
                }

                private static long longLongNegativeOverflow() {
                    try {
                        return Math.multiplyExact(Long.MIN_VALUE, -1L);
                    } catch (final ArithmeticException ignored) {
                        return -51;
                    }
                }

                private static long longLongPositiveTimesNegativeOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE, -2L);
                    } catch (final ArithmeticException ignored) {
                        return 52;
                    }
                }

                private static long longLongNegativeTimesPositiveOverflow() {
                    try {
                        return Math.multiplyExact(Long.MIN_VALUE, 2L);
                    } catch (final ArithmeticException ignored) {
                        return -52;
                    }
                }

                private static long longLongPositiveThresholdOverflow() {
                    try {
                        return Math.multiplyExact(Long.MAX_VALUE / 3L + 1L, 3L);
                    } catch (final ArithmeticException ignored) {
                        return 53;
                    }
                }

                private static long longLongNegativeThresholdOverflow() {
                    try {
                        return Math.multiplyExact(Long.MIN_VALUE / 3L - 1L, 3L);
                    } catch (final ArithmeticException ignored) {
                        return -53;
                    }
                }

                private static long longLongMinimumMultiplierOverflow() {
                    try {
                        return Math.multiplyExact(-1L, Long.MIN_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return 54;
                    }
                }

                private static long loadedLongOperands(final long left, final long right) {
                    try {
                        return Math.multiplyExact(left, right);
                    } catch (final ArithmeticException ignored) {
                        return 55;
                    }
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive" : args[0];
                    if ("int-positive".equals(scenario)) {
                        System.out.println(Math.multiplyExact(60_000, 7));
                    } else if ("int-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(-60_000, 7));
                    } else if ("int-positive-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(60_000, -7));
                    } else if ("int-negative-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(-60_000, -7));
                    } else if ("int-zero".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Integer.MIN_VALUE, 0));
                    } else if ("int-maximum".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Integer.MAX_VALUE, 1));
                    } else if ("int-minimum".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Integer.MIN_VALUE, 1));
                    } else if ("int-positive-threshold".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Integer.MAX_VALUE / 3, 3));
                    } else if ("int-negative-threshold".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Integer.MIN_VALUE / 3, 3));
                    } else if ("int-order".equals(scenario)) {
                        System.out.println(Math.multiplyExact(intLeft(), intRight()));
                        System.out.println(trace);
                    } else if ("int-caught-positive-overflow".equals(scenario)) {
                        System.out.println(intPositiveOverflow());
                    } else if ("int-caught-negative-overflow".equals(scenario)) {
                        System.out.println(intNegativeOverflow());
                    } else if ("int-caught-positive-negative-overflow".equals(scenario)) {
                        System.out.println(intPositiveTimesNegativeOverflow());
                    } else if ("int-caught-negative-positive-overflow".equals(scenario)) {
                        System.out.println(intNegativeTimesPositiveOverflow());
                    } else if ("int-caught-positive-threshold-overflow".equals(scenario)) {
                        System.out.println(intPositiveThresholdOverflow());
                    } else if ("int-caught-negative-threshold-overflow".equals(scenario)) {
                        System.out.println(intNegativeThresholdOverflow());
                    } else if ("int-caught-loaded-overflow".equals(scenario)) {
                        System.out.println(loadedIntOperands(Integer.MAX_VALUE, 2));
                    } else if ("int-uncaught-overflow".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Integer.MAX_VALUE, 2));
                    } else if ("negative".equals(scenario)) {
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
                    } else if ("long-long-positive".equals(scenario)) {
                        System.out.println(Math.multiplyExact(2_000_000_000L, 3_000_000_000L));
                    } else if ("long-long-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(-2_000_000_000L, 3_000_000_000L));
                    } else if ("long-long-positive-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(2_000_000_000L, -3_000_000_000L));
                    } else if ("long-long-negative-negative".equals(scenario)) {
                        System.out.println(Math.multiplyExact(-2_000_000_000L, -3_000_000_000L));
                    } else if ("long-long-zero".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MIN_VALUE, 0L));
                    } else if ("long-long-positive-threshold".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MAX_VALUE / 3L, 3L));
                    } else if ("long-long-negative-threshold".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MIN_VALUE / 3L, 3L));
                    } else if ("long-long-minimum-multiplier".equals(scenario)) {
                        System.out.println(Math.multiplyExact(1L, Long.MIN_VALUE));
                    } else if ("long-long-order".equals(scenario)) {
                        System.out.println(Math.multiplyExact(longLeft(), longRight()));
                        System.out.println(trace);
                    } else if ("long-long-caught-positive-overflow".equals(scenario)) {
                        System.out.println(longLongPositiveOverflow());
                    } else if ("long-long-caught-negative-overflow".equals(scenario)) {
                        System.out.println(longLongNegativeOverflow());
                    } else if ("long-long-caught-positive-negative-overflow".equals(scenario)) {
                        System.out.println(longLongPositiveTimesNegativeOverflow());
                    } else if ("long-long-caught-negative-positive-overflow".equals(scenario)) {
                        System.out.println(longLongNegativeTimesPositiveOverflow());
                    } else if ("long-long-caught-positive-threshold-overflow".equals(scenario)) {
                        System.out.println(longLongPositiveThresholdOverflow());
                    } else if ("long-long-caught-negative-threshold-overflow".equals(scenario)) {
                        System.out.println(longLongNegativeThresholdOverflow());
                    } else if ("long-long-caught-minimum-multiplier-overflow".equals(scenario)) {
                        System.out.println(longLongMinimumMultiplierOverflow());
                    } else if ("long-long-caught-loaded-overflow".equals(scenario)) {
                        System.out.println(loadedLongOperands(Long.MAX_VALUE, 2L));
                    } else if ("long-long-uncaught-overflow".equals(scenario)) {
                        System.out.println(Math.multiplyExact(Long.MAX_VALUE, 2L));
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
        binary = project.resolve(".javan/bin/math-multiply-exact");
    }

    @Test
    void intPositiveProductMatchesJvm() {
        assertThat(nativeRun("int-positive")).isEqualTo(jvmRun("int-positive"));
    }

    @Test
    void intNegativeProductMatchesJvm() {
        assertThat(nativeRun("int-negative")).isEqualTo(jvmRun("int-negative"));
    }

    @Test
    void intPositiveTimesNegativeProductMatchesJvm() {
        assertThat(nativeRun("int-positive-negative")).isEqualTo(jvmRun("int-positive-negative"));
    }

    @Test
    void intNegativeTimesNegativeProductMatchesJvm() {
        assertThat(nativeRun("int-negative-negative")).isEqualTo(jvmRun("int-negative-negative"));
    }

    @Test
    void intZeroProductMatchesJvm() {
        assertThat(nativeRun("int-zero")).isEqualTo(jvmRun("int-zero"));
    }

    @Test
    void intMaximumSafeProductMatchesJvm() {
        assertThat(nativeRun("int-maximum")).isEqualTo(jvmRun("int-maximum"));
    }

    @Test
    void intMinimumSafeProductMatchesJvm() {
        assertThat(nativeRun("int-minimum")).isEqualTo(jvmRun("int-minimum"));
    }

    @Test
    void intPositiveThresholdProductMatchesJvm() {
        assertThat(nativeRun("int-positive-threshold")).isEqualTo(jvmRun("int-positive-threshold"));
    }

    @Test
    void intNegativeThresholdProductMatchesJvm() {
        assertThat(nativeRun("int-negative-threshold")).isEqualTo(jvmRun("int-negative-threshold"));
    }

    @Test
    void intOperandsAreEvaluatedOnceInOrder() {
        assertThat(nativeRun("int-order")).isEqualTo(jvmRun("int-order"));
    }

    @Test
    void intPositiveOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-positive-overflow")).isEqualTo(jvmRun("int-caught-positive-overflow"));
    }

    @Test
    void intNegativeOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-negative-overflow")).isEqualTo(jvmRun("int-caught-negative-overflow"));
    }

    @Test
    void intPositiveTimesNegativeOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-positive-negative-overflow"))
            .isEqualTo(jvmRun("int-caught-positive-negative-overflow"));
    }

    @Test
    void intNegativeTimesPositiveOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-negative-positive-overflow"))
            .isEqualTo(jvmRun("int-caught-negative-positive-overflow"));
    }

    @Test
    void intPositiveThresholdAdjacentOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-positive-threshold-overflow"))
            .isEqualTo(jvmRun("int-caught-positive-threshold-overflow"));
    }

    @Test
    void intNegativeThresholdAdjacentOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-negative-threshold-overflow"))
            .isEqualTo(jvmRun("int-caught-negative-threshold-overflow"));
    }

    @Test
    void loadedIntOperandOverflowCanBeCaught() {
        assertThat(nativeRun("int-caught-loaded-overflow")).isEqualTo(jvmRun("int-caught-loaded-overflow"));
    }

    @Test
    void uncaughtIntOverflowFailsAtNativeBoundary() {
        assertThat(nativeRun("int-uncaught-overflow").exitCode()).isNotZero();
    }

    @Test
    void uncaughtIntOverflowProducesNoStandardOutput() {
        assertThat(nativeRun("int-uncaught-overflow").stdout()).isEmpty();
    }

    @Test
    void uncaughtIntOverflowNamesArithmeticException() {
        assertThat(nativeRun("int-uncaught-overflow").stderr()).contains("java/lang/ArithmeticException");
    }

    @Test
    void uncaughtIntOverflowNamesMessage() {
        assertThat(nativeRun("int-uncaught-overflow").stderr()).contains("integer overflow");
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
    void longLongPositiveProductMatchesJvm() {
        assertThat(nativeRun("long-long-positive")).isEqualTo(jvmRun("long-long-positive"));
    }

    @Test
    void longLongNegativeProductMatchesJvm() {
        assertThat(nativeRun("long-long-negative")).isEqualTo(jvmRun("long-long-negative"));
    }

    @Test
    void longLongPositiveTimesNegativeProductMatchesJvm() {
        assertThat(nativeRun("long-long-positive-negative")).isEqualTo(jvmRun("long-long-positive-negative"));
    }

    @Test
    void longLongNegativeTimesNegativeProductMatchesJvm() {
        assertThat(nativeRun("long-long-negative-negative")).isEqualTo(jvmRun("long-long-negative-negative"));
    }

    @Test
    void longLongZeroProductMatchesJvm() {
        assertThat(nativeRun("long-long-zero")).isEqualTo(jvmRun("long-long-zero"));
    }

    @Test
    void longLongPositiveThresholdProductMatchesJvm() {
        assertThat(nativeRun("long-long-positive-threshold")).isEqualTo(jvmRun("long-long-positive-threshold"));
    }

    @Test
    void longLongNegativeThresholdProductMatchesJvm() {
        assertThat(nativeRun("long-long-negative-threshold")).isEqualTo(jvmRun("long-long-negative-threshold"));
    }

    @Test
    void longLongMinimumMultiplierProductMatchesJvm() {
        assertThat(nativeRun("long-long-minimum-multiplier")).isEqualTo(jvmRun("long-long-minimum-multiplier"));
    }

    @Test
    void longLongOperandsAreEvaluatedOnceInOrder() {
        assertThat(nativeRun("long-long-order")).isEqualTo(jvmRun("long-long-order"));
    }

    @Test
    void longLongPositiveOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-positive-overflow"))
            .isEqualTo(jvmRun("long-long-caught-positive-overflow"));
    }

    @Test
    void longLongNegativeOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-negative-overflow"))
            .isEqualTo(jvmRun("long-long-caught-negative-overflow"));
    }

    @Test
    void longLongPositiveTimesNegativeOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-positive-negative-overflow"))
            .isEqualTo(jvmRun("long-long-caught-positive-negative-overflow"));
    }

    @Test
    void longLongNegativeTimesPositiveOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-negative-positive-overflow"))
            .isEqualTo(jvmRun("long-long-caught-negative-positive-overflow"));
    }

    @Test
    void longLongPositiveThresholdAdjacentOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-positive-threshold-overflow"))
            .isEqualTo(jvmRun("long-long-caught-positive-threshold-overflow"));
    }

    @Test
    void longLongNegativeThresholdAdjacentOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-negative-threshold-overflow"))
            .isEqualTo(jvmRun("long-long-caught-negative-threshold-overflow"));
    }

    @Test
    void longLongMinimumMultiplierOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-minimum-multiplier-overflow"))
            .isEqualTo(jvmRun("long-long-caught-minimum-multiplier-overflow"));
    }

    @Test
    void loadedLongOperandsOverflowCanBeCaught() {
        assertThat(nativeRun("long-long-caught-loaded-overflow"))
            .isEqualTo(jvmRun("long-long-caught-loaded-overflow"));
    }

    @Test
    void uncaughtLongLongOverflowFailsAtNativeBoundary() {
        assertThat(nativeRun("long-long-uncaught-overflow").exitCode()).isNotZero();
    }

    @Test
    void uncaughtLongLongOverflowProducesNoStandardOutput() {
        assertThat(nativeRun("long-long-uncaught-overflow").stdout()).isEmpty();
    }

    @Test
    void uncaughtLongLongOverflowNamesArithmeticException() {
        assertThat(nativeRun("long-long-uncaught-overflow").stderr()).contains("java/lang/ArithmeticException");
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
