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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliEscapeClassificationIntegrationTest extends CliIntegrationSupport {
    @Test
    void reportsEscapeScopesWithoutChangingJvmBehavior() throws Exception {
        final Path project = project("escape-classification");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static int[] saved;

                private Main() {
                }

                public static void main(final String[] args) {
                    int[] local = new int[1];
                    local[0] = 7;
                    System.out.println(local[0]);

                    int[] argument = new int[2];
                    System.out.println(length(argument));
                    saved = create();
                    System.out.println(saved.length);
                }

                private static int length(final int[] value) {
                    return value.length;
                }

                private static int[] create() {
                    return new int[3];
                }
            }
            """);
        final String jvm = runJvm(project, "com.acme.Main");

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/escape-classification");
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo(jvm);
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json"))).contains(
            "\"escapeAnalysis\"",
            "\"allocationSites\":",
            "\"noEscape\":",
            "\"argumentEscape\":",
            "\"globalEscape\":"
        );
    }
}
