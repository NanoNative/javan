package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliBoxedPrimitiveNullUnboxingIntegrationTest extends CliIntegrationSupport {
    @Test
    void transportsNullFailuresForEachBoxedPrimitiveValueMethod() throws Exception {
        final Path project = project("boxed-primitive-null-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int count;
                static Integer missing() { count++; return null; }
                public static void main(String[] args) {
                    Boolean z = null; Byte b = null; Short s = null; Character c = null;
                    Integer i = null; Long l = null; Float f = null; Double d = null;
                    try { z.booleanValue(); } catch (NullPointerException e) { System.out.println("boolean"); }
                    try { b.byteValue(); } catch (NullPointerException e) { System.out.println("byte"); }
                    try { s.shortValue(); } catch (NullPointerException e) { System.out.println("short"); }
                    try { c.charValue(); } catch (NullPointerException e) { System.out.println("char"); }
                    try { i.intValue(); } catch (NullPointerException e) { System.out.println("int"); }
                    try { l.longValue(); } catch (NullPointerException e) { System.out.println("long"); }
                    try { f.floatValue(); } catch (NullPointerException e) { System.out.println("float"); }
                    try { d.doubleValue(); } catch (NullPointerException e) { System.out.println("double"); }
                    try { int value = missing(); } catch (NullPointerException e) { System.out.println(count); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-primitive-null-unbox").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("boolean\nbyte\nshort\nchar\nint\nlong\nfloat\ndouble\n1\n");
    }
    @Test
    void unboxesNonNullValuesAndEvaluatesReceiversOnce() throws Exception {
        final Path project = project("boxed-primitive-unbox-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int count;
                static Integer next() { count++; return Integer.valueOf(17); }
                public static void main(String[] args) {
                    Boolean z = Boolean.valueOf(true); Byte b = Byte.valueOf((byte) -12);
                    Short s = Short.valueOf((short) 32000); Character c = Character.valueOf('A');
                    Long l = Long.valueOf(9000000000L); Float f = Float.valueOf(2.5f); Double d = Double.valueOf(-4.5d);
                    System.out.println(z.booleanValue()); System.out.println((int) b.byteValue());
                    System.out.println((int) s.shortValue()); System.out.println((int) c.charValue());
                    System.out.println(next().intValue()); System.out.println(count); System.out.println(l.longValue());
                    System.out.println(Float.floatToRawIntBits(f.floatValue()));
                    System.out.println(Double.doubleToRawLongBits(d.doubleValue()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/boxed-primitive-unbox-values").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

}
