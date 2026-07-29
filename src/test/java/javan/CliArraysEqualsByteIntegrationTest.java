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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliArraysEqualsByteIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("arrays-equals-byte-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private static int order;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String scenario = args.length == 0 ? "identity" : args[0];
                    if ("identity".equals(scenario)) {
                        final byte[] values = {1};
                        System.out.println(java.util.Arrays.equals(values, values));
                    } else if ("both-null".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals((byte[]) null, (byte[]) null));
                    } else if ("left-null".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals(null, new byte[]{1}));
                    } else if ("right-null".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals(new byte[]{1}, null));
                    } else if ("empty".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals(new byte[0], new byte[0]));
                    } else if ("length".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals(new byte[]{1}, new byte[]{1, 2}));
                    } else if ("equal-signed".equals(scenario)) {
                        final byte[] left = {-128, -1, 0, 127};
                        final byte[] right = {-128, -1, 0, 127};
                        System.out.println(java.util.Arrays.equals(left, right));
                    } else if ("different".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals(new byte[]{1, 2}, new byte[]{1, 3}));
                    } else if ("order".equals(scenario)) {
                        System.out.println(java.util.Arrays.equals(left(), right()));
                        System.out.println(order);
                    }
                }

                private static byte[] left() {
                    order = order * 10 + 1;
                    return new byte[]{1};
                }

                private static byte[] right() {
                    order = order * 10 + 2;
                    return new byte[]{1};
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/arrays-equals-byte-probe");
    }

    @Test
    void sameReferenceMatchesJvm() {
        assertThat(nativeRun("identity")).isEqualTo(jvmRun("identity"));
    }

    @Test
    void twoNullReferencesMatchJvm() {
        assertThat(nativeRun("both-null")).isEqualTo(jvmRun("both-null"));
    }

    @Test
    void nullLeftReferenceMatchesJvm() {
        assertThat(nativeRun("left-null")).isEqualTo(jvmRun("left-null"));
    }

    @Test
    void nullRightReferenceMatchesJvm() {
        assertThat(nativeRun("right-null")).isEqualTo(jvmRun("right-null"));
    }

    @Test
    void distinctEmptyArraysMatchJvm() {
        assertThat(nativeRun("empty")).isEqualTo(jvmRun("empty"));
    }

    @Test
    void differentLengthsMatchJvm() {
        assertThat(nativeRun("length")).isEqualTo(jvmRun("length"));
    }

    @Test
    void equalSignedValuesMatchJvm() {
        assertThat(nativeRun("equal-signed")).isEqualTo(jvmRun("equal-signed"));
    }

    @Test
    void differentValuesMatchJvm() {
        assertThat(nativeRun("different")).isEqualTo(jvmRun("different"));
    }

    @Test
    void argumentsEvaluateLeftToRight() {
        assertThat(nativeRun("order")).isEqualTo(jvmRun("order"));
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
