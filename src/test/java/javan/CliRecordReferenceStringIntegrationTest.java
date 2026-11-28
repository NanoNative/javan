package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliRecordReferenceStringIntegrationTest extends CliIntegrationSupport {
    @Test
    void convertsAnExactApplicationReferenceComponent() throws Exception {
        final Path project = project("record-string-application-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Item {
                    final String name;
                    Item(String name) { this.name = name; }
                    public String toString() { return "item:" + name; }
                }
                static final class Unrelated {
                    String id() { return "unrelated"; }
                    public String toString() { System.setProperty("unused", "never"); return "unused"; }
                }
                record Value(Item item) { }
                public static void main(String[] args) {
                    System.out.println(new Unrelated().id());
                    Object value = new Value(new Item("chosen"));
                    System.out.println("value:" + value);
                    System.out.println(new Value(null).toString());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-application-reference").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("unrelated\nvalue:Value[item=item:chosen]\nValue[item=null]\n");
    }
    @Test
    void usesDefaultObjectStringForAnExactComponentWithoutAnOuterObjectConversion() throws Exception {
        final Path project = project("record-string-default-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Item { public int hashCode() { return 42; } }
                record Value(Item item) { }
                public static void main(String[] args) { System.out.println(new Value(new Item()).toString()); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-default-reference").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("Value[item=com.acme.Main$Item@2a]\n");
    }

    @Test
    void convertsInheritedAndNullReturningToStringOverrides() throws Exception {
        final Path project = project("record-string-inherited-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static class Base { public String toString() { return "inherited"; } }
                static final class Child extends Base { }
                static final class NullValue { public String toString() { return null; } }
                record Value(Child child, NullValue absent) { }
                public static void main(String[] args) { System.out.println(new Value(new Child(), new NullValue()).toString()); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-inherited-reference").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("Value[child=inherited, absent=null]\n");
    }

    @Test
    void transportsComponentToStringFailureToTheCaller() throws Exception {
        final Path project = project("record-string-reference-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Item { public String toString() { throw new IllegalStateException("failure"); } }
                record Value(Item item) { }
                public static void main(String[] args) {
                    Object value = new Value(new Item());
                    try { System.out.println("value:" + value); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-reference-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failure\n");
    }

    @Test
    void keepsReferenceComponentStringsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("record-string-reference-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Item {
                    final int number;
                    Item(int number) { this.number = number; }
                    public String toString() { return "n=" + number; }
                }
                record Value(Item item) { }
                static final class Worker implements Runnable {
                    int total;
                    public void run() {
                        for (int n = 0; n < 300; n++) {
                            Object value = new Value(new Item(n));
                            String text = "value:" + value;
                            total += text.length();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Worker a = new Worker(); Worker b = new Worker();
                    Thread first = new Thread(a); Thread second = new Thread(b);
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(a.total + b.total);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-reference-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void stopsAfterAComponentFailureAndPropagatesThroughDirectToString() throws Exception {
        final Path project = project("record-string-direct-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int calls;
                static final class First { public String toString() { throw new IllegalStateException("failure"); } }
                static final class Second { public String toString() { calls++; return "second"; } }
                record Value(First first, Second second) { }
                public static void main(String[] args) {
                    Value value = new Value(new First(), new Second());
                    try { System.out.println(value.toString()); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                    System.out.println("calls:" + calls);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-direct-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failure\ncalls:0\n");
    }

    @Test
    void stopsAfterADefaultComponentHashFailure() throws Exception {
        final Path project = project("record-string-default-hash-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int calls;
                static final class First { public int hashCode() { throw new IllegalStateException("hash"); } }
                static final class Second { public String toString() { calls++; return "second"; } }
                record Value(First first, Second second) { }
                public static void main(String[] args) {
                    Value value = new Value(new First(), new Second());
                    try { System.out.println(value.toString()); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                    System.out.println("calls:" + calls);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-default-hash-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:hash\ncalls:0\n");
    }

    @Test
    void convertsAnExactNestedRecordComponent() throws Exception {
        final Path project = project("record-string-nested-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Inner(int number) { }
                record Outer(Inner inner) { }
                public static void main(String[] args) { System.out.println(new Outer(new Inner(7)).toString()); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-nested-record").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("Outer[inner=Inner[number=7]]\n");
    }

    @Test
    void transportsApplicationExceptionsFromAComponent() throws Exception {
        final Path project = project("record-string-application-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Problem extends RuntimeException { Problem() { super("failure"); } }
                static final class Item { public String toString() { throw new Problem(); } }
                record Value(Item item) { }
                public static void main(String[] args) {
                    Value value = new Value(new Item());
                    try { System.out.println(value.toString()); }
                    catch (Problem expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-application-exception").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failure\n");
    }

    @Test
    void rejectsAnUnboundedReferenceComponentRepeatably() throws Exception {
        final Path project = project("record-string-unbounded-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static class Item { public String toString() { return "item"; } }
                static final class Child extends Item { }
                record Value(Item item) { }
                public static void main(String[] args) { System.out.println(new Value(new Child()).toString()); }
            }
            """);
        final CliRun first = run(tempDir, "check", project.toString());
        final CliRun second = run(tempDir, "check", project.toString());
        assertThat(first.exitCode()).isNotZero().isEqualTo(second.exitCode());
        assertThat(first.stderr()).contains("JAVAN030").isEqualTo(second.stderr());
    }

}
