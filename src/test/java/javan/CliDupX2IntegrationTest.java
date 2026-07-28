package javan;

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
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliDupX2IntegrationTest extends CliIntegrationSupport {
    private static final ClassDesc FORM_ONE_CLASS = ClassDesc.of("dep.DupX2FormOne");
    private static final ClassDesc FORM_TWO_CLASS = ClassDesc.of("dep.DupX2FormTwo");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");
    private static final ClassDesc LONG = ClassDesc.ofDescriptor("J");

    @Test
    void threeCategoryOneValuesPreserveValueAndOperandOrder() throws Exception {
        final Path dependency = formOneDependency();
        final Path project = project("dup-x2-category-one");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormOne;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormOne.evaluate());
                    System.out.println(DupX2FormOne.order());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-category-one", List.of(dependency)))
            .isEqualTo(runJvm(project, "com.acme.Main", List.of(dependency)));
    }

    @Test
    void categoryOneAboveCategoryTwoPreservesValueAndOperandOrder() throws Exception {
        final Path dependency = formTwoDependency();
        final Path project = project("dup-x2-category-two");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DupX2FormTwo;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DupX2FormTwo.evaluate());
                    System.out.println(DupX2FormTwo.order());
                }
            }
            """);

        assertThat(buildAndRun(project, "dup-x2-category-two", List.of(dependency)))
            .isEqualTo(runJvm(project, "com.acme.Main", List.of(dependency)));
    }

    private String buildAndRun(final Path project, final String name, final List<Path> classpath) {
        final java.util.ArrayList<String> arguments = new java.util.ArrayList<>();
        arguments.add("build");
        arguments.add(project.toString());
        for (final Path entry : classpath) {
            arguments.add("--classpath");
            arguments.add(entry.toString());
        }
        final CliRun build = run(tempDir, arguments.toArray(String[]::new));
        if (build.exitCode() != 0) {
            return build.stderr();
        }
        return process(project, List.of(project.resolve(".javan/bin").resolve(name).toString())).stdout();
    }

    private Path formTwoDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(FORM_TWO_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("order", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withMethodBody(
                "left",
                MethodTypeDesc.of(LONG),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .putstatic(FORM_TWO_CLASS, "order", INT)
                    .ldc(Long.valueOf(10L))
                    .lreturn()
            )
            .withMethodBody(
                "right",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(FORM_TWO_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_2()
                    .iadd()
                    .putstatic(FORM_TWO_CLASS, "order", INT)
                    .iconst_3()
                    .ireturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .invokestatic(FORM_TWO_CLASS, "left", MethodTypeDesc.of(LONG))
                    .invokestatic(FORM_TWO_CLASS, "right", MethodTypeDesc.of(INT))
                    .dup_x2()
                    .i2l()
                    .ladd()
                    .l2i()
                    .iadd()
                    .ireturn()
            )
            .withMethodBody(
                "order",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.getstatic(FORM_TWO_CLASS, "order", INT).ireturn()
            ));
        return jar("dup-x2-form-two.jar", FORM_TWO_CLASS, bytes);
    }

    private Path formOneDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(FORM_ONE_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("order", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withMethodBody(
                "first",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code.iconst_1().putstatic(FORM_ONE_CLASS, "order", INT).iconst_1().ireturn()
            )
            .withMethodBody(
                "second",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(FORM_ONE_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_2()
                    .iadd()
                    .putstatic(FORM_ONE_CLASS, "order", INT)
                    .iconst_2()
                    .ireturn()
            )
            .withMethodBody(
                "third",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(FORM_ONE_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_3()
                    .iadd()
                    .putstatic(FORM_ONE_CLASS, "order", INT)
                    .iconst_3()
                    .ireturn()
            )
            .withMethodBody(
                "evaluate",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .invokestatic(FORM_ONE_CLASS, "first", MethodTypeDesc.of(INT))
                    .invokestatic(FORM_ONE_CLASS, "second", MethodTypeDesc.of(INT))
                    .invokestatic(FORM_ONE_CLASS, "third", MethodTypeDesc.of(INT))
                    .dup_x2()
                    .isub()
                    .isub()
                    .isub()
                    .ireturn()
            )
            .withMethodBody(
                "order",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code.getstatic(FORM_ONE_CLASS, "order", INT).ireturn()
            ));
        return jar("dup-x2-form-one.jar", FORM_ONE_CLASS, bytes);
    }

    private Path jar(final String name, final ClassDesc classDesc, final byte[] bytes) throws Exception {
        final Path jar = tempDir.resolve(name);
        try (OutputStream stream = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(stream)) {
            archive.putNextEntry(new JarEntry(classDesc.packageName().replace('.', '/') + "/" + classDesc.displayName() + ".class"));
            archive.write(bytes);
            archive.closeEntry();
        }
        return jar;
    }
}
