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
final class CliMultiArrayIntegrationTest extends CliIntegrationSupport {
    @Test
    void allocatesPrimitiveReferenceAndPartiallySpecifiedMultidimensionalArrays() throws Exception {
        final Path project = project("multidimensional-arrays");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int[][] numbers = new int[2][3];
                    numbers[1][2] = 7;
                    String[][] names = new String[2][3];
                    names[0][1] = "item";
                    long[][][] partial = new long[2][1][];
                    System.out.println(numbers.length + ":" + numbers[0].length + ":" + numbers[1][2]);
                    System.out.println(names.length + ":" + names[0].length + ":" + names[0][1]);
                    System.out.println(partial.length + ":" + partial[1].length + ":" + (partial[0][0] == null));
                    System.out.println(numbers.getClass().getName());
                    System.out.println(names[0].getClass().getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-arrays").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
    @Test
    void allocatesEveryPrimitiveLeafTypeAndPreservesEvaluationOrder() throws Exception {
        final Path project = project("multidimensional-primitive-types");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int trace;
                static int size(int value) { trace = trace * 10 + value; return value; }
                public static void main(String[] args) {
                    boolean[][] z = new boolean[1][2]; byte[][] b = new byte[1][2];
                    short[][] s = new short[1][2]; char[][] c = new char[1][2];
                    int[][] i = new int[size(1)][size(2)]; long[][] j = new long[1][2];
                    float[][] f = new float[1][2]; double[][] d = new double[1][2];
                    z[0][1] = true; b[0][1] = 3; s[0][1] = 4; c[0][1] = 'Q';
                    i[0][1] = 5; j[0][1] = 6L; f[0][1] = 1.25f; d[0][1] = 2.5;
                    System.out.println(z[0][1]); System.out.println(b[0][1]); System.out.println(s[0][1]);
                    System.out.println(c[0][1]); System.out.println(i[0][1]); System.out.println(j[0][1]);
                    System.out.println(f[0][1]); System.out.println(d[0][1]); System.out.println(trace);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-primitive-types").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void rejectsANegativeInnerDimensionEvenWhenTheOuterDimensionIsZero() throws Exception {
        final Path project = project("multidimensional-negative-inner");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int negative = -2;
                    try { int[][] value = new int[0][negative]; }
                    catch (NegativeArraySizeException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-negative-inner").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:-2\n");
    }

    @Test
    void keepsMultidimensionalArraysAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("multidimensional-arrays-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Worker implements Runnable {
                    int total;
                    public void run() {
                        for (int n = 0; n < 200; n++) {
                            int[][][] values = new int[2][3][4];
                            values[1][2][3] = n;
                            total += values[1][2][3] + values[0][1].length;
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
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-arrays-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void evaluatesEveryDimensionBeforeThrowingForANegativeSize() throws Exception {
        final Path project = project("multidimensional-negative-evaluation");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int trace;
                static int size(int id, int value) { trace = trace * 10 + id; return value; }
                public static void main(String[] args) {
                    try { int[][] value = new int[size(1, -1)][size(2, -2)]; }
                    catch (NegativeArraySizeException expected) { System.out.println("caught:" + expected.getMessage()); }
                    System.out.println("trace:" + trace);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-negative-evaluation").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void transportsAnExceptionFromADimensionExpression() throws Exception {
        final Path project = project("multidimensional-dimension-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int size() { throw new IllegalStateException("failure"); }
                public static void main(String[] args) {
                    try { int[][] value = new int[2][size()]; }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-dimension-exception").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failure\n");
    }

    @Test
    void rejectsAClassFileWithZeroAllocatedDimensionsRepeatably() throws Exception {
        final Path project = project("multidimensional-invalid-count");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) { int[][] value = new int[2][3]; System.out.println(value.length); }
            }
            """);
        runJvm(project, "com.acme.Main");
        final Path classes = project.resolve("jvm-classes");
        final Path classFile = classes.resolve("com/acme/Main.class");
        final byte[] bytes = Files.readAllBytes(classFile);
        int matches = 0;
        for (int index = 0; index + 3 < bytes.length; index++) {
            if ((bytes[index] & 0xff) == 197 && (bytes[index + 3] & 0xff) == 2) {
                bytes[index + 3] = 0;
                matches++;
            }
        }
        assertThat(matches).isEqualTo(1);
        Files.write(classFile, bytes);
        final CliRun first = run(tempDir, "check", classes.toString(), "--main", "com.acme.Main");
        final CliRun second = run(tempDir, "check", classes.toString(), "--main", "com.acme.Main");
        assertThat(first.exitCode()).isNotZero().isEqualTo(second.exitCode());
        assertThat(first.stderr()).contains("JAVAN032", "invalid reachable control flow").isEqualTo(second.stderr());
    }

    @Test
    void allocatesTheMaximumClassFileArrayRank() throws Exception {
        final Path project = project("multidimensional-maximum-rank");
        final String dimensions = "[1]".repeat(255);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    Object value = new int%s;
                    System.out.println(value.getClass().getName().length());
                }
            }
            """.formatted(dimensions));
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = runWithTimeout(tempDir, Duration.ofSeconds(60), "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/multidimensional-maximum-rank").toString()), Duration.ofSeconds(60));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("256\n");
    }

}
