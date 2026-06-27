package javan;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class TestProcesses {
    private TestProcesses() {
    }

    public static Result run(final Path cwd, final List<String> command, final Duration timeout) {
        return run(cwd, command, timeout, Map.of());
    }

    public static Result run(
        final Path cwd,
        final List<String> command,
        final Duration timeout,
        final Map<String, String> environment
    ) {
        try (RunningProcess process = start(cwd, command, environment)) {
            return process.await(timeout);
        }
    }

    public static RunningProcess start(final Path cwd, final List<String> command) {
        return start(cwd, command, Map.of());
    }

    public static RunningProcess start(
        final Path cwd,
        final List<String> command,
        final Map<String, String> environment
    ) {
        try {
            final ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile());
            builder.environment().putAll(environment);
            final Process process = builder.start();
            final StreamCollector stdout = new StreamCollector(process.getInputStream());
            final StreamCollector stderr = new StreamCollector(process.getErrorStream());
            Thread.ofVirtual().name("test-process-stdout-", 0).start(stdout);
            Thread.ofVirtual().name("test-process-stderr-", 0).start(stderr);
            return new RunningProcess(process, stdout, stderr, command);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public record Result(int exitCode, String stdout, String stderr) {
    }

    public static final class RunningProcess implements AutoCloseable {
        private final Process process;
        private final StreamCollector stdout;
        private final StreamCollector stderr;
        private final String commandText;
        private boolean closed;

        private RunningProcess(
            final Process process,
            final StreamCollector stdout,
            final StreamCollector stderr,
            final List<String> command
        ) {
            this.process = process;
            this.stdout = stdout;
            this.stderr = stderr;
            this.commandText = String.join(" ", command);
        }

        public Result await(final Duration timeout) {
            try {
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    terminate();
                    return new Result(
                        124,
                        stdout.await(Duration.ofSeconds(1)),
                        stderr.await(Duration.ofSeconds(1))
                            + "Timed out after "
                            + timeout.toSeconds()
                            + " seconds: "
                            + commandText
                            + "\n"
                    );
                }
                closeQuietly(process.getOutputStream());
                return new Result(
                    process.exitValue(),
                    stdout.await(Duration.ofSeconds(1)),
                    stderr.await(Duration.ofSeconds(1))
                );
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while running process: " + commandText, exception);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                if (process.isAlive()) {
                    terminate();
                } else {
                    closeChildStreams();
                }
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while closing process: " + commandText, exception);
            } finally {
                stdout.await(Duration.ofSeconds(1));
                stderr.await(Duration.ofSeconds(1));
            }
        }

        private void terminate() throws InterruptedException {
            final List<ProcessHandle> tree = processTree();
            destroyTree(tree, false);
            if (!waitForTermination(tree, Duration.ofSeconds(1))) {
                destroyTree(tree, true);
                if (!waitForTermination(tree, Duration.ofSeconds(2))) {
                    closeChildStreams();
                    throw new IllegalStateException("Timed out while terminating process tree: " + commandText);
                }
            }
            closeChildStreams();
        }

        private List<ProcessHandle> processTree() {
            final ArrayList<ProcessHandle> tree = new ArrayList<>();
            try (Stream<ProcessHandle> descendants = process.toHandle().descendants()) {
                tree.addAll(descendants.toList());
            }
            tree.add(process.toHandle());
            return tree;
        }

        private static void destroyTree(final List<ProcessHandle> tree, final boolean forcibly) {
            for (final ProcessHandle handle : tree) {
                if (!handle.isAlive()) {
                    continue;
                }
                if (forcibly) {
                    handle.destroyForcibly();
                } else {
                    handle.destroy();
                }
            }
        }

        private static boolean waitForTermination(final List<ProcessHandle> tree, final Duration timeout) throws InterruptedException {
            final long deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                boolean alive = false;
                for (final ProcessHandle handle : tree) {
                    if (handle.isAlive()) {
                        alive = true;
                        break;
                    }
                }
                if (!alive) {
                    return true;
                }
                Thread.sleep(20L);
            }
            for (final ProcessHandle handle : tree) {
                if (handle.isAlive()) {
                    return false;
                }
            }
            return true;
        }

        private void closeChildStreams() {
            closeQuietly(process.getOutputStream());
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
        }
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream stream;
        private final CountDownLatch done = new CountDownLatch(1);
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private StreamCollector(final InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            final byte[] chunk = new byte[1024];
            try {
                while (true) {
                    final int read = stream.read(chunk);
                    if (read < 0) {
                        return;
                    }
                    append(chunk, read);
                }
            } catch (final IOException exception) {
                // Forced child shutdown can close pipes before EOF; keep partial output.
            } finally {
                done.countDown();
            }
        }

        private void append(final byte[] chunk, final int read) {
            synchronized (buffer) {
                buffer.write(chunk, 0, read);
            }
        }

        private String await(final Duration timeout) {
            try {
                done.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while collecting child process output.", exception);
            }
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
    }

    private static void closeQuietly(final java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (final IOException exception) {
            // Ignore shutdown noise from already-closed pipes.
        }
    }
}
