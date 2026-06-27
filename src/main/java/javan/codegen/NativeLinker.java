package javan.codegen;

import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Links generated C into a native executable with an available C compiler.
 */
public final class NativeLinker {
    private final ProcessRunner processRunner;

    /**
     * Creates a native linker.
     */
    public NativeLinker() {
        this(new ProcessRunner());
    }

    /**
     * Creates a native linker.
     *
     * @param processRunner process runner
     */
    public NativeLinker(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Links generated C sources.
     *
     * @param root working directory
     * @param mainC generated main C path
     * @param runtimeC runtime C path
     * @param output output binary path
     * @return output binary path
     * @throws IOException when no compiler is available or linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path link(final Path root, final Path mainC, final Path runtimeC, final Path output) throws IOException, InterruptedException {
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        Files.createDirectories(output.getParent());
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(threadFlags());
        command.add(mainC.toString());
        command.add(runtimeC.toString());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw new IOException("Native link failed\n" + result.stderr() + result.stdout());
        }
        return output;
    }

    /**
     * Links generated C sources into a shared library.
     *
     * @param root working directory
     * @param mainC generated C path
     * @param runtimeC runtime C path
     * @param output output library path
     * @return output library path
     * @throws IOException when linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path linkSharedLibrary(final Path root, final Path mainC, final Path runtimeC, final Path output)
        throws IOException, InterruptedException {
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        Files.createDirectories(output.getParent());
        final boolean mac = Strings2.toAsciiLowerCase(System.getProperty("os.name", "")).contains("mac");
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(threadFlags());
        if (mac) {
            command.add("-dynamiclib");
        } else {
            command.add("-shared");
        }
        command.add("-fPIC");
        command.add(mainC.toString());
        command.add(runtimeC.toString());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw new IOException("Native shared library link failed\n" + result.stderr() + result.stdout());
        }
        return output;
    }

    /**
     * Links generated C sources into a static library.
     *
     * @param root working directory
     * @param mainC generated C path
     * @param runtimeC runtime C path
     * @param output output library path
     * @return output library path
     * @throws IOException when linking fails
     * @throws InterruptedException when interrupted while linking
     */
    public Path linkStaticLibrary(final Path root, final Path mainC, final Path runtimeC, final Path output)
        throws IOException, InterruptedException {
        final String compiler = requiredExecutable(compilerCandidates(), "No C compiler found. Install gcc, clang, or cc.");
        final String archiver = requiredExecutable(List.of("ar"), "No archiver found. Install ar.");
        Files.createDirectories(output.getParent());
        final Path objects = output.getParent().resolve("objects");
        Files.createDirectories(objects);
        final Path mainObject = objects.resolve("javan_library.o");
        final Path runtimeObject = objects.resolve("javan_runtime.o");
        compileObject(root, compiler, mainC, mainObject);
        compileObject(root, compiler, runtimeC, runtimeObject);
        final ProcessRunner.Result result = processRunner.run(root, List.of(
            archiver,
            "rcs",
            output.toString(),
            mainObject.toString(),
            runtimeObject.toString()
        ));
        if (result.exitCode() != 0) {
            throw new IOException("Native static library link failed\n" + result.stderr() + result.stdout());
        }
        return output;
    }

    private void compileObject(final Path root, final String compiler, final Path source, final Path output)
        throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(compiler);
        command.addAll(threadFlags());
        command.add("-fPIC");
        command.add("-c");
        command.add(source.toString());
        command.add("-o");
        command.add(output.toString());
        final ProcessRunner.Result result = processRunner.run(root, command);
        if (result.exitCode() != 0) {
            throw new IOException("Native compile failed\n" + result.stderr() + result.stdout());
        }
    }

    private static List<String> threadFlags() {
        final String os = Strings2.toAsciiLowerCase(System.getProperty("os.name", ""));
        if (os.contains("win")) {
            return List.of();
        }
        return List.of("-pthread");
    }

    private static List<String> compilerCandidates() {
        return compilerCandidatesForOs(System.getProperty("os.name", ""), System.getenv("CC"));
    }

    static List<String> compilerCandidatesForOs(final String osName, final String configuredCompiler) {
        final List<String> result = new ArrayList<>();
        if (!Strings2.isBlank(configuredCompiler)) {
            result.add(configuredCompiler.trim());
        }
        if (isWindowsHost(osName)) {
            result.add("gcc");
            result.add("clang");
            result.add("cc");
            return List.copyOf(result);
        }
        result.add("cc");
        result.add("clang");
        result.add("gcc");
        return List.copyOf(result);
    }

    private String requiredExecutable(final List<String> executables, final String message) throws IOException, InterruptedException {
        final Optional<String> pathExecutable = firstOnPath(executables);
        if (pathExecutable.isPresent()) {
            return pathExecutable.orElseThrow();
        }
        final Optional<String> executable = processRunner.firstAvailable(executables);
        if (executable.isEmpty()) {
            throw new IOException(message);
        }
        return executable.orElseThrow();
    }

    private static Optional<String> firstOnPath(final List<String> executables) {
        return firstOnPathForOs(
            executables,
            System.getenv("PATH"),
            System.getProperty("os.name", ""),
            windowsExecutableExtensions()
        );
    }

    static Optional<String> firstOnPathForOs(
        final List<String> executables,
        final String path,
        final String osName,
        final List<String> windowsExtensions
    ) {
        for (final String executable : executables) {
            final Optional<String> resolved = resolveOnPath(executable, path, osName, windowsExtensions);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveOnPath(final String executable) {
        return resolveOnPath(
            executable,
            System.getenv("PATH"),
            System.getProperty("os.name", ""),
            windowsExecutableExtensions()
        );
    }

    private static Optional<String> resolveOnPath(
        final String executable,
        final String path,
        final String osName,
        final List<String> windowsExtensions
    ) {
        if (Strings2.isBlank(executable)) {
            return Optional.empty();
        }
        if (containsPathSeparator(executable)) {
            return resolveExecutablePathForOs(Path.of(executable), osName, windowsExtensions);
        }
        if (Strings2.isBlank(path)) {
            return Optional.empty();
        }
        final char separator = pathSeparator();
        int start = 0;
        for (int index = 0; index <= path.length(); index++) {
            if (index == path.length() || path.charAt(index) == separator) {
                String directory = Strings2.slice(path, start, index);
                if (Strings2.isBlank(directory)) {
                    directory = ".";
                }
                final Optional<String> resolved = resolveExecutablePathForOs(Path.of(directory).resolve(executable), osName, windowsExtensions);
                if (resolved.isPresent()) {
                    return resolved;
                }
                start = index + 1;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveExecutablePath(final Path candidate) {
        return resolveExecutablePathForOs(candidate, System.getProperty("os.name", ""), windowsExecutableExtensions());
    }

    static Optional<String> resolveExecutablePathForOs(final Path candidate, final String osName, final List<String> windowsExtensions) {
        if (Files.isExecutable(candidate)) {
            return Optional.of(candidate.toString());
        }
        if (!isWindowsHost(osName) || hasExplicitExtension(candidate)) {
            return Optional.empty();
        }
        for (final String extension : windowsExtensions) {
            final Path extended = Path.of(candidate.toString() + extension);
            if (Files.isExecutable(extended)) {
                return Optional.of(extended.toString());
            }
        }
        return Optional.empty();
    }

    private static boolean hasExplicitExtension(final Path candidate) {
        final Path fileName = candidate.getFileName();
        if (fileName == null) {
            return false;
        }
        final String name = fileName.toString();
        final int index = name.lastIndexOf('.');
        return index > 0 && index < name.length() - 1;
    }

    private static List<String> windowsExecutableExtensions() {
        final String pathExt = System.getenv("PATHEXT");
        if (Strings2.isBlank(pathExt)) {
            return List.of(".exe", ".cmd", ".bat", ".com");
        }
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= pathExt.length(); index++) {
            if (index == pathExt.length() || pathExt.charAt(index) == ';') {
                final String extension = Strings2.slice(pathExt, start, index).trim();
                if (!Strings2.isBlank(extension)) {
                    result.add(extension.startsWith(".") ? extension : "." + extension);
                }
                start = index + 1;
            }
        }
        if (result.isEmpty()) {
            return List.of(".exe", ".cmd", ".bat", ".com");
        }
        return List.copyOf(result);
    }

    private static boolean containsPathSeparator(final String executable) {
        for (int index = 0; index < executable.length(); index++) {
            final char ch = executable.charAt(index);
            if (ch == '/' || ch == '\\') {
                return true;
            }
        }
        return false;
    }

    private static char pathSeparator() {
        final String separator = System.getProperty("path.separator", ":");
        if (separator.isEmpty()) {
            return ':';
        }
        return separator.charAt(0);
    }

    private static boolean isWindowsHost() {
        return isWindowsHost(System.getProperty("os.name", ""));
    }

    private static boolean isWindowsHost(final String osName) {
        return Strings2.toAsciiLowerCase(osName).contains("win");
    }
}
