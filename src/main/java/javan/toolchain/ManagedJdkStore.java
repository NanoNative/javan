package javan.toolchain;

import javan.util.Strings2;
import javan.util.Files2;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Chooses the durable location for verified managed JDK installations.
 *
 * <p>The selection order is machine-wide platform location, the current user's Javan home,
 * then an ephemeral private temporary cache. It never elevates privileges. Callers must only
 * prepare a store after a verified JDK archive is ready to install.</p>
 */
public final class ManagedJdkStore {
    private final Path javanHome;
    private final Path temporaryDirectory;
    private final String osName;
    private final Map<String, String> environment;
    /**
     * Creates the managed JDK store policy for the current host.
     *
     * @param javanHome user-global Javan home
     */
    public ManagedJdkStore(final Path javanHome) {
        this(javanHome, temporaryDirectory(), System.getProperty("os.name", ""), environment());
    }

    ManagedJdkStore(
        final Path javanHome,
        final Path temporaryDirectory,
        final String osName,
        final Map<String, String> environment
    ) {
        this.javanHome = normalize(javanHome, "javanHome");
        this.temporaryDirectory = normalize(temporaryDirectory, "temporaryDirectory");
        this.osName = Objects.requireNonNull(osName, "osName");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    /**
     * Returns the ordered install locations without touching the filesystem.
     *
     * @return machine-wide, user, then temporary fallback locations
     */
    public List<Location> locations() {
        return List.of(machineLocation(), userLocation(), temporaryLocation());
    }

    /**
     * Creates the first writable install and archive-cache pair in policy order.
     *
     * <p>The machine-wide location is attempted first but never requires elevation. A permission
     * or filesystem failure falls through to the user store, then to the temporary cache.</p>
     *
     * @return prepared storage location, or empty when every location failed
     */
    public Optional<Location> prepare() {
        for (final Location location : locations()) {
            if (Files2.createDirectoriesIfPossible(location.installRoot())
                && Files2.createDirectoriesIfPossible(location.downloadCache())) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    static Optional<Location> prepareForTesting(
        final List<Location> locations,
        final DirectoryPreparation preparation
    ) {
        Objects.requireNonNull(locations, "locations");
        Objects.requireNonNull(preparation, "preparation");
        for (final Location location : locations) {
            if (preparation.prepare(location.installRoot()) && preparation.prepare(location.downloadCache())) {
                return Optional.of(location);
            }
        }
        return Optional.empty();
    }

    private Location machineLocation() {
        if (isWindows()) {
            final Path programFiles = environmentPath("ProgramFiles", "C:/Program Files");
            final Path programData = environmentPath("ProgramData", "C:/ProgramData");
            return new Location(
                "machine",
                programFiles.resolve("Java"),
                programData.resolve("Javan/cache/downloads"),
                true
            );
        }
        if (isMac()) {
            return new Location(
                "machine",
                Path.of("/Library/Java/JavaVirtualMachines"),
                Path.of("/Library/Caches/Javan/downloads"),
                true
            );
        }
        return new Location(
            "machine",
            Path.of("/usr/lib/jvm"),
            Path.of("/var/cache/javan/downloads"),
            true
        );
    }

    private Location userLocation() {
        return new Location(
            "user",
            javanHome.resolve("jdks"),
            javanHome.resolve("cache/downloads"),
            true
        );
    }

    private Location temporaryLocation() {
        final Path root = temporaryDirectory.resolve("javan/jdks");
        return new Location(
            "temporary",
            root,
            temporaryDirectory.resolve("javan/cache/downloads"),
            false
        );
    }

    private Path environmentPath(final String name, final String fallback) {
        final String value = environment.get(name);
        if (Strings2.isBlank(value)) {
            return normalize(Path.of(fallback), "fallback");
        }
        return normalize(Path.of(value), name);
    }

    private boolean isMac() {
        return Strings2.toAsciiLowerCase(osName).contains("mac");
    }

    private boolean isWindows() {
        return Strings2.toAsciiLowerCase(osName).contains("win");
    }

    private static Path temporaryDirectory() {
        return Path.of(System.getProperty("java.io.tmpdir", "."));
    }

    private static Map<String, String> environment() {
        final Map<String, String> values = new HashMap<>();
        addEnvironment(values, "ProgramFiles");
        addEnvironment(values, "ProgramData");
        return values;
    }

    private static void addEnvironment(final Map<String, String> target, final String name) {
        final String value = System.getenv(name);
        if (!Strings2.isBlank(value)) {
            target.put(name, value);
        }
    }

    private static Path normalize(final Path path, final String name) {
        Objects.requireNonNull(path, name);
        return path.toAbsolutePath().normalize();
    }

    /**
     * One possible managed JDK install location.
     *
     * @param scope persistence scope
     * @param installRoot JDK installation parent directory
     * @param downloadCache verified archive cache directory
     * @param persistent whether the store survives temporary-cache cleanup
     */
    public record Location(String scope, Path installRoot, Path downloadCache, boolean persistent) {
        /**
         * Creates a normalized storage location.
         */
        public Location {
            scope = Objects.requireNonNull(scope, "scope");
            installRoot = normalize(installRoot, "installRoot");
            downloadCache = normalize(downloadCache, "downloadCache");
        }
    }

    @FunctionalInterface
    interface DirectoryPreparation {
        boolean prepare(Path path);
    }

}
