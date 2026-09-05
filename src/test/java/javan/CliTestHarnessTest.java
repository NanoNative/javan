package javan;

import javan.testing.TestSuite.PlatformTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.opentest4j.AssertionFailedError;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
@PlatformTest
final class CliTestHarnessTest {
    @TempDir
    private Path tempDir;

    @Test
    void timeoutWaitsForCliChildCleanupAndKeepsFinalDiagnostics() throws Exception {
        final Throwable failure = catchThrowable(() -> CliTestHarness.run(
            tempDir, Duration.ofSeconds(5), waitingJavaCommand("normal")
        ));

        assertThat(failure).isInstanceOf(AssertionFailedError.class).hasCauseInstanceOf(TimeoutException.class);
        assertThat(ProcessHandle.of(childPid()).filter(ProcessHandle::isAlive))
            .as("CLI child must stop before the test returns").isEmpty();
        assertDescendantStopped();
        assertThat(failure).hasMessageContaining("Cli.run timed out after 5 seconds")
            .hasMessageContaining("error[JAVAN902]: interrupted");
        assertThat(failure.getCause().getSuppressed()).isEmpty();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
        assertThat(CliTestHarness.run(tempDir, Duration.ofSeconds(5), "--help").exitCode()).isZero();
    }

    @Test
    void callerInterruptionWaitsForCliChildCleanupAndPreservesInterrupt() throws Exception {
        final AtomicBoolean interrupted = new AtomicBoolean();
        final FutureTask<Throwable> task = new FutureTask<>(() -> {
            try {
                return catchThrowable(() -> CliTestHarness.run(tempDir, Duration.ofMinutes(1), waitingJavaCommand("normal")));
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        final Thread caller = Thread.ofVirtual().start(task);
        try {
            final ProcessHandle child = ProcessHandle.of(childPid()).orElseThrow();
            caller.interrupt();
            assertThat(caller.join(Duration.ofSeconds(10))).isTrue();

            assertThat(child.isAlive()).as("Interrupted CLI child must stop before the test returns").isFalse();
            assertDescendantStopped();
            assertThat(task.get()).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class)
                .hasMessageContaining("Interrupted while waiting for Cli.run.")
                .hasMessageContaining("error[JAVAN902]: interrupted");
            assertThat(interrupted).isTrue();
            assertThat(CliTestHarness.run(tempDir, Duration.ofSeconds(5), "--version").exitCode()).isZero();
        } finally {
            caller.interrupt();
            assertThat(caller.join(Duration.ofSeconds(10))).isTrue();
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void anotherCallerInterruptDoesNotAbortCliForcedShutdown() throws Exception {
        final AtomicBoolean interrupted = new AtomicBoolean();
        final FutureTask<Throwable> task = new FutureTask<>(() -> {
            try {
                return catchThrowable(() -> CliTestHarness.run(
                    tempDir, Duration.ofMinutes(1), waitingJavaCommand("delay-shutdown")
                ));
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        final Thread caller = Thread.ofVirtual().start(task);
        try {
            final ProcessHandle child = ProcessHandle.of(childPid()).orElseThrow();
            caller.interrupt();
            awaitFile(tempDir.resolve("stopping"));
            caller.interrupt();
            assertThat(caller.join(Duration.ofSeconds(10))).isTrue();

            assertThat(child.isAlive()).isFalse();
            assertDescendantStopped();
            final Throwable failure = task.get();
            assertThat(failure).isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(InterruptedException.class)
                .hasMessageContaining("error[JAVAN902]: interrupted");
            assertThat(failure.getCause().getSuppressed()).hasSize(1);
            assertThat(failure.getCause().getSuppressed()[0]).isInstanceOf(InterruptedException.class);
            assertThat(interrupted).isTrue();
        } finally {
            caller.interrupt();
            assertThat(caller.join(Duration.ofSeconds(10))).isTrue();
        }
    }

    @Test
    void alreadyInterruptedCallerCancelsWithoutLosingItsInterrupt() throws Exception {
        try {
            Thread.currentThread().interrupt();
            final Throwable failure = catchThrowable(() -> CliTestHarness.run(
                tempDir, Duration.ofMinutes(1), waitingJavaCommand("normal")
            ));

            assertThat(failure).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private String[] waitingJavaCommand(final String mode) {
        return new String[] {
            "--jn-facade-java", "-cp", Path.of("target/test-classes").toAbsolutePath().toString(),
            WaitingJava.class.getName(), tempDir.toString(), mode
        };
    }

    private long childPid() throws Exception {
        awaitFile(tempDir.resolve("ready"));
        return Long.parseLong(Files.readAllLines(tempDir.resolve("child.pid")).getFirst());
    }

    private void assertDescendantStopped() throws Exception {
        final long pid = Long.parseLong(Files.readAllLines(tempDir.resolve("descendant.pid")).getFirst());
        assertThat(ProcessHandle.of(pid).filter(ProcessHandle::isAlive))
            .as("CLI descendant must stop before the test returns").isEmpty();
    }

    private static void awaitFile(final Path file) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(file).as("Java facade child reached its wait boundary").exists();
    }

    @AfterEach
    void stopChildren() {
        final AssertionError cleanup = new AssertionError("Could not clean up CLI fixture processes");
        for (final String name : List.of("descendant.pid", "child.pid")) {
            try {
                final Path pid = tempDir.resolve(name);
                if (Files.exists(pid)) {
                    final List<String> identity = Files.readAllLines(pid);
                    final var child = ProcessHandle.of(Long.parseLong(identity.getFirst()))
                        .filter(process -> process.info().startInstant().map(Instant::toEpochMilli)
                            .filter(start -> start == Long.parseLong(identity.get(1))).isPresent());
                    if (child.isPresent() && child.orElseThrow().isAlive()) {
                        child.orElseThrow().destroyForcibly();
                        child.orElseThrow().onExit().get(5, TimeUnit.SECONDS);
                    }
                }
            } catch (final Exception failure) {
                cleanup.addSuppressed(failure);
            }
        }
        if (cleanup.getSuppressed().length != 0) {
            throw cleanup;
        }
    }

    public static final class WaitingJava {
        public static void main(final String[] args) throws Exception {
            final Path directory = Path.of(args[0]);
            if ("descendant".equals(args[1])) {
                new CountDownLatch(1).await(30, TimeUnit.SECONDS);
                return;
            }
            publishIdentity(directory, "child.pid", ProcessHandle.current());
            if ("delay-shutdown".equals(args[1])) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        Files.createFile(directory.resolve("stopping"));
                        new CountDownLatch(1).await(30, TimeUnit.SECONDS);
                    } catch (final Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                }));
            }
            final Process descendant = new ProcessBuilder(
                ProcessHandle.current().info().command().orElseThrow(), "-cp", System.getProperty("java.class.path"),
                WaitingJava.class.getName(), directory.toString(), "descendant"
            ).start();
            publishIdentity(directory, "descendant.pid", descendant.toHandle());
            Files.createFile(directory.resolve("ready"));
            new CountDownLatch(1).await(30, TimeUnit.SECONDS);
        }

        private static void publishIdentity(final Path directory, final String name, final ProcessHandle process) throws Exception {
            final Path temporary = directory.resolve(name + ".partial");
            Files.writeString(temporary, process.pid() + "\n" + process.info().startInstant().orElseThrow().toEpochMilli());
            Files.move(temporary, directory.resolve(name));
        }
    }

    @Test
    void childCoverageCommandInjectsExplicitJacocoAgentForJavanMain() {
        final String originalAgent = System.getProperty("javan.childJacocoArgLine");
        final String originalDirectory = System.getProperty("javan.childJacocoDir");
        try {
            System.setProperty(
                "javan.childJacocoArgLine",
                "-javaagent:/tmp/org.jacoco.agent.jar=destfile=/tmp/parent.exec,append=true"
            );
            System.setProperty("javan.childJacocoDir", "target/jacoco-child-test");

            final List<String> command = CliTestHarness.childCoverageCommandForTesting(List.of(
                "java",
                "-cp",
                "target/classes",
                "javan.Main",
                "--version"
            ));

            assertThat(command).hasSize(6);
            assertThat(command.get(0)).isEqualTo("java");
            assertThat(command.get(1))
                .startsWith("-javaagent:/tmp/org.jacoco.agent.jar=destfile=")
                .contains("target/jacoco-child-test/child-")
                .endsWith(".exec,append=true");
            assertThat(command.subList(2, command.size())).containsExactly(
                "-cp",
                "target/classes",
                "javan.Main",
                "--version"
            );
        } finally {
            restoreProperty("javan.childJacocoArgLine", originalAgent);
            restoreProperty("javan.childJacocoDir", originalDirectory);
        }
    }

    @Test
    void childCoverageCommandLeavesNonJavanCommandUntouched() {
        final String originalAgent = System.getProperty("javan.childJacocoArgLine");
        final String originalDirectory = System.getProperty("javan.childJacocoDir");
        try {
            System.setProperty(
                "javan.childJacocoArgLine",
                "-javaagent:/tmp/org.jacoco.agent.jar=destfile=/tmp/parent.exec,append=true"
            );
            System.setProperty("javan.childJacocoDir", "target/jacoco-child-test");

            final List<String> command = List.of("java", "-version");
            assertThat(CliTestHarness.childCoverageCommandForTesting(command)).isEqualTo(command);
        } finally {
            restoreProperty("javan.childJacocoArgLine", originalAgent);
            restoreProperty("javan.childJacocoDir", originalDirectory);
        }
    }

    @Test
    void currentJdkToolCommandsResolveFromJavaHomeBin() {
        final String suffix = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
            ? ".exe"
            : "";
        final String bin = java.nio.file.Path.of(System.getProperty("java.home")).resolve("bin").toString();

        assertThat(CliTestHarness.currentJavaCommand()).isEqualTo(java.nio.file.Path.of(bin).resolve("java" + suffix).toString());
        assertThat(CliTestHarness.currentJavacCommand()).isEqualTo(java.nio.file.Path.of(bin).resolve("javac" + suffix).toString());
        assertThat(CliTestHarness.currentJarCommand()).isEqualTo(java.nio.file.Path.of(bin).resolve("jar" + suffix).toString());
    }

    @Test
    void childCoveragePropertiesArePresentWhenJacocoAgentIsAttached() {
        final boolean jacocoAttached = java.lang.management.ManagementFactory.getRuntimeMXBean()
            .getInputArguments()
            .stream()
            .anyMatch(argument -> argument.startsWith("-javaagent:") && argument.contains("org.jacoco.agent"));
        if (!jacocoAttached) {
            return;
        }

        assertThat(System.getProperty("javan.childJacocoArgLine", ""))
            .contains("-javaagent:")
            .contains("org.jacoco.agent")
            .contains("destfile=");
        assertThat(System.getProperty("javan.childJacocoDir", ""))
            .isNotBlank();
    }

    private static void restoreProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
