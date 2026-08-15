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
final class CliMethodEffectsIntegrationTest extends CliIntegrationSupport {
    @Test
    void reportsMethodEffectsWithoutChangingJvmBehavior() throws Exception {
        final Path project = project("method-effects");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static int state;

                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(increment(1));
                    update();
                    System.out.println(state);
                }

                private static int increment(final int value) {
                    return value + 1;
                }

                private static void update() {
                    state = 3;
                }
            }
            """);
        final String jvm = runJvm(project, "com.acme.Main");

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/method-effects");
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo(jvm);
        final String report = Files.readString(project.resolve(".javan/reports/optimizations.json"));
        assertThat(method(report, "increment")).contains(
            "\"pure\": true",
            "\"mayThrow\": false",
            "\"writes\": false",
            "\"unknown\": false"
        );
        assertThat(method(report, "update")).contains(
            "\"pure\": false",
            "\"writes\": true",
            "\"unknown\": false"
        );
    }

    private static String method(final String report, final String name) {
        final int start = report.indexOf("\"owner\": \"com/acme/Main\", \"method\": \"" + name + "\"");
        assertThat(start).as("method effect for %s", name).isNotNegative();
        final int end = report.indexOf('}', start);
        return report.substring(start, end + 1);
    }
}
