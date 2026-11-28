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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliLongConstantIntegrationTest extends CliIntegrationSupport {
    @Test
    void readsLongConstantValuesFromExplicitGetstaticInstructions() throws Exception {
        final ClassDesc type = ClassDesc.of("dep.Constants");
        final ClassDesc longType = ClassDesc.ofDescriptor("J");
        final int fieldFlags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL;
        final int methodFlags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        final byte[] bytes = ClassFile.of().build(type, builder -> builder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("MIN", longType, field -> field.withFlags(fieldFlags).with(ConstantValueAttribute.of(Long.MIN_VALUE)))
            .withField("BIG", longType, field -> field.withFlags(fieldFlags).with(ConstantValueAttribute.of(5000000000L)))
            .withMethodBody("minimum", MethodTypeDesc.of(longType), methodFlags,
                code -> code.getstatic(type, "MIN", longType).lreturn())
            .withMethodBody("large", MethodTypeDesc.of(longType), methodFlags,
                code -> code.getstatic(type, "BIG", longType).lreturn()));
        final Path dependency = tempDir.resolve("long-constants.jar");
        try (OutputStream stream = Files.newOutputStream(dependency); JarOutputStream archive = new JarOutputStream(stream)) {
            archive.putNextEntry(new JarEntry("dep/Constants.class"));
            archive.write(bytes);
            archive.closeEntry();
        }
        final Path project = project("long-constant-getstatic");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(dep.Constants.minimum());
                    System.out.println(dep.Constants.large());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun build = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/long-constant-getstatic").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-9223372036854775808\n5000000000\n");
    }
}
