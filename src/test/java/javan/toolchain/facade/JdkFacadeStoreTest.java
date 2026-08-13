package javan.toolchain.facade;

import javan.toolchain.JdkInventory;
import javan.toolchain.JdkResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisabledOnOs(OS.WINDOWS)
final class JdkFacadeStoreTest {
    @TempDir
    private Path tempDir;

    @Test
    void switchesOnlyTheCurrentFacadeLinkWhenTheSelectedJdkChanges() throws Exception {
        final JdkFacadeStore store = new JdkFacadeStore(tempDir.resolve("facades"));

        final JdkFacadeStore.Activation temurin = store.activate(entry("temurin", "Eclipse Adoptium"));
        final JdkFacadeStore.Activation corretto = store.activate(entry("corretto", "Amazon.com Inc."));

        assertThat(temurin.current()).isSymbolicLink();
        assertThat(temurin.current().toRealPath()).isEqualTo(corretto.facade().home().toRealPath());
        assertThat(corretto.current().toRealPath()).isEqualTo(corretto.facade().home().toRealPath());
        assertThat(temurin.facade().home()).isDirectory();
        assertThat(corretto.facade().home()).isDirectory();
    }

    @Test
    void refusesToOverwriteANonLinkCurrentPath() throws Exception {
        final Path root = tempDir.resolve("facades");
        Files.createDirectories(root);
        Files.writeString(root.resolve("current"), "not a facade link\n");
        final JdkFacadeStore store = new JdkFacadeStore(root);

        assertThatThrownBy(() -> store.activate(entry("temurin", "Eclipse Adoptium")))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("current path is not a link");
    }

    @Test
    void refreshesMacJdkBundleMetadataWhenTheBackendChanges() throws Exception {
        final Path root = tempDir.resolve("facades");
        final Path publicHome = tempDir.resolve("javan.jdk/Contents/Home");
        Files.createDirectories(publicHome.getParent());
        Files.createDirectories(root);
        Files.createSymbolicLink(publicHome, root.resolve("current"));
        final JdkFacadeStore store = new JdkFacadeStore(root, new JdkFacadeGenerator(), new javan.util.ProcessRunner(), "Mac OS X");

        store.registerPublicHome(publicHome);
        store.activate(entry("temurin", "Eclipse Adoptium", "25.0.1"));
        final Path info = publicHome.getParent().resolve("Info.plist");

        assertThat(Files.readString(info))
            .contains("<key>CFBundleIdentifier</key><string>org.nanonative.javan.jdk</string>")
            .contains("<key>JVMPlatformVersion</key><string>25.0.1</string>")
            .contains("<key>JVMVendor</key><string>Javan</string>");

        store.activate(entry("corretto", "Amazon.com Inc.", "21.0.7"));

        assertThat(Files.readString(info)).contains("<key>JVMPlatformVersion</key><string>21.0.7</string>");
        assertThat(Files.readString(info)).doesNotContain("<key>JVMPlatformVersion</key><string>25.0.1</string>");
    }

    private JdkInventory.Entry entry(final String name, final String vendor) throws Exception {
        return entry(name, vendor, "25.0.1");
    }

    private JdkInventory.Entry entry(final String name, final String vendor, final String version) throws Exception {
        final Path home = tempDir.resolve(name);
        Files.createDirectories(home.resolve("bin"));
        executable(home.resolve("bin/java"));
        executable(home.resolve("bin/javac"));
        Files.writeString(home.resolve("release"), "JAVA_VERSION=\"" + version + "\"\nIMPLEMENTOR=\"" + vendor + "\"\n");
        final JdkResolver.Candidate candidate = new JdkResolver.Candidate(
            "platform",
            home,
            home.resolve("bin/java"),
            home.resolve("bin/javac"),
            true,
            "usable"
        );
        return new JdkInventory.Entry(candidate, vendor, version, version.substring(0, version.indexOf('.')), true);
    }

    private static void executable(final Path path) throws Exception {
        Files.createFile(path);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }
}
