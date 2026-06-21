package javan.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    void timeoutMessagePreservesExistingStderr() throws Exception {
        final ProcessRunner.Result result = new ProcessRunner(Duration.ofMillis(50))
            .run(tempDir, List.of("sh", "-c", "echo waiting >&2; sleep 1"));

        assertThat(result.exitCode()).isEqualTo(124);
        assertThat(result.stderr()).contains("waiting", "Timed out after 0s: sh -c echo waiting >&2; sleep 1");
    }
}
