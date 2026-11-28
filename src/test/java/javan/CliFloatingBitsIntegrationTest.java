package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliFloatingBitsIntegrationTest extends CliIntegrationSupport {
    @Test
    void preservesRawDoubleBitsAndCanonicalizesNanBits() throws Exception {
        final Path project = project("double-bit-conversions");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    long[] bits = {0L, 0x8000000000000000L, 1L, 0x8000000000000001L,
                        0x7fefffffffffffffL, 0x7ff0000000000000L, 0xfff0000000000000L,
                        0x7ff8000000000001L, 0xfff8000000000001L, 0x7ff0000000000001L};
                    for (long original : bits) {
                        double value = Double.longBitsToDouble(original);
                        System.out.println(Double.doubleToRawLongBits(value));
                        System.out.println(Double.doubleToLongBits(value));
                    }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/double-bit-conversions").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void canonicalizesFloatNanBitsAndPreservesOtherBits() throws Exception {
        final Path project = project("float-canonical-bits");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int[] bits = {0, 0x80000000, 1, 0x80000001, 0x7f7fffff,
                        0x7f800000, 0xff800000, 0x7fc00001, 0xffc00001, 0x7f800001};
                    for (int original : bits) {
                        float value = Float.intBitsToFloat(original);
                        System.out.println(Float.floatToRawIntBits(value));
                        System.out.println(Float.floatToIntBits(value));
                    }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/float-canonical-bits").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
    @Test
    void convertsDeterministicFloatingBitPatterns() throws Exception {
        final Path project = project("floating-bits-patterns");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    long state = 0x123456789abcdef0L;
                    for (int i = 0; i < 1024; i++) {
                        state ^= state << 13; state ^= state >>> 7; state ^= state << 17;
                        double d = Double.longBitsToDouble(state);
                        float f = Float.intBitsToFloat((int) state);
                        System.out.println(Double.doubleToRawLongBits(d));
                        System.out.println(Double.doubleToLongBits(d));
                        System.out.println(Float.floatToIntBits(f));
                    }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-bits-patterns").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

}
