package javan.build;

import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class BuildInvokerTest {
    @Test
    void gradleJava25CompatibilityFailureIncludesActionableFix() {
        final String message = BuildInvoker.gradleFailureMessage(new ProcessRunner.Result(
            1,
            "",
            "BUG! exception in phase 'semantic analysis' Unsupported class file major version 69"
        ));

        assertThat(message)
            .contains("Gradle requires 9.1.0 or newer")
            .contains("add or update a Gradle wrapper");
    }

    @Test
    void genericGradleFailureIncludesOriginalOutput() {
        final String message = BuildInvoker.gradleFailureMessage(new ProcessRunner.Result(1, "out", "err"));

        assertThat(message).contains("Gradle classes failed", "errout");
    }
}
