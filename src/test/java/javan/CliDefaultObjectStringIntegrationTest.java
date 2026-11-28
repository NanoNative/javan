package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliDefaultObjectStringIntegrationTest extends CliIntegrationSupport {
    @Test
    void defaultToStringUsesTheRuntimeClassAndOverriddenHashCode() throws Exception {
        final Path project = project("default-object-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public int hashCode() { return 42; } }
                public static void main(String[] args) {
                    Object value = new Value();
                    System.out.println("value:" + value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-string").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value:com.acme.Main$Value@2a\n");
    }
    @Test
    void defaultStringUsesIdentityHashWhenNotOverridden() throws Exception {
        final Path project = project("default-object-identity-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { }
                public static void main(String[] args) {
                    Object value = new Value();
                    Object plain = new Object();
                    System.out.println(String.valueOf(value).equals("com.acme.Main$Value@" + Integer.toHexString(System.identityHashCode(value))));
                    System.out.println(String.valueOf(plain).equals("java.lang.Object@" + Integer.toHexString(System.identityHashCode(plain))));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-identity-string").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n");
    }

    @Test
    void doesNotRetainHashCodeWhenToStringIsOverridden() throws Exception {
        final Path project = project("default-object-unused-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value {
                    public String toString() { return "selected"; }
                    public int hashCode() { System.setProperty("unused", "never"); return 42; }
                }
                public static void main(String[] args) { Object value = new Value(); System.out.println("value:" + value); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-unused-hash").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value:selected\n");
    }

    @Test
    void formatsInheritedAndNegativeHashCodes() throws Exception {
        final Path project = project("default-object-inherited-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static class Base { public int hashCode() { return Integer.MIN_VALUE; } }
                static final class Child extends Base { }
                public static void main(String[] args) { Object value = new Child(); System.out.println("value:" + value); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-inherited-hash").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value:com.acme.Main$Child@80000000\n");
    }

    @Test
    void transportsFailureFromOverriddenHashCode() throws Exception {
        final Path project = project("default-object-hash-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public int hashCode() { throw new IllegalStateException("failure"); } }
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-hash-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failure\n");
    }

    @Test
    void transportsStringConversionFailuresAcrossMethodCalls() throws Exception {
        final Path project = project("default-object-nested-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public int hashCode() { throw new IllegalStateException("hash"); } }
                static String render(Object value) { return "value:" + value; }
                public static void main(String[] args) {
                    Object value = new Value();
                    try { System.out.println(render(value)); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-nested-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:hash\n");
    }

    @Test
    void keepsDefaultObjectStringsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("default-object-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public int hashCode() { return number; }
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/default-object-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void transportsOverriddenToStringFailureAcrossMethodCalls() throws Exception {
        final Path project = project("object-string-nested-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public String toString() { throw new IllegalArgumentException("string"); } }
                static String render(Object value) { return "value:" + value; }
                public static void main(String[] args) {
                    Object value = new Value();
                    try { System.out.println(render(value)); }
                    catch (IllegalArgumentException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/object-string-nested-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:string\n");
    }

}
