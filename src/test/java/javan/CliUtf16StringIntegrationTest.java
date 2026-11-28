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
            new Comparison("region-match", "value.regionMatches(1, \"x\\u20ac\\ud83d\\ude00y\", 1, 3)", "true"),
            new Comparison("region-case-mismatch", "value.regionMatches(0, \"A\", 0, 1)", "false"),
            new Comparison("region-low-half", "value.regionMatches(3, \"x\\ude00b\", 1, 2)", "true"),
            new Comparison("region-high-half-mismatch", "value.regionMatches(2, \"\\ud83e\", 0, 1)", "false"),
            new Comparison("region-embedded-nul", "new String(new char[]{'a', 0, 'b'}).regionMatches(1, \"x\\0b\", 1, 2)", "true"),
            new Comparison("region-zero-at-end", "value.regionMatches(7, \"\", 0, 0)", "true"),
            new Comparison("region-zero-past-end", "value.regionMatches(8, \"\", 0, 0)", "false"),
            new Comparison("region-right-past-end", "value.regionMatches(0, \"a\", 1, 1)", "false"),
            new Comparison("region-negative-length", "value.regionMatches(8, \"\", 1, -1)", "true"),
            new Comparison("region-minimum-length", "value.regionMatches(Integer.MAX_VALUE, \"\", Integer.MAX_VALUE, Integer.MIN_VALUE)", "true"),
            new Comparison("region-maximum-length", "value.regionMatches(1, \"\", 1, Integer.MAX_VALUE)", "false"),
            new Comparison("region-ignore-case-cyrillic-16", "\"\\u1c89\".regionMatches(true, 0, \"\\u1c8a\", 0, 1)", "true"),
            new Comparison("region-exact-cyrillic-16", "\"\\u1c89\".regionMatches(false, 0, \"\\u1c8a\", 0, 1)", "false"),
            new Comparison("region-ignore-case-kelvin", "\"\\u212a\".regionMatches(true, 0, \"k\", 0, 1)", "true"),
            new Comparison("region-ignore-case-turkish", "\"I\\u0130\".regionMatches(true, 0, \"\\u0131i\", 0, 2)", "true"),
            new Comparison("region-ignore-case-sigma-nul", "\"\\0\\u03a3\".regionMatches(true, 0, \"\\0\\u03c2\", 0, 2)", "true"),
            new Comparison("region-ignore-case-sharp-s", "\"\\u1e9e\".regionMatches(true, 0, \"\\u00df\", 0, 1)", "true"),
            new Comparison("region-ignore-case-no-expansion", "\"\\u00dfx\".regionMatches(true, 0, \"SS\", 0, 2)", "false"),
            new Comparison("region-ignore-case-no-normalization", "\"\\u00e9x\".regionMatches(true, 0, \"e\\u0301\", 0, 2)", "false"),
            new Comparison("region-ignore-case-supplementary", "\"a\\ud801\\udc00\".regionMatches(true, 1, \"\\ud801\\udc28\", 0, 2)", "true"),
            new Comparison("region-ignore-case-high-half", "\"\\ud801\\udc00\".regionMatches(true, 0, \"\\ud801\\udc28\", 0, 1)", "true"),
            new Comparison("region-ignore-case-low-half", "\"\\ud801\\udc00\".regionMatches(true, 1, \"\\ud801\\udc28\", 1, 1)", "false"),
            new Comparison("region-ignore-case-unpaired-high", "\"\\ud801\".regionMatches(true, 0, \"\\ud801\\udc28\", 0, 1)", "true"),
            new Comparison("region-ignore-case-minimum-length", "value.regionMatches(true, Integer.MAX_VALUE, \"\", Integer.MAX_VALUE, Integer.MIN_VALUE)", "true"),
            new Comparison("region-ignore-case-maximum-length", "value.regionMatches(true, 1, \"\", 1, Integer.MAX_VALUE)", "false"),
            new Comparison("region-exact-zero-at-end", "value.regionMatches(false, 7, \"\", 0, 0)", "true"),
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
    void regionMatchesPreservesRangeAndNullCheckOrder() throws Exception {
        final Path project = project("utf16-region-matches-nulls");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int mark(int value) { System.out.println(value); return value; }
                static String other() { System.out.println("other"); return null; }
                static int compare(String value, int left, String other, int right, int length) {
                    boolean result;
                    try { result = value.regionMatches(left, other, right, length); }
                    catch (NullPointerException expected) { return -1; }
                    return result ? 1 : 0;
                }
                static boolean unchecked(String value) { return value.regionMatches(0, null, 0, 1); }
                static void checkTransport(String value) {
                    try { unchecked(value); }
                    catch (NullPointerException expected) { System.out.println("null argument"); }
                }
                static void checkArguments(String value) {
                    try { value.regionMatches(mark(0), other(), mark(1), mark(2)); }
                    catch (NullPointerException expected) { System.out.println("null receiver"); }
                }
                public static void main(String[] args) {
                    System.out.println(compare("a", -1, null, 0, 1));
                    System.out.println(compare("a", 0, null, -1, 1));
                    System.out.println(compare("a", 2, null, 0, 1));
                    System.out.println(compare("a", 0, null, Integer.MAX_VALUE, 1));
                    System.out.println(compare(null, -1, null, -1, 1));
                    checkArguments(null);
                    checkTransport("a");
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        assertThat(expected).isEqualTo("0\n0\n0\n-1\n-1\n0\nother\n1\n2\nnull receiver\nnull argument\n");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/utf16-region-matches-nulls").toString()),
            defaultProcessTimeout(), Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void regionMatchesKeepsBothStringsAliveWhileLaterArgumentsAllocate() throws Exception {
        final Path project = project("utf16-region-matches-argument-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static String receiver() {
                    System.out.println("receiver");
                    return new String(new char[]{'a', '\\ud83d', '\\ude00', 'b'});
                }
                static String other() {
                    System.out.println("other");
                    return new String(new char[]{'x', '\\ud83d', '\\ude00', 'y'});
                }
                static int argument(String marker, int size) {
                    System.out.println(marker);
                    char[] chars = new char[size];
                    return new String(chars).length();
                }
                public static void main(String[] args) {
                    System.out.println(receiver().regionMatches(
                        argument("offset", 1), other(), argument("other offset", 1), argument("length", 2)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        assertThat(expected).isEqualTo("receiver\noffset\nother\nother offset\nlength\ntrue\n");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/utf16-region-matches-argument-gc").toString()),
            defaultProcessTimeout(), Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void regionMatchesWithCaseFlagPreservesNullChecksAndRootedArgumentOrder() throws Exception {
        final Path project = project("utf16-region-matches-case-flag");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static boolean flag() { System.out.println("flag"); return true; }
                static String value(String marker, char last) {
                    System.out.println(marker);
                    return new String(new char[]{'\\ud801', last});
                }
                static int number(String marker, int result) {
                    System.out.println(marker);
                    return new String(new char[result]).length();
                }
                static int compare(String value, boolean flag, int offset, String other, int start, int count) {
                    boolean matches;
                    try { matches = value.regionMatches(flag, offset, other, start, count); }
                    catch (NullPointerException expected) { return -1; }
                    return matches ? 1 : 0;
                }
                public static void main(String[] args) {
                    System.out.println(compare("x", true, 2, null, 0, 1));
                    System.out.println(compare("x", false, 2, null, 0, 1));
                    System.out.println(compare("x", true, 0, null, -1, 1));
                    System.out.println(compare("x", true, 0, null, Integer.MAX_VALUE, 1));
                    System.out.println(compare("x", false, 0, null, Integer.MAX_VALUE, 1));
                    System.out.println(value("receiver", '\\udc00').regionMatches(
                        flag(), number("offset", 0), value("other", '\\udc28'), number("start", 0), number("count", 2)));
                    String missing = args.length == 0 ? null : "x";
                    try {
                        missing.regionMatches(flag(), number("offset", 0), value("other", '\\udc28'),
                            number("start", 0), number("count", 2));
                    } catch (NullPointerException expected) { System.out.println("null receiver"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        assertThat(expected).isEqualTo("0\n0\n0\n-1\n-1\nreceiver\nflag\noffset\nother\nstart\ncount\ntrue\n"
            + "flag\noffset\nother\nstart\ncount\nnull receiver\n");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/utf16-region-matches-case-flag").toString()),
            defaultProcessTimeout(), Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualTo(expected);
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
