package javan;

import javan.cli.Cli;
import javan.cli.Version;
import javan.reporting.RuntimeFootprintReports;
import javan.util.Files2;
import javan.util.Json;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliThreadRuntimeIntegrationTest extends CliIntegrationSupport {
    @Test
    void currentThreadInterruptStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("current-thread-interrupt-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    System.out.println(current.isInterrupted());
                    current.interrupt();
                    System.out.println(current.isInterrupted());
                    System.out.println(Thread.interrupted());
                    System.out.println(current.isInterrupted());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/current-thread-interrupt-state").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadIsAliveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-isalive");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread current = Thread.currentThread();
                    final Thread fresh = new Thread();
                    final Thread started = new Thread(new Task("task"));
                    System.out.println(current.isAlive());
                    System.out.println(fresh.isAlive());
                    System.out.println(started.isAlive());
                    started.start();
                    started.join();
                    System.out.println(started.isAlive());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final String value;

                public Task(final String value) {
                    this.value = value;
                }

                @Override
                public void run() {
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-isalive").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void startedThreadCurrentThreadIdentityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-current-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Holder holder = new Holder();
                    final Thread started = new Thread(new Task(holder));
                    holder.value = started;
                    started.start();
                    started.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final Holder holder;

                public Task(final Holder holder) {
                    this.holder = holder;
                }

                @Override
                public void run() {
                    System.out.println(Thread.currentThread() == holder.value);
                }
            }
            """);
        writeJava(project, "com.acme.Holder", """
            package com.acme;

            final class Holder {
                Thread value;
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-current-identity").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadStartReturnsBeforeJoinBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-start-before-join");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread started = new Thread(new Task());
                    started.start();
                    System.out.println(started.isAlive());
                    started.join();
                    System.out.println(started.isAlive());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    final long until = System.nanoTime() + 50_000_000L;
                    while (System.nanoTime() < until) {
                        // spin
                    }
                    System.out.println("worker");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-start-before-join").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void currentThreadSurvivesGcPressureBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("current-thread-root-gc-pressure");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    for (int index = 0; index < 4_000; index++) {
                        final String value = "v" + index;
                        if (value.length() < 0) {
                            throw new IllegalStateException(value);
                        }
                    }
                    current.interrupt();
                    System.out.println(current.isInterrupted());
                    System.out.println(Thread.currentThread() == current);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/current-thread-root-gc-pressure").toString()),
            Duration.ofSeconds(10),
            Map.of(
                "JAVAN_HEAP_LIMIT_BYTES", "65536",
                "JAVAN_GC_SAFEPOINT_INTERVAL", "1"
            )
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void startVirtualThreadReturnedThreadIsVirtualBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-returned-isvirtual");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.startVirtualThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("worker");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-returned-isvirtual").toString())).stdout().lines().toList())
            .containsExactlyInAnyOrderElementsOf(jvmOutput.lines().toList());
    }

    @Test
    void startVirtualThreadCurrentThreadIsVirtualBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-current-isvirtual");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.startVirtualThread(new Task());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-current-isvirtual").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void startVirtualThreadInheritsInheritableThreadLocalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-start-inheritable-threadlocal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InheritableThreadLocal<String> local = new InheritableThreadLocal<>();
                    local.set("main");
                    final Thread worker = Thread.startVirtualThread(new Task(local));
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final InheritableThreadLocal<String> local;

                public Task(final InheritableThreadLocal<String> local) {
                    this.local = local;
                }

                @Override
                public void run() {
                    System.out.println(local.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-start-inheritable-threadlocal").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.ofVirtual().start(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartInheritsInheritableThreadLocalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start-inheritable-threadlocal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final Thread worker = Thread.ofVirtual().start(new Task());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start-inheritable-threadlocal").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartWithExplicitInheritanceEnabledBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start-inheritable-threadlocal-enabled");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final Thread worker = Thread.ofVirtual().inheritInheritableThreadLocals(true).start(new Task());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start-inheritable-threadlocal-enabled").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartWithExplicitInheritanceDisabledBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start-inheritable-threadlocal-disabled");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final Thread worker = Thread.ofVirtual().inheritInheritableThreadLocals(false).start(new Task());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start-inheritable-threadlocal-disabled").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartViaStaticBuilderHelperBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start-static-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static Thread.Builder.OfVirtual builder() {
                    return Thread.ofVirtual();
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = builder().start(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start-static-helper").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadFactoryNewThreadInheritsInheritableThreadLocalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-factory-new-thread-inheritable-threadlocal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final ThreadFactory factory = Thread.ofVirtual().factory();
                    final Thread worker = factory.newThread(new Task());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-new-thread-inheritable-threadlocal").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadFactoryNewThreadWithExplicitInheritanceEnabledBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-factory-new-thread-inheritable-threadlocal-enabled");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final ThreadFactory factory = Thread.ofVirtual().inheritInheritableThreadLocals(true).factory();
                    final Thread worker = factory.newThread(new Task());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-new-thread-inheritable-threadlocal-enabled").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadFactoryNewThreadWithExplicitInheritanceDisabledBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-factory-new-thread-inheritable-threadlocal-disabled");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final ThreadFactory factory = Thread.ofVirtual().inheritInheritableThreadLocals(false).factory();
                    final Thread worker = factory.newThread(new Task());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-new-thread-inheritable-threadlocal-disabled").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadPerTaskExecutorSubmitInheritsInheritableThreadLocalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-executor-submit-inheritable-threadlocal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;

            public final class Main {
                static final InheritableThreadLocal<String> LOCAL = new InheritableThreadLocal<>();

                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    LOCAL.set("main");
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    executor.submit(new Task());
                    executor.close();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Main.LOCAL.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-executor-submit-inheritable-threadlocal").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartViaParameterizedStaticBuilderHelperBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start-parameterized-static-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static Thread.Builder.OfVirtual builder(final String name) {
                    return Thread.ofVirtual().name(name);
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = builder("helper-worker").start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start-parameterized-static-helper").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualBuilderAliasStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-alias-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final var builder = Thread.ofVirtual();
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-alias-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualTypedBuilderAliasStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-typed-alias-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual();
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-typed-alias-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualGenericBuilderAliasStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-generic-alias-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder builder = Thread.ofVirtual();
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-generic-alias-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.ofVirtual().name("x").start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameBuilderAliasStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-alias-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final var builder = Thread.ofVirtual().name("x");
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-alias-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualTypedNameBuilderAliasStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-typed-name-alias-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("x");
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-typed-name-alias-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualGenericNamedBuilderAliasStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-generic-named-alias-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder builder = Thread.ofVirtual().name("x");
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-generic-named-alias-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameCounterGenericBuilderAliasReuseBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-counter-generic-alias-reuse");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder builder = Thread.ofVirtual().name("worker-", 7);
                    final Thread first = builder.unstarted(new Task());
                    System.out.println(first.getName());
                    first.start();
                    first.join();
                    final Thread second = builder.start(new Task());
                    System.out.println(second.getName());
                    second.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-counter-generic-alias-reuse").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameCounterFactorySnapshotBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-counter-factory-snapshot");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("snap-", 1);
                    final ThreadFactory factory = builder.factory();
                    final Thread.Builder.OfVirtual renamed = builder.name("changed");
                    System.out.println(factory.newThread(new Task()).getName());
                    System.out.println(renamed.unstarted(new Task()).getName());
                    System.out.println(factory.newThread(new Task()).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-counter-factory-snapshot").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualDiscardedNameMutationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-discarded-name-mutation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual();
                    builder.name("changed");
                    final Thread worker = builder.start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-discarded-name-mutation").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualDiscardedNameCounterMutationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-discarded-name-counter-mutation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual();
                    builder.name("worker-", 2);
                    System.out.println(builder.unstarted(new Task()).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-discarded-name-counter-mutation").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactorySnapshotAfterDiscardedRenameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-factory-snapshot-discarded-rename");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("snap-", 1);
                    final ThreadFactory factory = builder.factory();
                    builder.name("changed");
                    System.out.println(factory.newThread(new Task()).getName());
                    System.out.println(builder.unstarted(new Task()).getName());
                    System.out.println(factory.newThread(new Task()).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-factory-snapshot-discarded-rename").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameAfterNameCounterBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-after-name-counter");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("alpha-", 7).name("beta");
                    System.out.println(builder.unstarted(new Task()).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-after-name-counter").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameCounterAfterNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-counter-after-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("gamma").name("delta-", 5);
                    System.out.println(builder.unstarted(new Task()).getName());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-counter-after-name").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualUnstartedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-unstarted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.ofVirtual().unstarted(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-unstarted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualBuilderAliasUnstartedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-alias-unstarted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final var builder = Thread.ofVirtual();
                    final Thread worker = builder.unstarted(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-alias-unstarted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualGenericBuilderAliasUnstartedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-generic-alias-unstarted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder builder = Thread.ofVirtual();
                    final Thread worker = builder.unstarted(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-generic-alias-unstarted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNameBuilderAliasUnstartedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-alias-unstarted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final var builder = Thread.ofVirtual().name("x");
                    final Thread worker = builder.unstarted(new Task());
                    System.out.println(worker.getName());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-alias-unstarted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactoryNewThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-factory-new-thread");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.ofVirtual().factory().newThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-factory-new-thread").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactoryViaStaticHelperBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-factory-static-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                private static ThreadFactory factory() {
                    return Thread.ofVirtual().factory();
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = factory().newThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-factory-static-helper").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactoryViaParameterizedStaticHelperBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-factory-parameterized-static-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                private static ThreadFactory factory(final String prefix, final long start) {
                    return Thread.ofVirtual().name(prefix, start).factory();
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = factory("helper-", 3L).newThread(new Task());
                    System.out.println(worker.getName());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-factory-parameterized-static-helper").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualStartWithPrebuiltRunnableAliasBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-start-prebuilt-runnable-alias");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Runnable task = new Task();
                    final Thread worker = Thread.ofVirtual().start(task);
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-start-prebuilt-runnable-alias").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualUnstartedWithPrebuiltRunnableAliasBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-unstarted-prebuilt-runnable-alias");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Runnable task = new Task();
                    final Thread worker = Thread.ofVirtual().unstarted(task);
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-unstarted-prebuilt-runnable-alias").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactoryWithPrebuiltRunnableAliasBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-factory-prebuilt-runnable-alias");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Runnable task = new Task();
                    final Thread worker = Thread.ofVirtual().factory().newThread(task);
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-factory-prebuilt-runnable-alias").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactoryAliasNewThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-factory-alias-new-thread");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ThreadFactory factory = Thread.ofVirtual().factory();
                    final Thread worker = factory.newThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-factory-alias-new-thread").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualNamedFactoryNewThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-named-factory-new-thread");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ThreadFactory factory = Thread.ofVirtual().name("x").factory();
                    final Thread worker = factory.newThread(new Task());
                    System.out.println(worker.getName());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-named-factory-new-thread").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualGenericBuilderFactoryNewThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-generic-factory-new-thread");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread.Builder builder = Thread.ofVirtual();
                    final ThreadFactory factory = builder.factory();
                    final Thread worker = factory.newThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-generic-factory-new-thread").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualObjectAliasCheckcastStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-object-alias-checkcast-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Object raw = Thread.ofVirtual();
                    final Thread worker = ((Thread.Builder.OfVirtual) raw).start(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-object-alias-checkcast-start").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualFactoryObjectAliasCheckcastNewThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-factory-object-alias-checkcast-new-thread");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Object raw = Thread.ofVirtual().factory();
                    final Thread worker = ((ThreadFactory) raw).newThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.start();
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-object-alias-checkcast-new-thread").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorFromObjectAliasCheckcastBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-executor-object-alias-checkcast");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.ThreadFactory;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object raw = Thread.ofVirtual().factory();
                    final ExecutorService executor = Executors.newThreadPerTaskExecutor((ThreadFactory) raw);
                    executor.execute(new Task());
                    executor.close();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().isVirtual());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-executor-object-alias-checkcast").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void buildWritesVirtualThreadReachableCounts() throws Exception {
        final Path project = project("virtual-thread-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.startVirtualThread(new Task());
                    System.out.println(worker.isVirtual());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("worker");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 1",
            "\"reachableVirtualStartMethods\": 1",
            "\"reachableIsVirtualSites\": 1",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedExecutorApis\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- reachableVirtualStartSites: `1`",
            "- reachableVirtualStartMethods: `1`",
            "- reachableIsVirtualSites: `1`"
        );
    }

    @Test
    void checkWritesVirtualThreadBuilderReachableCounts() throws Exception {
        final Path project = project("virtual-thread-builder-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.ofVirtual().start(new Task());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("worker");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 1",
            "\"reachableVirtualStartMethods\": 1",
            "\"reachableIsVirtualSites\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedExecutorApis\": 0"
        );
    }

    @Test
    void checkWritesVirtualThreadBuilderAliasReachableCounts() throws Exception {
        final Path project = project("virtual-thread-builder-alias-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final var builder = Thread.ofVirtual();
                    final Thread worker = builder.start(new Task());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("worker");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 1",
            "\"reachableVirtualStartMethods\": 1",
            "\"reachableIsVirtualSites\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedExecutorApis\": 0"
        );
    }

    @Test
    void checkWritesVirtualThreadNamedBuilderReachableCounts() throws Exception {
        final Path project = project("virtual-thread-builder-name-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.ofVirtual().name("x").start(new Task());
                    System.out.println(worker.getName());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("worker");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 1",
            "\"reachableVirtualStartMethods\": 1",
            "\"reachableIsVirtualSites\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedBuilderApisReachable\": 0",
            "\"unsupportedBuilderApisUnreachable\": 0",
            "\"unsupportedExecutorApis\": 0",
            "\"unsupportedExecutorApisReachable\": 0",
            "\"unsupportedExecutorApisUnreachable\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- unsupportedBuilderApis: `0`",
            "- unsupportedBuilderApisReachable: `0`",
            "- unsupportedBuilderApisUnreachable: `0`",
            "- unsupportedExecutorApis: `0`"
        );
    }

    @Test
    void discardedThreadOfVirtualFactoryWritesCleanBuilderApiCounts() throws Exception {
        final Path project = project("virtual-thread-builder-factory-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.ofVirtual().factory();
                    System.out.println("ok");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedBuilderApisReachable\": 0",
            "\"unsupportedBuilderApisUnreachable\": 0",
            "\"unsupportedExecutorApis\": 0",
            "\"unsupportedExecutorApisReachable\": 0",
            "\"unsupportedExecutorApisUnreachable\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- unsupportedBuilderApis: `0`",
            "- unsupportedBuilderApisReachable: `0`",
            "- unsupportedBuilderApisUnreachable: `0`",
            "- unsupportedExecutorApis: `0`"
        );
    }

    @Test
    void unreachableDiscardedThreadOfVirtualFactoryWritesCleanBuilderApiCounts() throws Exception {
        final Path project = project("virtual-thread-builder-factory-unreachable-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead() {
                    Thread.ofVirtual().factory();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedBuilderApisReachable\": 0",
            "\"unsupportedBuilderApisUnreachable\": 0",
            "\"unsupportedExecutorApis\": 0",
            "\"unsupportedExecutorApisReachable\": 0",
            "\"unsupportedExecutorApisUnreachable\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- unsupportedBuilderApis: `0`",
            "- unsupportedBuilderApisReachable: `0`",
            "- unsupportedBuilderApisUnreachable: `0`",
            "- unsupportedExecutorApis: `0`"
        );
    }

    @Test
    void virtualThreadExecutorFactoryBuildsAndWritesCleanExecutorCounts() throws Exception {
        final Path project = project("virtual-thread-executor-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Executors.newVirtualThreadPerTaskExecutor();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedBuilderApisReachable\": 0",
            "\"unsupportedBuilderApisUnreachable\": 0",
            "\"unsupportedExecutorApis\": 0",
            "\"unsupportedExecutorApisReachable\": 0",
            "\"unsupportedExecutorApisUnreachable\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- unsupportedBuilderApis: `0`",
            "- unsupportedExecutorApis: `0`",
            "- unsupportedExecutorApisReachable: `0`",
            "- unsupportedExecutorApisUnreachable: `0`"
        );
    }

    @Test
    void unreachableVirtualThreadExecutorFactoryWritesCleanExecutorCounts() throws Exception {
        final Path project = project("virtual-thread-executor-unreachable-report-counts");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead() {
                    Executors.newVirtualThreadPerTaskExecutor();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.json"))).contains(
            "\"reachableApiScan\": \"reachable-method-scan\"",
            "\"reachableVirtualStartSites\": 0",
            "\"reachableVirtualStartMethods\": 0",
            "\"unsupportedBuilderApis\": 0",
            "\"unsupportedBuilderApisReachable\": 0",
            "\"unsupportedBuilderApisUnreachable\": 0",
            "\"unsupportedExecutorApis\": 0",
            "\"unsupportedExecutorApisReachable\": 0",
            "\"unsupportedExecutorApisUnreachable\": 0"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/virtual-threads.md"))).contains(
            "- unsupportedBuilderApis: `0`",
            "- unsupportedExecutorApis: `0`",
            "- unsupportedExecutorApisReachable: `0`",
            "- unsupportedExecutorApisUnreachable: `0`"
        );
    }

    @Test
    void virtualThreadPerTaskExecutorExecuteAndCloseBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-executor-execute-close");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var executor = Executors.newVirtualThreadPerTaskExecutor();
                    executor.execute(new Task());
                    executor.close();
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-executor-execute-close").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadPerTaskExecutorWithVirtualFactoryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-factory-executor-execute");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
                    executor.execute(new Task());
                    executor.shutdown();
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    final long end = System.nanoTime() + 25_000_000L;
                    while (System.nanoTime() < end) {
                        // keep the task alive long enough to make shutdown ordering deterministic
                    }
                    System.out.println("task");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-executor-execute").toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout().lines().toList()).containsExactlyInAnyOrder("done", "task");
    }

    @Test
    void discardedThreadOfVirtualBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-reject");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.ofVirtual();
                    System.out.println("ok");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-reject").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOfVirtualBuilderAliasObjectPrintAndToStringBuildsWithStableShape() throws Exception {
        final Path project = project("virtual-thread-builder-alias-object-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var builder = Thread.ofVirtual();
                    System.out.println(builder);
                    System.out.println(builder.toString());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final String[] lines = process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-alias-object-print").toString()))
            .stdout()
            .trim()
            .split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("java.lang.ThreadBuilders$VirtualThreadBuilder@");
        assertThat(lines[1]).startsWith("java.lang.ThreadBuilders$VirtualThreadBuilder@");
    }

    @Test
    void threadOfVirtualNameBuilderEqualsAndHashCodeSemanticsMatchJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-alias-equals-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var builder = Thread.ofVirtual().name("x");
                    System.out.println(builder.equals(builder));
                    System.out.println(builder.equals(null));
                    System.out.println(builder.hashCode() == builder.hashCode());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-alias-equals-hash").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadFactoryPrintAndToStringBuildsWithStableShape() throws Exception {
        final Path project = project("virtual-thread-factory-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var factory = Thread.ofVirtual().factory();
                    System.out.println(factory);
                    System.out.println(factory.toString());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final String[] lines = process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-print").toString()))
            .stdout()
            .trim()
            .split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("java.lang.ThreadBuilders$VirtualThreadFactory@");
        assertThat(lines[1]).startsWith("java.lang.ThreadBuilders$VirtualThreadFactory@");
    }

    @Test
    void virtualThreadFactoryEqualsAndHashCodeSemanticsMatchJvmOutput() throws Exception {
        final Path project = project("virtual-thread-factory-equals-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var factory = Thread.ofVirtual().factory();
                    System.out.println(factory.equals(factory));
                    System.out.println(factory.equals(null));
                    System.out.println(factory.hashCode() == factory.hashCode());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-factory-equals-hash").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorPrintAndToStringBuildsWithStableShape() throws Exception {
        final Path project = project("virtual-thread-executor-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var executor = Executors.newVirtualThreadPerTaskExecutor();
                    System.out.println(executor);
                    System.out.println(executor.toString());
                    executor.close();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final String[] lines = process(project, List.of(project.resolve(".javan/bin/virtual-thread-executor-print").toString()))
            .stdout()
            .trim()
            .split("\\R");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("java.util.concurrent.ThreadPerTaskExecutor@");
        assertThat(lines[1]).startsWith("java.util.concurrent.ThreadPerTaskExecutor@");
    }

    @Test
    void virtualThreadExecutorEqualsAndHashCodeSemanticsMatchJvmOutput() throws Exception {
        final Path project = project("virtual-thread-executor-equals-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var executor = Executors.newVirtualThreadPerTaskExecutor();
                    final var other = Executors.newVirtualThreadPerTaskExecutor();
                    System.out.println(executor.equals(executor));
                    System.out.println(executor.equals(other));
                    System.out.println(executor.hashCode() == executor.hashCode());
                    executor.close();
                    other.close();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-executor-equals-hash").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadBuilderGetClassStillFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("virtual-thread-builder-get-class-reject");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final var builder = Thread.ofVirtual();
                    System.out.println(builder.getClass().getName());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("Thread.ofVirtual()");
        assertThat(run.stderr()).contains("unsupported reachable concurrency runtime API");
    }

    @Test
    void discardedThreadOfVirtualNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-discard");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.ofVirtual().name("x");
                    System.out.println("ok");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-discard").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void discardedThreadOfVirtualFactoryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("virtual-thread-builder-name-reject");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.ofVirtual().factory();
                    System.out.println("ok");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/virtual-thread-builder-name-reject").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadYieldBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-yield");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("before");
                    Thread.yield();
                    System.out.println("after");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-yield").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadOnSpinWaitBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-on-spin-wait");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("before");
                    Thread.onSpinWait();
                    System.out.println("after");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-on-spin-wait").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadPriorityDefaultsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-priority-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Thread.currentThread().getPriority());
                    System.out.println(new Thread().getPriority());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-priority-default").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadPrioritySetGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-priority-set-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread worker = new Thread();
                    worker.setPriority(7);
                    System.out.println(worker.getPriority());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-priority-set-get").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadPriorityInheritedAtConstructionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-priority-inherited-construction");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    current.setPriority(8);
                    final Thread child = new Thread();
                    System.out.println(child.getPriority());
                    current.setPriority(5);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-priority-inherited-construction").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadGetIdBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-get-id");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    final Thread first = new Thread();
                    final Thread second = new Thread();
                    System.out.println(current.getId() > 0L);
                    System.out.println(first.getId() > 0L);
                    System.out.println(second.getId() > 0L);
                    System.out.println(first.getId() != second.getId());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-get-id").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadThreadIdBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-thread-id");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    final Thread fresh = new Thread("worker");
                    System.out.println(current.threadId() > 0L);
                    System.out.println(current.threadId() == current.getId());
                    System.out.println(fresh.threadId() > 0L);
                    System.out.println(fresh.threadId() == fresh.getId());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-thread-id").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadSleepDurationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-sleep-duration");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.time.Duration;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Thread.sleep(Duration.ofMillis(20L));
                    System.out.println("awake");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-sleep-duration").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadJoinDurationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-join-duration");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.time.Duration;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = new Thread(new Task(), "join-duration-worker");
                    worker.start();
                    System.out.println(worker.join(Duration.ofMillis(200L)));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-join-duration").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadSetNameBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-set-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread worker = new Thread();
                    worker.setName("renamed-worker");
                    System.out.println(worker.getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-set-name").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadSetNameNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("thread-set-name-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread worker = new Thread();
                    worker.setName(null);
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/thread-set-name-null").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains("null Thread name");
    }

    @Test
    void threadSleepUninterruptedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-sleep-uninterrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final long start = System.nanoTime();
                    Thread.sleep(25L);
                    final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
                    System.out.println(elapsedMillis >= 20L);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-sleep-uninterrupted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadSleepMillisNanosUninterruptedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-sleep-millis-nanos-uninterrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final long start = System.nanoTime();
                    Thread.sleep(20L, 500_000);
                    final long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
                    System.out.println(elapsedMillis >= 20L);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-sleep-millis-nanos-uninterrupted").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadSleepInterruptedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-sleep-interrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Thread.currentThread().interrupt();
                    try {
                        Thread.sleep(25L);
                        System.out.println("ok");
                    } catch (final InterruptedException interrupted) {
                        System.out.println(interrupted.getMessage() == null);
                        System.out.println(Thread.currentThread().isInterrupted());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-sleep-interrupted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadSleepMillisNanosInterruptedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-sleep-millis-nanos-interrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Thread.currentThread().interrupt();
                    try {
                        Thread.sleep(25L, 500_000);
                        System.out.println("ok");
                    } catch (final InterruptedException interrupted) {
                        System.out.println(interrupted.getMessage() == null);
                        System.out.println(Thread.currentThread().isInterrupted());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-sleep-millis-nanos-interrupted").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadSleepInterruptedByWorkerBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-sleep-runtime-interrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread current = Thread.currentThread();
                    final Thread interrupter = new Thread(new InterruptTask(current));
                    interrupter.start();
                    try {
                        Thread.sleep(500L);
                        System.out.println("ok");
                    } catch (final InterruptedException interrupted) {
                        System.out.println(interrupted.getMessage() == null);
                        System.out.println(Thread.currentThread().isInterrupted());
                    }
                    interrupter.join();
                }
            }
            """);
        writeJava(project, "com.acme.InterruptTask", """
            package com.acme;

            public final class InterruptTask implements Runnable {
                private final Thread target;

                public InterruptTask(final Thread target) {
                    this.target = target;
                }

                @Override
                public void run() {
                    final long until = System.nanoTime() + 25_000_000L;
                    while (System.nanoTime() < until) {
                        // spin
                    }
                    target.interrupt();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-sleep-runtime-interrupted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadConstructionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-construction");
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
                    System.out.println(plain.isInterrupted());
                    System.out.println(withTarget.isInterrupted());
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

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-construction").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadStringConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-string-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread("named-worker");
                    System.out.println(thread.getName());
                    System.out.println(thread.isInterrupted());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-string-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadRunnableStringConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-runnable-string-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread(new Task(), "named-task");
                    System.out.println(thread.getName());
                    thread.start();
                    thread.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-runnable-string-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadStringConstructorNullFailsClearlyAtRuntime() throws Exception {
        final Path project = project("thread-string-constructor-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread((String) null);
                    System.out.println(thread);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/thread-string-constructor-null").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains("null Thread name");
    }

    @Test
    void threadSubclassAllocationFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-subclass-allocation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new WorkerThread();
                    thread.start();
                    thread.join();
                }
            }
            """);
        writeJava(project, "com.acme.WorkerThread", """
            package com.acme;

            public final class WorkerThread extends Thread {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN074");
        assertThat(run.stderr()).contains("Thread subclass allocation is not supported");
    }

    @Test
    void currentThreadStartFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-current-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.currentThread().start();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN075");
        assertThat(run.stderr()).contains("Thread.currentThread().start()");
    }

    @Test
    void aliasedCurrentThreadStartFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-current-start-alias");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread current = Thread.currentThread();
                    current.start();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN075");
        assertThat(run.stderr()).contains("Thread.currentThread() alias on local");
    }

    @Test
    void currentThreadJoinFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-current-join");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Thread.currentThread().join();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN075");
        assertThat(run.stderr()).contains("Thread.currentThread().join()");
    }

    @Test
    void aliasedCurrentThreadJoinFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-current-join-alias");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread current = Thread.currentThread();
                    current.join();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN075");
        assertThat(run.stderr()).contains("Thread.currentThread() alias on local");
    }

    @Test
    void duplicateThreadStartOnSameLocalFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-duplicate-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread();
                    thread.start();
                    thread.start();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN075");
        assertThat(run.stderr()).contains("duplicate Thread.start() on local");
    }

    @Test
    void synchronizedMainFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-synchronized-main");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static synchronized void main(final String[] args) {
                    System.out.println("sync");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN076");
        assertThat(run.stderr()).contains("synchronized method");
    }

    @Test
    void unreachableSynchronizedMethodWarnsClearly() throws Exception {
        final Path project = project("thread-synchronized-unreachable");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static synchronized void dead() {
                    System.out.println("sync");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN176]");
        assertThat(run.stdout()).contains("synchronized method");
    }

    @Test
    void synchronizedBlockFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-synchronized-block");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    synchronized (Main.class) {
                        System.out.println("sync");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN076");
        assertThat(run.stderr()).contains("synchronized block");
        assertThat(run.stderr()).doesNotContain("JAVAN014", "JAVAN030");
    }

    @Test
    void unreachableSynchronizedBlockWarnsClearly() throws Exception {
        final Path project = project("thread-synchronized-block-unreachable");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead() {
                    synchronized (Main.class) {
                        System.out.println("sync");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN176]");
        assertThat(run.stdout()).contains("synchronized block");
        assertThat(run.stdout()).doesNotContain("warning[JAVAN114]", "warning[JAVAN130]");
    }

    @Test
    void synchronizedBlockDoesNotHideUnrelatedCatchFailure() throws Exception {
        final Path project = project("thread-synchronized-block-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        synchronized (Main.class) {
                            System.out.println("sync");
                        }
                    } catch (RuntimeException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN076");
        assertThat(run.stderr()).contains("synchronized block");
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.json"))).contains("JAVAN014");
    }

    @Test
    void objectWaitFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-object-wait");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    new Object().wait();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN076");
        assertThat(run.stderr()).contains("Object.wait()");
        assertThat(run.stderr()).doesNotContain("JAVAN031");
    }

    @Test
    void objectWaitWithInterruptedCatchFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-object-wait-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object lock = new Object();
                    try {
                        lock.wait();
                    } catch (InterruptedException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN076");
        assertThat(run.stderr()).contains("Object.wait()");
        assertThat(run.stderr()).doesNotContain("JAVAN014", "JAVAN031");
    }

    @Test
    void objectNotifyFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-object-notify");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    new Object().notify();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN076");
        assertThat(run.stderr()).contains("Object.notify()");
        assertThat(run.stderr()).doesNotContain("JAVAN031");
    }

    @Test
    void unreachableNotifyAllWarnsClearly() throws Exception {
        final Path project = project("thread-object-notify-all-unreachable");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead() {
                    new Object().notifyAll();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN176]");
        assertThat(run.stdout()).contains("Object.notifyAll()");
        assertThat(run.stdout()).doesNotContain("warning[JAVAN131]");
    }

    @Test
    void executorsFactoryFailsClearlyAtBuildTime() throws Exception {
        final Path project = project("thread-executors-factory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Executors.newSingleThreadExecutor();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("JAVAN077");
        assertThat(run.stderr()).contains("Executors.newSingleThreadExecutor()");
        assertThat(run.stderr()).doesNotContain("JAVAN031");
    }

    @Test
    void threadLocalSetThenGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-threadlocal-set-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ThreadLocal<String> local = new ThreadLocal<>();
                    local.set("main");
                    System.out.println(local.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-threadlocal-set-get").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadLocalRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-threadlocal-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ThreadLocal<String> local = new ThreadLocal<>();
                    local.set("main");
                    local.remove();
                    System.out.println(local.get() == null);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-threadlocal-remove").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadLocalStateIsIsolatedAcrossStartedThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-threadlocal-started-thread-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ThreadLocal<String> local = new ThreadLocal<>();
                    local.set("main");
                    final Thread worker = new Thread(new Task(local));
                    worker.start();
                    worker.join();
                    System.out.println(local.get());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final ThreadLocal<String> local;

                public Task(final ThreadLocal<String> local) {
                    this.local = local;
                }

                @Override
                public void run() {
                    System.out.println(local.get() == null);
                    local.set("worker");
                    System.out.println(local.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-threadlocal-started-thread-isolation").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void threadLocalBuildWritesCleanThreadAndUnifiedReports() throws Exception {
        final Path project = project("thread-threadlocal-clean-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ThreadLocal<String> local = new ThreadLocal<>();
                    local.set("main");
                    System.out.println(local.get());
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());
        final CliRun report = run(tempDir, "report", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        assertThat(report.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"diagnostics\": 0",
            "\"errors\": 0"
        ).doesNotContain(
            "ThreadLocal.<init>()",
            "\"code\": \"JAVAN077\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "{\"name\": \"threads\", \"status\": \"present\"",
            "\"diagnostics\": 0",
            "\"errors\": 0"
        );
    }

    @Test
    void inheritableThreadLocalStateIsInheritedByStartedThreadBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-inheritable-threadlocal-started-thread-inheritance");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InheritableThreadLocal<String> local = new InheritableThreadLocal<>();
                    local.set("main");
                    final Thread worker = new Thread(new Task(local));
                    worker.start();
                    worker.join();
                    System.out.println(local.get());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final InheritableThreadLocal<String> local;

                public Task(final InheritableThreadLocal<String> local) {
                    this.local = local;
                }

                @Override
                public void run() {
                    System.out.println(local.get());
                    local.set("worker");
                    System.out.println(local.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-inheritable-threadlocal-started-thread-inheritance").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void inheritableThreadLocalBuildWritesCleanThreadAndUnifiedReports() throws Exception {
        final Path project = project("thread-inheritable-threadlocal-clean-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final InheritableThreadLocal<String> local = new InheritableThreadLocal<>();
                    local.set("main");
                    final Thread worker = new Thread(new Task(local));
                    worker.start();
                    worker.join();
                    System.out.println(local.get());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final InheritableThreadLocal<String> local;

                public Task(final InheritableThreadLocal<String> local) {
                    this.local = local;
                }

                @Override
                public void run() {
                    System.out.println(local.get());
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());
        final CliRun report = run(tempDir, "report", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        assertThat(report.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/threads.json"))).contains(
            "\"errors\": 0",
            "\"concurrencyRuntime\": 0"
        ).doesNotContain(
            "InheritableThreadLocal.<init>()",
            "\"code\": \"JAVAN177\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "{\"name\": \"threads\", \"status\": \"present\"",
            "\"errors\": 0"
        );
    }

    @Test
    void virtualThreadExecutorSubmitBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-submit");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.Future;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    final Future<?> future = executor.submit(new Task());
                    executor.close();
                    System.out.println(future != null);
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-submit").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorSubmitCancelTrueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-submit-cancel");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.Future;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    final Future<?> future = executor.submit(new Task());
                    System.out.println(future.cancel(true));
                    executor.close();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        LockSupport.parkNanos(1_000_000L);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-submit-cancel").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorAwaitTerminationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-await-termination");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    executor.execute(new Task());
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-await-termination").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorShutdownNowBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-shutdown-now");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;
            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    executor.submit(new Task());
                    final List<Runnable> pending = executor.shutdownNow();
                    System.out.println(pending.size());
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        LockSupport.parkNanos(1_000_000L);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-shutdown-now").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorFutureCompletedStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-future-completed-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.Future;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    final Future<?> future = executor.submit(new Task());
                    executor.close();
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-future-completed-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorFutureCancelledStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-future-cancelled-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.Future;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    final Future<?> future = executor.submit(new Task());
                    System.out.println(future.cancel(true));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    executor.close();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        LockSupport.parkNanos(1_000_000L);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-future-cancelled-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadExecutorFutureCancelFalseRunningTaskBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-executor-future-cancel-false-running");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.Executors;
            import java.util.concurrent.Future;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                    final Future<?> future = executor.submit(new Task());
                    Thread.sleep(50L);
                    System.out.println(future.cancel(false));
                    executor.close();
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    try {
                        Thread.sleep(150L);
                        System.out.println("completed");
                    } catch (final InterruptedException interrupted) {
                        System.out.println("interrupted");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-executor-future-cancel-false-running").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorScheduleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-schedule");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    Thread.sleep(50L);
                    executor.shutdown();
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-schedule").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCompletedStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-completed-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-completed-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCancelledStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-cancelled-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.schedule(new Task(), 200L, TimeUnit.MILLISECONDS);
                    System.out.println(future.cancel(true));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("unexpected");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-cancelled-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCancelFalseBeforeFirstRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-cancel-false-before-run");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.schedule(new Task(), 200L, TimeUnit.MILLISECONDS);
                    System.out.println(future.cancel(false));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("unexpected");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-cancel-false-before-run").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceFutureCompletedStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-future-completed-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-future-completed-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceFutureCancelledStateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-future-cancelled-state");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.schedule(new Task(), 200L, TimeUnit.MILLISECONDS);
                    System.out.println(future.cancel(true));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("unexpected");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-future-cancelled-state").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCancelFalseStopsFixedRateAfterFirstRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-cancel-false-fixed-rate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleAtFixedRate(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(false));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                }

                public static int runs() {
                    return RUNS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-cancel-false-fixed-rate").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCancelFalseStopsFixedDelayAfterFirstRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-cancel-false-fixed-delay");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleWithFixedDelay(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(false));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                }

                public static int runs() {
                    return RUNS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-cancel-false-fixed-delay").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCancelTrueInterruptsFixedRateRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-cancel-true-fixed-rate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleAtFixedRate(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(true));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                    System.out.println(Task.interrupts());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();
                private static final AtomicInteger INTERRUPTS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                    try {
                        Thread.sleep(5_000L);
                    } catch (final InterruptedException interrupted) {
                        INTERRUPTS.incrementAndGet();
                    }
                }

                public static int runs() {
                    return RUNS.get();
                }

                public static int interrupts() {
                    return INTERRUPTS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-cancel-true-fixed-rate").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledFutureCancelTrueInterruptsFixedDelayRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-future-cancel-true-fixed-delay");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleWithFixedDelay(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(true));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                    System.out.println(Task.interrupts());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();
                private static final AtomicInteger INTERRUPTS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                    try {
                        Thread.sleep(5_000L);
                    } catch (final InterruptedException interrupted) {
                        INTERRUPTS.incrementAndGet();
                    }
                }

                public static int runs() {
                    return RUNS.get();
                }

                public static int interrupts() {
                    return INTERRUPTS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-future-cancel-true-fixed-delay").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceFutureCancelFalseStopsFixedRateAfterFirstRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-future-cancel-false-fixed-rate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleAtFixedRate(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(false));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                }

                public static int runs() {
                    return RUNS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-future-cancel-false-fixed-rate").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceFutureCancelFalseStopsFixedDelayAfterFirstRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-future-cancel-false-fixed-delay");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleWithFixedDelay(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(false));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                }

                public static int runs() {
                    return RUNS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-future-cancel-false-fixed-delay").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceFutureCancelTrueInterruptsFixedRateRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-future-cancel-true-fixed-rate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleAtFixedRate(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(true));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                    System.out.println(Task.interrupts());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();
                private static final AtomicInteger INTERRUPTS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                    try {
                        Thread.sleep(5_000L);
                    } catch (final InterruptedException interrupted) {
                        INTERRUPTS.incrementAndGet();
                    }
                }

                public static int runs() {
                    return RUNS.get();
                }

                public static int interrupts() {
                    return INTERRUPTS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-future-cancel-true-fixed-rate").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceFutureCancelTrueInterruptsFixedDelayRunBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-future-cancel-true-fixed-delay");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Future;
            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    final Future<?> future = executor.scheduleWithFixedDelay(new Task(), 0L, 200L, TimeUnit.MILLISECONDS);
                    while (Task.runs() == 0) {
                        Thread.yield();
                    }
                    System.out.println(future.cancel(true));
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println(future.isDone());
                    System.out.println(future.isCancelled());
                    System.out.println(Task.runs());
                    System.out.println(Task.interrupts());
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.atomic.AtomicInteger;

            public final class Task implements Runnable {
                private static final AtomicInteger RUNS = new AtomicInteger();
                private static final AtomicInteger INTERRUPTS = new AtomicInteger();

                @Override
                public void run() {
                    RUNS.incrementAndGet();
                    try {
                        Thread.sleep(5_000L);
                    } catch (final InterruptedException interrupted) {
                        INTERRUPTS.incrementAndGet();
                    }
                }

                public static int runs() {
                    return RUNS.get();
                }

                public static int interrupts() {
                    return INTERRUPTS.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-future-cancel-true-fixed-delay").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceScheduleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-schedule");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-schedule").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorAwaitTerminationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-await-termination");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    final ExecutorService service = executor;
                    service.shutdown();
                    System.out.println(service.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-await-termination").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorConcreteAwaitTerminationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-concrete-await-termination");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-concrete-await-termination").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorShutdownNowBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-shutdown-now");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 200L, TimeUnit.MILLISECONDS);
                    final ExecutorService service = executor;
                    service.shutdownNow();
                    System.out.println(service.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("late");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-shutdown-now").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorConcreteShutdownNowBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-concrete-shutdown-now");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 200L, TimeUnit.MILLISECONDS);
                    executor.shutdownNow();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("late");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-concrete-shutdown-now").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorShutdownNowReturnsPendingDelayedTaskBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-shutdown-now-pending");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.schedule(new Task(), 200L, TimeUnit.MILLISECONDS);
                    final List<Runnable> pending = executor.shutdownNow();
                    System.out.println(pending.size());
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("late");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-shutdown-now-pending").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceScheduleAtFixedRateStopsAfterShutdownBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-fixed-rate-shutdown");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    executor.scheduleAtFixedRate(new Task(), 200L, 50L, TimeUnit.MILLISECONDS);
                    Thread.sleep(20L);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("tick");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-fixed-rate-shutdown").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledThreadPoolExecutorScheduleWithFixedDelayStopsAfterShutdownBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-fixed-delay-shutdown");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                    executor.scheduleWithFixedDelay(new Task(), 200L, 50L, TimeUnit.MILLISECONDS);
                    Thread.sleep(20L);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("tick");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-fixed-delay-shutdown").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void scheduledExecutorServiceScheduleWithFixedDelayStopsAfterShutdownBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-scheduled-executor-service-fixed-delay-shutdown");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ScheduledExecutorService;
            import java.util.concurrent.ScheduledThreadPoolExecutor;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);
                    executor.scheduleWithFixedDelay(new Task(), 200L, 50L, TimeUnit.MILLISECONDS);
                    Thread.sleep(20L);
                    executor.shutdown();
                    System.out.println(executor.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("tick");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-scheduled-executor-service-fixed-delay-shutdown").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadLockSupportParkUnparkBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-virtual-locksupport-park-unpark");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.startVirtualThread(new Task());
                    LockSupport.unpark(worker);
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("ready");
                    LockSupport.park();
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-virtual-locksupport-park-unpark").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void virtualThreadLockSupportParkNanosBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-virtual-locksupport-park-nanos");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = Thread.startVirtualThread(new Task());
                    LockSupport.unpark(worker);
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("ready");
                    LockSupport.parkNanos(1_000_000L);
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-virtual-locksupport-park-nanos").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void currentThreadLockSupportParkUntilPastDeadlineBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-current-locksupport-park-until");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.locks.LockSupport;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    LockSupport.parkUntil(System.currentTimeMillis() - 1L);
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-current-locksupport-park-until").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void unreachableExecutorsFactoryWarnsClearly() throws Exception {
        final Path project = project("thread-executors-unreachable");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.Executors;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead() {
                    Executors.newCachedThreadPool();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN177]");
        assertThat(run.stdout()).contains("Executors.newCachedThreadPool()");
        assertThat(run.stdout()).doesNotContain("warning[JAVAN131]");
    }

    @Test
    void branchExclusiveThreadStartOnSameLocalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-branch-exclusive-start");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread();
                    if (args.length == 0) {
                        thread.start();
                    } else {
                        thread.start();
                    }
                    thread.join();
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-branch-exclusive-start").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void emptyThreadStartBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-start-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread();
                    thread.start();
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-start-empty").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void emptyThreadJoinBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-join-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread();
                    thread.start();
                    thread.join();
                    System.out.println("done");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-join-empty").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadJoinInterruptedBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-join-interrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread();
                    thread.start();
                    while (thread.isAlive()) {
                        Thread.sleep(1L);
                    }
                    Thread.currentThread().interrupt();
                    try {
                        thread.join();
                        System.out.println("ok");
                    } catch (final InterruptedException interrupted) {
                        System.out.println(interrupted.getMessage() == null);
                        System.out.println(Thread.currentThread().isInterrupted());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-join-interrupted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadJoinInterruptedByWorkerBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-join-runtime-interrupted");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread current = Thread.currentThread();
                    final Thread worker = new Thread(new SlowTask());
                    final Thread interrupter = new Thread(new InterruptTask(current));
                    worker.start();
                    interrupter.start();
                    try {
                        worker.join();
                        System.out.println("ok");
                    } catch (final InterruptedException interrupted) {
                        System.out.println(interrupted.getMessage() == null);
                        System.out.println(Thread.currentThread().isInterrupted());
                    }
                    worker.join();
                    interrupter.join();
                }
            }
            """);
        writeJava(project, "com.acme.SlowTask", """
            package com.acme;

            public final class SlowTask implements Runnable {
                @Override
                public void run() {
                    final long until = System.nanoTime() + 500_000_000L;
                    while (System.nanoTime() < until) {
                        // spin
                    }
                }
            }
            """);
        writeJava(project, "com.acme.InterruptTask", """
            package com.acme;

            public final class InterruptTask implements Runnable {
                private final Thread target;

                public InterruptTask(final Thread target) {
                    this.target = target;
                }

                @Override
                public void run() {
                    final long until = System.nanoTime() + 50_000_000L;
                    while (System.nanoTime() < until) {
                        // spin
                    }
                    target.interrupt();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-join-runtime-interrupted").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void runnableTargetThreadStartJoinBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-start-runnable-target");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread(new Task());
                    thread.start();
                    thread.join();
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("task");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-start-runnable-target").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void runnableThreadTargetSurvivesGcPressureBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-runnable-target-root-gc-pressure");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread thread = new Thread(new Task("task"));
                    thread.start();
                    thread.join();
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                private final String message;

                public Task(final String message) {
                    this.message = message;
                }

                @Override
                public void run() {
                    for (int index = 0; index < 4_000; index++) {
                        final String value = message + index;
                        if (value.length() < 0) {
                            throw new IllegalStateException(value);
                        }
                    }
                    System.out.println(message);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/thread-runnable-target-root-gc-pressure").toString()),
            Duration.ofSeconds(10),
            Map.of(
                "JAVAN_HEAP_LIMIT_BYTES", "65536",
                "JAVAN_GC_SAFEPOINT_INTERVAL", "1"
            )
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadJoinTimeoutBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-join-timeout");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = new Thread(new SleepTask());
                    worker.start();
                    worker.join(1L);
                    System.out.println(worker.isAlive());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.SleepTask", """
            package com.acme;

            public final class SleepTask implements Runnable {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500L);
                    } catch (final InterruptedException interrupted) {
                        System.out.println("interrupted");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-join-timeout").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadJoinMillisNanosTimeoutBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-join-millis-nanos-timeout");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = new Thread(new SleepTask());
                    worker.start();
                    worker.join(0L, 500_000);
                    System.out.println(worker.isAlive());
                    worker.join();
                }
            }
            """);
        writeJava(project, "com.acme.SleepTask", """
            package com.acme;

            public final class SleepTask implements Runnable {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500L);
                    } catch (final InterruptedException interrupted) {
                        System.out.println("interrupted");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-join-millis-nanos-timeout").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void threadDaemonFlagBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("thread-daemon-flag");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                        }
                    });
                    thread.setDaemon(true);
                    System.out.println(thread.isDaemon());
                    thread.setDaemon(false);
                    System.out.println(thread.isDaemon());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/thread-daemon-flag").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void callerRunsPolicyConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("caller-runs-policy-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ThreadPoolExecutor;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object policy = new ThreadPoolExecutor.CallerRunsPolicy();
                    System.out.println(policy != null);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/caller-runs-policy-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void unsupportedJdkIntrinsicOverloadsFailClearly() throws Exception {
        final Path objectsProject = project("unsupported-objects-overload");
        writeJava(objectsProject, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Objects.requireNonNullElse(null, "fallback");
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun objectsRun = run(tempDir, "build", objectsProject.toString());

        assertThat(objectsRun.exitCode()).isEqualTo(2);
        assertThat(objectsRun.stderr()).contains(
            "error[JAVAN031]",
            "java/util/Objects.requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        assertThat(Files.readString(objectsProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 0}",
                "{\"target\": \"java/util/Objects.requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\", \"count\": 1}"
            );

        final Path numberProject = project("unsupported-number-to-string-overload");
        writeJava(numberProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.toString(15, 16));
                    System.out.println(Long.toString(31L, 16));
                }
            }
            """);

        final CliRun numberRun = run(tempDir, "build", numberProject.toString());

        assertThat(numberRun.exitCode()).isEqualTo(2);
        assertThat(numberRun.stderr()).contains("error[JAVAN031]");
        assertThat(Files.readString(numberProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Integer.toString\", \"count\": 0}",
                "{\"name\": \"Long.toString\", \"count\": 0}",
                "{\"target\": \"java/lang/Integer.toString(II)Ljava/lang/String;\", \"count\": 1}",
                "{\"target\": \"java/lang/Long.toString(JI)Ljava/lang/String;\", \"count\": 1}"
            );
    }

    @Test
    void systemArraycopyPrimitiveTypeMismatchFailsAtRuntime() throws Exception {
        final Path project = project("system-arraycopy-type-mismatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object source = new byte[] {1};
                    final Object target = new boolean[1];
                    System.arraycopy(source, 0, target, 0, 1);
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/system-arraycopy-type-mismatch").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains("array copy type mismatch");
    }

    @Test
    void longArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("long-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final long[] values = new long[]{1L, 2L};
                    values[1] = 9L;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1\n9\n");
    }

    @Test
    void instanceOfExactApplicationClassBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new Box();
                    System.out.println(value instanceof Box);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box {
                public Box() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-exact").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfNullReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    System.out.println(value instanceof Box);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box {
                public Box() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-null").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfSuperclassBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new Child();
                    System.out.println(value instanceof Base);
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                public Base() {
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base {
                public Child() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-superclass").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfInterfaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-interface");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new EnglishGreeter();
                    System.out.println(value instanceof Greeter);
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                public EnglishGreeter() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-interface").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfCollectionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-collection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new ArrayList<>();
                    System.out.println(value instanceof Collection);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-collection").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfMapBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-map");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new HashMap<>();
                    System.out.println(value instanceof Map);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-map").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfMapEntryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-map-entry");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> map = new HashMap<>();
                    map.put("k", "v");
                    final Object value = map.entrySet().iterator().next();
                    System.out.println(value instanceof Map.Entry);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-map-entry").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfIntArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-int-array", "new int[]{1, 2}", "int[]");
    }

    @Test
    void instanceOfLongArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-long-array", "new long[]{1L, 2L}", "long[]");
    }

    @Test
    void instanceOfFloatArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-float-array", "new float[]{1.0f, 2.0f}", "float[]");
    }

    @Test
    void instanceOfDoubleArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-double-array", "new double[]{1.0d, 2.0d}", "double[]");
    }

    @Test
    void instanceOfByteArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-byte-array", "new byte[]{1, 2}", "byte[]");
    }

    @Test
    void instanceOfBooleanArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-boolean-array", "new boolean[]{true, false}", "boolean[]");
    }

    @Test
    void instanceOfShortArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-short-array", "new short[]{1, 2}", "short[]");
    }

    @Test
    void instanceOfCharArrayBuildsAndMatchesJvmOutput() throws Exception {
        assertPrimitiveArrayInstanceOf("instanceof-char-array", "new char[]{'a', 'b'}", "char[]");
    }

    private void assertPrimitiveArrayInstanceOf(
        final String projectName,
        final String valueExpression,
        final String instanceOfType
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = %s;
                    System.out.println(value instanceof %s);
                }
            }
            """.formatted(valueExpression, instanceOfType));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/" + projectName).toString())).stdout()).isEqualTo(jvmOutput);
    }

}
