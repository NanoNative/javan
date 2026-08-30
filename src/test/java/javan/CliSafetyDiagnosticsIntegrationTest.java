package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliSafetyDiagnosticsIntegrationTest extends CliIntegrationSupport {
    @Test
    void checkRejectsLiteralNullReceiverBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-null-receiver-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = null;
                    System.out.println(value.length());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "error[JAVAN070]",
            "provable null receiver",
            "java/lang/String.length()I",
            "Replace the null value before this receiver operation."
        );
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.json"))).contains(
            "\"code\": \"JAVAN070\"",
            "\"severity\": \"error\""
        );
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void checkRejectsLiteralNullArrayReceiverBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-null-array-receiver-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = null;
                    System.out.println(values.length);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN070]", "provable null receiver", "arraylength");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void buildRejectsLiteralOutOfBoundsArrayReadBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-out-of-bounds-array-read-build");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] source = new int[2];
                    final int[] values = source;
                    System.out.println(values[2]);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN071]", "provable array index out of bounds", "index 2", "length 2");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void checkRejectsLiteralNegativeArrayReadBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-negative-array-read-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[2];
                    System.out.println(values[-1]);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN071]", "provable array index out of bounds", "index -1", "length 2");
    }

    @Test
    void buildRejectsLiteralStringCharAtOutsideLengthBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-string-char-at-out-of-bounds-build");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String source = "ok";
                    final String value = source;
                    System.out.println(value.charAt(2));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN072]", "provable String.charAt index out of bounds", "index 2", "length 2");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void checkRejectsLiteralNegativeStringCharAtBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-negative-string-char-at-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok".charAt(-1));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN072]", "provable String.charAt index out of bounds", "index -1", "length 2");
    }

    @Test
    void buildRejectsLiteralNullFieldReceiverBeforeNativeGeneration() throws Exception {
        final Path project = project("literal-null-field-receiver-build");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Holder holder = null;
                    System.out.println(holder.value);
                }

                private static final class Holder {
                    private int value = 7;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN070]", "provable null receiver", "com/acme/Main$Holder.value:I");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void checkReportsUnreachableLiteralNullReceiverAsWarning() throws Exception {
        final Path project = project("unreachable-null-receiver-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("safe");
                }

                private static int unused() {
                    final String value = null;
                    return value.length();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.json"))).contains(
            "\"severity\": \"warning\"",
            "\"code\": \"JAVAN170\"",
            "provable null receiver in unreachable code"
        );
    }

    @Test
    void checkReportsUnreachableLiteralOutOfBoundsArrayReadAsWarning() throws Exception {
        final Path project = project("unreachable-out-of-bounds-array-read-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("safe");
                }

                private static int unused() {
                    final int[] values = new int[1];
                    return values[1];
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.json"))).contains(
            "\"severity\": \"warning\"",
            "\"code\": \"JAVAN171\"",
            "provable array index out of bounds in unreachable code"
        );
    }

    @Test
    void checkReportsUnreachableLiteralStringCharAtAsWarning() throws Exception {
        final Path project = project("unreachable-string-char-at-out-of-bounds-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("safe");
                }

                private static char unused() {
                    return "a".charAt(1);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.json"))).contains(
            "\"severity\": \"warning\"",
            "\"code\": \"JAVAN172\"",
            "provable String.charAt index out of bounds in unreachable code"
        );
    }

    @Test
    void checkAcceptsReassignedReceiver() throws Exception {
        final Path project = project("reassigned-null-receiver-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    String reassigned = null;
                    reassigned = "safe";
                    System.out.println(reassigned.length());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(run.stderr()).doesNotContain("JAVAN070");
    }

    @Test
    void checkAcceptsGuardedDynamicReceiver() throws Exception {
        final Path project = project("dynamic-null-receiver-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    String dynamic = null;
                    if (args.length == 0) {
                        dynamic = "safe";
                    }
                    if (dynamic != null) {
                        System.out.println(dynamic.length());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(run.stderr()).doesNotContain("JAVAN070");
    }

    @Test
    void checkAcceptsInBoundsAndDynamicArrayReads() throws Exception {
        final Path project = project("in-bounds-and-dynamic-array-read-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[2];
                    System.out.println(values[1]);

                    int index = 2;
                    if (args.length == 1) {
                        index = 0;
                    }
                    System.out.println(values[index]);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(run.stderr()).doesNotContain("JAVAN071");
    }

    @Test
    void checkAcceptsInBoundsAndDynamicStringCharAt() throws Exception {
        final Path project = project("in-bounds-and-dynamic-string-char-at-check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "ok";
                    System.out.println(value.charAt(1));

                    int index = 2;
                    if (args.length == 1) {
                        index = 0;
                    }
                    System.out.println(value.charAt(index));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(run.stderr()).doesNotContain("JAVAN072");
    }
}
