package javan.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
            try {
                final boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                if (!completed) {
                    stopProcessTree(process);
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
            } catch (final InterruptedException exception) {
                stopInterruptedProcess(process, exception);
                throw exception;
            }
        } finally {
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }

    /**
     * Runs a command and returns launcher or interruption failures as deterministic process results.
     *
     * <p>This is the concurrent-worker boundary. Exit code {@code 125} means interruption and
     * {@code 126} means the process could not be started or read.</p>
     *
     * @param workingDirectory process working directory
     * @param command command and arguments
     * @return captured result, including launcher failures
     */
    public Result runResult(final Path workingDirectory, final List<String> command) {
        try {
            return run(workingDirectory, command);
        } catch (final InterruptedException interruption) {
            Thread.currentThread().interrupt();
            return new Result(125, "", "Interrupted while running process");
        } catch (final IOException failure) {
            final String message = failure.getMessage();
            return new Result(126, "", message == null ? failure.getClass().getName() : message);
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
        return resolveExecutable(
            System.getenv("PATH"), executable, System.getenv("PATHEXT"), System.getProperty("os.name", "")
        ).isPresent();
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
            final Optional<Path> resolved = resolveExecutable(
                System.getenv("PATH"), executable, System.getenv("PATHEXT"), System.getProperty("os.name", "")
            );
            if (resolved.isPresent()) {
                return Optional.of(resolved.orElseThrow().toString());
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves an executable without invoking a shell.
     *
     * @param path search path
     * @param executable executable name or path
     * @param pathExt Windows executable extensions
     * @param osName operating-system name
     * @return resolved executable path
     */
    public static Optional<Path> resolveExecutable(
        final String path,
        final String executable,
        final String pathExt,
        final String osName
    ) {
        if (Strings2.isBlank(executable)) {
            return Optional.empty();
        }
        if (containsPathSeparator(executable)) {
            return resolveCandidate(Path.of(executable), pathExt, osName);
        }
        if (Strings2.isBlank(path)) {
            return Optional.empty();
        }
        int start = 0;
        for (int index = 0; index <= path.length(); index++) {
            if (index == path.length() || path.charAt(index) == java.io.File.pathSeparatorChar) {
                final String entry = Strings2.slice(path, start, index);
                if (!Strings2.isBlank(entry)) {
                    final Optional<Path> resolved = resolveCandidate(Path.of(entry).resolve(executable), pathExt, osName);
                    if (resolved.isPresent()) {
                        return resolved;
                    }
                }
                start = index + 1;
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> resolveCandidate(final Path candidate, final String pathExt, final String osName) {
        if (Files.isExecutable(candidate)) {
            return Optional.of(candidate);
        }
        if (!isWindows(osName) || hasExplicitExtension(candidate)) {
            return Optional.empty();
        }
        for (final String extension : windowsExtensions(pathExt)) {
            final Path extended = Path.of(candidate + extension);
            if (Files.isExecutable(extended)) {
                return Optional.of(extended);
            }
        }
        return Optional.empty();
    }

    private static List<String> windowsExtensions(final String pathExt) {
        if (Strings2.isBlank(pathExt)) {
            return List.of(".exe", ".cmd", ".bat", ".com");
        }
        final List<String> extensions = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= pathExt.length(); index++) {
            if (index == pathExt.length() || pathExt.charAt(index) == ';') {
                final String extension = Strings2.toAsciiLowerCase(Strings2.slice(pathExt, start, index).trim());
                if (!Strings2.isBlank(extension)) {
                    extensions.add(extension.startsWith(".") ? extension : "." + extension);
                }
                start = index + 1;
            }
        }
        if (extensions.isEmpty()) {
            return List.of(".exe", ".cmd", ".bat", ".com");
        }
        return List.copyOf(extensions);
    }

    private static boolean containsPathSeparator(final String executable) {
        for (int index = 0; index < executable.length(); index++) {
            if (executable.charAt(index) == '/' || executable.charAt(index) == '\\') {
                return true;
            }
        }
        return false;
    }

    private static boolean hasExplicitExtension(final Path candidate) {
        final Path fileName = candidate.getFileName();
        if (fileName == null) {
            return false;
        }
        final String name = fileName.toString();
        final int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1;
    }

    private static boolean isWindows(final String osName) {
        return Strings2.toAsciiLowerCase(osName).contains("win");
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
        final String stderr = Files.readString(stderrFile);
        if (Strings2.isBlank(stderr)) {
            return "Timed out after " + (timeoutMillis / 1000L) + "s: " + commandLine(command);
        }
        return stderr + System.lineSeparator() + "Timed out after " + (timeoutMillis / 1000L) + "s: " + commandLine(command);
    }

    private static void stopInterruptedProcess(final Process process, final InterruptedException interruption) {
        try {
            stopProcessTree(process);
        } catch (final IOException cleanup) {
            interruption.addSuppressed(cleanup);
        } catch (final InterruptedException cleanup) {
            interruption.addSuppressed(cleanup);
            Thread.currentThread().interrupt();
        }
    }

    private static void stopProcessTree(final Process process) throws IOException, InterruptedException {
        final List<ProcessHandle> processes = processTree(process);
        final ProcessHandle root = process.toHandle();
        for (int index = 0; index < 5; index++) {
            addProcessTree(processes, process);
            stopDescendants(processes, root, false);
            Thread.sleep(10L);
        }
        stopProcess(root, false);
        if (waitForProcessesExit(processes, 1L)) {
            return;
        }
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        do {
            addProcessTree(processes, process);
            stopProcesses(processes, true);
            if (allProcessesExited(processes)) {
                return;
            }
            Thread.sleep(10L);
        } while (System.nanoTime() < deadline);
        if (!allProcessesExited(processes)) {
            throw new IOException("Could not stop child process tree");
        }
    }

    private static List<ProcessHandle> processTree(final Process process) {
        final List<ProcessHandle> tree = new ArrayList<>();
        addProcessTree(tree, process);
        tree.sort(Comparator.comparingInt(ProcessRunner::processDepth).reversed());
        return tree;
    }

    private static void addProcessTree(final List<ProcessHandle> processes, final Process process) {
        addProcess(processes, process.toHandle());
        for (int index = 0; index < processes.size(); index++) {
            final ProcessHandle known = processes.get(index);
            known.descendants().forEach(descendant -> addProcess(processes, descendant));
        }
        processes.sort(Comparator.comparingInt(ProcessRunner::processDepth).reversed());
    }

    private static void addProcess(final List<ProcessHandle> processes, final ProcessHandle candidate) {
        for (final ProcessHandle process : processes) {
            if (process.pid() == candidate.pid()) {
                return;
            }
        }
        processes.add(candidate);
    }

    private static int processDepth(final ProcessHandle process) {
        int depth = 0;
        Optional<ProcessHandle> parent = process.parent();
        while (parent.isPresent()) {
            depth++;
            parent = parent.orElseThrow().parent();
        }
        return depth;
    }

    private static void stopProcesses(final List<ProcessHandle> processes, final boolean forcibly) {
        for (final ProcessHandle process : processes) {
            stopProcess(process, forcibly);
        }
    }

    private static void stopDescendants(
        final List<ProcessHandle> processes,
        final ProcessHandle root,
        final boolean forcibly
    ) {
        for (final ProcessHandle process : processes) {
            if (process.pid() != root.pid()) {
                stopProcess(process, forcibly);
            }
        }
    }

    private static void stopProcess(final ProcessHandle process, final boolean forcibly) {
        if (!process.isAlive()) {
            return;
        }
        if (forcibly) {
            process.destroyForcibly();
        } else {
            process.destroy();
        }
    }

    private static boolean waitForProcessesExit(final List<ProcessHandle> processes, final long seconds)
        throws InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (allProcessesExited(processes)) {
                return true;
            }
            Thread.sleep(10L);
        }
        return allProcessesExited(processes);
    }

    private static boolean allProcessesExited(final List<ProcessHandle> processes) {
        for (final ProcessHandle process : processes) {
            if (process.isAlive()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Captured process result.
     *
     * @param exitCode process exit code
     * @param stdout standard output
     * @param stderr standard error
     */
    public record Result(int exitCode, String stdout, String stderr) {
        /**
         * Returns whether the native runtime stopped this command because its calling thread was interrupted.
         *
         * @return true only for the reserved native interruption result
         */
        public boolean interrupted() {
            return exitCode == 125 && "Interrupted while running process".equals(stderr);
        }
    }
}
