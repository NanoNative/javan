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
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliPop2IntegrationTest extends CliIntegrationSupport {
    private static final ClassDesc PAIR_CLASS = ClassDesc.of("dep.Pop2Pair");
    private static final ClassDesc INT = ClassDesc.ofDescriptor("I");

    @Test
    void discardedLongCallMatchesJvm() throws Exception {
        final Path project = project("pop2-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    value();
                    System.out.println("done");
                }

                private static long value() {
                    System.out.println("long");
                    return 7L;
                }
            }
            """);

        assertThat(buildAndRun(project, "pop2-long", List.of()))
            .isEqualTo(runJvm(project, "com.acme.Main"));
    }

    @Test
    void discardedDoubleCallMatchesJvm() throws Exception {
        final Path project = project("pop2-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    value();
                    System.out.println("done");
                }

                private static double value() {
                    System.out.println("double");
                    return 7.5d;
                }
            }
            """);

        assertThat(buildAndRun(project, "pop2-double", List.of()))
            .isEqualTo(runJvm(project, "com.acme.Main"));
    }

    @Test
    void twoDiscardedCategoryOneCallsPreserveJvmOrder() throws Exception {
        final Path dependency = pairDependency();
        final Path project = project("pop2-category-one-pair");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Pop2Pair;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Pop2Pair.run());
                }
            }
            """);

        assertThat(buildAndRun(project, "pop2-category-one-pair", List.of(dependency)))
            .isEqualTo(runJvm(project, "com.acme.Main", List.of(dependency)));
    }

    private String buildAndRun(final Path project, final String name, final List<Path> classpath) {
        final ArrayList<String> arguments = new ArrayList<>();
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

    private Path pairDependency() throws Exception {
        final byte[] bytes = ClassFile.of().build(PAIR_CLASS, classBuilder -> classBuilder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_FINAL | ClassFile.ACC_SUPER)
            .withField("order", INT, ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC)
            .withMethodBody(
                "first",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .iconst_1()
                    .putstatic(PAIR_CLASS, "order", INT)
                    .iconst_1()
                    .ireturn()
            )
            .withMethodBody(
                "second",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC,
                code -> code
                    .getstatic(PAIR_CLASS, "order", INT)
                    .bipush(10)
                    .imul()
                    .iconst_2()
                    .iadd()
                    .putstatic(PAIR_CLASS, "order", INT)
                    .iconst_2()
                    .ireturn()
            )
            .withMethodBody(
                "run",
                MethodTypeDesc.of(INT),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                code -> code
                    .invokestatic(PAIR_CLASS, "first", MethodTypeDesc.of(INT))
                    .invokestatic(PAIR_CLASS, "second", MethodTypeDesc.of(INT))
                    .pop2()
                    .getstatic(PAIR_CLASS, "order", INT)
                    .ireturn()
            ));
        final Path jar = tempDir.resolve("pop2-pair.jar");
        try (OutputStream stream = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(stream)) {
            archive.putNextEntry(new JarEntry("dep/Pop2Pair.class"));
            archive.write(bytes);
            archive.closeEntry();
        }
        return jar;
    }
}
