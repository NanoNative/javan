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
    void floatMinReturnsLeftNanRawBitsFromNativeLibrary() throws Exception {
        assertFloatRawBits(
            "float-min-left-nan-bits",
            "Math.min(Float.intBitsToFloat(0xffc01234), 7.0f)",
            "ffc01234\n"
        );
    }

    @Test
    void floatMinReturnsRightNanRawBitsFromNativeLibrary() throws Exception {
        assertFloatRawBits(
            "float-min-right-nan-bits",
            "Math.min(7.0f, Float.intBitsToFloat(0x7fc05678))",
            "7fc05678\n"
        );
    }

    @Test
    void floatMaxReturnsLeftNanRawBitsFromNativeLibrary() throws Exception {
        assertFloatRawBits(
            "float-max-left-nan-bits",
            "Math.max(Float.intBitsToFloat(0xffc01234), 7.0f)",
            "ffc01234\n"
        );
    }

    @Test
    void floatMaxReturnsRightNanRawBitsFromNativeLibrary() throws Exception {
        assertFloatRawBits(
            "float-max-right-nan-bits",
            "Math.max(7.0f, Float.intBitsToFloat(0x7fc05678))",
            "7fc05678\n"
        );
    }

    @Test
    void floatMinReturnsLeftSignalingNanWhenBothOperandsAreNan() throws Exception {
        assertFloatRawBits(
            "float-min-both-nan-left-precedence",
            "Math.min(Float.intBitsToFloat(0xff801234), Float.intBitsToFloat(0x7f805678))",
            "ff801234\n"
        );
    }

    @Test
    void floatMaxReturnsLeftSignalingNanWhenBothOperandsAreNan() throws Exception {
        assertFloatRawBits(
            "float-max-both-nan-left-precedence",
            "Math.max(Float.intBitsToFloat(0x7f802468), Float.intBitsToFloat(0xff8068ac))",
            "7f802468\n"
        );
    }

    @Test
    void doubleMinReturnsLeftNanRawBitsFromNativeLibrary() throws Exception {
        assertDoubleRawBits(
            "double-min-left-nan-bits",
            "Math.min(Double.longBitsToDouble(0xfff8000000001234L), 7.0d)",
            "fff8000000001234\n"
        );
    }

    @Test
    void doubleMinReturnsRightNanRawBitsFromNativeLibrary() throws Exception {
        assertDoubleRawBits(
            "double-min-right-nan-bits",
            "Math.min(7.0d, Double.longBitsToDouble(0x7ff8000000005678L))",
            "7ff8000000005678\n"
        );
    }

    @Test
    void doubleMaxReturnsLeftNanRawBitsFromNativeLibrary() throws Exception {
        assertDoubleRawBits(
            "double-max-left-nan-bits",
            "Math.max(Double.longBitsToDouble(0xfff8000000001234L), 7.0d)",
            "fff8000000001234\n"
        );
    }

    @Test
    void doubleMaxReturnsRightNanRawBitsFromNativeLibrary() throws Exception {
        assertDoubleRawBits(
            "double-max-right-nan-bits",
            "Math.max(7.0d, Double.longBitsToDouble(0x7ff8000000005678L))",
            "7ff8000000005678\n"
        );
    }

    @Test
    void doubleMinReturnsLeftSignalingNanWhenBothOperandsAreNan() throws Exception {
        assertDoubleRawBits(
            "double-min-both-nan-left-precedence",
            "Math.min(Double.longBitsToDouble(0xfff0000000001234L), Double.longBitsToDouble(0x7ff0000000005678L))",
            "fff0000000001234\n"
        );
    }

    @Test
    void doubleMaxReturnsLeftSignalingNanWhenBothOperandsAreNan() throws Exception {
        assertDoubleRawBits(
            "double-max-both-nan-left-precedence",
            "Math.max(Double.longBitsToDouble(0x7ff0000000002468L), Double.longBitsToDouble(0xfff00000000068acL))",
            "7ff0000000002468\n"
        );
    }

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
    void floatMinOrdersInfinityBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("float-min-infinity", """
            float zero = 0.0f;
            System.out.println(Math.min(1.0f / zero, -1.0f / zero));
            """);
    }

    @Test
    void floatMaxOrdersInfinityBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("float-max-infinity", """
            float zero = 0.0f;
            System.out.println(Math.max(-1.0f / zero, 1.0f / zero));
            """);
    }

    @Test
    void doubleMinOrdersInfinityBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("double-min-infinity", """
            double zero = 0.0d;
            System.out.println(Math.min(1.0d / zero, -1.0d / zero));
            """);
    }

    @Test
    void doubleMaxOrdersInfinityBuildsAndMatchesJvmOutput() throws Exception {
        assertNativeOutputMatchesJvm("double-max-infinity", """
            double zero = 0.0d;
            System.out.println(Math.max(-1.0d / zero, 1.0d / zero));
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

    @Test
    void floatRoundRoundsBelowHalfDown() throws Exception {
        assertNativeOutputMatchesJvm("float-round-below-half", """
            System.out.println(Math.round(1.49f));
            """);
    }

    @Test
    void floatRoundRoundsPositiveHalfTowardPositiveInfinity() throws Exception {
        assertNativeOutputMatchesJvm("float-round-positive-half", """
            System.out.println(Math.round(1.5f));
            """);
    }

    @Test
    void floatRoundRoundsNegativeHalfTowardPositiveInfinity() throws Exception {
        assertNativeOutputMatchesJvm("float-round-negative-half", """
            System.out.println(Math.round(-1.5f));
            """);
    }

    @Test
    void floatRoundRoundsNegativeHalfToZero() throws Exception {
        assertNativeOutputMatchesJvm("float-round-negative-half-zero", """
            System.out.println(Math.round(-0.5f));
            """);
    }

    @Test
    void floatRoundRoundsNegativeZeroToZero() throws Exception {
        assertNativeOutputMatchesJvm("float-round-negative-zero", """
            System.out.println(Math.round(-0.0f));
            """);
    }

    @Test
    void floatRoundRoundsNanToZero() throws Exception {
        assertNativeOutputMatchesJvm("float-round-nan", """
            System.out.println(Math.round(Float.intBitsToFloat(0x7fc01234)));
            """);
    }

    @Test
    void floatRoundSaturatesPositiveInfinity() throws Exception {
        assertNativeOutputMatchesJvm("float-round-positive-infinity", """
            float zero = 0.0f;
            System.out.println(Math.round(1.0f / zero));
            """);
    }

    @Test
    void floatRoundSaturatesNegativeInfinity() throws Exception {
        assertNativeOutputMatchesJvm("float-round-negative-infinity", """
            float zero = 0.0f;
            System.out.println(Math.round(-1.0f / zero));
            """);
    }

    @Test
    void floatRoundSaturatesMaximumFiniteValue() throws Exception {
        assertNativeOutputMatchesJvm("float-round-maximum-finite", """
            System.out.println(Math.round(Float.MAX_VALUE));
            """);
    }

    @Test
    void floatRoundSaturatesMinimumFiniteValue() throws Exception {
        assertNativeOutputMatchesJvm("float-round-minimum-finite", """
            System.out.println(Math.round(-Float.MAX_VALUE));
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
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/" + projectName).toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    private void assertFloatRawBits(
        final String projectName,
        final String expression,
        final String expected
    ) throws Exception {
        assertRawBits(projectName, "float", "uint32_t", "%08llx", expression, expected);
    }

    private void assertDoubleRawBits(
        final String projectName,
        final String expression,
        final String expected
    ) throws Exception {
        assertRawBits(projectName, "double", "uint64_t", "%016llx", expression, expected);
    }

    private void assertRawBits(
        final String projectName,
        final String javaType,
        final String cBitsType,
        final String cFormat,
        final String expression,
        final String expected
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Probe", """
            package com.acme;

            public final class Probe {
                private Probe() {
                }

                public static %s value() {
                    return %s;
                }
            }
            """.formatted(javaType, expression));
        run(
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
        final Path caller = writeC(project, "read_bits.c", """
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>
            #include ".javan/dist/bindings/c/%s.h"

            int main(void) {
                %s value = javan_export_com_acme_Probe_value_void();
                %s bits = 0;
                memcpy(&bits, &value, sizeof(value));
                printf("%s\\n", (unsigned long long) bits);
                return 0;
            }
            """.formatted(projectName, javaType, cBitsType, cFormat));
        final Path binary = project.resolve("read-bits");
        process(project, List.of(
            "cc",
            caller.toString(),
            project.resolve(".javan/dist/lib" + projectName + ".a").toString(),
            "-o",
            binary.toString()
        ));

        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo(expected);
    }
}
