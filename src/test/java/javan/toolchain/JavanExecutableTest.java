package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
final class JavanExecutableTest {
    @TempDir
    private Path tempDir;

    @Test
    void resolvesTheNativeExecutableProperty() throws Exception {
        final Path launcher = Files.createFile(tempDir.resolve("javan"));
        final String previous = System.getProperty(JavanExecutable.PROPERTY);
        System.setProperty(JavanExecutable.PROPERTY, launcher.toString());
        try {
            assertThat(JavanExecutable.resolve()).contains(launcher.toAbsolutePath().normalize());
        } finally {
            restore(previous);
        }
    }

    @Test
    void ignoresAPropertyThatDoesNotPointToAFile() {
        final String previous = System.getProperty(JavanExecutable.PROPERTY);
        System.setProperty(JavanExecutable.PROPERTY, tempDir.resolve("missing").toString());
        try {
            assertThat(JavanExecutable.resolve()).isEmpty();
        } finally {
            restore(previous);
        }
    }

    private static void restore(final String value) {
        if (value == null) {
            System.clearProperty(JavanExecutable.PROPERTY);
            return;
        }
        System.setProperty(JavanExecutable.PROPERTY, value);
    }
}
