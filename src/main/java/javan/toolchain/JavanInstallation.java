package javan.toolchain;

import javan.toolchain.facade.JdkFacadeStore;
import javan.toolchain.facade.JdkFacadeGenerator;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Installs one self-contained Javan JDK facade without modifying a vendor JDK.
 */
public final class JavanInstallation {
    private final List<Location> locations;
    private final ProcessRunner processRunner;
    private final String osName;

    /**
     * Creates the installation policy for the current host.
     *
     * @param javanHome global Javan home used for the user fallback
     */
    public JavanInstallation(final Path javanHome) {
        this(locations(javanHome, Path.of(System.getProperty("java.io.tmpdir", ".")), System.getProperty("os.name", "")), new ProcessRunner(), System.getProperty("os.name", ""));
    }

    JavanInstallation(final List<Location> locations, final ProcessRunner processRunner, final String osName) {
        this.locations = List.copyOf(Objects.requireNonNull(locations, "locations"));
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.osName = Objects.requireNonNull(osName, "osName");
    }

    /**
     * Copies the native launcher, creates the selected facade, and publishes one stable JDK home.
     *
     * @param launcher packaged native Javan executable
     * @param selected selected backend JDK
     * @return completed installation
     * @throws IOException when no safe installation location is writable
     * @throws InterruptedException when interrupted while publishing links
     */
    public Installation install(final Path launcher, final JdkInventory.Entry selected) throws IOException, InterruptedException {
        final Path source = normalize(launcher, "launcher");
        if (!Files.isRegularFile(source)) {
            throw new IOException("Javan launcher is unavailable: " + source);
        }
        for (final Location location : locations) {
            if (!prepare(location)) {
                continue;
            }
            final Path installedLauncher = installLauncher(source, location.facadeRoot());
            final JdkFacadeStore store = new JdkFacadeStore(
                location.facadeRoot(),
                new JdkFacadeGenerator(),
                processRunner,
                osName
            );
            store.registerPublicHome(location.publicHome());
            final JdkFacadeStore.Activation activation = store.activate(selected);
            publishHome(location.publicHome(), activation.current());
            store.refreshPublicBundle(selected);
            return new Installation(location, installedLauncher, activation.current(), activation.facade().backendHome());
        }
        throw new IOException("Unable to install Javan in machine, user, or temporary locations");
    }

    private boolean prepare(final Location location) throws IOException, InterruptedException {
        final Path parent = location.publicHome().getParent();
        if (parent == null || !publicHomeIsAvailable(location.publicHome())) {
            return false;
        }
        return Files2.createDirectoriesIfPossible(location.facadeRoot())
            && Files2.createDirectoriesIfPossible(parent);
    }

    private boolean publicHomeIsAvailable(final Path publicHome) throws IOException, InterruptedException {
        if (!Files.exists(publicHome)) {
            return true;
        }
        if (isWindows()) {
            return Files.isRegularFile(publicHome.resolve("javan-backend.txt"));
        }
        final Path parent = Objects.requireNonNull(publicHome.getParent(), "public JDK home parent");
        return processRunner.run(parent, List.of("test", "-L", publicHome.toString())).exitCode() == 0
            && Files.isRegularFile(publicHome.resolve("javan-backend.txt"));
    }

    private Path installLauncher(final Path source, final Path facadeRoot) throws IOException, InterruptedException {
        final Path target = facadeRoot.resolve(executableName("javan"));
        final Path staging = facadeRoot.resolve(executableName("javan") + ".new");
        Files.copy(source, staging, StandardCopyOption.REPLACE_EXISTING);
        if (!isWindows()) {
            final ProcessRunner.Result chmod = processRunner.run(facadeRoot, List.of("chmod", "+x", staging.toString()));
            requireSuccess(chmod, "mark executable", staging);
            final ProcessRunner.Result publish = processRunner.run(facadeRoot, List.of("mv", "-f", staging.toString(), target.toString()));
            requireSuccess(publish, "publish launcher", target);
        } else {
            requireSuccess(
                processRunner.run(facadeRoot, List.of("cmd", "/d", "/s", "/c", "move /Y " + windowsQuote(staging) + " " + windowsQuote(target))),
                "publish launcher",
                target
            );
        }
        return target;
    }

    private void publishHome(final Path publicHome, final Path current) throws IOException, InterruptedException {
        if (isWindows()) {
            publishWindowsJunction(publicHome, current);
            return;
        }
        if (Files.exists(publicHome)) {
            final ProcessRunner.Result inspected = processRunner.run(publicHome.getParent(), List.of("test", "-L", publicHome.toString()));
            if (inspected.exitCode() != 0) {
                throw new IOException("Javan public JDK home is not an owned link: " + publicHome);
            }
        }
        final Path target = publicHome.getParent().relativize(current);
        requireSuccess(
            processRunner.run(publicHome.getParent(), List.of("ln", "-sfn", target.toString(), publicHome.toString())),
            "publish JDK home",
            publicHome
        );
    }

    private void publishWindowsJunction(final Path publicHome, final Path current) throws IOException, InterruptedException {
        if (Files.exists(publicHome)) {
            final Path metadata = publicHome.resolve("javan-backend.txt");
            if (!Files.isRegularFile(metadata)) {
                throw new IOException("Javan public JDK home is not owned by Javan: " + publicHome);
            }
            requireSuccess(
                processRunner.run(publicHome.getParent(), List.of("cmd", "/d", "/s", "/c", "rmdir " + windowsQuote(publicHome))),
                "replace JDK home junction",
                publicHome
            );
        }
        requireSuccess(
            processRunner.run(
                publicHome.getParent(),
                List.of("cmd", "/d", "/s", "/c", "mklink /J " + windowsQuote(publicHome) + " " + windowsQuote(current))
            ),
            "publish JDK home junction",
            publicHome
        );
    }

    private boolean isWindows() {
        return Strings2.toAsciiLowerCase(osName).contains("win");
    }

    private String executableName(final String base) {
        return isWindows() ? base + ".exe" : base;
    }

    private static void requireSuccess(final ProcessRunner.Result result, final String action, final Path target) throws IOException {
        if (result.exitCode() == 0) {
            return;
        }
        final String detail = Strings2.isBlank(result.stderr()) ? result.stdout() : result.stderr();
        throw new IOException("Unable to " + action + " " + target + ": " + detail);
    }

    private static String windowsQuote(final Path path) {
        return "\"" + path + "\"";
    }

    private static List<Location> locations(final Path javanHome, final Path temporary, final String osName) {
        final Path home = normalize(javanHome, "javanHome");
        final Path temp = normalize(temporary, "temporary");
        final List<Location> result = new ArrayList<>();
        final String normalizedOs = Strings2.toAsciiLowerCase(osName);
        if (normalizedOs.contains("win")) {
            final Path programFiles = windowsProgramFiles();
            result.add(new Location("machine", programFiles.resolve("Java/javan"), programFiles.resolve("Java/.javan-facade")));
        } else if (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) {
            result.add(new Location(
                "machine",
                Path.of("/Library/Java/JavaVirtualMachines/javan.jdk/Contents/Home"),
                Path.of("/Library/Java/JavaVirtualMachines/.javan-facade")
            ));
            result.add(new Location(
                "user",
                Path.of(System.getProperty("user.home", ".")).resolve("Library/Java/JavaVirtualMachines/javan.jdk/Contents/Home"),
                home.resolve("facades")
            ));
        } else {
            result.add(new Location("machine", Path.of("/usr/lib/jvm/javan"), Path.of("/usr/lib/jvm/.javan-facade")));
        }
        if (!normalizedOs.contains("mac") && !normalizedOs.contains("darwin")) {
            result.add(new Location("user", home.resolve("jdk"), home.resolve("facades")));
        }
        result.add(new Location("temporary", temp.resolve("javan/jdk"), temp.resolve("javan/facades")));
        return List.copyOf(result);
    }

    private static Path windowsProgramFiles() {
        final String value = System.getenv("ProgramFiles");
        if (Strings2.isBlank(value)) {
            return Path.of("C:/Program Files").toAbsolutePath().normalize();
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static Path normalize(final Path value, final String name) {
        return Objects.requireNonNull(value, name).toAbsolutePath().normalize();
    }

    /**
     * Completed facade installation paths.
     *
     * @param location selected installation location
     * @param launcher copied Javan launcher
     * @param current stable facade target
     * @param backend real unmodified vendor JDK
     */
    public record Installation(Location location, Path launcher, Path current, Path backend) {
        /**
         * Creates normalized installation paths.
         */
        public Installation {
            location = Objects.requireNonNull(location, "location");
            launcher = normalize(launcher, "launcher");
            current = normalize(current, "current");
            backend = normalize(backend, "backend");
        }
    }

    /**
     * One machine, user, or temporary facade location.
     *
     * @param scope persistence scope
     * @param publicHome stable JDK-shaped public home
     * @param facadeRoot private switchable facade store
     */
    public record Location(String scope, Path publicHome, Path facadeRoot) {
        /**
         * Creates normalized locations.
         */
        public Location {
            scope = Objects.requireNonNull(scope, "scope");
            publicHome = normalize(publicHome, "publicHome");
            facadeRoot = normalize(facadeRoot, "facadeRoot");
        }
    }
}
