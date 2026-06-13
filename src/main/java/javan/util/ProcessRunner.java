package javan.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs child processes with captured output.
 */
public final class ProcessRunner {
    private final Duration timeout;

    /**
     * Creates a process runner with a default timeout.
     */
    public ProcessRunner() {
        this(Duration.ofMinutes(5));
    }

    /**
     * Creates a process runner.
     *
     * @param timeout maximum process duration
     */
    public ProcessRunner(final Duration timeout) {
        this.timeout = timeout;
    }

    /**
     * Runs a command and captures stdout, stderr, and exit code.
     *
     * @param workingDirectory process working directory
     * @param command command and arguments
     * @return captured process result
     * @throws IOException when the process cannot be started or read
     * @throws InterruptedException when interrupted while waiting
     */
    public Result run(final Path workingDirectory, final List<String> command) throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(workingDirectory.toFile());
        final Process process = builder.start();
        final boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new Result(124, "", "Timed out after " + timeout.toSeconds() + "s: " + String.join(" ", command));
        }
        final String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        final String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.exitValue(), stdout, stderr);
    }

    /**
     * Returns true when a command can be started.
     *
     * @param executable executable name
     * @return true when the executable appears available
     */
    public boolean commandExists(final String executable) {
        try {
            final Result result = run(Path.of("").toAbsolutePath(), List.of(executable, "--version"));
            return result.exitCode() == 0 || !result.stdout().isBlank() || !result.stderr().isBlank();
        } catch (final IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Finds the first available command.
     *
     * @param executables candidate executable names
     * @return available executable
     */
    public Optional<String> firstAvailable(final List<String> executables) {
        return executables.stream().filter(this::commandExists).findFirst();
    }

    /**
     * Captured process result.
     *
     * @param exitCode process exit code
     * @param stdout standard output
     * @param stderr standard error
     */
    public record Result(int exitCode, String stdout, String stderr) {
    }
}
