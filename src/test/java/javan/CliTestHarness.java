package javan;

import javan.cli.Cli;
import org.opentest4j.AssertionFailedError;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class CliTestHarness {
    private CliTestHarness() {
    }

    static CliResult run(final Path cwd, final Duration timeout, final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (
            PrintStream stdoutStream = new PrintStream(stdout, true, StandardCharsets.UTF_8);
            PrintStream stderrStream = new PrintStream(stderr, true, StandardCharsets.UTF_8)
        ) {
            final FutureTask<Integer> task = new FutureTask<>(() -> new Cli().run(cwd, stdoutStream, stderrStream, args));
            final Thread worker = Thread.ofVirtual().name("cli-test-", 0).start(task);
            try {
                final int exitCode = task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                return new CliResult(
                    exitCode,
                    stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8)
                );
            } catch (final TimeoutException exception) {
                task.cancel(true);
                worker.interrupt();
                throw new AssertionFailedError(
                    "Cli.run timed out after "
                        + timeout.toSeconds()
                        + " seconds: "
                        + String.join(" ", args)
                        + System.lineSeparator()
                        + "stdout:"
                        + System.lineSeparator()
                        + stdout.toString(StandardCharsets.UTF_8)
                        + System.lineSeparator()
                        + "stderr:"
                        + System.lineSeparator()
                        + stderr.toString(StandardCharsets.UTF_8),
                    exception
                );
            } catch (final InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Cli.run.", exception);
            } catch (final ExecutionException exception) {
                throw rethrow(exception.getCause());
            }
        }
    }

    static ProcessResult process(final Path cwd, final List<String> command, final Duration timeout) {
        return process(cwd, command, timeout, Map.of());
    }

    static ProcessResult process(
        final Path cwd,
        final List<String> command,
        final Duration timeout,
        final Map<String, String> environment
    ) {
        final List<String> actualCommand = childCoverageCommand(command);
        final TestProcesses.Result result = TestProcesses.run(cwd, actualCommand, timeout, environment);
        return new ProcessResult(result.exitCode(), result.stdout(), result.stderr());
    }

    static boolean commandAvailable(final String command) {
        try {
            return process(
                Path.of("").toAbsolutePath(),
                List.of(command, "--version"),
                Duration.ofSeconds(5)
            ).exitCode() == 0;
        } catch (final RuntimeException exception) {
            return false;
        }
    }

    private static List<String> childCoverageCommand(final List<String> command) {
        final String agent = childCoverageAgentTemplate();
        final String directory = System.getProperty("javan.childJacocoDir", "");
        if (agent.isBlank() || directory.isBlank() || !isJavanMainJavaCommand(command)) {
            return command;
        }
        try {
            final Path coverageDirectory = Path.of(directory);
            Files.createDirectories(coverageDirectory);
            final Path exec = coverageDirectory.resolve("child-" + UUID.randomUUID() + ".exec");
            final List<String> result = new ArrayList<>();
            result.add(command.getFirst());
            result.add(childCoverageAgent(agent, exec));
            result.addAll(command.subList(1, command.size()));
            return List.copyOf(result);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String childCoverageAgentTemplate() {
        final String configured = System.getProperty("javan.childJacocoArgLine", "");
        if (!configured.isBlank()) {
            return configured;
        }
        for (final String argument : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("org.jacoco.agent")) {
                return argument;
            }
        }
        return "";
    }

    private static boolean isJavanMainJavaCommand(final List<String> command) {
        if (command.isEmpty()) {
            return false;
        }
        final Path executable = Path.of(command.getFirst()).getFileName();
        if (executable == null) {
            return false;
        }
        final String name = executable.toString();
        if (!"java".equals(name) && !"java.exe".equals(name)) {
            return false;
        }
        for (int index = 1; index < command.size(); index++) {
            final String argument = command.get(index);
            if ("-cp".equals(argument) || "-classpath".equals(argument) || "--class-path".equals(argument)) {
                index++;
            } else if (!argument.startsWith("-")) {
                return "javan.Main".equals(argument);
            }
        }
        return false;
    }

    private static String childCoverageAgent(final String agent, final Path exec) {
        final String key = "destfile=";
        final int start = agent.indexOf(key);
        if (start < 0) {
            return agent;
        }
        final int valueStart = start + key.length();
        final int valueEnd = agent.indexOf(',', valueStart);
        if (valueEnd < 0) {
            return agent.substring(0, valueStart) + exec;
        }
        return agent.substring(0, valueStart) + exec + agent.substring(valueEnd);
    }

    private static RuntimeException rethrow(final Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    record CliResult(int exitCode, String stdout, String stderr) {
    }

    record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
