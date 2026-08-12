package javan.toolchain.facade;

import javan.toolchain.JdkResolver;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Creates a JDK-shaped Unix facade over one verified backend JDK.
 *
 * <p>The facade links the backend's standard-library directories rather than copying them. Its
 * {@code javac} launcher delegates through Javan while {@code java} delegates directly to the
 * backend. Windows is deliberately rejected until Javan ships a native {@code javac.exe}
 * launcher; a batch file would not be an SDK-compatible replacement.</p>
 */
public final class JdkFacadeGenerator {
    private static final List<String> LINKED_DIRECTORIES = List.of("lib", "jmods", "include", "conf");
    private final ProcessRunner processRunner;

    /**
     * Creates a facade generator using the default process runner.
     */
    public JdkFacadeGenerator() {
        this(new ProcessRunner());
    }

    /**
     * Creates a facade generator using the supplied process runner.
     *
     * @param processRunner command runner for link, rename, and permission operations
     */
    public JdkFacadeGenerator(final ProcessRunner processRunner) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    /**
     * Creates an unopened JDK-shaped facade directory.
     *
     * @param output requested final facade home
     * @param backend selected verified JDK
     * @return created facade result
     * @throws IOException when the filesystem or a platform command fails
     * @throws InterruptedException when interrupted while linking the facade
     */
    public Result generate(final Path output, final JdkResolver.Candidate backend) throws IOException, InterruptedException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(backend, "backend");
        if (!backend.usable()) {
            throw new IllegalArgumentException("Cannot create a facade for an unusable JDK: " + backend.reason());
        }
        if (isWindows()) {
            throw new IllegalArgumentException(
                "JDK facade generation is unavailable on Windows until Javan ships a native javac.exe launcher"
            );
        }
        final Path finalHome = output.toAbsolutePath().normalize();
        if (Files.exists(finalHome)) {
            throw new IllegalArgumentException("JDK facade output already exists: " + finalHome);
        }
        final Path parent = Objects.requireNonNull(finalHome.getParent(), "facade output parent");
        final Path staging = parent.resolve(finalHome.getFileName().toString() + ".javan-staging");
        if (Files.exists(staging)) {
            throw new IllegalArgumentException("JDK facade staging directory already exists: " + staging);
        }
        Files.createDirectories(parent);
        Files.createDirectories(staging.resolve("bin"));
        Files2.writeString(staging.resolve("release"), Files.readString(backend.home().resolve("release")));
        Files2.writeString(staging.resolve("bin/javac"), javacLauncher());
        Files2.writeString(staging.resolve("bin/java"), backendLauncher(backend.javaExecutable()));
        setExecutable(staging.resolve("bin/javac"));
        setExecutable(staging.resolve("bin/java"));
        for (final String directory : LINKED_DIRECTORIES) {
            final Path source = backend.home().resolve(directory);
            if (Files.isDirectory(source)) {
                link(source, staging.resolve(directory));
            }
        }
        Files2.writeString(staging.resolve("javan-backend.txt"), backendReport(backend));
        move(staging, finalHome);
        return new Result(finalHome, backend.home(), backend.javacExecutable());
    }

    private void setExecutable(final Path launcher) throws IOException, InterruptedException {
        requireSuccess(processRunner.run(launcher.getParent(), List.of("chmod", "+x", launcher.toString())), "chmod", launcher);
    }

    private void link(final Path source, final Path target) throws IOException, InterruptedException {
        requireSuccess(processRunner.run(target.getParent(), List.of("ln", "-s", source.toString(), target.toString())), "link", target);
    }

    private void move(final Path staging, final Path output) throws IOException, InterruptedException {
        requireSuccess(processRunner.run(output.getParent(), List.of("mv", staging.toString(), output.toString())), "publish", output);
    }

    private static void requireSuccess(final ProcessRunner.Result result, final String action, final Path path) throws IOException {
        if (result.exitCode() == 0) {
            return;
        }
        final String detail = Strings2.isBlank(result.stderr()) ? result.stdout() : result.stderr();
        throw new IOException("Unable to " + action + " JDK facade path " + path + ": " + detail);
    }

    private static String javacLauncher() {
        return "#!/bin/sh\nexec \"${JAVAN_BIN:-javan}\" javac \"$@\"\n";
    }

    private static String backendLauncher(final Path executable) {
        return "#!/bin/sh\nexec " + shellLiteral(executable.toString()) + " \"$@\"\n";
    }

    private static String backendReport(final JdkResolver.Candidate backend) {
        return "backendHome=" + backend.home() + "\n"
            + "backendJavac=" + backend.javacExecutable() + "\n"
            + "origin=" + backend.origin() + "\n";
    }

    private static String shellLiteral(final String value) {
        final StringBuilder literal = new StringBuilder("'");
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '\'') {
                literal.append("'\\\"'\\\"'");
            } else {
                literal.append(character);
            }
        }
        return literal.append('\'').toString();
    }

    private static boolean isWindows() {
        return Strings2.toAsciiLowerCase(System.getProperty("os.name", "")).contains("win");
    }

    /**
     * Created facade metadata.
     *
     * @param home generated facade home
     * @param backendHome real JDK home
     * @param backendJavac real javac executable
     */
    public record Result(Path home, Path backendHome, Path backendJavac) {
        /**
         * Creates normalized result paths.
         */
        public Result {
            home = normalize(home, "home");
            backendHome = normalize(backendHome, "backendHome");
            backendJavac = normalize(backendJavac, "backendJavac");
        }

        private static Path normalize(final Path path, final String name) {
            return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
        }
    }
}
