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
final class CliDoubleIntrinsicsIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("double-intrinsics-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "positive-normal" : args[0];
                    final double value;
                    if ("positive-zero".equals(scenario)) {
                        value = 0.0d;
                    } else if ("negative-zero".equals(scenario)) {
                        value = -0.0d;
                    } else if ("positive-normal".equals(scenario)) {
                        value = 1.5d;
                    } else if ("negative-normal".equals(scenario)) {
                        value = -1.5d;
                    } else if ("positive-subnormal".equals(scenario)) {
                        value = Double.longBitsToDouble(0x0000000000000001L);
                    } else if ("negative-subnormal".equals(scenario)) {
                        value = Double.longBitsToDouble(0x8000000000000001L);
                    } else if ("positive-maximum".equals(scenario)) {
                        value = Double.longBitsToDouble(0x7fefffffffffffffL);
                    } else if ("negative-maximum".equals(scenario)) {
                        value = Double.longBitsToDouble(0xffefffffffffffffL);
                    } else if ("positive-infinity".equals(scenario)) {
                        value = Double.longBitsToDouble(0x7ff0000000000000L);
                    } else if ("negative-infinity".equals(scenario)) {
                        value = Double.longBitsToDouble(0xfff0000000000000L);
                    } else if ("positive-nan".equals(scenario)) {
                        value = Double.longBitsToDouble(0x7ff8000000001234L);
                    } else if ("negative-nan".equals(scenario)) {
                        value = Double.longBitsToDouble(0xfff8000000005678L);
                    } else if ("signaling-nan".equals(scenario)) {
                        value = Double.longBitsToDouble(0x7ff0000000001234L);
                    } else {
                        throw new IllegalArgumentException(scenario);
                    }
                    System.out.println(Double.isFinite(value));
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/double-intrinsics-probe");
    }

    @Test
    void isFiniteAcceptsPositiveZero() {
        assertThat(nativeRun("positive-zero")).isEqualTo(jvmRun("positive-zero"));
    }

    @Test
    void isFiniteAcceptsNegativeZero() {
        assertThat(nativeRun("negative-zero")).isEqualTo(jvmRun("negative-zero"));
    }

    @Test
    void isFiniteAcceptsPositiveNormal() {
        assertThat(nativeRun("positive-normal")).isEqualTo(jvmRun("positive-normal"));
    }

    @Test
    void isFiniteAcceptsNegativeNormal() {
        assertThat(nativeRun("negative-normal")).isEqualTo(jvmRun("negative-normal"));
    }

    @Test
    void isFiniteAcceptsPositiveSubnormal() {
        assertThat(nativeRun("positive-subnormal")).isEqualTo(jvmRun("positive-subnormal"));
    }

    @Test
    void isFiniteAcceptsNegativeSubnormal() {
        assertThat(nativeRun("negative-subnormal")).isEqualTo(jvmRun("negative-subnormal"));
    }

    @Test
    void isFiniteAcceptsPositiveMaximum() {
        assertThat(nativeRun("positive-maximum")).isEqualTo(jvmRun("positive-maximum"));
    }

    @Test
    void isFiniteAcceptsNegativeMaximum() {
        assertThat(nativeRun("negative-maximum")).isEqualTo(jvmRun("negative-maximum"));
    }

    @Test
    void isFiniteRejectsPositiveInfinity() {
        assertThat(nativeRun("positive-infinity")).isEqualTo(jvmRun("positive-infinity"));
    }

    @Test
    void isFiniteRejectsNegativeInfinity() {
        assertThat(nativeRun("negative-infinity")).isEqualTo(jvmRun("negative-infinity"));
    }

    @Test
    void isFiniteRejectsPositiveNan() {
        assertThat(nativeRun("positive-nan")).isEqualTo(jvmRun("positive-nan"));
    }

    @Test
    void isFiniteRejectsNegativeNan() {
        assertThat(nativeRun("negative-nan")).isEqualTo(jvmRun("negative-nan"));
    }

    @Test
    void isFiniteRejectsSignalingNan() {
        assertThat(nativeRun("signaling-nan")).isEqualTo(jvmRun("signaling-nan"));
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
