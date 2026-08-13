package javan.toolchain.facade;

import javan.util.ProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Delegates the Javan javac facade to a selected real javac executable.
 */
public final class JavacWrapper {
    private static final String HEX = "0123456789abcdef";
    private final ProcessRunner processRunner;

    /**
     * Creates a javac facade backed by a process runner.
     *
     * @param processRunner process runner used to invoke javac
     */
    JavacWrapper(final ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    /**
     * Creates a javac facade using the default process runner.
     */
    public JavacWrapper() {
        this(new ProcessRunner());
    }

    /**
     * Invokes a selected javac executable without writing its captured output.
     *
     * @param cwd current working directory
     * @param javacExecutable selected real javac executable
     * @param args javac arguments
     * @return captured javac result
     * @throws IOException when javac cannot be started
     * @throws InterruptedException when interrupted while waiting for javac
     */
    public ProcessRunner.Result invoke(final Path cwd, final Path javacExecutable, final List<String> args)
        throws IOException, InterruptedException {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(javacExecutable, "javacExecutable");
        Objects.requireNonNull(args, "args");
        final List<String> command = new ArrayList<>();
        command.add(javacExecutable.toAbsolutePath().normalize().toString());
        command.addAll(args);
        return processRunner.run(cwd, command);
    }

    /**
     * Parses Javan-owned facade arguments while preserving every backend argument.
     *
     * @param args raw javac facade arguments
     * @return parse result
     */
    public static FacadeArguments parseFacadeArguments(final List<String> args) {
        Objects.requireNonNull(args, "args");
        final List<String> javacArgs = new ArrayList<>();
        boolean javanHelp = false;
        boolean javanVersion = false;
        FacadeMode mode = FacadeMode.REPORT;
        boolean passthrough = false;
        Optional<String> classOutput = Optional.empty();
        Optional<String> classpath = Optional.empty();
        Optional<String> mainClass = Optional.empty();
        Optional<String> outputName = Optional.empty();
        final List<String> targets = new ArrayList<>();
        Optional<String> diagnosticFormat = Optional.empty();
        for (int index = 0; index < args.size(); index++) {
            final String arg = Objects.requireNonNull(args.get(index), "arg");
            if (passthrough) {
                javacArgs.add(arg);
                continue;
            }
            if ("--jn-end".equals(arg) || "-jn-end".equals(arg)) {
                passthrough = true;
                continue;
            }
            if ("--jn-help".equals(arg) || "-jn-help".equals(arg)) {
                javanHelp = true;
                continue;
            }
            if ("--jn-version".equals(arg) || "-jn-version".equals(arg)) {
                javanVersion = true;
                continue;
            }
            if ("--jn-off".equals(arg) || "-jn-off".equals(arg)) {
                final Optional<String> conflict = modeConflict(mode, FacadeMode.OFF);
                if (conflict.isPresent()) {
                    return FacadeArguments.failure(conflict.orElseThrow());
                }
                mode = FacadeMode.OFF;
                continue;
            }
            if ("--jn-warn".equals(arg) || "-jn-warn".equals(arg)) {
                final Optional<String> conflict = modeConflict(mode, FacadeMode.WARN);
                if (conflict.isPresent()) {
                    return FacadeArguments.failure(conflict.orElseThrow());
                }
                mode = FacadeMode.WARN;
                continue;
            }
            if ("--jn-strict".equals(arg) || "-jn-strict".equals(arg)) {
                final Optional<String> conflict = modeConflict(mode, FacadeMode.STRICT);
                if (conflict.isPresent()) {
                    return FacadeArguments.failure(conflict.orElseThrow());
                }
                mode = FacadeMode.STRICT;
                continue;
            }
            if ("--jn-build".equals(arg) || "-jn-build".equals(arg)) {
                final Optional<String> conflict = modeConflict(mode, FacadeMode.BUILD);
                if (conflict.isPresent()) {
                    return FacadeArguments.failure(conflict.orElseThrow());
                }
                mode = FacadeMode.BUILD;
                continue;
            }
            if ("--jn-main".equals(arg) || "-jn-main".equals(arg)) {
                final Optional<String> value = nextValue(args, index);
                if (value.isEmpty()) {
                    return FacadeArguments.failure("Missing value for " + arg);
                }
                mainClass = value;
                index++;
                continue;
            }
            if ("--jn-out".equals(arg) || "-jn-out".equals(arg)) {
                final Optional<String> value = nextValue(args, index);
                if (value.isEmpty()) {
                    return FacadeArguments.failure("Missing value for " + arg);
                }
                outputName = value;
                index++;
                continue;
            }
            if ("--jn-target".equals(arg) || "-jn-target".equals(arg)) {
                final Optional<String> value = nextValue(args, index);
                if (value.isEmpty()) {
                    return FacadeArguments.failure("Missing value for " + arg);
                }
                targets.add(value.orElseThrow());
                index++;
                continue;
            }
            if ("--jn-diag".equals(arg) || "-jn-diag".equals(arg)) {
                final Optional<String> value = nextValue(args, index);
                if (value.isEmpty()) {
                    return FacadeArguments.failure("Missing value for " + arg);
                }
                if (!diagnosticFormat(value.orElseThrow())) {
                    return FacadeArguments.failure("Unsupported Javan diagnostic format: " + value.orElseThrow());
                }
                diagnosticFormat = value;
                index++;
                continue;
            }
            if (arg.startsWith("--jn-") || arg.startsWith("-jn-")) {
                return FacadeArguments.failure("Unsupported Javan compiler option: " + arg);
            }
            javacArgs.add(arg);
            if ("-d".equals(arg)) {
                final Optional<String> value = nextValue(args, index);
                if (value.isPresent()) {
                    final String output = value.orElseThrow();
                    javacArgs.add(output);
                    classOutput = Optional.of(output);
                    index++;
                }
                continue;
            }
            if (classpathOption(arg)) {
                final Optional<String> value = nextValue(args, index);
                if (value.isPresent()) {
                    final String valueText = value.orElseThrow();
                    javacArgs.add(valueText);
                    classpath = Optional.of(valueText);
                    index++;
                }
                continue;
            }
            if (arg.startsWith("--class-path=")) {
                classpath = Optional.of(arg.substring("--class-path=".length()));
            }
        }
        return FacadeArguments.success(
            List.copyOf(javacArgs),
            javanHelp,
            javanVersion,
            mode,
            classOutput,
            classpath,
            mainClass,
            outputName,
            List.copyOf(targets),
            diagnosticFormat
        );
    }

    /**
     * Returns the currently implemented Javan facade help section.
     *
     * @return human-readable extension help
     */
    public static String facadeHelp() {
        return """

            Javan extensions
              --jn-help, -jn-help       show this Javan section without invoking javac
              --jn-version, -jn-version show Javan and selected-backend identity
              --jn-off, -jn-off         disable Javan analysis and reports for this compile
              --jn-warn, -jn-warn       print Javan findings without changing javac success
              --jn-strict, -jn-strict   fail after successful javac when Javan finds blockers
              --jn-build, -jn-build     build a native app from fresh -d class output
              --jn-main, -jn-main <C>   native application main class
              --jn-out, -jn-out <name>  native output name below .javan/bin
              --jn-target, -jn-target <os/arch>
                                      host target assertion for native build
              --jn-diag, -jn-diag <auto|compiler|pretty|jsonl>
                                      finding presentation for warn, strict, and build
              --jn-end, -jn-end         pass all following arguments to javac unchanged

            Javan writes an invocation report after successful or failed javac runs by default.
            Full native compatibility analysis requires javac -d <classes> output.
            `--jn-build` requires javac -d <classes>; it never recompiles Java source.
            Use `javan check` for an explicit report-only run outside compilation.
            """;
    }

    /**
     * Writes the immutable outcome of one facade invocation.
     *
     * @param outputDirectory Javan output directory, normally {@code .javan}
     * @param outcome invocation outcome
     * @return JSON report path
     * @throws IOException when the report cannot be written
     */
    public static Path writeReport(final Path outputDirectory, final InvocationOutcome outcome) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(outcome, "outcome");
        final Path reports = outputDirectory.toAbsolutePath().normalize().resolve("reports");
        Files.createDirectories(reports);
        final Path json = reports.resolve("javac-invocation.json");
        Files.writeString(json, invocationJson(outcome));
        Files.writeString(reports.resolve("javac-invocation.md"), invocationMarkdown(outcome));
        return json;
    }

    private static String invocationJson(final InvocationOutcome outcome) {
        final String classOutput = outcome.classOutput().isPresent()
            ? "\"" + json(outcome.classOutput().orElseThrow().toString()) + "\""
            : "null";
        return "{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"javacExitCode\": " + outcome.javacExitCode() + ",\n"
            + "  \"analysis\": \"" + json(outcome.analysis()) + "\",\n"
            + "  \"reason\": \"" + json(outcome.reason()) + "\",\n"
            + "  \"classOutput\": " + classOutput + ",\n"
            + "  \"diagnostics\": " + outcome.diagnosticCount() + "\n"
            + "}\n";
    }

    private static String invocationMarkdown(final InvocationOutcome outcome) {
        final String classOutput = outcome.classOutput().isPresent()
            ? outcome.classOutput().orElseThrow().toString()
            : "not available";
        return "# Javan Javac Invocation\n\n"
            + "- javac exit code: " + outcome.javacExitCode() + '\n'
            + "- native analysis: " + outcome.analysis() + '\n'
            + "- reason: " + outcome.reason() + '\n'
            + "- class output: " + classOutput + '\n'
            + "- diagnostics: " + outcome.diagnosticCount() + '\n';
    }

    private static String json(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '\\' || character == '"') {
                result.append('\\');
            }
            if (character == '\n') {
                result.append("\\n");
            } else if (character == '\r') {
                result.append("\\r");
            } else if (character == '\t') {
                result.append("\\t");
            } else if (character < ' ') {
                result.append("\\u00").append(HEX.charAt((character >>> 4) & 0x0f)).append(HEX.charAt(character & 0x0f));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static boolean classpathOption(final String arg) {
        return "-cp".equals(arg) || "-classpath".equals(arg) || "--class-path".equals(arg);
    }

    private static Optional<String> nextValue(final List<String> args, final int index) {
        if (index + 1 >= args.size()) {
            return Optional.empty();
        }
        return Optional.of(Objects.requireNonNull(args.get(index + 1), "arg"));
    }

    private static Optional<String> modeConflict(final FacadeMode current, final FacadeMode requested) {
        if (current == FacadeMode.REPORT || current == requested) {
            return Optional.empty();
        }
        return Optional.of("Conflicting Javan compiler modes: " + modeName(current) + " and " + modeName(requested));
    }

    private static String modeName(final FacadeMode mode) {
        if (mode == FacadeMode.OFF) {
            return "--jn-off";
        }
        if (mode == FacadeMode.WARN) {
            return "--jn-warn";
        }
        if (mode == FacadeMode.STRICT) {
            return "--jn-strict";
        }
        if (mode == FacadeMode.BUILD) {
            return "--jn-build";
        }
        return "--jn-report";
    }

    private static boolean diagnosticFormat(final String value) {
        return "auto".equals(value)
            || "compiler".equals(value)
            || "pretty".equals(value)
            || "jsonl".equals(value);
    }

    /**
     * Parsed facade argument result.
     *
     * @param pass whether parsing succeeded
     * @param javacArgs preserved backend arguments
     * @param javanHelp whether Javan-only help was requested
     * @param javanVersion whether Javan-only version information was requested
     * @param mode selected post-compile mode
     * @param classOutput javac class-output value when {@code -d} is present
     * @param classpath javac classpath value when present
     * @param mainClass explicit native application main class
     * @param outputName explicit native output name
     * @param targets requested native targets
     * @param diagnosticFormat selected finding presentation when present
     * @param error deterministic parse error when parsing failed
     */
    public record FacadeArguments(
        boolean pass,
        List<String> javacArgs,
        boolean javanHelp,
        boolean javanVersion,
        FacadeMode mode,
        Optional<String> classOutput,
        Optional<String> classpath,
        Optional<String> mainClass,
        Optional<String> outputName,
        List<String> targets,
        Optional<String> diagnosticFormat,
        String error
    ) {
        /**
         * Creates an immutable parse result.
         */
        public FacadeArguments {
            javacArgs = List.copyOf(Objects.requireNonNull(javacArgs, "javacArgs"));
            mode = Objects.requireNonNull(mode, "mode");
            classOutput = Objects.requireNonNull(classOutput, "classOutput");
            classpath = Objects.requireNonNull(classpath, "classpath");
            mainClass = Objects.requireNonNull(mainClass, "mainClass");
            outputName = Objects.requireNonNull(outputName, "outputName");
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            diagnosticFormat = Objects.requireNonNull(diagnosticFormat, "diagnosticFormat");
            error = Objects.requireNonNull(error, "error");
        }

        /**
         * Returns whether post-compile Javan behavior is enabled.
         *
         * @return true unless {@code --jn-off} was selected
         */
        public boolean analysisEnabled() {
            return mode != FacadeMode.OFF;
        }

        private static FacadeArguments success(
            final List<String> javacArgs,
            final boolean javanHelp,
            final boolean javanVersion,
            final FacadeMode mode,
            final Optional<String> classOutput,
            final Optional<String> classpath,
            final Optional<String> mainClass,
            final Optional<String> outputName,
            final List<String> targets,
            final Optional<String> diagnosticFormat
        ) {
            return new FacadeArguments(
                true,
                javacArgs,
                javanHelp,
                javanVersion,
                mode,
                classOutput,
                classpath,
                mainClass,
                outputName,
                targets,
                diagnosticFormat,
                ""
            );
        }

        private static FacadeArguments failure(final String error) {
            return new FacadeArguments(
                false,
                List.of(),
                false,
                false,
                FacadeMode.OFF,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                error
            );
        }
    }

    /** Selected post-compile facade behavior. */
    public enum FacadeMode {
        REPORT,
        OFF,
        WARN,
        STRICT,
        BUILD
    }

    /**
     * One complete facade invocation outcome.
     *
     * @param javacExitCode real javac exit code
     * @param analysis native analysis state
     * @param reason concise state explanation
     * @param classOutput class-output directory when known
     * @param diagnosticCount diagnostics written by completed analysis
     */
    public record InvocationOutcome(
        int javacExitCode,
        String analysis,
        String reason,
        Optional<Path> classOutput,
        int diagnosticCount
    ) {
        /** Creates an immutable invocation outcome. */
        public InvocationOutcome {
            analysis = Objects.requireNonNull(analysis, "analysis");
            reason = Objects.requireNonNull(reason, "reason");
            classOutput = Objects.requireNonNull(classOutput, "classOutput");
            if (classOutput.isPresent()) {
                classOutput = Optional.of(classOutput.orElseThrow().toAbsolutePath().normalize());
            }
            if (diagnosticCount < 0) {
                throw new IllegalArgumentException("diagnosticCount");
            }
        }
    }
}
