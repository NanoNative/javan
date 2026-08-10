package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliBooleanParseIntegrationTest extends CliIntegrationSupport {
    @Test
    void runtimeValuesBuildAndMatchExactJvmParsing() throws Exception {
        final Path project = project("boolean-parse-runtime-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = {
                        null,
                        "",
                        "t",
                        "tr",
                        "tru",
                        "true",
                        "TRUE",
                        "TrUe",
                        "xrue",
                        "txue",
                        "trxe",
                        "trux",
                        "truex",
                        " true",
                        "true ",
                        "false",
                        "yes"
                    };
                    for (int index = 0; index < values.length; index++) {
                        System.out.println(Boolean.parseBoolean(values[index]));
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-parse-runtime-values").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("""
            false
            false
            false
            false
            false
            true
            true
            true
            false
            false
            false
            false
            false
            false
            false
            false
            false
            """);
    }
}
