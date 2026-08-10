package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliNativeImportBuildIntegrationTest extends CliIntegrationSupport {
    private static final Map<String, String> GC_STRESS = Map.of(
        "JAVAN_GC_STRESS", "1",
        "JAVAN_HEAP_LIMIT_BYTES", "1048576"
    );

    @Test
    void configuredIntImportExecutesWithExactParameterAndReturn() throws Exception {
        final ProcessResult result = buildAndRun("native-int", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(adjust(17)); }
                private static native int adjust(int value);
            }
            """, "int native_adjust(int value) { return value * 3; }", List.of("com.acme.Main.adjust(int):int -> native_adjust"));

        assertThat(result).isEqualTo(new ProcessResult(0, "51\n", ""));
    }

    @Test
    void configuredLongImportExecutesWithExactParameterAndReturn() throws Exception {
        final ProcessResult result = buildAndRun("native-long", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(adjust(4000000000L)); }
                private static native long adjust(long value);
            }
            """, "long long native_adjust(long long value) { return value * 2; }", List.of("com.acme.Main.adjust(long):long -> native_adjust"));

        assertThat(result).isEqualTo(new ProcessResult(0, "8000000000\n", ""));
    }

    @Test
    void configuredFloatImportExecutesWithExactParameterAndReturn() throws Exception {
        final ProcessResult result = buildAndRun("native-float", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(adjust(1.25f)); }
                private static native float adjust(float value);
            }
            """, "float native_adjust(float value) { return value * 3.0f; }", List.of("com.acme.Main.adjust(float):float -> native_adjust"));

        assertThat(result).isEqualTo(new ProcessResult(0, "3.75\n", ""));
    }

    @Test
    void configuredDoubleImportExecutesWithExactParameterAndReturn() throws Exception {
        final ProcessResult result = buildAndRun("native-double", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(adjust(1.25d)); }
                private static native double adjust(double value);
            }
            """, "double native_adjust(double value) { return value * 2.5; }", List.of("com.acme.Main.adjust(double):double -> native_adjust"));

        assertThat(result).isEqualTo(new ProcessResult(0, "3.125\n", ""));
    }

    @Test
    void configuredVoidImportExecutesAndUpdatesNativeState() throws Exception {
        final ProcessResult result = buildAndRun("native-void", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { trigger(); System.out.println(calls()); }
                private static native void trigger();
                private static native int calls();
            }
            """, """
            static int count = 0;
            void native_trigger(void) { count++; }
            int native_calls(void) { return count; }
            """, List.of(
                "com.acme.Main.trigger():void -> native_trigger",
                "com.acme.Main.calls():int -> native_calls"
            ));

        assertThat(result).isEqualTo(new ProcessResult(0, "1\n", ""));
    }

    @Test
    void configuredByteArrayImportBorrowsAndMutatesExactLength() throws Exception {
        final ProcessResult result = buildAndRun("native-bytes", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    final byte[] values = {3, 4, 5};
                    System.out.println(mutate(values) + ":" + values[0] + ":" + values[1] + ":" + values[2]);
                }
                private static native int mutate(byte[] values);
            }
            """, byteArrayC("int native_mutate(JavanNativeImportedByteArray values) { values.data[1] = 11; return values.length; }"),
            List.of("com.acme.Main.mutate(byte[]):int -> native_mutate"));

        assertThat(result).isEqualTo(new ProcessResult(0, "3:3:11:5\n", ""));
    }

    @Test
    void configuredSourceIncludesGeneratedRuntimeHeaderForByteArrayAbi() throws Exception {
        final ProcessResult result = buildAndRun("native-runtime-header", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    final byte[] values = {2, 3};
                    System.out.println(mutate(values) + ":" + values[0] + ":" + values[1]);
                }
                private static native int mutate(byte[] values);
            }
            """, """
            #include "javan_runtime.h"
            int native_mutate(JavanNativeImportedByteArray values) {
                values.data[0] = 12;
                return values.length;
            }
            """, List.of("com.acme.Main.mutate(byte[]):int -> native_mutate"));

        assertThat(result).isEqualTo(new ProcessResult(0, "2:12:3\n", ""));
    }

    @Test
    void consumerByteArrayMethodReferenceInvokesConfiguredNativeImport() throws Exception {
        final ProcessResult result = buildAndRun("native-consumer-bytes", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                public static void main(final String[] args) {
                    final byte[] values = {3, 4};
                    final Consumer<byte[]> consumer = Main::mutate;
                    consumer.accept(values);
                    System.out.println(values[0] + ":" + values[1]);
                }

                private static native void mutate(byte[] values);
            }
            """, byteArrayC("void native_mutate(JavanNativeImportedByteArray values) { values.data[1] = 11; }"),
            List.of("com.acme.Main.mutate(byte[]):void -> native_mutate"));

        assertThat(result).isEqualTo(new ProcessResult(0, "3:11\n", ""));
    }

    @Test
    void configuredByteArrayImportAcceptsEmptyArrayWithZeroLength() throws Exception {
        final ProcessResult result = buildAndRun("native-empty-bytes", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(length(new byte[0])); }
                private static native int length(byte[] values);
            }
            """, byteArrayC("int native_length(JavanNativeImportedByteArray values) { return values.length; }"),
            List.of("com.acme.Main.length(byte[]):int -> native_length"));

        assertThat(result).isEqualTo(new ProcessResult(0, "0\n", ""));
    }

    @Test
    void configuredNullByteArrayPanicsBeforeExternalBody() throws Exception {
        final ProcessResult result = buildAndRun("native-null-bytes", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(length(null)); }
                private static native int length(byte[] values);
            }
            """, byteArrayC("""
            #include <stdio.h>
            int native_length(JavanNativeImportedByteArray values) {
                fprintf(stderr, "external body reached\\n");
                return values.length;
            }
            """), List.of("com.acme.Main.length(byte[]):int -> native_length"));

        assertThat(result.exitCode() + "\n" + result.stdout() + "\n" + result.stderr())
            .startsWith("1\n\n")
            .contains("[JAVAN-RUNTIME-PANIC] runtime helper failure", "detail: native import byte[] argument is null")
            .doesNotContain("external body reached");
    }

    @Test
    void inlineAllocatedByteArraySurvivesNativeForcedGc() throws Exception {
        final ProcessResult result = buildAndRun("native-inline-gc", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(mutate(new byte[] {2, 3, 4})); }
                private static native int mutate(byte[] values);
            }
            """, byteArrayC("""
            void javan_gc_collect(void);
            int native_mutate(JavanNativeImportedByteArray values) {
                javan_gc_collect();
                values.data[0] = 12;
                return values.data[0] + values.length;
            }
            """), List.of("com.acme.Main.mutate(byte[]):int -> native_mutate"), GC_STRESS);

        assertThat(result).isEqualTo(new ProcessResult(0, "15\n", ""));
    }

    @Test
    void twoByteArrayArgumentsSurviveNativeForcedGc() throws Exception {
        final ProcessResult result = buildAndRun("native-two-arrays-gc", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    final byte[] first = {1, 2};
                    final byte[] second = {3, 4, 5};
                    System.out.println(mutate(first, second) + ":" + first[0] + ":" + second[2]);
                }
                private static native int mutate(byte[] first, byte[] second);
            }
            """, byteArrayC("""
            void javan_gc_collect(void);
            int native_mutate(JavanNativeImportedByteArray first, JavanNativeImportedByteArray second) {
                javan_gc_collect();
                first.data[0] = 10;
                second.data[2] = 11;
                return first.length + second.length;
            }
            """), List.of("com.acme.Main.mutate(byte[],byte[]):int -> native_mutate"), GC_STRESS);

        assertThat(result).isEqualTo(new ProcessResult(0, "5:10:11\n", ""));
    }

    @Test
    void repeatedByteArrayImportsSurviveConcurrentForcedCollection() throws Exception {
        final ProcessResult result = buildAndRun("native-concurrent-byte-gc", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) throws Exception {
                    final Thread collector = new Thread(new Collector());
                    collector.start();
                    final byte[] values = {1, 2, 3};
                    int totalLength = 0;
                    for (int index = 0; index < 1000; index++) {
                        totalLength += mutate(values);
                    }
                    collector.join();
                    System.out.println(totalLength + ":" + values[0]);
                }

                private static native int mutate(byte[] values);
                private static native int collect(byte[] values);

                private static final class Collector implements Runnable {
                    public void run() {
                        for (int index = 0; index < 1000; index++) {
                            collect(new byte[] {(byte) index});
                        }
                    }
                }
            }
            """, byteArrayC("""
            void javan_gc_collect(void);
            int native_mutate(JavanNativeImportedByteArray values) {
                values.data[0] = (signed char) (values.data[0] + 1);
                return values.length;
            }
            int native_collect(JavanNativeImportedByteArray values) {
                javan_gc_collect();
                return values.length;
            }
            """), List.of(
                "com.acme.Main.mutate(byte[]):int -> native_mutate",
                "com.acme.Main.collect(byte[]):int -> native_collect"
            ), GC_STRESS);

        assertThat(result).isEqualTo(new ProcessResult(0, "3000:-23\n", ""));
    }

    @Test
    void configuredPrecompiledObjectIsLinkedAndInvoked() throws Exception {
        final Optional<String> compiler = cCompiler();
        assumeTrue(compiler.isPresent(), "No host C compiler is available.");
        final Path project = project("native-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(value()); }
                private static native int value();
            }
            """);
        final Path source = writeNative(project, "native/value.c", "int native_value(void) { return 41; }");
        compileObject(project, compiler.orElseThrow(), source, project.resolve("native/value.o"));
        writeNativeConfig(project, List.of("com.acme.Main.value():int -> native_value"), List.of(), List.of("native/value.o"), false);
        final ProcessResult result = buildAndRun(project, "native-object", Map.of());

        assertThat(result).isEqualTo(new ProcessResult(0, "41\n", ""));
    }

    @Test
    void configuredSourceBeforeObjectDeclarationPreservesBehavior() throws Exception {
        final Optional<String> compiler = cCompiler();
        assumeTrue(compiler.isPresent(), "No host C compiler is available.");
        final ProcessResult result = buildSourceAndObjectProject("native-source-object-first", compiler.orElseThrow(), false);

        assertThat(result).isEqualTo(new ProcessResult(0, "42\n", ""));
    }

    @Test
    void configuredObjectBeforeSourceDeclarationPreservesBehavior() throws Exception {
        final Optional<String> compiler = cCompiler();
        assumeTrue(compiler.isPresent(), "No host C compiler is available.");
        final ProcessResult result = buildSourceAndObjectProject("native-object-source-first", compiler.orElseThrow(), true);

        assertThat(result).isEqualTo(new ProcessResult(0, "42\n", ""));
    }

    @Test
    void unresolvedConfiguredExternalSymbolReportsEnrichedDiagnostic() throws Exception {
        final Path project = project("native-unresolved");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(missing()); }
                private static native int missing();
            }
            """);
        writeNativeConfig(project, List.of("com.acme.Main.missing():int -> native_symbol_missing"), List.of(), List.of(), false);
        final CliRun result = run(tempDir, "build", project.toString());

        assertThat(result.exitCode() + "\n" + result.stderr())
            .startsWith("1\nerror[JAVAN901]: Native link failed\nMissing native import symbols: native_symbol_missing\n");
    }

    @Test
    void unreachableConfiguredNativeMethodDoesNotRequireExternalDefinition() throws Exception {
        final Path project = project("native-unreachable");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println("reachable"); }
                private static native int unused();
            }
            """);
        writeNativeConfig(project, List.of("com.acme.Main.unused():int -> native_unused"), List.of(), List.of(), false);
        final ProcessResult result = buildAndRun(project, "native-unreachable", Map.of());

        assertThat(result).isEqualTo(new ProcessResult(0, "reachable\n", ""));
    }

    @Test
    void configuredNativeWrapperCollisionWithReachableJavaMethodRejectsBeforeNativeCompilation() throws Exception {
        final Path project = project("native-wrapper-collision");
        writeJava(project, "sample.Main", """
            package sample;
            public final class Main {
                public static void main(final String[] args) {
                    A_B.probe();
                    A$B.probe();
                }
            }
            """);
        writeJava(project, "sample.A_B", """
            package sample;
            final class A_B {
                static void probe() {
                }
            }
            """);
        writeJava(project, "sample.A$B", """
            package sample;
            final class A$B {
                static native void probe();
            }
            """);
        writeNativeConfig(project, List.of("sample.A$B.probe():void -> client_probe"), List.of(), List.of(), false);

        final CliRun result = run(tempDir, "build", project.toString());

        assertThat(result.exitCode() + "\n" + result.stderr()).isEqualTo("""
            2
            error[JAVAN900]: Native import wrapper symbol collision: javan_sample_A_B_probe___V for sample/A$B.probe()V and sample/A_B.probe()V
            """);
    }

    @Test
    void allocatorShapedNativeCanonicalKeyBuildsThroughPrivateWrapper() throws Exception {
        final Path project = project("native-allocator-collision");
        writeJava(project, "Main", """
            public final class Main {
                public static void main(final String[] args) {
                    new _A_m___V();
                    new_.A.m();
                    System.out.println("ok");
                }
            }
            """);
        writeJava(project, "_A_m___V", """
            final class _A_m___V {
            }
            """);
        writeJava(project, "new_.A", """
            package new_;
            public final class A {
                public static native void m();
            }
            """);
        writeNative(project, "native/imports.c", "void client_probe(void) { }");
        writeNativeConfig(project, List.of("new_.A.m():void -> client_probe"), List.of("native/imports.c"), List.of(), false);

        final ProcessResult result = buildAndRun(project, "native-allocator-collision", Map.of());

        assertThat(result).isEqualTo(new ProcessResult(0, "ok\n", ""));
    }

    @Test
    void sharedLibraryRejectsReachableMissingNativeImport() throws Exception {
        final Path project = project("native-shared-unresolved");
        writeJava(project, "com.acme.Library", """
            package com.acme;
            public final class Library {
                public static int value() {
                    return missing();
                }

                private static native int missing();
            }
            """);
        writeNativeConfig(project, List.of("com.acme.Library.missing():int -> native_symbol_missing"), List.of(), List.of(), false);

        final CliRun result = run(tempDir, "build", project.toString(), "--library", "--format", "shared", "--export", "com.acme.Library.value");

        assertThat(result.exitCode() + "\n" + result.stderr()).startsWith(
            "1\nerror[JAVAN901]: Native shared library link failed\nMissing native import symbols: native_symbol_missing\n"
        );
    }

    @Test
    void staticLibraryDefersReachableMissingNativeImportToFinalConsumerLink() throws Exception {
        final Path project = project("native-static-unresolved");
        writeJava(project, "com.acme.Library", """
            package com.acme;
            public final class Library {
                public static int value() {
                    return missing();
                }

                private static native int missing();
            }
            """);
        writeNativeConfig(project, List.of("com.acme.Library.missing():int -> native_symbol_missing"), List.of(), List.of(), false);

        final CliRun result = run(tempDir, "build", project.toString(), "--library", "--format", "static", "--export", "com.acme.Library.value");

        assertThat(result.exitCode()).isZero();
    }

    @Test
    void staticLibraryConsumerSuppliesConfiguredNativeImport() throws Exception {
        final Optional<String> compiler = cCompiler();
        assumeTrue(compiler.isPresent(), "No host C compiler is available.");
        final Path project = project("native-static-consumer");
        writeJava(project, "com.acme.Library", """
            package com.acme;
            public final class Library {
                public static int value() {
                    return imported() + 2;
                }

                private static native int imported();
            }
            """);
        writeNativeConfig(project, List.of("com.acme.Library.imported():int -> consumer_imported"), List.of(), List.of(), false);
        final CliRun build = run(
            tempDir,
            "build",
            project.toString(),
            "--library",
            "--format",
            "static",
            "--export",
            "com.acme.Library.value"
        );
        if (build.exitCode() != 0) {
            throw new AssertionError("CLI static library build failed:\n" + build.stderr());
        }
        final Path consumer = writeC(project, "consumer.c", """
            #include <stdio.h>

            int consumer_imported(void) {
                return 40;
            }

            int javan_export_com_acme_Library_value_void(void);

            int main(void) {
                printf("%d\\n", javan_export_com_acme_Library_value_void());
                return 0;
            }
            """);
        final Path binary = project.resolve("native-static-consumer");
        final ProcessResult link = process(project, List.of(
            compiler.orElseThrow(),
            consumer.toString(),
            project.resolve(".javan/dist/libnative-static-consumer.a").toString(),
            "-o",
            binary.toString()
        ));
        if (link.exitCode() != 0) {
            throw new AssertionError("C final consumer link failed:\n" + link.stderr() + link.stdout());
        }
        final ProcessResult result = process(project, List.of(binary.toString()));

        assertThat(result).isEqualTo(new ProcessResult(0, "42\n", ""));
    }

    private ProcessResult buildAndRun(
        final String projectName,
        final String javaSource,
        final String cSource,
        final List<String> imports
    ) throws Exception {
        return buildAndRun(projectName, javaSource, cSource, imports, Map.of());
    }

    private ProcessResult buildAndRun(
        final String projectName,
        final String javaSource,
        final String cSource,
        final List<String> imports,
        final Map<String, String> environment
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", javaSource);
        writeNative(project, "native/imports.c", cSource);
        writeNativeConfig(project, imports, List.of("native/imports.c"), List.of(), false);
        return buildAndRun(project, projectName, environment);
    }

    private ProcessResult buildSourceAndObjectProject(
        final String projectName,
        final String compiler,
        final boolean objectsFirst
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) { System.out.println(total()); }
                private static native int total();
            }
            """);
        writeNative(project, "native/source.c", """
            int object_value(void);
            int native_total(void) { return 40 + object_value(); }
            """);
        final Path objectSource = writeNative(project, "native/object.c", "int object_value(void) { return 2; }");
        compileObject(project, compiler, objectSource, project.resolve("native/object.o"));
        writeNativeConfig(
            project,
            List.of("com.acme.Main.total():int -> native_total"),
            List.of("native/source.c"),
            List.of("native/object.o"),
            objectsFirst
        );
        return buildAndRun(project, projectName, Map.of());
    }

    private ProcessResult buildAndRun(final Path project, final String projectName, final Map<String, String> environment) {
        final CliRun build = run(tempDir, "build", project.toString());
        if (build.exitCode() != 0) {
            throw new AssertionError("CLI build failed for " + projectName + ":\n" + build.stderr());
        }
        return process(project, List.of(project.resolve(".javan/bin").resolve(projectName).toString()), defaultProcessTimeout(), environment);
    }

    private static Path writeNative(final Path project, final String name, final String source) throws Exception {
        Files.createDirectories(project.resolve("native"));
        return writeC(project, name, source);
    }

    private static void writeNativeConfig(
        final Path project,
        final List<String> imports,
        final List<String> sources,
        final List<String> objects,
        final boolean objectsFirst
    ) throws Exception {
        final String importValues = tomlList(imports);
        final String sourceValues = tomlList(sources);
        final String objectValues = tomlList(objects);
        final String inputs = objectsFirst
            ? "objects = " + objectValues + "\nsources = " + sourceValues
            : "sources = " + sourceValues + "\nobjects = " + objectValues;
        Files.writeString(project.resolve("javan.toml"), "[native]\nimports = " + importValues + "\n" + inputs + "\n");
    }

    private static String tomlList(final List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"").collect(java.util.stream.Collectors.joining(", ", "[", "]"));
    }

    private static String byteArrayC(final String body) {
        return """
            typedef struct {
                signed char* data;
                int length;
            } JavanNativeImportedByteArray;
            """ + body;
    }

    private static Optional<String> cCompiler() {
        final String configured = System.getenv("CC");
        if (configured != null && !configured.isBlank() && configured.indexOf(' ') < 0 && commandAvailable(configured)) {
            return Optional.of(configured);
        }
        for (final String candidate : List.of("cc", "clang", "gcc")) {
            if (commandAvailable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static void compileObject(final Path project, final String compiler, final Path source, final Path object) {
        final ProcessResult result = process(project, List.of(compiler, "-c", source.toString(), "-o", object.toString()));
        if (result.exitCode() != 0) {
            throw new AssertionError("Native object compilation failed:\n" + result.stderr() + result.stdout());
        }
    }
}
