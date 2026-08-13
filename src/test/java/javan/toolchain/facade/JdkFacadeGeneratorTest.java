package javan.toolchain.facade;

import javan.toolchain.JdkResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
final class JdkFacadeGeneratorTest {
    @TempDir
    private Path tempDir;

    @Test
    void createsACompleteDynamicUnixFacadeWithoutCopyingTheBackendJdk() throws Exception {
        final Path backend = backendJdk();
        final Path facade = tempDir.resolve("javan-jdk");

        final JdkFacadeGenerator.Result result = new JdkFacadeGenerator().generate(facade, candidate(backend));

        assertThat(result.home()).isEqualTo(facade);
        assertThat(facade.resolve("release")).isSymbolicLink();
        assertThat(Files.readString(facade.resolve("bin/javac"))).contains("--jn-facade-javac");
        assertThat(Files.readString(facade.resolve("bin/java"))).contains("--jn-facade-java");
        assertThat(facade.resolve("bin/jar")).isSymbolicLink();
        assertThat(facade.resolve("lib")).isSymbolicLink();
        assertThat(facade.resolve("jmods")).isSymbolicLink();
        assertThat(facade.resolve("include")).isSymbolicLink();
        assertThat(facade.resolve("conf")).isSymbolicLink();
        assertThat(facade.resolve("legal")).isSymbolicLink();
        assertThat(facade.resolve("GRAALVM-README.md")).isSymbolicLink();
        assertThat(Files.readString(facade.resolve("javan-backend.txt"))).contains("backendHome=" + backend);
    }

    @Test
    void usesTheNativeJavanLauncherWhenTheFacadeStoreProvidesOne() throws Exception {
        final Path backend = backendJdk();
        final Path root = Files.createDirectories(tempDir.resolve("facades"));
        final Path launcher = root.resolve("javan");
        Files.writeString(launcher, "#!/bin/sh\nexit 0\n");
        assertThat(launcher.toFile().setExecutable(true)).isTrue();

        new JdkFacadeGenerator().generate(tempDir.resolve("native-javan-jdk"), candidate(backend), root);

        assertThat(tempDir.resolve("native-javan-jdk/bin/java")).hasContent(Files.readString(launcher));
        assertThat(tempDir.resolve("native-javan-jdk/bin/javac")).hasContent(Files.readString(launcher));
        assertThat(tempDir.resolve("native-javan-jdk/bin/javan")).hasContent(Files.readString(launcher));
    }

    @Test
    void refreshesOnlyTheOwnedLaunchersWhenJavanIsUpdated() throws Exception {
        final Path backend = backendJdk();
        final Path root = Files.createDirectories(tempDir.resolve("facades"));
        final Path first = root.resolve("javan");
        Files.writeString(first, "#!/bin/sh\necho first\n");
        assertThat(first.toFile().setExecutable(true)).isTrue();
        final Path facade = tempDir.resolve("updated-javan-jdk");
        final JdkFacadeGenerator generator = new JdkFacadeGenerator();
        generator.generate(facade, candidate(backend), root);
        Files.writeString(first, "#!/bin/sh\necho second\n");

        generator.refreshLaunchers(facade, candidate(backend), root);

        assertThat(facade.resolve("bin/java")).hasContent("#!/bin/sh\necho second\n");
        assertThat(facade.resolve("bin/javac")).hasContent("#!/bin/sh\necho second\n");
        assertThat(facade.resolve("bin/javan")).hasContent("#!/bin/sh\necho second\n");
        assertThat(facade.resolve("bin/jar")).isSymbolicLink();
    }

    private Path backendJdk() throws Exception {
        final Path backend = tempDir.resolve("backend");
        Files.createDirectories(backend.resolve("bin"));
        Files.writeString(backend.resolve("bin/java"), "backend java\n");
        Files.writeString(backend.resolve("bin/javac"), "backend javac\n");
        Files.writeString(backend.resolve("bin/jar"), "backend jar\n");
        Files.writeString(backend.resolve("release"), "JAVA_VERSION=\"25\"\n");
        Files.createDirectories(backend.resolve("lib"));
        Files.createDirectories(backend.resolve("jmods"));
        Files.createDirectories(backend.resolve("include"));
        Files.createDirectories(backend.resolve("conf"));
        Files.createDirectories(backend.resolve("legal"));
        Files.writeString(backend.resolve("GRAALVM-README.md"), "backend extra\n");
        return backend;
    }

    private static JdkResolver.Candidate candidate(final Path backend) {
        return new JdkResolver.Candidate(
            "test",
            backend,
            backend.resolve("bin/java"),
            backend.resolve("bin/javac"),
            true,
            "usable"
        );
    }
}
