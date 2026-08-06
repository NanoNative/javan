package javan;

import javan.testing.TestSuite.NativeTest;

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
@NativeTest
final class CliFloatToDoubleIntegrationTest extends CliIntegrationSupport {
    @Test
    void genericFloatToDoubleConversionMatchesJvm() throws Exception {
        final Path project = project("float-to-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    print(1.5f);
                    print(-2.25f);
                    print(Float.MIN_VALUE);
                    print(-Float.MIN_VALUE);
                    print(Float.MAX_VALUE);
                    print(0.0f);
                    print(-0.0f);
                    print(Float.intBitsToFloat(0x7f800000));
                    print(Float.intBitsToFloat(0xff800000));
                    print(Float.intBitsToFloat(0x7fc00000));
                }

                private static void print(final float value) {
                    System.out.println((double) value);
                }
            }
            """);

        final List<DoubleObservation> jvmOutput = observations(runJvm(project, "com.acme.Main"));
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/float-to-double").toString()))
            : new ProcessResult(-1, "", build.stderr());

        assertThat(new FloatToDoubleParity(
            build.exitCode(),
            nativeRun.exitCode(),
            nativeRun.stderr(),
            observations(nativeRun.stdout()),
            jvmOutput
        ))
            .as(build.stderr())
            .isEqualTo(new FloatToDoubleParity(0, 0, "", jvmOutput, jvmOutput));
    }

    private static List<DoubleObservation> observations(final String output) {
        return output.lines()
            .map(Double::parseDouble)
            .map(value -> Double.isNaN(value)
                ? new DoubleObservation(true, 0L)
                : new DoubleObservation(false, Double.doubleToRawLongBits(value)))
            .toList();
    }

    private record FloatToDoubleParity(
        int buildExitCode,
        int nativeExitCode,
        String nativeStderr,
        List<DoubleObservation> nativeOutput,
        List<DoubleObservation> jvmOutput
    ) {
    }

    private record DoubleObservation(boolean nan, long rawBits) {
    }
}
