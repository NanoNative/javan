package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliFloatingRemainderIntegrationTest extends CliIntegrationSupport {
    @Test
    void preservesFloatAndDoubleRemainderForFiniteAndSpecialValues() throws Exception {
        final Path project = project("floating-remainder");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static float remainder(float left, float right) { return left % right; }
                static double remainder(double left, double right) { return left % right; }
                public static void main(String[] args) {
                    float[] floats = {7.0f, -7.0f, 0.0f, -0.0f, Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY, Float.NaN, Float.MIN_VALUE, Float.MAX_VALUE};
                    float[] floatDivisors = {3.0f, -3.0f, 0.0f, -0.0f, Float.POSITIVE_INFINITY,
                        Float.NaN, Float.MIN_VALUE};
                    for (float left : floats) for (float right : floatDivisors) {
                        float value = remainder(left, right);
                        if (value != value) System.out.println("nan");
                        else System.out.println(Float.floatToRawIntBits(value));
                    }
                    double[] doubles = {7.0, -7.0, 0.0, -0.0, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, Double.NaN, Double.MIN_VALUE, Double.MAX_VALUE};
                    double[] doubleDivisors = {3.0, -3.0, 0.0, -0.0, Double.POSITIVE_INFINITY,
                        Double.NaN, Double.MIN_VALUE};
                    for (double left : doubles) for (double right : doubleDivisors) {
                        double value = remainder(left, right);
                        if (value != value) System.out.println("nan");
                        else System.out.println(Double.doubleToRawLongBits(value));
                    }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-remainder").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
    @Test
    void evaluatesBothRemainderOperandsInJavaOrder() throws Exception {
        final Path project = project("floating-remainder-evaluation");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int trace;
                static float nextFloat(int id, float value) { trace = trace * 10 + id; return value; }
                static double nextDouble(int id, double value) { trace = trace * 10 + id; return value; }
                public static void main(String[] args) {
                    System.out.println(nextFloat(1, 7.0f) % nextFloat(2, 3.0f));
                    System.out.println(trace);
                    trace = 0;
                    System.out.println(nextDouble(1, -7.0) % nextDouble(2, 3.0));
                    System.out.println(trace);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-remainder-evaluation").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("1.0\n12\n-1.0\n12\n");
    }

    @Test
    void evaluatesRemainderInAProtectedRange() throws Exception {
        final Path project = project("floating-remainder-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static double value(boolean fail) {
                    if (fail) throw new IllegalStateException("failed");
                    return 7.0;
                }
                static void run(boolean fail) {
                    try { System.out.println(value(fail) % 3.0); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
                public static void main(String[] args) { run(false); run(true); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-remainder-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("1.0\ncaught:failed\n");
    }

}
