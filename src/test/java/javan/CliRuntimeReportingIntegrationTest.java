package javan;

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
final class CliRuntimeReportingIntegrationTest extends CliIntegrationSupport {
    @Test
    void reportShowsReachableNetworkRuntimeModuleNamesAfterSocketCheck() throws Exception {
        final Path project = project("unsupported-network-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    new java.net.Socket();
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());
        final CliRun report = run(tempDir, "report", project.toString());

        assertThat(check.exitCode()).isZero();
        assertThat(report.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/report.md"))).contains(
            "reachableRuntimeModuleNames: `core, network, socket`",
            "reachableRuntimeModules: `3`"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "\"reachableRuntimeModuleNames\": \"core, network, socket\"",
            "\"reachableRuntimeModules\": 3"
        );
    }

    @Test
    void buildRejectsReachableDisabledRuntimeModuleBeforeCodegen() throws Exception {
        final Path project = project("disabled-time-build");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            disabled = ["time"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.currentTimeMillis());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(project.resolve(".javan/generated")).doesNotExist();
        assertThat(run.stderr()).contains("error[JAVAN060]", "time");
    }

    @Test
    void checkReportsUnusedDisabledRuntimeModule() throws Exception {
        final Path project = project("disabled-unused-check");
        Files.writeString(project.resolve("javan.toml"), """
            [build.runtime]
            disabled = ["thread-profiling"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("small");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"disabledRuntimeModules\": [\"thread-profiling\"]",
            "\"disabledReachableRuntimeModules\": []",
            "\"disabledUnusedRuntimeModules\": [\"thread-profiling\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAndReportExposeReadyRuntimeProfilingWhenRequested() throws Exception {
        final Path project = project("runtime-profiling-requested");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            profiling = true
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("profile");
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());
        final CliRun report = run(tempDir, "report", project.toString());

        assertThat(check.exitCode()).isZero();
        assertThat(report.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-profiling.json"))).contains(
            "\"status\": \"ready\"",
            "\"requested\": true",
            "\"enabled\": true",
            "\"collectionState\": \"linked-not-run\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"profilingSupported\": true",
            "\"profilingCollected\": false"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- profilingSupported: `true`",
            "- profilingCollected: `false`",
            "Virtual-thread profiling hooks are linked through runtime-profiling.*, but the current run has not collected counters yet."
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.md"))).contains(
            "| `runtime-profiling` | present |",
            "status: `ready`",
            "requested: `true`",
            "enabled: `true`",
            "collectionState: `linked-not-run`"
        );
    }

    @Test
    void runCollectsRuntimeProfilingThreadCountersWhenRequested() throws Exception {
        final Path project = project("runtime-profiling-run");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            profiling = true
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.startVirtualThread(new Task());
                    worker.join();
                    System.out.println("profiled");
                }

                private static final class Task implements Runnable {
                    @Override
                    public void run() {
                        System.out.print("");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Built:", "profiled");
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-profiling.json"))).contains(
            "\"status\": \"collected\"",
            "\"requested\": true",
            "\"enabled\": true",
            "\"collectionState\": \"collected\"",
            "\"platformThreadObjectsCreated\": 1",
            "\"virtualThreadObjectsCreated\": 1",
            "\"threadStartCalls\": 1",
            "\"threadCompletions\": 1",
            "\"threadJoinCalls\": 1"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-profiling.md"))).contains(
            "- status: `collected`",
            "- platformThreadObjectsCreated: `1`",
            "- virtualThreadObjectsCreated: `1`",
            "- threadStartCalls: `1`",
            "- threadJoinCalls: `1`"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"profilingSupported\": true",
            "\"profilingCollected\": true"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- profilingSupported: `true`",
            "- profilingCollected: `true`",
            "Virtual-thread profiling counters are collected through runtime-profiling.* for the current host-thread-backed slice."
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.md"))).contains(
            "| `runtime-profiling` | present |",
            "status: `collected`",
            "platformThreadObjectsCreated: `1`",
            "virtualThreadObjectsCreated: `1`",
            "| `virtual-threads` | present |",
            "profilingCollected: `true`"
        );
    }

    @Test
    void runKeepsRuntimeProfilingDisabledWhenThreadProfilingModuleIsBlocked() throws Exception {
        final Path project = project("runtime-profiling-thread-module-blocked");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            profiling = true
            disabled = ["thread-profiling"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("blocked");
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-profiling.json"))).contains(
            "\"status\": \"disabled\"",
            "\"collectionState\": \"disabled-by-module\"",
            "\"disabledProfilingModules\": [\"thread-profiling\"]"
        );
    }

    @Test
    void runKeepsRuntimeProfilingDisabledWhenLiveProfilingModuleIsBlocked() throws Exception {
        final Path project = project("runtime-profiling-live-module-blocked");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            profiling = true
            disabled = ["live-profiling"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("blocked");
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-profiling.json"))).contains(
            "\"status\": \"disabled\"",
            "\"collectionState\": \"disabled-by-module\"",
            "\"disabledProfilingModules\": [\"live-profiling\"]"
        );
    }

    @Test
    void unknownProfileFailsAtCliBoundary() {
        final CliRun run = run(tempDir, "check", "--profile", "enterprise");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("error[JAVAN900]: Unsupported profile: enterprise");
    }

    @Test
    void runExecutesNativeExecutable() throws Exception {
        final Path project = project("run");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("run-native");
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("run-native");
    }
}
