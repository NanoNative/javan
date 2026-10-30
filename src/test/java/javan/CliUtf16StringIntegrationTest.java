package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@NativeTest
@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliUtf16StringIntegrationTest extends CliIntegrationSupport {
    @TestFactory
    Stream<DynamicTest> stringComparisons() throws Exception {
        final List<Comparison> cases = List.of(
            new Comparison("equal", "value.equals(\"a\\u20ac\\ud83d\\ude00b\\ud83d\\ude00\")", "true"),
            new Comparison("different-last", "value.equals(\"a\\u20ac\\ud83d\\ude00b\\ud83d\\ude01\")", "false"),
            new Comparison("different-length", "value.equals(\"a\\u20ac\\ud83d\\ude00b\")", "false"),
            new Comparison("null", "value.equals(null)", "false"),
            new Comparison("prefix", "value.startsWith(\"a\\u20ac\\ud83d\\ude00\")", "true"),
            new Comparison("prefix-high-half", "value.startsWith(\"\\ud83d\", 2)", "true"),
            new Comparison("prefix-low-half", "value.startsWith(\"\\ude00b\", 3)", "true"),
            new Comparison("prefix-after-pair", "value.startsWith(\"b\", 4)", "true"),
            new Comparison("prefix-past-end", "value.startsWith(\"x\", 7)", "false"),
            new Comparison("empty-prefix-at-end", "value.startsWith(\"\", 7)", "true"),
            new Comparison("empty-prefix-past-end", "value.startsWith(\"\", 8)", "false"),
            new Comparison("suffix", "value.endsWith(\"b\\ud83d\\ude00\")", "true"),
            new Comparison("suffix-low-half", "value.endsWith(\"\\ude00\")", "true"),
            new Comparison("suffix-high-half", "value.endsWith(\"\\ud83d\")", "false"),
            new Comparison("find-low-half", "value.indexOf(new String(new char[]{'\\ude00', 'b'}))", "3"),
            new Comparison("find-last-pair", "value.lastIndexOf(new String(new char[]{'\\ud83d', '\\ude00'}))", "5"),
            new Comparison("find-last-low-half", "value.lastIndexOf(String.valueOf('\\ude00'), 4)", "3"),
            new Comparison("find-missing", "value.lastIndexOf(String.valueOf('\\ude01'))", "-1"),
            new Comparison("embedded-nul", "new String(new char[]{'a', 0, 'b'}).equals(new StringBuilder().append('a').append((char) 0).append('b').toString())", "true"),
            new Comparison("unpaired-high", "new String(new char[]{'a', '\\ud800'}).equals(\"a\\ud800\")", "true"),
            new Comparison("unpaired-low", "new String(new char[]{'a', '\\udc00'}).endsWith(\"\\udc00\")", "true")
        );
        final StringBuilder dispatch = new StringBuilder();
        final StringBuilder methods = new StringBuilder();
        for (int index = 0; index < cases.size(); index++) {
            dispatch.append("case ").append(index).append(" -> compare").append(index).append("(value);\n");
            methods.append("static void compare").append(index).append("(String value) { System.out.println(")
                .append(cases.get(index).expression()).append("); }\n");
        }
        final Path project = project("utf16-comparisons");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                public static void main(final String[] args) {
                    final String value = args.length < 2
                        ? new String(new char[]{'a', '\\u20ac', '\\ud83d', '\\ude00', 'b', '\\ud83d', '\\ude00'})
                        : args[1];
                    switch (args.length == 0 ? 0 : Integer.parseInt(args[0])) {
                        %s
                    }
                }
                %s
            }
            """.formatted(dispatch, methods));
        runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/utf16-comparisons").toString();
        return IntStream.range(0, cases.size()).mapToObj(index ->
            DynamicTest.dynamicTest(cases.get(index).scenario(), () -> {
                final String scenario = Integer.toString(index);
                final ProcessResult jvm = process(project, List.of(CliTestHarness.currentJavaCommand(),
                    "-cp", project.resolve("jvm-classes").toString(), "com.acme.Main", scenario));
                assertThat(jvm.exitCode()).as(jvm.stderr()).isZero();
                assertThat(jvm.stdout()).isEqualTo(cases.get(index).expected() + "\n");
                for (final List<String> command : List.of(
                    List.of(binary, scenario), List.of(binary, scenario, "a\u20ac\ud83d\ude00b\ud83d\ude00")
                )) {
                    final ProcessResult result = process(project, command, defaultProcessTimeout(),
                        Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
                    assertThat(result.exitCode()).as(result.stderr()).isZero();
                    assertThat(result.stderr()).isEmpty();
                    assertThat(result.stdout()).isEqualTo(jvm.stdout());
                }
            }));
    }

    record Comparison(String scenario, String expression, String expected) {
    }

    @Test
    void stringAndBuilderOperationsPreserveUtf16CodeUnits() throws Exception {
        final Path project = project("utf16-string-operations");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char euro = '\\u20ac';
                    final char nul = '\\0';
                    final char high = '\\ud83d';
                    final char low = '\\ude00';
                    final String value = new String(new char[]{'a', euro, nul, 'b', high, low});

                    System.out.println(value.length());
                    for (int index = 0; index < value.length(); index++) {
                        System.out.println((int) value.charAt(index));
                    }
                    System.out.println(value.indexOf(euro));
                    System.out.println(value.indexOf(nul));
                    System.out.println(value.indexOf(String.valueOf(euro)));
                    System.out.println(value.substring(1, 5).length());
                    System.out.println(value.replace(euro, 'x').charAt(1));

                    final StringBuilder builder = new StringBuilder();
                    builder.append(euro).append(nul).append(high).append(low);
                    builder.insert(1, 'x');
                    builder.setCharAt(1, euro);
                    builder.deleteCharAt(2);
                    System.out.println(builder.length());
                    for (int index = 0; index < builder.length(); index++) {
                        System.out.println((int) builder.charAt(index));
                    }
                    System.out.println(builder.substring(1).length());
                    System.out.println(builder.indexOf(String.valueOf(euro)));
                    System.out.print(String.valueOf(nul));
                    System.out.println(42);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/utf16-string-operations").toString())).stdout())
            .isEqualTo(jvmOutput);
    }
}
