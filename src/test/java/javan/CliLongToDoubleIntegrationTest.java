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
final class CliLongToDoubleIntegrationTest extends CliIntegrationSupport {
    @Test
    void genericLongToDoubleConversionsMatchJvmRawBits() throws Exception {
        final Path project = project("long-to-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static final long TWO_TO_53 = 1L << 53;

                private Main() {
                }

                public static void main(final String[] args) {
                    print(0L);
                    print(1L);
                    print(-1L);
                    print(TWO_TO_53 - 1L);
                    print(TWO_TO_53);
                    print(TWO_TO_53 + 1L);
                    print(TWO_TO_53 + 3L);
                    print(-(TWO_TO_53 + 1L));
                    print(-(TWO_TO_53 + 3L));
                    print(Long.MIN_VALUE);
                    print(Long.MAX_VALUE);
                }

                private static void print(final long value) {
                    System.out.println((double) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/long-to-double").toString()))
            : new ProcessResult(-1, "", build.stderr());
        final List<Long> jvmRawBits = rawBits(jvmOutput);

        assertThat(new LongToDoubleParity(
            build.exitCode(),
            nativeRun.exitCode(),
            nativeRun.stderr(),
            rawBits(nativeRun.stdout()),
            jvmRawBits
        ))
            .as(build.stderr())
            .isEqualTo(new LongToDoubleParity(0, 0, "", jvmRawBits, jvmRawBits));
    }

    private static List<Long> rawBits(final String output) {
        return output.lines()
            .map(Double::parseDouble)
            .map(Double::doubleToRawLongBits)
            .toList();
    }

    private record LongToDoubleParity(
        int buildExitCode,
        int nativeExitCode,
        String nativeStderr,
        List<Long> nativeRawBits,
        List<Long> jvmRawBits
    ) {
    }
}
