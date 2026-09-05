package javan.toolchain.facade;

import javan.toolchain.JdkResolver;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;

/**
 * Creates a JDK-shaped facade over one verified backend JDK.
 *
 * <p>The facade links every backend entry other than the intercepted {@code bin} launchers,
 * rather than maintaining a vendor-specific file list. Its {@code java} and {@code javac}
 * launchers enter Javan with the exact backend path. On Windows, they are native Javan
 * executables rather than batch files, while every other vendor tool remains dynamically linked.</p>
 *
 */
public final class JdkFacadeGenerator {
    private final ProcessRunner processRunner = new ProcessRunner();

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
        final Path finalHome = output.toAbsolutePath().normalize();
        return generate(output, backend, Objects.requireNonNull(finalHome.getParent(), "facade output parent"));
    }

    /**
     * Creates an unopened JDK-shaped facade directory with an explicit switchable facade root.
     *
     * @param output requested final facade home
     * @param backend selected verified JDK
     * @param facadeRoot root containing the stable current facade link
     * @return created facade result
     * @throws IOException when the filesystem or a platform command fails
     * @throws InterruptedException when interrupted while linking the facade
     */
    public Result generate(final Path output, final JdkResolver.Candidate backend, final Path facadeRoot)
        throws IOException, InterruptedException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(backend, "backend");
        final Path normalizedFacadeRoot = normalize(facadeRoot, "facadeRoot");
        if (!backend.usable()) {
            throw new IllegalArgumentException("Cannot create a facade for an unusable JDK: " + backend.reason());
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
        linkBackendEntries(staging, backend.home());
        writeLaunchers(staging.resolve("bin"), backend.home(), normalizedFacadeRoot);
        Files2.writeString(staging.resolve("javan-backend.txt"), backendReport(backend, normalizedFacadeRoot));
        move(staging, finalHome);
        return new Result(finalHome, backend.home(), backend.javacExecutable());
    }

    /**
     * Refreshes only the Javan-owned launchers in an existing facade.
     *
     * @param facadeHome existing facade home
     * @param backend selected verified JDK
     * @param facadeRoot root containing the current Javan launcher
     * @throws IOException when the facade launchers cannot be updated
     * @throws InterruptedException when interrupted while setting Unix permissions
     */
    public void refreshLaunchers(final Path facadeHome, final JdkResolver.Candidate backend, final Path facadeRoot)
        throws IOException, InterruptedException {
        final Path home = normalize(facadeHome, "facadeHome");
        Objects.requireNonNull(backend, "backend");
        writeLaunchers(home.resolve("bin"), backend.home(), normalize(facadeRoot, "facadeRoot"));
    }

    private void linkBackendEntries(final Path staging, final Path backendHome) throws IOException, InterruptedException {
        for (final Path source : directoryEntries(backendHome)) {
            final String name = source.getFileName().toString();
            if ("bin".equals(name)) {
                linkBackendBin(staging.resolve("bin"), source);
            } else {
                link(source, staging.resolve(name));
            }
        }
    }

    private void linkBackendBin(final Path targetBin, final Path backendBin) throws IOException, InterruptedException {
        for (final Path source : directoryEntries(backendBin)) {
            final String name = source.getFileName().toString();
            if (!interceptedLauncher(name)) {
                link(source, targetBin.resolve(name));
            }
        }
    }

    private void writeLaunchers(final Path bin, final Path backendHome, final Path facadeRoot) throws IOException, InterruptedException {
        final Path launcher = facadeRoot.resolve(executableName("javan"));
        if (Files.isRegularFile(launcher) && Files.isExecutable(launcher)) {
            copyNativeLauncher(launcher, bin.resolve(executableName("java")));
            copyNativeLauncher(launcher, bin.resolve(executableName("javac")));
            copyNativeLauncher(launcher, bin.resolve(executableName("javan")));
            return;
        }
        if (isWindows()) {
            throw new IOException("Javan facade launcher is unavailable: " + launcher);
        }
        final Path java = bin.resolve("java");
        final Path javac = bin.resolve("javac");
        final Path javan = bin.resolve("javan");
        Files2.writeString(javac, javanLauncher("--jn-facade-javac", backendHome, facadeRoot));
        Files2.writeString(java, javanLauncher("--jn-facade-java", backendHome, facadeRoot));
        Files2.writeString(javan, javanLauncher("", backendHome, facadeRoot));
        setExecutable(javac);
        setExecutable(java);
        setExecutable(javan);
    }

    private void copyNativeLauncher(final Path source, final Path target) throws IOException, InterruptedException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        if (!isWindows()) {
            setExecutable(target);
        }
    }

    private static String executableName(final String base) {
        return isWindows() ? base + ".exe" : base;
    }

    private static boolean interceptedLauncher(final String name) {
        return "java".equals(withoutExe(name)) || "javac".equals(withoutExe(name));
    }

    private static String withoutExe(final String name) {
        if (name.endsWith(".exe")) {
            return Strings2.slice(name, 0, name.length() - 4);
        }
        return name;
    }

    private List<Path> directoryEntries(final Path directory) throws IOException {
        final java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        final java.util.ArrayList<Path> entries = new java.util.ArrayList<>();
        for (final Path entry : stream) {
            entries.add(entry);
        }
        stream.close();
        return List.copyOf(entries);
    }

    private void setExecutable(final Path launcher) throws IOException, InterruptedException {
        requireSuccess(processRunner.run(launcher.getParent(), List.of("chmod", "+x", launcher.toString())), "chmod", launcher);
    }

    private void link(final Path source, final Path target) throws IOException, InterruptedException {
        if (isWindows()) {
            if (Files.isDirectory(source)) {
                requireSuccess(
                    processRunner.run(target.getParent(), List.of("cmd", "/d", "/s", "/c", "mklink /J " + windowsQuote(target) + " " + windowsQuote(source))),
                    "link",
                    target
                );
                return;
            }
            Files.copy(source, target);
            return;
        }
        requireSuccess(processRunner.run(target.getParent(), List.of("ln", "-s", source.toString(), target.toString())), "link", target);
    }

    private void move(final Path staging, final Path output) throws IOException, InterruptedException {
        if (isWindows()) {
            requireSuccess(
                processRunner.run(output.getParent(), List.of("cmd", "/d", "/s", "/c", "move " + windowsQuote(staging) + " " + windowsQuote(output))),
                "publish",
                output
            );
            return;
        }
        requireSuccess(processRunner.run(output.getParent(), List.of("mv", staging.toString(), output.toString())), "publish", output);
    }

    private static void requireSuccess(final ProcessRunner.Result result, final String action, final Path path) throws IOException {
        if (result.exitCode() == 0) {
            return;
        }
        final String detail = Strings2.isBlank(result.stderr()) ? result.stdout() : result.stderr();
        throw new IOException("Unable to " + action + " JDK facade path " + path + ": " + detail);
    }

    private static String javanLauncher(final String command, final Path backendHome, final Path facadeRoot) {
        return "#!/bin/sh\n"
            + "JAVAN_FACADE_BACKEND=" + shellLiteral(backendHome.toString()) + "\n"
            + "JAVAN_FACADE_ROOT=" + shellLiteral(facadeRoot.toString()) + "\n"
            + "export JAVAN_FACADE_BACKEND JAVAN_FACADE_ROOT\n"
            + "if [ -n \"${JAVAN_BIN:-}\" ]; then exec \"$JAVAN_BIN\" " + command + " \"$@\"; fi\n"
            + "if [ -x \"$JAVAN_FACADE_ROOT/javan\" ]; then exec \"$JAVAN_FACADE_ROOT/javan\" " + command + " \"$@\"; fi\n"
            + "exec javan " + command + " \"$@\"\n";
    }

    private static String backendReport(final JdkResolver.Candidate backend, final Path facadeRoot) {
        return "backendHome=" + backend.home() + "\n"
            + "backendJavac=" + backend.javacExecutable() + "\n"
            + "origin=" + backend.origin() + "\n"
            + "facadeRoot=" + facadeRoot + "\n";
    }

    private static String shellLiteral(final String value) {
        final StringBuilder literal = new StringBuilder("'");
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character == '\'') {
                literal.append("'\"'\"'");
            } else {
                literal.append(character);
            }
        }
        return literal.append('\'').toString();
    }

    private static boolean isWindows() {
        return Strings2.toAsciiLowerCase(System.getProperty("os.name", "")).contains("win");
    }

    private static String windowsQuote(final Path path) {
        return "\"" + path + "\"";
    }

    private static Path normalize(final Path path, final String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
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
