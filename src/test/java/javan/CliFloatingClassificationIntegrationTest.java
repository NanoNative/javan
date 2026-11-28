package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliFloatingClassificationIntegrationTest extends CliIntegrationSupport {
    @Test
    void classifiesFloatAndDoubleNanInfinityAndFiniteValues() throws Exception {
        final Path project = project("floating-classification");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int[] floats = {0, 0x80000000, 1, 0x7f7fffff, 0x7f800000,
                        0xff800000, 0x7fc00001, 0xffc00001, 0x7f800001};
                    for (int bits : floats) {
                        float value = Float.intBitsToFloat(bits);
                        System.out.println(Float.isNaN(value));
                        System.out.println(Float.isInfinite(value));
                        System.out.println(Float.isFinite(value));
                    }
                    long[] doubles = {0L, 0x8000000000000000L, 1L, 0x7fefffffffffffffL,
                        0x7ff0000000000000L, 0xfff0000000000000L, 0x7ff8000000000001L,
                        0xfff8000000000001L, 0x7ff0000000000001L};
                    for (long bits : doubles) {
                        double value = Double.longBitsToDouble(bits);
                        System.out.println(Double.isNaN(value));
                        System.out.println(Double.isInfinite(value));
                        System.out.println(Double.isFinite(value));
                    }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-classification").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
    @Test
    void evaluatesTheClassificationArgumentOnce() throws Exception {
        final Path project = project("floating-classification-evaluation");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int count;
                static float nextFloat() { count++; return Float.NaN; }
                static double nextDouble() { count++; return Double.POSITIVE_INFINITY; }
                public static void main(String[] args) {
                    System.out.println(Float.isNaN(nextFloat()));
                    System.out.println(Double.isInfinite(nextDouble()));
                    System.out.println(count);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-classification-evaluation").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n2\n");
    }

}
