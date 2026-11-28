package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.OutputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliSmallIntegralConstantIntegrationTest extends CliIntegrationSupport {
    @Test
    void narrowsIntegerConstantValuesToTheirDeclaredFieldTypes() throws Exception {
        final ClassDesc type = ClassDesc.of("dep.Constants");
        final ClassDesc reader = ClassDesc.of("dep.Reader");
        final ClassDesc integer = ClassDesc.ofDescriptor("I");
        final ClassDesc b = ClassDesc.ofDescriptor("B"), s = ClassDesc.ofDescriptor("S");
        final ClassDesc c = ClassDesc.ofDescriptor("C"), z = ClassDesc.ofDescriptor("Z");
        final int fieldFlags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL;
        final int methodFlags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        final byte[] bytes = ClassFile.of().build(type, builder -> builder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("B", b, f -> f.withFlags(fieldFlags).with(ConstantValueAttribute.of(384)))
            .withField("S", s, f -> f.withFlags(fieldFlags).with(ConstantValueAttribute.of(98304)))
            .withField("C", c, f -> f.withFlags(fieldFlags).with(ConstantValueAttribute.of(-1)))
            .withField("Z", z, f -> f.withFlags(fieldFlags).with(ConstantValueAttribute.of(2)))
            .withField("Z1", z, f -> f.withFlags(fieldFlags).with(ConstantValueAttribute.of(3))));
        final byte[] readerBytes = ClassFile.of().build(reader, builder -> builder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody("b", MethodTypeDesc.of(integer), methodFlags, code -> code.getstatic(type, "B", b).ireturn())
            .withMethodBody("s", MethodTypeDesc.of(integer), methodFlags, code -> code.getstatic(type, "S", s).ireturn())
            .withMethodBody("c", MethodTypeDesc.of(integer), methodFlags, code -> code.getstatic(type, "C", c).ireturn())
            .withMethodBody("z", MethodTypeDesc.of(integer), methodFlags, code -> code.getstatic(type, "Z", z).ireturn())
            .withMethodBody("z1", MethodTypeDesc.of(integer), methodFlags, code -> code.getstatic(type, "Z1", z).ireturn()));
        final Path dependency = tempDir.resolve("small-constants.jar");
        try (OutputStream stream = Files.newOutputStream(dependency); JarOutputStream archive = new JarOutputStream(stream)) {
            archive.putNextEntry(new JarEntry("dep/Constants.class"));
            archive.write(bytes);
            archive.closeEntry();
            archive.putNextEntry(new JarEntry("dep/Reader.class"));
            archive.write(readerBytes);
            archive.closeEntry();
        }
        final Path project = project("small-integral-constants");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(dep.Reader.b()); System.out.println(dep.Reader.s());
                    System.out.println(dep.Reader.c()); System.out.println(dep.Reader.z()); System.out.println(dep.Reader.z1());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun build = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/small-integral-constants").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-128\n-32768\n65535\n0\n1\n");
    }
}
