package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds JDK homes under deterministic platform-specific installation roots.
 */
public final class JdkLocationDiscovery {
    private final Map<String, String> environment;
    private final Path userHome;
    private final String osName;

    /**
     * Creates a local JDK location scanner.
     *
     * @param environment process environment
     * @param userHome user home directory
     * @param osName operating-system name
     */
    public JdkLocationDiscovery(final Map<String, String> environment, final Path userHome, final String osName) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.userHome = Objects.requireNonNull(userHome, "userHome").toAbsolutePath().normalize();
        this.osName = Objects.requireNonNull(osName, "osName");
    }

    /**
     * Returns discovered JDK homes in deterministic order.
     *
     * @return verified JDK-home locations
     */
    public List<Path> homes() throws IOException {
        final List<Path> result = new ArrayList<>();
        for (final Path root : roots()) {
            addHomesBelowRoot(result, root);
        }
        return List.copyOf(result);
    }

    private List<Path> roots() {
        if (isWindows()) {
            return windowsRoots();
        }
        if (isMac()) {
            return macRoots();
        }
        return linuxRoots();
    }

    private List<Path> macRoots() {
        return List.of(
            userHome.resolve("Library/Java/JavaVirtualMachines"),
            Path.of("/Library/Java/JavaVirtualMachines"),
            Path.of("/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"),
            Path.of("/usr/local/opt/openjdk/libexec/openjdk.jdk/Contents/Home")
        );
    }

    private List<Path> linuxRoots() {
        return List.of(
            Path.of("/usr/lib/jvm"),
            Path.of("/usr/java"),
            userHome.resolve(".sdkman/candidates/java"),
            userHome.resolve(".jdks")
        );
    }

    private List<Path> windowsRoots() {
        final List<Path> result = new ArrayList<>();
        addWindowsVendorRoots(result, environmentPath("ProgramFiles"));
        addWindowsVendorRoots(result, environmentPath("ProgramW6432"));
        addWindowsVendorRoots(result, environmentPath("ProgramFiles(x86)"));
        return List.copyOf(result);
    }

    private static void addWindowsVendorRoots(final List<Path> roots, final java.util.Optional<Path> programFiles) {
        if (programFiles.isEmpty()) {
            return;
        }
        final Path root = programFiles.orElseThrow();
        addPath(roots, root.resolve("Java"));
        addPath(roots, root.resolve("Eclipse Adoptium"));
        addPath(roots, root.resolve("Amazon Corretto"));
        addPath(roots, root.resolve("Microsoft"));
        addPath(roots, root.resolve("Zulu"));
    }

    private java.util.Optional<Path> environmentPath(final String name) {
        final String value = environment.get(name);
        if (Strings2.isBlank(value)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Path.of(value).toAbsolutePath().normalize());
    }

    private static void addHomesBelowRoot(final List<Path> homes, final Path root) throws IOException {
        addHomeVariants(homes, root);
        if (!Files.isDirectory(root)) {
            return;
        }
        final List<Path> children = children(root);
        for (final Path child : children) {
            addHomeVariants(homes, child);
        }
    }

    private static List<Path> children(final Path root) throws IOException {
        final List<Path> result = new ArrayList<>();
        final DirectoryStream<Path> stream = Files.newDirectoryStream(root);
        for (final Path child : stream) {
            if (Files.isDirectory(child)) {
                insertPath(result, child.toAbsolutePath().normalize());
            }
        }
        stream.close();
        return List.copyOf(result);
    }

    private static void addHomeVariants(final List<Path> homes, final Path location) {
        addHome(homes, location);
        addHome(homes, location.resolve("Contents/Home"));
        addHome(homes, location.resolve("libexec/openjdk.jdk/Contents/Home"));
    }

    private static void addHome(final List<Path> homes, final Path home) {
        final Path normalized = home.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized.resolve("release"))) {
            return;
        }
        insertPath(homes, normalized);
    }

    private static void addPath(final List<Path> paths, final Path path) {
        insertPath(paths, path.toAbsolutePath().normalize());
    }

    private static void insertPath(final List<Path> values, final Path value) {
        int index = 0;
        while (index < values.size() && Strings2.compareAscii(values.get(index).toString(), value.toString()) < 0) {
            index++;
        }
        if (index < values.size() && values.get(index).equals(value)) {
            return;
        }
        values.add(index, value);
    }

    private boolean isMac() {
        return Strings2.toAsciiLowerCase(osName).contains("mac");
    }

    private boolean isWindows() {
        return Strings2.toAsciiLowerCase(osName).contains("win");
    }
}
