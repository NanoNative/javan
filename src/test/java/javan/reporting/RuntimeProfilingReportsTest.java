package javan.reporting;

import javan.build.RuntimeFeatureSelection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class RuntimeProfilingReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeReportsDisabledWhenProfilingIsOff() throws Exception {
        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection.Settings(
            Optional.empty(),
            List.of(),
            "system-linked",
            "balanced",
            false,
            false
        );

        new RuntimeProfilingReports().write(tempDir, settings);

        assertThat(Files.readString(tempDir.resolve("runtime-profiling.json"))).contains(
            "\"status\": \"disabled\"",
            "\"requested\": false",
            "\"enabled\": false",
            "\"collectionState\": \"disabled\"",
            "\"reason\": \"Runtime profiling is off in configuration.\"",
            "\"disabledProfilingModules\": []"
        );
        assertThat(Files.readString(tempDir.resolve("runtime-profiling.md"))).contains(
            "- status: `disabled`",
            "- requested: `false`",
            "- enabled: `false`",
            "- disabledProfilingModules: `-`"
        );
    }

    @Test
    void writeReportsDisabledWhenProfilingModulesAreBlocked() throws Exception {
        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection.Settings(
            Optional.of(tempDir.resolve("javan.toml")),
            List.of("process", "live-profiling", "thread-profiling"),
            "system-linked",
            "balanced",
            false,
            true
        );

        new RuntimeProfilingReports().write(tempDir, settings);

        assertThat(Files.readString(tempDir.resolve("runtime-profiling.json"))).contains(
            "\"status\": \"disabled\"",
            "\"requested\": true",
            "\"enabled\": false",
            "\"collectionState\": \"disabled-by-module\"",
            "\"disabledProfilingModules\": [\"live-profiling\", \"thread-profiling\"]"
        );
        assertThat(Files.readString(tempDir.resolve("runtime-profiling.md"))).contains(
            "- status: `disabled`",
            "- requested: `true`",
            "- enabled: `false`",
            "- disabledProfilingModules: `live-profiling, thread-profiling`",
            "disabled runtime modules block profiling hooks: live-profiling, thread-profiling."
        );
    }

    @Test
    void writeReportsReadyWhenProfilingIsRequestedAndUnblocked() throws Exception {
        final RuntimeFeatureSelection.Settings settings = new RuntimeFeatureSelection.Settings(
            Optional.of(tempDir.resolve("javan.toml")),
            List.of("process"),
            "system-linked",
            "balanced",
            false,
            true
        );

        new RuntimeProfilingReports().write(tempDir, settings);

        assertThat(Files.readString(tempDir.resolve("runtime-profiling.json"))).contains(
            "\"status\": \"ready\"",
            "\"requested\": true",
            "\"enabled\": true",
            "\"collectionState\": \"linked-not-run\"",
            "\"reason\": \"Runtime profiling is linked and will collect counters when the native binary runs through a profiling-enabled launch path.\"",
            "\"disabledProfilingModules\": []"
        );
        assertThat(Files.readString(tempDir.resolve("runtime-profiling.md"))).contains(
            "- status: `ready`",
            "- requested: `true`",
            "- enabled: `true`",
            "- disabledProfilingModules: `-`"
        );
    }
}
