package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
final class JavanHomeCurrentProcessTest {
    @Test
    void resolvePrefersCurrentProcessProperty() {
        final String previous = System.getProperty(JavanHome.PROPERTY);
        System.setProperty(JavanHome.PROPERTY, " build/javan-home ");
        try {
            assertThat(JavanHome.resolve()).isEqualTo(Path.of("build/javan-home").toAbsolutePath().normalize());
        } finally {
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(final String value) {
        if (value == null) {
            System.clearProperty(JavanHome.PROPERTY);
            return;
        }
        System.setProperty(JavanHome.PROPERTY, value);
    }
}
