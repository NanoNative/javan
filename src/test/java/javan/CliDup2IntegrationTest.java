package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliDup2IntegrationTest extends CliIntegrationSupport {
    @Test
    void intArrayCompoundAssignmentMatchesJvm() throws Exception {
        final Path project = project("dup2-int-array-compound-assignment");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = {7};
                    values[0] += 5;
                    System.out.println(values[0]);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup2-int-array-compound-assignment"))
            .isEqualTo(runJvm(project, "com.acme.Main"));
    }

    @Test
    void longPostIncrementMatchesJvm() throws Exception {
        final Path project = project("dup2-long-post-increment");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = 7L;
                    System.out.println(value++);
                    System.out.println(value);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup2-long-post-increment"))
            .isEqualTo(runJvm(project, "com.acme.Main"));
    }

    @Test
    void doublePostIncrementMatchesJvm() throws Exception {
        final Path project = project("dup2-double-post-increment");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    double value = 7.5d;
                    System.out.println(value++);
                    System.out.println(value);
                }
            }
            """);

        assertThat(buildAndRun(project, "dup2-double-post-increment"))
            .isEqualTo(runJvm(project, "com.acme.Main"));
    }

    @Test
    void branchSelectedArrayIndexIsEvaluatedOnce() throws Exception {
        final Path project = project("dup2-branch-selected-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static int calls;

                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = {7};
                    values[args.length == 0 ? index() : index()] += 5;
                    System.out.println(values[0] + ":" + calls);
                }

                private static int index() {
                    calls++;
                    return 0;
                }
            }
            """);

        assertThat(buildAndRun(project, "dup2-branch-selected-index"))
            .isEqualTo(runJvm(project, "com.acme.Main"));
    }

    private String buildAndRun(final Path project, final String name) {
        final CliRun build = run(tempDir, "build", project.toString());
        if (build.exitCode() != 0) {
            return build.stderr();
        }
        return process(project, java.util.List.of(project.resolve(".javan/bin").resolve(name).toString())).stdout();
    }
}
