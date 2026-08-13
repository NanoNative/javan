package javan.cli;

import javan.Javan;
import javan.toolchain.JavanExecutable;
import javan.toolchain.JavanInstallation;
import javan.toolchain.JdkResolver;
import javan.toolchain.ToolchainManager;
import javan.toolchain.facade.JavacWrapper;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;
import javan.verify.Diagnostic;
import javan.verify.DiagnosticException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Command line facade for javan.
 */
public final class Cli {
    private final Javan javan = new Javan();
    private final JavacWrapper javacWrapper = new JavacWrapper();
    private final ToolchainManager toolchainManager = new ToolchainManager();
    private final ProcessRunner processRunner = new ProcessRunner();

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
            return runParsed(cwd, out, err, Options.parse(facadeArguments(args)));
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
        final Options.ParseResult parsed = Options.parseResult(facadeArguments(args));
        if (!parsed.pass()) {
            err.println("error[JAVAN900]: " + parsed.error());
            return 2;
        }
        return runParsed(cwd, out, err, parsed.options());
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
        if (command == Command.INSTALL) {
            if (!options.passthroughArgs().isEmpty() || options.target().isPresent()) {
                throw new IllegalArgumentException("javan install does not accept arguments");
            }
            out.println(install());
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
            return javac(cwd, out, err, options);
        }
        if (command == Command.FACADE_JAVAC) {
            return javac(cwd, out, err, options, facadeBackendHome());
        }
        if (command == Command.FACADE_JAVA) {
            return facadeJava(cwd, out, err, options);
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
        if (command == Command.JDK) {
            out.println(jdk(cwd, options));
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
              javan install
              javan inspect [path]
              javan check [path] [--main com.acme.Main]
              javan test [path]
              javan build [path] [--main com.acme.Main] [--profile core|service|library|strict] [--output app]
              javan build [path] --jar
              javan build [path] --library --export com.acme.Math.add --bindings c,rust,go,python
              javan run [path] [--main com.acme.Main] [-- args...]
              javan compat [path] [--main com.acme.Main]
              javan report [path]
              javan clean [path]
              javan doctor
              javan jdk list
              javan jdk install
              javan jdk use <25|vendor@25>
              javan jdk doctor
              javan jdk resolve [jdk-home]
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

    private String jdk(final Path cwd, final Options options) throws IOException, InterruptedException {
        if (options.target().isEmpty()) {
            throw new IllegalArgumentException("Missing JDK command: list, install, use, doctor, resolve, or facade");
        }
        final String subcommand = options.target().orElseThrow().toString();
        if ("list".equals(subcommand)) {
            requireNoJdkArguments(options, subcommand);
            return toolchainManager.listJdks();
        }
        if ("use".equals(subcommand)) {
            if (options.passthroughArgs().size() != 1) {
                throw new IllegalArgumentException("Expected one JDK selector for use, for example: 25 or temurin@25");
            }
            return toolchainManager.useJdk(options.passthroughArgs().getFirst());
        }
        if ("install".equals(subcommand)) {
            requireNoJdkArguments(options, subcommand);
            return install();
        }
        if ("doctor".equals(subcommand)) {
            requireNoJdkArguments(options, subcommand);
            return toolchainManager.jdkDoctor();
        }
        if ("resolve".equals(subcommand)) {
            return resolveJdk(options);
        }
        if ("facade".equals(subcommand)) {
            return createJdkFacade(cwd, options);
        }
        throw new IllegalArgumentException("Unsupported JDK command: " + subcommand);
    }

    private int javac(final Path cwd, final PrintStream out, final PrintStream err, final Options options)
        throws IOException, InterruptedException {
        return javac(cwd, out, err, options, Optional.empty());
    }

    private int javac(
        final Path cwd,
        final PrintStream out,
        final PrintStream err,
        final Options options,
        final Optional<Path> facadeBackend
    ) throws IOException, InterruptedException {
        final JavacWrapper.FacadeArguments facade = JavacWrapper.parseFacadeArguments(options.passthroughArgs());
        if (!facade.pass()) {
            err.println("error[JAVAN900]: " + facade.error());
            return 2;
        }
        if (facade.javanHelp()) {
            out.print(JavacWrapper.facadeHelp());
            return 0;
        }
        final java.util.Optional<JdkResolver.Candidate> selected = selectedJdk(facadeBackend);
        if (facade.javanVersion()) {
            writeFacadeVersion(out, selected);
            return 0;
        }
        if (selected.isEmpty()) {
            err.println("error[JAVAN900]: No usable local JDK found; run javan jdk resolve");
            return 2;
        }
        final ProcessRunner.Result result = javacWrapper.invoke(
            cwd,
            selected.orElseThrow().javacExecutable(),
            facade.javacArgs()
        );
        out.print(result.stdout());
        err.print(result.stderr());
        if (result.exitCode() != 0) {
            writeJavacReport(
                cwd,
                facade,
                result.exitCode(),
                "not-run",
                "javac failed; Javan did not inspect class output",
                Optional.empty(),
                0,
                out,
                err
            );
            return result.exitCode();
        }
        if (requestsBackendHelp(facade.javacArgs())) {
            out.print(JavacWrapper.facadeHelp());
        }
        if (requestsBackendVersion(facade.javacArgs())) {
            writeFacadeVersion(out, selected);
        }
        if (facade.analysisEnabled() && !requestsBackendHelp(facade.javacArgs()) && !requestsBackendVersion(facade.javacArgs())) {
            return analyzeJavacOutput(cwd, facade, out, err);
        }
        return result.exitCode();
    }

    private int facadeJava(final Path cwd, final PrintStream out, final PrintStream err, final Options options)
        throws IOException, InterruptedException {
        if (!options.passthroughArgs().isEmpty() && "jdk".equals(options.passthroughArgs().getFirst())) {
            final String[] args = new String[options.passthroughArgs().size()];
            for (int index = 0; index < args.length; index++) {
                args[index] = options.passthroughArgs().get(index);
            }
            final Options jdkOptions = Options.parse(args);
            out.println(jdk(cwd, jdkOptions));
            return 0;
        }
        final Optional<JdkResolver.Candidate> selected = selectedJdk(facadeBackendHome());
        if (selected.isEmpty()) {
            err.println("error[JAVAN900]: Facade backend JDK is unavailable");
            return 2;
        }
        final java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add(selected.orElseThrow().javaExecutable().toString());
        command.addAll(options.passthroughArgs());
        final ProcessRunner.Result result = processRunner.run(cwd, command);
        out.print(result.stdout());
        err.print(result.stderr());
        if (requestsBackendHelp(options.passthroughArgs())) {
            writeJavaFacadeHelp(out);
        }
        if (requestsBackendVersion(options.passthroughArgs())) {
            writeJavaFacadeVersion(out, selected);
        }
        return result.exitCode();
    }

    private Optional<JdkResolver.Candidate> selectedJdk(final Optional<Path> facadeBackend) throws IOException {
        final JdkResolver.Resolution resolution = toolchainManager.resolveLocalJdk(facadeBackend);
        if (facadeBackend.isEmpty()) {
            return resolution.selected();
        }
        for (final JdkResolver.Candidate candidate : resolution.candidates()) {
            if ("explicit".equals(candidate.origin()) && candidate.usable()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> facadeBackendHome() throws IOException {
        final String value = System.getenv("JAVAN_FACADE_BACKEND");
        if (value != null && !value.isBlank()) {
            return Optional.of(Path.of(value));
        }
        final Optional<Path> executable = JavanExecutable.resolve();
        if (executable.isEmpty()) {
            return Optional.empty();
        }
        final Path parent = executable.orElseThrow().getParent();
        if (parent == null || parent.getParent() == null) {
            return Optional.empty();
        }
        return facadeMetadataValue(parent.getParent().resolve("javan-backend.txt"), "backendHome=");
    }

    private static String[] facadeArguments(final String[] args) throws IOException {
        if (args.length > 0 && ("--jn-facade-java".equals(args[0]) || "--jn-facade-javac".equals(args[0]))) {
            return args;
        }
        final Optional<Path> executable = JavanExecutable.resolve();
        if (executable.isEmpty()) {
            return args;
        }
        final Path file = executable.orElseThrow().getFileName();
        final Path parent = executable.orElseThrow().getParent();
        if (file == null || parent == null || parent.getParent() == null) {
            return args;
        }
        final String name = file.toString();
        final boolean javaFacade = "java".equals(name) || "java.exe".equals(name);
        final boolean javacFacade = "javac".equals(name) || "javac.exe".equals(name);
        if ((!javaFacade && !javacFacade)
            || facadeMetadataValue(parent.getParent().resolve("javan-backend.txt"), "facadeRoot=").isEmpty()) {
            return args;
        }
        final String command = javaFacade ? "--jn-facade-java" : "--jn-facade-javac";
        final String[] result = new String[args.length + 1];
        result[0] = command;
        for (int index = 0; index < args.length; index++) {
            result[index + 1] = args[index];
        }
        return result;
    }

    private static Optional<Path> facadeMetadataValue(final Path metadata, final String prefix) throws IOException {
        if (!java.nio.file.Files.isRegularFile(metadata)) {
            return Optional.empty();
        }
        final String content = java.nio.file.Files.readString(metadata);
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.slice(content, start, index);
                if (line.startsWith(prefix)) {
                    return Optional.of(Path.of(Strings2.slice(line, prefix.length(), line.length())).toAbsolutePath().normalize());
                }
                start = index + 1;
            }
        }
        return Optional.empty();
    }

    private int analyzeJavacOutput(
        final Path cwd,
        final JavacWrapper.FacadeArguments facade,
        final PrintStream out,
        final PrintStream err
    ) throws IOException, InterruptedException {
        if (facade.classOutput().isEmpty()) {
            writeJavacReport(
                cwd,
                facade,
                0,
                "unavailable",
                "javac did not declare -d <classes>; fresh class output cannot be proven",
                Optional.empty(),
                0,
                out,
                err
            );
            return 0;
        }
        final Path classes = resolveFromCwd(cwd, facade.classOutput().orElseThrow());
        if (!Files2.containsClassFile(classes)) {
            writeJavacReport(
                cwd,
                facade,
                0,
                "unavailable",
                "javac completed without class files in the declared -d directory",
                Optional.of(classes),
                0,
                out,
                err
            );
            return 0;
        }
        if (facade.mode() == JavacWrapper.FacadeMode.BUILD) {
            return buildJavacOutput(cwd, facade, classes, out, err);
        }
        final Javan.CheckResult check = javan.check(cwd, analysisOptions(classes, facade), out);
        writeJavacReport(
            cwd,
            facade,
            0,
            "completed",
            "Javan analyzed the declared javac class output; pre-existing output freshness is unverified",
            Optional.of(classes),
            check.diagnostics().size(),
            out,
            err,
            Optional.of(check.layout().outputDirectory())
        );
        if (facade.mode() == JavacWrapper.FacadeMode.WARN || facade.mode() == JavacWrapper.FacadeMode.STRICT) {
            writeFacadeDiagnostics(check.diagnostics(), facade, out, err);
        }
        if (facade.mode() == JavacWrapper.FacadeMode.STRICT) {
            return diagnosticExitCode(check.diagnostics());
        }
        return 0;
    }

    private int buildJavacOutput(
        final Path cwd,
        final JavacWrapper.FacadeArguments facade,
        final Path classes,
        final PrintStream out,
        final PrintStream err
    ) throws IOException, InterruptedException {
        final Options buildOptions = nativeBuildOptions(classes, facade);
        final Javan.BuildResult build = javan.build(cwd, buildOptions, out);
        final String analysis = build.pass() ? "built" : "blocked";
        final String reason = build.pass()
            ? "Javan built a native app from the declared javac class output; output freshness is unverified"
            : "Javan found native blockers in the declared javac class output";
        final Optional<Path> reports = build.pass()
            ? Optional.of(build.artifact().orElseThrow().getParent().getParent())
            : Optional.empty();
        writeJavacReport(cwd, facade, 0, analysis, reason, Optional.of(classes), build.diagnostics().size(), out, err, reports);
        writeFacadeDiagnostics(build.diagnostics(), facade, out, err);
        return diagnosticExitCode(build.diagnostics());
    }

    private static Options nativeBuildOptions(final Path classes, final JavacWrapper.FacadeArguments facade) {
        if (facade.targets().size() > 1) {
            throw new IllegalArgumentException("--jn-build currently accepts one --jn-target; cross-target packaging is unavailable");
        }
        final java.util.ArrayList<String> args = new java.util.ArrayList<>();
        args.add("build");
        args.add(classes.toString());
        if (facade.mainClass().isPresent()) {
            args.add("--main");
            args.add(facade.mainClass().orElseThrow());
        }
        if (facade.outputName().isPresent()) {
            final String output = facade.outputName().orElseThrow();
            if (containsPathSeparator(output)) {
                throw new IllegalArgumentException("--jn-out accepts a file name, not a path; native output is written below .javan/bin");
            }
            args.add("--output");
            args.add(output);
        }
        if (facade.classpath().isPresent()) {
            args.add("--classpath");
            args.add(facade.classpath().orElseThrow());
        }
        if (!facade.targets().isEmpty()) {
            args.add("--target");
            args.add(facade.targets().getFirst());
        }
        final String[] values = new String[args.size()];
        for (int index = 0; index < values.length; index++) {
            values[index] = args.get(index);
        }
        return Options.parse(values);
    }

    private static boolean containsPathSeparator(final String value) {
        return value.indexOf('/') >= 0 || value.indexOf('\\') >= 0;
    }

    private static void writeFacadeDiagnostics(
        final List<Diagnostic> diagnostics,
        final JavacWrapper.FacadeArguments facade,
        final PrintStream out,
        final PrintStream err
    ) {
        if (diagnostics.isEmpty()) {
            return;
        }
        final String format = facade.diagnosticFormat().orElse("auto");
        for (final Diagnostic diagnostic : diagnostics) {
            final PrintStream stream = diagnostic.error() ? err : out;
            if ("jsonl".equals(format)) {
                stream.println(jsonDiagnostic(diagnostic));
            } else if ("pretty".equals(format)) {
                stream.println(prettyDiagnostic(diagnostic));
            } else {
                stream.println(diagnostic.format());
            }
        }
    }

    private static String prettyDiagnostic(final Diagnostic diagnostic) {
        final String severity = diagnostic.error() ? "error" : "warning";
        final String location = diagnostic.className().isEmpty()
            ? "unknown location"
            : diagnostic.className() + (diagnostic.methodName().isEmpty() ? "" : " :: " + diagnostic.methodName());
        return "+-- " + severity + " [" + diagnostic.code() + "]" + System.lineSeparator()
            + "| " + diagnostic.message() + System.lineSeparator()
            + "| at: " + location + System.lineSeparator()
            + "| why: " + diagnostic.reason() + System.lineSeparator()
            + "`-- fix: " + diagnostic.fix();
    }

    private static String jsonDiagnostic(final Diagnostic diagnostic) {
        return "{\"schemaVersion\":1,\"severity\":\"" + (diagnostic.error() ? "error" : "warning")
            + "\",\"code\":\"" + json(diagnostic.code())
            + "\",\"message\":\"" + json(diagnostic.message())
            + "\",\"class\":\"" + json(diagnostic.className())
            + "\",\"method\":\"" + json(diagnostic.methodName())
            + "\",\"subject\":\"" + json(diagnostic.subject())
            + "\",\"reason\":\"" + json(diagnostic.reason())
            + "\",\"fix\":\"" + json(diagnostic.fix()) + "\"}";
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
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static Options analysisOptions(final Path classes, final JavacWrapper.FacadeArguments facade) {
        if (facade.classpath().isEmpty()) {
            return Options.parse(new String[] {"check", classes.toString()});
        }
        return Options.parse(new String[] {"check", classes.toString(), "--classpath", facade.classpath().orElseThrow()});
    }

    private void writeJavacReport(
        final Path cwd,
        final JavacWrapper.FacadeArguments facade,
        final int javacExitCode,
        final String analysis,
        final String reason,
        final Optional<Path> classOutput,
        final int diagnostics,
        final PrintStream out,
        final PrintStream err
    ) throws IOException {
        writeJavacReport(cwd, facade, javacExitCode, analysis, reason, classOutput, diagnostics, out, err, Optional.empty());
    }

    private void writeJavacReport(
        final Path cwd,
        final JavacWrapper.FacadeArguments facade,
        final int javacExitCode,
        final String analysis,
        final String reason,
        final Optional<Path> classOutput,
        final int diagnostics,
        final PrintStream out,
        final PrintStream err,
        final Optional<Path> outputDirectory
    ) throws IOException {
        final Path reportsHome = outputDirectory.isPresent()
            ? outputDirectory.orElseThrow()
            : reportOutputDirectory(cwd, facade, classOutput);
        final Path report = JavacWrapper.writeReport(
            reportsHome,
            new JavacWrapper.InvocationOutcome(javacExitCode, analysis, reason, classOutput, diagnostics)
        );
        out.println("Javan report: " + report);
    }

    private static Path reportOutputDirectory(
        final Path cwd,
        final JavacWrapper.FacadeArguments facade,
        final Optional<Path> classOutput
    ) {
        if (classOutput.isPresent()) {
            final Path parent = classOutput.orElseThrow().getParent();
            if (parent != null) {
                return parent.resolve(".javan");
            }
        }
        if (facade.classOutput().isPresent()) {
            final Path parsedOutput = resolveFromCwd(cwd, facade.classOutput().orElseThrow());
            if (parsedOutput.getParent() != null) {
                return parsedOutput.getParent().resolve(".javan");
            }
        }
        return cwd.toAbsolutePath().normalize().resolve(".javan");
    }

    private static Path resolveFromCwd(final Path cwd, final String value) {
        final Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        return cwd.toAbsolutePath().normalize().resolve(path).normalize();
    }

    private static boolean requestsBackendHelp(final java.util.List<String> args) {
        return args.contains("--help") || args.contains("-help");
    }

    private static boolean requestsBackendVersion(final java.util.List<String> args) {
        return args.contains("--version") || args.contains("-version");
    }

    private static void writeFacadeVersion(final PrintStream out, final java.util.Optional<JdkResolver.Candidate> selected) {
        out.println();
        out.println("Javan facade " + Version.number());
        if (selected.isEmpty()) {
            out.println("Backend: unresolved");
            return;
        }
        final JdkResolver.Candidate backend = selected.orElseThrow();
        out.println("Backend: " + backend.origin());
        out.println("Javac:   " + backend.javacExecutable());
    }

    private static void writeJavaFacadeHelp(final PrintStream out) {
        out.println();
        out.println("Javan:");
        out.println("  java jdk list");
        out.println("  java jdk use <25|vendor@25>");
        out.println("  java jdk doctor");
    }

    private static void writeJavaFacadeVersion(final PrintStream out, final java.util.Optional<JdkResolver.Candidate> selected) {
        out.println();
        out.println("Javan facade " + Version.number());
        if (selected.isPresent()) {
            out.println("Backend: " + selected.orElseThrow().home());
        }
        out.println("Management: java jdk list | java jdk use <25|vendor@25>");
    }

    private static void requireNoJdkArguments(final Options options, final String subcommand) {
        if (!options.passthroughArgs().isEmpty()) {
            throw new IllegalArgumentException("Unexpected JDK " + subcommand + " arguments: " + joinArgs(options.passthroughArgs()));
        }
    }

    private String resolveJdk(final Options options) throws IOException {
        if (options.passthroughArgs().size() > 1) {
            throw new IllegalArgumentException("Expected at most one JDK home for resolve");
        }
        if (options.passthroughArgs().isEmpty()) {
            return toolchainManager.resolveJdk(java.util.Optional.empty());
        }
        return toolchainManager.resolveJdk(java.util.Optional.of(Path.of(options.passthroughArgs().getFirst())));
    }

    private String createJdkFacade(final Path cwd, final Options options) throws IOException, InterruptedException {
        if (options.passthroughArgs().size() != 1) {
            throw new IllegalArgumentException("Expected one output directory for jdk facade");
        }
        final Path output = resolveFromCwd(cwd, options.passthroughArgs().getFirst());
        final javan.toolchain.facade.JdkFacadeGenerator.Result facade = toolchainManager.createJdkFacade(output);
        return "JDK facade\n  home:    " + facade.home() + "\n  backend: " + facade.backendHome();
    }

    private String install() throws IOException, InterruptedException {
        final JavanInstallation.Installation installation = toolchainManager.installJavan(JavanExecutable.require());
        return "Javan installed" + System.lineSeparator()
            + "  scope:    " + installation.location().scope() + System.lineSeparator()
            + "  jdk home: " + installation.location().publicHome() + System.lineSeparator()
            + "  backend:  " + installation.backend() + System.lineSeparator()
            + "  launcher: " + installation.location().publicHome().resolve("bin").resolve(facadeExecutableName()) + System.lineSeparator()
            + "  commands: java jdk list | java jdk use 25";
    }

    private static String facadeExecutableName() {
        return Strings2.toAsciiLowerCase(System.getProperty("os.name", "")).contains("win") ? "javan.exe" : "javan";
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

    private static int diagnosticExitCode(final List<Diagnostic> diagnostics) {
        for (final Diagnostic diagnostic : diagnostics) {
            if (diagnostic.error()) {
                return 2;
            }
        }
        return 0;
    }
}
