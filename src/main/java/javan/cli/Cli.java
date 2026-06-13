package javan.cli;

import javan.Javan;
import javan.toolchain.JavacWrapper;
import javan.toolchain.ToolchainManager;
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
            final Options options = Options.parse(args);
            return switch (options.command()) {
                case HELP -> {
                    out.println(help());
                    yield 0;
                }
                case VERSION -> {
                    out.println(Version.full());
                    yield 0;
                }
                case INSPECT -> {
                    javan.inspect(cwd, options, out);
                    yield 0;
                }
                case CHECK -> {
                    javan.check(cwd, options, out);
                    yield 0;
                }
                case TEST -> javan.test(cwd, options, out);
                case BUILD -> {
                    javan.build(cwd, options, out);
                    yield 0;
                }
                case RUN -> javan.run(cwd, options, out);
                case JAVAC -> javacWrapper.run(cwd, out, err, options.passthroughArgs());
                case COMPAT -> javan.compat(cwd, options, out).pass() ? 0 : 2;
                case CLEAN -> {
                    javan.clean(cwd, options, out);
                    yield 0;
                }
                case DOCTOR -> {
                    out.println(toolchainManager.doctor());
                    yield 0;
                }
                case TOOLCHAIN -> {
                    out.println(toolchain(options));
                    yield 0;
                }
            };
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

    private static String help() {
        return """
            javan %s

            Usage:
              javan --version
              javan inspect [path]
              javan check [path] [--main com.acme.Main]
              javan test [path]
              javan build [path] [--main com.acme.Main] [--profile core|service|library|strict] [--output app]
              javan build [path] --kind jar
              javan build [path] --kind sharedlib --export com.acme.Math.add --bindings c,rust,go,python
              javan run [path] [--main com.acme.Main] [-- args...]
              javan javac [javac args...]
              javan compat [path] [--main com.acme.Main]
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
              --kind <kind>          app, jar, staticlib, or sharedlib
              --profile <profile>    core, service, library, or strict
              --export <method>      exported library method
              --bindings <list>      c,rust,go,python
              --release             enable release build mode
              --target <triple>      requested target triple
              --no-build            reuse existing class files
            """.formatted(Version.number());
    }

    private String toolchain(final Options options) {
        final String subcommand = options.target()
            .map(Path::toString)
            .orElseThrow(() -> new IllegalArgumentException("Missing toolchain command: list or doctor"));
        if (!options.passthroughArgs().isEmpty()) {
            throw new IllegalArgumentException("Unexpected toolchain arguments: " + String.join(" ", options.passthroughArgs()));
        }
        return switch (subcommand) {
            case "list" -> toolchainManager.listToolchains();
            case "doctor" -> toolchainManager.doctor();
            default -> throw new IllegalArgumentException("Unsupported toolchain command: " + subcommand);
        };
    }
}
