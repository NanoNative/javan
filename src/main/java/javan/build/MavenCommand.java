package javan.build;

import javan.util.Strings2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the Maven wrapper or installed Maven command for a project.
 */
public final class MavenCommand {
    private MavenCommand() {
    }

    /**
     * Creates a platform-correct Maven command for a project.
     *
     * @param root project root containing an optional Maven wrapper
     * @param arguments Maven arguments after the launcher
     * @return complete process command
     */
    public static List<String> forProject(final Path root, final List<String> arguments) {
        return forProject(root, arguments, System.getProperty("os.name", ""));
    }

    static List<String> forProject(final Path root, final List<String> arguments, final String osName) {
        final List<String> command = new ArrayList<>();
        if (Strings2.toAsciiLowerCase(osName).contains("win")) {
            if (Files.exists(root.resolve("mvnw.cmd"))) {
                command.addAll(List.of("cmd", "/d", "/s", "/c", "mvnw.cmd"));
            } else {
                command.add("mvn");
            }
        } else if (Files.exists(root.resolve("mvnw"))) {
            command.addAll(List.of("sh", "./mvnw"));
        } else {
            command.add("mvn");
        }
        command.addAll(arguments);
        return List.copyOf(command);
    }
}
