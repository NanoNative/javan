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
final class CliDoubleToLongIntegrationTest extends CliIntegrationSupport {
    @Test
    void genericDoubleToLongConversionsMatchJvm() throws Exception {
        final Path project = project("double-to-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    print(Double.longBitsToDouble(0x0000000000000000L));
                    print(Double.longBitsToDouble(0x8000000000000000L));
                    print(Double.longBitsToDouble(0x401f000000000000L));
                    print(Double.longBitsToDouble(0xc01f000000000000L));
                    print(Double.longBitsToDouble(0x0000000000000001L));
                    print(Double.longBitsToDouble(0x8000000000000001L));
                    print(Double.longBitsToDouble(0x7ff8000000001234L));
                    print(Double.longBitsToDouble(0x7ff0000000000000L));
                    print(Double.longBitsToDouble(0xfff0000000000000L));
                    print(Double.longBitsToDouble(0x43e0000000000000L));
                    print(Double.longBitsToDouble(0xc3e0000000000000L));
                    print(Double.longBitsToDouble(0x43dfffffffffffffL));
                    print(Double.longBitsToDouble(0xc3dfffffffffffffL));
                    print(Double.longBitsToDouble(0x43e0000000000001L));
                    print(Double.longBitsToDouble(0xc3e0000000000001L));
                    print(Double.longBitsToDouble(0x7fefffffffffffffL));
                    print(Double.longBitsToDouble(0xffefffffffffffffL));
                }

                private static void print(final double value) {
                    System.out.println((long) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/double-to-long").toString()))
            : new ProcessResult(-1, "", build.stderr());

        assertThat(new DoubleToLongParity(
            build.exitCode(),
            nativeRun.exitCode(),
            nativeRun.stderr(),
            nativeRun.stdout(),
            jvmOutput
        ))
            .as(build.stderr())
            .isEqualTo(new DoubleToLongParity(0, 0, "", jvmOutput, jvmOutput));
    }

    private record DoubleToLongParity(
        int buildExitCode,
        int nativeExitCode,
        String nativeStderr,
        String nativeOutput,
        String jvmOutput
    ) {
    }
}
