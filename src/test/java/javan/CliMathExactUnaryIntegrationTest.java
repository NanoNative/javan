package javan;

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

import static javan.testing.TestSuite.NativeTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliMathExactUnaryIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("math-exact-unary-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int order;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "safe" : args[0];
                    if ("safe".equals(scenario)) {
                        System.out.println(Math.subtractExact(12, 5));
                        System.out.println(Math.incrementExact(41));
                        System.out.println(Math.incrementExact(41L));
                        System.out.println(Math.decrementExact(-41));
                        System.out.println(Math.decrementExact(-41L));
                        System.out.println(Math.negateExact(41));
                        System.out.println(Math.negateExact(41L));
                    } else if ("caught".equals(scenario)) {
                        System.out.println(subtractOverflow());
                        System.out.println(incrementIntOverflow());
                        System.out.println(incrementLongOverflow());
                        System.out.println(decrementIntOverflow());
                        System.out.println(decrementLongOverflow());
                        System.out.println(negateIntOverflow());
                        System.out.println(negateLongOverflow());
                    } else if ("order".equals(scenario)) {
                        System.out.println(Math.subtractExact(left(), right()));
                        System.out.println(order);
                    } else if ("uncaught".equals(scenario)) {
                        System.out.println(Math.negateExact(Long.MIN_VALUE));
                    }
                }

                private static int subtractOverflow() {
                    try {
                        return Math.subtractExact(Integer.MIN_VALUE, 1);
                    } catch (final ArithmeticException ignored) {
                        return -101;
                    }
                }

                private static int incrementIntOverflow() {
                    try {
                        return Math.incrementExact(Integer.MAX_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return -102;
                    }
                }

                private static long incrementLongOverflow() {
                    try {
                        return Math.incrementExact(Long.MAX_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return 3L;
                    }
                }

                private static int decrementIntOverflow() {
                    try {
                        return Math.decrementExact(Integer.MIN_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return -104;
                    }
                }

                private static long decrementLongOverflow() {
                    try {
                        return Math.decrementExact(Long.MIN_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return -5L;
                    }
                }

                private static int negateIntOverflow() {
                    try {
                        return Math.negateExact(Integer.MIN_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return 106;
                    }
                }

                private static long negateLongOverflow() {
                    try {
                        return Math.negateExact(Long.MIN_VALUE);
                    } catch (final ArithmeticException ignored) {
                        return 7L;
                    }
                }

                private static int left() {
                    order = order * 10 + 1;
                    return 108;
                }

                private static int right() {
                    order = order * 10 + 2;
                    return 110;
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/math-exact-unary-probe");
    }

    @Test
    void safeValuesMatchJvm() {
        assertThat(nativeRun("safe")).isEqualTo(jvmRun("safe"));
    }

    @Test
    void caughtOverflowsMatchJvm() {
        assertThat(nativeRun("caught")).isEqualTo(jvmRun("caught"));
    }

    @Test
    void subtractExactIntArgumentsEvaluateLeftToRightOnce() {
        assertThat(nativeRun("order")).isEqualTo(jvmRun("order"));
    }

    @Test
    void uncaughtOverflowFailsAtNativeBoundary() {
        assertThat(nativeRun("uncaught").exitCode()).isNotZero();
    }

    @Test
    void uncaughtOverflowNamesArithmeticException() {
        assertThat(nativeRun("uncaught").stderr()).contains("java/lang/ArithmeticException");
    }

    private ProcessResult nativeRun(final String scenario) {
        return process(project, List.of(binary.toString(), scenario));
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
