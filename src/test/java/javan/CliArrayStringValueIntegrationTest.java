package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliArrayStringValueIntegrationTest extends CliIntegrationSupport {
    @Test
    void convertsArrayValuesUsingTheirClassNameAndIdentityHash() throws Exception {
        final Path project = project("array-string-value");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Item { }
                static void show(Object value) {
                    String expected = value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
                    System.out.println(String.valueOf(value).equals(expected));
                }
                public static void main(String[] args) {
                    show(new boolean[1]); show(new byte[1]); show(new short[1]); show(new char[1]);
                    show(new int[1]); show(new long[1]); show(new float[1]); show(new double[1]);
                    show(new Object[1]); show(new String[1][]); show(new Item[1]);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/array-string-value").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n".repeat(11));
    }

    @Test
    void keepsArrayStringValuesAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("array-string-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Worker implements Runnable {
                    int matches;
                    public void run() {
                        for (int n = 0; n < 300; n++) {
                            Object value = new int[1];
                            String text = String.valueOf(value);
                            String expected = value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
                            if (text.equals(expected)) matches++;
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/array-string-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("600\n");
    }
    @Test
    void convertsClonedAndCopiedArraysWithTheirOwnIdentity() throws Exception {
        final Path project = project("array-string-copy");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Arrays;
            public final class Main {
                static void show(Object value) {
                    String expected = value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
                    System.out.println(String.valueOf(value).equals(expected));
                }
                public static void main(String[] args) {
                    int[] numbers = { 1, 2 };
                    String[] strings = { "a" };
                    show(numbers.clone());
                    show(Arrays.copyOf(strings, 2));
                    System.out.println(String.valueOf((Object) null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/array-string-copy").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\nnull\n");
    }

}
