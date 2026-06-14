package javan.toolchain;

import javan.util.ProcessRunner;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Delegates {@code javan javac} to the real {@code javac} available on the host toolchain.
 */
public final class JavacWrapper {
    private final ProcessRunner processRunner;

    /**
     * Creates a javac wrapper with the default process timeout.
     */
    public JavacWrapper() {
        this(new ProcessRunner());
    }

    /**
     * Creates a javac wrapper.
     *
     * @param processRunner process runner used to invoke javac
     */
    public JavacWrapper(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

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

        final ProcessRunner.Result result = processRunner.run(cwd, command);
        out.print(result.stdout());
        err.print(result.stderr());
        return result.exitCode();
    }
}
