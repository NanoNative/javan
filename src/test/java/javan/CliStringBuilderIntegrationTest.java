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
final class CliStringBuilderIntegrationTest extends CliIntegrationSupport {
    @Test
    void stringBuilderAppendBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append("javan");
                    builder.append('-');
                    builder.append(25);
                    builder.append('-');
                    builder.append(9L);
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan-25-9\n");
    }

    @Test
    void stringBuilderAppendBooleanBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-boolean");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append(true);
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-boolean").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void stringBuilderAppendObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    final Object value = "object";
                    builder.append(value);
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("object\n");
    }

    @Test
    void stringBuilderCapacityConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-capacity-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder(64);
                    builder.append("cap");
                    builder.append('-');
                    builder.append(64);
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-capacity-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("cap-64\n");
    }

    @Test
    void stringBuilderNegativeCapacityFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-negative-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    new StringBuilder(-1);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-negative-capacity").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("negative string builder capacity");
    }

    @Test
    void stringBuilderCharAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-char-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("native");
                    System.out.println(builder.charAt(2));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-char-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("t\n");
    }

    @Test
    void stringBuilderCharAtOutOfBoundsFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-char-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("native");
                    System.out.println(builder.charAt(6));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-char-at-oob").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("string builder index out of bounds");
    }

    @Test
    void stringBuilderSubstringBeginBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-substring-begin");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan native");
                    System.out.println(builder.substring(6));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-substring-begin").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderSubstringRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-substring-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan native");
                    System.out.println(builder.substring(0, 5));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-substring-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderIndexOfStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-index-of-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcabc");
                    System.out.println(builder.indexOf("abc"));
                    System.out.println(builder.indexOf("cab"));
                    System.out.println(builder.indexOf("zzz"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-index-of-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderIndexOfStringFromBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-index-of-string-from");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcabc");
                    System.out.println(builder.indexOf("abc", 1));
                    System.out.println(builder.indexOf("abc", 3));
                    System.out.println(builder.indexOf("", -1));
                    System.out.println(builder.indexOf("", 7));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-index-of-string-from").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderLastIndexOfStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-last-index-of-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcabc");
                    System.out.println(builder.lastIndexOf("abc"));
                    System.out.println(builder.lastIndexOf("cab"));
                    System.out.println(builder.lastIndexOf("zzz"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-last-index-of-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderLastIndexOfStringFromBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-last-index-of-string-from");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcabc");
                    System.out.println(builder.lastIndexOf("abc", 5));
                    System.out.println(builder.lastIndexOf("abc", 1));
                    System.out.println(builder.lastIndexOf("", -1));
                    System.out.println(builder.lastIndexOf("", 7));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-last-index-of-string-from").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderIndexOfNullStringFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-index-of-null-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    System.out.println(builder.indexOf((String) null));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-index-of-null-string").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null string");
    }

    @Test
    void stringBuilderSubSequenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-sub-sequence");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan native");
                    final CharSequence value = builder.subSequence(0, 5);
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-sub-sequence").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderCompareToBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-compare-to");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder left = new StringBuilder("abc");
                    final StringBuilder equal = new StringBuilder("abc");
                    final StringBuilder greater = new StringBuilder("abd");
                    System.out.println(left.compareTo(equal));
                    System.out.println(left.compareTo(greater));
                    System.out.println(greater.compareTo(left));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-compare-to").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderDeleteBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-delete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcdef");
                    System.out.println(builder.delete(2, 4));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-delete").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderDeleteEndBeyondLengthBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-delete-end-beyond-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcdef");
                    System.out.println(builder.delete(2, 20));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-delete-end-beyond-length").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderDeleteCharAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-delete-char-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcdef");
                    System.out.println(builder.deleteCharAt(3));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-delete-char-at").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderReverseBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-reverse");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan");
                    System.out.println(builder.reverse());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-reverse").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderDeleteCharAtOutOfBoundsFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-delete-char-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    System.out.println(builder.deleteCharAt(3));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-delete-char-at-oob").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("string builder delete char index out of bounds");
    }

    @Test
    void stringBuilderInsertStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abef");
                    System.out.println(builder.insert(2, "cd"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abef");
                    System.out.println(builder.insert(2, 'c'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderReplaceStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-replace-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcdef");
                    System.out.println(builder.replace(2, 4, "XY"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-replace-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderReplaceEndBeyondLengthBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-replace-end-beyond-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abcdef");
                    System.out.println(builder.replace(2, 20, "XY"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-replace-end-beyond-length").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertIndexOutOfBoundsFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-insert-index-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    System.out.println(builder.insert(4, "x"));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-index-oob").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("string builder insert index out of bounds");
    }

    @Test
    void stringBuilderSetCharAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-set-char-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    builder.setCharAt(1, 'Z');
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-set-char-at").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderEnsureCapacityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-ensure-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    builder.ensureCapacity(100);
                    builder.append("def");
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-ensure-capacity").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderTrimToSizeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-trim-to-size");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    builder.append("def");
                    builder.trimToSize();
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-trim-to-size").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderSetCharAtOutOfBoundsFailsClearlyAtRuntime() throws Exception {
        final Path project = project("stringbuilder-set-char-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    builder.setCharAt(3, 'Z');
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/stringbuilder-set-char-at-oob").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("string builder set char index out of bounds");
    }

    @Test
    void stringBuilderInsertBooleanBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-boolean");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ac");
                    builder.insert(1, true);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-boolean").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertIntBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ac");
                    builder.insert(1, 42);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-int").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertLongBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ac");
                    builder.insert(1, 1234567890123L);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-long").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ac");
                    builder.insert(1, -1.25f);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-float").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ac");
                    builder.insert(1, -3.5d);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-double").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ab");
                    final Object value = "MID";
                    builder.insert(1, value);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-object").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertNullObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-null-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("ab");
                    final Object value = null;
                    builder.insert(1, value);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-null-object").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderAppendCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    final char[] chars = new char[] {'j', 'a', 'v', 'a', 'n'};
                    builder.append(chars);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-char-array").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderAppendCharArrayRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-char-array-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    final char[] chars = new char[] {'j', 'a', 'v', 'a', 'n'};
                    builder.append(chars, 1, 3);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-char-array-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("jn");
                    final char[] chars = new char[] {'a', 'v', 'a'};
                    builder.insert(1, chars);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-char-array").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderInsertCharArrayRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-insert-char-array-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("jn");
                    final char[] chars = new char[] {'x', 'a', 'v', 'a', 'y'};
                    builder.insert(1, chars, 1, 3);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-insert-char-array-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderCapacityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder(64);
                    builder.append("javan");
                    System.out.println(builder.capacity());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-capacity").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderDefaultCapacityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-default-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    System.out.println(builder.capacity());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-default-capacity").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderStringConstructorCapacityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-string-constructor-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("abc");
                    System.out.println(builder.capacity());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-string-constructor-capacity").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderIsEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-is-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    System.out.println(builder.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-is-empty").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void stringBuilderSetLengthBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-set-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan");
                    builder.setLength(4);
                    System.out.println(builder.length());
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-set-length").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\njava\n");
    }

    @Test
    void stringBuilderCharApisUseUtf16() throws Exception {
        final Path project = project("stringbuilder-utf16-char-apis");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append('a');
                    builder.append((char) 0x03bb);
                    builder.append('b');
                    System.out.println(builder.length());
                    System.out.println((int) builder.charAt(1));
                    builder.insert(2, (char) 0x03b2);
                    System.out.println(builder);
                    builder.deleteCharAt(1);
                    System.out.println(builder);
                    builder.setCharAt(1, (char) 0x03bb);
                    System.out.println(builder);
                    builder.replace(1, 2, Character.toString((char) 0x03b2));
                    System.out.println(builder);
                    builder.setLength(2);
                    System.out.println(builder);
                    final StringBuilder left = new StringBuilder();
                    left.append('a');
                    left.append((char) 0x03bb);
                    final StringBuilder equal = new StringBuilder();
                    equal.append('a');
                    equal.append((char) 0x03bb);
                    final StringBuilder greater = new StringBuilder();
                    greater.append('a');
                    greater.append((char) 0x03b2);
                    System.out.println(left.compareTo(equal));
                    System.out.println(left.compareTo(greater) > 0);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-utf16-char-apis").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n955\naλβb\naβb\naλb\naβb\naβ\n0\ntrue\n");
    }

    @Test
    void stringBuilderAppendFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append(1.5f);
                    builder.append('/');
                    builder.append(2.5f);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-float").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringBuilderAppendDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append(1.5d);
                    builder.append('/');
                    builder.append(2.5d);
                    System.out.println(builder);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-double").toString())).stdout()).isEqualTo(jvmOutput);
    }

}
