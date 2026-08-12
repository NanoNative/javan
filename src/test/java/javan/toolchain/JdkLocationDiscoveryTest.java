package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkLocationDiscoveryTest {
    @TempDir
    private Path tempDir;

    @Test
    void macosDiscoveryFindsJdkBundleBelowTheUserJavaRoot() throws Exception {
        final Path userHome = tempDir.resolve("home");
        final Path jdk = jdk(userHome.resolve("Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"));

        final java.util.List<Path> homes = new JdkLocationDiscovery(Map.of(), userHome, "Mac OS X").homes();

        assertThat(homes).contains(jdk);
    }

    @Test
    void linuxDiscoveryFindsSdkmanJdkHome() throws Exception {
        final Path userHome = tempDir.resolve("home");
        final Path jdk = jdk(userHome.resolve(".sdkman/candidates/java/25.0.1-tem/"));

        final java.util.List<Path> homes = new JdkLocationDiscovery(Map.of(), userHome, "Linux").homes();

        assertThat(homes).contains(jdk);
    }

    @Test
    void windowsDiscoveryFindsKnownVendorRootFromProgramFiles() throws Exception {
        final Path programFiles = tempDir.resolve("Program Files");
        final Path jdk = windowsJdk(programFiles.resolve("Eclipse Adoptium/jdk-25"));

        final java.util.List<Path> homes = new JdkLocationDiscovery(
            Map.of("ProgramFiles", programFiles.toString()),
            tempDir.resolve("home"),
            "Windows 11"
        ).homes();

        assertThat(homes).contains(jdk);
    }

    @Test
    void discoveryIgnoresDirectoriesWithoutReleaseMetadata() throws Exception {
        final Path userHome = tempDir.resolve("home");
        Files.createDirectories(userHome.resolve(".jdks/not-a-jdk/bin"));

        final java.util.List<Path> homes = new JdkLocationDiscovery(Map.of(), userHome, "Linux").homes();

        assertThat(homes).doesNotContain(userHome.resolve(".jdks/not-a-jdk").toAbsolutePath().normalize());
    }

    private static Path jdk(final Path home) throws IOException {
        final Path normalized = Files.createDirectories(home).toAbsolutePath().normalize();
        final Path bin = Files.createDirectories(normalized.resolve("bin"));
        Files.createFile(normalized.resolve("release"));
        assertThat(Files.createFile(bin.resolve("java")).toFile().setExecutable(true)).isTrue();
        assertThat(Files.createFile(bin.resolve("javac")).toFile().setExecutable(true)).isTrue();
        return normalized;
    }

    private static Path windowsJdk(final Path home) throws IOException {
        final Path normalized = Files.createDirectories(home).toAbsolutePath().normalize();
        final Path bin = Files.createDirectories(normalized.resolve("bin"));
        Files.createFile(normalized.resolve("release"));
        assertThat(Files.createFile(bin.resolve("java.exe")).toFile().setExecutable(true)).isTrue();
        assertThat(Files.createFile(bin.resolve("javac.exe")).toFile().setExecutable(true)).isTrue();
        return normalized;
    }
}
