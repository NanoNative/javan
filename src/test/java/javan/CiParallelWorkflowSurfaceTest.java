package javan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class CiParallelWorkflowSurfaceTest {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path RELEASE = Path.of(".github/workflows/release.yml");
    private static final Path PLATFORM_PROOF = Path.of(".github/workflows/platform-proof.yml");
    private static final Path PUBLISH_CENTRAL = Path.of(".github/workflows/publish-central.yml");

    @Test
    void pullRequestAndMainWorkflowsDelegateToOneCommonBuild() throws Exception {
        assertThat(Files.readString(BUILD_PR))
            .contains("pull_request:")
            .contains("uses: ./.github/workflows/build-common.yml");
        assertThat(Files.readString(BUILD_MERGE))
            .contains("push:")
            .contains("branches:")
            .contains("- main")
            .contains("uses: ./.github/workflows/build-common.yml");
        assertThat(Files.readString(RELEASE))
            .contains("workflow_dispatch:")
            .contains("uses: ./.github/workflows/build-common.yml");
    }

    @Test
    void commonBuildSplitsLongNativeProofsIntoIndependentJobs() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("verify-core:")
            .contains("verify-cli-integration:")
            .contains("native-acceptance:")
            .contains("native-sanitizer:")
            .contains("native-package-self-host:")
            .contains("platform-smoke:")
            .contains("windows-runtime-smoke:")
            .doesNotContain("native-smoke:");
    }

    @Test
    void commonBuildRunsAllRequestedOperatingSystemArchitecturesInParallel() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("name: Platform smoke · ${{ matrix.target }}")
            .contains("max-parallel: 6")
            .contains("target: linux-x64", "os: ubuntu-24.04")
            .contains("target: linux-arm64", "os: ubuntu-24.04-arm")
            .contains("target: macos-x64", "os: macos-15-intel")
            .contains("target: macos-arm64", "os: macos-15")
            .contains("target: windows-x64", "os: windows-2025")
            .contains("target: windows-arm64", "os: windows-11-arm")
            .contains("enabled: true")
            .contains("uses: ./.github/workflows/platform-proof.yml")
            .contains("enabled: ${{ matrix.enabled }}");
        assertThat(Files.readString(PLATFORM_PROOF)).contains("if: inputs.enabled");
    }

    @Test
    void nativeArtifactsKeepEveryPlatformRowWhenSlowOrUnsupportedTargetsAreDisabled() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("target: linux-x64", "target: linux-aarch64")
            .contains("target: macos-x64", "target: macos-aarch64")
            .contains("target: windows-x64", "target: windows-aarch64")
            .contains("historical slower architecture lane")
            .contains("local self-host proof exceeded the 45-minute job projection")
            .contains("native linker and process runtime are incomplete")
            .contains("proof: package-self-host")
            .contains("enabled: ${{ matrix.enabled }}");
    }

    @Test
    void cliSuitesUseSixIndependentDurationBalancedShards() throws Exception {
        assertThat(Files.readString(BUILD_COMMON))
            .contains("shard: cli-general")
            .contains("shard: cli-jdk-semantics")
            .contains("shard: cli-thread-runtime")
            .contains("shard: cli-runtime-translation")
            .contains("shard: cli-packaging")
            .contains("shard: cli-external-probes")
            .contains("max-parallel: 6");
    }

    @Test
    void mavenCentralPublishingRemainsPresentAndHardDisabled() throws Exception {
        assertThat(PUBLISH_CENTRAL).isRegularFile();
        assertThat(Files.readString(PUBLISH_CENTRAL))
            .contains("name: CD · Publish Maven Central (disabled)")
            .contains("if: ${{ false }}")
            .contains("GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}")
            .contains("GPG_SIGNING_KEY: ${{ secrets.GPG_SIGNING_KEY }}")
            .contains("OSSH_PASS: ${{ secrets.OSSH_PASS }}")
            .contains("OSSH_USER: ${{ secrets.OSSH_USER }}")
            .contains("name: build-workspace")
            .contains("gpg-private-key: ${{ secrets.GPG_SIGNING_KEY }}")
            .contains("-Dproject.build.outputTimestamp=\"$BUILD_OUTPUT_TIMESTAMP\"")
            .contains("-DskipTests deploy");
        assertThat(Files.readString(BUILD_MERGE))
            .contains("publish-central:")
            .contains("if: ${{ false }}")
            .contains("uses: ./.github/workflows/publish-central.yml");
    }
}
