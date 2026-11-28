package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliObjectStringValueIntegrationTest extends CliIntegrationSupport {
    @Test
    void concatenatesAnApplicationObjectUsingItsToStringOverride() throws Exception {
        final Path project = project("object-string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value {
                    public String toString() { return "custom"; }
                }
                public static void main(String[] args) {
                    Object value = new Value();
                    System.out.println("value:" + value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-concat").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value:custom\n");
    }
    @Test
    void avoidsRetainingUnrelatedToStringOverrides() throws Exception {
        final Path project = project("object-string-bounded");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public String toString() { return "selected"; } }
                static final class Unrelated {
                    String id() { return "unrelated"; }
                    public String toString() { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) {
                    System.out.println(new Unrelated().id());
                    Object value = new Value();
                    System.out.println("value:" + value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-bounded").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("unrelated\nvalue:selected\n");
    }

    @Test
    void convertsEitherDynamicObjectAndNull() throws Exception {
        final Path project = project("object-string-dynamic");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class First { public String toString() { return "first"; } }
                static final class Second { public String toString() { return "second"; } }
                public static void main(String[] args) {
                    Object value = args.length == 0 ? new First() : new Second();
                    System.out.println("value:" + value);
                    System.out.println("null:" + (Object) null);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/object-string-dynamic");
        final ProcessResult first = process(project, List.of(binary.toString()));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo(expected).isEqualTo("value:first\nnull:null\n");
        final ProcessResult jvm = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            project.resolve("jvm-classes").toString(), "com.acme.Main", "second"));
        final ProcessResult second = process(project, List.of(binary.toString(), "second"));
        assertThat(jvm.exitCode()).as(jvm.stderr()).isZero();
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo(jvm.stdout()).isEqualTo("value:second\nnull:null\n");
    }

    @Test
    void transportsExceptionsFromToStringWithoutExecutingTheRemainingConcatenation() throws Exception {
        final Path project = project("object-string-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value {
                    public String toString() { throw new IllegalStateException("failure"); }
                }
                public static void main(String[] args) {
                    Object value = new Value();
                    try { System.out.println("value:" + value); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-exception").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failure\n");
    }

    @Test
    void preservesANullResultFromToString() throws Exception {
        final Path project = project("object-string-null-result");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public String toString() { return null; } }
                public static void main(String[] args) {
                    Object value = new Value();
                    System.out.println(String.valueOf(value) == null);
                    System.out.println("value:" + value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-null-result").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nvalue:null\n");
    }

    @Test
    void keepsApplicationToStringResultsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("object-string-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public String toString() { return "n=" + number; }
                }
                static final class Worker implements Runnable {
                    int total;
                    public void run() {
                        for (int n = 0; n < 300; n++) {
                            Object value = new Value(n);
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void convertsAnInheritedToStringPassedThroughAMethodArgument() throws Exception {
        final Path project = project("object-string-inherited-argument");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static class Base { public String toString() { return "inherited"; } }
                static final class Child extends Base { }
                static String render(Object value) { return "value:" + value; }
                public static void main(String[] args) { System.out.println(render(new Child())); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-inherited-argument").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value:inherited\n");
    }

}
