package javan.util;

import javan.testing.TestSuite.PlatformTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class ProcessRunnerTest {
    @TempDir
    private Path tempDir;

    @Test
    @PlatformTest
    void interruptionReleasesCapturedOutputFilesBeforeReturning() throws Exception {
        assertCaptureProbe("interrupt", "interrupted:0\nremaining-output-files:0\n");
    }

    @Test
    @PlatformTest
    @EnabledOnOs(OS.WINDOWS)
    void interruptionPreservesBothLockedOutputCleanupFailures() throws Exception {
        assertCaptureProbe("locked-interrupt", "interrupted:2\nremaining-output-files:2\n");
    }

    @Test
    @PlatformTest
    @EnabledOnOs(OS.WINDOWS)
    void completionReportsBothLockedOutputCleanupFailures() throws Exception {
        assertCaptureProbe("locked-complete", "io-failure:2\nremaining-output-files:2\n");
    }

    private void assertCaptureProbe(final String mode, final String expectedOutput) throws Exception {
        final Path capture = Files.createDirectory(tempDir.resolve("capture"));
        final String classpath = Path.of("target/classes").toAbsolutePath() + java.io.File.pathSeparator
            + Path.of("target/test-classes").toAbsolutePath();
        final ProcessRunner.Result result = new ProcessRunner(Duration.ofSeconds(20)).run(tempDir, List.of(
            ProcessHandle.current().info().command().orElseThrow(), "-Djava.io.tmpdir=" + capture,
            "-cp", classpath, CaptureProbe.class.getName(), tempDir.toString(), mode
        ));

        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).isEqualToNormalizingNewlines(expectedOutput);
        try (var files = Files.list(capture)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    public static final class CaptureProbe {
        public static void main(final String[] args) throws Exception {
            final Path directory = Path.of(args[0]);
            final Path ready = directory.resolve("ready");
            final Path complete = directory.resolve("complete");
            final String mode = args[1];
            if ("wait".equals(mode)) {
                Files.createFile(ready);
                awaitFile(complete);
                Files.createFile(directory.resolve("completed"));
                return;
            }
            final FutureTask<Exception> task = new FutureTask<>(() -> {
                try {
                    new ProcessRunner(Duration.ofSeconds(10)).run(directory, List.of(
                        ProcessHandle.current().info().command().orElseThrow(), "-cp",
                        System.getProperty("java.class.path"), CaptureProbe.class.getName(), directory.toString(), "wait"
                    ));
                    throw new AssertionError("Expected process interruption or output cleanup failure");
                } catch (final IOException | InterruptedException failure) {
                    return failure;
                }
            });
            final Thread caller = Thread.ofVirtual().start(task);
            try {
                awaitFile(ready);
                if ("interrupt".equals(mode)) {
                    caller.interrupt();
                    final Exception failure = task.get(10, TimeUnit.SECONDS);
                    if (!(failure instanceof InterruptedException)) {
                        throw new AssertionError("Expected the original interruption", failure);
                    }
                    System.out.println("interrupted:" + failure.getSuppressed().length);
                    printRemainingOutputFiles();
                } else {
                    verifyLockedCleanup(mode, complete, caller, task);
                }
            } finally {
                caller.interrupt();
                if (!caller.join(Duration.ofSeconds(10))) {
                    throw new AssertionError("Process runner did not stop");
                }
            }
        }

        private static void verifyLockedCleanup(
            final String mode,
            final Path complete,
            final Thread caller,
            final FutureTask<Exception> task
        ) throws Exception {
            final List<Path> output;
            try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                output = files.toList();
            }
            if (output.size() != 2) {
                throw new AssertionError("Expected exactly two captured output files: " + output);
            }
            final Path stdout = output.stream().filter(path -> path.toString().endsWith(".out")).findFirst().orElseThrow();
            final Path stderr = output.stream().filter(path -> path.toString().endsWith(".err")).findFirst().orElseThrow();
            // These handles belong to the probe, so terminating the child cannot release them.
            try (
                FileInputStream stdoutLock = new FileInputStream(stdout.toFile());
                FileInputStream stderrLock = new FileInputStream(stderr.toFile())
            ) {
                if ("locked-interrupt".equals(mode)) {
                    caller.interrupt();
                } else {
                    Files.createFile(complete);
                }
                final Exception failure = task.get(10, TimeUnit.SECONDS);
                if (!stdoutLock.getFD().valid() || !stderrLock.getFD().valid()) {
                    throw new AssertionError("Capture handles closed before process cleanup completed");
                }
                final Throwable cleanup;
                if ("locked-interrupt".equals(mode)) {
                    if (!(failure instanceof InterruptedException) || failure.getSuppressed().length != 1) {
                        throw new AssertionError("Expected interruption with one aggregated cleanup failure", failure);
                    }
                    cleanup = failure.getSuppressed()[0];
                } else {
                    if (!(failure instanceof IOException) || !Files.exists(complete.resolveSibling("completed"))) {
                        throw new AssertionError("Expected cleanup IOException after successful completion", failure);
                    }
                    cleanup = failure;
                }
                if (!(cleanup instanceof FileSystemException first) || !stdout.toString().equals(first.getFile())
                    || cleanup.getSuppressed().length != 1
                    || !(cleanup.getSuppressed()[0] instanceof FileSystemException second)
                    || !stderr.toString().equals(second.getFile()) || second.getSuppressed().length != 0) {
                    throw new AssertionError("Expected cleanup failures for both stdout and stderr", cleanup);
                }
                System.out.println("locked-interrupt".equals(mode) ? "interrupted:2" : "io-failure:2");
                printRemainingOutputFiles();
            }
            Files.delete(stdout);
            Files.delete(stderr);
        }

        private static void printRemainingOutputFiles() throws IOException {
            try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                System.out.println("remaining-output-files:" + files.count());
            }
        }

        private static void awaitFile(final Path file) throws Exception {
            try (var changes = file.getFileSystem().newWatchService()) {
                file.getParent().register(changes, StandardWatchEventKinds.ENTRY_CREATE);
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(file)) {
                    final long remaining = deadline - System.nanoTime();
                    final var change = remaining > 0 ? changes.poll(remaining, TimeUnit.NANOSECONDS) : null;
                    if (change == null) {
                        throw new AssertionError("Timed out waiting for " + file);
                    }
                    change.pollEvents();
                    if (!change.reset()) {
                        throw new AssertionError("Watch directory became unavailable: " + file.getParent());
                    }
                }
            }
        }
    }

    @Test
    void commandExistsReturnsTrueForShell() throws Exception {
        assertThat(new ProcessRunner().commandExists("sh")).isTrue();
    }

    @Test
    void firstAvailableReturnsEmptyWhenNoCandidateExists() throws Exception {
        final String missing = "definitely-not-a-javan-command-" + System.nanoTime();

        assertThat(new ProcessRunner().firstAvailable(List.of(missing))).isEmpty();
    }

    @Test
    void resolveExecutableAcceptsAnExplicitPath() throws Exception {
        final Path executable = Files.createFile(tempDir.resolve("compiler"));
        assertThat(executable.toFile().setExecutable(true)).isTrue();

        assertThat(ProcessRunner.resolveExecutable("", executable.toString(), "", "Linux"))
            .contains(executable);
    }

    @Test
    void resolveExecutableNeverEvaluatesShellSyntax() {
        assertThat(ProcessRunner.resolveExecutable(
            System.getenv("PATH"), "sh; exit 0", "", "Linux"
        )).isEmpty();
    }

    @Test
    void timeoutMessagePreservesExistingStderr() throws Exception {
        final ProcessRunner.Result result = new ProcessRunner(Duration.ofMillis(50))
            .run(tempDir, List.of("sh", "-c", "echo waiting >&2; sleep 1"));

        assertThat(result.exitCode()).isEqualTo(124);
        assertThat(result.stderr()).contains("waiting", "Timed out after 0s: sh -c echo waiting >&2; sleep 1");
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void timeoutStopsShellDescendants() throws Exception {
        assumeTrue(canInspectDescendantProcesses(), "Host does not permit process-tree inspection");
        final Path childPid = tempDir.resolve("child.pid");

        final ProcessRunner.Result result = new ProcessRunner(Duration.ofMillis(50)).run(
            tempDir,
            List.of("sh", "-c", "trap '' TERM; (trap '' TERM; sleep 30) & echo $! > '" + childPid + "'; wait")
        );

        assertThat(result.exitCode()).isEqualTo(124);
        assertThat(childProcessIsAlive(childPid)).isFalse();
    }

    @Test
    void runPropagatesAProcessLaunchFailure() {
        assertThatThrownBy(() -> new ProcessRunner().run(tempDir, List.of("definitely-not-a-javan-command")))
            .isInstanceOf(java.io.IOException.class);
    }

    @Test
    void runResultCapturesAProcessLaunchFailure() {
        final ProcessRunner.Result result = new ProcessRunner().runResult(
            tempDir, List.of("definitely-not-a-javan-command")
        );

        assertThat(result.exitCode()).isEqualTo(126);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isNotBlank();
    }

    @Test
    void nativeInterruptionResultIsDistinctFromAChildExitCode() {
        assertThat(new ProcessRunner.Result(125, "", "Interrupted while running process").interrupted()).isTrue();
        assertThat(new ProcessRunner.Result(125, "", "compiler exited 125").interrupted()).isFalse();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void interruptionStopsTheRunningChildProcess() throws Exception {
        assumeTrue(canInspectDescendantProcesses(), "Host does not permit process-tree inspection");
        final Path started = tempDir.resolve("started");
        final Path completed = tempDir.resolve("completed");
        final Path childPid = tempDir.resolve("child.pid");
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> running = executor.submit(() -> {
            try {
                new ProcessRunner().run(
                    tempDir,
                    List.of("sh", "-c", "trap '' TERM; touch '" + started + "'; (trap '' TERM; sleep 30) & echo $! > '" + childPid + "'; wait; touch '" + completed + "'")
                );
                throw new AssertionError("Expected the process runner to be interrupted");
            } catch (final InterruptedException expected) {
                Thread.currentThread().interrupt();
            } catch (final java.io.IOException exception) {
                throw new AssertionError("Could not start the child process", exception);
            }
        });
        try {
            waitFor(started);
            waitFor(childPid);
            final ProcessIdentity child = childProcess(childPid);
            running.cancel(true);
            assertThat(processIsAlive(child)).isFalse();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(completed).doesNotExist();
    }

    private static boolean childProcessIsAlive(final Path childPid) throws Exception {
        waitFor(childPid);
        final long pid = Long.parseLong(Files.readString(childPid).trim());
        final Optional<ProcessHandle> process = ProcessHandle.of(pid);
        return process.isPresent() && processIsAlive(new ProcessIdentity(pid, process.orElseThrow().info().startInstant()));
    }

    private static boolean canInspectDescendantProcesses() {
        try (var descendants = ProcessHandle.current().descendants()) {
            descendants.toList();
            return true;
        } catch (final RuntimeException unavailable) {
            return false;
        }
    }

    private static ProcessIdentity childProcess(final Path childPid) throws Exception {
        waitFor(childPid);
        final long pid = Long.parseLong(Files.readString(childPid).trim());
        final ProcessHandle process = ProcessHandle.of(pid).orElseThrow();
        return new ProcessIdentity(pid, process.info().startInstant());
    }

    private static boolean processIsAlive(final ProcessIdentity process) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (sameProcessIsAlive(process) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        return sameProcessIsAlive(process);
    }

    private static boolean sameProcessIsAlive(final ProcessIdentity expected) {
        return ProcessHandle.of(expected.pid())
            .filter(ProcessHandle::isAlive)
            .filter(process -> process.info().startInstant().equals(expected.started()))
            .isPresent();
    }

    private static void waitFor(final Path file) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(file).exists();
    }

    private record ProcessIdentity(long pid, Optional<Instant> started) {
    }
}
