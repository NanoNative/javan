package javan;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class ReleasePackagingSurfaceTest extends CliIntegrationSupport {
    private static final Path BUILD_COMMON = Path.of(".github/workflows/build-common.yml");
    private static final Path BUILD_PR = Path.of(".github/workflows/build-pr.yml");
    private static final Path BUILD_MERGE = Path.of(".github/workflows/build-merge.yml");
    private static final Path NATIVE_PROOF = Path.of(".github/workflows/native-proof.yml");
    private static final Path TIMINGS_WORKFLOW = Path.of(".github/workflows/timings.yml");
    private static final Path PACKAGE_BASELINES_WORKFLOW = Path.of(".github/workflows/package-build-baselines.yml");
    private static final Path RELEASE_WORKFLOW = Path.of(".github/workflows/release.yml");
    private static final Path CONTAINER_WORKFLOW = Path.of(".github/workflows/container-images.yml");
    private static final Path ROADMAP = Path.of("docs/roadmap.md");
    private static final Path VERIFY_RELEASE = Path.of(".github/scripts/verify-release.sh");
    private static final Path VERIFY_CI_PACKAGE_SMOKE = Path.of(".github/scripts/verify-ci-package-smoke.sh");
    private static final Path VERIFY_PACKAGE_NATIVE_IMPORTS = Path.of(".github/scripts/verify-package-native-imports.sh");
    private static final Path PACKAGE_RELEASE_REHEARSAL = Path.of(".github/scripts/package-release-rehearsal.sh");
    private static final Path REHEARSE_RELEASE_ARTIFACT = Path.of(".github/scripts/rehearse-release-artifact.sh");
    private static final Path VERIFY_IMAGE = Path.of(".github/scripts/verify-image.sh");
    private static final Path VERSION_TEMPLATE = Path.of("src/main/version/javan/cli/Version.java");
    private static final Path REPO_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final String UPLOAD_VERSION = "2031.2.3";

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
            .contains("assert_match \"javan home:\", shell_output(\"#{bin}/javan doctor\")")
            .doesNotContain("javan install");
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
    void firstNativeReleaseMilestonesKeepTheirStableReferences() throws Exception {
        final String roadmap = Files.readString(ROADMAP);

        assertThat(roadmap)
            .contains(
                "issues/103",
                "issues/116",
                "issues/117",
                "issues/130",
                "issues/250"
            );
        assertThat(roadmap).contains("REL-CONTAINER-01", "issues/115");
    }

    @Test
    void imageVerificationRecordsVerifiedArchivesAndImmutableDigest() throws Exception {
        final String version = "2026.8.30";
        final String image = "ghcr.io/nanonative/javan:" + version;
        final Path releaseDir = Files.createDirectories(tempDir.resolve("release"));
        final Path proofDir = tempDir.resolve("proof");
        final Path bin = Files.createDirectories(tempDir.resolve("bin"));
        final String x64Content = "linux-x64";
        final String arm64Content = "linux-aarch64";
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-x64.tar.gz", x64Content);
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-aarch64.tar.gz", arm64Content);
        writeDockerManifestStub(bin);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(VERIFY_IMAGE).toString(), image),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_RELEASE_VERSION", version,
                "JAVAN_RELEASE_ARCHIVE_DIR", releaseDir.toString(),
                "JAVAN_RELEASE_PROOF_DIR", proofDir.toString(),
                "PATH", bin + System.getProperty("path.separator") + System.getenv("PATH")
            )
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).contains(
            "javan-" + version + "-linux-x64.tar.gz: OK",
            "javan-" + version + "-linux-aarch64.tar.gz: OK"
        );
        assertThat(run.stdout()).contains("Verified image manifest", "Recorded release proof");
        assertThat(Files.readString(proofDir.resolve("ghcr.io-nanonative-javan-" + version + ".json")))
            .contains(
                "\"image\": \"" + image + "\"",
                "\"digest\": \"sha256:" + "a".repeat(64) + "\"",
                "\"version\": \"" + version + "\"",
                "\"target\": \"linux-x64\", \"sha256\": \""
                    + sha256Hex(x64Content.getBytes(StandardCharsets.UTF_8)) + "\"",
                "\"target\": \"linux-aarch64\", \"sha256\": \""
                    + sha256Hex(arm64Content.getBytes(StandardCharsets.UTF_8)) + "\""
            );
    }

    @Test
    void imageVerificationRejectsMissingReleaseProofInputsBeforeRegistryAccess() {
        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(VERIFY_IMAGE).toString(), "ghcr.io/nanonative/javan:2026.8.30"),
            Duration.ofSeconds(20)
        );

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Missing release proof input: JAVAN_RELEASE_VERSION");
    }

    @Test
    void imageVerificationRejectsMissingArchiveEvidenceWithoutWritingMetadata() throws Exception {
        final String version = "2026.8.30";
        final String image = "ghcr.io/nanonative/javan:" + version;
        final Path releaseDir = Files.createDirectories(tempDir.resolve("release"));
        final Path proofDir = tempDir.resolve("proof");
        final Path bin = Files.createDirectories(tempDir.resolve("bin"));
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-x64.tar.gz", "linux-x64");
        writeDockerManifestStub(bin);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(VERIFY_IMAGE).toString(), image),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_RELEASE_VERSION", version,
                "JAVAN_RELEASE_ARCHIVE_DIR", releaseDir.toString(),
                "JAVAN_RELEASE_PROOF_DIR", proofDir.toString(),
                "PATH", bin + System.getProperty("path.separator") + System.getenv("PATH")
            )
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains(
            "javan-" + version + "-linux-x64.tar.gz: OK",
            "Missing release proof input: javan-" + version + "-linux-aarch64.tar.gz or javan-"
                + version + "-linux-aarch64.tar.gz.sha256"
        );
        assertThat(proofDir).doesNotExist();
    }

    @Test
    void imageVerificationRejectsNonLinuxManifestArchitecture() throws Exception {
        final String version = "2026.8.30";
        final String image = "ghcr.io/nanonative/javan:" + version;
        final Path releaseDir = Files.createDirectories(tempDir.resolve("release"));
        final Path proofDir = tempDir.resolve("proof");
        final Path bin = Files.createDirectories(tempDir.resolve("bin"));
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-x64.tar.gz", "linux-x64");
        writeReleaseArtifact(releaseDir, "javan-" + version + "-linux-aarch64.tar.gz", "linux-aarch64");
        writeDockerManifestStub(bin, """
            {"manifests":[
              {"platform":{"architecture":"amd64","os":"linux"}},
              {"platform":{"architecture":"arm64","os":"windows"}}
            ]}
            """);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(VERIFY_IMAGE).toString(), image),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_RELEASE_VERSION", version,
                "JAVAN_RELEASE_ARCHIVE_DIR", releaseDir.toString(),
                "JAVAN_RELEASE_PROOF_DIR", proofDir.toString(),
                "PATH", bin + System.getProperty("path.separator") + System.getenv("PATH")
            )
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stderr()).contains("Image manifest is missing linux/arm64");
        assertThat(proofDir).doesNotExist();
    }

    @Test
    void releaseRehearsalPackagesChecksummedCompiledInputsAndRejectsPublication() throws Exception {
        final Path releaseDir = tempDir.resolve("release");
        final String target = "linux-x64";
        final String archiveName = "javan-2026.8.28-" + target + ".tar.gz";
        Files.createDirectories(releaseDir);
        writeReleaseArtifact(releaseDir, archiveName, "release archive");
        final Path archive = releaseDir.resolve(archiveName);

        final ProcessResult packageSidecar = process(
            REPO_ROOT,
            List.of(
                "sh",
                REPO_ROOT.resolve(PACKAGE_RELEASE_REHEARSAL).toString(),
                archive.toString(),
                target,
                "0123456789abcdef"
            ),
            Duration.ofSeconds(30)
        );

        assertThat(packageSidecar.exitCode()).isZero();
        final Path sidecar = Path.of(packageSidecar.stdout().strip());
        assertThat(sidecar).isRegularFile();
        assertThat(sidecar.resolveSibling(sidecar.getFileName() + ".sha256")).isRegularFile();
        final ProcessResult contents = process(
            REPO_ROOT,
            List.of("tar", "-tzf", sidecar.toString()),
            Duration.ofSeconds(20)
        );
        assertThat(contents.exitCode()).isZero();
        assertThat(contents.stdout()).contains(
            "self-host/classes/javan/Main.class",
            "abi/caller.c",
            "memory-soak/src/main/java/com/acme/Main.java",
            "platform-target.sh",
            "sanitizer-smoke.sh"
        );

        final ProcessResult rejectedPublication = process(
            REPO_ROOT,
            List.of(
                "sh",
                REPO_ROOT.resolve(REHEARSE_RELEASE_ARTIFACT).toString(),
                "--archive", archive.toString(),
                "--target", target,
                "--upload"
            ),
            Duration.ofSeconds(20)
        );
        assertThat(rejectedPublication.exitCode()).isEqualTo(2);
        assertThat(rejectedPublication.stderr()).contains("does not accept publication control");
    }

    @Test
    void releaseRehearsalRejectsLinkEntriesBeforeExtraction() throws Exception {
        final Path releaseDir = tempDir.resolve("linked-release");
        final String target = hostTarget();
        final String archiveName = "javan-2026.8.28-" + target + ".tar.gz";
        final String rootName = archiveName.substring(0, archiveName.length() - ".tar.gz".length());
        final Path archiveRoot = releaseDir.resolve(rootName);
        Files.createDirectories(archiveRoot);
        Files.createSymbolicLink(archiveRoot.resolve("escape"), Path.of(".."));
        final Path archive = releaseDir.resolve(archiveName);
        final ProcessResult packaged = process(
            releaseDir,
            List.of("tar", "-czf", archive.toString(), rootName),
            Duration.ofSeconds(20)
        );
        assertThat(packaged.exitCode()).isZero();
        writeReleaseArtifactChecksum(archive);
        final ProcessResult sidecar = process(
            REPO_ROOT,
            List.of(
                "sh",
                REPO_ROOT.resolve(PACKAGE_RELEASE_REHEARSAL).toString(),
                archive.toString(),
                target,
                "0123456789abcdef"
            ),
            Duration.ofSeconds(30)
        );
        assertThat(sidecar.exitCode()).isZero();

        final ProcessResult rejected = process(
            REPO_ROOT,
            List.of(
                "sh",
                REPO_ROOT.resolve(REHEARSE_RELEASE_ARTIFACT).toString(),
                "--archive", archive.toString(),
                "--target", target
            ),
            Duration.ofSeconds(20)
        );

        assertThat(rejected.exitCode()).isEqualTo(1);
        assertThat(rejected.stderr()).contains("unsupported link entries");
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

    @TestFactory
    Stream<DynamicTest> releaseUploadSelectsOnlyPublicAssetsOnCreationAndRetry() {
        return Stream.of(false, true).map(existingRelease -> DynamicTest.dynamicTest(
            existingRelease ? "existing release" : "new release", () -> {
                final Path root = prepareReleaseUpload();
                final Path calls = root.resolve("gh-recordings");
                if (existingRelease) {
                    Files.createFile(calls.resolve("exists"));
                }
                final Map<String, String> evidence = Map.of(
                    "javan-" + UPLOAD_VERSION + "-linux-x64-rehearsal.tar.gz", "internal compiled inputs",
                    "javan-" + UPLOAD_VERSION + "-linux-x64-rehearsal.tar.gz.sha256", "internal checksum",
                    "javan-" + UPLOAD_VERSION + "-linux-x64.rehearsal.json", "internal report",
                    "javan-" + UPLOAD_VERSION + "-linux-x64.rehearsal.md", "internal report",
                    "javan-2030.1.2-linux-x64.tar.gz", "stale package",
                    "javan-" + UPLOAD_VERSION + "-windows-x64.tar.gz", "excluded target",
                    "notes with spaces.txt", "unrelated file"
                );
                for (final var entry : evidence.entrySet()) {
                    Files.writeString(root.resolve("dist/release").resolve(entry.getKey()), entry.getValue());
                }

                for (int attempt = 0; attempt < 2; attempt++) {
                    final ProcessResult run = runReleaseUpload(root);
                    assertThat(run.exitCode()).as(run.stderr()).isZero();
                    final List<String> upload = Files.readAllLines(calls.resolve("upload"));
                    assertThat(upload).startsWith(UPLOAD_VERSION).endsWith("--clobber");
                    assertThat(upload.subList(1, upload.size() - 1))
                        .containsExactlyInAnyOrderElementsOf(publicReleaseAssets().toList());
                }

                if (existingRelease) {
                    assertThat(calls.resolve("create")).doesNotExist();
                } else {
                    assertThat(Files.readAllLines(calls.resolve("create"))).containsExactly(
                        UPLOAD_VERSION, "--target", "7".repeat(40), "--title", UPLOAD_VERSION, "--generate-notes"
                    );
                }
                assertThat(Files.readAllLines(calls.resolve("commands")))
                    .containsExactlyElementsOf(existingRelease
                        ? List.of("view", "upload", "view", "upload")
                        : List.of("view", "create", "upload", "view", "upload"));
                for (final var entry : evidence.entrySet()) {
                    assertThat(Files.readString(root.resolve("dist/release").resolve(entry.getKey())))
                        .isEqualTo(entry.getValue());
                }
            }));
    }

    @TestFactory
    Stream<DynamicTest> releaseUploadRejectsMissingAssetsBeforeContactingGitHub() {
        return publicReleaseAssets().map(asset -> DynamicTest.dynamicTest(asset, () -> {
            final Path root = prepareReleaseUpload();
            Files.delete(root.resolve(asset));

            final ProcessResult run = runReleaseUpload(root);

            assertThat(run.exitCode()).isNotZero();
            assertThat(run.stderr()).contains(asset);
            assertThat(root.resolve("gh-recordings/commands")).doesNotExist();
        }));
    }

    @TestFactory
    Stream<DynamicTest> releaseUploadRejectsEmptyAssetsBeforeContactingGitHub() {
        return publicReleaseAssets().map(asset -> DynamicTest.dynamicTest(asset, () -> {
            final Path root = prepareReleaseUpload();
            Files.writeString(root.resolve(asset), "");

            final ProcessResult run = runReleaseUpload(root);

            assertThat(run.exitCode()).isNotZero();
            assertThat(run.stderr()).contains(asset);
            assertThat(root.resolve("gh-recordings/commands")).doesNotExist();
        }));
    }

    @TestFactory
    Stream<DynamicTest> releaseUploadRejectsAnotherVersionsPackage() {
        return Stream.of("linux-x64", "linux-aarch64", "macos-aarch64")
            .map(target -> DynamicTest.dynamicTest(target, () -> {
                final Path root = prepareReleaseUpload();
                for (final String suffix : List.of(".tar.gz", ".tar.gz.sha256")) {
                    Files.move(root.resolve("dist/release/javan-" + UPLOAD_VERSION + "-" + target + suffix),
                        root.resolve("dist/release/javan-2030.1.2-" + target + suffix));
                }

                final ProcessResult run = runReleaseUpload(root);

                assertThat(run.exitCode()).isNotZero();
                assertThat(run.stderr()).contains("javan-" + UPLOAD_VERSION + "-" + target + ".tar.gz");
                assertThat(root.resolve("gh-recordings/commands")).doesNotExist();
            }));
    }

    @TestFactory
    Stream<DynamicTest> releaseUploadPropagatesPublicationFailures() {
        return Stream.of("create", "upload").map(command -> DynamicTest.dynamicTest(command, () -> {
            final Path root = prepareReleaseUpload();
            Files.createFile(root.resolve("gh-recordings/fail-" + command));

            final ProcessResult run = runReleaseUpload(root);

            assertThat(run.exitCode()).isEqualTo(73);
            assertThat(Files.readAllLines(root.resolve("gh-recordings/commands")))
                .containsExactlyElementsOf(command.equals("create")
                    ? List.of("view", "create") : List.of("view", "create", "upload"));
        }));
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
    void releaseEntrypointReusesVerifiedClassesAndPreservesReportsThroughEveryProof() throws Exception {
        final Path root = tempDir.resolve("release fixture");
        for (final String script : List.of(
            ".github/scripts/verify-release.sh", "scripts/build.sh",
            ".github/scripts/timing-report.sh", ".github/scripts/generated-sources.sh"
        )) {
            final Path destination = root.resolve(script);
            Files.createDirectories(destination.getParent());
            writeExecutableScript(destination, Files.readString(REPO_ROOT.resolve(script)));
        }
        final Map<String, String> stubs = Map.of(
            "mvnw", """
                #!/bin/sh
                set -eu
                case "$*" in
                  *help:evaluate*) printf '1.0.0\\n'; exit 0 ;;
                  '-q clean verify'|'-q -DskipTests clean package') ;;
                  *) printf 'Unexpected Maven arguments: %s\\n' "$*" >&2; exit 1 ;;
                esac
                printf '%s\\n' "$*" >> maven-preparations.log
                rm -rf target
                mkdir -p target/classes/javan target/site/jacoco
                touch target/classes/javan/Main.class target/javan-1.0.0.jar
                if [ "$*" = '-q clean verify' ]; then
                  printf 'verified report\\n' > target/site/jacoco/report-sentinel
                fi
                """,
            "tools/uname", """
                #!/bin/sh
                printf 'fixture-host\\n'
                """,
            "tools/java", """
                #!/bin/sh
                set -eu
                case "$*" in
                  *--output*)
                    for output do :; done
                    printf 'bootstrap:%s\\n' "$output" >> "$PROOF_LOG"
                    mkdir -p target/.javan/generated target/.javan/bin
                    for file in main.c javan_program.h javan_runtime.c javan_runtime.h; do
                      printf 'stub source\\n' > "target/.javan/generated/$file"
                    done
                    printf 'javan-generated-sources-v1\\nmain.c\\n' > target/.javan/generated/javan_program.sources
                    cp "$0" "target/.javan/bin/$output"
                    ;;
                  *) printf 'compiler:%s\\n' "$*" >> "$PROOF_LOG" ;;
                esac
                """,
            ".github/scripts/package-release.sh", """
                #!/bin/sh
                set -eu
                printf 'package\\n' >> "$PROOF_LOG"
                mkdir -p dist/package/bin
                cp dist/javan dist/package/bin/javan
                tar -czf dist/package.tar.gz -C dist package
                printf '%s/dist/package.tar.gz\\n' "$PWD"
                """,
            ".github/scripts/verify-package.sh", """
                #!/bin/sh
                set -eu
                test -f "$1"
                printf 'verify-package\\n' >> "$PROOF_LOG"
                """,
            ".github/scripts/acceptance.sh", """
                #!/bin/sh
                set -eu
                test -x "$JAVAN_BIN"
                printf 'acceptance\\n' >> "$PROOF_LOG"
                """,
            ".github/scripts/sanitizer-suite.sh", """
                #!/bin/sh
                set -eu
                test -x "$JAVAN_BIN"
                printf 'sanitizer:%s\\n' "$JAVAN_SANITIZER_REQUIRED" >> "$PROOF_LOG"
                """
        );
        for (final Map.Entry<String, String> stub : stubs.entrySet()) {
            final Path destination = root.resolve(stub.getKey());
            Files.createDirectories(destination.getParent());
            writeExecutableScript(destination, stub.getValue());
        }
        final Path proofLog = root.resolve("proof.log");
        final ProcessResult run = process(root, List.of("sh", ".github/scripts/verify-release.sh"),
            Duration.ofSeconds(20), Map.of(
                "PATH", root.resolve("tools") + java.io.File.pathSeparator + System.getenv("PATH"),
                "TMPDIR", Files.createDirectories(root.resolve("tmp")).toString(),
                "PROOF_LOG", proofLog.toString(),
                "JAVAN_BUILD_REUSE_TARGET", "false",
                "JAVAN_BOOTSTRAP_GENERATION", "3",
                "JAVAN_BOOTSTRAP_SOURCE", "",
                "JAVAN_TIMING_LOG", ""
            ));

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(Files.readAllLines(root.resolve("maven-preparations.log"))).containsExactly("-q clean verify");
        assertThat(root.resolve("target/site/jacoco/report-sentinel")).hasContent("verified report\n");
        assertThat(Files.readAllLines(proofLog)).containsExactly(
            "bootstrap:javan-bootstrap-from-jvm", "bootstrap:javan-bootstrap-rebuilt", "bootstrap:javan-bootstrap-verified",
            "compiler:--version", "package", "verify-package",
            "compiler:doctor", "compiler:--help", "compiler:--version", "acceptance", "sanitizer:true"
        );
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
            .contains("MINGW*|MSYS*|CYGWIN*)")
            .contains("BOOTSTRAP_SUFFIX=.exe")
            .contains("OUTPUT=${1:-dist/javan$BOOTSTRAP_SUFFIX}")
            .contains("javan-bootstrap-from-jvm$BOOTSTRAP_SUFFIX build target/classes")
            .contains("javan-bootstrap-rebuilt$BOOTSTRAP_SUFFIX build target/classes")
            .contains("javan-bootstrap-rebuilt$BOOTSTRAP_SUFFIX")
            .contains("javan-bootstrap-verified$BOOTSTRAP_SUFFIX")
            .contains("GENERATION=${JAVAN_BOOTSTRAP_GENERATION:-3}")
            .contains("2|3)")
            .contains("javan-bootstrap-rebuilt")
            .contains("javan-bootstrap-verified")
            .contains("if [ \"$GENERATION\" = \"3\" ]; then")
            .contains("javan_timing_run bootstrap_jvm")
            .contains("javan_timing_run bootstrap_gen2")
            .contains("javan_timing_run bootstrap_gen3")
            .contains("javan_copy_generated_sources target/.javan/generated \"$GEN2_SOURCES\"")
            .contains("javan_compare_generated_sources \"$GEN2_SOURCES\" target/.javan/generated")
            .contains("cp \"$BUILT\" \"$OUTPUT\"");
    }

    @Test
    void generationThreeBuildCanCompilePortableGeneratedSourcesOnTheTargetHost() throws Exception {
        final String build = Files.readString(Path.of("scripts/build.sh"));
        final String workflow = Files.readString(NATIVE_PROOF);

        assertThat(build)
            .contains("SOURCE=${JAVAN_BOOTSTRAP_SOURCE:-}")
            .contains("JAVAN_BOOTSTRAP_SOURCE requires generation 3")
            .contains("for file in javan_program.h javan_program.sources javan_runtime.c javan_runtime.h")
            .contains("javan_copy_generated_sources \"$SOURCE\" \"$GENERATED\"")
            .contains("javan_generated_sources \"$SOURCE\"")
            .contains("javan_timing_run bootstrap_gen3 sh -c")
            .doesNotContain("JAVAN_BOOTSTRAP_SEED", "JAVAN_BOOTSTRAP_TIMING_SEED");
        assertThat(workflow)
            .contains("bootstrap_source_artifact:")
            .contains("upload_bootstrap_source:")
            .contains("name: bootstrap-source-${{ inputs.target }}")
            .contains("include-hidden-files: true")
            .contains("target/.javan/generated/javan_program.sources")
            .contains("target/.javan/generated/units/*.c")
            .contains("uses: actions/download-artifact@")
            .contains("path: target/.javan/generated")
            .contains("JAVAN_BOOTSTRAP_SOURCE: ${{ inputs.bootstrap_source_artifact != '' && 'target/.javan/generated'")
            .doesNotContain("proof == 'bootstrap-seed'", "JAVAN_BOOTSTRAP_TIMING_SEED");
        assertThat(Files.readString(Path.of(".github/workflows/build-common.yml")))
            .contains("  linux-package-generation3:")
            .contains("upload_bootstrap_source: true")
            .contains("bootstrap_source_artifact: ${{ inputs.bootstrap_generation == 3")
            .doesNotContain("macos-package-seed", "bootstrap_seed_artifact");
    }

    @Test
    void portableBootstrapSourcesFailBeforeCompilationWhenInvalid() throws Exception {
        final Path source = Files.createDirectories(tempDir.resolve("bootstrap-source"));
        final List<String> command = List.of(
            "sh",
            REPO_ROOT.resolve("scripts/build.sh").toString(),
            tempDir.resolve("javan").toString()
        );
        final ProcessResult wrongGeneration = process(
            REPO_ROOT,
            command,
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_BUILD_REUSE_TARGET", "true",
                "JAVAN_BOOTSTRAP_GENERATION", "2",
                "JAVAN_BOOTSTRAP_SOURCE", source.toString()
            )
        );
        final ProcessResult missingSource = process(
            REPO_ROOT,
            command,
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_BUILD_REUSE_TARGET", "true",
                "JAVAN_BOOTSTRAP_GENERATION", "3",
                "JAVAN_BOOTSTRAP_SOURCE", source.toString()
            )
        );

        assertThat(wrongGeneration.exitCode()).isEqualTo(2);
        assertThat(wrongGeneration.stderr()).contains("JAVAN_BOOTSTRAP_SOURCE requires generation 3.");
        assertThat(missingSource.exitCode()).isEqualTo(1);
        assertThat(missingSource.stderr()).contains("Missing generated bootstrap source:", "javan_program.h");
    }

    @Test
    void generatedSourceManifestRejectsUnsafeOrIncompleteInventories() throws Exception {
        final Path generated = Files.createDirectories(tempDir.resolve("generated sources"));
        Files.writeString(generated.resolve("main.c"), "int main(void) { return 0; }\n");
        Files.writeString(generated.resolve("javan_program.sources"),
            "javan-generated-sources-v1\nmain.c\n");
        final Path runner = tempDir.resolve("manifest-test.sh");
        Files.writeString(runner, """
            set -eu
            . "$1"
            javan_generated_sources "$2"
            """);
        final List<String> command = List.of(
            "sh",
            runner.toString(),
            REPO_ROOT.resolve(".github/scripts/generated-sources.sh").toString(),
            generated.toString()
        );

        final ProcessResult valid = process(REPO_ROOT, command, Duration.ofSeconds(20), Map.of());
        assertThat(valid.exitCode()).isZero();
        assertThat(valid.stdout()).isEqualTo("main.c\n");

        Files.writeString(generated.resolve("javan_program.sources"),
            "javan-generated-sources-v1\nmain.c\n../escape.c\n");
        final ProcessResult traversal = process(REPO_ROOT, command, Duration.ofSeconds(20), Map.of());
        assertThat(traversal.exitCode()).isEqualTo(1);
        assertThat(traversal.stderr()).contains("Invalid generated source entry: ../escape.c");

        Files.writeString(generated.resolve("javan_program.sources"),
            "javan-generated-sources-v1\nmain.c\nmain.c\n");
        final ProcessResult duplicate = process(REPO_ROOT, command, Duration.ofSeconds(20), Map.of());
        assertThat(duplicate.exitCode()).isEqualTo(1);
        assertThat(duplicate.stderr()).contains("Duplicate generated source entry: main.c");
    }

    @Test
    void generatedSourceCopyDoesNotFollowDestinationLinks() throws Exception {
        final Path source = Files.createDirectories(tempDir.resolve("source"));
        Files.createDirectories(source.resolve("units"));
        Files.writeString(source.resolve("main.c"), "int main(void) { return 0; }\n");
        Files.writeString(source.resolve("units/functions-00.c"), "void value(void) {}\n");
        Files.writeString(source.resolve("javan_program.h"), "/* program */\n");
        Files.writeString(source.resolve("javan_runtime.c"), "/* runtime */\n");
        Files.writeString(source.resolve("javan_runtime.h"), "/* runtime */\n");
        Files.writeString(source.resolve("javan_program.sources"),
            "javan-generated-sources-v1\nmain.c\nunits/functions-00.c\n");
        final Path outside = Files.createDirectories(tempDir.resolve("outside"));
        final Path sentinel = Files.writeString(outside.resolve("functions-00.c"), "outside\n");
        final Path target = Files.createDirectories(tempDir.resolve("target"));
        try {
            Files.createSymbolicLink(target.resolve("units"), outside);
            Files.createSymbolicLink(target.resolve("javan_program.h"), sentinel);
        } catch (final UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "symbolic links are unavailable: " + exception.getMessage());
        }
        final Path runner = tempDir.resolve("copy-test.sh");
        Files.writeString(runner, """
            set -eu
            . "$1"
            javan_copy_generated_sources "$2" "$3"
            """);

        final ProcessResult copied = process(REPO_ROOT, List.of(
            "sh",
            runner.toString(),
            REPO_ROOT.resolve(".github/scripts/generated-sources.sh").toString(),
            source.toString(),
            target.toString()
        ), Duration.ofSeconds(20), Map.of());

        assertThat(copied.exitCode()).as(copied.stderr()).isZero();
        assertThat(Files.readString(sentinel)).isEqualTo("outside\n");
        assertThat(target.resolve("units")).isDirectory();
        assertThat(target.resolve("javan_program.h")).isRegularFile();
    }

    @Test
    void packageProofPublishesComparableNativeTimingReports() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);
        final String workflow = Files.readString(NATIVE_PROOF);

        assertThat(script)
            .contains("javan_timing_run package_verify")
            .contains("javan_timing_record package_self_check")
            .contains("javan_timing_run package_jar")
            .contains("javan_timing_run package_sanitizer")
            .contains("JAVAN_SELF_HOST_REUSE_GENERATED=true")
            .contains("javan_timing_write_reports")
            .contains("target/javan-$PACKAGE_TARGET-timings.tsv")
            .contains("javan-$PACKAGE_TARGET-timings.json")
            .contains("javan-$PACKAGE_TARGET-timings.md")
            .doesNotContain(
                "javan_timing_run package_native",
                "rm -rf target/.javan"
            );
        assertThat(script.indexOf("if [ \"$PACKAGE_PROOF_SCOPE\" = \"bootstrap\" ]; then"))
            .isLessThan(script.indexOf("tar -xzf \"$ARCHIVE\" -C \"$TMP\""));
        assertThat(Files.readString(Path.of(".github/scripts/sanitizer-self-host-smoke.sh")))
            .contains("javan_timing_record sanitizer_compile");
        assertThat(workflow)
            .contains("upload_package:")
            .contains("if: inputs.proof == 'package-self-host' && inputs.upload_package")
            .contains("hashFiles(format('target/javan-{0}-timings.tsv', inputs.target)) != ''")
            .contains("name: timings-${{ inputs.target }}-gen${{ inputs.bootstrap_generation }}")
            .contains("retention-days: 7")
            .contains("target/javan-${{ inputs.target }}-timings.tsv")
            .contains("dist/release/javan-${{ inputs.target }}-timings.json")
            .contains("dist/release/javan-${{ inputs.target }}-timings.md");
    }

    @Test
    void packageBuildBaselineMeasuresTheVersionedPublicShowcaseWithoutInventingThresholds() throws Exception {
        final Path script = Path.of(".github/scripts/measure-package-build-baseline.sh");
        final String content = Files.readString(script);

        assertThat(content)
            .contains(
                "sh .github/scripts/verify-package.sh \"$ARCHIVE\"",
                "cp -R \"$ROOT/example\" \"$FIXTURE\"",
                "javan_timing_measure \"$PACKAGE_BIN\" build \"$FIXTURE/target/classes\"",
                "for iteration in 1 2 3; do",
                "measure_build \"cold-$iteration\" cold",
                "measure_build \"warm-$iteration\" warm",
                "measure_build changed-source changed-source",
                "\"wallSeconds\"",
                "\"cpuSeconds\"",
                "\"peakRssBytes\"",
                "\"artifactBytes\"",
                "\"cToolchain\"",
                "\"regressionPolicy\": \"none; results are comparative evidence, not universal thresholds\""
            )
            .doesNotContain("maximumRegression", "fail-on-regression");

        final ProcessResult syntax = process(
            REPO_ROOT,
            List.of("sh", "-n", REPO_ROOT.resolve(script).toString()),
            Duration.ofSeconds(20),
            Map.of()
        );
        assertThat(syntax.exitCode()).as(syntax.stderr()).isZero();
    }

    @Test
    void packageBuildBaselineWorkflowUsesEveryFirstReleaseTargetAndUploadsResults() throws Exception {
        final String workflow = Files.readString(PACKAGE_BASELINES_WORKFLOW);
        final String proof = Files.readString(NATIVE_PROOF);

        assertThat(workflow)
            .contains(
                "name: PackageBuildBaselines",
                "workflow_dispatch:",
                "max-parallel: 3",
                "target: linux-x64, runner: ubuntu-24.04",
                "target: linux-aarch64, runner: ubuntu-24.04-arm",
                "target: macos-aarch64, runner: macos-15",
                "uses: ./.github/workflows/native-proof.yml",
                "proof: package-baseline",
                "timeout_minutes: 90",
                "pattern: package-build-baseline-*",
                "Measurements are evidence, not regression thresholds."
            );
        assertThat(proof)
            .contains(
                "if: inputs.proof == 'package-baseline'",
                "measure-package-build-baseline.sh",
                "name: package-build-baseline-${{ inputs.target }}",
                "retention-days: 90"
            );
    }

    @Test
    void packageBuildBaselineRejectsMissingArchives() throws Exception {
        final ProcessResult result = process(
            REPO_ROOT,
            List.of("sh", REPO_ROOT.resolve(".github/scripts/measure-package-build-baseline.sh").toString()),
            Duration.ofSeconds(20),
            Map.of()
        );

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("Usage: .github/scripts/measure-package-build-baseline.sh");
    }

    @Test
    void sanitizerScriptsShareOneStrictAsanConfiguration() throws Exception {
        final Path helper = REPO_ROOT.resolve(".github/scripts/sanitizer-common.sh");
        final Path runner = tempDir.resolve("sanitizer-common-test.sh");
        Files.writeString(runner, """
            set -eu
            . "$1"
            ASAN_OPTIONS=
            configure_asan_options false smoke
            printf '%s\n' "$ASAN_OPTIONS"
            ASAN_OPTIONS=detect_leaks=0
            configure_asan_options true required-smoke
            """);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of("sh", runner.toString(), helper.toString()),
            Duration.ofSeconds(20),
            Map.of()
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stdout()).contains("detect_leaks=1:halt_on_error=1");
        assertThat(run.stderr()).contains("required-smoke cannot inherit ASAN_OPTIONS with detect_leaks=0");
        for (final String script : List.of(
            "sanitizer-smoke.sh",
            "sanitizer-self-host-smoke.sh",
            "sanitizer-library-smoke.sh"
        )) {
            assertThat(Files.readString(REPO_ROOT.resolve(".github/scripts").resolve(script)))
                .contains("sanitizer-common.sh")
                .doesNotContain("json_escape()", "configure_asan_options()");
        }
    }

    @TestFactory
    Stream<DynamicTest> selfHostCompilerDiagnosticsSurviveFailureAndSignals() {
        return Stream.of(
            "success", "failure", "HUP", "INT", "TERM", "support-failure", "support-TERM", "probe-failure",
            "resource-success", "resource-failure", "resource-HUP", "resource-INT", "resource-TERM", "resource-unavailable"
        ).map(mode -> DynamicTest.dynamicTest(mode, () -> {
            final Path root = Files.createTempDirectory(tempDir, "compiler diagnostics ");
            final Path scripts = Files.createDirectories(root.resolve(".github/scripts"));
            for (final String name : List.of("sanitizer-self-host-smoke.sh", "sanitizer-common.sh",
                "generated-sources.sh", "timing-report.sh")) {
                Files.copy(REPO_ROOT.resolve(".github/scripts/" + name), scripts.resolve(name));
            }
            final Path generated = Files.createDirectories(root.resolve("target/.javan/generated"));
            for (final String name : List.of("javan_program.h", "javan_runtime.c", "main.c")) {
                Files.writeString(generated.resolve(name), "fixture\n");
            }
            Files.writeString(generated.resolve("javan_program.sources"), "javan-generated-sources-v1\nmain.c\n");
            final Path temporary = Files.createDirectories(root.resolve("temporary"));
            final Path compiler = root.resolve("compiler");
            writeExecutableScript(compiler, """
                #!/bin/sh
                set -eu
                case "$*" in
                  *sanitizer-support.c*)
                    case "$COMPILER_MODE" in
                      support-*) COMPILER_MODE=${COMPILER_MODE#support-} ;;
                      *) exit 0 ;;
                    esac ;;
                esac
                printf '%s\\n' 'compiler stdout retained'
                printf '%s\\n' 'compiler stderr retained' >&2
                case "$COMPILER_MODE" in
                  resource-*)
                    attempts=0
                    while [ ! -f "$PROBE_CALLS.samples-ready" ]; do
                      attempts=$((attempts + 1))
                      [ "$attempts" -lt 200 ] || exit 24
                      sleep 0.01
                    done
                    COMPILER_MODE=${COMPILER_MODE#resource-} ;;
                esac
                case "$COMPILER_MODE" in
                  failure) exit 23 ;;
                  HUP|INT|TERM) kill -"$COMPILER_MODE" "$PROOF_PID"; exit 23 ;;
                esac
                while [ "$1" != -o ]; do shift; done
                cp "$PROBE_FIXTURE" "$2"
                chmod +x "$2"
                """);
            final Path probe = root.resolve("probe");
            writeExecutableScript(probe, """
                #!/bin/sh
                set -eu
                printf '%s\\n' "$1" >> "$PROBE_CALLS"
                if [ "$COMPILER_MODE" = probe-failure ]; then
                  printf '%s\\n' 'probe failed' >&2
                  exit 19
                fi
                printf '%s\\n' live_allocations=0 live_bytes=0 peak_live_bytes=16 total_allocations=1 \\
                  gc_collections=1 gc_collected_allocations=0 gc_collected_bytes=0 root_frame_depth=0 \\
                  frame_root_count=0 > "$JAVAN_SANITIZER_PROOF_COUNTERS"
                """);
            final Path javan = root.resolve("javan");
            writeExecutableScript(javan, "#!/bin/sh\nexit 0\n");
            final Path calls = root.resolve("probe-calls");
            final Path timings = root.resolve("timings.tsv");
            final Map<String, String> environment = new HashMap<>(Map.of(
                "CC", compiler.toString(), "COMPILER_MODE", mode, "PROBE_FIXTURE", probe.toString(),
                "PROBE_CALLS", calls.toString(), "JAVAN_BIN", javan.toString(), "TMPDIR", temporary.toString(),
                "JAVAN_TIMING_LOG", timings.toString(), "JAVAN_SELF_HOST_REUSE_GENERATED", "true",
                "JAVAN_SANITIZER_REQUIRED", "true", "ASAN_OPTIONS", "detect_leaks=1:halt_on_error=1"
            ));
            if (mode.startsWith("resource-")) {
                final Path bin = Files.createDirectories(root.resolve("bin"));
                writeExecutableScript(bin.resolve("ps"), """
                    #!/bin/sh
                    printf '%s\\n' "$PPID" >> "$PROBE_CALLS.monitor-pids"
                    [ "$COMPILER_MODE" != resource-unavailable ] || exit 1
                    printf '%s\\n' '42 1 2048 compiler-fixture'
                    """);
                writeExecutableScript(bin.resolve("sleep"), """
                    #!/bin/sh
                    if [ "$1" = 10 ]; then
                      printf '%s\\n' "$$" >> "$PROBE_CALLS.sleep-pids"
                      if [ "$(wc -l < "$PROBE_CALLS.sleep-pids")" -ge 2 ]; then
                        : > "$PROBE_CALLS.samples-ready"
                        exec /bin/sleep 30
                      fi
                      exec /bin/sleep 0.01
                    fi
                    exec /bin/sleep "$@"
                    """);
                environment.put("PATH", bin + java.io.File.pathSeparator + System.getenv("PATH"));
            }
            final ProcessResult run = process(root, List.of("sh", "-c", """
                export PROOF_PID=$$
                exec sh .github/scripts/sanitizer-self-host-smoke.sh
                """), Duration.ofSeconds(20), environment);
            final int expectedExit = switch (mode) {
                case "success", "resource-success", "resource-unavailable" -> 0;
                case "failure", "support-failure", "resource-failure" -> 1;
                case "HUP", "resource-HUP" -> 129;
                case "INT", "resource-INT" -> 130;
                case "TERM", "support-TERM", "resource-TERM" -> 143;
                case "probe-failure" -> 19;
                default -> throw new IllegalArgumentException(mode);
            };
            assertThat(run.exitCode()).as("stdout=%s stderr=%s", run.stdout(), run.stderr()).isEqualTo(expectedExit);
            final String timing;
            if (mode.startsWith("support-")) {
                assertThat(timings).doesNotExist();
                timing = "";
            } else {
                timing = Files.readString(timings);
                assertThat(timing.lines().toList()).hasSize(1);
                final String status = expectedExit == 0 || mode.equals("probe-failure") ? "pass" : "fail";
                assertThat(timing).containsPattern("sanitizer_compile\\t\\d+\\t" + status + "\\tfalse\\t");
            }
            if (mode.equals("success") || mode.equals("failure")) {
                final ProcessResult measurement = process(root, List.of("sh", "-c", """
                    . .github/scripts/timing-report.sh
                    javan_timing_measure true
                    printf '%s' "$javan_timing_measure_source"
                    """), Duration.ofSeconds(20), Map.of());
                assertThat(measurement.exitCode()).isZero();
                if (!measurement.stdout().equals("unavailable")) {
                    assertThat(timing).containsPattern("\\t[0-9.]+\\t[0-9]+\\t(?:gnu|bsd)-time\\n");
                }
            }
            if (expectedExit == 0) {
                assertThat(run.stdout()).contains("ok - self-host sanitizer smoke passed");
                assertThat(run.stderr()).isEmpty();
                assertThat(Files.readAllLines(calls)).containsExactly("--version", "check", "report", "check", "build");
            } else {
                assertThat(run.stderr()).contains("compiler stdout retained", "compiler stderr retained")
                    .doesNotContain("No such file", "cannot open");
                if (mode.equals("probe-failure")) {
                    assertThat(Files.readAllLines(calls)).containsExactly("--version");
                    assertThat(run.stderr()).contains("probe failed");
                } else {
                    assertThat(calls).doesNotExist();
                }
            }
            if (mode.startsWith("resource-")) {
                assertThat(run.stdout().lines().filter(line -> line.startsWith("Resource sample:")).count())
                    .isGreaterThanOrEqualTo(2);
                assertThat(run.stdout()).contains(mode.equals("resource-unavailable")
                    ? "process_memory=unavailable" : "compiler-fixture");
                if (Files.isReadable(Path.of("/proc/meminfo"))) {
                    assertThat(run.stdout()).containsPattern("MemAvailable:\\s+\\d+ kB");
                } else {
                    assertThat(run.stdout()).contains("host_memory=unavailable");
                }
                if (Files.isReadable(Path.of("/proc/vmstat"))) {
                    assertThat(run.stdout()).containsPattern("oom_kill \\d+");
                }
                for (final String suffix : List.of("monitor-pids", "sleep-pids")) {
                    final List<String> pids = Files.readAllLines(root.resolve("probe-calls." + suffix));
                    assertThat(pids).hasSizeGreaterThanOrEqualTo(2);
                    for (final String pid : pids.stream().distinct().toList()) {
                        assertThat(ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false))
                            .as("resource observer process %s must be reaped", pid).isFalse();
                    }
                }
            }
            try (final Stream<Path> remaining = Files.list(temporary)) {
                assertThat(remaining.toList()).isEmpty();
            }
        }));
    }

    @Test
    void timingReporterWritesMachineAndHumanReadablePhaseComparisons() throws Exception {
        final Path runner = tempDir.resolve("timing-report-test.sh");
        final Path log = tempDir.resolve("timings.tsv");
        final Path json = tempDir.resolve("timings.json");
        final Path markdown = tempDir.resolve("timings.md");
        final Path gnuTime = tempDir.resolve("gnu-time.txt");
        Files.writeString(gnuTime, "Command exited with non-zero status 7\n0.12 0.34 1024\n");
        Files.writeString(runner, """
            set -eu
            . "$1"
            JAVAN_TIMING_LOG=$2
            export JAVAN_TIMING_LOG
            : > "$JAVAN_TIMING_LOG"
            javan_timing_run bootstrap_jvm true
            javan_timing_run bootstrap_gen2 true
            run_shell_function() { :; }
            javan_timing_run shell_function run_shell_function
            javan_timing_write_reports linux-x64 2 bootstrap "$3" "$4" pass 0
            javan_timing_parse_gnu "$5"
            printf 'GNU: cpu=%s rss=%s\\n' "$javan_timing_measure_cpu_seconds" "$javan_timing_measure_max_rss_bytes"
            """);

        final ProcessResult run = process(
            REPO_ROOT,
            List.of(
                "sh",
                runner.toString(),
                REPO_ROOT.resolve(".github/scripts/timing-report.sh").toString(),
                log.toString(),
                json.toString(),
                markdown.toString(),
                gnuTime.toString()
            ),
            Duration.ofSeconds(20),
            Map.of()
        );

        assertThat(run.exitCode()).as("stdout=%s stderr=%s", run.stdout(), run.stderr()).isZero();
        assertThat(run.stdout()).contains(
            "Timing: bootstrap_jvm=",
            "Timing: bootstrap_gen2=",
            "Timing: shell_function="
        ).containsPattern("Timing: shell_function=\\d+s status=pass counted=true cpu=unknown max_rss_bytes=unknown source=unavailable")
            .contains("GNU: cpu=0.460000 rss=1048576");
        final String jsonContent = Files.readString(json);
        assertThat(jsonContent)
            .contains("\"target\": \"linux-x64\"")
            .contains("\"schemaVersion\": 2")
            .contains("\"bootstrapGeneration\": 2")
            .contains("\"proofScope\": \"bootstrap\"")
            .contains("\"status\": \"pass\"")
            .contains("\"exitCode\": 0")
            .contains("\"availableProcessors\": \"")
            .contains("\"physicalMemoryBytes\": \"")
            .containsPattern("\\{\\\"name\\\": \\\"bootstrap_jvm\\\", \\\"seconds\\\": \\d+, \\\"status\\\": \\\"pass\\\", \\\"countedInTotal\\\": true, \\\"cpuSeconds\\\": \\\"[^\\\"]+\\\", \\\"maxRssBytes\\\": \\\"[^\\\"]+\\\", \\\"resourceSource\\\": \\\"[^\\\"]+\\\"}")
            .containsPattern("\\{\\\"name\\\": \\\"bootstrap_gen2\\\", \\\"seconds\\\": \\d+, \\\"status\\\": \\\"pass\\\", \\\"countedInTotal\\\": true, \\\"cpuSeconds\\\": \\\"[^\\\"]+\\\", \\\"maxRssBytes\\\": \\\"[^\\\"]+\\\", \\\"resourceSource\\\": \\\"[^\\\"]+\\\"}")
            .containsPattern("\\{\\\"name\\\": \\\"shell_function\\\", \\\"seconds\\\": \\d+, \\\"status\\\": \\\"pass\\\", \\\"countedInTotal\\\": true, \\\"cpuSeconds\\\": \\\"unknown\\\", \\\"maxRssBytes\\\": \\\"unknown\\\", \\\"resourceSource\\\": \\\"unavailable\\\"}")
            .containsPattern("\\\"totalSeconds\\\": \\d+");
        assertThat(Files.readString(markdown))
            .contains("| Phase | Seconds | CPU seconds | Peak RSS bytes | Resource source | Status | Total |")
            .containsPattern("\\| `bootstrap_jvm` \\| \\d+ \\| [^|]+ \\| [^|]+ \\| [^|]+ \\| pass \\| true \\|")
            .containsPattern("\\| `bootstrap_gen2` \\| \\d+ \\| [^|]+ \\| [^|]+ \\| [^|]+ \\| pass \\| true \\|")
            .containsPattern("\\| `shell_function` \\| \\d+ \\| unknown \\| unknown \\| unavailable \\| pass \\| true \\|")
            .containsPattern("\\| \\*\\*Total measured\\*\\* \\| \\*\\*\\d+\\*\\* \\| \\| \\| \\| \\*\\*pass\\*\\* \\| \\|");
        if (run.stdout().matches("(?s).*source=(?:gnu|bsd)-time.*")) {
            assertThat(jsonContent)
                .containsPattern("\\\"cpuSeconds\\\": \\\"\\d+(?:\\.\\d+)?\\\", \\\"maxRssBytes\\\": \\\"\\d+\\\", \\\"resourceSource\\\": \\\"(?:gnu|bsd)-time\\\"");
        } else {
            assertThat(jsonContent).contains("\"resourceSource\": \"unavailable\"");
        }
    }

    @Test
    void timingReporterPreservesFailureAndDoesNotDoubleCountNestedPhases() throws Exception {
        final Path runner = tempDir.resolve("timing-failure-test.sh");
        final Path log = tempDir.resolve("timings.tsv");
        final Path json = tempDir.resolve("timings.json");
        final Path markdown = tempDir.resolve("timings.md");
        Files.writeString(runner, """
            set -eu
            . "$1"
            JAVAN_TIMING_LOG=$2
            export JAVAN_TIMING_LOG
            printf 'package_sanitizer\\t5\\tpass\\ttrue\\n' > "$JAVAN_TIMING_LOG"
            printf 'sanitizer_compile\\t3\\tpass\\tfalse\\n' >> "$JAVAN_TIMING_LOG"
            fail_shell_function() { return 7; }
            set +e
            javan_timing_run failed_phase fail_shell_function
            code=$?
            set -e
            javan_timing_write_reports linux-x64 3 full "$3" "$4" fail "$code"
            exit "$code"
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

        assertThat(run.exitCode()).isEqualTo(7);
        final String jsonContent = Files.readString(json);
        assertThat(jsonContent)
            .contains("\"status\": \"fail\"")
            .contains("\"exitCode\": 7")
            .contains("{\"name\": \"sanitizer_compile\", \"seconds\": 3, \"status\": \"pass\", \"countedInTotal\": false, \"cpuSeconds\": \"unknown\", \"maxRssBytes\": \"unknown\", \"resourceSource\": \"unavailable\"}")
            .containsPattern("\\{\\\"name\\\": \\\"failed_phase\\\", \\\"seconds\\\": \\d+, \\\"status\\\": \\\"fail\\\", \\\"countedInTotal\\\": true, \\\"cpuSeconds\\\": \\\"[^\\\"]+\\\", \\\"maxRssBytes\\\": \\\"[^\\\"]+\\\", \\\"resourceSource\\\": \\\"[^\\\"]+\\\"}");
        final var failedSeconds = Pattern.compile("\\\"name\\\": \\\"failed_phase\\\", \\\"seconds\\\": (\\d+)")
            .matcher(jsonContent);
        final var totalSeconds = Pattern.compile("\\\"totalSeconds\\\": (\\d+)").matcher(jsonContent);
        assertThat(failedSeconds.find()).isTrue();
        assertThat(totalSeconds.find()).isTrue();
        assertThat(Long.parseLong(totalSeconds.group(1)))
            .isEqualTo(5L + Long.parseLong(failedSeconds.group(1)));
    }

    @Test
    void timingWorkflowComparesBothGenerationsOnEveryNativePackageRunner() throws Exception {
        assertThat(Files.readString(TIMINGS_WORKFLOW))
            .contains("workflow_dispatch:")
            .contains("fail-fast: false")
            .contains("max-parallel: 6")
            .contains("name: Prepare")
            .contains("name: \"🏷️ Version [date]\"")
            .contains("version=$(date -u +'%Y.%-m.%-d')")
            .contains("tee -a \"$GITHUB_OUTPUT\"")
            .contains("generation: 2, target: linux-x64, runner: ubuntu-24.04")
            .contains("generation: 3, target: linux-x64, runner: ubuntu-24.04")
            .contains("generation: 2, target: linux-aarch64, runner: ubuntu-24.04-arm")
            .contains("generation: 3, target: linux-aarch64, runner: ubuntu-24.04-arm")
            .contains("generation: 2, target: macos-aarch64, runner: macos-15")
            .contains("generation: 3, target: macos-aarch64, runner: macos-15")
            .contains("uses: ./.github/workflows/native-proof.yml")
            .contains("package_scope: bootstrap")
            .contains("upload_package: false")
            .contains("bootstrap_generation: ${{ matrix.generation }}")
            .contains("name: Summary")
            .contains("name: \"📥 Download [timings]\"")
            .contains("name: \"⏱️ Compare [gen2_gen3]\"")
            .contains("pattern: timings-*")
            .contains("# Native self-host comparison")
            .contains("| Target | Gen2 | Gen3 | Gen3 extra |");
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
            .contains(
                "if [ -f target/classes/javan/Main.class ]; then",
                "JAVAN_BUILD_REUSE_TARGET=true scripts/build.sh",
                "else\n  scripts/build.sh\nfi",
                "build target/classes --main javan.Main --jar --output javan-package-selfhost-jar"
            )
            .contains("Main-Class: javan.Main");
    }

    @Test
    void verifyCiPackageSmokeComposesPackagedAndBootstrapSelfHostProof() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);

        assertThat(script)
            .contains(
                "\"$PACKAGE_BIN\" check target/classes --main javan.Main",
                "JAVAN_SELF_HOST_REUSE_GENERATED=true"
            )
            .doesNotContain("build target/classes --main javan.Main --output javan-package-selfhost-smoke");
    }

    @Test
    void verifyCiPackageSmokeKeepsPackageBackedSelfHostSanitizerProof() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);

        assertThat(script)
            .contains("JAVAN_SELF_HOST_REUSE_GENERATED=true")
            .contains("sh .github/scripts/sanitizer-self-host-smoke.sh")
            .contains("sh .github/scripts/verify-showcase.sh")
            .contains("package-release-rehearsal.sh")
            .contains("rehearse-release-artifact.sh")
            .contains("\"kind\": \"self-host\"")
            .contains("\"actualLiveAllocations\": 0")
            .contains("\"actualLiveBytes\": 0");
        assertThat(script.indexOf("PACKAGE_BIN=$PACKAGE_ROOT/bin/javan"))
            .isLessThan(script.indexOf("run_package_showcase()"));
    }

    @Test
    void artifactOnlyPackageVerifierDoesNotReadTheSourceCheckout() throws Exception {
        final String script = Files.readString(REPO_ROOT.resolve(".github/scripts/verify-package.sh"));

        assertThat(script)
            .doesNotContain("REPO_ROOT")
            .doesNotContain("verify-showcase.sh")
            .contains("unsupported link entries");
    }

    @Test
    void packageNativeImportVerifierUsesOnlyTheMatchingPackagedCliAndConfiguredAbiFixtures() throws Exception {
        final String script = Files.readString(REPO_ROOT.resolve(VERIFY_PACKAGE_NATIVE_IMPORTS));
        final String fixture = Files.readString(REPO_ROOT.resolve("src/test/resources/projects/acceptance/native-imports/native/imports.c"));

        assertThat(script)
            .contains("javan_host_target", "Archive $ARCHIVE_NAME does not match host target $TARGET.")
            .contains("ARCHIVE_DIR=$(CDPATH= cd -- \"$(dirname -- \"$ARCHIVE\")\" && pwd)")
            .contains("(cd \"$ARCHIVE_DIR\" && verify_checksum \"$ARCHIVE_NAME.sha256\")")
            .contains("verify-package.sh", "native-imports", "native-import-invalid")
            .contains("\"$PACKAGE_BIN\" build \"$SUCCESS\"", "\"$PACKAGE_BIN\" check \"$INVALID\"")
            .contains("28:3:3:11:5", "error[JAVAN013]: native import ABI is not supported")
            .contains("Use only the supported native import ABI.");
        assertThat(fixture).contains("#include \"javan_runtime.h\"");
    }

    @Test
    void fullPackageProofRetainsReleaseRehearsalEvidence() throws Exception {
        final String workflow = Files.readString(NATIVE_PROOF);

        assertThat(workflow)
            .contains("- name: \"📤 Rehearsal")
            .contains("inputs.package_scope == 'full'")
            .contains(".rehearsal.json")
            .contains("-rehearsal.tar.gz");
    }

    @Test
    void fullPackageProofRunsConfiguredNativeImportsFromThePackageArchive() throws Exception {
        final String script = Files.readString(VERIFY_CI_PACKAGE_SMOKE);

        assertThat(script)
            .contains("package_imports", "verify-package-native-imports.sh \"$ARCHIVE\"")
            .contains("PACKAGE_BIN=$PACKAGE_ROOT/bin/javan");
        assertThat(script.indexOf("PACKAGE_BIN=$PACKAGE_ROOT/bin/javan"))
            .isLessThan(script.indexOf("package_imports"));
    }

    @Test
    void containerWorkflowBuildsFromReleasedLinuxPackages() throws Exception {
        final String containerWorkflow = Files.readString(CONTAINER_WORKFLOW);

        assertThat(containerWorkflow)
            .contains("- name: \"📥 Download")
            .contains("gh release download")
            .contains(
                "archive-target: x64",
                "archive-target: aarch64",
                "archive=\"javan-${RELEASE_VERSION}-linux-${ARCHIVE_TARGET}.tar.gz\""
            );
    }

    @Test
    void containerWorkflowKeepsDefaultImageShowcaseVerification() throws Exception {
        final String containerWorkflow = Files.readString(CONTAINER_WORKFLOW);

        assertThat(containerWorkflow)
            .contains("- name: \"🧪 Showcase")
            .contains("JAVAN_IMAGE=\"$image\" sh .github/scripts/verify-showcase.sh");
    }

    @Test
    void containerExtractionVerifiesArchiveChecksumsBeforeUnpacking() throws Exception {
        for (final Path dockerfile : List.of(
            Path.of("packaging/containers/javan-wolfi.Dockerfile"),
            Path.of("packaging/containers/javan-distroless.Dockerfile"),
            Path.of("packaging/containers/javan-scratch.Dockerfile")
        )) {
            final String source = Files.readString(dockerfile);
            assertThat(source).contains("$archive.sha256", "sha256sum -c");
            assertThat(source.indexOf("sha256sum -c")).isLessThan(source.indexOf("tar -xzf \"$archive\""));
        }
    }

    @Test
    void imageShowcaseVerificationUsesOnlyCompiledTemporaryInput() throws Exception {
        final String script = Files.readString(Path.of(".github/scripts/verify-showcase.sh"));
        assertThat(script).contains("-v \"$project:/workspace\"", "check classes --main com.acme.showcase.Main", "report . >/dev/null", "SHOWCASE_REPORT=$project/.javan/reports/report.json")
            .doesNotContain("-v \"$ROOT:/workspace\"", "rm -rf \"$SHOWCASE_ROOT/target\"");
    }

    private static Stream<String> publicReleaseAssets() {
        return Stream.concat(Stream.of("dist/release/javan.rb"),
            Stream.of("linux-x64", "linux-aarch64", "macos-aarch64").flatMap(target -> {
                final String archive = "dist/release/javan-" + UPLOAD_VERSION + "-" + target + ".tar.gz";
                return Stream.of(archive, archive + ".sha256");
            }));
    }

    private Path prepareReleaseUpload() throws Exception {
        final Path root = Files.createTempDirectory(tempDir, "release fixture ");
        final Path bin = Files.createDirectories(root.resolve("bin"));
        Files.createDirectories(root.resolve("dist/release"));
        Files.createDirectories(root.resolve("gh-recordings"));
        for (final String asset : publicReleaseAssets().toList()) {
            Files.writeString(root.resolve(asset), "verified fixture " + asset);
        }
        writeExecutableScript(bin.resolve("gh"), """
            #!/bin/sh
            set -eu
            [ "$1" = release ] || exit 97
            command=$2
            shift 2
            printf '%s\\n' "$command" >> gh-recordings/commands
            printf '%s\\n' "$@" > "gh-recordings/$command"
            [ ! -f "gh-recordings/fail-$command" ] || exit 73
            case "$command" in
              view) test -f gh-recordings/exists ;;
              create) touch gh-recordings/exists ;;
              upload) : ;;
              *) exit 98 ;;
            esac
            """);
        return root;
    }

    private static ProcessResult runReleaseUpload(final Path root) throws Exception {
        final String workflow = Files.readString(RELEASE_WORKFLOW);
        final int publishStart = workflow.indexOf("      - name: \"🚀 Publish");
        assertThat(publishStart).as("release publication step").isNotNegative();
        final String step = workflow.substring(publishStart);
        final String runBlock = "        run: |\n";
        final int runStart = step.indexOf(runBlock);
        assertThat(runStart).as("executable publication body").isNotNegative();
        final String script = step.substring(runStart + runBlock.length()).lines()
            .takeWhile(line -> line.isBlank() || line.startsWith("          "))
            .map(line -> line.isBlank() ? "" : line.substring(10))
            .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(script).contains("gh release upload");
        return process(root, List.of("sh", "-c", script), Duration.ofSeconds(20), Map.of(
            "PATH", root.resolve("bin") + java.io.File.pathSeparator + System.getenv("PATH"),
            "GH_TOKEN", "", "GITHUB_TOKEN", "", "GH_REPO", "fixture/repository",
            "RELEASE_VERSION", UPLOAD_VERSION, "TARGET_SHA", "7".repeat(40)
        ));
    }

    private static void writeReleaseArtifact(final Path releaseDir, final String name, final String content) throws Exception {
        final Path archive = releaseDir.resolve(name);
        Files.writeString(archive, content, StandardCharsets.UTF_8);
        writeReleaseArtifactChecksum(archive);
    }

    private static void writeReleaseArtifactChecksum(final Path archive) throws Exception {
        final String sha256 = sha256Hex(Files.readAllBytes(archive));
        Files.writeString(
            archive.resolveSibling(archive.getFileName() + ".sha256"),
            sha256 + "  " + archive.getFileName() + "\n",
            StandardCharsets.UTF_8
        );
    }

    private static void writeDockerManifestStub(final Path bin) throws Exception {
        writeDockerManifestStub(bin, """
            {"manifests":[
              {"platform":{"architecture":"amd64","os":"linux"}},
              {"platform":{"architecture":"arm64","os":"linux"}}
            ]}
            """);
    }

    private static void writeDockerManifestStub(final Path bin, final String manifest) throws Exception {
        final Path docker = bin.resolve("docker");
        final String script = """
            #!/bin/sh
            if [ "$5" = "--raw" ]; then
              printf '%s\\n' '__JAVAN_MANIFEST__'
              exit 0
            fi
            printf '%s\\n' 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
            """;
        Files.writeString(docker, script.replace("__JAVAN_MANIFEST__", manifest.replace("\n", "")), StandardCharsets.UTF_8);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
    }

    private static String hostTarget() {
        final String os = System.getProperty("os.name").toLowerCase();
        final String arch = System.getProperty("os.arch").toLowerCase();
        if (os.contains("mac") && (arch.equals("aarch64") || arch.equals("arm64"))) {
            return "macos-aarch64";
        }
        if (os.contains("linux") && (arch.equals("aarch64") || arch.equals("arm64"))) {
            return "linux-aarch64";
        }
        if (os.contains("linux") && (arch.equals("x86_64") || arch.equals("amd64"))) {
            return "linux-x64";
        }
        throw new IllegalStateException("Unsupported first-release test host: " + os + '-' + arch);
    }

    private static String sha256Hex(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
