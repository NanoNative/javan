package javan.cli;

import javan.util.Strings2;

import java.util.Optional;

/**
 * Supported command line commands.
 */
public enum Command {
    HELP,
    VERSION,
    INSPECT,
    CHECK,
    TEST,
    BUILD,
    RUN,
    JAVAC,
    COMPAT,
    REPORT,
    CLEAN,
    DOCTOR,
    TOOLCHAIN;

    /**
     * Parses a command name.
     *
     * @param value raw command
     * @return parsed command when supported
     */
    public static Optional<Command> parse(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiUpperCase(value), '-', '_');
        for (final Command command : values()) {
            if (command.name().equals(normalized)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }
}
