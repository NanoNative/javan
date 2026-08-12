package javan.toolchain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class ManagedJdkStoreTest {
    @TempDir
    private Path tempDir;

    @Test
    void preparesTheLinuxMachineStoreFirstWhenItIsWritable() {
        final ManagedJdkStore.Location location = store("Linux", Map.of()).locations().getFirst();

        assertThat(location.scope()).isEqualTo("machine");
        assertThat(location.installRoot()).isEqualTo(Path.of("/usr/lib/jvm"));
        assertThat(location.downloadCache()).isEqualTo(Path.of("/var/cache/javan/downloads"));
    }

    @Test
    void fallsBackToTheUserStoreWhenTheMachineStoreIsUnavailable() {
        final ManagedJdkStore store = store("Linux", Map.of());
        final ManagedJdkStore.Location location = ManagedJdkStore.prepareForTesting(
            store.locations(),
            new RecordingPreparation(1)
        ).orElseThrow();

        assertThat(location.scope()).isEqualTo("user");
        assertThat(location.installRoot()).isEqualTo(tempDir.resolve("home/jdks").toAbsolutePath().normalize());
        assertThat(location.downloadCache()).isEqualTo(tempDir.resolve("home/cache/downloads").toAbsolutePath().normalize());
    }

    @Test
    void fallsBackToTemporaryStorageWhenMachineAndUserStoresAreUnavailable() {
        final ManagedJdkStore store = store("Linux", Map.of());
        final ManagedJdkStore.Location location = ManagedJdkStore.prepareForTesting(
            store.locations(),
            new RecordingPreparation(2)
        ).orElseThrow();

        assertThat(location.scope()).isEqualTo("temporary");
        assertThat(location.persistent()).isFalse();
        assertThat(location.installRoot()).isEqualTo(tempDir.resolve("temporary/javan/jdks").toAbsolutePath().normalize());
    }

    @Test
    void returnsEmptyWhenNoStoreCanBePrepared() {
        assertThat(ManagedJdkStore.prepareForTesting(store("Linux", Map.of()).locations(), new RecordingPreparation(3))).isEmpty();
    }

    @Test
    void macPolicyUsesTheStandardMachineJavaLocation() {
        final ManagedJdkStore.Location location = store("Mac OS X", Map.of()).locations().getFirst();

        assertThat(location.installRoot()).isEqualTo(Path.of("/Library/Java/JavaVirtualMachines"));
        assertThat(location.downloadCache()).isEqualTo(Path.of("/Library/Caches/Javan/downloads"));
    }

    @Test
    void windowsPolicyUsesConfiguredProgramFilesAndProgramData() {
        final Path programFiles = tempDir.resolve("Program Files");
        final Path programData = tempDir.resolve("ProgramData");
        final ManagedJdkStore.Location location = store(
            "Windows 11",
            Map.of("ProgramFiles", programFiles.toString(), "ProgramData", programData.toString())
        ).locations().getFirst();

        assertThat(location.installRoot()).isEqualTo(programFiles.resolve("Java").toAbsolutePath().normalize());
        assertThat(location.downloadCache()).isEqualTo(programData.resolve("Javan/cache/downloads").toAbsolutePath().normalize());
    }

    private ManagedJdkStore store(
        final String osName,
        final Map<String, String> environment
    ) {
        return new ManagedJdkStore(tempDir.resolve("home"), tempDir.resolve("temporary"), osName, environment);
    }

    private static final class RecordingPreparation implements ManagedJdkStore.DirectoryPreparation {
        private final int failedScopes;
        private final List<Path> attempted = new ArrayList<>();

        private RecordingPreparation(final int failedScopes) {
            this.failedScopes = failedScopes;
        }

        @Override
        public boolean prepare(final Path path) {
            attempted.add(path);
            return attempted.size() > failedScopes;
        }
    }
}
