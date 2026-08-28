package javan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
