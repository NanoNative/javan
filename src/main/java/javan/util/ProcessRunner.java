package javan.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs child processes with captured output.
 */
public class ProcessRunner {
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
                    Files.readString(stdoutFile),
                    timeoutMessage(command, stderrFile)
                );
            }
            return new Result(
                process.exitValue(),
                Files.readString(stdoutFile),
                Files.readString(stderrFile)
            );
        } finally {
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }

    /**
     * Runs a command while forwarding each output stream as it is produced.
     * Captured output is still returned for diagnostics and callers that need it.
     *
     * @param workingDirectory process working directory
     * @param command command and arguments
     * @param out destination for forwarded standard output
     * @param err destination for forwarded standard error
     * @return captured process result
     * @throws IOException when the process cannot be started or read
     * @throws InterruptedException when interrupted while waiting
     */
    public Result runAttached(
        final Path workingDirectory,
        final List<String> command,
        final PrintStream out,
        final PrintStream err
    ) throws IOException, InterruptedException {
        final ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.directory(workingDirectory.toFile());
        final Process process = builder.start();
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final Thread stdoutPump = Thread.ofVirtual().start(() -> copy(process.getInputStream(), out, stdout));
        final Thread stderrPump = Thread.ofVirtual().start(() -> copy(process.getErrorStream(), err, stderr));
        try {
            final boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor();
                joinPumps(stdoutPump, stderrPump);
                return new Result(124, text(stdout), timeoutMessage(command, text(stderr)));
            }
            joinPumps(stdoutPump, stderrPump);
            return new Result(process.exitValue(), text(stdout), text(stderr));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
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
        final Result result = run(Path.of("").toAbsolutePath(), List.of(posixShell(), "-c", "command -v " + executable));
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

    private static String posixShell() {
        final Path shell = Path.of("/bin/sh");
        if (Files.isExecutable(shell)) {
            return shell.toString();
        }
        return "sh";
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
        return timeoutMessage(command, Files.readString(stderrFile));
    }

    private String timeoutMessage(final List<String> command, final String stderr) {
        if (Strings2.isBlank(stderr)) {
            return "Timed out after " + (timeoutMillis / 1000L) + "s: " + commandLine(command);
        }
        return stderr + System.lineSeparator() + "Timed out after " + (timeoutMillis / 1000L) + "s: " + commandLine(command);
    }

    private static void copy(final InputStream input, final PrintStream destination, final ByteArrayOutputStream captured) {
        try (InputStream stream = input) {
            final byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                captured.write(buffer, 0, read);
                destination.write(buffer, 0, read);
                destination.flush();
            }
        } catch (IOException ignored) {
            // Process teardown owns the final exit result.
        }
    }

    private static void joinPumps(final Thread stdoutPump, final Thread stderrPump) throws InterruptedException {
        stdoutPump.join();
        stderrPump.join();
    }

    private static String text(final ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8);
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
