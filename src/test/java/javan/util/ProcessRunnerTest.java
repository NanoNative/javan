package javan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
    void runResultCapturesAProcessLaunchFailure() {
        final ProcessRunner.Result result = new ProcessRunner().runResult(
            tempDir, List.of("definitely-not-a-javan-command")
        );

        assertThat(result.exitCode()).isEqualTo(126);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).isNotBlank();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void interruptionStopsTheRunningChildProcess() throws Exception {
        final Path started = tempDir.resolve("started");
        final Path completed = tempDir.resolve("completed");
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<?> running = executor.submit(() -> {
            try {
                new ProcessRunner().run(
                    tempDir,
                    List.of("sh", "-c", "touch '" + started + "'; sleep 2; touch '" + completed + "'")
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
            running.cancel(true);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        Thread.sleep(2_100L);
        assertThat(completed).doesNotExist();
    }

    private static void waitFor(final Path file) throws Exception {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(file) && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(file).exists();
    }
}
