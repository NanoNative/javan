package javan.cli;

import javan.Javan;
import javan.toolchain.JavacWrapper;
import javan.toolchain.ToolchainManager;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Command line facade for javan.
 */
public final class Cli {
    private final Javan javan = new Javan();
    private final JavacWrapper javacWrapper = new JavacWrapper();
    private final ToolchainManager toolchainManager = new ToolchainManager();

    /**
     * Runs the command line interface.
     *
     * @param cwd current working directory
     * @param out stdout
     * @param err stderr
     * @param args command line arguments
     * @return process exit code
     */
    public int run(final Path cwd, final PrintStream out, final PrintStream err, final String... args) {
        try {
            return runUnchecked(cwd, out, err, args);
        } catch (final DiagnosticException exception) {
            err.println(exception.diagnostic().format());
            return 2;
        } catch (final IllegalArgumentException exception) {
            err.println("error[JAVAN900]: " + exception.getMessage());
            return 2;
        } catch (final IOException exception) {
            err.println("error[JAVAN901]: " + exception.getMessage());
            return 1;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            err.println("error[JAVAN902]: interrupted");
            return 130;
        }
    }

    /**
     * Runs the process entrypoint without reachable broad catch handlers.
     *
     * @param cwd current working directory
     * @param out stdout
     * @param err stderr
     * @param args command line arguments
     * @return process exit code
     * @throws IOException when command IO fails
     * @throws InterruptedException when interrupted
     */
    public int runProcess(final Path cwd, final PrintStream out, final PrintStream err, final String... args)
        throws IOException, InterruptedException {
        final Options.ParseResult parsed = Options.parseResult(args);
        if (!parsed.pass()) {
            err.println("error[JAVAN900]: " + parsed.error());
            return 2;
        }
        return runParsed(cwd, out, err, parsed.options());
    }

    /**
     * Runs the command line interface without broad catch handlers.
     *
     * @param cwd current working directory
     * @param out stdout
     * @param err stderr
     * @param args command line arguments
     * @return process exit code
     * @throws IOException when command IO fails
     * @throws InterruptedException when interrupted
     */
    public int runUnchecked(final Path cwd, final PrintStream out, final PrintStream err, final String... args)
        throws IOException, InterruptedException {
        final Options options = Options.parse(args);
        return runParsed(cwd, out, err, options);
    }

    private int runParsed(final Path cwd, final PrintStream out, final PrintStream err, final Options options)
        throws IOException, InterruptedException {
        final Command command = options.command();
        if (command == Command.HELP) {
            out.println(help());
            return 0;
        }
        if (command == Command.VERSION) {
            out.println(Version.full());
            return 0;
        }
        if (command == Command.INSPECT) {
            javan.inspect(cwd, options, out);
            return 0;
        }
        if (command == Command.CHECK) {
            return finishDiagnostics(javan.check(cwd, options, out).diagnostics(), err, 0);
        }
        if (command == Command.TEST) {
            return javan.test(cwd, options, out);
        }
        if (command == Command.BUILD) {
            return finishDiagnostics(javan.build(cwd, options, out).diagnostics(), err, 0);
        }
        if (command == Command.RUN) {
            final Javan.RunResult result = javan.run(cwd, options, out);
            return finishDiagnostics(result.diagnostics(), err, result.exitCode());
        }
        if (command == Command.JAVAC) {
            return javacWrapper.run(cwd, out, err, options.passthroughArgs());
        }
        if (command == Command.COMPAT) {
            return javan.compat(cwd, options, out).pass() ? 0 : 2;
        }
        if (command == Command.REPORT) {
            javan.report(cwd, options, out);
            return 0;
        }
        if (command == Command.CLEAN) {
            javan.clean(cwd, options, out);
            return 0;
        }
        if (command == Command.DOCTOR) {
            out.println(toolchainManager.doctor());
            return 0;
        }
        if (command == Command.TOOLCHAIN) {
            out.println(toolchain(options));
            return 0;
        }
        throw new IllegalStateException("Unsupported command");
    }

    private static String help() {
        return "javan " + Version.number() + """

            Usage:
              javan --version
              javan inspect [path]
              javan check [path] [--main com.acme.Main]
              javan test [path]
              javan build [path] [--main com.acme.Main] [--profile core|service|library|strict] [--output app]
              javan build [path] --jar
              javan build [path] --library --export com.acme.Math.add --bindings c,rust,go,python
              javan run [path] [--main com.acme.Main] [-- args...]
              javan javac [javac args...]
              javan compat [path] [--main com.acme.Main]
              javan report [path]
              javan clean [path]
              javan doctor
              javan toolchain list
              javan toolchain doctor

            Inputs:
              project directory, classes directory, jar file, or single Java source file

            Options:
              --version            print version
              --main <class>        explicit main class
              --classes <dir>       explicit class folder
              --classpath <paths>   dependency classpath
              --output, -o <name>   output executable name
              --jar                  build a JVM jar
              --library, --lib       build a native library package
              --format <formats>     static, shared, or both for library builds
              --kind <kind>          app, jar, library, staticlib, or sharedlib
              --profile <profile>    core, service, library, or strict
              --export <method>      exported library method
              --bindings <list>      c,rust,go,python
              --release             enable release build mode
              --target <triple>      assert host target for native build
            """;
    }

    private String toolchain(final Options options) throws IOException {
        if (options.target().isEmpty()) {
            throw new IllegalArgumentException("Missing toolchain command: list or doctor");
        }
        final String subcommand = options.target().orElseThrow().toString();
        if (!options.passthroughArgs().isEmpty()) {
            throw new IllegalArgumentException("Unexpected toolchain arguments: " + joinArgs(options.passthroughArgs()));
        }
        if ("list".equals(subcommand)) {
            return toolchainManager.listToolchains();
        }
        if ("doctor".equals(subcommand)) {
            return toolchainManager.doctor();
        }
        throw new IllegalArgumentException("Unsupported toolchain command: " + subcommand);
    }

    private static String joinArgs(final java.util.List<String> values) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(' ');
            }
            result.append(values.get(index));
        }
        return result.toString();
    }

    private static int finishDiagnostics(final java.util.List<Diagnostic> diagnostics, final PrintStream err, final int successCode) {
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                err.println(diagnostic.format());
                return 2;
            }
        }
        return successCode;
    }
}
