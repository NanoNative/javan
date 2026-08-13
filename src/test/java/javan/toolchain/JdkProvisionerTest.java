package javan.toolchain;

import javan.util.ProcessRunner;
import javan.util.Files2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class JdkProvisionerTest {
    private static final String CHECKSUM = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    @TempDir
    private Path tempDir;

    @Test
    void downloadsVerifiesStagesPublishesAndRegistersTemurin() throws Exception {
        final Path home = tempDir.resolve("home");
        final JdkProvisioner provisioner = provisioner(home, new ArchiveRunner(CHECKSUM));

        final ToolchainMetadata installed = provisioner.provision("25");

        assertThat(installed.id()).isEqualTo("temurin-25-linux-x64");
        assertThat(installed.version()).isEqualTo("25.0.1+8");
        assertThat(installed.home()).isDirectory();
        assertThat(installed.javaExecutable()).isExecutable();
        assertThat(installed.javacExecutable()).isExecutable();
        assertThat(installed.checksum()).contains("sha256:" + CHECKSUM);
        assertThat(manager(home).installedToolchains()).containsExactly(installed);
        assertThat(home.resolve("jdks/temurin-25-linux-x64.javan-staging")).doesNotExist();
    }

    @Test
    void neverRegistersAnArchiveWhenItsChecksumDoesNotMatch() {
        final Path home = tempDir.resolve("home");
        final JdkProvisioner provisioner = provisioner(home, new ArchiveRunner("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));

        assertThatThrownBy(() -> provisioner.provision("temurin@25"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("SHA-256 mismatch");
        assertThat(home.resolve("toolchains")).doesNotExist();
    }

    @Test
    void rejectsUnimplementedAutomaticVendorsBeforeAnyTransfer() {
        final ArchiveRunner runner = new ArchiveRunner(CHECKSUM);

        assertThatThrownBy(() -> provisioner(tempDir.resolve("home"), runner).provision("corretto@25"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No verified JDK download provider for corretto");
        assertThat(runner.invocations()).isZero();
    }

    @Test
    void expandsMacPackagesWithoutRunningAnElevatedInstaller() throws Exception {
        final Path home = tempDir.resolve("home");
        final JdkProvisioner provisioner = new JdkProvisioner(
            home,
            new ManagedJdkStore(home, tempDir.resolve("temporary"), "Mac OS X", Map.of()),
            new ArchiveRunner(CHECKSUM, true),
            "Mac OS X",
            "aarch64"
        );

        final ToolchainMetadata installed = provisioner.provision("temurin@25");

        assertThat(installed.id()).isEqualTo("temurin-25-mac-aarch64");
        assertThat(installed.home()).isEqualTo(home.resolve("jdks/temurin-25-mac-aarch64/Contents/Home"));
        assertThat(installed.javaExecutable()).isExecutable();
        assertThat(installed.javacExecutable()).isExecutable();
        assertThat(home.resolve("jdks/temurin-25-mac-aarch64.javan-staging")).doesNotExist();
    }

    private JdkProvisioner provisioner(final Path home, final ProcessRunner runner) {
        return new JdkProvisioner(
            home,
            new ManagedJdkStore(home, tempDir.resolve("temporary"), "Linux", Map.of()),
            runner,
            "Linux",
            "x86_64"
        );
    }

    private static ToolchainManager manager(final Path home) {
        return new ToolchainManager(home, executable -> new ToolchainManager.ToolStatus(executable));
    }

    private static final class ArchiveRunner extends ProcessRunner {
        private final String computedChecksum;
        private final boolean macPackage;
        private int invocations;

        private ArchiveRunner(final String computedChecksum) {
            this(computedChecksum, false);
        }

        private ArchiveRunner(final String computedChecksum, final boolean macPackage) {
            this.computedChecksum = computedChecksum;
            this.macPackage = macPackage;
        }

        @Override
        public Result run(final Path workingDirectory, final List<String> command) throws IOException {
            invocations++;
            if ("curl".equals(command.getFirst()) && command.contains("-fsSL")) {
                return new Result(0, """
                    [{"binary":{"package":{"link":"https://example.invalid/temurin.%s","checksum":"%s","name":"temurin.%s"}}}]
                    """.formatted(macPackage ? "pkg" : "tar.gz", CHECKSUM, macPackage ? "pkg" : "tar.gz"), "");
            }
            if ("curl".equals(command.getFirst())) {
                Files.writeString(Path.of(command.get(command.indexOf("--output") + 1)), "archive");
                return new Result(0, "", "");
            }
            if ("sha256sum".equals(command.getFirst())) {
                return new Result(0, computedChecksum + "  " + command.get(1) + "\n", "");
            }
            if ("tar".equals(command.getFirst())) {
                final Path staging = Path.of(command.get(command.indexOf("-C") + 1));
                Files.createDirectories(staging.resolve("bin"));
                Files.writeString(staging.resolve("release"), "JAVA_VERSION=\"25.0.1+8\"\nIMPLEMENTOR=\"Eclipse Adoptium\"\n");
                executable(staging.resolve("bin/java"));
                executable(staging.resolve("bin/javac"));
                return new Result(0, "", "");
            }
            if ("pkgutil".equals(command.getFirst())) {
                assertThat(workingDirectory).isEqualTo(Path.of(command.get(3)).getParent());
                final Path home = Path.of(command.get(3))
                    .resolve("net.temurin.25.jdk.pkg/Payload/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home");
                Files.createDirectories(home.resolve("bin"));
                Files.writeString(home.resolve("release"), "JAVA_VERSION=\"25.0.1+8\"\nIMPLEMENTOR=\"Eclipse Adoptium\"\n");
                executable(home.resolve("bin/java"));
                executable(home.resolve("bin/javac"));
                return new Result(0, "", "");
            }
            if ("find".equals(command.getFirst())) {
                return new Result(
                    0,
                    Path.of(command.get(1))
                        .resolve("net.temurin.25.jdk.pkg/Payload/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/release")
                        + "\n",
                    ""
                );
            }
            if ("mv".equals(command.getFirst())) {
                Files.move(Path.of(command.get(1)), Path.of(command.get(2)));
                return new Result(0, "", "");
            }
            if ("rm".equals(command.getFirst())) {
                Files2.deleteRecursive(Path.of(command.get(2)));
                return new Result(0, "", "");
            }
            return new Result(127, "", "Unexpected test command: " + command);
        }

        private int invocations() {
            return invocations;
        }

        private static void executable(final Path file) throws IOException {
            Files.writeString(file, "#!/bin/sh\nexit 0\n");
            if (!file.toFile().setExecutable(true)) {
                throw new IOException("Could not mark test launcher executable: " + file);
            }
        }
    }
}
