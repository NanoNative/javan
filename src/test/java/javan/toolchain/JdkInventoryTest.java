package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class JdkInventoryTest {
    @TempDir
    private Path tempDir;

    @Test
    void listsUsableJdksWithVersionVendorAndDiscoveryOrigin() throws Exception {
        final Path temurin = jdk("temurin", "25.0.1", "Eclipse Adoptium");
        final Path corretto = jdk("corretto", "25.0.1", "Amazon.com Inc.");
        final JdkInventory inventory = new JdkInventory();

        final List<JdkInventory.Entry> entries = inventory.inspect(new JdkResolver.Resolution(
            Optional.empty(),
            List.of(candidate("PATH", temurin), candidate("platform", corretto))
        ));

        assertThat(entries).extracting(JdkInventory.Entry::vendor)
            .containsExactly("Eclipse Adoptium", "Amazon.com Inc.");
        assertThat(entries).extracting(JdkInventory.Entry::featureVersion).containsExactly("25", "25");
        assertThat(entries).extracting(entry -> entry.candidate().origin()).containsExactly("PATH", "platform");
    }

    @Test
    void selectsTheFirstDiscoveredUsableJdkForABareFeatureVersion() throws Exception {
        final Path temurin = jdk("temurin", "25.0.1", "Eclipse Adoptium");
        final Path corretto = jdk("corretto", "25.0.1", "Amazon.com Inc.");
        final JdkInventory inventory = new JdkInventory();
        final List<JdkInventory.Entry> entries = inventory.inspect(new JdkResolver.Resolution(
            Optional.empty(),
            List.of(candidate("JAVA_HOME", corretto), candidate("PATH", temurin))
        ));

        final Optional<JdkInventory.Entry> selected = inventory.select(entries, "25");

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().candidate().home()).isEqualTo(corretto);
    }

    @Test
    void recognizesTemurinAsAnAdoptiumVendorAlias() throws Exception {
        final Path temurin = jdk("temurin", "25.0.1", "Eclipse Adoptium");
        final JdkInventory inventory = new JdkInventory();
        final List<JdkInventory.Entry> entries = inventory.inspect(new JdkResolver.Resolution(
            Optional.empty(),
            List.of(candidate("platform", temurin))
        ));

        final Optional<JdkInventory.Entry> selected = inventory.select(entries, "temurin@25");

        assertThat(selected).isPresent();
        assertThat(selected.orElseThrow().vendor()).isEqualTo("Eclipse Adoptium");
    }

    @Test
    void excludesDelegatablePathLaunchersFromFacadeSelection() throws Exception {
        final Path launcher = tempDir.resolve("launcher");
        Files.createDirectories(launcher.resolve("bin"));
        executable(launcher.resolve("bin/java"));
        executable(launcher.resolve("bin/javac"));
        final JdkInventory inventory = new JdkInventory();
        final JdkResolver.Candidate candidate = new JdkResolver.Candidate(
            "PATH",
            launcher,
            launcher.resolve("bin/java"),
            launcher.resolve("bin/javac"),
            true,
            "usable launcher; JDK home unresolved"
        );

        final List<JdkInventory.Entry> entries = inventory.inspect(new JdkResolver.Resolution(
            Optional.of(candidate),
            List.of(candidate)
        ));

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.facadeReady()).isFalse();
            assertThat(entry.featureVersion()).isEqualTo("unknown");
        });
        assertThat(inventory.select(entries, "25")).isEmpty();
    }

    private Path jdk(final String name, final String version, final String vendor) throws Exception {
        final Path home = tempDir.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        executable(home.resolve("bin/java"));
        executable(home.resolve("bin/javac"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\nIMPLEMENTOR=\"" + vendor + "\"\n");
        return home.toAbsolutePath().normalize();
    }

    private static JdkResolver.Candidate candidate(final String origin, final Path home) {
        return new JdkResolver.Candidate(
            origin,
            home,
            home.resolve("bin/java"),
            home.resolve("bin/javac"),
            true,
            "usable"
        );
    }

    private static void executable(final Path path) throws Exception {
        Files.createFile(path);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }
}
