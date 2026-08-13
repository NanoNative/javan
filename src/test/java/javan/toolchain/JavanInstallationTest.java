package javan.toolchain;

import javan.toolchain.facade.JdkFacadeStore;
import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
final class JavanInstallationTest {
    @TempDir
    private Path tempDir;

    @Test
    void installsOneStableJdkHomeThatFollowsTheCurrentFacade() throws Exception {
        final Path launcher = launcher();
        final Path first = backend("first", "Eclipse Adoptium", "25.0.1");
        final Path second = backend("second", "Amazon.com Inc.", "25.0.2");
        final JavanInstallation.Location location = new JavanInstallation.Location(
            "user",
            tempDir.resolve("public-jdk"),
            tempDir.resolve("facades")
        );
        final JavanInstallation installer = new JavanInstallation(List.of(location), new ProcessRunner(), "Linux");

        final JavanInstallation.Installation installed = installer.install(launcher, entry(first, "Eclipse Adoptium", "25.0.1"));
        new JdkFacadeStore(location.facadeRoot()).activate(entry(second, "Amazon.com Inc.", "25.0.2"));

        assertThat(installed.location()).isEqualTo(location);
        assertThat(location.publicHome()).isSymbolicLink();
        assertThat(location.publicHome().resolve("bin/java")).isExecutable();
        assertThat(location.publicHome().resolve("bin/javan")).isExecutable();
        assertThat(Files.readString(location.facadeRoot().resolve("javan"))).isEqualTo(Files.readString(launcher));
        assertThat(Files.readString(location.publicHome().resolve("javan-backend.txt"))).contains("backendHome=" + second);
        assertThat(first.resolve("bin/java")).isRegularFile();
        assertThat(second.resolve("bin/javac")).isRegularFile();
    }

    @Test
    void fallsBackWhenTheFirstLocationCannotBePrepared() throws Exception {
        final Path launcher = launcher();
        final Path backend = backend("backend", "Eclipse Adoptium", "25.0.1");
        final Path blockedParent = tempDir.resolve("blocked-parent");
        Files.writeString(blockedParent, "not a directory\n");
        final JavanInstallation.Location blocked = new JavanInstallation.Location(
            "machine",
            blockedParent.resolve("jdk"),
            blockedParent.resolve("facades")
        );
        final JavanInstallation.Location fallback = new JavanInstallation.Location(
            "user",
            tempDir.resolve("fallback-jdk"),
            tempDir.resolve("fallback-facades")
        );
        final JavanInstallation installer = new JavanInstallation(List.of(blocked, fallback), new ProcessRunner(), "Linux");

        final JavanInstallation.Installation installed = installer.install(launcher, entry(backend, "Eclipse Adoptium", "25.0.1"));

        assertThat(installed.location()).isEqualTo(fallback);
        assertThat(fallback.publicHome()).isSymbolicLink();
        assertThat(blockedParent).hasContent("not a directory\n");
    }

    @Test
    void fallsBackWhenThePublicHomeBelongsToAnotherInstallation() throws Exception {
        final Path launcher = launcher();
        final Path backend = backend("backend", "Eclipse Adoptium", "25.0.1");
        final JavanInstallation.Location occupied = new JavanInstallation.Location(
            "machine",
            tempDir.resolve("occupied-jdk"),
            tempDir.resolve("occupied-facades")
        );
        Files.createDirectories(occupied.publicHome());
        Files.writeString(occupied.publicHome().resolve("release"), "JAVA_VERSION=\"25\"\n");
        final JavanInstallation.Location fallback = new JavanInstallation.Location(
            "user",
            tempDir.resolve("fallback-jdk"),
            tempDir.resolve("fallback-facades")
        );
        final JavanInstallation installer = new JavanInstallation(List.of(occupied, fallback), new ProcessRunner(), "Linux");

        final JavanInstallation.Installation installed = installer.install(launcher, entry(backend, "Eclipse Adoptium", "25.0.1"));

        assertThat(installed.location()).isEqualTo(fallback);
        assertThat(occupied.publicHome()).isDirectory();
        assertThat(occupied.publicHome().resolve("release")).hasContent("JAVA_VERSION=\"25\"\n");
        assertThat(occupied.facadeRoot()).doesNotExist();
        assertThat(fallback.publicHome()).isSymbolicLink();
    }

    private Path launcher() throws Exception {
        final Path launcher = tempDir.resolve("source-javan");
        Files.writeString(launcher, "#!/bin/sh\nexit 0\n");
        assertThat(launcher.toFile().setExecutable(true)).isTrue();
        return launcher;
    }

    private Path backend(final String name, final String vendor, final String version) throws Exception {
        final Path home = tempDir.resolve(name);
        final Path bin = Files.createDirectories(home.resolve("bin"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\nIMPLEMENTOR=\"" + vendor + "\"\n");
        writeExecutable(bin.resolve("java"));
        writeExecutable(bin.resolve("javac"));
        writeExecutable(bin.resolve("jar"));
        Files.createDirectories(home.resolve("lib"));
        return home;
    }

    private static JdkInventory.Entry entry(final Path home, final String vendor, final String version) {
        return new JdkInventory.Entry(
            new JdkResolver.Candidate("test", home, home.resolve("bin/java"), home.resolve("bin/javac"), true, "usable"),
            vendor,
            version,
            "25",
            true
        );
    }

    private static void writeExecutable(final Path path) throws Exception {
        Files.writeString(path, "#!/bin/sh\nexit 0\n");
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }
}
