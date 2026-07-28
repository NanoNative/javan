package javan;

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
final class CliLongCompareIntegrationTest extends CliIntegrationSupport {
    @Test
    void signedEqualValuesCompareAsEqual() throws Exception {
        assertMatchesJvm("long-compare-signed-equal", "Long.compare(Long.MIN_VALUE, Long.MIN_VALUE)");
    }

    @Test
    void signedMinimumSortsBeforeMaximum() throws Exception {
        assertMatchesJvm("long-compare-signed-minimum-maximum", "Long.compare(Long.MIN_VALUE, Long.MAX_VALUE)");
    }

    @Test
    void signedMaximumSortsAfterMinimum() throws Exception {
        assertMatchesJvm("long-compare-signed-maximum-minimum", "Long.compare(Long.MAX_VALUE, Long.MIN_VALUE)");
    }

    @Test
    void zeroSortsBeforePositiveUnsignedValue() throws Exception {
        assertMatchesJvm("long-compare-unsigned-zero-positive", "Long.compareUnsigned(0L, 1L)");
    }

    @Test
    void zeroSortsBeforeUnsignedMaximum() throws Exception {
        assertMatchesJvm("long-compare-unsigned-zero-maximum", "Long.compareUnsigned(0L, -1L)");
    }

    @Test
    void signedMaximumSortsBeforeUnsignedSignBoundary() throws Exception {
        assertMatchesJvm(
            "long-compare-unsigned-sign-boundary",
            "Long.compareUnsigned(Long.MAX_VALUE, Long.MIN_VALUE)"
        );
    }

    @Test
    void unsignedSignBoundarySortsAfterSignedMaximum() throws Exception {
        assertMatchesJvm(
            "long-compare-unsigned-reverse-sign-boundary",
            "Long.compareUnsigned(Long.MIN_VALUE, Long.MAX_VALUE)"
        );
    }

    @Test
    void unsignedMaximumSortsAfterSignBoundary() throws Exception {
        assertMatchesJvm(
            "long-compare-unsigned-maximum-minimum",
            "Long.compareUnsigned(-1L, Long.MIN_VALUE)"
        );
    }

    @Test
    void equalUnsignedValuesCompareAsEqual() throws Exception {
        assertMatchesJvm(
            "long-compare-unsigned-equal",
            "Long.compareUnsigned(Long.MIN_VALUE, Long.MIN_VALUE)"
        );
    }

    @Test
    void unsignedOperandsAreEvaluatedFromLeftToRight() throws Exception {
        final NativeParity output = compileAndRun(
            "long-compare-unsigned-order",
            """
            System.out.println(Long.compareUnsigned(left(), right()));
            System.out.println(order);
            """,
            """
            private static int order;

            private static long left() {
                order = order * 10 + 1;
                return Long.MIN_VALUE;
            }

            private static long right() {
                order = order * 10 + 2;
                return Long.MAX_VALUE;
            }
            """
        );

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    private void assertMatchesJvm(final String projectName, final String expression) throws Exception {
        final NativeParity output = compileAndRun(projectName, "System.out.println(" + expression + ");", "");

        assertThat(output.nativeOutput()).as(output.buildStderr()).isEqualTo(output.jvmOutput());
    }

    private NativeParity compileAndRun(final String projectName, final String body, final String members) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source(body, members));
        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin").resolve(projectName).toString()))
            : new ProcessResult(-1, "", "native build failed");
        return new NativeParity(build.stderr(), nativeRun.stdout(), jvmOutput);
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
