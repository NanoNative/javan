package javan;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliPrintObjectIntegrationTest extends CliIntegrationSupport {
    @Test
    void printsBoundedApplicationObjectsAndNull() throws Exception {
        final Path project = project("print-application-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Value(int n) { }
                static final class Custom { public String toString() { return "custom"; } }
                static final class Unrelated {
                    String id() { return "unrelated"; }
                    public String toString() { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) {
                    System.out.println(new Unrelated().id());
                    System.out.print((Object) new Custom());
                    System.out.println((Object) new Value(7));
                    System.out.println((Object) null);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/print-application-object").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("unrelated\ncustomValue[n=7]\nnull\n");
    }

    @Test
    void propagatesToStringFailureWithoutPrinting() throws Exception {
        final Path project = project("print-object-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Broken { public String toString() { throw new IllegalStateException("failed"); } }
                public static void main(String[] args) {
                    try { System.out.println((Object) new Broken()); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/print-object-exception").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failed\n");
    }
    @Test
    void printsDefaultAndNullResultsToEitherStream() throws Exception {
        final Path project = project("print-object-streams");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Identity { public int hashCode() { return 42; } }
                static final class Empty { public String toString() { return null; } }
                public static void main(String[] args) {
                    System.out.println((Object) new Identity());
                    System.out.println((Object) new Empty());
                    try { System.err.print((Object) new Empty()); }
                    catch (NullPointerException expected) { System.err.print("caught:"); }
                    System.err.println((Object) new Identity());
                }
            }
            """);
        final Path classes = project.resolve("jvm-classes");
        Files.createDirectories(classes);
        final ProcessResult javac = process(project, List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString(),
            project.resolve("src/main/java/com/acme/Main.java").toString()));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            classes.toString(), "com.acme.Main"));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/print-object-streams").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected.stdout());
        assertThat(result.stderr()).isEqualTo(expected.stderr());
    }

    @Test
    void keepsPrintedObjectStringsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("print-object-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value { public String toString() { return "value=" + 123; } }
                static final class Worker implements Runnable {
                    public void run() { for (int n = 0; n < 100; n++) System.out.println((Object) new Value()); }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/print-object-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void rejectsANullStreamBeforeConvertingItsArgument() throws Exception {
        final Path project = project("print-null-stream");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static final class Value {
                    public String toString() { System.out.println("converted"); return "value"; }
                }
                public static void main(String[] args) {
                    PrintStream stream = null;
                    try { stream.println((Object) new Value()); }
                    catch (NullPointerException expected) { System.out.println("caught"); }
                    try { stream.print((Object) new Value()); }
                    catch (NullPointerException expected) { System.out.println("caught"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/print-null-stream").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught\ncaught\n");
    }

    @Test
    void printsThroughAStreamPassedAsAnArgument() throws Exception {
        final Path project = project("print-object-stream-argument");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static final class Value { public String toString() { return "value"; } }
                static void write(PrintStream stream, Object value) {
                    stream.print(value); stream.println(value);
                }
                public static void main(String[] args) { write(System.out, new Value()); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/print-object-stream-argument").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("valuevalue\n");
    }

}
