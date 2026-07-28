package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
final class CliFloatingMathMinMaxIntegrationTest extends CliIntegrationSupport {
    @Test
    void floatMinReturnsNanBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("float-min-nan", """
            float zero = 0.0f;
            System.out.println(Math.min(zero / zero, 7.0f));
            """);
    }

    @Test
    void floatMaxKeepsPositiveZeroBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("float-max-positive-zero", """
            System.out.println(1.0f / Math.max(-0.0f, 0.0f));
            """);
    }

    @Test
    void doubleMinKeepsNegativeZeroBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("double-min-negative-zero", """
            System.out.println(1.0d / Math.min(0.0d, -0.0d));
            """);
    }

    @Test
    void doubleMaxReturnsNanBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("double-max-nan", """
            double zero = 0.0d;
            System.out.println(Math.max(7.0d, zero / zero));
            """);
    }

    @Test
    void floatingMinMaxEvaluatesOperandsLeftToRightOnceBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("floating-min-max-order", """
            System.out.println(Math.min(nextFloat("L", 5.0f), nextFloat("R", 3.0f)));
            System.out.println();
            System.out.println(Math.max(nextDouble("L", 5.0d), nextDouble("R", 3.0d)));
            System.out.println();
            """, """
            private static float nextFloat(final String marker, final float value) {
                System.out.print(marker);
                return value;
            }

            private static double nextDouble(final String marker, final double value) {
                System.out.print(marker);
                return value;
            }
            """);
    }

    private void assertNativeOutputMatchesJvm(final String projectName, final String body) throws Exception {
        assertNativeOutputMatchesJvm(projectName, body, "");
    }

    private void assertNativeOutputMatchesJvm(final String projectName, final String body, final String methods) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }

                %s
            }
            """.formatted(body, methods));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        run(tempDir, "build", project.toString());

        assertThat(process(project, List.of(project.resolve(".javan/bin/" + projectName).toString())).stdout())
            .isEqualTo(jvmOutput);
    }
}
