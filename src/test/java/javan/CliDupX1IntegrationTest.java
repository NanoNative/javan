package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.OutputStream;
import java.lang.classfile.ClassFile;
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
final class CliDupX1IntegrationTest extends CliIntegrationSupport {
    @Test
    void operandLocalIsSnapshottedBeforeStore() throws Exception {
        final ClassDesc type = ClassDesc.of("dep.Snapshot");
        final ClassDesc integer = ClassDesc.ofDescriptor("I");
        final byte[] bytes = ClassFile.of().build(type, builder -> builder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withMethodBody("evaluate", MethodTypeDesc.of(integer, integer),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.iload(0).iconst_2().istore(0).iconst_3().dup_x1().pop().isub().ireturn()));
        final Path dependency = tempDir.resolve("snapshot.jar");
        try (OutputStream stream = Files.newOutputStream(dependency); JarOutputStream archive = new JarOutputStream(stream)) {
            archive.putNextEntry(new JarEntry("dep/Snapshot.class"));
            archive.write(bytes);
            archive.closeEntry();
        }
        final Path project = project("dup-x1-local-snapshot");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) { System.out.println(dep.Snapshot.evaluate(1)); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun build = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/dup-x1-local-snapshot").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void fieldAssignmentPreservesReferenceAndEvaluationOrder() throws Exception {
        final Path project = project("dup-x1-reference-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int order;
                static Main target;
                Object value;
                static Main receiver() { order = order * 10 + 1; return target; }
                static Object value() { order = order * 10 + 2; return new Main(); }
                public static void main(String[] args) {
                    target = new Main();
                    Object result = (receiver().value = value());
                    System.out.println(result == target.value);
                    System.out.println(order);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/dup-x1-reference-field").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void fieldFloatAssignmentPreservesExpressionValue() throws Exception {
        final Path project = project("dup-x1-float-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                float value;
                public static void main(String[] args) {
                    Main target = new Main();
                    float result = (target.value = 1.5f);
                    System.out.println(result);
                    System.out.println(target.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/dup-x1-float-field").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void branchSelectedReceiverIsEvaluatedOnce() throws Exception {
        final Path project = project("dup-x1-branch-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int calls;
                static Main target;
                int value = 7;
                static Main receiver() { calls = calls + 1; return target; }
                public static void main(String[] args) {
                    target = new Main();
                    System.out.println((args.length == 0 ? receiver() : receiver()).value++);
                    System.out.println(target.value);
                    System.out.println(calls);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/dup-x1-branch-receiver").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void fieldPostIncrementEvaluatesReceiverOnce() throws Exception {
        final Path project = project("dup-x1-field-post-increment");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int calls;
                int value = 7;
                static Main target;
                static Main receiver() { calls = calls + 1; return target; }
                public static void main(String[] args) {
                    target = new Main();
                    System.out.println(receiver().value++);
                    System.out.println(target.value);
                    System.out.println(calls);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/dup-x1-field-post-increment").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
}
