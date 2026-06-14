package javan.test;

import javan.detect.BuildTool;
import javan.detect.ProjectLayout;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Runs project tests through the detected external build tool.
 */
public final class ProjectTestRunner {
    private final ProcessRunner processRunner;

    /**
     * Creates a test runner with the default process timeout.
     */
    public ProjectTestRunner() {
        this(new ProcessRunner());
    }

    /**
     * Creates a test runner.
     *
     * @param processRunner process runner used for test tasks
     */
    public ProjectTestRunner(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Runs the configured project test task.
     *
     * @param layout detected project layout
     * @param out output stream
     * @return test process exit code
     * @throws IOException when the test process cannot be started
     * @throws InterruptedException when interrupted while waiting for the test process
     */
    public int run(final ProjectLayout layout, final PrintStream out) throws IOException, InterruptedException {
        final List<String> command = command(layout);
        out.println("Running tests:");
        out.println("  " + joinCommand(command));
        final ProcessRunner.Result result = processRunner.run(layout.root(), command);
        out.print(result.stdout());
        if (!Strings2.isBlank(result.stderr())) {
            out.print(result.stderr());
        }
        return result.exitCode();
    }

    private static List<String> command(final ProjectLayout layout) {
        if (layout.buildTool() == BuildTool.MAVEN) {
            return mavenCommand(layout.root());
        }
        if (layout.buildTool() == BuildTool.GRADLE) {
            return gradleCommand(layout.root());
        }
        if (layout.buildTool() == BuildTool.JAVAC
            || layout.buildTool() == BuildTool.NONE
            || layout.buildTool() == BuildTool.JAR
            || layout.buildTool() == BuildTool.CLASSES) {
            throw new IllegalArgumentException(
                "No configured test runner for " + layout.buildTool().name() + " projects. Add Maven or Gradle build files, or run tests directly."
            );
        }
        throw new IllegalStateException("Unsupported build tool");
    }

    private static List<String> mavenCommand(final Path root) {
        return Files.exists(root.resolve("mvnw"))
            ? List.of("./mvnw", "test")
            : List.of("mvn", "test");
    }

    private static String joinCommand(final List<String> command) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < command.size(); index++) {
            if (index > 0) {
                result.append(' ');
            }
            result.append(command.get(index));
        }
        return result.toString();
    }

    private static List<String> gradleCommand(final Path root) {
        return Files.exists(root.resolve("gradlew"))
            ? List.of("./gradlew", "test")
            : List.of("gradle", "test");
    }
}
