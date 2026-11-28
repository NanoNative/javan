package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.MTD_void;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliFloatingConstantIntegrationTest extends CliIntegrationSupport {
    @Test
    void readsExactFloatingConstantBitsFromGetstatic() throws Exception {
        final int[] floatBits = {0x80000000, 0x00000001, 0xc0400000, 0x7f7fffff,
            0x7f800000, 0xff800000, 0xffc12345, 0x7f812345};
        final long[] doubleBits = {0x8000000000000000L, 0x0000000000000001L, 0xbfe0000000000000L, 0x7fefffffffffffffL,
            0x7ff0000000000000L, 0xfff0000000000000L, 0xfff8123456789abcL, 0x7ff0123456789abcL};
        final ClassDesc type = ClassDesc.of("dep.Constants");
        final int fieldFlags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL;
        final int methodFlags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        final byte[] bytes = ClassFile.of().build(type, builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER);
            for (int index = 0; index < floatBits.length; index++) {
                final String field = "f" + index;
                final float marker = 200.25f + index;
                builder.withField(field, CD_float, definition -> definition.withFlags(fieldFlags)
                    .with(ConstantValueAttribute.of(marker)));
                builder.withMethodBody(field, MethodTypeDesc.of(CD_float), methodFlags,
                    code -> code.getstatic(type, field, CD_float).freturn());
            }
            for (int index = 0; index < doubleBits.length; index++) {
                final String field = "d" + index;
                final double marker = 400.5d + index;
                builder.withField(field, CD_double, definition -> definition.withFlags(fieldFlags)
                    .with(ConstantValueAttribute.of(marker)));
                builder.withMethodBody(field, MethodTypeDesc.of(CD_double), methodFlags,
                    code -> code.getstatic(type, field, CD_double).dreturn());
            }
            builder.withField("beforeClinit", CD_float, methodFlags)
                .withField("mutable", CD_float, definition -> definition.withFlags(methodFlags)
                    .with(ConstantValueAttribute.of(2.5f)))
                .withField("instance", CD_double, definition -> definition.withFlags(ClassFile.ACC_PUBLIC)
                    .with(ConstantValueAttribute.of(6.25d)))
                .withMethodBody("<init>", MTD_void, ClassFile.ACC_PUBLIC,
                    code -> code.aload(0).invokespecial(CD_Object, "<init>", MTD_void).return_())
                .withMethodBody("<clinit>", MTD_void, ClassFile.ACC_STATIC,
                    code -> code.getstatic(type, "f0", CD_float).putstatic(type, "beforeClinit", CD_float).return_());
        });
        // The JDK classfile builder canonicalizes NaNs, so patch the unique constant pool markers.
        for (int index = 0; index < floatBits.length; index++) {
            replaceConstantBits(bytes, 4, Float.floatToRawIntBits(200.25f + index), floatBits[index], Integer.BYTES);
        }
        for (int index = 0; index < doubleBits.length; index++) {
            replaceConstantBits(bytes, 6, Double.doubleToRawLongBits(400.5d + index), doubleBits[index], Long.BYTES);
        }
        final Path dependency = tempDir.resolve("floating-constants.jar");
        try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(dependency))) {
            archive.putNextEntry(new JarEntry("dep/Constants.class"));
            archive.write(bytes);
            archive.closeEntry();
        }
        final StringBuilder observations = new StringBuilder();
        final StringBuilder expectedBits = new StringBuilder();
        for (int index = 0; index < floatBits.length; index++) {
            observations.append("System.out.println(Float.floatToRawIntBits(dep.Constants.f").append(index).append("()));\n");
            expectedBits.append(floatBits[index]).append('\n');
        }
        for (int index = 0; index < doubleBits.length; index++) {
            observations.append("System.out.println(Double.doubleToRawLongBits(dep.Constants.d").append(index).append("()));\n");
            expectedBits.append(doubleBits[index]).append('\n');
        }
        final Path project = project("floating-constant-getstatic");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    %s
                    System.out.println(Float.floatToRawIntBits(dep.Constants.beforeClinit));
                    System.out.println(dep.Constants.mutable);
                    dep.Constants.mutable = 8.0f;
                    System.out.println(dep.Constants.mutable);
                    System.out.println(Double.doubleToRawLongBits(new dep.Constants().instance));
                    System.out.println(dep.Constants.class.getDeclaredField("d0").getType().getName());
                }
            }
            """.formatted(observations));
        final String expected = runJvm(project, "com.acme.Main", List.of(dependency));
        assertThat(expected).isEqualTo(expectedBits.append(Integer.MIN_VALUE).append("\n2.5\n8.0\n0\ndouble\n").toString());
        final CliRun build = run(tempDir, "build", project.toString(), "--classpath", dependency.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/floating-constant-getstatic").toString()),
            defaultProcessTimeout(), Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    private static void replaceConstantBits(final byte[] bytes, final int tag, final long marker, final long bits, final int width) {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int matches = 0;
        for (int index = 0; index + width < bytes.length; index++) {
            if (buffer.get(index) == tag
                && (width == Integer.BYTES ? buffer.getInt(index + 1) : buffer.getLong(index + 1)) == marker) {
                if (width == Integer.BYTES) {
                    buffer.putInt(index + 1, (int) bits);
                } else {
                    buffer.putLong(index + 1, bits);
                }
                matches++;
            }
        }
        assertThat(matches).as("constant pool marker %s", marker).isEqualTo(1);
    }
}
