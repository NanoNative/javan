package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliStringApiIntegrationTest extends CliIntegrationSupport {
    @Test
    void stringValueOfIntBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.valueOf(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-int").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "\"runtimeCallSiteCount\": 1",
                "\"intrinsicCallSiteCount\": 1",
                "\"supportedDirectJdkCallSiteCount\": 0",
                "\"supportedJdkCallSiteCount\": 2",
                "{\"name\": \"PrintStream.println\", \"count\": 1}",
                "{\"name\": \"String.valueOf\", \"count\": 1}",
                "\"unsupportedJdkCallCandidateCount\": 0"
            );
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.md")))
            .contains(
                "Supported reachable JDK call sites: `2`",
                "Runtime-registry reachable call sites: `1`",
                "Supported-direct reachable call sites: `0`",
                "Unsupported reachable call sites: `0`"
            );
    }

    @Test
    void stringValueOfLongBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.valueOf(7L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-long").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.valueOf(1.5f));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-float").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.valueOf(1.5d));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-double").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfBooleanBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-boolean");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.valueOf(true));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-boolean").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.valueOf('A'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = "javan";
                    System.out.println(String.valueOf(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-object").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfNullObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-null-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    System.out.println(String.valueOf(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-null-object").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.println(String.valueOf(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-char-array").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringValueOfCharArrayRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-value-of-char-array-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'x', 'j', 'a', 'v', 'a', 'n', 'y'};
                    System.out.println(String.valueOf(value, 1, 5));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-value-of-char-array-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringCopyValueOfCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-copy-value-of-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.println(String.copyValueOf(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-copy-value-of-char-array").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringCopyValueOfCharArrayRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-copy-value-of-char-array-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'x', 'j', 'a', 'v', 'a', 'n', 'y'};
                    System.out.println(String.copyValueOf(value, 1, 5));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-copy-value-of-char-array-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringDefaultConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-default-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new String().length());
                    System.out.println("[" + new String() + "]");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-default-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringCopyConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-copy-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "javan";
                    System.out.println(new String(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-copy-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringCopyConstructorNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("string-copy-constructor-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = null;
                    System.out.println(new String(value));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/string-copy-constructor-null").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains("null object");
    }

    @Test
    void stringToStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-to-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "javan";
                    System.out.println(value.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-to-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringToStringNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("string-to-string-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = null;
                    System.out.println(value.toString());
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/string-to-string-null").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("[JAVAN-RUNTIME-PANIC]", "detail: null object");
    }

    @Test
    void stringConcatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-concat-method");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String left = "ja";
                    final String right = "van";
                    System.out.println(left.concat(right));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-concat-method").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringConcatNullArgumentFailsClearlyAtRuntime() throws Exception {
        final Path project = project("string-concat-null-argument");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String left = "ja";
                    final String right = null;
                    System.out.println(left.concat(right));
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/string-concat-null-argument").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("[JAVAN-RUNTIME-PANIC]", "detail: null object");
    }

    @Test
    void stringCharArrayConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-char-array-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.println(new String(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-char-array-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringCharArrayConstructorNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("string-char-array-constructor-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] value = null;
                    System.out.println(new String(value));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/string-char-array-constructor-null").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("[JAVAN-RUNTIME-PANIC]", "detail: null array");
    }

    @Test
    void stringBuilderCopyConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-copy-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan");
                    System.out.println(new String(builder));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-copy-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderCopyConstructorNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-copy-constructor-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = null;
                    System.out.println(new String(builder));
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-copy-constructor-null").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("[JAVAN-RUNTIME-PANIC]", "detail: null object");
    }

    @Test
    void stringSubSequenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-sub-sequence");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "javan";
                    final CharSequence slice = value.subSequence(1, 4);
                    System.out.println(slice);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-sub-sequence").toString())).stdout()).isEqualTo(jvmOutput);
    }

}
