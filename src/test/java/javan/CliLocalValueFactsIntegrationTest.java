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
final class CliLocalValueFactsIntegrationTest extends CliIntegrationSupport {
    @Test
    void releaseFactsPreserveJvmBehaviorAndExplainRemovedGuards() throws Exception {
        final Path project = project("local-value-facts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String text = "abc";
                    Objects.requireNonNull(text);
                    final int[] values = new int[4];
                    final int selected = args.length == 0 ? 1 : 3;
                    if (selected > 5) {
                        System.out.println(99);
                    }
                    System.out.println(text.length());
                    System.out.println(values.length);
                    System.out.println(selected);
                }
            }
            """);
        final String jvm = runJvm(project, "com.acme.Main");

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/local-value-facts");
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo(jvm);
        final String report = Files.readString(project.resolve(".javan/reports/optimizations.json"));
        assertThat(report).contains(
            "\"redundantNullChecks\": 1",
            "\"kind\": \"null-check\"",
            "\"kind\": \"branch\"",
            "\"exactTypes\":",
            "\"arrayLengths\":",
            "\"stringLengths\":"
        );
    }
}
