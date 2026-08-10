package javan;

import javan.testing.TestSuite.NativeTest;

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
@NativeTest
final class CliMathFloorDoubleIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("math-floor-double-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int calls;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive-fraction" : args[0];
                    if ("positive-fraction".equals(scenario)) {
                        System.out.println(Math.floor(7.75d));
                    } else if ("negative-fraction".equals(scenario)) {
                        System.out.println(Math.floor(-7.25d));
                    } else if ("integral".equals(scenario)) {
                        System.out.println(Math.floor(7.0d));
                    } else if ("positive-zero".equals(scenario)) {
                        System.out.println(1.0d / Math.floor(0.0d));
                    } else if ("negative-zero".equals(scenario)) {
                        System.out.println(1.0d / Math.floor(-0.0d));
                    } else if ("positive-infinity".equals(scenario)) {
                        System.out.println(Math.floor(
                            Double.longBitsToDouble(0x7ff0000000000000L)
                        ));
                    } else if ("negative-infinity".equals(scenario)) {
                        System.out.println(Math.floor(
                            Double.longBitsToDouble(0xfff0000000000000L)
                        ));
                    } else if ("quiet-nan".equals(scenario)) {
                        final double result = Math.floor(
                            Double.longBitsToDouble(0x7ff8000000001234L)
                        );
                        System.out.println(result != result);
                    } else if ("signaling-nan".equals(scenario)) {
                        final double result = Math.floor(
                            Double.longBitsToDouble(0x7ff0000000001234L)
                        );
                        System.out.println(result != result);
                    } else if ("positive-subnormal".equals(scenario)) {
                        final double result = Math.floor(
                            Double.longBitsToDouble(0x0000000000000001L)
                        );
                        System.out.println(1.0d / result);
                    } else if ("negative-subnormal".equals(scenario)) {
                        final double result = Math.floor(
                            Double.longBitsToDouble(0x8000000000000001L)
                        );
                        System.out.println(result == -1.0d);
                    } else if ("positive-boundary".equals(scenario)) {
                        System.out.println(
                            Math.floor(0x1.fffffffffffffp51) == 4503599627370495.0d
                        );
                    } else if ("negative-boundary".equals(scenario)) {
                        System.out.println(
                            Math.floor(-0x1.fffffffffffffp51) == -4503599627370496.0d
                        );
                    } else if ("single-evaluation".equals(scenario)) {
                        System.out.println(Math.floor(next()));
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
        binary = project.resolve(".javan/bin/math-floor-double-probe");
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
    void integralValueMatchesJvm() {
        assertThat(nativeRun("integral")).isEqualTo(jvmRun("integral"));
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
    void positiveSubnormalRoundsToPositiveZero() {
        assertThat(nativeRun("positive-subnormal")).isEqualTo(jvmRun("positive-subnormal"));
    }

    @Test
    void negativeSubnormalRoundsToNegativeOne() {
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
