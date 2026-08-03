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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliDoubleToFloatIntegrationTest extends CliIntegrationSupport {
    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;
    private Path jvmClasses;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("double-to-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int scenario = args.length;
                    final float converted = (float) value(scenario);
                    if (scenario >= 8 && scenario <= 11) {
                        System.out.println(converted != converted);
                        return;
                    }
                    System.out.println(Float.floatToRawIntBits(converted));
                }

                private static double value(final int scenario) {
                    if (scenario == 0) {
                        return 0x1.8p0;
                    }
                    if (scenario == 1) {
                        return 0x1.0000018p0;
                    }
                    if (scenario == 2) {
                        return 0x1.000001p0;
                    }
                    if (scenario == 3) {
                        return -0x1.000001p0;
                    }
                    if (scenario == 4) {
                        return 0.0d;
                    }
                    if (scenario == 5) {
                        return -0.0d;
                    }
                    if (scenario == 6) {
                        return Double.longBitsToDouble(0x7ff0000000000000L);
                    }
                    if (scenario == 7) {
                        return Double.longBitsToDouble(0xfff0000000000000L);
                    }
                    if (scenario == 8) {
                        return Double.longBitsToDouble(0x7ff8000000000001L);
                    }
                    if (scenario == 9) {
                        return Double.longBitsToDouble(0xfff8000000000001L);
                    }
                    if (scenario == 10) {
                        return Double.longBitsToDouble(0x7ff0000000000001L);
                    }
                    if (scenario == 11) {
                        return Double.longBitsToDouble(0xfff0000000000001L);
                    }
                    if (scenario == 12) {
                        return 0x1.0p-127;
                    }
                    if (scenario == 13) {
                        return 0x1.0p-149;
                    }
                    if (scenario == 14) {
                        return 0x1.0p-150;
                    }
                    if (scenario == 15) {
                        return -0x1.0p-150;
                    }
                    if (scenario == 16) {
                        return 0x1.fffffep-127;
                    }
                    if (scenario == 17) {
                        return 0x1.fffffep127;
                    }
                    if (scenario == 18) {
                        return 0x1.fffffefffffffp127;
                    }
                    if (scenario == 19) {
                        return 0x1.ffffffp127;
                    }
                    if (scenario == 20) {
                        return 0x1.fffffffffffffp1023;
                    }
                    if (scenario == 21) {
                        return -0x1.fffffffffffffp1023;
                    }
                    if (scenario == 22) {
                        return Double.longBitsToDouble(0x3ff0000030000000L);
                    }
                    if (scenario == 23) {
                        return Double.longBitsToDouble(0x0000000000000001L);
                    }
                    if (scenario == 24) {
                        return Double.longBitsToDouble(0x8000000000000001L);
                    }
                    if (scenario == 25) {
                        return Double.longBitsToDouble(0x3ffffffff0000000L);
                    }
                    return Double.longBitsToDouble(0xc7effffff0000000L);
                }
            }
            """);

        runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/double-to-float");
        jvmClasses = project.resolve("jvm-classes");
    }

    @Test
    void exactFiniteValueMatchesJvmRawBits() {
        assertThat(nativeRun(0)).isEqualTo(jvmRun(0));
    }

    @Test
    void ordinaryRoundingMatchesJvmRawBits() {
        assertThat(nativeRun(1)).isEqualTo(jvmRun(1));
    }

    @Test
    void positiveTieMatchesJvmRawBits() {
        assertThat(nativeRun(2)).isEqualTo(jvmRun(2));
    }

    @Test
    void negativeTieMatchesJvmRawBits() {
        assertThat(nativeRun(3)).isEqualTo(jvmRun(3));
    }

    @Test
    void positiveZeroMatchesJvmRawBits() {
        assertThat(nativeRun(4)).isEqualTo(jvmRun(4));
    }

    @Test
    void negativeZeroMatchesJvmRawBits() {
        assertThat(nativeRun(5)).isEqualTo(jvmRun(5));
    }

    @Test
    void positiveInfinityMatchesJvmRawBits() {
        assertThat(nativeRun(6)).isEqualTo(jvmRun(6));
    }

    @Test
    void negativeInfinityMatchesJvmRawBits() {
        assertThat(nativeRun(7)).isEqualTo(jvmRun(7));
    }

    @Test
    void positiveQuietNanHasJavaNanValue() {
        assertThat(nativeRun(8).stdout()).isEqualTo("true\n");
    }

    @Test
    void negativeQuietNanHasJavaNanValue() {
        assertThat(nativeRun(9).stdout()).isEqualTo("true\n");
    }

    @Test
    void positiveSignalingNanHasJavaNanValue() {
        assertThat(nativeRun(10).stdout()).isEqualTo("true\n");
    }

    @Test
    void negativeSignalingNanHasJavaNanValue() {
        assertThat(nativeRun(11).stdout()).isEqualTo("true\n");
    }

    @Test
    void gradualUnderflowMatchesJvmRawBits() {
        assertThat(nativeRun(12)).isEqualTo(jvmRun(12));
    }

    @Test
    void minimumBinary32SubnormalMatchesJvmRawBits() {
        assertThat(nativeRun(13)).isEqualTo(jvmRun(13));
    }

    @Test
    void positiveHalfMinimumBinary32SubnormalMatchesJvmRawBits() {
        assertThat(nativeRun(14)).isEqualTo(jvmRun(14));
    }

    @Test
    void negativeHalfMinimumBinary32SubnormalMatchesJvmRawBits() {
        assertThat(nativeRun(15)).isEqualTo(jvmRun(15));
    }

    @Test
    void minimumNormalCarryMatchesJvmRawBits() {
        assertThat(nativeRun(16)).isEqualTo(jvmRun(16));
    }

    @Test
    void maximumFiniteFloatMatchesJvmRawBits() {
        assertThat(nativeRun(17)).isEqualTo(jvmRun(17));
    }

    @Test
    void justBelowOverflowThresholdMatchesJvmRawBits() {
        assertThat(nativeRun(18)).isEqualTo(jvmRun(18));
    }

    @Test
    void overflowTieMatchesJvmRawBits() {
        assertThat(nativeRun(19)).isEqualTo(jvmRun(19));
    }

    @Test
    void positiveOverflowMatchesJvmRawBits() {
        assertThat(nativeRun(20)).isEqualTo(jvmRun(20));
    }

    @Test
    void negativeOverflowMatchesJvmRawBits() {
        assertThat(nativeRun(21)).isEqualTo(jvmRun(21));
    }

    @Test
    void oddLowerTieMatchesJvmRawBits() {
        assertThat(nativeRun(22)).isEqualTo(jvmRun(22));
    }

    @Test
    void minimumPositiveBinary64SubnormalMatchesJvmRawBits() {
        assertThat(nativeRun(23)).isEqualTo(jvmRun(23));
    }

    @Test
    void minimumNegativeBinary64SubnormalMatchesJvmRawBits() {
        assertThat(nativeRun(24)).isEqualTo(jvmRun(24));
    }

    @Test
    void finiteNormalMantissaCarryMatchesJvmRawBits() {
        assertThat(nativeRun(25)).isEqualTo(jvmRun(25));
    }

    @Test
    void negativeOverflowMidpointMatchesJvmRawBits() {
        assertThat(nativeRun(26)).isEqualTo(jvmRun(26));
    }

    private ProcessResult nativeRun(final int scenario) {
        return process(project, command(binary.toString(), scenario));
    }

    private ProcessResult jvmRun(final int scenario) {
        return process(project, command(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            jvmClasses.toString(),
            "com.acme.Main",
            scenario
        ));
    }

    private static List<String> command(final String executable, final int scenario) {
        final List<String> command = new ArrayList<>(List.of(executable));
        for (int index = 0; index < scenario; index++) {
            command.add("");
        }
        return List.copyOf(command);
    }

    private static List<String> command(
        final String executable,
        final String classpathFlag,
        final String classpath,
        final String mainClass,
        final int scenario
    ) {
        final List<String> command = new ArrayList<>(List.of(executable, classpathFlag, classpath, mainClass));
        for (int index = 0; index < scenario; index++) {
            command.add("");
        }
        return List.copyOf(command);
    }
}
