package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliJdkSemanticsIntegrationTest extends CliIntegrationSupport {
    @Test
    void objectsRequireNonNullElseReturnsFallbackBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-require-non-null-else-fallback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElse(null, "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-fallback").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void objectsRequireNonNullElseReturnsPrimaryValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-require-non-null-else-primary");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElse("value", "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-primary").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void objectsRequireNonNullElseGetReturnsFallbackBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-require-non-null-else-get-fallback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElseGet(null, () -> "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-fallback").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void objectsRequireNonNullElseGetReturnsPrimaryValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-require-non-null-else-get-primary");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElseGet("value", () -> "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-primary").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void objectsIsNullWithNullBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-is-null-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.isNull(null));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-is-null-null").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void objectsIsNullWithValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-is-null-value");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.isNull("value"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-is-null-value").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n");
    }

    @Test
    void objectsNonNullWithNullBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-non-null-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.nonNull(null));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-non-null-null").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n");
    }

    @Test
    void objectsNonNullWithValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-non-null-value");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.nonNull("value"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-non-null-value").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void objectsToStringObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-to-string-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.toString(Integer.valueOf(7)));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-to-string-object").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void objectsToStringDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-to-string-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.toString(null, "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-to-string-default").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void stringIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("string-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "javan";
                    final int code = value.charAt(1);
                    System.out.println(value.length());
                    System.out.println(code);
                    System.out.println(value.equals("javan"));
                    System.out.println(value.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("5\n97\ntrue\nfalse\n");
    }

    @Test
    void stringContainsIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".contains("native"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-contains").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void stringFromCharArrayRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-from-char-array-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] chars = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.println(new String(chars, 1, 3));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-from-char-array-range").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ava\n");
    }

    @Test
    void stringHashCodeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-hash-code");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".hashCode());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-hash-code").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("100899468\n");
    }

    @Test
    void stringHashCodeFromUtf16CharArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-hash-code-utf16-char-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] chars = new char[] {'x', '\\uD83D', '\\uDE42', 'y'};
                    System.out.println(new String(chars).hashCode());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-hash-code-utf16-char-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("58536956\n");
    }

    @Test
    void stringConstableMethodsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("string-constable-methods");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Throwable {
                    final String value = "javan";
                    System.out.println(value.describeConstable().orElseThrow());
                    System.out.println(value.resolveConstantDesc(null));
                    final Object widened = value.resolveConstantDesc(null);
                    System.out.println(widened);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-constable-methods").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\njavan\njavan\n");
    }

    @Test
    void integerConstableMethodsBuildAndMatchJvmOutput() throws Exception {
        assertNumericWrapperConstableMethodsBuildAndMatchJvmOutput("integer-constable-methods", "Integer", "Integer.valueOf(7)");
    }

    @Test
    void longConstableMethodsBuildAndMatchJvmOutput() throws Exception {
        assertNumericWrapperConstableMethodsBuildAndMatchJvmOutput("long-constable-methods", "Long", "Long.valueOf(9L)");
    }

    @Test
    void floatConstableMethodsBuildAndMatchJvmOutput() throws Exception {
        assertNumericWrapperConstableMethodsBuildAndMatchJvmOutput("float-constable-methods", "Float", "Float.valueOf(1.5f)");
    }

    @Test
    void doubleConstableMethodsBuildAndMatchJvmOutput() throws Exception {
        assertNumericWrapperConstableMethodsBuildAndMatchJvmOutput("double-constable-methods", "Double", "Double.valueOf(2.5d)");
    }

    @Test
    void booleanWrapperPrintableBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveWrapperPrintableBuildAndMatchJvmOutput("boolean-wrapper-printable", "Boolean", "Boolean.valueOf(true)");
    }

    @Test
    void byteWrapperPrintableBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveWrapperPrintableBuildAndMatchJvmOutput("byte-wrapper-printable", "Byte", "Byte.valueOf((byte) 12)");
    }

    @Test
    void shortWrapperPrintableBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveWrapperPrintableBuildAndMatchJvmOutput("short-wrapper-printable", "Short", "Short.valueOf((short) 34)");
    }

    @Test
    void characterWrapperPrintableBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveWrapperPrintableBuildAndMatchJvmOutput("character-wrapper-printable", "Character", "Character.valueOf('j')");
    }

    @Test
    void classLiteralGetNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("class-literal-get-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-literal-get-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang.String\n");
    }

    @Test
    void primitiveClassGetNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-class-get-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int.class.getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-class-get-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("int\n");
    }

    @Test
    void wrapperClassLiteralGetNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("wrapper-class-literal-get-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.class.getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/wrapper-class-literal-get-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang.Integer\n");
    }

    @Test
    void stringClassDescriptorStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-class-descriptor-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.descriptorString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-class-descriptor-string").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("Ljava/lang/String;\n");
    }

    @Test
    void objectArrayClassDescriptorStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-class-descriptor-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String[].class.descriptorString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-class-descriptor-string").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[Ljava/lang/String;\n");
    }

    @Test
    void primitiveClassDescriptorStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-class-descriptor-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int.class.descriptorString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-class-descriptor-string").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("I\n");
    }

    @Test
    void objectArrayComponentTypeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-component-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String[].class.componentType().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-component-type").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang.String\n");
    }

    @Test
    void primitiveArrayComponentTypeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-array-component-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int[].class.componentType().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-array-component-type").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("int\n");
    }

    @Test
    void objectArrayGetComponentTypeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-get-component-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String[].class.getComponentType().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-get-component-type").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang.String\n");
    }

    @Test
    void primitiveClassIsPrimitiveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-class-is-primitive");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int.class.isPrimitive());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-class-is-primitive").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void referenceClassIsPrimitiveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("reference-class-is-primitive");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.isPrimitive());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/reference-class-is-primitive").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n");
    }

    @Test
    void voidTypeDescriptorStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("void-type-descriptor-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Void.TYPE.descriptorString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/void-type-descriptor-string").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("V\n");
    }

    @Test
    void voidTypeIsPrimitiveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("void-type-is-primitive");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Void.TYPE.isPrimitive());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/void-type-is-primitive").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void voidTypeGetNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("void-type-get-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Void.TYPE.getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/void-type-get-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("void\n");
    }

    @Test
    void classGetTypeNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("class-get-type-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.getTypeName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-get-type-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang.String\n");
    }

    @Test
    void objectArrayClassGetTypeNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-class-get-type-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String[].class.getTypeName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-class-get-type-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang.String[]\n");
    }

    @Test
    void primitiveNestedArrayClassGetTypeNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-nested-array-class-get-type-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int[][].class.getTypeName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-nested-array-class-get-type-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("int[][]\n");
    }

    @Test
    void voidTypeGetTypeNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("void-type-get-type-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Void.TYPE.getTypeName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/void-type-get-type-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("void\n");
    }

    @Test
    void classGetPackageNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("class-get-package-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.getPackageName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-get-package-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang\n");
    }

    @Test
    void classGetSimpleNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("class-get-simple-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.getSimpleName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-get-simple-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("String\n");
    }

    @Test
    void objectArrayClassGetSimpleNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-class-get-simple-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String[].class.getSimpleName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-class-get-simple-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("String[]\n");
    }

    @Test
    void primitiveNestedArrayClassGetSimpleNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-nested-array-class-get-simple-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int[][].class.getSimpleName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-nested-array-class-get-simple-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("int[][]\n");
    }

    @Test
    void memberClassGetSimpleNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("member-class-get-simple-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Map.Entry.class.getSimpleName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/member-class-get-simple-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("Entry\n");
    }

    @Test
    void memberArrayClassGetSimpleNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("member-array-class-get-simple-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Map.Entry[].class.getSimpleName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/member-array-class-get-simple-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("Entry[]\n");
    }

    @Test
    void voidTypeGetSimpleNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("void-type-get-simple-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Void.TYPE.getSimpleName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/void-type-get-simple-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("void\n");
    }

    @Test
    void objectArrayClassGetPackageNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-class-get-package-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String[].class.getPackageName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-class-get-package-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang\n");
    }

    @Test
    void primitiveClassGetPackageNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-class-get-package-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int.class.getPackageName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-class-get-package-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.lang\n");
    }

    @Test
    void memberClassGetPackageNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("member-class-get-package-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Map.Entry.class.getPackageName());
                    System.out.println(Map.Entry[].class.getPackageName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/member-class-get-package-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("java.util\njava.util\n");
    }

    @Test
    void stringClassArrayTypeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-class-array-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(String.class.arrayType().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-class-array-type").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[Ljava.lang.String;\n");
    }

    @Test
    void primitiveClassArrayTypeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-class-array-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(int.class.arrayType().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-class-array-type").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[I\n");
    }

    @Test
    void generatedClassArrayTypeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("generated-class-array-type");
        writeJava(project, "com.acme.Widget", """
            package com.acme;

            public final class Widget {
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Widget.class.arrayType().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/generated-class-array-type").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[Lcom.acme.Widget;\n");
    }

    @Test
    void objectClassIsNotAssignableFromPrimitiveClassBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-class-not-assignable-from-primitive-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Object.class.isAssignableFrom(int.class));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-class-not-assignable-from-primitive-class").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n");
    }

    @Test
    void objectArrayGetClassNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-array-get-class-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new String[2].getClass().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-get-class-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[Ljava.lang.String;\n");
    }

    @Test
    void nestedPrimitiveArrayGetClassNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("nested-primitive-array-get-class-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new int[1][].getClass().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/nested-primitive-array-get-class-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[[I\n");
    }

    @Test
    void mainArgsGetClassNameBuildsAndMatchesExpectedRuntimeOutput() throws Exception {
        final Path project = project("main-args-get-class-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(args.getClass().getName());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(
            project.resolve(".javan/bin/main-args-get-class-name").toString(),
            "one",
            "two"
        )).stdout()).isEqualTo("[Ljava.lang.String;\n");
    }

    @Test
    void classIsInstanceAndCastBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("class-is-instance-cast");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Class<?> target = String.class;
                    final Object value = "value";
                    System.out.println(target.isInstance(value));
                    System.out.println(target.isInstance(42));
                    System.out.println(target.cast(value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-is-instance-cast").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\nvalue\n");
    }

    @Test
    void generatedObjectGetClassNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("generated-object-get-class-name");
        writeJava(project, "com.acme.Widget", """
            package com.acme;

            public final class Widget {
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new Widget();
                    System.out.println(value.getClass().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/generated-object-get-class-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("com.acme.Widget\n");
    }

    @Test
    void generatedClassLiteralGetNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("generated-class-literal-get-name");
        writeJava(project, "com.acme.Widget", """
            package com.acme;

            public final class Widget {
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Widget.class.getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/generated-class-literal-get-name").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("com.acme.Widget\n");
    }

    @Test
    void dateTimeFormatterBuilderStaticInitBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("datetime-formatter-builder-static-init");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.time.format.DateTimeFormatter;
            import java.time.format.DateTimeFormatterBuilder;
            import java.time.format.TextStyle;
            import java.util.Locale;

            public final class Main {
                private static final DateTimeFormatter[] FORMATTERS = {
                    DateTimeFormatter.ISO_ZONED_DATE_TIME,
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                    new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("EEE MMM dd HH:mm:ss")
                        .appendZoneText(TextStyle.SHORT)
                        .appendPattern(" yyyy")
                        .toFormatter(Locale.ENGLISH)
                };

                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(FORMATTERS.length);
                    System.out.println(FORMATTERS[0] != null);
                    System.out.println(FORMATTERS[1] != null);
                    System.out.println(FORMATTERS[2] != null);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/datetime-formatter-builder-static-init").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\ntrue\ntrue\ntrue\n");
    }

    @Test
    void collectionsEmptyMapAndEntrySetBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("collections-empty-map-entry-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<Class<?>, String> values = new HashMap<>();
                    values.put(Object.class, "object");
                    for (final Map.Entry<Class<?>, String> entry : values.entrySet()) {
                        System.out.println(entry.getKey().isAssignableFrom(String.class));
                        System.out.println(entry.getValue());
                    }
                    System.out.println(Collections.emptyMap().isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-empty-map-entry-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nobject\ntrue\n");
    }

    @Test
    void mapOfEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Map.of().isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-empty").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void collectionsEmptyListBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-empty-list");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = Collections.emptyList();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-empty-list").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\n");
    }

    @Test
    void collectionsEmptySetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-empty-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Collections.emptySet();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.contains("x"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-empty-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\nfalse\n");
    }

    @Test
    void collectionsUnmodifiableSetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-unmodifiable-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.HashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> mutable = new HashSet<>();
                    mutable.add("x");
                    final Set<String> values = Collections.unmodifiableSet(mutable);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.contains("x"));
                    mutable.add("y");
                    System.out.println(values.contains("y"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-unmodifiable-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\ntrue\n2\n");
    }

    @Test
    void collectionsUnmodifiableCollectionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-unmodifiable-collection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.Collections;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> mutable = new ArrayList<>();
                    mutable.add("x");
                    final Collection<String> values = Collections.unmodifiableCollection(mutable);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.contains("x"));
                    mutable.add("y");
                    System.out.println(values.contains("y"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-unmodifiable-collection").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\ntrue\n2\n");
    }

    @Test
    void collectionsUnmodifiableCollectionRejectsMutationAtRuntime() throws Exception {
        assertCollectionsUnmodifiableCollectionFailureAtRuntime(
            "collections-unmodifiable-collection-add",
            """
            final ArrayList<String> mutable = new ArrayList<>();
            mutable.add("x");
            final Collection<String> values = Collections.unmodifiableCollection(mutable);
            ((java.util.List<String>) values).add("y");
            """,
            "unsupported operation on immutable list"
        );
    }

    @Test
    void arrayListDirectOwnerRemoveFirstRejectsEmptyListAtRuntime() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-first-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>();
                    values.removeFirst();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-first-empty").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("list is empty");
    }

    @Test
    void arrayListDirectOwnerRemoveAtRejectsOutOfBoundsAtRuntime() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left"));
                    values.remove(1);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-at-oob").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("index out of bounds");
    }

    @Test
    void listRemoveAtRejectsOutOfBoundsAtRuntime() throws Exception {
        final Path project = project("list-remove-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left"));
                    values.remove(1);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/list-remove-at-oob").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("index out of bounds");
    }

    @Test
    void arrayListDirectOwnerAddAllAtRejectsOutOfBoundsAtRuntime() throws Exception {
        final Path project = project("arraylist-direct-owner-add-all-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left"));
                    values.addAll(2, List.of("right"));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-add-all-at-oob").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("index out of bounds");
    }

    @Test
    void listAddAllAtRejectsOutOfBoundsAtRuntime() throws Exception {
        final Path project = project("list-add-all-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left"));
                    values.addAll(2, List.of("right"));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/list-add-all-at-oob").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("index out of bounds");
    }

    @Test
    void abstractListDirectOwnerAddAllAtRejectsOutOfBoundsAtRuntime() throws Exception {
        final Path project = project("abstractlist-direct-owner-add-all-at-oob");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left"));
                    values.addAll(2, List.of("right"));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-add-all-at-oob").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("index out of bounds");
    }

    @Test
    void iteratorRemoveRejectsBeforeNextAtRuntime() throws Exception {
        final Path project = project("iterator-remove-before-next");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Iterator<String> iterator = new ArrayList<>(List.of("left")).iterator();
                    iterator.remove();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/iterator-remove-before-next").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("invalid iterator state");
    }

    @Test
    void collectionToArrayReturnsSnapshotAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-to-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> mutable = new ArrayList<>();
                    mutable.add("x");
                    final Collection<String> values = mutable;
                    final Object[] snapshot = values.toArray();
                    mutable.add("y");
                    System.out.println(snapshot.length);
                    System.out.println(snapshot[0]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-to-array").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\nx\n");
    }

    @Test
    void listToArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-to-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("x");
                    values.add("y");
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot.length);
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-to-array").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\nx\ny\n");
    }

    @Test
    void setToArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-to-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new LinkedHashSet<>();
                    values.add("x");
                    values.add("y");
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot.length);
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-to-array").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\nx\ny\n");
    }

    @Test
    void collectionContainsAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-contains-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new ArrayList<>(List.of("a", "b", "c"));
                    System.out.println(values.containsAll(List.of("a", "c")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-contains-all").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void listContainsAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-contains-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("a", "b"));
                    System.out.println(values.containsAll(List.of("a", "c")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-contains-all").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n");
    }

    @Test
    void setContainsAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-contains-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new LinkedHashSet<>(List.of("a", "b", "c"));
                    System.out.println(values.containsAll(List.of("a", "b")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-contains-all").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void hashSetCollectionConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-collection-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new HashSet<>(List.of("a", "b", "a"));
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-collection-constructor").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\ntrue\n");
    }

    @Test
    void linkedHashSetCollectionConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-collection-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new LinkedHashSet<>(List.of("a", "b", "a"));
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot.length);
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-collection-constructor").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\na\nb\n");
    }

    @Test
    void hashMapMapConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-map-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> source = new LinkedHashMap<>();
                    source.put("a", 1);
                    source.put("b", 2);
                    final Map<String, Integer> copy = new HashMap<>(source);
                    System.out.println(copy.size());
                    System.out.println(copy.get("a"));
                    System.out.println(copy.get("b"));
                    System.out.println(copy.containsKey("a"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-map-constructor").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1\n2\ntrue\n");
    }

    @Test
    void linkedHashMapMapConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-map-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> source = new LinkedHashMap<>();
                    source.put("third", "c");
                    source.put("first", "a");
                    source.put("second", "b");
                    final LinkedHashMap<String, String> copy = new LinkedHashMap<>(source);
                    final Object[] keys = copy.keySet().toArray();
                    System.out.println(copy.size());
                    System.out.println(keys[0]);
                    System.out.println(keys[1]);
                    System.out.println(keys[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-map-constructor").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\nthird\nfirst\nsecond\n");
    }

    @Test
    void concurrentHashMapMapConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("concurrenthashmap-map-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> source = new LinkedHashMap<>();
                    source.put("a", 1);
                    source.put("b", 2);
                    final ConcurrentHashMap<String, Integer> copy = new ConcurrentHashMap<>(source);
                    System.out.println(copy.size());
                    System.out.println(copy.get("a"));
                    System.out.println(copy.get("b"));
                    System.out.println(copy.containsKey("a"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/concurrenthashmap-map-constructor").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1\n2\ntrue\n");
    }

    @Test
    void enumMapClassConstructorBuildsAndUsesNaturalEnumOrder() throws Exception {
        final Path project = project("enummap-class-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;
            import java.util.Map;

            public final class Main {
                private enum Phase {
                    FIRST,
                    SECOND,
                    THIRD
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.THIRD, "three");
                    values.put(Phase.FIRST, null);
                    values.put(Phase.SECOND, "two");
                    final Object[] keys = values.keySet().toArray();
                    System.out.println(keys[0]);
                    System.out.println(keys[1]);
                    System.out.println(keys[2]);
                    System.out.println(values.get(Phase.FIRST));
                    System.out.println(values.get(Phase.THIRD));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-class-constructor").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapInvalidLookupKeysRemainAbsent() throws Exception {
        final Path project = project("enummap-invalid-lookup-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;
            import java.util.Map;

            public final class Main {
                private enum Phase {
                    FIRST
                }

                private enum Other {
                    FIRST
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.FIRST, "first");
                    System.out.println(values.get(Other.FIRST));
                    System.out.println(values.containsKey(Other.FIRST));
                    System.out.println(values.remove(Other.FIRST));
                    System.out.println(values.containsKey(null));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-invalid-lookup-keys").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapRejectsNullPutKeyAtRuntime() throws Exception {
        final Path project = enumMapFailureProject(
            "enummap-null-put-key",
            "final Map<Phase, String> values = new EnumMap<>(Phase.class); values.put(null, \"value\");"
        );
        final CliRun run = run(tempDir, "build", project.toString());
        final String outcome = run.exitCode() == 0
            ? runtimeFailureOutcome(process(
                project,
                List.of(project.resolve(".javan/bin/enummap-null-put-key").toString())
            ), "null EnumMap key")
            : "build-failure";

        assertThat(outcome).isEqualTo("runtime-failure:null EnumMap key");
    }

    @Test
    void enumMapRejectsWrongEnumPutKeyAtRuntime() throws Exception {
        final Path project = enumMapFailureProject(
            "enummap-wrong-put-key",
            """
            final Map values = new EnumMap<Phase, String>(Phase.class);
            values.put(Other.FIRST, "value");
            """
        );
        final CliRun run = run(tempDir, "build", project.toString());
        final String outcome = run.exitCode() == 0
            ? runtimeFailureOutcome(process(
                project,
                List.of(project.resolve(".javan/bin/enummap-wrong-put-key").toString())
            ), "EnumMap key type mismatch")
            : "build-failure";

        assertThat(outcome).isEqualTo("runtime-failure:EnumMap key type mismatch");
    }

    @Test
    void enumMapRejectsEnumNameStringAsKeyAtRuntime() throws Exception {
        final Path project = enumMapFailureProject(
            "enummap-name-string-key",
            """
            final EnumMap values = new EnumMap<Phase, String>(Phase.class);
            values.put(Phase.FIRST.name(), "value");
            """
        );
        final CliRun run = run(tempDir, "build", project.toString());
        final String outcome = run.exitCode() == 0
            ? runtimeFailureOutcome(process(
                project,
                List.of(project.resolve(".javan/bin/enummap-name-string-key").toString())
            ), "EnumMap key type mismatch")
            : "build-failure";

        assertThat(outcome).isEqualTo("runtime-failure:EnumMap key type mismatch");
    }

    @Test
    void enumMapRejectsNullKeyClassAtRuntime() throws Exception {
        final Path project = enumMapFailureProject(
            "enummap-null-key-class",
            "new EnumMap((Class) null);"
        );
        final CliRun run = run(tempDir, "build", project.toString());
        final String outcome = run.exitCode() == 0
            ? runtimeFailureOutcome(process(
                project,
                List.of(project.resolve(".javan/bin/enummap-null-key-class").toString())
            ), "null EnumMap key type")
            : "build-failure";

        assertThat(outcome).isEqualTo("runtime-failure:null EnumMap key type");
    }

    @Test
    void enumMapRejectsNonEnumKeyClassAtRuntime() throws Exception {
        final Path project = enumMapFailureProject(
            "enummap-non-enum-key-class",
            "new EnumMap((Class) String.class);"
        );
        final CliRun run = run(tempDir, "build", project.toString());
        final String outcome = run.exitCode() == 0
            ? runtimeFailureOutcome(process(
                project,
                List.of(project.resolve(".javan/bin/enummap-non-enum-key-class").toString())
            ), "EnumMap key type is not an enum")
            : "build-failure";

        assertThat(outcome).isEqualTo("runtime-failure:EnumMap key type is not an enum");
    }

    @Test
    void enumMapAcceptsEmptyEnumKeyClass() throws Exception {
        final Path project = project("enummap-empty-enum-key-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;
            import java.util.Map;

            public final class Main {
                private enum Empty {
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<Empty, String> values = new EnumMap<>(Empty.class);
                    System.out.println(values.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-empty-enum-key-class").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapTypedSizeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enummap-typed-size");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;

            public final class Main {
                private enum Phase {
                    FIRST
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final EnumMap<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.FIRST, "first");
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-typed-size").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapTypedGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enummap-typed-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;

            public final class Main {
                private enum Phase {
                    FIRST
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final EnumMap<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.FIRST, "first");
                    System.out.println(values.get(Phase.FIRST));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-typed-get").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapTypedContainsKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enummap-typed-contains-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;

            public final class Main {
                private enum Phase {
                    FIRST
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final EnumMap<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.FIRST, "first");
                    System.out.println(values.containsKey(Phase.FIRST));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-typed-contains-key").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapTypedRemoveAndReinsertPreserveNaturalOrder() throws Exception {
        final Path project = project("enummap-typed-remove-reinsert");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;
            import java.util.Map;

            public final class Main {
                private enum Phase {
                    FIRST,
                    SECOND,
                    THIRD
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final EnumMap<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.THIRD, "third");
                    values.put(Phase.FIRST, "first");
                    values.remove(Phase.FIRST);
                    values.put(Phase.SECOND, "second");
                    final Object[] keys = ((Map<Phase, String>) values).keySet().toArray();
                    System.out.println(keys[0]);
                    System.out.println(keys[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/enummap-typed-remove-reinsert").toString())).stdout()
            : run.stderr();

        assertThat(nativeOutput).isEqualTo(jvmOutput);
    }

    @Test
    void enumMapPutAllRejectsWrongEnumDomainAtRuntime() throws Exception {
        final Path project = enumMapFailureProject(
            "enummap-put-all-wrong-domain",
            """
            final Map source = new HashMap();
            source.put(Other.FIRST, "value");
            final Map<Phase, String> values = new EnumMap<>(Phase.class);
            values.putAll(source);
            """
        );
        final CliRun run = run(tempDir, "build", project.toString());
        final String outcome = run.exitCode() == 0
            ? runtimeFailureOutcome(process(
                project,
                List.of(project.resolve(".javan/bin/enummap-put-all-wrong-domain").toString())
            ), "EnumMap key type mismatch")
            : "build-failure";

        assertThat(outcome).isEqualTo("runtime-failure:EnumMap key type mismatch");
    }

    @Test
    void enumMapEntriesSurviveGcStress() throws Exception {
        final Path project = project("enummap-gc-stress");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;
            import java.util.Map;

            public final class Main {
                private enum Phase {
                    FIRST,
                    SECOND
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<Phase, String> values = new EnumMap<>(Phase.class);
                    values.put(Phase.SECOND, new String("survives"));
                    for (int index = 0; index < 64; index++) {
                        new String("pressure-" + index);
                    }
                    System.out.println(values.get(Phase.SECOND));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String output = run.exitCode() == 0
            ? process(
                project,
                List.of(project.resolve(".javan/bin/enummap-gc-stress").toString()),
                defaultProcessTimeout(),
                java.util.Map.of("JAVAN_GC_STRESS", "1")
            ).stdout()
            : run.stderr();

        assertThat(output).isEqualTo("survives\n");
    }

    @Test
    void hashMapLoadFactorConstructorRejectsZeroLoadFactorAtRuntime() throws Exception {
        assertMapConstructorFailureAtRuntime(
            "hashmap-load-factor-constructor-zero-load-factor",
            "import java.util.HashMap;",
            "new HashMap<String, String>(16, 0.0f);",
            "invalid map load factor"
        );
    }

    @Test
    void linkedHashMapLoadFactorConstructorRejectsZeroLoadFactorAtRuntime() throws Exception {
        assertMapConstructorFailureAtRuntime(
            "linkedhashmap-load-factor-constructor-zero-load-factor",
            "import java.util.LinkedHashMap;",
            "new LinkedHashMap<String, String>(8, 0.0f);",
            "invalid map load factor"
        );
    }

    @Test
    void concurrentHashMapLoadFactorConstructorRejectsZeroLoadFactorAtRuntime() throws Exception {
        assertMapConstructorFailureAtRuntime(
            "concurrenthashmap-load-factor-constructor-zero-load-factor",
            "import java.util.concurrent.ConcurrentHashMap;",
            "new ConcurrentHashMap<String, String>(4, 0.0f);",
            "invalid map load factor"
        );
    }

    @Test
    void concurrentHashMapConcurrencyLevelConstructorRejectsNonPositiveConcurrencyLevelAtRuntime() throws Exception {
        assertMapConstructorFailureAtRuntime(
            "concurrenthashmap-concurrency-level-constructor-zero-concurrency",
            "import java.util.concurrent.ConcurrentHashMap;",
            "new ConcurrentHashMap<String, String>(4, 0.75f, 0);",
            "non-positive map concurrency level"
        );
    }

    @Test
    void hashMapNewHashMapStaticFactoryRejectsNegativeMappingsAtRuntime() throws Exception {
        assertMapStaticFactoryFailureAtRuntime(
            "hashmap-static-factory-negative-mappings",
            "import java.util.HashMap;",
            "final HashMap<String, String> values = HashMap.newHashMap(-1); System.out.println(values.size());",
            "Negative number of mappings: -1"
        );
    }

    @Test
    void linkedHashMapNewLinkedHashMapStaticFactoryRejectsNegativeMappingsAtRuntime() throws Exception {
        assertMapStaticFactoryFailureAtRuntime(
            "linkedhashmap-static-factory-negative-mappings",
            "import java.util.LinkedHashMap;",
            "final LinkedHashMap<String, String> values = LinkedHashMap.newLinkedHashMap(-1); System.out.println(values.size());",
            "Negative number of mappings: -1"
        );
    }

    @Test
    void hashSetNewHashSetStaticFactoryRejectsNegativeElementsAtRuntime() throws Exception {
        assertSetStaticFactoryFailureAtRuntime(
            "hashset-static-factory-negative-elements",
            "import java.util.HashSet;",
            "HashSet.<String>newHashSet(-1);",
            "Negative number of elements: -1"
        );
    }

    @Test
    void linkedHashSetNewLinkedHashSetStaticFactoryRejectsNegativeElementsAtRuntime() throws Exception {
        assertSetStaticFactoryFailureAtRuntime(
            "linkedhashset-static-factory-negative-elements",
            "import java.util.LinkedHashSet;",
            "LinkedHashSet.<String>newLinkedHashSet(-1);",
            "Negative number of elements: -1"
        );
    }

    @Test
    void hashSetCapacityConstructorRejectsNegativeCapacityAtRuntime() throws Exception {
        assertSetConstructorFailureAtRuntime(
            "hashset-capacity-constructor-negative-capacity",
            "import java.util.HashSet;",
            "new HashSet<String>(-1);",
            "Illegal initial capacity: -1"
        );
    }

    @Test
    void linkedHashSetCapacityConstructorRejectsNegativeCapacityAtRuntime() throws Exception {
        assertSetConstructorFailureAtRuntime(
            "linkedhashset-capacity-constructor-negative-capacity",
            "import java.util.LinkedHashSet;",
            "new LinkedHashSet<String>(-1);",
            "Illegal initial capacity: -1"
        );
    }

    @Test
    void hashSetLoadFactorConstructorRejectsZeroLoadFactorAtRuntime() throws Exception {
        assertSetConstructorFailureAtRuntime(
            "hashset-load-factor-constructor-zero-load-factor",
            "import java.util.HashSet;",
            "new HashSet<String>(16, 0.0f);",
            "Illegal load factor: 0.0"
        );
    }

    @Test
    void linkedHashSetLoadFactorConstructorRejectsZeroLoadFactorAtRuntime() throws Exception {
        assertSetConstructorFailureAtRuntime(
            "linkedhashset-load-factor-constructor-zero-load-factor",
            "import java.util.LinkedHashSet;",
            "new LinkedHashSet<String>(8, 0.0f);",
            "Illegal load factor: 0.0"
        );
    }

    @Test
    void collectionAddAllUsesSetSemanticsForSetReceiverAtRuntime() throws Exception {
        final Path project = project("collection-add-all-set-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new LinkedHashSet<>(List.of("b", "a"));
                    System.out.println(values.addAll(List.of("a")));
                    System.out.println(values.addAll(List.of("c", "b")));
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                    System.out.println(snapshot[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-add-all-set-receiver").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\ntrue\nb\na\nc\n");
    }

    @Test
    void collectionAddUsesSetSemanticsForSetReceiverAtRuntime() throws Exception {
        final Path project = project("collection-add-set-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new LinkedHashSet<>(List.of("b", "a"));
                    System.out.println(values.add("a"));
                    System.out.println(values.add("c"));
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                    System.out.println(snapshot[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-add-set-receiver").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\ntrue\nb\na\nc\n");
    }

    @Test
    void collectionRemoveAllUsesSetSemanticsForSetReceiverAtRuntime() throws Exception {
        final Path project = project("collection-remove-all-set-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new LinkedHashSet<>(List.of("b", "a", "c"));
                    System.out.println(values.removeAll(List.of("a", "x")));
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-remove-all-set-receiver").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nb\nc\n");
    }

    @Test
    void collectionRetainAllUsesSetSemanticsForSetReceiverAtRuntime() throws Exception {
        final Path project = project("collection-retain-all-set-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new LinkedHashSet<>(List.of("b", "a", "c"));
                    System.out.println(values.retainAll(List.of("c", "b", "x")));
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-retain-all-set-receiver").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nb\nc\n");
    }

    @Test
    void collectionsUnmodifiableCollectionRejectsDirectAddAtRuntime() throws Exception {
        assertCollectionsUnmodifiableCollectionFailureAtRuntime(
            "collections-unmodifiable-collection-direct-add",
            """
            final ArrayList<String> mutable = new ArrayList<>();
            mutable.add("x");
            final Collection<String> values = Collections.unmodifiableCollection(mutable);
            values.add("y");
            """,
            "unsupported operation on immutable list"
        );
    }

    @Test
    void collectionsUnmodifiableCollectionRejectsDirectRemoveAllAtRuntime() throws Exception {
        assertCollectionsUnmodifiableCollectionFailureAtRuntime(
            "collections-unmodifiable-collection-direct-remove-all",
            """
            final ArrayList<String> mutable = new ArrayList<>();
            mutable.add("x");
            final Collection<String> values = Collections.unmodifiableCollection(mutable);
            values.removeAll(java.util.List.of("x"));
            """,
            "unsupported operation on immutable list"
        );
    }

    @Test
    void collectionsUnmodifiableCollectionRejectsDirectRetainAllAtRuntime() throws Exception {
        assertCollectionsUnmodifiableCollectionFailureAtRuntime(
            "collections-unmodifiable-collection-direct-retain-all",
            """
            final ArrayList<String> mutable = new ArrayList<>();
            mutable.add("x");
            final Collection<String> values = Collections.unmodifiableCollection(mutable);
            values.retainAll(java.util.List.of("x"));
            """,
            "unsupported operation on immutable list"
        );
    }

    @Test
    void collectionsUnmodifiableListBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-unmodifiable-list");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> mutable = new ArrayList<>();
                    mutable.add("x");
                    final List<String> values = Collections.unmodifiableList(mutable);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.get(0));
                    mutable.add("y");
                    System.out.println(values.get(1));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-unmodifiable-list").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\nx\ny\n2\n");
    }

    @Test
    void collectionsUnmodifiableListRejectsMutationAtRuntime() throws Exception {
        assertCollectionsUnmodifiableListFailureAtRuntime(
            "collections-unmodifiable-list-add",
            """
            final List<String> mutable = new ArrayList<>();
            mutable.add("x");
            final List<String> values = Collections.unmodifiableList(mutable);
            values.add("y");
            """,
            "unsupported operation on immutable list"
        );
    }

    @Test
    void collectionsSingletonSetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-singleton-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Collections.singleton("x");
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.contains("x"));
                    System.out.println(values.contains("y"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-singleton-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\nfalse\n");
    }

    @Test
    void collectionsSingletonListBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-singleton-list");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = Collections.singletonList("x");
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.get(0));
                    System.out.println(values.contains("x"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-singleton-list").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\nx\ntrue\n");
    }

    @Test
    void collectionsSingletonMapBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-singleton-map");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Collections.singletonMap("x", 7);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("x"));
                    System.out.println(values.get("x"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-singleton-map").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\n7\n");
    }

    @Test
    void collectionsUnmodifiableMapBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-unmodifiable-map");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> mutable = new HashMap<>();
                    mutable.put("x", 7);
                    final Map<String, Integer> values = Collections.unmodifiableMap(mutable);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("x"));
                    System.out.println(values.get("x"));
                    mutable.put("y", 9);
                    System.out.println(values.containsKey("y"));
                    System.out.println(values.get("y"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-unmodifiable-map").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\n7\ntrue\n9\n2\n");
    }

    @Test
    void collectionsUnmodifiableMapRejectsMutationAtRuntime() throws Exception {
        assertCollectionsUnmodifiableMapFailureAtRuntime(
            "collections-unmodifiable-map-put",
            """
            final Map<String, Integer> mutable = new HashMap<>();
            mutable.put("x", 7);
            final Map<String, Integer> values = Collections.unmodifiableMap(mutable);
            values.put("y", 9);
            """,
            "unsupported operation on immutable map"
        );
    }

    @Test
    void mapOfSingletonBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-singleton");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("x", 7);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("x"));
                    System.out.println(values.get("x"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-singleton").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\n7\n");
    }

    @Test
    void mapEntryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-entry");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map.Entry<String, Integer> entry = Map.entry("alpha", 7);
                    System.out.println(entry instanceof Map.Entry);
                    System.out.println(entry.getKey());
                    System.out.println(entry.getValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-entry").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nalpha\n7\n");
    }

    @Test
    void mapEntryNullKeyFailsAtRuntime() throws Exception {
        assertMapEntryFailureAtRuntime("map-entry-null-key", "Map.entry((String) null, 7);", "null Map.entry component");
    }

    @Test
    void mapEntryNullValueFailsAtRuntime() throws Exception {
        assertMapEntryFailureAtRuntime("map-entry-null-value", "Map.entry(\"alpha\", (Integer) null);", "null Map.entry component");
    }

    @Test
    void mapOfEntriesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-entries");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.ofEntries(
                        Map.entry("x", 7),
                        Map.entry("y", 9),
                        Map.entry("z", 11)
                    );
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("x"));
                    System.out.println(values.containsKey("y"));
                    System.out.println(values.containsKey("z"));
                    System.out.println(values.get("x"));
                    System.out.println(values.get("y"));
                    System.out.println(values.get("z"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-entries").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n3\ntrue\ntrue\ntrue\n7\n9\n11\n");
    }

    @Test
    void mapOfEntriesDuplicateKeyFailsAtRuntime() throws Exception {
        assertMapOfEntriesFailureAtRuntime(
            "map-of-entries-duplicate-key",
            "Map.ofEntries(Map.entry(\"same\", 1), Map.entry(\"same\", 2));",
            "duplicate Map.of key"
        );
    }

    @Test
    void mapOfEntriesNullEntryFailsAtRuntime() throws Exception {
        assertMapOfEntriesFailureAtRuntime(
            "map-of-entries-null-entry",
            "Map.ofEntries(Map.entry(\"x\", 1), null);",
            "null Map.ofEntries entry"
        );
    }

    @Test
    void mapOfPairBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-pair");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("x", 7, "y", 9);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("x"));
                    System.out.println(values.containsKey("y"));
                    System.out.println(values.containsKey("z"));
                    System.out.println(values.get("x"));
                    System.out.println(values.get("y"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-pair").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n2\ntrue\ntrue\nfalse\n7\n9\n");
    }

    @Test
    void mapOfPairDuplicateKeyFailsAtRuntime() throws Exception {
        assertMapOfPairFailureAtRuntime("map-of-pair-duplicate-key", "Map.of(\"same\", 1, \"same\", 2);", "duplicate Map.of key");
    }

    @Test
    void mapOfPairNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfPairFailureAtRuntime("map-of-pair-null-first-key", "Map.of((String) null, 1, \"y\", 2);", "null Map.of entry");
    }

    @Test
    void mapOfPairNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfPairFailureAtRuntime("map-of-pair-null-first-value", "Map.of(\"x\", (Integer) null, \"y\", 2);", "null Map.of entry");
    }

    @Test
    void mapOfPairNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfPairFailureAtRuntime("map-of-pair-null-second-key", "Map.of(\"x\", 1, (String) null, 2);", "null Map.of entry");
    }

    @Test
    void mapOfPairNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfPairFailureAtRuntime("map-of-pair-null-second-value", "Map.of(\"x\", 1, \"y\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfTripleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-triple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("x", 7, "y", 9, "z", 11);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("x"));
                    System.out.println(values.containsKey("y"));
                    System.out.println(values.containsKey("z"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("x"));
                    System.out.println(values.get("y"));
                    System.out.println(values.get("z"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-triple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n3\ntrue\ntrue\ntrue\nfalse\n7\n9\n11\n");
    }

    @Test
    void mapOfTripleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"z\", 3);", "duplicate Map.of key");
    }

    @Test
    void mapOfTripleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-duplicate-first-third", "Map.of(\"same\", 1, \"y\", 2, \"same\", 3);", "duplicate Map.of key");
    }

    @Test
    void mapOfTripleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-duplicate-second-third", "Map.of(\"x\", 1, \"same\", 2, \"same\", 3);", "duplicate Map.of key");
    }

    @Test
    void mapOfTripleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-null-first-key", "Map.of((String) null, 1, \"y\", 2, \"z\", 3);", "null Map.of entry");
    }

    @Test
    void mapOfTripleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-null-first-value", "Map.of(\"x\", (Integer) null, \"y\", 2, \"z\", 3);", "null Map.of entry");
    }

    @Test
    void mapOfTripleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-null-second-key", "Map.of(\"x\", 1, (String) null, 2, \"z\", 3);", "null Map.of entry");
    }

    @Test
    void mapOfTripleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-null-second-value", "Map.of(\"x\", 1, \"y\", (Integer) null, \"z\", 3);", "null Map.of entry");
    }

    @Test
    void mapOfTripleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-null-third-key", "Map.of(\"x\", 1, \"y\", 2, (String) null, 3);", "null Map.of entry");
    }

    @Test
    void mapOfTripleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfTripleFailureAtRuntime("map-of-triple-null-third-value", "Map.of(\"x\", 1, \"y\", 2, \"z\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-quadruple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-quadruple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n4\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n");
    }

    @Test
    void mapOfQuadrupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuadrupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuadrupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuadrupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuadrupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuadrupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuadrupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4);", "null Map.of entry");
    }

    @Test
    void mapOfQuadrupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfQuadrupleFailureAtRuntime("map-of-quadruple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-quintuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("e"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                    System.out.println(values.get("e"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-quintuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n5\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n5\n");
    }

    @Test
    void mapOfQuintupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-first-fifth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4, \"e\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4, \"e\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-second-fifth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"same\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4, \"e\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-third-fifth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"same\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-duplicate-fourth-fifth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"same\", 5);", "duplicate Map.of key");
    }

    @Test
    void mapOfQuintupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null, \"e\", 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullFifthKeyFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-fifth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, (String) null, 5);", "null Map.of entry");
    }

    @Test
    void mapOfQuintupleNullFifthValueFailsAtRuntime() throws Exception {
        assertMapOfQuintupleFailureAtRuntime("map-of-quintuple-null-fifth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-sextuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("e"));
                    System.out.println(values.containsKey("f"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                    System.out.println(values.get("e"));
                    System.out.println(values.get("f"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-sextuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n6\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n5\n6\n");
    }

    @Test
    void mapOfSextupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-first-fifth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-first-sixth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-second-fifth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-second-sixth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4, \"e\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-third-fifth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"same\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-third-sixth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"same\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-fourth-fifth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"same\", 5, \"f\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-fourth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"same\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-duplicate-fifth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"same\", 6);", "duplicate Map.of key");
    }

    @Test
    void mapOfSextupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null, \"e\", 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullFifthKeyFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-fifth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, (String) null, 5, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullFifthValueFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-fifth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", (Integer) null, \"f\", 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullSixthKeyFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-sixth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, (String) null, 6);", "null Map.of entry");
    }

    @Test
    void mapOfSextupleNullSixthValueFailsAtRuntime() throws Exception {
        assertMapOfSextupleFailureAtRuntime("map-of-sextuple-null-sixth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-septuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("e"));
                    System.out.println(values.containsKey("f"));
                    System.out.println(values.containsKey("g"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                    System.out.println(values.get("e"));
                    System.out.println(values.get("f"));
                    System.out.println(values.get("g"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-septuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n7\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n5\n6\n7\n");
    }

    @Test
    void mapOfSeptupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-first-fifth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-first-sixth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-first-seventh", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-second-fifth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-second-sixth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-second-seventh", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-third-fifth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-third-sixth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-third-seventh", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-fourth-fifth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"same\", 5, \"f\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-fourth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"same\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-fourth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"same\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-fifth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"same\", 6, \"g\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-fifth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"same\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-duplicate-sixth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"same\", 7);", "duplicate Map.of key");
    }

    @Test
    void mapOfSeptupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null, \"e\", 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullFifthKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-fifth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, (String) null, 5, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullFifthValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-fifth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", (Integer) null, \"f\", 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullSixthKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-sixth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, (String) null, 6, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullSixthValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-sixth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", (Integer) null, \"g\", 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullSeventhKeyFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-seventh-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, (String) null, 7);", "null Map.of entry");
    }

    @Test
    void mapOfSeptupleNullSeventhValueFailsAtRuntime() throws Exception {
        assertMapOfSeptupleFailureAtRuntime("map-of-septuple-null-seventh-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-octuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("e"));
                    System.out.println(values.containsKey("f"));
                    System.out.println(values.containsKey("g"));
                    System.out.println(values.containsKey("h"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                    System.out.println(values.get("e"));
                    System.out.println(values.get("f"));
                    System.out.println(values.get("g"));
                    System.out.println(values.get("h"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-octuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n8\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n5\n6\n7\n8\n");
    }

    @Test
    void mapOfOctupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-fifth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-sixth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-seventh", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFirstEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-first-eighth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-second-fifth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-second-sixth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-second-seventh", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSecondEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-second-eighth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-third-fifth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-third-sixth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-third-seventh", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateThirdEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-third-eighth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fourth-fifth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fourth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fourth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFourthEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fourth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fifth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"same\", 6, \"g\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fifth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"same\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateFifthEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-fifth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-sixth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"same\", 7, \"h\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSixthEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-sixth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleDuplicateSeventhEighthFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-duplicate-seventh-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"same\", 8);", "duplicate Map.of key");
    }

    @Test
    void mapOfOctupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullFifthKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-fifth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, (String) null, 5, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullFifthValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-fifth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", (Integer) null, \"f\", 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullSixthKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-sixth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, (String) null, 6, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullSixthValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-sixth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", (Integer) null, \"g\", 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullSeventhKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-seventh-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, (String) null, 7, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullSeventhValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-seventh-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", (Integer) null, \"h\", 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullEighthKeyFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-eighth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, (String) null, 8);", "null Map.of entry");
    }

    @Test
    void mapOfOctupleNullEighthValueFailsAtRuntime() throws Exception {
        assertMapOfOctupleFailureAtRuntime("map-of-octuple-null-eighth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-nonuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("e"));
                    System.out.println(values.containsKey("f"));
                    System.out.println(values.containsKey("g"));
                    System.out.println(values.containsKey("h"));
                    System.out.println(values.containsKey("i"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                    System.out.println(values.get("e"));
                    System.out.println(values.get("f"));
                    System.out.println(values.get("g"));
                    System.out.println(values.get("h"));
                    System.out.println(values.get("i"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-nonuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n9\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n5\n6\n7\n8\n9\n");
    }

    @Test
    void mapOfNonupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-fifth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-sixth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-seventh", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-eighth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFirstNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-first-ninth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-fifth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-sixth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-seventh", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-eighth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSecondNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-second-ninth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-third-fifth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-third-sixth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-third-seventh", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateThirdEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-third-eighth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateThirdNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-third-ninth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fourth-fifth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fourth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fourth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFourthEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fourth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFourthNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fourth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fifth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fifth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFifthEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fifth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateFifthNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-fifth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-sixth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"same\", 7, \"h\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSixthEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-sixth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSixthNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-sixth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSeventhEighthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-seventh-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"same\", 8, \"i\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateSeventhNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-seventh-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleDuplicateEighthNinthFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-duplicate-eighth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"same\", 9);", "duplicate Map.of key");
    }

    @Test
    void mapOfNonupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullFifthKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-fifth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, (String) null, 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullFifthValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-fifth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", (Integer) null, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullSixthKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-sixth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, (String) null, 6, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullSixthValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-sixth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", (Integer) null, \"g\", 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullSeventhKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-seventh-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, (String) null, 7, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullSeventhValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-seventh-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", (Integer) null, \"h\", 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullEighthKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-eighth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, (String) null, 8, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullEighthValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-eighth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", (Integer) null, \"i\", 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullNinthKeyFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-ninth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, (String) null, 9);", "null Map.of entry");
    }

    @Test
    void mapOfNonupleNullNinthValueFailsAtRuntime() throws Exception {
        assertMapOfNonupleFailureAtRuntime("map-of-nonuple-null-ninth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-of-decuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Integer> values = Map.of("a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7, "h", 8, "i", 9, "j", 10);
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.containsKey("a"));
                    System.out.println(values.containsKey("b"));
                    System.out.println(values.containsKey("c"));
                    System.out.println(values.containsKey("d"));
                    System.out.println(values.containsKey("e"));
                    System.out.println(values.containsKey("f"));
                    System.out.println(values.containsKey("g"));
                    System.out.println(values.containsKey("h"));
                    System.out.println(values.containsKey("i"));
                    System.out.println(values.containsKey("j"));
                    System.out.println(values.containsKey("missing"));
                    System.out.println(values.get("a"));
                    System.out.println(values.get("b"));
                    System.out.println(values.get("c"));
                    System.out.println(values.get("d"));
                    System.out.println(values.get("e"));
                    System.out.println(values.get("f"));
                    System.out.println(values.get("g"));
                    System.out.println(values.get("h"));
                    System.out.println(values.get("i"));
                    System.out.println(values.get("j"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-of-decuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n10\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n");
    }

    @Test
    void mapOfDecupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-second", "Map.of(\"same\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-third", "Map.of(\"same\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-fourth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-fifth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-sixth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-seventh", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-eighth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-ninth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFirstTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-first-tenth", "Map.of(\"same\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-third", "Map.of(\"a\", 1, \"same\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-fourth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-fifth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-sixth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-seventh", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-eighth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-ninth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSecondTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-second-tenth", "Map.of(\"a\", 1, \"same\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-fourth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-fifth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-sixth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-seventh", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-eighth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-ninth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateThirdTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-third-tenth", "Map.of(\"a\", 1, \"b\", 2, \"same\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fourth-fifth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fourth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fourth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFourthEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fourth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFourthNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fourth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFourthTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fourth-tenth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"same\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fifth-sixth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fifth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFifthEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fifth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFifthNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fifth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateFifthTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-fifth-tenth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"same\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-sixth-seventh", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSixthEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-sixth-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSixthNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-sixth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSixthTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-sixth-tenth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"same\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSeventhEighthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-seventh-eighth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"same\", 8, \"i\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSeventhNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-seventh-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateSeventhTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-seventh-tenth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"same\", 7, \"h\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateEighthNinthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-eighth-ninth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"same\", 9, \"j\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateEighthTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-eighth-tenth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"same\", 8, \"i\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleDuplicateNinthTenthFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-duplicate-ninth-tenth", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"same\", 9, \"same\", 10);", "duplicate Map.of key");
    }

    @Test
    void mapOfDecupleNullFirstKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-first-key", "Map.of((String) null, 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullFirstValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-first-value", "Map.of(\"a\", (Integer) null, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullSecondKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-second-key", "Map.of(\"a\", 1, (String) null, 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullSecondValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-second-value", "Map.of(\"a\", 1, \"b\", (Integer) null, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullThirdKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-third-key", "Map.of(\"a\", 1, \"b\", 2, (String) null, 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullThirdValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-third-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", (Integer) null, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullFourthKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-fourth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, (String) null, 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullFourthValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-fourth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", (Integer) null, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullFifthKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-fifth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, (String) null, 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullFifthValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-fifth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", (Integer) null, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullSixthKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-sixth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, (String) null, 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullSixthValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-sixth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", (Integer) null, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullSeventhKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-seventh-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, (String) null, 7, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullSeventhValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-seventh-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", (Integer) null, \"h\", 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullEighthKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-eighth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, (String) null, 8, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullEighthValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-eighth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", (Integer) null, \"i\", 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullNinthKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-ninth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, (String) null, 9, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullNinthValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-ninth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", (Integer) null, \"j\", 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullTenthKeyFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-tenth-key", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, (String) null, 10);", "null Map.of entry");
    }

    @Test
    void mapOfDecupleNullTenthValueFailsAtRuntime() throws Exception {
        assertMapOfDecupleFailureAtRuntime("map-of-decuple-null-tenth-value", "Map.of(\"a\", 1, \"b\", 2, \"c\", 3, \"d\", 4, \"e\", 5, \"f\", 6, \"g\", 7, \"h\", 8, \"i\", 9, \"j\", (Integer) null);", "null Map.of entry");
    }

    @Test
    void setOfEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.contains("x"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-empty").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\nfalse\n");
    }

    @Test
    void setCopyOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-copy-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("left");
                    values.add("right");
                    final Set<String> snapshot = Set.copyOf(values);
                    values.add("later");
                    System.out.println(snapshot.size());
                    System.out.println(snapshot.contains("left"));
                    System.out.println(snapshot.contains("right"));
                    System.out.println(snapshot.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-copy-of").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfSingletonBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-singleton");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("x");
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                    System.out.println(values.contains("x"));
                    System.out.println(values.contains("y"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-singleton").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\ntrue\nfalse\n");
    }

    @Test
    void setOfSingletonNullFailsAtRuntime() throws Exception {
        final Path project = project("set-of-singleton-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of((String) null);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-singleton-null").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfPairBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-pair");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("left", "right");
                    System.out.println(values.size());
                    System.out.println(values.contains("left"));
                    System.out.println(values.contains("right"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-pair").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfPairDuplicateFailsAtRuntime() throws Exception {
        final Path project = project("set-of-pair-duplicate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("same", "same");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-pair-duplicate").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfPairNullFailsAtRuntime() throws Exception {
        final Path project = project("set-of-pair-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("left", (String) null);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-pair-null").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfTripleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-triple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("left", "middle", "right");
                    System.out.println(values.size());
                    System.out.println(values.contains("left"));
                    System.out.println(values.contains("middle"));
                    System.out.println(values.contains("right"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-triple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfTripleDuplicateFirstMiddleFailsAtRuntime() throws Exception {
        final Path project = project("set-of-triple-duplicate-first-middle");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("same", "same", "right");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-triple-duplicate-first-middle").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfTripleDuplicateFirstRightFailsAtRuntime() throws Exception {
        final Path project = project("set-of-triple-duplicate-first-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("same", "middle", "same");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-triple-duplicate-first-right").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfTripleDuplicateMiddleRightFailsAtRuntime() throws Exception {
        final Path project = project("set-of-triple-duplicate-middle-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("left", "same", "same");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-triple-duplicate-middle-right").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfTripleNullFailsAtRuntime() throws Exception {
        final Path project = project("set-of-triple-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("left", (String) null, "right");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-triple-null").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfQuadrupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-quadruple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-quadruple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfQuadrupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-duplicate-first-second");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("same", "same", "c", "d");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-duplicate-first-second").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfQuadrupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-duplicate-first-third");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("same", "b", "same", "d");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-duplicate-first-third").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfQuadrupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-duplicate-first-fourth");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("same", "b", "c", "same");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-duplicate-first-fourth").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfQuadrupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-duplicate-second-third");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("a", "same", "same", "d");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-duplicate-second-third").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfQuadrupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-duplicate-second-fourth");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("a", "same", "c", "same");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-duplicate-second-fourth").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfQuadrupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-duplicate-third-fourth");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("a", "b", "same", "same");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-duplicate-third-fourth").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("duplicate Set.of element");
    }

    @Test
    void setOfQuadrupleNullFirstFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-null-first");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of((String) null, "b", "c", "d");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-null-first").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfQuadrupleNullSecondFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-null-second");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("a", (String) null, "c", "d");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-null-second").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfQuadrupleNullThirdFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-null-third");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("a", "b", (String) null, "d");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-null-third").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfQuadrupleNullFourthFailsAtRuntime() throws Exception {
        final Path project = project("set-of-quadruple-null-fourth");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Set.of("a", "b", "c", (String) null);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/set-of-quadruple-null-fourth").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("null Set.of element");
    }

    @Test
    void setOfQuintupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-quintuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d", "e");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("e"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-quintuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("5\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfQuintupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-first-second",
            "Set.of(\"same\", \"same\", \"c\", \"d\", \"e\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-first-third",
            "Set.of(\"same\", \"b\", \"same\", \"d\", \"e\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-first-fourth",
            "Set.of(\"same\", \"b\", \"c\", \"same\", \"e\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-first-fifth",
            "Set.of(\"same\", \"b\", \"c\", \"d\", \"same\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-second-third",
            "Set.of(\"a\", \"same\", \"same\", \"d\", \"e\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-second-fourth",
            "Set.of(\"a\", \"same\", \"c\", \"same\", \"e\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-second-fifth",
            "Set.of(\"a\", \"same\", \"c\", \"d\", \"same\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-third-fourth",
            "Set.of(\"a\", \"b\", \"same\", \"same\", \"e\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-third-fifth",
            "Set.of(\"a\", \"b\", \"same\", \"d\", \"same\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-duplicate-fourth-fifth",
            "Set.of(\"a\", \"b\", \"c\", \"same\", \"same\");",
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfQuintupleNullFirstFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-null-first",
            "Set.of((String) null, \"b\", \"c\", \"d\", \"e\");",
            "null Set.of element"
        );
    }

    @Test
    void setOfQuintupleNullSecondFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-null-second",
            "Set.of(\"a\", (String) null, \"c\", \"d\", \"e\");",
            "null Set.of element"
        );
    }

    @Test
    void setOfQuintupleNullThirdFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-null-third",
            "Set.of(\"a\", \"b\", (String) null, \"d\", \"e\");",
            "null Set.of element"
        );
    }

    @Test
    void setOfQuintupleNullFourthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-null-fourth",
            "Set.of(\"a\", \"b\", \"c\", (String) null, \"e\");",
            "null Set.of element"
        );
    }

    @Test
    void setOfQuintupleNullFifthFailsAtRuntime() throws Exception {
        assertSetOfQuintupleFailureAtRuntime(
            "set-of-quintuple-null-fifth",
            "Set.of(\"a\", \"b\", \"c\", \"d\", (String) null);",
            "null Set.of element"
        );
    }

    @Test
    void setOfSextupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-sextuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d", "e", "f");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("e"));
                    System.out.println(values.contains("f"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-sextuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("6\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfSextupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-first-second", "Set.of(\"same\", \"same\", \"c\", \"d\", \"e\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-first-third", "Set.of(\"same\", \"b\", \"same\", \"d\", \"e\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-first-fourth", "Set.of(\"same\", \"b\", \"c\", \"same\", \"e\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-first-fifth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"same\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-first-sixth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-second-third", "Set.of(\"a\", \"same\", \"same\", \"d\", \"e\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-second-fourth", "Set.of(\"a\", \"same\", \"c\", \"same\", \"e\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-second-fifth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"same\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-second-sixth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-third-fourth", "Set.of(\"a\", \"b\", \"same\", \"same\", \"e\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-third-fifth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"same\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-third-sixth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-fourth-fifth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"same\", \"f\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-fourth-sixth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-duplicate-fifth-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSextupleNullFirstFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-null-first", "Set.of((String) null, \"b\", \"c\", \"d\", \"e\", \"f\");", "null Set.of element");
    }

    @Test
    void setOfSextupleNullSecondFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-null-second", "Set.of(\"a\", (String) null, \"c\", \"d\", \"e\", \"f\");", "null Set.of element");
    }

    @Test
    void setOfSextupleNullThirdFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-null-third", "Set.of(\"a\", \"b\", (String) null, \"d\", \"e\", \"f\");", "null Set.of element");
    }

    @Test
    void setOfSextupleNullFourthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-null-fourth", "Set.of(\"a\", \"b\", \"c\", (String) null, \"e\", \"f\");", "null Set.of element");
    }

    @Test
    void setOfSextupleNullFifthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-null-fifth", "Set.of(\"a\", \"b\", \"c\", \"d\", (String) null, \"f\");", "null Set.of element");
    }

    @Test
    void setOfSextupleNullSixthFailsAtRuntime() throws Exception {
        assertSetOfSextupleFailureAtRuntime("set-of-sextuple-null-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", (String) null);", "null Set.of element");
    }

    @Test
    void setOfSeptupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-septuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d", "e", "f", "g");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("e"));
                    System.out.println(values.contains("f"));
                    System.out.println(values.contains("g"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-septuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfSeptupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-first-second", "Set.of(\"same\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-first-third", "Set.of(\"same\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-first-fourth", "Set.of(\"same\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-first-fifth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-first-sixth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-first-seventh", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-second-third", "Set.of(\"a\", \"same\", \"same\", \"d\", \"e\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-second-fourth", "Set.of(\"a\", \"same\", \"c\", \"same\", \"e\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-second-fifth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"same\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-second-sixth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"same\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-second-seventh", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-third-fourth", "Set.of(\"a\", \"b\", \"same\", \"same\", \"e\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-third-fifth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"same\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-third-sixth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"same\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-third-seventh", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-fourth-fifth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"same\", \"f\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-fourth-sixth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"same\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-fourth-seventh", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-fifth-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"same\", \"g\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-fifth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-duplicate-sixth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfSeptupleNullFirstFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-first", "Set.of((String) null, \"b\", \"c\", \"d\", \"e\", \"f\", \"g\");", "null Set.of element");
    }

    @Test
    void setOfSeptupleNullSecondFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-second", "Set.of(\"a\", (String) null, \"c\", \"d\", \"e\", \"f\", \"g\");", "null Set.of element");
    }

    @Test
    void setOfSeptupleNullThirdFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-third", "Set.of(\"a\", \"b\", (String) null, \"d\", \"e\", \"f\", \"g\");", "null Set.of element");
    }

    @Test
    void setOfSeptupleNullFourthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-fourth", "Set.of(\"a\", \"b\", \"c\", (String) null, \"e\", \"f\", \"g\");", "null Set.of element");
    }

    @Test
    void setOfSeptupleNullFifthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-fifth", "Set.of(\"a\", \"b\", \"c\", \"d\", (String) null, \"f\", \"g\");", "null Set.of element");
    }

    @Test
    void setOfSeptupleNullSixthFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", (String) null, \"g\");", "null Set.of element");
    }

    @Test
    void setOfSeptupleNullSeventhFailsAtRuntime() throws Exception {
        assertSetOfSeptupleFailureAtRuntime("set-of-septuple-null-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", (String) null);", "null Set.of element");
    }

    @Test
    void setOfOctupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-octuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d", "e", "f", "g", "h");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("e"));
                    System.out.println(values.contains("f"));
                    System.out.println(values.contains("g"));
                    System.out.println(values.contains("h"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-octuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("8\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfOctupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-second", "Set.of(\"same\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-third", "Set.of(\"same\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-fourth", "Set.of(\"same\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-fifth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-sixth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-seventh", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFirstEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-first-eighth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-second-third", "Set.of(\"a\", \"same\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-second-fourth", "Set.of(\"a\", \"same\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-second-fifth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-second-sixth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-second-seventh", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSecondEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-second-eighth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-third-fourth", "Set.of(\"a\", \"b\", \"same\", \"same\", \"e\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-third-fifth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"same\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-third-sixth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"same\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-third-seventh", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"same\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateThirdEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-third-eighth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fourth-fifth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"same\", \"f\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fourth-sixth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"same\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fourth-seventh", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"same\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFourthEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fourth-eighth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fifth-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"same\", \"g\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fifth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"same\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateFifthEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-fifth-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-sixth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"same\", \"h\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSixthEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-sixth-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleDuplicateSeventhEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-duplicate-seventh-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfOctupleNullFirstFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-first", "Set.of((String) null, \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullSecondFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-second", "Set.of(\"a\", (String) null, \"c\", \"d\", \"e\", \"f\", \"g\", \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullThirdFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-third", "Set.of(\"a\", \"b\", (String) null, \"d\", \"e\", \"f\", \"g\", \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullFourthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-fourth", "Set.of(\"a\", \"b\", \"c\", (String) null, \"e\", \"f\", \"g\", \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullFifthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-fifth", "Set.of(\"a\", \"b\", \"c\", \"d\", (String) null, \"f\", \"g\", \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullSixthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", (String) null, \"g\", \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullSeventhFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", (String) null, \"h\");", "null Set.of element");
    }

    @Test
    void setOfOctupleNullEighthFailsAtRuntime() throws Exception {
        assertSetOfOctupleFailureAtRuntime("set-of-octuple-null-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", (String) null);", "null Set.of element");
    }

    @Test
    void setOfNonupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-nonuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d", "e", "f", "g", "h", "i");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("e"));
                    System.out.println(values.contains("f"));
                    System.out.println(values.contains("g"));
                    System.out.println(values.contains("h"));
                    System.out.println(values.contains("i"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-nonuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("9\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfNonupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-second", "Set.of(\"same\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-third", "Set.of(\"same\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-fourth", "Set.of(\"same\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-fifth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-sixth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-seventh", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-eighth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFirstNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-first-ninth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-third", "Set.of(\"a\", \"same\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-fourth", "Set.of(\"a\", \"same\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-fifth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-sixth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-seventh", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-eighth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSecondNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-second-ninth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-third-fourth", "Set.of(\"a\", \"b\", \"same\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-third-fifth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-third-sixth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-third-seventh", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateThirdEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-third-eighth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateThirdNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-third-ninth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fourth-fifth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"same\", \"f\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fourth-sixth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"same\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fourth-seventh", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"same\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFourthEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fourth-eighth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFourthNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fourth-ninth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fifth-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"same\", \"g\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fifth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"same\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFifthEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fifth-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateFifthNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-fifth-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-sixth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"same\", \"h\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSixthEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-sixth-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSixthNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-sixth-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSeventhEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-seventh-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"same\", \"i\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateSeventhNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-seventh-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleDuplicateEighthNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-duplicate-eighth-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfNonupleNullFirstFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-first", "Set.of((String) null, \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullSecondFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-second", "Set.of(\"a\", (String) null, \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullThirdFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-third", "Set.of(\"a\", \"b\", (String) null, \"d\", \"e\", \"f\", \"g\", \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullFourthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-fourth", "Set.of(\"a\", \"b\", \"c\", (String) null, \"e\", \"f\", \"g\", \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullFifthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-fifth", "Set.of(\"a\", \"b\", \"c\", \"d\", (String) null, \"f\", \"g\", \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullSixthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", (String) null, \"g\", \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullSeventhFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", (String) null, \"h\", \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullEighthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", (String) null, \"i\");", "null Set.of element");
    }

    @Test
    void setOfNonupleNullNinthFailsAtRuntime() throws Exception {
        assertSetOfNonupleFailureAtRuntime("set-of-nonuple-null-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", (String) null);", "null Set.of element");
    }

    @Test
    void setOfDecupleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-decuple");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Set.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j");
                    System.out.println(values.size());
                    System.out.println(values.contains("a"));
                    System.out.println(values.contains("b"));
                    System.out.println(values.contains("c"));
                    System.out.println(values.contains("d"));
                    System.out.println(values.contains("e"));
                    System.out.println(values.contains("f"));
                    System.out.println(values.contains("g"));
                    System.out.println(values.contains("h"));
                    System.out.println(values.contains("i"));
                    System.out.println(values.contains("j"));
                    System.out.println(values.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-decuple").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfDecupleDuplicateFirstSecondFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-second", "Set.of(\"same\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstThirdFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-third", "Set.of(\"same\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstFourthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-fourth", "Set.of(\"same\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstFifthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-fifth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstSixthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-sixth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-seventh", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-eighth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-ninth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFirstTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-first-tenth", "Set.of(\"same\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondThirdFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-third", "Set.of(\"a\", \"same\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondFourthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-fourth", "Set.of(\"a\", \"same\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondFifthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-fifth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondSixthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-sixth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-seventh", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-eighth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-ninth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSecondTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-second-tenth", "Set.of(\"a\", \"same\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdFourthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-fourth", "Set.of(\"a\", \"b\", \"same\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdFifthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-fifth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdSixthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-sixth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-seventh", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-eighth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-ninth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateThirdTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-third-tenth", "Set.of(\"a\", \"b\", \"same\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFourthFifthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fourth-fifth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"same\", \"f\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFourthSixthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fourth-sixth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"same\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFourthSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fourth-seventh", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"same\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFourthEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fourth-eighth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFourthNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fourth-ninth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFourthTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fourth-tenth", "Set.of(\"a\", \"b\", \"c\", \"same\", \"e\", \"f\", \"g\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFifthSixthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fifth-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"same\", \"g\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFifthSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fifth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"same\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFifthEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fifth-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFifthNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fifth-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateFifthTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-fifth-tenth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"same\", \"f\", \"g\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSixthSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-sixth-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"same\", \"h\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSixthEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-sixth-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSixthNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-sixth-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSixthTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-sixth-tenth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"same\", \"g\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSeventhEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-seventh-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"same\", \"i\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSeventhNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-seventh-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateSeventhTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-seventh-tenth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"same\", \"h\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateEighthNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-eighth-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"same\", \"j\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateEighthTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-eighth-tenth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"same\", \"i\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleDuplicateNinthTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-duplicate-ninth-tenth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"same\", \"same\");", "duplicate Set.of element");
    }

    @Test
    void setOfDecupleNullFirstFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-first", "Set.of((String) null, \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullSecondFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-second", "Set.of(\"a\", (String) null, \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullThirdFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-third", "Set.of(\"a\", \"b\", (String) null, \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullFourthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-fourth", "Set.of(\"a\", \"b\", \"c\", (String) null, \"e\", \"f\", \"g\", \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullFifthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-fifth", "Set.of(\"a\", \"b\", \"c\", \"d\", (String) null, \"f\", \"g\", \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullSixthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-sixth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", (String) null, \"g\", \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullSeventhFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-seventh", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", (String) null, \"h\", \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullEighthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-eighth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", (String) null, \"i\", \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullNinthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-ninth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", (String) null, \"j\");", "null Set.of element");
    }

    @Test
    void setOfDecupleNullTenthFailsAtRuntime() throws Exception {
        assertSetOfDecupleFailureAtRuntime("set-of-decuple-null-tenth", "Set.of(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\", \"h\", \"i\", (String) null);", "null Set.of element");
    }

    @Test
    void setOfVarargsArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-of-varargs-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"};
                    final Set<String> set = Set.of(values);
                    System.out.println(set.size());
                    System.out.println(set.contains("a"));
                    System.out.println(set.contains("k"));
                    System.out.println(set.contains("later"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-of-varargs-array").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("11\ntrue\ntrue\nfalse\n");
    }

    @Test
    void setOfVarargsArrayDuplicateFailsAtRuntime() throws Exception {
        assertSetOfVarargsArrayFailureAtRuntime(
            "set-of-varargs-array-duplicate",
            """
                final String[] values = {"a", "b", "same", "d", "same", "f", "g", "h", "i", "j", "k"};
                Set.of(values);
                """,
            "duplicate Set.of element"
        );
    }

    @Test
    void setOfVarargsArrayNullFailsAtRuntime() throws Exception {
        assertSetOfVarargsArrayFailureAtRuntime(
            "set-of-varargs-array-null",
            """
                final String[] values = {"a", "b", "c", "d", null, "f", "g", "h", "i", "j", "k"};
                Set.of(values);
                """,
            "null Set.of element"
        );
    }

    private void assertSetOfQuintupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetOfVarargsArrayFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfPairFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapEntryFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapConstructorFailureAtRuntime(
        final String projectName,
        final String imports,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            %s

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(imports, statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private Path enumMapFailureProject(final String projectName, final String statement) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.EnumMap;
            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private enum Phase {
                    FIRST
                }

                private enum Other {
                    FIRST
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));
        return project;
    }

    private static String runtimeFailureOutcome(final ProcessResult result, final String expectedMessage) {
        if (result.exitCode() == 0) {
            return "runtime-success";
        }
        return result.stderr().contains(expectedMessage)
            ? "runtime-failure:" + expectedMessage
            : "runtime-failure:unexpected-diagnostic";
    }

    private void assertMapStaticFactoryFailureAtRuntime(
        final String projectName,
        final String imports,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            %s

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(imports, statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetStaticFactoryFailureAtRuntime(
        final String projectName,
        final String imports,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            %s

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(imports, statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetConstructorFailureAtRuntime(
        final String projectName,
        final String imports,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            %s

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(imports, statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertCollectionsUnmodifiableMapFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertCollectionsUnmodifiableListFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertCollectionsUnmodifiableCollectionFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.Collections;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfTripleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfQuadrupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfQuintupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfSextupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetOfSextupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetOfSeptupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetOfOctupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetOfNonupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfSeptupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfOctupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfNonupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfDecupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertMapOfEntriesFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    private void assertSetOfDecupleFailureAtRuntime(
        final String projectName,
        final String statement,
        final String expectedMessage
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    %s
                }
            }
            """.formatted(statement));

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains(expectedMessage);
    }

    @Test
    void booleanEqualsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boolean-equals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Boolean.TRUE.equals(Boolean.valueOf(true)));
                    System.out.println(Boolean.TRUE.equals(Boolean.FALSE));
                    System.out.println(Boolean.TRUE.equals("true"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-equals").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\nfalse\n");
    }

    @Test
    void mapKeySetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-key-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("left", "1");
                    values.put("right", "2");
                    System.out.println(values.keySet().contains("left"));
                    for (final String key : values.keySet()) {
                        System.out.println(key);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-key-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nleft\nright\n");
    }

    @Test
    void atomicIntegerBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-integer");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var counter = new java.util.concurrent.atomic.AtomicInteger(4);
                    System.out.println(counter.get());
                    System.out.println(counter.getAndIncrement());
                    System.out.println(counter.incrementAndGet());
                    System.out.println(counter.decrementAndGet());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-integer").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\n4\n6\n5\n");
    }

    @Test
    void atomicBooleanSetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-boolean-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var flag = new java.util.concurrent.atomic.AtomicBoolean();
                    System.out.println(flag.get());
                    flag.set(true);
                    System.out.println(flag.get());
                    flag.set(false);
                    System.out.println(flag.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-boolean-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\ntrue\nfalse\n");
    }

    @Test
    void atomicIntegerSetAndBoundaryWrapBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-integer-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var counter = new java.util.concurrent.atomic.AtomicInteger();
                    System.out.println(counter.get());
                    counter.set(9);
                    System.out.println(counter.get());
                    counter.set(-3);
                    System.out.println(counter.get());
                    counter.set(Integer.MAX_VALUE);
                    System.out.println(counter.incrementAndGet());
                    counter.set(Integer.MIN_VALUE);
                    System.out.println(counter.decrementAndGet());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-integer-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n9\n-3\n-2147483648\n2147483647\n");
    }

    @Test
    void atomicLongSetAndBoundaryWrapBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-long-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var counter = new java.util.concurrent.atomic.AtomicLong(2L);
                    System.out.println(counter.get());
                    counter.set(15L);
                    System.out.println(counter.get());
                    counter.set(-7L);
                    System.out.println(counter.get());
                    counter.set(Long.MAX_VALUE);
                    System.out.println(counter.incrementAndGet());
                    counter.set(Long.MIN_VALUE);
                    System.out.println(counter.decrementAndGet());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-long-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n15\n-7\n-9223372036854775808\n9223372036854775807\n");
    }

    @Test
    void atomicReferenceCompareAndSetSuccessBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-reference-compare-and-set-success");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var expected = new StringBuilder("alpha");
                    final var next = new StringBuilder("beta");
                    final var ref = new java.util.concurrent.atomic.AtomicReference<Object>(expected);
                    System.out.println(ref.compareAndSet(expected, next));
                    System.out.println(ref.get() == next);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-reference-compare-and-set-success").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\ntrue\n");
    }

    @Test
    void atomicReferenceCompareAndSetIdentityFailureBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-reference-compare-and-set-identity-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var current = new String("same");
                    final var equalButDifferent = new String("same");
                    final var next = new String("next");
                    final var ref = new java.util.concurrent.atomic.AtomicReference<Object>(current);
                    System.out.println(ref.compareAndSet(equalButDifferent, next));
                    System.out.println(ref.get() == current);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-reference-compare-and-set-identity-failure").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\ntrue\n");
    }

    @Test
    void atomicReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("atomic-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var current = new java.util.concurrent.atomic.AtomicReference<Object>("alpha");
                    final var fallback = new java.util.concurrent.atomic.AtomicReference<Object>();
                    fallback.set("beta");
                    System.out.println(current.get());
                    System.out.println(fallback.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/atomic-reference").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("alpha\nbeta\n");
    }

    @Test
    void stringStartsWithBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-starts-with");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".startsWith("javan"));
                    System.out.println("javan native".startsWith("native"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-starts-with").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void stringStartsWithOffsetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-starts-with-offset");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".startsWith("native", 6));
                    System.out.println("javan native".startsWith("native", 7));
                    System.out.println("javan native".startsWith("", 12));
                    System.out.println("javan native".startsWith("", 13));
                    System.out.println("javan native".startsWith("javan", -1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-starts-with-offset").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\ntrue\nfalse\nfalse\n");
    }

    @Test
    void stringIndexOfCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".indexOf('v'));
                    System.out.println("javan".indexOf('x'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-char").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n-1\n");
    }

    @Test
    void stringIndexOfCharFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-char-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".indexOf('a', 2));
                    System.out.println("javan".indexOf('a', -2));
                    System.out.println("javan".indexOf('a', 9));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-char-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringIndexOfStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".indexOf("native"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringIndexOfStringFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-string-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native native".indexOf("native", 7));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-string-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLastIndexOfCharFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-last-index-of-char-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".lastIndexOf('a', 3));
                    System.out.println("javan".lastIndexOf('a', -1));
                    System.out.println("javan".lastIndexOf('a', 9));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-last-index-of-char-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLastIndexOfCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-last-index-of-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".lastIndexOf('a'));
                    System.out.println("javan".lastIndexOf('x'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-last-index-of-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLastIndexOfStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-last-index-of-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("abcabc".lastIndexOf("abc"));
                    System.out.println("abcabc".lastIndexOf("cab"));
                    System.out.println("abcabc".lastIndexOf("zzz"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-last-index-of-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLastIndexOfStringFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-last-index-of-string-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("abcabc".lastIndexOf("abc", 5));
                    System.out.println("abcabc".lastIndexOf("abc", 2));
                    System.out.println("abcabc".lastIndexOf("abc", 1));
                    System.out.println("abc".lastIndexOf("", -1));
                    System.out.println("abc".lastIndexOf("", 4));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-last-index-of-string-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringSubstringBeginBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-substring-begin");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".substring(6));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-substring-begin").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringSubstringRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-substring-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".substring(0, 5));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-substring-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringEndsWithBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-ends-with");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".endsWith("native"));
                    System.out.println("javan native".endsWith("javan"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-ends-with").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void stringReplaceCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-replace-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("com.acme.Main".replace('.', '/'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-replace-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringTrimBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-trim");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("\\t javan \\n".trim());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-trim").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n");
    }

    @Test
    void stringStripBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-strip");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("\\t javan \\n".strip());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-strip").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n");
    }

    @Test
    void stringToLowerCaseBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-to-lower-case");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("JaVaN_123".toLowerCase());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-to-lower-case").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan_123\n");
    }

    @Test
    void stringToLowerCaseRootBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-to-lower-case-root");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("JaVaN_123".toLowerCase(Locale.ROOT));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/string-to-lower-case-root").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void stringToLowerCaseEnglishBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-to-lower-case-english");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("JaVaN_123".toLowerCase(Locale.ENGLISH));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/string-to-lower-case-english").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void localeRootRetainsStaticFinalIdentity() throws Exception {
        final Path project = project("locale-root-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Locale.ROOT == Locale.ROOT);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/locale-root-identity").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void localeEnglishRetainsStaticFinalIdentity() throws Exception {
        final Path project = project("locale-english-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Locale.ENGLISH == Locale.ENGLISH);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/locale-english-identity").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void rootAndEnglishLocalesRemainDistinct() throws Exception {
        final Path project = project("locale-root-english-distinct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Locale.ROOT != Locale.ENGLISH);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/locale-root-english-distinct").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void concurrentLocaleRootAccessRetainsSingletonIdentity() throws Exception {
        final Path project = project("locale-root-concurrent-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                static Locale first;
                static Locale second;

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread firstThread = new Thread(new FirstReader());
                    final Thread secondThread = new Thread(new SecondReader());
                    firstThread.start();
                    secondThread.start();
                    firstThread.join();
                    secondThread.join();
                    System.out.println(first == Locale.ROOT && second == Locale.ROOT && first == second);
                }
            }
            """);
        writeJava(project, "com.acme.FirstReader", """
            package com.acme;

            import java.util.Locale;

            public final class FirstReader implements Runnable {
                @Override
                public void run() {
                    Main.first = Locale.ROOT;
                }
            }
            """);
        writeJava(project, "com.acme.SecondReader", """
            package com.acme;

            import java.util.Locale;

            public final class SecondReader implements Runnable {
                @Override
                public void run() {
                    Main.second = Locale.ROOT;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/locale-root-concurrent-identity").toString()),
            defaultProcessTimeout(),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        ).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void dateTimeFormatterBuilderAcceptsRootLocale() throws Exception {
        final Path project = project("date-time-formatter-root-locale");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.time.format.DateTimeFormatter;
            import java.time.format.DateTimeFormatterBuilder;
            import java.time.format.TextStyle;
            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("EEE MMM dd HH:mm:ss")
                        .appendZoneText(TextStyle.SHORT)
                        .appendPattern(" yyyy")
                        .toFormatter(Locale.ROOT);
                    System.out.println(formatter != null);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/date-time-formatter-root-locale").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void charSequenceLengthBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("char-sequence-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final CharSequence value = "text";
                    System.out.println(value.length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/char-sequence-length").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\n");
    }

    @Test
    void charSequenceCharAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("char-sequence-char-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final CharSequence value = "text";
                    System.out.println((int) value.charAt(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/char-sequence-char-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("101\n");
    }

    @Test
    void characterIsWhitespaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("character-is-whitespace");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Character.isWhitespace('\\t'));
                    System.out.println(Character.isWhitespace('x'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/character-is-whitespace").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void stringRepeatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-repeat");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ja".repeat(3));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-repeat").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("jajaja\n");
    }

    @Test
    void stringRepeatZeroBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-repeat-zero");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".repeat(0).length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-repeat-zero").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n");
    }

    @Test
    void stringRepeatNegativeFailsClearlyAtRuntime() throws Exception {
        final Path project = project("string-repeat-negative");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".repeat(-1));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/string-repeat-negative").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("negative string repeat count");
    }

    @Test
    void stringInternBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-intern");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".intern());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-intern").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLiteralWithControlCharacterBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-literal-control-character");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("A\\001B");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-literal-control-character").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void systemLineSeparatorIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-line-separator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.lineSeparator().length());
                    System.out.println("a" + System.lineSeparator() + "b");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-line-separator").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\na\nb\n");
    }

    @Test
    void durationOfMillisToMillisBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("duration-of-millis");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.time.Duration;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Duration.ofMillis(1234L).toMillis());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/duration-of-millis").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void durationOfSecondsToMillisBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("duration-of-seconds");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.time.Duration;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Duration.ofSeconds(65L).toMillis());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/duration-of-seconds").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void fileSeparatorCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("file-separator-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println((int) java.io.File.separatorChar);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/file-separator-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filePathSeparatorCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("file-path-separator-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println((int) java.io.File.pathSeparatorChar);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/file-path-separator-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filePathSeparatorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("file-path-separator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(java.io.File.pathSeparator);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/file-path-separator").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void systemGetenvIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-getenv");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.getenv("PATH"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-getenv").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void systemGetPropertyDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-get-property-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.getProperty("javan.missing", "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-get-property-default").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void optionalOrElseBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").orElse("fallback"));
                    System.out.println(Optional.empty().orElse("fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nfallback\n");
    }

    @Test
    void optionalOrWithStaticSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").or(Main::fallback).orElse("missing"));
                    System.out.println(Optional.<String>empty().or(Main::fallback).orElse("missing"));
                }

                private static Optional<String> fallback() {
                    return Optional.of("[fallback]");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n[fallback]\n");
    }

    @Test
    void optionalGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-get").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void optionalIsPresentBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-is-present");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").isPresent());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-is-present").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void optionalIsEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-is-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.empty().isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-is-empty").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void optionalFilterWithStaticPredicateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-filter-static-predicate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").filter(Main::hasText).orElse("missing"));
                    System.out.println(Optional.of(" ").filter(Main::hasText).orElse("missing"));
                }

                private static boolean hasText(final String value) {
                    return value != null && value.length() > 1;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-filter-static-predicate").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nmissing\n");
    }

    @Test
    void optionalMapWithStaticFunctionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-map-static-function");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").map(Main::decorate).orElse("missing"));
                }

                private static String decorate(final String value) {
                    return "[" + value + "]";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-map-static-function").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[value]\n");
    }

    @Test
    void optionalMapWithCapturedLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-map-captured-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String suffix = "-native";
                    System.out.println(Optional.of("value").map(value -> value + suffix).orElse("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-map-captured-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value-native\n");
    }

    @Test
    void optionalMapWithBoundInstanceFunctionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-map-bound-instance-function");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private final String prefix;

                private Main(final String prefix) {
                    this.prefix = prefix;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main("[").render(Optional.of("value"), "]"));
                }

                private String render(final Optional<String> value, final String suffix) {
                    return value.map(item -> decorate(suffix, item)).orElse("missing");
                }

                private String decorate(final String suffix, final String item) {
                    return prefix + item + suffix;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/optional-map-bound-instance-function").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void optionalMapWithUnboundInstanceMethodReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-map-unbound-instance-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of(new Row("row-7")).map(Row::key).orElse("missing"));
                }
            }
            """);
        writeJava(project, "com.acme.Row", """
            package com.acme;

            public final class Row {
                private final String key;

                public Row(final String key) {
                    this.key = key;
                }

                public String key() {
                    return key;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/optional-map-unbound-instance-reference").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void optionalMapWithUnboundInstanceLongMethodReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-map-unbound-instance-long-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of(new Row(42L)).map(Row::amount).orElse(0L));
                }
            }
            """);
        writeJava(project, "com.acme.Row", """
            package com.acme;

            public final class Row {
                private final long amount;

                public Row(final long amount) {
                    this.amount = amount;
                }

                public long amount() {
                    return amount;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/optional-map-unbound-instance-long-reference").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void unboundInstanceFunctionReferenceRejectsNullReceiverLikeJvm() throws Exception {
        final Path project = project("unbound-instance-reference-null-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(((Function<Row, String>) Row::constant).apply(null));
                }
            }
            """);
        writeJava(project, "com.acme.Row", """
            package com.acme;

            public final class Row {
                public String constant() {
                    return "incorrect";
                }
            }
            """);

        final Path classes = project.resolve("jvm-classes");
        Files.createDirectories(classes);
        final ProcessResult javac = process(project, List.of(
            CliTestHarness.currentJavacCommand(),
            "-d",
            classes.toString(),
            project.resolve("src/main/java/com/acme/Main.java").toString(),
            project.resolve("src/main/java/com/acme/Row.java").toString()
        ));
        final ProcessResult jvm = process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            classes.toString(),
            "com.acme.Main"
        ));
        final CliRun run = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/unbound-instance-reference-null-receiver").toString()))
            : new ProcessResult(-1, "", run.stderr());

        assertThat(
            javac.exitCode() + "\n"
                + jvm.exitCode() + "\n"
                + jvm.stdout().isEmpty() + "\n"
                + jvm.stderr().contains("NullPointerException") + "\n"
                + run.exitCode() + "\n"
                + nativeRun.exitCode() + "\n"
                + nativeRun.stdout().isEmpty() + "\n"
                + nativeRun.stderr().contains("null object")
        ).isEqualTo("0\n1\ntrue\ntrue\n0\n1\ntrue\ntrue");
    }

    @Test
    void optionalFlatMapWithStaticFunctionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-flat-map-static-function");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").flatMap(Main::decorate).orElse("missing"));
                    System.out.println(Optional.<String>empty().flatMap(Main::decorate).orElse("missing"));
                }

                private static Optional<String> decorate(final String value) {
                    return Optional.of("[" + value + "]");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-flat-map-static-function").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("[value]\nmissing\n");
    }

    @Test
    void optionalIfPresentWithStaticConsumerMethodBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-if-present-static-consumer");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.of("value").ifPresent(Main::printSeen);
                    Optional.<String>empty().ifPresent(Main::printSeen);
                }

                private static void printSeen(final String value) {
                    System.out.println("seen:" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-if-present-static-consumer").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("seen:value\n");
    }

    @Test
    void mapComputeIfAbsentWithCapturedLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-if-absent-captured-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String suffix = "-value";
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.computeIfAbsent("demo", key -> key + suffix));
                    System.out.println(values.computeIfAbsent("demo", key -> "other"));
                    System.out.println(values.get("demo"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-compute-if-absent-captured-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo-value\ndemo-value\ndemo-value\n");
    }

    @Test
    void customFunctionalInterfaceLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("custom-functional-interface-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Mapper mapper = value -> value + "!";
                    System.out.println(mapper.apply("demo"));
                }

                @FunctionalInterface
                interface Mapper {
                    String apply(String value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/custom-functional-interface-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo!\n");
    }

    @Test
    void capturedCustomFunctionalInterfaceLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("captured-custom-functional-interface-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String suffix = "!";
                    final Mapper mapper = value -> value + suffix;
                    System.out.println(mapper.apply("demo"));
                }

                @FunctionalInterface
                interface Mapper {
                    String apply(String value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/captured-custom-functional-interface-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo!\n");
    }

    @Test
    void defaultMethodFunctionalInterfaceMethodReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("default-method-functional-interface-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ThrowingMapper trim = Main::trimOrFail;
                    System.out.println(trim.apply(" demo "));
                    System.out.println(trim.apply(null));
                }

                private static String trimOrFail(final String value) throws Exception {
                    if (value == null) {
                        throw new Exception("missing");
                    }
                    return value.trim();
                }

                interface ThrowingMapper {
                    String applyWithException(String value) throws Exception;

                    default String apply(final String value) {
                        try {
                            return applyWithException(value);
                        } catch (final Exception ignored) {
                            return null;
                        }
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/default-method-functional-interface-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo\nnull\n");
    }

    @Test
    void defaultMethodFunctionalInterfaceConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("default-method-functional-interface-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ThrowingMapper trim = new TrimMapper();
                    System.out.println(trim.apply(" demo "));
                    System.out.println(trim.apply(null));
                }

                private static final class TrimMapper implements ThrowingMapper {
                    @Override
                    public String applyWithException(final String value) throws Exception {
                        if (value == null) {
                            throw new Exception("missing");
                        }
                        return value.trim();
                    }
                }

                interface ThrowingMapper {
                    String applyWithException(String value) throws Exception;

                    default String apply(final String value) {
                        try {
                            return applyWithException(value);
                        } catch (final Exception ignored) {
                            return null;
                        }
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/default-method-functional-interface-concrete").toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stderr()).isEmpty();
        assertThat(nativeRun.stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo\nnull\n");
    }

    @Test
    void optionalEmptyOrElseThrowFailsAtRuntime() throws Exception {
        final Path project = project("optional-empty-or-else-throw");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.empty().orElseThrow();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/optional-empty-or-else-throw").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("optional is empty");
    }

    @Test
    void integerLongToStringIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("number-to-string-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.toString(123));
                    System.out.println(Integer.toString(Integer.MIN_VALUE));
                    System.out.println(Long.toString(9876543210L));
                    System.out.println(Long.toString(Long.MIN_VALUE));
                    System.out.println(Integer.toString(-7).equals("-7"));
                    System.out.println(Long.toString(-9L).length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/number-to-string-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("123\n-2147483648\n9876543210\n-9223372036854775808\ntrue\n2\n");
    }

    @Test
    void floatToStringIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("float-to-string-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Float.toString(1.5f));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-to-string-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void doubleToStringIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("double-to-string-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Double.toString(1.5d));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/double-to-string-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void floatIntBitsToFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("float-int-bits-to-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Float.intBitsToFloat(1069547520));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-int-bits-to-float").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void doubleLongBitsToDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("double-long-bits-to-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Double.longBitsToDouble(4609434218613702656L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/double-long-bits-to-double").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intNegationWrapsMinimumValueLikeJvm() throws Exception {
        final Path project = project("int-negation-minimum");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(negate(Integer.MIN_VALUE));
                }

                private static int negate(final int value) {
                    return -value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/int-negation-minimum").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void jdkMathIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("jdk-math-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.abs(-9));
                    System.out.println(Math.abs(Integer.MIN_VALUE));
                    System.out.println(Math.min(4, -7));
                    System.out.println(Math.max(4, -7));
                    System.out.println(Math.abs(-12L));
                    System.out.println(Math.abs(Long.MIN_VALUE));
                    System.out.println(Math.min(100L, -200L));
                    System.out.println(Math.max(100L, -200L));
                    System.out.println(Math.abs(-1.25f));
                    System.out.println(1.0f / Math.abs(-0.0f));
                    System.out.println(1.0d / Math.abs(-0.0d));
                    System.out.println(Math.abs(-3.5d));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/jdk-math-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("9\n-2147483648\n-7\n4\n12\n-9223372036854775808\n-200\n100\n1.25\nInfinity\nInfinity\n3.5\n");
    }

    @Test
    void mathToIntExactBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("math-to-int-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.toIntExact(123456789L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/math-to-int-exact").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("123456789\n");
    }

    @Test
    void mathAddExactIntBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("math-add-exact-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.addExact(1_000_000_000, 234_567_890));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/math-add-exact-int").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mathAddExactIntEvaluatesOperandsOnceInOrderInsidePrintln() throws Exception {
        final Path project = project("math-add-exact-int-operand-order");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static int trace;

                private Main() {
                }

                private static int left() {
                    trace = trace * 10 + 1;
                    return 4;
                }

                private static int right() {
                    trace = trace * 10 + 2;
                    return 5;
                }

                public static void main(final String[] args) {
                    System.out.println(Math.addExact(left(), right()));
                    System.out.println(trace);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/math-add-exact-int-operand-order").toString())).stdout()
            : "";

        assertThat(build.exitCode() + "\n" + build.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mathAddExactIntOverflowFailsAtRuntime() throws Exception {
        final Path project = project("math-add-exact-int-overflow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.addExact(Integer.MAX_VALUE, 1));
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/math-add-exact-int-overflow").toString()))
            : new ProcessResult(-1, "", build.stderr());

        assertThat(List.of(
            build.exitCode(),
            nativeRun.exitCode() == 0 ? 0 : 1,
            nativeRun.stderr().contains("java/lang/ArithmeticException") ? 1 : 0
        )).containsExactly(0, 1, 1);
    }

    @Test
    void mathAddExactIntPositiveOverflowCanBeCaught() throws Exception {
        final Path project = project("math-add-exact-int-caught-positive-overflow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static int add() {
                    try {
                        return Math.addExact(Integer.MAX_VALUE, 1);
                    } catch (final ArithmeticException ignored) {
                        return 41;
                    }
                }

                public static void main(final String[] args) {
                    System.out.println(add());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/math-add-exact-int-caught-positive-overflow").toString())).stdout()
            : "";

        assertThat(build.exitCode() + "\n" + build.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mathAddExactIntNegativeOverflowCanBeCaught() throws Exception {
        final Path project = project("math-add-exact-int-caught-negative-overflow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static int add() {
                    try {
                        return Math.addExact(Integer.MIN_VALUE, -1);
                    } catch (final ArithmeticException ignored) {
                        return -41;
                    }
                }

                public static void main(final String[] args) {
                    System.out.println(add());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/math-add-exact-int-caught-negative-overflow").toString())).stdout()
            : "";

        assertThat(build.exitCode() + "\n" + build.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void exhaustiveEnumSwitchExpressionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("exhaustive-enum-switch-expression");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private enum Action {
                    PRESS,
                    FOCUS,
                    SET_VALUE
                }

                private Main() {
                }

                private static int flag(final Action action) {
                    return switch (action) {
                        case PRESS -> 1;
                        case FOCUS -> 2;
                        case SET_VALUE -> 4;
                    };
                }

                public static void main(final String[] args) {
                    for (final Action action : Action.values()) {
                        System.out.println(flag(action));
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/exhaustive-enum-switch-expression").toString())).stdout()
            : "";

        assertThat(build.exitCode() + "\n" + build.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void matchExceptionCauseConstructorCanBeCaughtAsRuntimeExceptionWithItsMessage() throws Exception {
        final Path project = project("match-exception-cause-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new MatchException("boom", null);
                    } catch (final RuntimeException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/match-exception-cause-constructor").toString())).stdout()
            : "";

        assertThat(build.exitCode() + "\n" + build.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void exactCatchNullEnumLookupBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("exact-catch-null-enum-lookup");
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE,
                GREEN
            }
            """);
        writeJava(project, "com.acme.EnumLookupSupport", """
            package com.acme;

            public final class EnumLookupSupport {
                private EnumLookupSupport() {
                }

                public static <T extends Enum<T>> T enumOf(final Object value, final Class<T> type) {
                    try {
                        if (value instanceof Number) {
                            final int ordinal = ((Number) value).intValue();
                            final T[] enums = type.getEnumConstants();
                            return ordinal >= 0 && ordinal < enums.length ? enums[ordinal] : null;
                        }
                        return Enum.valueOf(type, String.valueOf(value));
                    } catch (final IllegalArgumentException ignored) {
                        return null;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(EnumLookupSupport.enumOf("RED", Color.class));
                    System.out.println(EnumLookupSupport.enumOf("MISSING", Color.class));
                    System.out.println(EnumLookupSupport.enumOf(1, Color.class));
                    System.out.println(EnumLookupSupport.enumOf(4L, Color.class));
                    System.out.println(EnumLookupSupport.enumOf(null, Color.class));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/exact-catch-null-enum-lookup").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("RED\nnull\nBLUE\nnull\nnull\n");
    }

    @Test
    void exactCatchNullFallibleApplyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("exact-catch-null-fallible-function-apply");
        writeJava(project, "com.acme.FallibleFunction", """
            package com.acme;

            @FunctionalInterface
            public interface FallibleFunction<T, R> {
                R applyWithException(T value) throws Exception;

                default R apply(T value) {
                    try {
                        return applyWithException(value);
                    } catch (final Exception ignored) {
                        return null;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final FallibleFunction<String, String> trimOrNull = value -> {
                        if (value == null) {
                            throw new IllegalArgumentException("missing");
                        }
                        return value.trim();
                    };
                    System.out.println(trimOrNull.apply("  ok  "));
                    System.out.println(trimOrNull.apply(null));
                    System.out.println(trimOrNull.apply(" again "));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/exact-catch-null-fallible-function-apply").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ok\nnull\nagain\n");
    }

    @Test
    void capturedConsumerOverObjectArrayBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("captured-consumer-object-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                private static final class Sink {
                    private Object last;
                }

                private static void iterate(final Object[] values, final Consumer<Object> consumer) {
                    for (final Object value : values) {
                        consumer.accept(value);
                    }
                }

                public static void main(final String[] args) {
                    final Sink sink = new Sink();
                    final Consumer<Object> consumer = value -> sink.last = value;
                    iterate(new Object[]{"first", "second"}, consumer);
                    System.out.println(sink.last);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/captured-consumer-object-array").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("second\n");
    }

    @Test
    void mathToIntExactOverflowFailsAtRuntime() throws Exception {
        final Path project = project("math-to-int-exact-overflow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.toIntExact(2147483648L));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/math-to-int-exact-overflow").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("integer overflow");
    }

    @Test
    void jdkSystemTimeIntrinsicsBuildAndReturnLongValues() throws Exception {
        final Path project = project("jdk-time-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.nanoTime());
                    System.out.println(System.currentTimeMillis());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final String nativeOutput = process(project, List.of(project.resolve(".javan/bin/jdk-time-intrinsics").toString())).stdout();
        assertThat(nativeOutput).matches("[0-9]+\\n[0-9]+\\n");
    }

    @Test
    void systemExitBuildsAndReturnsStatusCode() throws Exception {
        final Path project = project("system-exit");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.exit(7);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/system-exit").toString()));

        assertThat(nativeRun.exitCode()).isEqualTo(7);
    }

    @Test
    void setEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-owner-set", """
            final Set<String> receiver = Set.of("left", "right");
            System.out.println(receiver.equals(Set.of("right", "left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void abstractSetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-owner-abstract-set", """
            final HashSet<String> values = new HashSet<>();
            values.add("left");
            values.add("right");
            final AbstractSet<String> receiver = values;
            System.out.println(receiver.equals(Set.of("right", "left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void hashSetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-owner-hash-set", """
            final HashSet<String> receiver = new HashSet<>();
            receiver.add("left");
            receiver.add("right");
            System.out.println(receiver.equals(Set.of("right", "left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void linkedHashSetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-owner-linked-hash-set", """
            final LinkedHashSet<String> receiver = new LinkedHashSet<>();
            receiver.add("left");
            receiver.add("right");
            System.out.println(receiver.equals(Set.of("right", "left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void setEqualsBuildEmitsOneGeneratedNativeCall() throws Exception {
        final Path project = project("set-equals-generated-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Set.of("left").equals(Set.of("left")));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String source = run.exitCode() == 0
            ? Files.readString(project.resolve(".javan/generated/main.c"))
            : "";

        assertThat(source.split("javan_set_equals\\(", -1).length - 1).isEqualTo(1);
    }

    @Test
    void setCopyOfEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-copy-of", """
            System.out.println(Set.copyOf(List.of("left")).equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void collectionsEmptySetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-empty-set", """
            System.out.println(Collections.emptySet().equals(Set.of()));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void collectionsSingletonSetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-singleton-set", """
            System.out.println(Collections.singleton("left").equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void collectionsUnmodifiableSetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-unmodifiable-set", """
            final HashSet<String> values = new HashSet<>();
            values.add("left");
            System.out.println(Collections.unmodifiableSet(values).equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void linkedHashSetProducerEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-linked-hash-set", """
            final LinkedHashSet<String> values = new LinkedHashSet<>();
            values.add("left");
            System.out.println(values.equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void hashSetFactoryEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-hash-set-factory", """
            final HashSet<String> values = HashSet.newHashSet(1);
            values.add("left");
            System.out.println(values.equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void linkedHashSetFactoryEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-linked-hash-set-factory", """
            final LinkedHashSet<String> values = LinkedHashSet.newLinkedHashSet(1);
            values.add("left");
            System.out.println(values.equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void mapKeySetEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-map-key-set", """
            final Map<String, String> values = new LinkedHashMap<>();
            values.put("left", "one");
            System.out.println(values.keySet().equals(Set.of("left")));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void collectionsUnmodifiableCollectionOfSetIsNotEqual() throws Exception {
        assertThat(runSetEqualsParity("set-equals-wrapper-unmodifiable-collection", """
            final Set<String> values = Set.of("left");
            System.out.println(values.equals(Collections.unmodifiableCollection(values)));
            """)).isEqualTo(setEqualsParitySuccess("false\n"));
    }

    @Test
    void hashSetNullMemberEqualsBuildsAndMatchesJvmOutput() throws Exception {
        assertThat(runSetEqualsParity("set-equals-producer-hash-set-null", """
            final HashSet<String> left = new HashSet<>();
            final HashSet<String> right = new HashSet<>();
            left.add(null);
            right.add(null);
            System.out.println(left.equals(right));
            """)).isEqualTo(setEqualsParitySuccess("true\n"));
    }

    @Test
    void entryAnchoredTypedHandlerBuildsAndMatchesJvmFallback() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-typed-handler", "RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "invalid");
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerReturnsSuccessfulValue() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-typed-success", "RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "42");
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("42\n"));
    }

    @Test
    void entryAnchoredTypedHandlerCatchesNullInputReceiver() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-null-input", "RuntimeException", """
            System.out.println(parse(null));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerCatchesNullMapReceiver() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-null-map", "RuntimeException", """
            System.out.println(parse(new Envelope(null)));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerCatchesNullMapValue() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-null-value", "RuntimeException", """
            System.out.println(parse(new Envelope(new LinkedHashMap<>())));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerCatchesWrongMapValueType() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-wrong-type", "RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", Long.valueOf(42L));
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredCheckcastPassesNullToApplicationCall() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-null-checkcast", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(cast(null));
                }

                private static long cast(final Object value) {
                    try {
                        return classify((String) value);
                    } catch (final RuntimeException exception) {
                        return -1L;
                    }
                }

                private static long classify(final String value) {
                    return value == null ? 7L : 8L;
                }
            }
            """)).isEqualTo(typedHandlerParitySuccess("7\n"));
    }

    @Test
    void entryAnchoredTypedHandlerCatchesPositiveOverflow() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-positive-overflow", "RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "9223372036854775808");
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerCatchesNegativeOverflow() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-negative-overflow", "RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "-9223372036854775809");
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerSupportsSpecificNumberFormatCatch() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-specific-catch", "NumberFormatException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "invalid");
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredTypedHandlerAlternatesSuccessAndFailure() throws Exception {
        assertThat(runMapTypedHandlerParity("entry-anchored-alternating", "RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "7");
            System.out.println(parse(new Envelope(payload)));
            payload.put("cipher", "invalid");
            System.out.println(parse(new Envelope(payload)));
            payload.put("cipher", "8");
            System.out.println(parse(new Envelope(payload)));
            """)).isEqualTo(typedHandlerParitySuccess("7\n-1\n8\n"));
    }

    @Test
    void entryAnchoredTypedHandlerSurvivesRepeatedFailureWithForcedGc() throws Exception {
        final String source = mapTypedHandlerSource("RuntimeException", """
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cipher", "invalid");
            long total = 0L;
            for (int index = 0; index < 100000; index++) {
                total += parse(new Envelope(payload));
            }
            System.out.println(total);
            """);

        assertThat(runTypedHandlerParity(
            "entry-anchored-forced-gc",
            source,
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        )).isEqualTo(typedHandlerParitySuccess("-100000\n"));
    }

    @Test
    void entryAnchoredApplicationValidationIsCaught() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-application-validation", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(validated(-1L));
                }

                private static long validated(final long value) {
                    try {
                        return requirePositive(value);
                    } catch (final IllegalArgumentException exception) {
                        return -1L;
                    }
                }

                private static long requirePositive(final long value) {
                    if (value < 0L) {
                        throw new IllegalArgumentException("negative");
                    }
                    return value;
                }
            }
            """)).isEqualTo(typedHandlerParitySuccess("-1\n"));
    }

    @Test
    void entryAnchoredCaughtObjectCanBeRethrown() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-caught-rethrow", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(observe());
                }

                private static long observe() {
                    try {
                        return parse("invalid");
                    } catch (final RuntimeException exception) {
                        return -2L;
                    }
                }

                private static long parse(final String value) {
                    try {
                        return Long.parseLong(value);
                    } catch (final RuntimeException exception) {
                        throw exception;
                    }
                }
            }
        """)).isEqualTo(typedHandlerParitySuccess("-2\n"));
    }

    @Test
    void entryAnchoredHandlerRejectsUnsupportedCaughtValueInspection() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-caught-inspection", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(parse("invalid"));
                }

                private static long parse(final String value) {
                    try {
                        return Long.parseLong(value);
                    } catch (final RuntimeException exception) {
                        return exception == null ? 1L : -2L;
                    }
                }
            }
            """).buildStderr()).contains("caught throwable escape is not supported");
    }

    @Test
    void entryAnchoredStoredThrowableFactoryIsRejectedBeforeNativeExecution() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-stored-throwable", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(observe());
                }

                private static long observe() {
                    try {
                        return parse("invalid");
                    } catch (final UnsupportedOperationException exception) {
                        return -2L;
                    }
                }

                private static long parse(final String value) {
                    try {
                        return Long.parseLong(value);
                    } catch (final RuntimeException exception) {
                        final UnsupportedOperationException converted = unsupported();
                        throw converted;
                    }
                }

                private static UnsupportedOperationException unsupported() {
                    return new UnsupportedOperationException("converted");
                }
            }
            """).buildStderr()).contains("exception handler needs a known thrown type");
    }

    @Test
    void entryAnchoredTypedHandlerRejectsCustomMapDispatch() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-custom-map-dispatch", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(parse(new CustomMap()));
                }

                private static long parse(final Map<String, Object> input) {
                    try {
                        return Long.parseLong((String) input.get("cipher"));
                    } catch (final RuntimeException exception) {
                        return -1L;
                    }
                }

                private static final class CustomMap extends LinkedHashMap<String, Object> {
                    @Override
                    public Object get(final Object key) {
                        return "42";
                    }
                }
            }
            """).buildStderr()).contains("error[JAVAN014]");
    }

    @Test
    void entryAnchoredTypedHandlerRejectsDefaultMapDispatch() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-default-map-dispatch", """
            package com.acme;

            import java.util.Collection;
            import java.util.Map;
            import java.util.SequencedMap;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(parse(new DefaultMap()));
                }

                private static long parse(final Map<String, Object> input) {
                    try {
                        return Long.parseLong((String) input.get("cipher"));
                    } catch (final RuntimeException exception) {
                        return -1L;
                    }
                }

                private interface CustomMap extends SequencedMap<String, Object> {
                    @Override
                    default int size() {
                        return 0;
                    }

                    @Override
                    default boolean isEmpty() {
                        return true;
                    }

                    @Override
                    default boolean containsKey(final Object key) {
                        return false;
                    }

                    @Override
                    default boolean containsValue(final Object value) {
                        return false;
                    }

                    @Override
                    default Object get(final Object key) {
                        return "42";
                    }

                    @Override
                    default Object put(final String key, final Object value) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    default Object remove(final Object key) {
                        return null;
                    }

                    @Override
                    default void putAll(final Map<? extends String, ?> values) {
                    }

                    @Override
                    default void clear() {
                    }

                    @Override
                    default Set<String> keySet() {
                        return Set.of();
                    }

                    @Override
                    default Collection<Object> values() {
                        return Set.of();
                    }

                    @Override
                    default Set<Entry<String, Object>> entrySet() {
                        return Set.of();
                    }

                    @Override
                    default SequencedMap<String, Object> reversed() {
                        return this;
                    }
                }

                private static final class DefaultMap implements CustomMap {
                }
            }
            """).buildStderr()).contains("error[JAVAN014]");
    }

    @Test
    void unusedCustomMapDoesNotRejectRuntimeMapHandler() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-unused-custom-map", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, Object> input = new LinkedHashMap<>();
                    input.put("cipher", "42");
                    System.out.println(parse(input));
                }

                private static long parse(final Map<String, Object> input) {
                    try {
                        return Long.parseLong((String) input.get("cipher"));
                    } catch (final RuntimeException exception) {
                        return -1L;
                    }
                }

                private static final class UnusedMap extends LinkedHashMap<String, Object> {
                    @Override
                    public Object get(final Object key) {
                        return "unused";
                    }
                }
            }
            """)).isEqualTo(typedHandlerParitySuccess("42\n"));
    }

    @Test
    void unknownThrowAfterUnrelatedHandlerStillBuilds() throws Exception {
        final Path project = project("unknown-throw-after-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    fail(new IllegalStateException("boom"));
                }

                private static void fail(final RuntimeException failure) {
                    long parsed;
                    try {
                        parsed = Long.parseLong("1");
                    } catch (final NumberFormatException exception) {
                        parsed = -1L;
                    }
                    if (parsed != 1L) {
                        return;
                    }
                    throw failure;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
    }

    @Test
    void entryAnchoredThrowNullProducesNullPointerException() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-throw-null", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(observe());
                }

                private static long observe() {
                    try {
                        throw null;
                    } catch (final NullPointerException exception) {
                        return -3L;
                    }
                }
            }
            """)).isEqualTo(typedHandlerParitySuccess("-3\n"));
    }

    @Test
    void entryAnchoredNonassignableErrorEscapesRuntimeCatch() throws Exception {
        assertThat(runTypedHandlerParity("entry-anchored-error-rethrow", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(observe());
                }

                private static long observe() {
                    try {
                        return guarded();
                    } catch (final AssertionError error) {
                        return -4L;
                    }
                }

                private static long guarded() {
                    try {
                        return fail();
                    } catch (final RuntimeException exception) {
                        return -1L;
                    }
                }

                private static long fail() {
                    throw new AssertionError();
                }
            }
            """)).isEqualTo(typedHandlerParitySuccess("-4\n"));
    }

    private TypedHandlerParityResult runMapTypedHandlerParity(
        final String projectName,
        final String catchType,
        final String statements
    ) throws Exception {
        return runTypedHandlerParity(
            projectName,
            mapTypedHandlerSource(catchType, statements),
            Map.of()
        );
    }

    private static String mapTypedHandlerSource(
        final String catchType,
        final String statements
    ) {
        return """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
            %s
                }

                private static long parse(final Envelope input) {
                    try {
                        return Long.parseLong((String) input.payload().get("cipher"));
                    } catch (final %s exception) {
                        return -1L;
                    }
                }

                private record Envelope(Map<String, Object> payload) {
                }
            }
            """.formatted(statements.indent(8), catchType);
    }

    private TypedHandlerParityResult runTypedHandlerParity(
        final String projectName,
        final String source
    ) throws Exception {
        return runTypedHandlerParity(projectName, source, Map.of());
    }

    private TypedHandlerParityResult runTypedHandlerParity(
        final String projectName,
        final String source,
        final Map<String, String> environment
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source);
        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(
                project,
                List.of(project.resolve(".javan/bin/" + projectName).toString()),
                Duration.ofSeconds(30),
                environment
            )
            : new ProcessResult(-1, "", "not built");
        return new TypedHandlerParityResult(
            build.exitCode(),
            build.stderr(),
            nativeRun.exitCode(),
            nativeRun.stdout(),
            nativeRun.stderr(),
            jvmOutput
        );
    }

    private static TypedHandlerParityResult typedHandlerParitySuccess(final String stdout) {
        return new TypedHandlerParityResult(0, "", 0, stdout, "", stdout);
    }

    private SetEqualsParityResult runSetEqualsParity(final String projectName, final String statements) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractSet;
            import java.util.Collections;
            import java.util.HashSet;
            import java.util.LinkedHashMap;
            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Map;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
            %s
                }
            }
            """.formatted(statements.indent(8)));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()))
            : new ProcessResult(-1, "", "not built");
        return new SetEqualsParityResult(
            run.exitCode(),
            run.stderr(),
            nativeRun.exitCode(),
            nativeRun.stdout(),
            nativeRun.stderr(),
            jvmOutput
        );
    }

    private static SetEqualsParityResult setEqualsParitySuccess(final String stdout) {
        return new SetEqualsParityResult(0, "", 0, stdout, "", stdout);
    }

    private record SetEqualsParityResult(
        int buildExitCode,
        String buildStderr,
        int nativeExitCode,
        String nativeStdout,
        String nativeStderr,
        String jvmStdout
    ) {
    }

    private record TypedHandlerParityResult(
        int buildExitCode,
        String buildStderr,
        int nativeExitCode,
        String nativeStdout,
        String nativeStderr,
        String jvmStdout
    ) {
    }

    private void assertNumericWrapperConstableMethodsBuildAndMatchJvmOutput(
        final String projectName,
        final String wrapperType,
        final String wrapperExpression
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Throwable {
                    final %s value = %s;
                    System.out.println(value.describeConstable().orElseThrow());
                    System.out.println(value.resolveConstantDesc(null));
                    final Object widened = value.resolveConstantDesc(null);
                    System.out.println(widened);
                }
            }
            """.formatted(wrapperType, wrapperExpression));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/" + projectName).toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    private void assertPrimitiveWrapperPrintableBuildAndMatchJvmOutput(
        final String projectName,
        final String wrapperType,
        final String wrapperExpression
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final %s value = %s;
                    System.out.println(value.toString());
                    System.out.println(value);
                }
            }
            """.formatted(wrapperType, wrapperExpression));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/" + projectName).toString())).stdout())
            .isEqualTo(jvmOutput);
    }

}
