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
final class CliLongNegationIntegrationTest extends CliIntegrationSupport {
    @Test
    void minimumValueWrapsLikeJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-minimum",
            "System.out.println(negate(Long.MIN_VALUE));",
            """
            private static long negate(final long value) {
                return -value;
            }
            """
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void localOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-local",
            """
            long value = args.length + 7L;
            System.out.println(-value);
            """,
            ""
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void instanceFieldOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-instance-field",
            """
            final Main instance = new Main();
            System.out.println(-instance.instanceValue);
            """,
            "private long instanceValue = 7L;"
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void staticFieldOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-static-field",
            "System.out.println(-staticValue);",
            "private static long staticValue = -7L;"
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void arrayOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-array",
            """
            final long[] values = {7L};
            System.out.println(-values[0]);
            """,
            ""
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void methodOperandIsEvaluatedOnce() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-method",
            """
            System.out.println(-nextValue());
            System.out.println(callCount);
            """,
            """
            private static int callCount;

            private static long nextValue() {
                callCount++;
                return 7L;
            }
            """
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void branchOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-branch",
            "System.out.println(negateWhen(true, 7L));",
            """
            private static long negateWhen(final boolean enabled, final long value) {
                if (enabled) {
                    return -value;
                }
                return value;
            }
            """
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void loopOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-loop",
            """
            long total = 0L;
            for (long value = 1L; value <= 3L; value++) {
                total += -value;
            }
            System.out.println(total);
            """,
            ""
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    @Test
    void typedHandlerOperandMatchesJvm() throws Exception {
        final NativeParity output = compileAndRun(
            "long-negation-handler",
            "System.out.println(parseOrNegate(\"not-a-long\", 7L));",
            """
            private static long parseOrNegate(final String value, final long fallback) {
                try {
                    return Long.parseLong(value);
                } catch (final NumberFormatException ignored) {
                    return -fallback;
                }
            }
            """
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    private NativeParity compileAndRun(final String projectName, final String body, final String members)
        throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source(body, members));
        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin").resolve(projectName).toString()))
            : new ProcessResult(-1, "", "native build failed");
        final String diagnostics = build.stderr()
            + "\nnative exit: " + nativeRun.exitCode()
            + "\nnative stderr:\n" + nativeRun.stderr();
        return new NativeParity(diagnostics, nativeRun.stdout(), jvmOutput);
    }

    private static String source(final String body, final String members) {
        return """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
            %s
                }

            %s
            }
            """.formatted(indent(body), members);
    }

    private static String indent(final String value) {
        return value.replace("\n", "\n        ");
    }

    private record NativeParity(String buildStderr, String nativeOutput, String jvmOutput) {
    }
}
