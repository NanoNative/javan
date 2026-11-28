package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliBoxedFloatingClassificationIntegrationTest extends CliIntegrationSupport {
    @Test
    void classifiesBoxedFloatAndDoubleValues() throws Exception {
        final Path project = project("boxed-float-classify");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    float[] floats = {0f, -0f, 1f, Float.intBitsToFloat(1), Float.NaN, Float.intBitsToFloat(0x7fc01234), Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
                    for (float value : floats) {
                        Float box = Float.valueOf(value);
                        System.out.println(box.isNaN()); System.out.println(box.isInfinite());
                    }
                    double[] doubles = {0d, -0d, 1d, Double.longBitsToDouble(1), Double.NaN, Double.longBitsToDouble(0x7ff8000000001234L), Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
                    for (double value : doubles) {
                        Double box = Double.valueOf(value);
                        System.out.println(box.isNaN()); System.out.println(box.isInfinite());
                    }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-float-classify").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void evaluatesEachReceiverOnceAndTransportsNullReceiverFailures() throws Exception {
        final Path project = project("boxed-float-classify-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int count;
                static Float next() { count++; return Float.valueOf(Float.NaN); }
                static Double missing() { count++; return null; }
                public static void main(String[] args) {
                    System.out.println(next().isNaN()); System.out.println(count);
                    Float f = null; Double d = null;
                    try { f.isNaN(); } catch (NullPointerException expected) { System.out.println("float-nan"); }
                    try { f.isInfinite(); } catch (NullPointerException expected) { System.out.println("float-infinite"); }
                    try { d.isNaN(); } catch (NullPointerException expected) { System.out.println("double-nan"); }
                    try { d.isInfinite(); } catch (NullPointerException expected) { System.out.println("double-infinite"); }
                    try { missing().isNaN(); } catch (NullPointerException expected) { System.out.println(count); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-float-classify-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n1\nfloat-nan\nfloat-infinite\ndouble-nan\ndouble-infinite\n2\n");
    }
}
