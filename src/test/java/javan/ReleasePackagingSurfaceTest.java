package javan;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class ReleasePackagingSurfaceTest extends CliIntegrationSupport {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path NATIVE_PROOF = Path.of(".github/workflows/native-proof.yml");
    private static final Path RELEASE_WORKFLOW = Path.of(".github/workflows/release.yml");
    private static final Path CONTAINER_WORKFLOW = Path.of(".github/workflows/container-images.yml");
    private static final Path VERIFY_RELEASE = Path.of(".github/scripts/verify-release.sh");
    private static final Path VERIFY_CI_PACKAGE_SMOKE = Path.of(".github/scripts/verify-ci-package-smoke.sh");
    private static final Path VERSION_TEMPLATE = Path.of("src/main/version/javan/cli/Version.java");
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().normalize();

    @Test
    void mavenOwnsDateVersionAndGeneratesTheCliConstant() throws Exception {
        assertThat(Files.readString(Path.of("pom.xml")))
            .contains("<groupId>org.nanonative</groupId>")
            .contains("<description>Native Java compiler.</description>")
            .contains("<!-- ########## PROD ########## -->")
            .contains("<!-- ########## TEST ########## -->")
            .contains("<!-- ########## BUILD ########## -->")
            .contains("<!-- ########## RELEASE ########## -->")
            .contains("<version>1.0.0</version>")
            .contains("<project.build.outputTimestamp>${git.commit.time}</project.build.outputTimestamp>")
            .contains("<id>git-commit-time</id>")
            .contains("<id>generate-version-source</id>")
            .contains("<id>add-generated-version-source</id>")
            .doesNotContain("<revision>", "flatten-maven-plugin", "versions-maven-plugin");
        assertThat(Files.readString(VERSION_TEMPLATE))
            .contains("private static final String VERSION = \"${project.version}\"");
        assertThat(Path.of(".github/scripts/set-version.sh")).doesNotExist();
        assertThat(List.of(Path.of("mvnw"), Path.of("mvnw.cmd"), Path.of(".mvn/wrapper/maven-wrapper.properties")))
            .allMatch(Files::isRegularFile);
        assertThat(Files.readString(BUILD_COMMON))
            .contains("versions:set -DnewVersion=\"$RELEASE_VERSION\" -DgenerateBackupPoms=false")
            .doesNotContain("set-version.sh");
    }

    @Test
    void homebrewFormulaGenerationRendersPinnedLinuxUrlsAndChecksums() throws Exception {
        final Path releaseDir = tempDir.resolve("release");
        Files.createDirectories(releaseDir);
        final String version = "2026.7.16";
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-x64.tar.gz", "linux-x64");
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-aarch64.tar.gz", "linux-aarch64");
        final Path formula = releaseDir.resolve("javan.rb");

        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(".github/scripts/generate-homebrew-formula.sh").toString(), version, "NanoNative/javan"),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_RELEASE_DIR", releaseDir.toString(),
                "JAVAN_HOMEBREW_FORMULA_OUTPUT", formula.toString()
            )
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(formula).isRegularFile();
        final String content = Files.readString(formula);
        assertThat(content)
            .contains("class Javan < Formula")
            .contains("homepage \"https://github.com/NanoNative/javan\"")
            .contains("version \"" + version + "\"")
            .contains("javan-" + version + "-linux-x64.tar.gz")
            .contains("javan-" + version + "-linux-aarch64.tar.gz")
            .doesNotContain("macos-aarch64")
            .contains("assert_match version.to_s, shell_output(\"#{bin}/javan --version\")")
            .contains("assert_match \"javan home:\", shell_output(\"#{bin}/javan doctor\")");
    }

    @Test
    void homebrewFormulaGenerationFailsWhenChecksumIsMissing() throws Exception {
        final Path releaseDir = tempDir.resolve("release");
        Files.createDirectories(releaseDir);
        final String version = "2026.7.16";
        Files.writeString(releaseDir.resolve("javan-" + version + "-linux-x64.tar.gz"), "linux-x64", StandardCharsets.UTF_8);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(".github/scripts/generate-homebrew-formula.sh").toString(), version, "NanoNative/javan"),
            Duration.ofSeconds(20),
            Map.of("JAVAN_RELEASE_DIR", releaseDir.toString())
        );

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Missing checksum file:");
    }

    @Test
    void homebrewFormulaGenerationRejectsLeadingZeroVersionIdentifiers() throws Exception {
        final ProcessResult run = process(
            REPO_ROOT,
            List.of(
                "sh",
                REPO_ROOT.resolve(".github/scripts/generate-homebrew-formula.sh").toString(),
                "2026.07.16",
                "NanoNative/javan"
            ),
            Duration.ofSeconds(20),
            Map.of("JAVAN_RELEASE_DIR", tempDir.toString())
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("without leading zeroes");
    }

    @Test
    void homebrewFormulaVerificationChecksGeneratedFormulaAgainstReleaseChecksums() throws Exception {
        final Path releaseDir = tempDir.resolve("release");
        Files.createDirectories(releaseDir);
        final String version = "2026.7.16";
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-x64.tar.gz", "linux-x64");
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-aarch64.tar.gz", "linux-aarch64");
        final Path formula = releaseDir.resolve("javan.rb");

        final ProcessResult generate = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(".github/scripts/generate-homebrew-formula.sh").toString(), version, "NanoNative/javan"),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_RELEASE_DIR", releaseDir.toString(),
                "JAVAN_HOMEBREW_FORMULA_OUTPUT", formula.toString()
            )
        );
        assertThat(generate.exitCode()).isZero();

        final ProcessResult verify = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(".github/scripts/verify-homebrew-formula.sh").toString(), formula.toString(), version, "NanoNative/javan"),
            Duration.ofSeconds(20),
            Map.of("JAVAN_RELEASE_DIR", releaseDir.toString())
        );

        assertThat(verify.exitCode()).isZero();
        assertThat(verify.stderr()).isEmpty();
        assertThat(verify.stdout()).contains("Verified Homebrew formula");
    }

    @Test
    void releaseWorkflowGeneratesHomebrewFormulaBeforePublishing() throws Exception {
        final String releaseWorkflow = Files.readString(RELEASE_WORKFLOW);

        assertThat(releaseWorkflow)
            .contains("- name: \"🍺 Homebrew")
            .contains(".github/scripts/generate-homebrew-formula.sh")
            .contains("- name: \"🍺 Formula")
            .contains(".github/scripts/verify-homebrew-formula.sh");
    }

    @Test
    void commonWorkflowKeepsLinuxNativeProofTargets() throws Exception {
        final String commonWorkflow = Files.readString(BUILD_COMMON);

        assertThat(commonWorkflow)
            .contains("target: linux-x64")
            .contains("target: linux-aarch64")
            .contains("native-acceptance:")
            .contains("native-sanitizer:")
            .contains("native-package-self-host:");
    }

    @Test
    void ciWorkflowKeepsPackagedSelfHostSmokeStep() throws Exception {
        final String nativeProof = Files.readString(NATIVE_PROOF);

        assertThat(nativeProof)
            .contains("- name: \"📦 Package")
            .contains("sh .github/scripts/verify-ci-package-smoke.sh");
    }

    @Test
    void workflowsQueueByRefWithoutCancelingRuns() throws Exception {
        final String pullRequestWorkflow = Files.readString(BUILD_PR);
        final String mainWorkflow = Files.readString(BUILD_MERGE);
        final String releaseWorkflow = Files.readString(RELEASE_WORKFLOW);
        final String containerWorkflow = Files.readString(CONTAINER_WORKFLOW);

        assertThat(pullRequestWorkflow)
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
        assertThat(mainWorkflow)
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
        assertThat(releaseWorkflow)
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
        assertThat(containerWorkflow)
            .contains("concurrency:")
            .contains("cancel-in-progress: false");
    }

    @Test
    void ciWorkflowLeavesCoverageToMavenWithoutPartialSummaryOrGate() throws Exception {
        final String ciWorkflow = Files.readString(BUILD_COMMON);

        assertThat(ciWorkflow)
            .doesNotContain(
                "JAVAN_COVERAGE_SOFT_TARGET",
                "Soft target:",
                "::warning::JaCoCo",
                "GITHUB_STEP_SUMMARY",
                "- name: \"📊 Coverage",
                "Upload coverage artifact",
                "minimum>0.09</minimum>"
            );
    }

    @Test
    void ciWorkflowKeepsWindowsRuntimeSmokeGate() throws Exception {
        final String ciWorkflow = Files.readString(BUILD_COMMON);
        final String windows = ciWorkflow.substring(
            ciWorkflow.indexOf("  windows-runtime-smoke:"),
            ciWorkflow.indexOf("  prepare-publication:")
        );

        assertThat(windows)
            .contains("windows-runtime-smoke:")
            .contains(
                "name: ${{ matrix.label }}",
                "runs-on: ${{ matrix.os }}",
                "os: windows-2025",
                "label: runtime_win_x64"
            )
            .contains("./mvnw.cmd -q -Dgroups=windows-compatibility test")
            .doesNotContain("worker_index:", "worker_count:", "test-selector:", "RuntimeFilesTest#");
    }

    @Test
    void mainWorkflowKeepsReleaseBlockedOnTheCompleteCommonBuild() throws Exception {
        final String commonWorkflow = Files.readString(BUILD_COMMON);
        final String mainWorkflow = Files.readString(BUILD_MERGE);

        assertThat(commonWorkflow)
            .contains("verify-core:")
            .contains("cli:")
            .contains("native-acceptance:")
            .contains("native-sanitizer:")
            .contains("native-package-self-host:")
            .contains("windows-runtime-smoke:");
        assertThat(mainWorkflow)
            .contains("needs:")
            .contains("- verify")
            .contains("uses: ./.github/workflows/publish-central.yml")
            .doesNotContain("uses: ./.github/workflows/release.yml");
    }

    @Test
    void mainPushBuildsDateSnapshotArtifactsOnceWithoutInvokingManualRelease() throws Exception {
        final String ciWorkflow = Files.readString(BUILD_MERGE);

        assertThat(ciWorkflow)
            .contains("prepare_publication: true")
            .contains("snapshot: true")
            .doesNotContain("release_strategy")
            .doesNotContain("uses: ./.github/workflows/release.yml");
        assertThat(Files.readString(BUILD_COMMON))
            .contains("version=$version-SNAPSHOT")
            .contains("prepare-publication:")
            .contains("name: build-workspace");
        assertThat(Files.readString(RELEASE_WORKFLOW))
            .doesNotContain("snapshot: true", "git add pom.xml", "javan/cli/Version.java");
    }

    @Test
    void commonBuildKeepsLinuxAndMacOsPackagesWhileRetainingDisabledWindowsRows() throws Exception {
        final String commonWorkflow = Files.readString(BUILD_COMMON);

        assertThat(commonWorkflow)
            .contains("target: linux-x64", "target: linux-aarch64")
            .contains("target: macos-x64", "target: macos-aarch64")
            .contains("target: windows-x64", "target: windows-aarch64")
            .contains("enabled: false");
    }

    @Test
    void releaseWorkflowVerifiesNativePackagesBeforeUpload() throws Exception {
        final String nativeProof = Files.readString(NATIVE_PROOF);

        assertThat(nativeProof)
            .contains("- name: \"📦 Package")
            .contains("sh .github/scripts/verify-ci-package-smoke.sh")
            .contains("- name: \"📤 Upload");
    }

    @Test
    void releaseWorkflowDownloadsPackagesBeforePublishingAtVerifiedCommit() throws Exception {
        final String releaseWorkflow = Files.readString(RELEASE_WORKFLOW);

        assertThat(releaseWorkflow)
            .contains("- name: \"📥 Download")
            .contains("uses: actions/download-artifact@")
            .contains("pattern: javan-*")
            .contains("merge-multiple: true")
            .contains("- name: \"🚀 Publish")
            .contains("TARGET_SHA: ${{ needs.build.outputs.commit_sha }}")
            .doesNotContain("git push", "git tag", "BOT_TOKEN");
    }

    @Test
    void verifyReleaseScriptKeepsPackageVerificationBeforePackagedSmoke() throws Exception {
        final String script = Files.readString(VERIFY_RELEASE);

        assertThat(script)
            .contains("./mvnw -q \\")
            .contains("clean verify")
            .contains("scripts/build.sh")
            .contains(".github/scripts/package-release.sh")
            .contains(".github/scripts/verify-package.sh");
    }

    @Test
    void buildScriptAllowsReuseTargetWithoutPackagedJar() throws Exception {
        final String script = Files.readString(Path.of("scripts/build.sh"));

        assertThat(script)
            .contains("VERSION=$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout | tail -n 1)")
            .contains("if [ \"$REUSE_TARGET\" = \"true\" ]; then")
            .contains("Missing target/classes/javan/Main.class for JAVAN_BUILD_REUSE_TARGET=true.")
            .contains("if [ \"$REUSE_TARGET\" != \"true\" ] && [ ! -f \"$JAR\" ]; then")
            .contains("No packaged javan jar found in target/.");
    }

    @Test
    void buildScriptSelectsSecondOrThirdBootstrapGeneration() throws Exception {
        final String script = Files.readString(Path.of("scripts/build.sh"));

        assertThat(script)
            .contains("GENERATION=${JAVAN_BOOTSTRAP_GENERATION:-3}")
            .contains("2|3)")
            .contains("javan-bootstrap-rebuilt")
            .contains("javan-bootstrap-verified")
            .contains("if [ \"$GENERATION\" = \"3\" ]; then")
            .contains("javan_timing_run bootstrap_jvm")
            .contains("javan_timing_run bootstrap_gen2")
            .contains("javan_timing_run bootstrap_gen3")
            .contains("cp \"$BUILT\" \"$OUTPUT\"");
    }

    @Test
    void packageProofPublishesComparableNativeTimingReports() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);
        final String workflow = Files.readString(NATIVE_PROOF);

        assertThat(script)
            .contains("javan_timing_run package_verify")
            .contains("javan_timing_record package_self_check")
            .contains("javan_timing_run package_jar")
            .contains("javan_timing_run package_native")
            .contains("javan_timing_record package_sanitizer")
            .contains("javan_timing_write_reports")
            .contains("javan-$PACKAGE_TARGET-timings.json")
            .contains("javan-$PACKAGE_TARGET-timings.md");
        assertThat(Files.readString(Path.of(".github/scripts/sanitizer-self-host-smoke.sh")))
            .contains("javan_timing_record sanitizer_compile");
        assertThat(workflow)
            .contains("name: timings-${{ inputs.target }}-gen${{ inputs.bootstrap_generation }}")
            .contains("retention-days: 7")
            .contains("dist/release/javan-${{ inputs.target }}-timings.json")
            .contains("dist/release/javan-${{ inputs.target }}-timings.md");
    }

    @Test
    void timingReporterWritesMachineAndHumanReadablePhaseComparisons() throws Exception {
        final Path runner = tempDir.resolve("timing-report-test.sh");
        final Path log = tempDir.resolve("timings.tsv");
        final Path json = tempDir.resolve("timings.json");
        final Path markdown = tempDir.resolve("timings.md");
        Files.writeString(runner, """
            set -eu
            . "$1"
            JAVAN_TIMING_LOG=$2
            export JAVAN_TIMING_LOG
            : > "$JAVAN_TIMING_LOG"
            javan_timing_run bootstrap_jvm true
            javan_timing_run bootstrap_gen2 true
            javan_timing_write_reports linux-x64 2 bootstrap "$3" "$4"
            """);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of(
                "sh",
                runner.toString(),
                REPO_ROOT.resolve(".github/scripts/timing-report.sh").toString(),
                log.toString(),
                json.toString(),
                markdown.toString()
            ),
            Duration.ofSeconds(20),
            Map.of()
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Timing: bootstrap_jvm=", "Timing: bootstrap_gen2=");
        assertThat(Files.readString(json))
            .contains("\"target\": \"linux-x64\"")
            .contains("\"bootstrapGeneration\": 2")
            .contains("\"proofScope\": \"bootstrap\"")
            .containsPattern("\\{\\\"name\\\": \\\"bootstrap_jvm\\\", \\\"seconds\\\": \\d+}")
            .containsPattern("\\{\\\"name\\\": \\\"bootstrap_gen2\\\", \\\"seconds\\\": \\d+}")
            .containsPattern("\\\"totalSeconds\\\": \\d+");
        assertThat(Files.readString(markdown))
            .containsPattern("\\| `bootstrap_jvm` \\| \\d+ \\|")
            .containsPattern("\\| `bootstrap_gen2` \\| \\d+ \\|")
            .containsPattern("\\| \\*\\*Total measured\\*\\* \\| \\*\\*\\d+\\*\\* \\|");
    }

    @Test
    void verifyReleaseScriptKeepsPackagedAcceptanceAndSanitizerProof() throws Exception {
        final String script = Files.readString(VERIFY_RELEASE);

        assertThat(script)
            .contains("JAVAN_BIN=$PACKAGE_BIN .github/scripts/acceptance.sh")
            .contains("JAVAN_BIN=$PACKAGE_BIN JAVAN_SANITIZER_REQUIRED=true sh .github/scripts/sanitizer-suite.sh");
    }

    @Test
    void verifyCiPackageSmokeKeepsPackagedJarProof() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);

        assertThat(script)
            .contains("build target/classes --main javan.Main --jar --output javan-package-selfhost-jar")
            .contains("Main-Class: javan.Main");
    }

    @Test
    void verifyCiPackageSmokeKeepsPackagedNativeSelfHostProof() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);

        assertThat(script)
            .contains("build target/classes --main javan.Main --output javan-package-selfhost-smoke")
            .contains("\"$SELFHOST_BIN\" --version | grep -F \"javan $PACKAGE_VERSION\"");
    }

    @Test
    void verifyCiPackageSmokeKeepsPackageBackedSelfHostSanitizerProof() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);

        assertThat(script)
            .contains("JAVAN_SELF_HOST_REUSE_GENERATED=true")
            .contains("sh .github/scripts/sanitizer-self-host-smoke.sh")
            .contains("\"kind\": \"self-host\"")
            .contains("\"actualLiveAllocations\": 0")
            .contains("\"actualLiveBytes\": 0");
    }

    @Test
    void containerWorkflowBuildsFromReleasedLinuxPackages() throws Exception {
        final String containerWorkflow = Files.readString(CONTAINER_WORKFLOW);

        assertThat(containerWorkflow)
            .contains("- name: \"📥 Download")
            .contains("gh release download")
            .contains("linux-x64.tar.gz")
            .contains("linux-aarch64.tar.gz");
    }

    @Test
    void containerWorkflowKeepsDefaultImageShowcaseVerification() throws Exception {
        final String containerWorkflow = Files.readString(CONTAINER_WORKFLOW);

        assertThat(containerWorkflow)
            .contains("- name: \"🧪 Showcase")
            .contains("JAVAN_IMAGE=\"$image\" sh .github/scripts/verify-showcase.sh");
    }

    private static void writeReleaseArtifact(final Path releaseDir, final String name, final String content) throws Exception {
        final Path archive = releaseDir.resolve(name);
        Files.writeString(archive, content, StandardCharsets.UTF_8);
        final String sha256 = sha256Hex(Files.readAllBytes(archive));
        Files.writeString(releaseDir.resolve(name + ".sha256"), sha256 + "  " + name + "\n", StandardCharsets.UTF_8);
    }

    private static String sha256Hex(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
