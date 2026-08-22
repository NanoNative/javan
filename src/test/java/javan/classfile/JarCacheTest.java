package javan.classfile;

import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class JarCacheTest {
    @Test
    void extractReusesContentAddressedJar(@TempDir final Path tempDir) throws Exception {
        final Path jar = jar(tempDir.resolve("dependency.jar"), "first.txt", "first");
        final CountingProcessRunner runner = new CountingProcessRunner();
        final JarCache cache = new JarCache(runner);

        final Path first = cache.extract(jar, tempDir.resolve("output"));
        final Path second = cache.extract(jar, tempDir.resolve("output"));

        assertThat(second).isEqualTo(first);
        assertThat(first.resolve("first.txt")).hasContent("first");
        assertThat(runner.calls).isOne();
    }

    @Test
    void extractUsesANewCacheWhenJarContentChanges(@TempDir final Path tempDir) throws Exception {
        final Path jar = jar(tempDir.resolve("dependency.jar"), "first.txt", "first");
        final CountingProcessRunner runner = new CountingProcessRunner();
        final JarCache cache = new JarCache(runner);
        final Path first = cache.extract(jar, tempDir.resolve("output"));
        jar(jar, "second.txt", "second");

        final Path second = cache.extract(jar, tempDir.resolve("output"));

        assertThat(second).isNotEqualTo(first);
        assertThat(second.resolve("second.txt")).hasContent("second");
        assertThat(runner.calls).isEqualTo(2);
    }

    private static Path jar(final Path path, final String name, final String value) throws Exception {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry(name));
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private static final class CountingProcessRunner extends ProcessRunner {
        private int calls;

        @Override
        public Result run(final Path workingDirectory, final List<String> command) throws java.io.IOException, InterruptedException {
            calls++;
            return super.run(workingDirectory, command);
        }
    }
}
