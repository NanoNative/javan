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
final class CliFloatIntrinsicsIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("float-intrinsics-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "finite" : args[0];
                    if ("is-finite-positive-zero".equals(scenario)) {
                        System.out.println(Float.isFinite(0.0f));
                        return;
                    } else if ("is-finite-negative-zero".equals(scenario)) {
                        System.out.println(Float.isFinite(-0.0f));
                        return;
                    } else if ("is-finite-positive-normal".equals(scenario)) {
                        System.out.println(Float.isFinite(1.5f));
                        return;
                    } else if ("is-finite-negative-normal".equals(scenario)) {
                        System.out.println(Float.isFinite(-1.5f));
                        return;
                    } else if ("is-finite-positive-subnormal".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0x00000001)));
                        return;
                    } else if ("is-finite-negative-subnormal".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0x80000001)));
                        return;
                    } else if ("is-finite-positive-maximum".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0x7f7fffff)));
                        return;
                    } else if ("is-finite-negative-maximum".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0xff7fffff)));
                        return;
                    } else if ("is-finite-positive-infinity".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0x7f800000)));
                        return;
                    } else if ("is-finite-negative-infinity".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0xff800000)));
                        return;
                    } else if ("is-finite-positive-nan".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0x7fc01234)));
                        return;
                    } else if ("is-finite-negative-nan".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0xffc05678)));
                        return;
                    } else if ("is-finite-signaling-nan".equals(scenario)) {
                        System.out.println(Float.isFinite(Float.intBitsToFloat(0x7f801234)));
                        return;
                    }
                    final float value;
                    if ("positive-zero".equals(scenario)) {
                        value = 0.0f;
                    } else if ("negative-zero".equals(scenario)) {
                        value = -0.0f;
                    } else if ("positive-infinity".equals(scenario)) {
                        value = Float.intBitsToFloat(0x7f800000);
                    } else if ("negative-infinity".equals(scenario)) {
                        value = Float.intBitsToFloat(0xff800000);
                    } else if ("subnormal".equals(scenario)) {
                        value = Float.intBitsToFloat(0x00000001);
                    } else if ("positive-nan".equals(scenario)) {
                        value = Float.intBitsToFloat(0x7fc01234);
                    } else if ("negative-nan".equals(scenario)) {
                        value = Float.intBitsToFloat(0xffc05678);
                    } else if ("signaling-nan".equals(scenario)) {
                        value = Float.intBitsToFloat(0x7f801234);
                    } else {
                        value = 1.5f;
                    }
                    System.out.println(Float.floatToRawIntBits(value));
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/float-intrinsics-probe");
    }

    @Test
    void finiteValuePreservesBits() {
        assertThat(runBits("finite")).isEqualTo(success(0x3fc00000));
    }

    @Test
    void positiveZeroPreservesBits() {
        assertThat(runBits("positive-zero")).isEqualTo(success(0x00000000));
    }

    @Test
    void negativeZeroPreservesBits() {
        assertThat(runBits("negative-zero")).isEqualTo(success(0x80000000));
    }

    @Test
    void positiveInfinityPreservesBits() {
        assertThat(runBits("positive-infinity")).isEqualTo(success(0x7f800000));
    }

    @Test
    void negativeInfinityPreservesBits() {
        assertThat(runBits("negative-infinity")).isEqualTo(success(0xff800000));
    }

    @Test
    void minimumSubnormalPreservesBits() {
        assertThat(runBits("subnormal")).isEqualTo(success(0x00000001));
    }

    @Test
    void positiveNanPayloadPreservesBits() {
        assertThat(runBits("positive-nan")).isEqualTo(success(0x7fc01234));
    }

    @Test
    void negativeNanPayloadPreservesBits() {
        assertThat(runBits("negative-nan")).isEqualTo(success(0xffc05678));
    }

    @Test
    void signalingNanExitMatchesJvmPlatformResult() {
        final BitsResult result = runBits("signaling-nan");

        assertThat(result.nativeExit()).isEqualTo(result.jvmExit());
    }

    @Test
    void signalingNanOutputMatchesJvmPlatformResult() {
        final BitsResult result = runBits("signaling-nan");

        assertThat(result.nativeStdout()).isEqualTo(result.jvmStdout());
    }

    @Test
    void signalingNanErrorMatchesJvmPlatformResult() {
        final BitsResult result = runBits("signaling-nan");

        assertThat(result.nativeStderr()).isEqualTo(result.jvmStderr());
    }

    @Test
    void isFiniteAcceptsPositiveZero() {
        assertThat(nativeRun("is-finite-positive-zero")).isEqualTo(jvmRun("is-finite-positive-zero"));
    }

    @Test
    void isFiniteAcceptsNegativeZero() {
        assertThat(nativeRun("is-finite-negative-zero")).isEqualTo(jvmRun("is-finite-negative-zero"));
    }

    @Test
    void isFiniteAcceptsPositiveNormal() {
        assertThat(nativeRun("is-finite-positive-normal")).isEqualTo(jvmRun("is-finite-positive-normal"));
    }

    @Test
    void isFiniteAcceptsNegativeNormal() {
        assertThat(nativeRun("is-finite-negative-normal")).isEqualTo(jvmRun("is-finite-negative-normal"));
    }

    @Test
    void isFiniteAcceptsPositiveSubnormal() {
        assertThat(nativeRun("is-finite-positive-subnormal")).isEqualTo(jvmRun("is-finite-positive-subnormal"));
    }

    @Test
    void isFiniteAcceptsNegativeSubnormal() {
        assertThat(nativeRun("is-finite-negative-subnormal")).isEqualTo(jvmRun("is-finite-negative-subnormal"));
    }

    @Test
    void isFiniteAcceptsPositiveMaximum() {
        assertThat(nativeRun("is-finite-positive-maximum")).isEqualTo(jvmRun("is-finite-positive-maximum"));
    }

    @Test
    void isFiniteAcceptsNegativeMaximum() {
        assertThat(nativeRun("is-finite-negative-maximum")).isEqualTo(jvmRun("is-finite-negative-maximum"));
    }

    @Test
    void isFiniteRejectsPositiveInfinity() {
        assertThat(nativeRun("is-finite-positive-infinity")).isEqualTo(jvmRun("is-finite-positive-infinity"));
    }

    @Test
    void isFiniteRejectsNegativeInfinity() {
        assertThat(nativeRun("is-finite-negative-infinity")).isEqualTo(jvmRun("is-finite-negative-infinity"));
    }

    @Test
    void isFiniteRejectsPositiveNan() {
        assertThat(nativeRun("is-finite-positive-nan")).isEqualTo(jvmRun("is-finite-positive-nan"));
    }

    @Test
    void isFiniteRejectsNegativeNan() {
        assertThat(nativeRun("is-finite-negative-nan")).isEqualTo(jvmRun("is-finite-negative-nan"));
    }

    @Test
    void isFiniteRejectsSignalingNan() {
        assertThat(nativeRun("is-finite-signaling-nan")).isEqualTo(jvmRun("is-finite-signaling-nan"));
    }

    private BitsResult runBits(final String scenario) {
        final ProcessResult nativeRun = nativeRun(scenario);
        final ProcessResult jvmRun = jvmRun(scenario);
        return new BitsResult(
            nativeRun.exitCode(),
            nativeRun.stdout(),
            nativeRun.stderr(),
            jvmRun.exitCode(),
            jvmRun.stdout(),
            jvmRun.stderr()
        );
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

    private static BitsResult success(final int bits) {
        final String output = bits + System.lineSeparator();
        return new BitsResult(0, output, "", 0, output, "");
    }

    private record BitsResult(
        int nativeExit,
        String nativeStdout,
        String nativeStderr,
        int jvmExit,
        String jvmStdout,
        String jvmStderr
    ) {
    }
}
