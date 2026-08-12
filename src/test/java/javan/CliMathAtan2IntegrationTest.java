package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@NativeTest
final class CliMathAtan2IntegrationTest extends CliIntegrationSupport {
    @Test
    void atan2SpecialValuesAndQuadrantsBuildAndMatchJvmBehavior() throws Exception {
        assertNativeOutputMatchesJvm("math-atan2-semantics", """
            double positiveZero = Math.atan2(0.0d, 1.0d);
            double negativeZero = Math.atan2(-0.0d, 1.0d);
            System.out.println(1.0d / positiveZero);
            System.out.println(1.0d / negativeZero);
            System.out.println(Math.atan2(0.0d, -1.0d) > 3.14d);
            System.out.println(Math.atan2(-0.0d, -1.0d) < -3.14d);
            System.out.println(Math.abs(Math.atan2(1.0d, 1.0d) - 0.7853981633974483d) < 0.000000000000001d);
            System.out.println(Math.abs(Math.atan2(1.0d, -1.0d) - 2.356194490192345d) < 0.000000000000001d);
            System.out.println(Math.atan2(1.0d / 0.0d, 1.0d / 0.0d) > 0.78d);
            System.out.println(Math.atan2(0.0d / 0.0d, 1.0d));
            """);
    }

    @Test
    void atan2EvaluatesOperandsLeftToRightOnce() throws Exception {
        assertNativeOutputMatchesJvm("math-atan2-order", """
            System.out.println(Math.atan2(next("Y", 1.0d), next("X", 1.0d)) > 0.78d);
            """, """
            private static double next(final String marker, final double value) {
                System.out.print(marker);
                return value;
            }
            """);
    }

    @Test
    void atan2StaticLibraryLinksAndRunsFromC() throws Exception {
        final Path project = project("math-atan2-library");
        writeJava(project, "com.acme.Probe", """
            package com.acme;

            public final class Probe {
                private Probe() {
                }

                public static double value(final double y, final double x) {
                    return Math.atan2(y, x);
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
            "com.acme.Probe.value",
            "--bindings",
            "c"
        );
        assertThat(run.exitCode() + "\n" + run.stderr()).isEqualTo("0\n");

        final Path caller = writeC(project, "caller.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/math-atan2-library.h"

            int main(void) {
                printf("%.6f\\n", javan_export_com_acme_Probe_value_double_double(1.0, 1.0));
                return 0;
            }
            """);
        final Path binary = project.resolve("caller");
        final List<String> command = new java.util.ArrayList<>(List.of(
            "cc",
            caller.toString(),
            project.resolve(".javan/dist/libmath-atan2-library.a").toString(),
            "-o",
            binary.toString()
        ));
        if (System.getProperty("os.name", "").startsWith("Linux")) {
            command.add("-lm");
        }

        assertThat(process(project, command).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("0.785398\n");
    }

    private void assertNativeOutputMatchesJvm(final String projectName, final String body) throws Exception {
        assertNativeOutputMatchesJvm(projectName, body, "");
    }

    private void assertNativeOutputMatchesJvm(
        final String projectName,
        final String body,
        final String methods
    ) throws Exception {
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
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/" + projectName).toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }
}
