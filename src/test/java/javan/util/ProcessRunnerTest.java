package javan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

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
    void retriesAProcessTemporarilyBlockedByAWriter() throws Exception {
        final Path executable = tempDir.resolve("eventually-ready");
        Files.writeString(executable, "#!/bin/sh\nprintf 'ready\\n'\n", StandardCharsets.UTF_8);
        assertThat(executable.toFile().setExecutable(true)).isTrue();

        final FileChannel writer = FileChannel.open(executable, StandardOpenOption.WRITE);
        final Thread release = Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(75L);
                writer.close();
            } catch (final IOException exception) {
                throw new IllegalStateException(exception);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            final ProcessRunner.Result result = new ProcessRunner().run(tempDir, List.of("./eventually-ready"));

            assertThat(result).isEqualTo(new ProcessRunner.Result(0, "ready\n", ""));
        } finally {
            writer.close();
            release.join();
        }
    }
}
