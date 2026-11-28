package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliRecordStringIntegrationTest extends CliIntegrationSupport {
    @Test
    void concatenatesARecordUsingItsGeneratedToString() throws Exception {
        final Path project = project("record-string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Value(String name, int count) { }
                public static void main(String[] args) {
                    Object value = new Value("item", 7);
                    System.out.println("value:" + value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-concat").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value:Value[name=item, count=7]\n");
    }
    @Test
    void convertsPrimitiveFieldsNullStringsAndEmptyRecords() throws Exception {
        final Path project = project("record-string-primitives");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Values(boolean z, byte b, short s, char c, int i, long j, float f, double d, String text) { }
                record Empty() { }
                public static void main(String[] args) {
                    System.out.println(new Values(true, (byte) -3, (short) 400, 'Q', -7, 123456789L, 1.25f, -0.0, null).toString());
                    System.out.println(new Empty().toString());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-primitives").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void preservesDollarSignsInTopLevelNestedAndLocalRecordNames() throws Exception {
        final Path project = project("record-string-class-names");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            record Top$Value(int x) { }
            public final class Main {
                record Nested$Value(int x) { }
                public static void main(String[] args) {
                    record Local$Value(int x) { }
                    System.out.println(new Top$Value(1).toString());
                    System.out.println(new Nested$Value(2).toString());
                    System.out.println(new Local$Value(3).toString());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-class-names").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("Top$Value[x=1]\nNested$Value[x=2]\nLocal$Value[x=3]\n");
    }

    @Test
    void rejectsRecordObjectComponentsUntilTheirStringConversionIsSupported() throws Exception {
        final Path project = project("record-string-object-component");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Value(Object object) { }
                public static void main(String[] args) { System.out.println(new Value("value").toString()); }
            }
            """);
        final CliRun first = run(tempDir, "check", project.toString());
        final CliRun second = run(tempDir, "check", project.toString());
        assertThat(first.exitCode()).isNotZero().isEqualTo(second.exitCode());
        assertThat(first.stderr()).contains("JAVAN030").isEqualTo(second.stderr());
    }

    @Test
    void keepsRecordStringsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("record-string-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                record Value(String name, int number) { }
                static final class Worker implements Runnable {
                    int total;
                    public void run() {
                        for (int n = 0; n < 300; n++) {
                            Object value = new Value("item", n);
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-string-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

}
