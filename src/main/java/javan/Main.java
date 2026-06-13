package javan;

import javan.cli.Cli;

import java.nio.file.Path;

/**
 * Process entry point for the javan command line tool.
 */
public final class Main {
    private Main() {
    }

    /**
     * Runs the command line interface and exits with its status code.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        final int exitCode = new Cli().run(Path.of("").toAbsolutePath(), System.out, System.err, args);
        System.exit(exitCode);
    }
}
