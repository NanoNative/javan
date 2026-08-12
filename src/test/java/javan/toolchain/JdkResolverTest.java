package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkResolverTest {
    @TempDir
    private Path tempDir;

    @Test
    void explicitJdkWinsOverEveryAutomaticCandidate() throws Exception {
        final Path explicit = jdk("explicit");
        final Path javaHome = jdk("java-home");
        final Path current = jdk("current");
        final Path path = jdk("path");
        final ToolchainMetadata managed = managedJdk("managed");

        final JdkResolver.Resolution resolution = resolver(
            Map.of("JAVA_HOME", javaHome.toString()),
            Optional.of(current),
            Optional.of(path.resolve("bin/javac")),
            List.of(managed)
        ).resolve(Optional.of(explicit));

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().origin()).isEqualTo("explicit");
        assertThat(resolution.selected().orElseThrow().home()).isEqualTo(explicit);
        assertThat(resolution.candidates()).extracting(JdkResolver.Candidate::origin)
            .containsExactly("explicit", "JAVA_HOME", "current", "PATH", "managed:managed");
    }

    @Test
    void validJavaHomeWinsOverCurrentPathAndManagedCandidates() throws Exception {
        final Path javaHome = jdk("java-home");
        final Path current = jdk("current");
        final Path path = jdk("path");

        final JdkResolver.Resolution resolution = resolver(
            Map.of("JAVA_HOME", javaHome.toString()),
            Optional.of(current),
            Optional.of(path.resolve("bin/javac")),
            List.of(managedJdk("managed"))
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().origin()).isEqualTo("JAVA_HOME");
        assertThat(resolution.selected().orElseThrow().home()).isEqualTo(javaHome);
    }

    @Test
    void invalidHigherPriorityCandidateIsReportedAndSkipped() throws Exception {
        final Path invalidHome = Files.createDirectories(tempDir.resolve("invalid"));
        final Path current = jdk("current");

        final JdkResolver.Resolution resolution = resolver(
            Map.of("JAVA_HOME", invalidHome.toString()),
            Optional.of(current),
            Optional.empty(),
            List.of()
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().origin()).isEqualTo("current");
        assertThat(resolution.candidates()).anySatisfy(candidate -> {
            assertThat(candidate.origin()).isEqualTo("JAVA_HOME");
            assertThat(candidate.usable()).isFalse();
            assertThat(candidate.reason()).contains("javac");
        });
    }

    @Test
    void pathJavacResolvesWhenNoHigherPriorityJdkExists() throws Exception {
        final Path path = jdk("path");

        final JdkResolver.Resolution resolution = resolver(
            Map.of(),
            Optional.empty(),
            Optional.of(path.resolve("bin/javac")),
            List.of()
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().origin()).isEqualTo("PATH");
        assertThat(resolution.selected().orElseThrow().home()).isEqualTo(path);
    }

    @Test
    void pathLauncherRemainsDelegatableButDoesNotClaimToBeAJdkHome() throws Exception {
        final Path launcher = launcher("path-launcher");

        final JdkResolver.Resolution resolution = resolver(
            Map.of(),
            Optional.empty(),
            Optional.of(launcher.resolve("bin/javac")),
            List.of()
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().origin()).isEqualTo("PATH");
        assertThat(resolution.selected().orElseThrow().reason()).isEqualTo("usable launcher; JDK home unresolved");
    }

    @Test
    void configuredHomeWithoutReleaseMetadataIsRejected() throws Exception {
        final Path launcher = launcher("configured-launcher");

        final JdkResolver.Resolution resolution = resolver(
            Map.of("JAVA_HOME", launcher.toString()),
            Optional.empty(),
            Optional.empty(),
            List.of()
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isEmpty();
        assertThat(resolution.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.origin()).isEqualTo("JAVA_HOME");
            assertThat(candidate.usable()).isFalse();
            assertThat(candidate.reason()).isEqualTo("missing JDK release metadata");
        });
    }

    @Test
    void platformJdkWinsOverManagedMetadataWhenNoHigherPriorityCandidateExists() throws Exception {
        final Path platform = jdk("platform");
        final ToolchainMetadata managed = managedJdk("managed");

        final JdkResolver.Resolution resolution = new JdkResolver(
            Map.of(),
            Optional.empty(),
            Optional.empty(),
            List.of(managed),
            "Mac OS X",
            List.of(platform)
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().origin()).isEqualTo("platform");
        assertThat(resolution.selected().orElseThrow().home()).isEqualTo(platform);
    }

    @Test
    void windowsResolutionUsesExeTools() throws Exception {
        final Path home = windowsJdk("windows");

        final JdkResolver.Resolution resolution = new JdkResolver(
            Map.of(),
            Optional.of(home),
            Optional.empty(),
            List.of(),
            "Windows 11"
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isPresent();
        assertThat(resolution.selected().orElseThrow().javaExecutable()).isEqualTo(home.resolve("bin/java.exe"));
        assertThat(resolution.selected().orElseThrow().javacExecutable()).isEqualTo(home.resolve("bin/javac.exe"));
    }

    @Test
    void missingCandidatesReturnAnEmptyResolution() {
        final JdkResolver.Resolution resolution = resolver(
            Map.of(),
            Optional.empty(),
            Optional.empty(),
            List.of()
        ).resolve(Optional.empty());

        assertThat(resolution.selected()).isEmpty();
        assertThat(resolution.candidates()).isEmpty();
    }

    private JdkResolver resolver(
        final Map<String, String> environment,
        final Optional<Path> currentJavaHome,
        final Optional<Path> pathJavac,
        final List<ToolchainMetadata> managed
    ) {
        return new JdkResolver(environment, currentJavaHome, pathJavac, managed, "Mac OS X");
    }

    private ToolchainMetadata managedJdk(final String id) throws IOException {
        final Path home = jdk(id);
        return new ToolchainMetadata(
            id,
            ToolchainKind.JDK,
            "25",
            home,
            home.resolve("bin/java"),
            home.resolve("bin/javac"),
            Optional.of("Eclipse Temurin"),
            Optional.empty()
        );
    }

    private Path jdk(final String name) throws IOException {
        final Path home = Files.createDirectories(tempDir.resolve(name));
        final Path bin = Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("release"));
        assertThat(Files.createFile(bin.resolve("java")).toFile().setExecutable(true)).isTrue();
        assertThat(Files.createFile(bin.resolve("javac")).toFile().setExecutable(true)).isTrue();
        return home.toAbsolutePath().normalize();
    }

    private Path windowsJdk(final String name) throws IOException {
        final Path home = Files.createDirectories(tempDir.resolve(name));
        final Path bin = Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("release"));
        assertThat(Files.createFile(bin.resolve("java.exe")).toFile().setExecutable(true)).isTrue();
        assertThat(Files.createFile(bin.resolve("javac.exe")).toFile().setExecutable(true)).isTrue();
        return home.toAbsolutePath().normalize();
    }

    private Path launcher(final String name) throws IOException {
        final Path home = Files.createDirectories(tempDir.resolve(name));
        final Path bin = Files.createDirectories(home.resolve("bin"));
        assertThat(Files.createFile(bin.resolve("java")).toFile().setExecutable(true)).isTrue();
        assertThat(Files.createFile(bin.resolve("javac")).toFile().setExecutable(true)).isTrue();
        return home.toAbsolutePath().normalize();
    }
}
