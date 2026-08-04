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
final class CliPrintStreamIntegrationTest extends CliIntegrationSupport {
    @Test
    void printStreamPrintCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print('A');
                    System.out.print('B');
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.print(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-char-array").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintlnCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-println-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println('A');
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-println-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintlnCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-println-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-println-char-array").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintCharArrayNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("printstream-print-char-array-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = null;
                    System.out.print(value);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/printstream-print-char-array-null").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("[JAVAN-RUNTIME-PANIC]", "detail: null array");
    }

    @Test
    void printStreamPrintBooleanBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-boolean");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print(true);
                    System.out.print(false);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-boolean").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintIntBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print(12);
                    System.out.print(34);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-int").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintLongBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print(12L);
                    System.out.print(34L);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-long").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print(1.5f);
                    System.out.print(2.5f);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-float").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print(1.5d);
                    System.out.print(2.5d);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-double").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStreamPrintObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("printstream-print-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object left = "left";
                    final Object none = null;
                    System.out.print(left);
                    System.out.print(":");
                    System.out.print(none);
                    System.out.println();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/printstream-print-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left:null\n");
    }

}
