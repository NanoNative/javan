package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
final class TestProcessesTest {
    @Test
    void capturesFastProcessStdoutWithoutTruncation() {
        for (int attempt = 0; attempt < 100; attempt++) {
            final TestProcesses.Result result = TestProcesses.run(
                Path.of("").toAbsolutePath(),
                java.util.List.of("sh", "-c", "printf 'true\\n'"),
                Duration.ofSeconds(2)
            );

            assertThat(result.exitCode()).isZero();
            assertThat(result.stdout()).isEqualTo("true\n");
            assertThat(result.stderr()).isEmpty();
        }
    }

    @Test
    void timeoutKillsShellSpawnedDescendant() {
        final TestProcesses.Result result = TestProcesses.run(
            Path.of("").toAbsolutePath(),
            java.util.List.of("sh", "-c", "sleep 30 & child=$!; echo $child; wait $child"),
            Duration.ofMillis(100)
        );

        final long childPid = Long.parseLong(result.stdout().trim());
        assertThat(result.exitCode()).isEqualTo(124);
        assertThat(ProcessHandle.of(childPid)).isEmpty();
    }
}
