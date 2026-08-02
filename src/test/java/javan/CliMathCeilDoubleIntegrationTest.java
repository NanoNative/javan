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
final class CliMathCeilDoubleIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("generic-jvm-native-parity");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int calls;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive-fraction" : args[0];
                    if ("positive-fraction".equals(scenario)) {
                        System.out.println(Math.ceil(7.75d));
                    } else if ("negative-fraction".equals(scenario)) {
                        System.out.println(Math.ceil(-7.25d));
                    } else if ("positive-integral".equals(scenario)) {
                        System.out.println(Math.ceil(7.0d));
                    } else if ("negative-integral".equals(scenario)) {
                        System.out.println(Math.ceil(-7.0d));
                    } else if ("positive-float-origin".equals(scenario)) {
                        System.out.println(Math.ceil((double) 3.5f));
                    } else if ("negative-float-origin".equals(scenario)) {
                        System.out.println(Math.ceil((double) -3.5f));
                    } else if ("positive-zero".equals(scenario)) {
                        System.out.println(1.0d / Math.ceil(0.0d));
                    } else if ("negative-zero".equals(scenario)) {
                        System.out.println(1.0d / Math.ceil(-0.0d));
                    } else if ("positive-infinity".equals(scenario)) {
                        System.out.println(Math.ceil(
                            Double.longBitsToDouble(0x7ff0000000000000L)
                        ));
                    } else if ("negative-infinity".equals(scenario)) {
                        System.out.println(Math.ceil(
                            Double.longBitsToDouble(0xfff0000000000000L)
                        ));
                    } else if ("quiet-nan".equals(scenario)) {
                        final double result = Math.ceil(
                            Double.longBitsToDouble(0x7ff8000000001234L)
                        );
                        System.out.println(result != result);
                    } else if ("signaling-nan".equals(scenario)) {
                        final double result = Math.ceil(
                            Double.longBitsToDouble(0x7ff0000000001234L)
                        );
                        System.out.println(result != result);
                    } else if ("positive-subnormal".equals(scenario)) {
                        System.out.println(Math.ceil(
                            Double.longBitsToDouble(0x0000000000000001L)
                        ));
                    } else if ("negative-subnormal".equals(scenario)) {
                        System.out.println(1.0d / Math.ceil(
                            Double.longBitsToDouble(0x8000000000000001L)
                        ));
                    } else if ("positive-boundary".equals(scenario)) {
                        System.out.println(
                            Math.ceil(0x1.fffffffffffffp51) == 4503599627370496.0d
                        );
                    } else if ("negative-boundary".equals(scenario)) {
                        System.out.println(
                            Math.ceil(-0x1.fffffffffffffp51) == -4503599627370495.0d
                        );
                    } else if ("integral-exponent".equals(scenario)) {
                        System.out.println(Math.ceil(0x1.0p52) == 0x1.0p52);
                    } else if ("single-evaluation".equals(scenario)) {
                        System.out.println(Math.ceil(next()));
                        System.out.println(calls);
                    } else {
                        throw new IllegalArgumentException(scenario);
                    }
                }

                private static double next() {
                    calls++;
                    return -1.25d;
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/generic-jvm-native-parity");
    }

    @Test
    void positiveFractionMatchesJvm() {
        assertThat(nativeRun("positive-fraction")).isEqualTo(jvmRun("positive-fraction"));
    }

    @Test
    void negativeFractionMatchesJvm() {
        assertThat(nativeRun("negative-fraction")).isEqualTo(jvmRun("negative-fraction"));
    }

    @Test
    void positiveIntegralMatchesJvm() {
        assertThat(nativeRun("positive-integral")).isEqualTo(jvmRun("positive-integral"));
    }

    @Test
    void negativeIntegralMatchesJvm() {
        assertThat(nativeRun("negative-integral")).isEqualTo(jvmRun("negative-integral"));
    }

    @Test
    void positiveFloatOriginMatchesJvm() {
        assertThat(nativeRun("positive-float-origin")).isEqualTo(jvmRun("positive-float-origin"));
    }

    @Test
    void negativeFloatOriginMatchesJvm() {
        assertThat(nativeRun("negative-float-origin")).isEqualTo(jvmRun("negative-float-origin"));
    }

    @Test
    void positiveZeroSignMatchesJvm() {
        assertThat(nativeRun("positive-zero")).isEqualTo(jvmRun("positive-zero"));
    }

    @Test
    void negativeZeroSignMatchesJvm() {
        assertThat(nativeRun("negative-zero")).isEqualTo(jvmRun("negative-zero"));
    }

    @Test
    void positiveInfinityMatchesJvm() {
        assertThat(nativeRun("positive-infinity")).isEqualTo(jvmRun("positive-infinity"));
    }

    @Test
    void negativeInfinityMatchesJvm() {
        assertThat(nativeRun("negative-infinity")).isEqualTo(jvmRun("negative-infinity"));
    }

    @Test
    void quietNanRemainsNan() {
        assertThat(nativeRun("quiet-nan")).isEqualTo(jvmRun("quiet-nan"));
    }

    @Test
    void signalingNanRemainsNan() {
        assertThat(nativeRun("signaling-nan")).isEqualTo(jvmRun("signaling-nan"));
    }

    @Test
    void positiveMinimumSubnormalRoundsToOne() {
        assertThat(nativeRun("positive-subnormal")).isEqualTo(jvmRun("positive-subnormal"));
    }

    @Test
    void negativeMinimumSubnormalRoundsToNegativeZero() {
        assertThat(nativeRun("negative-subnormal")).isEqualTo(jvmRun("negative-subnormal"));
    }

    @Test
    void positiveTwoToTheFiftyTwoBoundaryMatchesJvm() {
        assertThat(nativeRun("positive-boundary")).isEqualTo(jvmRun("positive-boundary"));
    }

    @Test
    void negativeTwoToTheFiftyTwoBoundaryMatchesJvm() {
        assertThat(nativeRun("negative-boundary")).isEqualTo(jvmRun("negative-boundary"));
    }

    @Test
    void integralExponentMatchesJvm() {
        assertThat(nativeRun("integral-exponent")).isEqualTo(jvmRun("integral-exponent"));
    }

    @Test
    void argumentIsEvaluatedOnce() {
        assertThat(nativeRun("single-evaluation")).isEqualTo(jvmRun("single-evaluation"));
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
