package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliStringGetBytesUtf8IntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("string-get-bytes-utf8-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            import java.nio.charset.Charset;
            import java.nio.charset.StandardCharsets;

            public final class Main {
                static Charset firstCharset;
                static Charset secondCharset;
                static int order;

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final String scenario = args.length == 0 ? "ascii" : args[0];
                    if ("ascii".equals(scenario)) {
                        printBytes("Javan");
                    } else if ("bmp".equals(scenario)) {
                        printBytes("caf\\u00e9");
                    } else if ("supplementary".equals(scenario)) {
                        printBytes("\\ud83d\\ude00");
                    } else if ("unpaired-surrogate".equals(scenario)) {
                        printBytes("\\ud800");
                    } else if ("runtime-nul".equals(scenario)) {
                        printBytes(new String(new char[] {'A', '\\u0000', 'B'}));
                    } else if ("lone-low-surrogate".equals(scenario)) {
                        printBytes(new String(new char[] {'\\uDC00'}));
                    } else if ("consecutive-high-surrogates".equals(scenario)) {
                        printBytes(new String(new char[] {'\\uD800', '\\uD801'}));
                    } else if ("low-then-high-surrogate".equals(scenario)) {
                        printBytes(new String(new char[] {'\\uDC00', '\\uD800'}));
                    } else if ("mixed-high-text-low-surrogate".equals(scenario)) {
                        printBytes(new String(new char[] {'\\uD800', 'A', '\\uDC00'}));
                    } else if ("empty".equals(scenario)) {
                        printBytes("");
                    } else if ("fresh-identity".equals(scenario)) {
                        final byte[] first = "A".getBytes(StandardCharsets.UTF_8);
                        final byte[] second = "A".getBytes(StandardCharsets.UTF_8);
                        System.out.println(first != second);
                    } else if ("fresh-independence".equals(scenario)) {
                        final byte[] first = "A".getBytes(StandardCharsets.UTF_8);
                        final byte[] second = "A".getBytes(StandardCharsets.UTF_8);
                        first[0] = 66;
                        System.out.println(second[0]);
                    } else if ("evaluation-order".equals(scenario)) {
                        final byte[] bytes = receiver().getBytes(charset());
                        System.out.println(order);
                        System.out.println(bytes[0]);
                    } else if ("null-receiver-evaluation-order".equals(scenario)) {
                        try {
                            nullReceiver().getBytes(charset());
                        } catch (final NullPointerException ignored) {
                            System.out.println(order);
                        }
                    } else if ("identity".equals(scenario)) {
                        System.out.println(StandardCharsets.UTF_8 == StandardCharsets.UTF_8);
                    } else if ("concurrent-identity".equals(scenario)) {
                        final Thread first = new Thread(new FirstReader());
                        final Thread second = new Thread(new SecondReader());
                        first.start();
                        second.start();
                        first.join();
                        second.join();
                        System.out.println(
                            firstCharset == StandardCharsets.UTF_8
                                && secondCharset == StandardCharsets.UTF_8
                                && firstCharset == secondCharset
                        );
                    } else if ("caught-null-charset".equals(scenario)) {
                        final Charset charset = null;
                        try {
                            "value".getBytes(charset);
                        } catch (final NullPointerException ignored) {
                            System.out.println("caught");
                        }
                    } else if ("caught-null-receiver".equals(scenario)) {
                        final Charset charset = StandardCharsets.UTF_8;
                        final String value = null;
                        try {
                            value.getBytes(charset);
                        } catch (final NullPointerException ignored) {
                            System.out.println("caught");
                        }
                    } else if ("uncaught-null-charset".equals(scenario)) {
                        final Charset charset = null;
                        System.out.println("value".getBytes(charset).length);
                    }
                }

                private static void printBytes(final String value) {
                    final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                    System.out.println(bytes.length);
                    for (int index = 0; index < bytes.length; index++) {
                        System.out.println(bytes[index]);
                    }
                }

                private static String receiver() {
                    order = order * 10 + 1;
                    return "A";
                }

                private static String nullReceiver() {
                    order = order * 10 + 1;
                    return null;
                }

                private static Charset charset() {
                    order = order * 10 + 2;
                    return StandardCharsets.UTF_8;
                }

                private static final class FirstReader implements Runnable {
                    @Override
                    public void run() {
                        firstCharset = StandardCharsets.UTF_8;
                    }
                }

                private static final class SecondReader implements Runnable {
                    @Override
                    public void run() {
                        secondCharset = StandardCharsets.UTF_8;
                    }
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/string-get-bytes-utf8-probe");
    }

    @Test
    void asciiEncodingMatchesJvm() {
        assertThat(nativeRun("ascii")).isEqualTo(jvmRun("ascii"));
    }

    @Test
    void bmpEncodingMatchesJvm() {
        assertThat(nativeRun("bmp")).isEqualTo(jvmRun("bmp"));
    }

    @Test
    void supplementaryEncodingMatchesJvm() {
        assertThat(nativeRun("supplementary")).isEqualTo(jvmRun("supplementary"));
    }

    @Test
    void unpairedSurrogateEncodingMatchesJvm() {
        assertThat(nativeRun("unpaired-surrogate")).isEqualTo(jvmRun("unpaired-surrogate"));
    }

    @Test
    void runtimeCreatedEmbeddedNulFailsBeforeEncoding() {
        assertThat(nativeRun("runtime-nul")).matches(result -> result.exitCode() != 0
            && result.stdout().isEmpty()
            && result.stderr().contains("native String profile does not support U+0000"));
    }

    @Test
    void loneLowSurrogateEncodingMatchesJvm() {
        assertThat(nativeRun("lone-low-surrogate")).isEqualTo(jvmRun("lone-low-surrogate"));
    }

    @Test
    void consecutiveHighSurrogateEncodingMatchesJvm() {
        assertThat(nativeRun("consecutive-high-surrogates")).isEqualTo(jvmRun("consecutive-high-surrogates"));
    }

    @Test
    void lowThenHighSurrogateEncodingMatchesJvm() {
        assertThat(nativeRun("low-then-high-surrogate")).isEqualTo(jvmRun("low-then-high-surrogate"));
    }

    @Test
    void mixedHighTextLowSurrogateEncodingMatchesJvm() {
        assertThat(nativeRun("mixed-high-text-low-surrogate"))
            .isEqualTo(jvmRun("mixed-high-text-low-surrogate"));
    }

    @Test
    void emptyEncodingMatchesJvm() {
        assertThat(nativeRun("empty")).isEqualTo(jvmRun("empty"));
    }

    @Test
    void eachEncodingReturnsDistinctStorage() {
        assertThat(nativeRun("fresh-identity")).isEqualTo(jvmRun("fresh-identity"));
    }

    @Test
    void mutatingOneEncodingDoesNotChangeAnother() {
        assertThat(nativeRun("fresh-independence")).isEqualTo(jvmRun("fresh-independence"));
    }

    @Test
    void receiverAndCharsetEvaluateLeftToRightOnce() {
        assertThat(nativeRun("evaluation-order")).isEqualTo(jvmRun("evaluation-order"));
    }

    @Test
    void charsetEvaluatesBeforeNullReceiverThrows() {
        assertThat(nativeRun("null-receiver-evaluation-order"))
            .isEqualTo(jvmRun("null-receiver-evaluation-order"));
    }

    @Test
    void freshEncodingSurvivesGcStress() {
        assertThat(nativeRun("fresh-independence", Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")))
            .isEqualTo(jvmRun("fresh-independence"));
    }

    @Test
    void standardUtf8RetainsStaticFinalIdentity() {
        assertThat(nativeRun("identity")).isEqualTo(jvmRun("identity"));
    }

    @Test
    void concurrentStandardUtf8AccessRetainsSingletonIdentity() {
        assertThat(nativeRun("concurrent-identity", Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")))
            .isEqualTo(jvmRun("concurrent-identity"));
    }

    @Test
    void nullCharsetCanBeCaught() {
        assertThat(nativeRun("caught-null-charset")).isEqualTo(jvmRun("caught-null-charset"));
    }

    @Test
    void nullReceiverCanBeCaught() {
        assertThat(nativeRun("caught-null-receiver")).isEqualTo(jvmRun("caught-null-receiver"));
    }

    @Test
    void uncaughtNullCharsetFailsAtNativeBoundary() {
        assertThat(nativeRun("uncaught-null-charset").exitCode()).isNotZero();
    }

    @Test
    void uncaughtNullCharsetNamesException() {
        assertThat(nativeRun("uncaught-null-charset").stderr()).contains("java/lang/NullPointerException");
    }

    @Test
    void nonUtf8StandardCharsetIsRejectedExplicitly() throws Exception {
        final Path rejectedProject = project("string-get-bytes-non-utf8-rejected");
        writeJava(rejectedProject, MAIN_CLASS, """
            package com.acme;

            import java.nio.charset.StandardCharsets;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("value".getBytes(StandardCharsets.ISO_8859_1).length);
                }
            }
            """);

        assertThat(run(tempDir, "build", rejectedProject.toString()).stderr()).contains("""
            error[JAVAN031]: unsupported standard charset
            Class:
              com/acme/Main
            Method:
              main([Ljava/lang/String;)V
            Subject:
              getstatic java/nio/charset/StandardCharsets.ISO_8859_1:Ljava/nio/charset/Charset;
            Reason:
              Only StandardCharsets.UTF_8 is supported by the current native string representation.
            """);
    }

    @Test
    void charsetCheckcastIsRejectedExplicitly() throws Exception {
        final Path rejectedProject = project("charset-checkcast-rejected");
        writeJava(rejectedProject, MAIN_CLASS, """
            package com.acme;

            import java.nio.charset.Charset;
            import java.nio.charset.StandardCharsets;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = StandardCharsets.UTF_8;
                    final Charset charset = (Charset) value;
                    System.out.println("value".getBytes(charset).length);
                }
            }
            """);

        assertThat(run(tempDir, "build", rejectedProject.toString()).stderr()).contains("""
            error[JAVAN045]: unsupported checkcast target
            Class:
              com/acme/Main
            Method:
              main([Ljava/lang/String;)V
            Subject:
              java/nio/charset/Charset
            Reason:
              The current runtime cannot perform a deterministic checkcast to this built-in singleton type or transport the required ClassCastException.
            """);
    }

    private ProcessResult nativeRun(final String scenario) {
        return process(project, List.of(binary.toString(), scenario));
    }

    private ProcessResult nativeRun(final String scenario, final Map<String, String> environment) {
        return process(project, List.of(binary.toString(), scenario), defaultProcessTimeout(), environment);
    }

    private ProcessResult jvmRun(final String scenario) {
        return process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            project.resolve("jvm-classes").toString(),
            MAIN_CLASS,
            scenario
        ));
    }
}
