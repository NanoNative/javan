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
final class CliArraysFillByteIntegrationTest extends CliIntegrationSupport {
    @Test
    void wholeByteArrayFillMatchesJvm() throws Exception {
        assertMatchesJvm("arrays-fill-byte-whole", """
            final byte[] values = {1, 2, 3};
            java.util.Arrays.fill(values, (byte) -7);
            System.out.println(values[0]);
            System.out.println(values[1]);
            System.out.println(values[2]);
            """);
    }

    @Test
    void rangedByteArrayFillLeavesOutsideValuesUntouched() throws Exception {
        assertMatchesJvm("arrays-fill-byte-range", """
            final byte[] values = {1, 2, 3, 4};
            java.util.Arrays.fill(values, 1, 3, (byte) 9);
            System.out.println(values[0]);
            System.out.println(values[1]);
            System.out.println(values[2]);
            System.out.println(values[3]);
            """);
    }

    @Test
    void emptyByteArrayFillRangeMatchesJvm() throws Exception {
        assertMatchesJvm("arrays-fill-byte-empty-range", """
            final byte[] values = {1, 2, 3};
            java.util.Arrays.fill(values, 2, 2, (byte) 8);
            System.out.println(values[2]);
            """);
    }

    @Test
    void rangedByteArrayFillEvaluatesArgumentsFromLeftToRight() throws Exception {
        assertMatchesJvm("arrays-fill-byte-order", """
            java.util.Arrays.fill(array(), from(), to(), value());
            System.out.println(order);
            """, """
            private static int order;
            private static final byte[] VALUES = {1, 2, 3};

            private static byte[] array() {
                order = order * 10 + 1;
                return VALUES;
            }

            private static int from() {
                order = order * 10 + 2;
                return 0;
            }

            private static int to() {
                order = order * 10 + 3;
                return 1;
            }

            private static byte value() {
                order = order * 10 + 4;
                return 7;
            }
            """);
    }

    @Test
    void wholeByteArrayFillThrowsCatchableNullPointerException() throws Exception {
        assertMatchesJvm("arrays-fill-byte-null", """
            try {
                java.util.Arrays.fill((byte[]) null, (byte) 1);
                System.out.println("not thrown");
            } catch (final NullPointerException ignored) {
                System.out.println("NullPointerException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillThrowsCatchableNullPointerException() throws Exception {
        assertMatchesJvm("arrays-fill-byte-range-null", """
            try {
                java.util.Arrays.fill((byte[]) null, 0, 0, (byte) 1);
                System.out.println("not thrown");
            } catch (final NullPointerException ignored) {
                System.out.println("NullPointerException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillThrowsCatchableIllegalArgumentException() throws Exception {
        assertMatchesJvm("arrays-fill-byte-inverted-range", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, 1, 0, (byte) 2);
                System.out.println("not thrown");
            } catch (final IllegalArgumentException ignored) {
                System.out.println("IllegalArgumentException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillThrowsCatchableArrayIndexOutOfBoundsExceptionForNegativeFromIndex() throws Exception {
        assertMatchesJvm("arrays-fill-byte-negative-from", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, -1, 1, (byte) 2);
                System.out.println("not thrown");
            } catch (final ArrayIndexOutOfBoundsException ignored) {
                System.out.println("ArrayIndexOutOfBoundsException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillThrowsCatchableArrayIndexOutOfBoundsExceptionForPastEndIndex() throws Exception {
        assertMatchesJvm("arrays-fill-byte-past-end", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, 0, 2, (byte) 2);
                System.out.println("not thrown");
            } catch (final ArrayIndexOutOfBoundsException ignored) {
                System.out.println("ArrayIndexOutOfBoundsException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillChecksNullBeforeInvertedRange() throws Exception {
        assertMatchesJvm("arrays-fill-byte-null-precedence", """
            try {
                java.util.Arrays.fill((byte[]) null, 1, 0, (byte) 2);
                System.out.println("not thrown");
            } catch (final NullPointerException ignored) {
                System.out.println("NullPointerException");
            } catch (final IllegalArgumentException ignored) {
                System.out.println("IllegalArgumentException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillChecksInvertedRangeBeforeNegativeBounds() throws Exception {
        assertMatchesJvm("arrays-fill-byte-range-precedence", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, -1, -2, (byte) 2);
                System.out.println("not thrown");
            } catch (final IllegalArgumentException ignored) {
                System.out.println("IllegalArgumentException");
            } catch (final ArrayIndexOutOfBoundsException ignored) {
                System.out.println("ArrayIndexOutOfBoundsException");
            }
            """);
    }

    @Test
    void rangedByteArrayFillIsCatchableThroughRuntimeException() throws Exception {
        assertMatchesJvm("arrays-fill-byte-runtime-exception", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, 1, 0, (byte) 2);
                System.out.println("not thrown");
            } catch (final RuntimeException ignored) {
                System.out.println("RuntimeException");
            }
            """);
    }

    @Test
    void rangedByteArrayBoundsFailureIsCatchableThroughIndexOutOfBoundsException() throws Exception {
        assertMatchesJvm("arrays-fill-byte-index-out-of-bounds-exception", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, 0, 2, (byte) 2);
                System.out.println("not thrown");
            } catch (final IndexOutOfBoundsException ignored) {
                System.out.println("IndexOutOfBoundsException");
            }
            """);
    }

    @Test
    void rangedByteArrayInvertedRangeIsCatchableThroughException() throws Exception {
        assertMatchesJvm("arrays-fill-byte-exception", """
            final byte[] values = {1};
            try {
                java.util.Arrays.fill(values, 1, 0, (byte) 2);
                System.out.println("not thrown");
            } catch (final Exception ignored) {
                System.out.println("Exception");
            }
            """);
    }

    @Test
    void wholeByteArrayNullFailureIsCatchableThroughThrowable() throws Exception {
        assertMatchesJvm("arrays-fill-byte-throwable", """
            try {
                java.util.Arrays.fill((byte[]) null, (byte) 2);
                System.out.println("not thrown");
            } catch (final Throwable ignored) {
                System.out.println("Throwable");
            }
            """);
    }

    private void assertMatchesJvm(final String projectName, final String body) throws Exception {
        assertMatchesJvm(projectName, body, "");
    }

    private void assertMatchesJvm(final String projectName, final String body, final String members) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source(body, members));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin").resolve(projectName).toString())).stdout()
            : "";

        assertThat(nativeOutput).as(build.stderr()).isEqualTo(jvmOutput);
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
}
