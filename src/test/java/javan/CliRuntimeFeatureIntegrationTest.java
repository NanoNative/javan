package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliRuntimeFeatureIntegrationTest extends CliIntegrationSupport {
    @Test
    void checkRejectsReachableDisabledRuntimeModule() throws Exception {
        final Path project = project("disabled-time-check");
        Files.writeString(project.resolve("javan.toml"), """
            [build.runtime]
            disabled = ["time"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.nanoTime());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "error[JAVAN060]",
            "disabled runtime module is reachable",
            "time"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"disabledReachableRuntimeModules\": [\"time\"]",
            "\"status\": \"fail\""
        );
    }

    @Test
    void buildRejectsReachableDisabledFilesystemRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-filesystem-build", "filesystem", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.readString(Path.of("message.txt")));
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledCollectionsRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-collections-build", "collections", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = List.of("a", "b");
                    System.out.println(values.size());
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledMapsRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-maps-build", "maps", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>();
                    values.put("key", "value");
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledOptionalRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-optional-build", "optional", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.of("value").isPresent();
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledEnvironmentRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-environment-build", "environment", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.getProperty("java.version");
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledArraysRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-arrays-build", "arrays", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] source = new int[] {1};
                    final int[] target = new int[1];
                    System.arraycopy(source, 0, target, 0, 1);
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledStringsRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-strings-build", "strings", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    "value".length();
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledMathRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-math-build", "math", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Math.abs(args.length - 1);
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledIoRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-io-build", "io", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("value");
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledExceptionsRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-exceptions-build", "exceptions", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    new IllegalStateException("value").getMessage();
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledManagedHeapRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-managed-heap-build", "managed-heap", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Integer.valueOf(args.length).intValue();
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledProcessRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-process-build", "process", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.exit(0);
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledDurationTimeRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-duration-time-build", "time", """
            package com.acme;

            import java.time.Duration;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Duration.ofMillis(args.length).toMillis());
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledFileTimeTimeRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-file-time-build", "time", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.getLastModifiedTime(Path.of("message.txt")).toMillis());
                }
            }
            """);
    }

    @Test
    void buildRejectsReachableDisabledThreadsRuntimeModule() throws Exception {
        assertBuildRejectsDisabledRuntimeModule("disabled-threads-build", "threads", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.currentThread().interrupt();
                }
            }
            """);
    }

    @Test
    void checkAcceptsReachableThreadCallsAndReportsThreadRuntimeModules() throws Exception {
        final Path project = project("thread-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    current.interrupt();
                    System.out.println(current.isInterrupted());
                    System.out.println(Thread.interrupted());
                    System.out.println(current.isInterrupted());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"io\", \"strings\", \"threads\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAcceptsReachableThreadConstructionAndReportsThreadRuntimeModules() throws Exception {
        final Path project = project("thread-construction-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread plain = new Thread();
                    final Thread withTarget = new Thread(new Task());
                    System.out.println(plain != null);
                    System.out.println(withTarget != null);
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("unused");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"io\", \"strings\", \"threads\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAcceptsReachableThreadSleepAndReportsThreadRuntimeModules() throws Exception {
        final Path project = project("thread-sleep-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Thread.sleep(1L);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"threads\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void reachableThreadStartWritesThreadStartSiteCount() throws Exception {
        final Path project = project("thread-start-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread();
                    thread.start();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"threadStartSites\": 1",
            "\"threadStartMethods\": 1",
            "\"lifecycleMethods\": 0",
            "\"blockingMethods\": 0",
            "\"synchronizationMethods\": 0",
            "\"concurrencyRuntimeMethods\": 0",
            "\"unknownBlockingMethods\": 0",
            "\"unsupportedThreadTaskMethods\": 0",
            "\"tinyCpuTaskMethods\": 1",
            "\"ioSignalMethods\": 0",
            "\"taskRoots\": 1",
            "\"threadStartRoots\": 1",
            "\"methods\": [",
            "\"roots\": [",
            "\"method\": \"main([Ljava/lang/String;)V\"",
            "\"lifecycleRisks\": 0",
            "\"synchronizationRisks\": 0",
            "\"concurrencyRuntimeRisks\": 0",
            "\"rootKind\": \"THREAD_START\"",
            "\"hasLoop\": false",
            "\"classification\": \"TINY_CPU_TASK\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "- threadStartSites: `1`",
            "- threadStartMethods: `1`",
            "- lifecycleMethods: `0`",
            "- blockingMethods: `0`",
            "- synchronizationMethods: `0`",
            "- concurrencyRuntimeMethods: `0`",
            "- unknownBlockingMethods: `0`",
            "- unsupportedThreadTaskMethods: `0`",
            "- tinyCpuTaskMethods: `1`",
            "- ioSignalMethods: `0`",
            "- taskRoots: `1`",
            "- threadStartRoots: `1`",
            "## Task Roots",
            "`com/acme/Main#main([Ljava/lang/String;)V`: rootKind=`THREAD_START`, classification=`TINY_CPU_TASK`, threadStartSites=`1`, blockingWaits=`0`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
            "## Reachable Thread Methods",
            "`com/acme/Main#main([Ljava/lang/String;)V`: threadStartSites=`1`, lifecycleRisks=`0`, blockingWaits=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, sleepWaits=`0`, joinWaits=`0`, estimatedInstructions=`7`, allocationSites=`1`, ioCallSites=`0`, hasLoop=`false`, classification=`TINY_CPU_TASK`"
        );
    }

    @Test
    void reachableThreadSleepWritesBlockingThreadWarning() throws Exception {
        final Path project = project("thread-sleep-blocking-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Thread.sleep(1L);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN178]");
        assertThat(run.stdout()).contains("Thread.sleep(long)");
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"diagnostics\": 1",
            "\"warnings\": 1",
            "\"blocking\": 1",
            "\"threadStartSites\": 0",
            "\"threadStartMethods\": 0",
            "\"lifecycleMethods\": 0",
            "\"blockingMethods\": 1",
            "\"synchronizationMethods\": 0",
            "\"concurrencyRuntimeMethods\": 0",
            "\"unknownBlockingMethods\": 0",
            "\"unsupportedThreadTaskMethods\": 0",
            "\"sleepWaits\": 1",
            "\"joinWaits\": 0",
            "\"blockingTaskMethods\": 1",
            "\"ioSignalMethods\": 0",
            "\"taskRoots\": 1",
            "\"blockingRoots\": 1",
            "\"methods\": [",
            "\"roots\": [",
            "\"method\": \"main([Ljava/lang/String;)V\"",
            "\"lifecycleRisks\": 0",
            "\"synchronizationRisks\": 0",
            "\"concurrencyRuntimeRisks\": 0",
            "\"classification\": \"BLOCKING_WAIT\"",
            "\"rootKind\": \"BLOCKING_WAIT\"",
            "\"code\": \"JAVAN178\"",
            "\"subject\": \"Thread.sleep(long)\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "# Thread Analysis",
            "## warning[JAVAN178] reachable blocking wait",
            "- threadStartSites: `0`",
            "- threadStartMethods: `0`",
            "- lifecycleMethods: `0`",
            "- blockingMethods: `1`",
            "- synchronizationMethods: `0`",
            "- concurrencyRuntimeMethods: `0`",
            "- unknownBlockingMethods: `0`",
            "- unsupportedThreadTaskMethods: `0`",
            "- sleepWaits: `1`",
            "- joinWaits: `0`",
            "- blockingTaskMethods: `1`",
            "- ioSignalMethods: `0`",
            "- taskRoots: `1`",
            "- blockingRoots: `1`",
            "## Task Roots",
            "`com/acme/Main#main([Ljava/lang/String;)V`: rootKind=`BLOCKING_WAIT`, classification=`BLOCKING_WAIT`, threadStartSites=`0`, blockingWaits=`1`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
            "## Reachable Thread Methods",
            "`com/acme/Main#main([Ljava/lang/String;)V`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`1`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, sleepWaits=`1`, joinWaits=`0`, estimatedInstructions=`3`, allocationSites=`0`, ioCallSites=`0`, hasLoop=`false`, classification=`BLOCKING_WAIT`",
            "- category: `blocking`"
        );
    }

    @Test
    void checkAcceptsReachableEmptyThreadStartAndReportsThreadRuntimeModules() throws Exception {
        final Path project = project("thread-start-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread();
                    thread.start();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"threads\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAcceptsReachableEmptyThreadJoinAndReportsThreadRuntimeModules() throws Exception {
        final Path project = project("thread-join-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread();
                    thread.join();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"threads\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void reachableThreadJoinWritesBlockingThreadWarning() throws Exception {
        final Path project = project("thread-join-blocking-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread();
                    thread.join();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN178]");
        assertThat(run.stdout()).contains("Thread.join()");
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"diagnostics\": 1",
            "\"warnings\": 1",
            "\"blocking\": 1",
            "\"threadStartSites\": 0",
            "\"threadStartMethods\": 0",
            "\"lifecycleMethods\": 0",
            "\"blockingMethods\": 1",
            "\"synchronizationMethods\": 0",
            "\"concurrencyRuntimeMethods\": 0",
            "\"unknownBlockingMethods\": 0",
            "\"unsupportedThreadTaskMethods\": 0",
            "\"sleepWaits\": 0",
            "\"joinWaits\": 1",
            "\"blockingTaskMethods\": 1",
            "\"ioSignalMethods\": 0",
            "\"taskRoots\": 1",
            "\"blockingRoots\": 1",
            "\"methods\": [",
            "\"roots\": [",
            "\"method\": \"main([Ljava/lang/String;)V\"",
            "\"lifecycleRisks\": 0",
            "\"synchronizationRisks\": 0",
            "\"concurrencyRuntimeRisks\": 0",
            "\"classification\": \"BLOCKING_WAIT\"",
            "\"rootKind\": \"BLOCKING_WAIT\"",
            "\"code\": \"JAVAN178\"",
            "\"subject\": \"Thread.join()\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "# Thread Analysis",
            "## warning[JAVAN178] reachable blocking wait",
            "- threadStartSites: `0`",
            "- threadStartMethods: `0`",
            "- lifecycleMethods: `0`",
            "- blockingMethods: `1`",
            "- synchronizationMethods: `0`",
            "- concurrencyRuntimeMethods: `0`",
            "- unknownBlockingMethods: `0`",
            "- unsupportedThreadTaskMethods: `0`",
            "- sleepWaits: `0`",
            "- joinWaits: `1`",
            "- blockingTaskMethods: `1`",
            "- ioSignalMethods: `0`",
            "- taskRoots: `1`",
            "- blockingRoots: `1`",
            "## Task Roots",
            "`com/acme/Main#main([Ljava/lang/String;)V`: rootKind=`BLOCKING_WAIT`, classification=`BLOCKING_WAIT`, threadStartSites=`0`, blockingWaits=`1`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
            "## Reachable Thread Methods",
            "`com/acme/Main#main([Ljava/lang/String;)V`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`1`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, sleepWaits=`0`, joinWaits=`1`, estimatedInstructions=`7`, allocationSites=`1`, ioCallSites=`0`, hasLoop=`false`, classification=`BLOCKING_WAIT`",
            "- category: `blocking`"
        );
    }

    @Test
    void reachableRunnableBlockingWaitCollapsesToSingleThreadRoot() throws Exception {
        final Path project = project("thread-runnable-blocking-root-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread(new Worker());
                    thread.start();
                }
            }
            """);
        writeJava(project, "com.acme.Worker", """
            package com.acme;

            public final class Worker implements Runnable {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1L);
                    } catch (final InterruptedException exception) {
                        return;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"threadStartSites\": 1",
            "\"blockingTaskMethods\": 1",
            "\"taskRoots\": 1",
            "\"threadStartRoots\": 1",
            "\"blockingRoots\": 0",
            "\"methods\": [",
            "\"method\": \"main([Ljava/lang/String;)V\"",
            "\"method\": \"run()V\"",
            "\"rootKind\": \"THREAD_START\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "- taskRoots: `1`",
            "- threadStartRoots: `1`",
            "- blockingRoots: `0`",
            "## Task Roots",
            "`com/acme/Main#main([Ljava/lang/String;)V`: rootKind=`THREAD_START`",
            "## Reachable Thread Methods",
            "`com/acme/Worker#run()V`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`1`"
        );
    }

    @Test
    void reachableThreadStartLoopClassifiesCpuBoundThreadMethod() throws Exception {
        final Path project = project("thread-start-loop-cpu-bound-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    for (int index = 0; index < 2; index++) {
                        final Thread thread = new Thread();
                        thread.start();
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"threadStartSites\": 1",
            "\"cpuBoundTaskMethods\": 1",
            "\"ioSignalMethods\": 0",
            "\"taskRoots\": 1",
            "\"threadStartRoots\": 1",
            "\"hasLoop\": true",
            "\"rootKind\": \"THREAD_START\"",
            "\"classification\": \"CPU_BOUND\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "- cpuBoundTaskMethods: `1`",
            "- ioSignalMethods: `0`",
            "- taskRoots: `1`",
            "## Task Roots",
            "rootKind=`THREAD_START`, classification=`CPU_BOUND`, threadStartSites=`1`, blockingWaits=`0`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
            "classification=`CPU_BOUND`"
        );
    }

    @Test
    void reachableThreadStartWithPrintlnRecordsIoSignal() throws Exception {
        final Path project = project("thread-start-io-signal-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread();
                    thread.start();
                    System.out.println("io");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"threadStartSites\": 1",
            "\"ioBoundTaskMethods\": 1",
            "\"mixedTaskMethods\": 0",
            "\"ioSignalMethods\": 1",
            "\"threadStartRoots\": 1",
            "\"ioCallSites\": 1",
            "\"classification\": \"IO_BOUND\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "- ioBoundTaskMethods: `1`",
            "- mixedTaskMethods: `0`",
            "- ioSignalMethods: `1`",
            "rootKind=`THREAD_START`, classification=`IO_BOUND`, threadStartSites=`1`, blockingWaits=`0`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`1`",
            "ioCallSites=`1`, hasLoop=`false`, classification=`IO_BOUND`"
        );
    }

    @Test
    void nestedBlockingHelperDoesNotBecomeSeparateTaskRoot() throws Exception {
        final Path project = project("thread-nested-blocking-root-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    outer();
                }

                private static void outer() throws Exception {
                    Thread.sleep(1L);
                    inner();
                }

                private static void inner() throws Exception {
                    Thread.sleep(1L);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"blockingMethods\": 2",
            "\"sleepWaits\": 2",
            "\"taskRoots\": 1",
            "\"blockingRoots\": 1"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "- blockingMethods: `2`",
            "- sleepWaits: `2`",
            "- taskRoots: `1`",
            "## Task Roots",
            "`com/acme/Main#outer()V`: rootKind=`BLOCKING_WAIT`, classification=`BLOCKING_WAIT`, threadStartSites=`0`, blockingWaits=`1`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`",
            "## Reachable Thread Methods",
            "`com/acme/Main#inner()V`: threadStartSites=`0`, lifecycleRisks=`0`, blockingWaits=`1`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, sleepWaits=`1`, joinWaits=`0`, estimatedInstructions=`3`, allocationSites=`0`, ioCallSites=`0`, hasLoop=`false`, classification=`BLOCKING_WAIT`"
        ).doesNotContain(
            "`com/acme/Main#inner()V`: rootKind=`BLOCKING_WAIT`, classification=`BLOCKING_WAIT`, threadStartSites=`0`, blockingWaits=`1`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`"
        );
    }

    @Test
    void spawnedRunnableBlockingTaskRemainsSeparateTaskRoot() throws Exception {
        final Path project = project("thread-runnable-separate-root-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread(new Task());
                    thread.start();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1L);
                    } catch (final InterruptedException ignored) {
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"threadStartMethods\": 1",
            "\"blockingMethods\": 1",
            "\"taskRoots\": 1",
            "\"threadStartRoots\": 1",
            "\"blockingRoots\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/threads.md"))).contains(
            "- taskRoots: `1`",
            "`com/acme/Main#main([Ljava/lang/String;)V`: rootKind=`THREAD_START`, classification=`UNKNOWN`, threadStartSites=`1`, blockingWaits=`0`, lifecycleRisks=`0`, synchronizationRisks=`0`, concurrencyRuntimeRisks=`0`, ioCallSites=`0`"
        );
    }

    @Test
    void checkWritesVirtualThreadStatusReports() throws Exception {
        final Path project = project("virtual-thread-status-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"status\": \"partial\"",
            "\"runtimeSupported\": true",
            "\"profilingCollected\": false",
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"reachableIsVirtualSites\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedExecutorApis\": 0",
            "\"reasonCount\": 3"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "# Virtual Thread Analysis",
            "- status: `partial`",
            "- reachableVirtualStartSites: `0`",
            "- diagnosticSource: `platform-thread-analysis-plus-virtual-builder-executor-park-slice`"
        );
    }

    @Test
    void checkAcceptsReachableThreadIsAliveAndReportsThreadRuntimeModules() throws Exception {
        final Path project = project("thread-isalive-runtime-modules");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.currentThread().isAlive();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"threads\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void checkAcceptsReachableAtomicBooleanConstructorsAndGet() throws Exception {
        final Path project = project("atomic-boolean-runtime");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var ready = new java.util.concurrent.atomic.AtomicBoolean(true);
                    final var done = new java.util.concurrent.atomic.AtomicBoolean();
                    if (ready.get() && !done.get()) {
                        System.out.println("ok");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void checkAcceptsReachableAtomicIntegerConstructorsAndGet() throws Exception {
        final Path project = project("atomic-integer-runtime");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var eventCount = new java.util.concurrent.atomic.AtomicInteger(3);
                    final var ids = new java.util.concurrent.atomic.AtomicInteger();
                    if (eventCount.get() == 3 && ids.get() == 0) {
                        System.out.println("ok");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void checkRejectsReachableThirdPartyHttpServerDependencyAndReportsHttpRuntimeModules() throws Exception {
        final Path project = project("unsupported-third-party-http-server-dependency");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    com.thirdparty.http.HttpServer.create();
                }
            }
            """);
        writeJava(project, "com.thirdparty.http.HttpServer", """
            package com.thirdparty.http;

            public final class HttpServer {
                private HttpServer() {
                }

                public static com.sun.net.httpserver.HttpServer create() throws java.io.IOException {
                    return com.sun.net.httpserver.HttpServer.create(null, 0);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "error[JAVAN061]",
            "com/thirdparty/http/HttpServer",
            "create()Lcom/sun/net/httpserver/HttpServer;",
            "com/sun/net/httpserver/HttpServer.create",
            "network/http"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"reachableRuntimeModules\": [\"core\", \"http\", \"network\"]",
            "\"status\": \"pass\""
        );
    }
}
