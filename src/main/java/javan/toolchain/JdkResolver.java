package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves a usable local JDK without installing or mutating anything.
 */
public final class JdkResolver {
    private final Map<String, String> environment;
    private final Optional<Path> currentJavaHome;
    private final Optional<Path> pathJavac;
    private final List<ToolchainMetadata> managedToolchains;
    private final String osName;
    private final List<Path> platformHomes;

    /**
     * Creates a deterministic local JDK resolver.
     *
     * @param environment process environment
     * @param currentJavaHome JDK home running Javan when known
     * @param pathJavac javac executable discovered on PATH when known
     * @param managedToolchains Javan-managed toolchains
     * @param osName operating-system name
     */
    public JdkResolver(
        final Map<String, String> environment,
        final Optional<Path> currentJavaHome,
        final Optional<Path> pathJavac,
        final List<ToolchainMetadata> managedToolchains,
        final String osName
    ) {
        this(environment, currentJavaHome, pathJavac, managedToolchains, osName, List.of());
    }

    /**
     * Creates a deterministic local JDK resolver with discovered platform JDK homes.
     *
     * @param environment process environment
     * @param currentJavaHome JDK home running Javan when known
     * @param pathJavac javac executable discovered on PATH when known
     * @param managedToolchains Javan-managed toolchains
     * @param osName operating-system name
     * @param platformHomes ordered JDK homes discovered from platform locations
     */
    public JdkResolver(
        final Map<String, String> environment,
        final Optional<Path> currentJavaHome,
        final Optional<Path> pathJavac,
        final List<ToolchainMetadata> managedToolchains,
        final String osName,
        final List<Path> platformHomes
    ) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.currentJavaHome = normalizeOptional(currentJavaHome, "currentJavaHome");
        this.pathJavac = normalizeOptional(pathJavac, "pathJavac");
        this.managedToolchains = List.copyOf(Objects.requireNonNull(managedToolchains, "managedToolchains"));
        this.osName = Objects.requireNonNull(osName, "osName");
        this.platformHomes = List.copyOf(Objects.requireNonNull(platformHomes, "platformHomes"));
    }

    /**
     * Resolves the first usable local JDK in deterministic precedence order.
     *
     * @param explicitHome explicit JDK home requested by the user
     * @return selected JDK and every candidate that was evaluated
     */
    public Resolution resolve(final Optional<Path> explicitHome) throws IOException {
        Objects.requireNonNull(explicitHome, "explicitHome");
        final List<Candidate> candidates = new ArrayList<>();
        if (explicitHome.isPresent()) {
            candidates.add(fromHome("explicit", normalize(explicitHome.orElseThrow())));
        }
        addEnvironmentHome(candidates, "JAVA_HOME");
        addEnvironmentHome(candidates, "JDK_HOME");
        if (currentJavaHome.isPresent()) {
            candidates.add(fromHome("current", currentJavaHome.orElseThrow()));
        }
        if (pathJavac.isPresent()) {
            final Optional<Candidate> pathCandidate = fromPathJavac(pathJavac.orElseThrow());
            if (pathCandidate.isPresent()) {
                candidates.add(pathCandidate.orElseThrow());
            }
        }
        for (final Path platformHome : platformHomes) {
            candidates.add(fromHome("platform", platformHome));
        }
        for (final ToolchainMetadata managed : managedToolchains) {
            candidates.add(fromManaged(managed));
        }
        return new Resolution(firstUsable(candidates), List.copyOf(candidates));
    }

    private void addEnvironmentHome(final List<Candidate> candidates, final String name) throws IOException {
        final Optional<Path> home = environmentHome(name);
        if (home.isPresent()) {
            candidates.add(fromHome(name, home.orElseThrow()));
        }
    }

    private Optional<Path> environmentHome(final String name) {
        final String value = environment.get(name);
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(normalize(Path.of(value)));
    }

    private Candidate fromHome(final String origin, final Path home) throws IOException {
        return candidate(
            origin,
            home,
            executable(home, "java"),
            executable(home, "javac"),
            true
        );
    }

    private Optional<Candidate> fromPathJavac(final Path javac) throws IOException {
        final Path bin = javac.getParent();
        if (bin == null || bin.getParent() == null) {
            return Optional.empty();
        }
        final Path home = bin.getParent();
        return Optional.of(candidate("PATH", home, executable(home, "java"), javac, false));
    }

    private Candidate fromManaged(final ToolchainMetadata toolchain) throws IOException {
        return candidate(
            "managed:" + toolchain.id(),
            toolchain.home(),
            toolchain.javaExecutable(),
            toolchain.javacExecutable(),
            true
        );
    }

    private Candidate candidate(
        final String origin,
        final Path home,
        final Path javaExecutable,
        final Path javacExecutable,
        final boolean requiresJdkHome
    ) throws IOException {
        final Path suppliedHome = normalize(home);
        final Path normalizedHome = facadeBackendHome(suppliedHome);
        final Path normalizedJava = suppliedHome.equals(normalizedHome)
            ? normalize(javaExecutable)
            : executable(normalizedHome, "java");
        final Path normalizedJavac = suppliedHome.equals(normalizedHome)
            ? normalize(javacExecutable)
            : executable(normalizedHome, "javac");
        final String javacReason = unavailableReason(normalizedJavac, "javac");
        if (!javacReason.isEmpty()) {
            return new Candidate(origin, normalizedHome, normalizedJava, normalizedJavac, false, javacReason);
        }
        final String javaReason = unavailableReason(normalizedJava, "java");
        if (!javaReason.isEmpty()) {
            return new Candidate(origin, normalizedHome, normalizedJava, normalizedJavac, false, javaReason);
        }
        if (requiresJdkHome && !isJdkHome(normalizedHome)) {
            return new Candidate(origin, normalizedHome, normalizedJava, normalizedJavac, false, "missing JDK release metadata");
        }
        if (!requiresJdkHome && !isJdkHome(normalizedHome)) {
            return new Candidate(
                origin,
                normalizedHome,
                normalizedJava,
                normalizedJavac,
                true,
                "usable launcher; JDK home unresolved"
            );
        }
        return new Candidate(origin, normalizedHome, normalizedJava, normalizedJavac, true, "usable");
    }

    private static boolean isJdkHome(final Path home) {
        return Files.isRegularFile(home.resolve("release"));
    }

    private static Path facadeBackendHome(final Path home) throws IOException {
        final Path metadata = home.resolve("javan-backend.txt");
        if (!Files.isRegularFile(metadata)) {
            return home;
        }
        final String content = Files.readString(metadata);
        final String prefix = "backendHome=";
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.slice(content, start, index);
                if (line.startsWith(prefix)) {
                    final Path backend = normalize(Path.of(Strings2.slice(line, prefix.length(), line.length())));
                    if (!backend.equals(home)) {
                        return backend;
                    }
                }
                start = index + 1;
            }
        }
        return home;
    }

    private static String unavailableReason(final Path executable, final String name) {
        if (!Files.isRegularFile(executable)) {
            return "missing " + name;
        }
        if (!Files.isExecutable(executable)) {
            return name + " is not executable";
        }
        return "";
    }

    private Path executable(final Path home, final String name) {
        final String executable = isWindows() ? name + ".exe" : name;
        return home.resolve("bin").resolve(executable);
    }

    private boolean isWindows() {
        return Strings2.toAsciiLowerCase(osName).contains("win");
    }

    private static Optional<Candidate> firstUsable(final List<Candidate> candidates) {
        for (final Candidate candidate : candidates) {
            if (candidate.usable()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<Path> normalizeOptional(final Optional<Path> value, final String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(normalize(value.orElseThrow()));
    }

    private static Path normalize(final Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    /**
     * JDK resolution result.
     *
     * @param selected selected usable JDK when one exists
     * @param candidates ordered candidates that were evaluated
     */
    public record Resolution(Optional<Candidate> selected, List<Candidate> candidates) {
        /**
         * Creates an immutable resolution result.
         */
        public Resolution {
            selected = Objects.requireNonNull(selected, "selected");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    /**
     * One evaluated local JDK candidate.
     *
     * @param origin deterministic discovery source
     * @param home candidate JDK home
     * @param javaExecutable candidate java executable
     * @param javacExecutable candidate javac executable
     * @param usable whether both executables are usable
     * @param reason concise usability result
     */
    public record Candidate(
        String origin,
        Path home,
        Path javaExecutable,
        Path javacExecutable,
        boolean usable,
        String reason
    ) {
        /**
         * Creates a validated JDK candidate.
         */
        public Candidate {
            if (Strings2.isBlank(origin)) {
                throw new IllegalArgumentException("origin");
            }
            origin = Strings2.trimAscii(origin);
            home = normalize(home);
            javaExecutable = normalize(javaExecutable);
            javacExecutable = normalize(javacExecutable);
            if (Strings2.isBlank(reason)) {
                throw new IllegalArgumentException("reason");
            }
            reason = Strings2.trimAscii(reason);
        }
    }
}
