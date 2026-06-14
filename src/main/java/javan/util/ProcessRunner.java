package javan.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final long timeoutMillis;

    /**
     * Creates a process runner with a default timeout.
     */
    public ProcessRunner() {
        this(300_000L);
    }

    /**
     * Creates a process runner.
     *
     * @param timeoutMillis maximum process duration in milliseconds
     */
    public ProcessRunner(final long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Creates a process runner.
     *
     * @param timeout maximum process duration
     */
    public ProcessRunner(final Duration timeout) {
        this(timeout.toMillis());
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
        final Path stdoutFile = Files.createTempFile("javan-process-", ".out");
        final Path stderrFile = Files.createTempFile("javan-process-", ".err");
        final ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(workingDirectory.toFile());
        builder.redirectOutput(stdoutFile.toFile());
        builder.redirectError(stderrFile.toFile());
        try {
            final Process process = builder.start();
            final boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor();
                return new Result(
                    124,
                    Files.readString(stdoutFile, StandardCharsets.UTF_8),
                    timeoutMessage(command, stderrFile)
                );
            }
            return new Result(
                process.exitValue(),
                Files.readString(stdoutFile, StandardCharsets.UTF_8),
                Files.readString(stderrFile, StandardCharsets.UTF_8)
            );
        } finally {
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }

    /**
     * Returns true when a command can be started.
     *
     * @param executable executable name
     * @return true when the executable appears available
     * @throws IOException when the process cannot be started or read
     * @throws InterruptedException when interrupted while waiting
     */
    public boolean commandExists(final String executable) throws IOException, InterruptedException {
        final Result result = run(Path.of("").toAbsolutePath(), List.of("sh", "-c", "command -v " + executable));
        if (result.exitCode() == 0) {
            return true;
        }
        if (!Strings2.isBlank(result.stdout())) {
            return true;
        }
        return false;
    }

    /**
     * Finds the first available command.
     *
     * @param executables candidate executable names
     * @return available executable
     * @throws IOException when a process cannot be started or read
     * @throws InterruptedException when interrupted while waiting
     */
    public Optional<String> firstAvailable(final List<String> executables) throws IOException, InterruptedException {
        for (final String executable : executables) {
            if (commandExists(executable)) {
                return Optional.of(executable);
            }
        }
        return Optional.empty();
    }

    private static String commandLine(final List<String> command) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < command.size(); index++) {
            if (index > 0) {
                result.append(' ');
            }
            result.append(command.get(index));
        }
        return result.toString();
    }

    private String timeoutMessage(final List<String> command, final Path stderrFile) throws IOException {
        final String stderr = Files.readString(stderrFile, StandardCharsets.UTF_8);
        if (Strings2.isBlank(stderr)) {
            return "Timed out after " + (timeoutMillis / 1000L) + "s: " + commandLine(command);
        }
        return stderr + System.lineSeparator() + "Timed out after " + (timeoutMillis / 1000L) + "s: " + commandLine(command);
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
