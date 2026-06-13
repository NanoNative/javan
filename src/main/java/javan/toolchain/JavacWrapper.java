package javan.toolchain;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Delegates {@code javan javac} to the real {@code javac} available on the host toolchain.
 */
public final class JavacWrapper {
    /**
     * Runs javac in the requested working directory.
     *
     * @param cwd current working directory
     * @param out stdout destination
     * @param err stderr destination
     * @param args javac arguments
     * @return javac exit code
     * @throws IOException when javac cannot be started or output cannot be copied
     * @throws InterruptedException when interrupted while waiting for javac
     */
    public int run(final Path cwd, final PrintStream out, final PrintStream err, final List<String> args)
        throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add("javac");
        command.addAll(args);

        final Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
        final AtomicReference<IOException> stdoutFailure = new AtomicReference<>();
        final AtomicReference<IOException> stderrFailure = new AtomicReference<>();
        final Thread stdout = copyAsync("javan-javac-stdout", process.getInputStream(), out, stdoutFailure);
        final Thread stderr = copyAsync("javan-javac-stderr", process.getErrorStream(), err, stderrFailure);
        final int exitCode = process.waitFor();
        stdout.join();
        stderr.join();
        if (stdoutFailure.get() != null) {
            throw stdoutFailure.get();
        }
        if (stderrFailure.get() != null) {
            throw stderrFailure.get();
        }
        return exitCode;
    }

    private static Thread copyAsync(
        final String name,
        final InputStream input,
        final PrintStream output,
        final AtomicReference<IOException> failure
    ) {
        final Thread thread = new Thread(() -> {
            final byte[] buffer = new byte[8192];
            try {
                int read = input.read(buffer);
                while (read >= 0) {
                    output.write(buffer, 0, read);
                    read = input.read(buffer);
                }
                output.flush();
            } catch (final IOException exception) {
                failure.set(exception);
            }
        }, name);
        thread.start();
        return thread;
    }
}
