package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliBoxedStringConcatIntegrationTest extends CliIntegrationSupport {
    @Test
    void convertsBoxedPrimitiveOperandsAndNullDuringStringConcatenation() throws Exception {
        final Path project = project("boxed-string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    Byte b = Byte.valueOf((byte) -7);
                    Short s = Short.valueOf((short) 1234);
                    Integer i = Integer.valueOf(7);
                    Long l = Long.valueOf(123456789L);
                    Float f = Float.valueOf(1.25f);
                    Double d = Double.valueOf(-2.5);
                    Boolean z = Boolean.TRUE;
                    Character c = Character.valueOf('Q');
                    Integer absent = null;
                    System.out.println("byte:" + b);
                    System.out.println("short:" + s);
                    System.out.println("integer:" + i);
                    System.out.println("long:" + l);
                    System.out.println("float:" + f);
                    System.out.println("double:" + d);
                    System.out.println("boolean:" + z);
                    System.out.println("character:" + c);
                    System.out.println("null:" + absent);
                    System.out.println("minimum:" + Integer.valueOf(Integer.MIN_VALUE));
                    System.out.println("negative-zero:" + Double.valueOf(-0.0d));
                    System.out.println("infinity:" + Float.valueOf(Float.POSITIVE_INFINITY));
                    System.out.println("not-a-number:" + Double.valueOf(Double.NaN));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-string-concat").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void keepsBoxedConcatOperandsAndResultsAliveDuringCollection() throws Exception {
        final Path project = project("boxed-string-concat-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int total = 0;
                    for (int n = 0; n < 1000; n++) {
                        Integer value = Integer.valueOf(n);
                        String result = "number:" + value;
                        total += result.length();
                    }
                    System.out.println(total);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-string-concat-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void keepsBoxedConcatResultsAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("boxed-string-concat-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Worker implements Runnable {
                    int total;
                    public void run() {
                        for (int n = 0; n < 500; n++) {
                            Integer value = Integer.valueOf(n);
                            Long other = Long.valueOf(n + 1L);
                            String result = "number:" + value + "/" + other;
                            total += result.length();
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-string-concat-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
}
