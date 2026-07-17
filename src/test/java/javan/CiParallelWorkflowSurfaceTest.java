package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class CiParallelWorkflowSurfaceTest {
    private static final Path CI_WORKFLOW = Path.of(".github/workflows/ci.yml");

    @Test
    void ciWorkflowSplitsVerifyAndNativeSmokeIntoParallelJobs() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("verify:")
            .contains("native-smoke:")
            .contains("name: verify (${{ matrix.target }})")
            .contains("name: native-smoke (${{ matrix.target }})")
            .contains("Compile test fixtures")
            .contains("Verify acceptance suite");
    }

    @Test
    void releaseWaitsForNativeSmokeBeforePackaging() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("needs:")
            .contains("- verify")
            .contains("- native-smoke")
            .contains("- windows-runtime-smoke");
    }

    @Test
    void nativeSmokeKeepsMacOsX64Disabled() throws Exception {
        assertThat(Files.readString(CI_WORKFLOW))
            .contains("target: linux-x64")
            .contains("target: linux-aarch64")
            .contains("target: macos-aarch64")
            .doesNotContain("target: macos-x64")
            .doesNotContain("os: macos-13")
            .doesNotContain("os: macos-14");
    }
}
