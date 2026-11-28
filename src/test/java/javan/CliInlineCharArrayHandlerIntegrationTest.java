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
final class CliInlineCharArrayHandlerIntegrationTest extends CliIntegrationSupport {
    @Test
    void initializesACharArrayInsideAProtectedPrintCall() throws Exception {
        final Path project = project("inline-char-array-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static void print(PrintStream stream) {
                    try { stream.print(new char[] {'x', 'y'}); }
                    catch (NullPointerException expected) { System.out.println("caught"); }
                }
                public static void main(String[] args) { print(null); print(System.out); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/inline-char-array-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught\nxy");
    }
    @Test
    void rejectsAnUnprovenArrayStoreInAProtectedRangeRepeatably() throws Exception {
        final Path project = project("unproven-char-array-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    try { char[] chars = new char[1]; chars[2] = 'x'; System.out.print(chars); }
                    catch (ArrayIndexOutOfBoundsException expected) { System.out.println("caught"); }
                }
            }
            """);
        assertThat(runJvm(project, "com.acme.Main")).isEqualTo("caught\n");
        final CliRun first = run(tempDir, "check", project.toString());
        final CliRun second = run(tempDir, "check", project.toString());
        assertThat(first.exitCode()).isNotZero().isEqualTo(second.exitCode());
        assertThat(first.stderr()).contains("JAVAN014").isEqualTo(second.stderr());
    }

    @Test
    void retainsInlineCharacterArraysDuringConcurrentCollection() throws Exception {
        final Path project = project("inline-char-array-handler-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static final class Worker implements Runnable {
                    public void run() {
                        PrintStream stream = System.out;
                        for (int i = 0; i < 100; i++) {
                            try { stream.print(new char[] {'x'}); }
                            catch (NullPointerException expected) { System.out.print("bad"); }
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread one = new Thread(new Worker()); Thread two = new Thread(new Worker());
                    one.start(); two.start(); one.join(); two.join();
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/inline-char-array-handler-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void rejectsAnOutOfBoundsStoreInAChangedClassFile() throws Exception {
        final Path project = project("inline-char-array-invalid-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    try { System.out.print(new char[] {'x'}); }
                    catch (ArrayIndexOutOfBoundsException expected) { System.out.println("caught"); }
                }
            }
            """);
        assertThat(runJvm(project, "com.acme.Main")).isEqualTo("x");
        final Path classes = project.resolve("jvm-classes");
        final Path classFile = classes.resolve("com/acme/Main.class");
        final byte[] bytes = Files.readAllBytes(classFile);
        int matches = 0;
        for (int index = 0; index + 6 < bytes.length; index++) {
            if ((bytes[index] & 0xff) == 188 && (bytes[index + 1] & 0xff) == 5
                && (bytes[index + 2] & 0xff) == 89 && (bytes[index + 3] & 0xff) == 3
                && (bytes[index + 4] & 0xff) == 16 && (bytes[index + 5] & 0xff) == 120
                && (bytes[index + 6] & 0xff) == 85) {
                bytes[index + 3] = 5;
                matches++;
            }
        }
        assertThat(matches).isEqualTo(1);
        Files.write(classFile, bytes);
        final ProcessResult expected = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            classes.toString(), "com.acme.Main"));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        assertThat(expected.stdout()).isEqualTo("caught\n");
        final CliRun first = run(tempDir, "check", classes.toString(), "--main", "com.acme.Main");
        final CliRun second = run(tempDir, "check", classes.toString(), "--main", "com.acme.Main");
        assertThat(first.exitCode()).isNotZero().isEqualTo(second.exitCode());
        assertThat(first.stderr()).contains("JAVAN014").isEqualTo(second.stderr());
    }

    @Test
    void initializesAProtectedCharArrayFromLocalValues() throws Exception {
        final Path project = project("inline-char-array-local-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static void print(PrintStream stream, char value) {
                    try { stream.print(new char[] {value, '!' }); }
                    catch (NullPointerException expected) { System.out.println("caught"); }
                }
                static void extended(PrintStream stream, int a, int b, int c, char value) {
                    try { stream.print(new char[] {value}); }
                    catch (NullPointerException expected) { System.out.println("caught"); }
                }
                public static void main(String[] args) {
                    print(null, 'q'); print(System.out, 'q'); extended(System.out, 0, 0, 0, 'r');
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/inline-char-array-local-values").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught\nq!r");
    }

}
