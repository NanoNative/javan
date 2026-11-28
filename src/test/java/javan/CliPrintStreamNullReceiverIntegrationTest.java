package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliPrintStreamNullReceiverIntegrationTest extends CliIntegrationSupport {
    @Test
    void evaluatesArgumentsAndRejectsNullReceiversForPrimitiveStringAndEmptyPrints() throws Exception {
        final Path project = project("printstream-null-overloads");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static int argument() { System.out.println("evaluated"); return 7; }
                static void string(PrintStream stream) {
                    try { stream.println("wrong"); } catch (NullPointerException expected) { System.out.println("string"); }
                }
                static void number(PrintStream stream) {
                    try { stream.print(argument()); } catch (NullPointerException expected) { System.out.println("int"); }
                }
                static void empty(PrintStream stream) {
                    try { stream.println(); } catch (NullPointerException expected) { System.out.println("empty"); }
                }
                static void chars(PrintStream stream) {
                    char[] value = {'x'};
                    try { stream.print(value); } catch (NullPointerException expected) { System.out.println("chars"); }
                }
                public static void main(String[] args) {
                    string(null); number(null); empty(null); chars(null);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/printstream-null-overloads").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("string\nevaluated\nint\nempty\nchars\n");
    }
    @Test
    void printsEverySupportedOverloadThroughAPassedStream() throws Exception {
        final Path project = project("printstream-passed-overloads");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static void write(PrintStream stream) {
                    char[] text = {'o', 'k'};
                    stream.print("s"); stream.print(true); stream.print('C'); stream.print(1);
                    stream.print(2L); stream.print(3.0f); stream.print(4.0); stream.print(text); stream.println();
                    stream.println("end"); stream.println(false); stream.println('Z'); stream.println(5);
                    stream.println(6L); stream.println(7.0f); stream.println(8.0); stream.println(text);
                }
                public static void main(String[] args) { write(System.out); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/printstream-passed-overloads").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void evaluatesAReceiverAndArgumentBeforeRejectingANullReceiver() throws Exception {
        final Path project = project("printstream-null-evaluation");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.io.PrintStream;
            public final class Main {
                static PrintStream receiver() { System.out.println("receiver"); return null; }
                static int argument() { System.out.println("argument"); return 7; }
                public static void main(String[] args) {
                    try { receiver().print(argument()); }
                    catch (NullPointerException expected) { System.out.println("caught"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/printstream-null-evaluation").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("receiver\nargument\ncaught\n");
    }

}
