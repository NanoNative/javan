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

import java.nio.file.Files;
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
final class CliDoubleToIntIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("double-to-int-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static final long DOUBLE_IMMEDIATELY_BELOW_INTEGER_MAX_BITS = 0x41dfffffffbfffffL;
                private static final long DOUBLE_IMMEDIATELY_ABOVE_INTEGER_MAX_BITS = 0x41dfffffffc00001L;
                private static final long DOUBLE_IMMEDIATELY_BELOW_INTEGER_MIN_BITS = 0xc1e0000000000001L;
                private static final long DOUBLE_IMMEDIATELY_ABOVE_INTEGER_MIN_BITS = 0xc1dfffffffffffffL;
                private static double field = -12.75d;
                private static int calls;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive-fraction" : args[0];
                    if ("positive-fraction".equals(scenario)) {
                        System.out.println((int) value(7.75d));
                    } else if ("negative-fraction".equals(scenario)) {
                        System.out.println((int) value(-7.75d));
                    } else if ("negative-less-than-one".equals(scenario)) {
                        System.out.println((int) value(-0.75d));
                    } else if ("positive-zero".equals(scenario)) {
                        System.out.println((int) value(0.0d));
                    } else if ("negative-zero".equals(scenario)) {
                        System.out.println((int) value(-0.0d));
                    } else if ("nan".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(0x7ff8000000001234L));
                    } else if ("positive-infinity".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(0x7ff0000000000000L));
                    } else if ("negative-infinity".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(0xfff0000000000000L));
                    } else if ("exact-max".equals(scenario)) {
                        System.out.println((int) value(2147483647.0d));
                    } else if ("immediately-below-max".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(DOUBLE_IMMEDIATELY_BELOW_INTEGER_MAX_BITS));
                    } else if ("immediately-above-max".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(DOUBLE_IMMEDIATELY_ABOVE_INTEGER_MAX_BITS));
                    } else if ("above-max".equals(scenario)) {
                        System.out.println((int) value(2147483648.0d));
                    } else if ("exact-min".equals(scenario)) {
                        System.out.println((int) value(-2147483648.0d));
                    } else if ("immediately-below-min".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(DOUBLE_IMMEDIATELY_BELOW_INTEGER_MIN_BITS));
                    } else if ("immediately-above-min".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(DOUBLE_IMMEDIATELY_ABOVE_INTEGER_MIN_BITS));
                    } else if ("below-min".equals(scenario)) {
                        System.out.println((int) value(-2147483649.0d));
                    } else if ("positive-subnormal".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(0x0000000000000001L));
                    } else if ("negative-subnormal".equals(scenario)) {
                        System.out.println((int) Double.longBitsToDouble(0x8000000000000001L));
                    } else if ("single-evaluation".equals(scenario)) {
                        System.out.println((int) next());
                        System.out.println(calls);
                    } else if ("local".equals(scenario)) {
                        double value = 23.75d;
                        System.out.println((int) value);
                    } else if ("field".equals(scenario)) {
                        System.out.println((int) field);
                    } else if ("array".equals(scenario)) {
                        final double[] values = { 9.75d };
                        System.out.println((int) values[0]);
                    } else if ("branch-positive".equals(scenario)) {
                        double value = 4.75d;
                        System.out.println((int) value);
                    } else if ("branch-negative".equals(scenario)) {
                        double value = -4.75d;
                        System.out.println((int) value);
                    } else {
                        throw new IllegalArgumentException(scenario);
                    }
                }

                private static double next() {
                    calls++;
                    return -3.75d;
                }

                private static double value(final double input) {
                    return input;
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/double-to-int-probe");
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
    void negativeLessThanOneMatchesJvm() {
        assertThat(nativeRun("negative-less-than-one")).isEqualTo(jvmRun("negative-less-than-one"));
    }

    @Test
    void generatedSourceContainsExpectedDoubleToIntCalls() throws Exception {
        assertThat(generatedProgramSource(project).split("javan_double_to_int\\(", -1).length - 1)
            .isEqualTo(24);
    }

    @Test
    void positiveZeroMatchesJvm() {
        assertThat(nativeRun("positive-zero")).isEqualTo(jvmRun("positive-zero"));
    }

    @Test
    void negativeZeroMatchesJvm() {
        assertThat(nativeRun("negative-zero")).isEqualTo(jvmRun("negative-zero"));
    }

    @Test
    void nanMatchesJvm() {
        assertThat(nativeRun("nan")).isEqualTo(jvmRun("nan"));
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
    void exactMaximumMatchesJvm() {
        assertThat(nativeRun("exact-max")).isEqualTo(jvmRun("exact-max"));
    }

    @Test
    void immediatelyBelowIntegerMaximumMatchesJvm() {
        assertThat(nativeRun("immediately-below-max")).isEqualTo(jvmRun("immediately-below-max"));
    }

    @Test
    void immediatelyAboveIntegerMaximumMatchesJvm() {
        assertThat(nativeRun("immediately-above-max")).isEqualTo(jvmRun("immediately-above-max"));
    }

    @Test
    void aboveMaximumMatchesJvm() {
        assertThat(nativeRun("above-max")).isEqualTo(jvmRun("above-max"));
    }

    @Test
    void exactMinimumMatchesJvm() {
        assertThat(nativeRun("exact-min")).isEqualTo(jvmRun("exact-min"));
    }

    @Test
    void immediatelyBelowIntegerMinimumMatchesJvm() {
        assertThat(nativeRun("immediately-below-min")).isEqualTo(jvmRun("immediately-below-min"));
    }

    @Test
    void immediatelyAboveIntegerMinimumMatchesJvm() {
        assertThat(nativeRun("immediately-above-min")).isEqualTo(jvmRun("immediately-above-min"));
    }

    @Test
    void belowMinimumMatchesJvm() {
        assertThat(nativeRun("below-min")).isEqualTo(jvmRun("below-min"));
    }

    @Test
    void positiveSubnormalMatchesJvm() {
        assertThat(nativeRun("positive-subnormal")).isEqualTo(jvmRun("positive-subnormal"));
    }

    @Test
    void negativeSubnormalMatchesJvm() {
        assertThat(nativeRun("negative-subnormal")).isEqualTo(jvmRun("negative-subnormal"));
    }

    @Test
    void argumentIsEvaluatedOnce() {
        assertThat(nativeRun("single-evaluation")).isEqualTo(jvmRun("single-evaluation"));
    }

    @Test
    void localValueMatchesJvm() {
        assertThat(nativeRun("local")).isEqualTo(jvmRun("local"));
    }

    @Test
    void staticFieldValueMatchesJvm() {
        assertThat(nativeRun("field")).isEqualTo(jvmRun("field"));
    }

    @Test
    void arrayValueMatchesJvm() {
        assertThat(nativeRun("array")).isEqualTo(jvmRun("array"));
    }

    @Test
    void positiveBranchValueMatchesJvm() {
        assertThat(nativeRun("branch-positive")).isEqualTo(jvmRun("branch-positive"));
    }

    @Test
    void negativeBranchValueMatchesJvm() {
        assertThat(nativeRun("branch-negative")).isEqualTo(jvmRun("branch-negative"));
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
