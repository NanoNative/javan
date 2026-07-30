package javan.analysis;

import javan.classfile.ClassFile;
import javan.classfile.ClassFileReader;
import javan.classfile.Instruction;
import javan.classfile.MethodInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class FunctionValueFlowTest {
    @TempDir
    private Path tempDir;

    @Test
    void lateParameterGrowthReschedulesCallee() throws Exception {
        final Map<String, ClassFile> classes = compile("late-parameter", """
            static Object use(final Function<Object, Object> function) {
                return function.apply("value");
            }

            static Object call() {
                return use(value -> value);
            }
            """);
        final EntryPoint use = entry(
            "use",
            "(Ljava/util/function/Function;)Ljava/lang/Object;"
        );
        final EntryPoint call = entry("call", "()Ljava/lang/Object;");

        assertThat(functionKind(classes, List.of(use, call), use))
            .isEqualTo(FunctionValueFlow.ValueKind.MATERIALIZED);
    }

    @Test
    void lateReturnGrowthReschedulesCaller() throws Exception {
        final Map<String, ClassFile> classes = compile("late-return", """
            static Object call() {
                return factory().apply("value");
            }

            static Function<Object, Object> factory() {
                return value -> value;
            }
            """);
        final EntryPoint call = entry("call", "()Ljava/lang/Object;");
        final EntryPoint factory = entry(
            "factory",
            "()Ljava/util/function/Function;"
        );

        assertThat(functionKind(classes, List.of(call, factory), call))
            .isEqualTo(FunctionValueFlow.ValueKind.MATERIALIZED);
    }

    @Test
    void finalCallbackFieldGrowthReschedulesReader() throws Exception {
        final Map<String, ClassFile> classes = compile("late-field", """
            private static final Function<Object, Object> CALLBACK = value -> value;

            static Object read() {
                return CALLBACK.apply("value");
            }
            """);
        final EntryPoint read = entry("read", "()Ljava/lang/Object;");
        final EntryPoint initializer = entry("<clinit>", "()V");

        assertThat(functionKind(classes, List.of(read, initializer), read))
            .isEqualTo(FunctionValueFlow.ValueKind.MATERIALIZED);
    }

    @Test
    void recursiveParameterGrowthReschedulesSameMethod() throws Exception {
        final Map<String, ClassFile> classes = compile("recursive-parameter", """
            static Object recursive(
                final Function<Object, Object> first,
                final Function<Object, Object> second,
                final boolean swap
            ) {
                if (swap) {
                    return recursive(second, first, false);
                }
                return first.apply("value");
            }

            static Object start(final Function<Object, Object> unknown) {
                return recursive(unknown, value -> value, true);
            }
            """);
        final EntryPoint recursive = entry(
            "recursive",
            "(Ljava/util/function/Function;Ljava/util/function/Function;Z)Ljava/lang/Object;"
        );
        final EntryPoint start = entry(
            "start",
            "(Ljava/util/function/Function;)Ljava/lang/Object;"
        );

        assertThat(functionKind(classes, List.of(recursive, start), recursive))
            .isEqualTo(FunctionValueFlow.ValueKind.MATERIALIZED);
    }

    private static FunctionValueFlow.ValueKind functionKind(
        final Map<String, ClassFile> classes,
        final List<EntryPoint> reachable,
        final EntryPoint use
    ) {
        final ClassFile classFile = classes.get(use.className());
        final MethodInfo method = classFile.method(use.methodName(), use.descriptor()).orElseThrow();
        final Instruction instruction = method.code().orElseThrow().instructions().stream()
            .filter(candidate -> candidate.methodRef().isPresent())
            .filter(candidate -> "java/util/function/Function".equals(
                candidate.methodRef().orElseThrow().owner()
            ))
            .filter(candidate -> "apply".equals(candidate.methodRef().orElseThrow().name()))
            .findFirst()
            .orElseThrow();
        return FunctionValueFlow.analyze(classes, reachable).functionKind(
            use.className(),
            use.methodName(),
            use.descriptor(),
            instruction.offset()
        );
    }

    private Map<String, ClassFile> compile(final String name, final String members) throws Exception {
        final Path root = tempDir.resolve(name);
        final Path sourceFile = root.resolve("src/com/acme/Main.java");
        final Path classesRoot = root.resolve("classes");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classesRoot);
        Files.writeString(sourceFile, """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

            """ + members + "}\n");
        final int exitCode = ToolProvider.getSystemJavaCompiler().run(
            null,
            null,
            null,
            "-d",
            classesRoot.toString(),
            sourceFile.toString()
        );
        if (exitCode != 0) {
            throw new IllegalStateException("javac failed with exit code " + exitCode);
        }
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        final ClassFileReader reader = new ClassFileReader();
        try (var paths = Files.walk(classesRoot)) {
            for (final Path classFile
                : paths.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                final ClassFile parsed = reader.read(Files.readAllBytes(classFile), classFile);
                classes.put(parsed.name(), parsed);
            }
        }
        return Map.copyOf(classes);
    }

    private static EntryPoint entry(final String methodName, final String descriptor) {
        return new EntryPoint("com/acme/Main", methodName, descriptor);
    }
}
