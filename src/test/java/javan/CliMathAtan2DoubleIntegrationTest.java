package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliMathAtan2DoubleIntegrationTest extends CliIntegrationSupport {
    @Test
    void mathAtan2DoubleMatchesJvmInFirstQuadrant() throws Exception {
        assertMatchesJvm(
            "math-atan2-first-quadrant",
            "System.out.println(Math.atan2(1.0d, 1.0d) == 0.7853981633974483d);"
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmInSecondQuadrant() throws Exception {
        assertMatchesJvm(
            "math-atan2-second-quadrant",
            "System.out.println(Math.atan2(1.0d, -1.0d) == 2.356194490192345d);"
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmInThirdQuadrant() throws Exception {
        assertMatchesJvm(
            "math-atan2-third-quadrant",
            "System.out.println(Math.atan2(-1.0d, -1.0d) == -2.356194490192345d);"
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmInFourthQuadrant() throws Exception {
        assertMatchesJvm(
            "math-atan2-fourth-quadrant",
            "System.out.println(Math.atan2(-1.0d, 1.0d) == -0.7853981633974483d);"
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmWhenYIsNan() throws Exception {
        assertMatchesJvm(
            "math-atan2-y-nan",
            """
            final double nan = Double.longBitsToDouble(9221120237041090560L);
            final double result = Math.atan2(nan, 1.0d);
            System.out.println(result != result);
            """
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmWhenXIsNan() throws Exception {
        assertMatchesJvm(
            "math-atan2-x-nan",
            """
            final double nan = Double.longBitsToDouble(9221120237041090560L);
            final double result = Math.atan2(1.0d, nan);
            System.out.println(result != result);
            """
        );
    }

    @Test
    void mathAtan2DoublePreservesSignedZeroForPositiveX() throws Exception {
        assertMatchesJvm(
            "math-atan2-signed-zero-positive-x",
            """
            System.out.println(1.0d / Math.atan2(0.0d, 2.0d));
            System.out.println(1.0d / Math.atan2(-0.0d, 2.0d));
            """
        );
    }

    @Test
    void mathAtan2DoublePreservesSignedPiForNegativeX() throws Exception {
        assertMatchesJvm(
            "math-atan2-signed-pi-negative-x",
            """
            System.out.println(Math.atan2(0.0d, -2.0d) == 3.141592653589793d);
            System.out.println(Math.atan2(-0.0d, -2.0d) == -3.141592653589793d);
            """
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmForFiniteYAndSignedZeroX() throws Exception {
        assertMatchesJvm(
            "math-atan2-finite-y-signed-zero-x",
            """
            System.out.println(Math.atan2(2.0d, 0.0d));
            System.out.println(Math.atan2(2.0d, -0.0d));
            System.out.println(Math.atan2(-2.0d, 0.0d));
            System.out.println(Math.atan2(-2.0d, -0.0d));
            """
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmForInfiniteYAndFiniteX() throws Exception {
        assertMatchesJvm(
            "math-atan2-infinite-y-finite-x",
            """
            final double positiveInfinity = Double.longBitsToDouble(9218868437227405312L);
            final double negativeInfinity = Double.longBitsToDouble(-4503599627370496L);
            System.out.println(Math.atan2(positiveInfinity, 2.0d));
            System.out.println(Math.atan2(negativeInfinity, -2.0d));
            """
        );
    }

    @Test
    void mathAtan2DoublePreservesSignedZeroForPositiveInfiniteX() throws Exception {
        assertMatchesJvm(
            "math-atan2-finite-y-positive-infinite-x",
            """
            final double positiveInfinity = Double.longBitsToDouble(9218868437227405312L);
            System.out.println(1.0d / Math.atan2(2.0d, positiveInfinity));
            System.out.println(1.0d / Math.atan2(-2.0d, positiveInfinity));
            """
        );
    }

    @Test
    void mathAtan2DoublePreservesSignedPiForNegativeInfiniteX() throws Exception {
        assertMatchesJvm(
            "math-atan2-finite-y-negative-infinite-x",
            """
            final double negativeInfinity = Double.longBitsToDouble(-4503599627370496L);
            System.out.println(Math.atan2(2.0d, negativeInfinity) == 3.141592653589793d);
            System.out.println(Math.atan2(-2.0d, negativeInfinity) == -3.141592653589793d);
            """
        );
    }

    @Test
    void mathAtan2DoubleMatchesJvmForAllInfinitePairs() throws Exception {
        assertMatchesJvm(
            "math-atan2-infinite-pairs",
            """
            final double positiveInfinity = Double.longBitsToDouble(9218868437227405312L);
            final double negativeInfinity = Double.longBitsToDouble(-4503599627370496L);
            System.out.println(Math.atan2(positiveInfinity, positiveInfinity) == 0.7853981633974483d);
            System.out.println(Math.atan2(positiveInfinity, negativeInfinity) == 2.356194490192345d);
            System.out.println(Math.atan2(negativeInfinity, positiveInfinity) == -0.7853981633974483d);
            System.out.println(Math.atan2(negativeInfinity, negativeInfinity) == -2.356194490192345d);
            """
        );
    }

    @Test
    void mathAtan2DoubleEvaluatesYThenXExactlyOnce() throws Exception {
        assertMatchesJvm(
            "math-atan2-evaluation-order",
            "System.out.println((Math.atan2(y(), x()) == 0.7853981633974483d) + \"/\" + order);",
            """
            private static int order;

            private static double y() {
                order = order * 10 + 1;
                return 1.0d;
            }

            private static double x() {
                order = order * 10 + 2;
                return 1.0d;
            }
            """
        );
    }

    private void assertMatchesJvm(final String projectName, final String body) throws Exception {
        assertMatchesJvm(projectName, body, "");
    }

    private void assertMatchesJvm(final String projectName, final String body, final String members) throws Exception {
        final OutputPair output = compileAndRun(projectName, body, members);

        assertThat(output.nativeOutput()).as(output.stderr()).isEqualTo(output.jvmOutput());
    }

    private OutputPair compileAndRun(final String projectName, final String body, final String members) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source(body, members));
        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin").resolve(projectName).toString())).stdout()
            : run.stderr();
        return new OutputPair(jvmOutput, nativeOutput, run.stderr());
    }

    private static String source(final String body, final String members) {
        return """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }

                %s
            }
            """.formatted(body, members);
    }

    private record OutputPair(String jvmOutput, String nativeOutput, String stderr) {
    }
}
