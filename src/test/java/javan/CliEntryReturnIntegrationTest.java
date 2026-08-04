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
final class CliEntryReturnIntegrationTest extends CliIntegrationSupport {
    @Test
    void earlyVoidReturnStopsNativeEntryExecution() throws Exception {
        final Path project = project("entry-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("before");
                    if (args.length == 0) {
                        return;
                    }
                    System.out.println("after");
                }
            }
            """);
        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(project, List.of(project.resolve(".javan/bin/entry-return").toString())))
            .isEqualTo(new ProcessResult(0, jvmOutput, ""));
    }
}
