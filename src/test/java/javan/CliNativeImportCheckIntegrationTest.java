package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliNativeImportCheckIntegrationTest extends CliIntegrationSupport {
    @Test
    void configuredStaticNativeWithSupportedAbiPassesCheck() throws Exception {
        final Path project = project("configured-native-supported-abi");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    probe(1, 2L, 3.0f, 4.0d, new byte[] {1});
                }

                private static native double probe(int integer, long wide, float decimal, double precise, byte[] bytes);
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe(int,long,float,double,byte[]):double -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void reachableUndeclaredStaticNativeReportsJavan013() throws Exception {
        final Path project = project("undeclared-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    probe();
                }

                private static native int probe();
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN013]: native import is not declared", "Declare the exact native method in [native].imports.");
    }

    @Test
    void configuredReachableInstanceNativeReportsJavan013() throws Exception {
        final Path project = project("configured-instance-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    new Main().probe();
                }

                private native int probe();
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe():int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN013]: native import must be static", "Declare the native method static.");
    }

    @Test
    void configuredReachableReferenceAbiReportsJavan013() throws Exception {
        final Path project = project("configured-reference-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    probe("unsupported");
                }

                private static native int probe(String value);
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe(java.lang.String):int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN013]: native import ABI is not supported", "Use only the supported native import ABI.");
    }

    @Test
    void configuredReachableBooleanAbiReportsJavan013() throws Exception {
        final Path project = project("configured-boolean-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    probe(true);
                }

                private static native int probe(boolean value);
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe(boolean):int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN013]: native import ABI is not supported",
                "non-null borrowed byte[] parameters"
            );
    }

    @Test
    void configuredReachableByteArrayReturnReportsJavan013() throws Exception {
        final Path project = project("configured-byte-array-return-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    probe();
                }

                private static native byte[] probe();
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe():byte[] -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN013]: native import ABI is not supported",
                "non-null borrowed byte[] parameters"
            );
    }

    @Test
    void unusedConfiguredInstanceNativeReportsJavan013() throws Exception {
        final Path project = project("unused-configured-instance-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("unused");
                }

                private native int probe();
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe():int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN013]: native import must be static", "Declare the native method static.");
    }

    @Test
    void unusedConfiguredUnsupportedAbiReportsJavan013() throws Exception {
        final Path project = project("unused-configured-reference-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("unused");
                }

                private static native int probe(String value);
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe(java.lang.String):int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN013]: native import ABI is not supported", "Use only the supported native import ABI.");
    }

    @Test
    void invalidConfiguredNativeIsReportedWhenMainIsMissing() throws Exception {
        final Path project = project("missing-main-invalid-native");
        writeJava(project, "com.acme.Tool", """
            package com.acme;

            public final class Tool {
                private native int probe();
            }
            """);
        writeNativeImports(project, "com.acme.Tool.probe():int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());
        final String report = Files.readString(project.resolve(".javan/reports/diagnostics.txt"));

        assertThat(run.exitCode() + "\n" + run.stderr() + "\n" + report)
            .startsWith("2\nerror[JAVAN020]: no main class found")
            .contains("error[JAVAN013]: native import must be static", "com/acme/Tool.probe()I");
    }

    @Test
    void buildEmitsOnlyReachableConfiguredNativeImports() throws Exception {
        final Path project = project("reachable-native-emission");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(reachable());
                }

                private static native int reachable();
                private static native int unused();
            }
            """);
        final Path nativeDirectory = Files.createDirectories(project.resolve("native"));
        Files.writeString(nativeDirectory.resolve("reachable.c"), "int native_reachable(void) { return 42; }\n");
        Files.writeString(project.resolve("javan.toml"), """
            [native]
            imports = ["com.acme.Main.reachable():int -> native_reachable", "com.acme.Main.unused():int -> native_unused"]
            sources = ["native/reachable.c"]
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final Path generated = project.resolve(".javan/generated/main.c");
        final String generatedSource = Files.exists(generated) ? Files.readString(generated) : "missing generated C";

        assertThat(run.exitCode() + "\n" + run.stderr() + "\n" + generatedSource)
            .startsWith("0\n\n")
            .contains("native_reachable")
            .doesNotContain("native_unused");
    }

    @Test
    void configuredNativeCallDoesNotContaminateMaterializedFunctionFlow() throws Exception {
        final Path project = project("configured-native-function-flow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    probe(1);
                    final Function<String, String> function = Main::decorate;
                    System.out.println(function.apply("value"));
                }

                private static native int probe(int value);

                private static String decorate(final String value) {
                    return "native-" + value;
                }
            }
            """);
        writeNativeImports(project, "com.acme.Main.probe(int):int -> native_probe");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void consumerIntegerMethodReferenceToConfiguredNativeIntParameterIsRejected() throws Exception {
        final Path project = project("configured-native-consumer-integer-adaptation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Consumer<Integer> consumer = Main::consume;
                    consumer.accept(1);
                }

                private static native void consume(int value);
            }
            """);
        writeNativeImports(project, "com.acme.Main.consume(int):void -> native_consume");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr()).contains(
            "2\nerror[JAVAN013]: configured native method reference requires exact descriptors",
            "com/acme/Main.consume(I)V",
            "(Ljava/lang/Integer;)V != (I)V"
        );
    }

    @Test
    void supplierIntegerMethodReferenceToConfiguredNativeIntReturnIsRejected() throws Exception {
        final Path project = project("configured-native-supplier-integer-adaptation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Integer> supplier = Main::produce;
                    System.out.println(supplier.get());
                }

                private static native int produce();
            }
            """);
        writeNativeImports(project, "com.acme.Main.produce():int -> native_produce");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr()).contains(
            "2\nerror[JAVAN013]: configured native method reference requires exact descriptors",
            "com/acme/Main.produce()I",
            "()Ljava/lang/Integer; != ()I"
        );
    }

    @Test
    void unreachableConfiguredNativeIntegerMethodReferenceDoesNotFailCheck() throws Exception {
        final Path project = project("unreachable-configured-native-consumer-integer-adaptation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("reachable");
                }

                private static void unused() {
                    final Consumer<Integer> consumer = Main::consume;
                    consumer.accept(1);
                }

                private static native void consume(int value);
            }
            """);
        writeNativeImports(project, "com.acme.Main.consume(int):void -> native_consume");

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    private static void writeNativeImports(final Path project, final String declaration) throws Exception {
        Files.writeString(project.resolve("javan.toml"), """
            [native]
            imports = ["%s"]
            """.formatted(declaration));
    }
}
