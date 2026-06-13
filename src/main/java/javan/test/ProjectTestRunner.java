package javan.test;

import javan.detect.ProjectLayout;
import javan.util.ProcessRunner;

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
        out.println("  " + String.join(" ", command));
        final ProcessRunner.Result result = processRunner.run(layout.root(), command);
        out.print(result.stdout());
        if (!result.stderr().isBlank()) {
            out.print(result.stderr());
        }
        return result.exitCode();
    }

    private static List<String> command(final ProjectLayout layout) {
        return switch (layout.buildTool()) {
            case MAVEN -> mavenCommand(layout.root());
            case GRADLE -> gradleCommand(layout.root());
            case JAVAC, NONE, JAR, CLASSES -> throw new IllegalArgumentException(
                "No configured test runner for " + layout.buildTool() + " projects. Add Maven or Gradle build files, or run tests directly."
            );
        };
    }

    private static List<String> mavenCommand(final Path root) {
        return Files.exists(root.resolve("mvnw"))
            ? List.of("./mvnw", "test")
            : List.of("mvn", "test");
    }

    private static List<String> gradleCommand(final Path root) {
        return Files.exists(root.resolve("gradlew"))
            ? List.of("./gradlew", "test")
            : List.of("gradle", "test");
    }
}
