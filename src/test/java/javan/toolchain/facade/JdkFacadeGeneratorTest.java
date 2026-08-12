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
    void createsALinkedUnixFacadeWithoutCopyingTheBackendJdk() throws Exception {
        final Path backend = backendJdk();
        final Path facade = tempDir.resolve("javan-jdk");

        final JdkFacadeGenerator.Result result = new JdkFacadeGenerator().generate(facade, candidate(backend));

        assertThat(result.home()).isEqualTo(facade);
        assertThat(facade.resolve("release")).hasContent("JAVA_VERSION=\"25\"\n");
        assertThat(Files.readString(facade.resolve("bin/javac"))).contains("exec \"${JAVAN_BIN:-javan}\" javac \"$@\"");
        assertThat(Files.readString(facade.resolve("bin/java"))).contains("exec '" + backend.resolve("bin/java") + "' \"$@\"");
        assertThat(facade.resolve("lib")).isSymbolicLink();
        assertThat(facade.resolve("jmods")).isSymbolicLink();
        assertThat(facade.resolve("include")).isSymbolicLink();
        assertThat(facade.resolve("conf")).isSymbolicLink();
        assertThat(Files.readString(facade.resolve("javan-backend.txt"))).contains("backendHome=" + backend);
    }

    private Path backendJdk() throws Exception {
        final Path backend = tempDir.resolve("backend");
        Files.createDirectories(backend.resolve("bin"));
        Files.writeString(backend.resolve("bin/java"), "backend java\n");
        Files.writeString(backend.resolve("bin/javac"), "backend javac\n");
        Files.writeString(backend.resolve("release"), "JAVA_VERSION=\"25\"\n");
        Files.createDirectories(backend.resolve("lib"));
        Files.createDirectories(backend.resolve("jmods"));
        Files.createDirectories(backend.resolve("include"));
        Files.createDirectories(backend.resolve("conf"));
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
