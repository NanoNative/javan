package javan.cli;

import java.util.Arrays;
import java.util.Locale;
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
        return Arrays.stream(values())
            .filter(command -> command.name().equals(value.toUpperCase(Locale.ROOT).replace('-', '_')))
            .findFirst();
    }
}
