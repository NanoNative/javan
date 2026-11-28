package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliRecordArrayStringIntegrationTest extends CliIntegrationSupport {
    @Test
    void formatsArrayComponentsUsingTheirIdentityStrings() throws Exception {
        final Path project = project("record-string-array-components");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Value(int[] numbers, String[] names, int[][] partial) { }
                public static void main(String[] args) {
                    int[] numbers = { 1, 2 }; String[] names = { "item" }; int[][] partial = new int[1][];
                    Value value = new Value(numbers, names, partial);
                    String expected = "Value[numbers=" + (Object) numbers + ", names=" + (Object) names + ", partial=" + (Object) partial + "]";
                    System.out.println(value.toString().equals(expected));
                    System.out.println(new Value(null, null, null).toString());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-array-components").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nValue[numbers=null, names=null, partial=null]\n");
    }

    @Test
    void keepsArrayComponentStringsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("record-string-array-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Value(int[] numbers) { }
                static final class Worker implements Runnable {
                    int matches;
                    public void run() {
                        for (int n = 0; n < 300; n++) {
                            int[] numbers = { n };
                            String expected = "Value[numbers=" + (Object) numbers + "]";
                            if (new Value(numbers).toString().equals(expected)) matches++;
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Worker a = new Worker(); Worker b = new Worker();
                    Thread first = new Thread(a); Thread second = new Thread(b);
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(a.matches + b.matches);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-array-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("600\n");
    }
}
