package javan.reporting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Isolated
final class RuntimeFootprintReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeReportsHostFootprintMatrix() throws Exception {
        final Path binary = tempDir.resolve(".javan/bin/demo");
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "binary\n");

        final RuntimeFootprintReports.Report report = new RuntimeFootprintReports().write(
            tempDir.resolve(".javan"),
            "app",
            List.of(binary),
            Optional.empty(),
            "core",
            false
        );

        final String json = Files.readString(report.jsonPath());
        final String markdown = Files.readString(report.markdownPath());
        assertThat(json).contains(
            "\"artifactKind\": \"app\"",
            "\"hostTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"requestedTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"actualTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"crossCompilation\": false",
            "\"name\": \"system-linked\"",
            "\"status\": \"verified-host\"",
            "\"name\": \"self-contained\"",
            "\"status\": \"not-implemented\"",
            "\"target\": \"linux-x64\"",
            "\"target\": \"linux-aarch64\"",
            "\"target\": \"macos-aarch64\"",
            "\"target\": \"macos-x64\"",
            "\"target\": \"windows-x64\""
        );
        assertThat(markdown).contains(
            "Runtime Footprint",
            "host target: `" + RuntimeFootprintReports.hostTarget() + "`",
            "`system-linked`",
            "`self-contained`",
            "`ubuntu-24.04-arm`",
            "`windows-2025`"
        );
        assertThat(report.artifacts().getFirst().bytes()).isEqualTo(Files.size(binary));
    }

    @Test
    void normalizeTargetMapsCommonLinuxArmTriple() {
        assertThat(RuntimeFootprintReports.normalizeTarget("aarch64-unknown-linux-gnu")).isEqualTo("linux-aarch64");
    }

    @Test
    void normalizeTargetMapsCommonMacArmTriple() {
        assertThat(RuntimeFootprintReports.normalizeTarget("arm64-apple-darwin")).isEqualTo("macos-aarch64");
    }

    @Test
    void normalizeTargetMapsWindowsX64Triple() {
        assertThat(RuntimeFootprintReports.normalizeTarget("x86_64-pc-windows-msvc")).isEqualTo("windows-x64");
    }

    @Test
    void normalizeTargetLeavesUnknownTripleStable() {
        assertThat(RuntimeFootprintReports.normalizeTarget("riscv64-unknown-plan9")).isEqualTo("riscv64-unknown-plan9");
    }

    @Test
    void hostTargetMapsLinuxAmd64() {
        withHostProperties("Linux", "amd64", () ->
            assertThat(RuntimeFootprintReports.hostTarget()).isEqualTo("linux-x64")
        );
    }

    @Test
    void hostTargetMapsWindowsArm64() {
        withHostProperties("Windows 11", "arm64", () ->
            assertThat(RuntimeFootprintReports.hostTarget()).isEqualTo("windows-aarch64")
        );
    }

    @Test
    void hostTargetKeepsUnknownHostStable() {
        withHostProperties("Haiku OS", "riscv64", () ->
            assertThat(RuntimeFootprintReports.hostTarget()).isEqualTo("haiku-os-riscv64")
        );
    }

    @Test
    void writeReportsRequestedReleaseAndMissingArtifact() throws Exception {
        final Path missing = tempDir.resolve(".javan/bin/missing");
        final RuntimeFootprintReports.Report report = new RuntimeFootprintReports().write(
            tempDir.resolve(".javan"),
            "app",
            List.of(missing),
            Optional.of(RuntimeFootprintReports.hostTarget()),
            "strict",
            true
        );

        final String json = Files.readString(report.jsonPath());
        assertThat(json).contains(
            "\"requestedTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"release\": true",
            "\"bytes\": 0",
            "\"status\": \"accepted-conservative\""
        );
    }

    @Test
    void requireHostTargetAcceptsCurrentHostTarget() {
        RuntimeFootprintReports.requireHostTarget(Optional.of(RuntimeFootprintReports.hostTarget()));
    }

    @Test
    void requireHostTargetRejectsCrossTarget() {
        final String crossTarget = RuntimeFootprintReports.hostTarget().startsWith("linux-") ? "macos-aarch64" : "linux-x64";

        assertThatThrownBy(() -> RuntimeFootprintReports.requireHostTarget(Optional.of(crossTarget)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cross-target native linking is not implemented")
            .hasMessageContaining("host is " + RuntimeFootprintReports.hostTarget());
    }

    private static void withHostProperties(final String os, final String arch, final Runnable assertion) {
        final String originalOs = System.getProperty("os.name");
        final String originalArch = System.getProperty("os.arch");
        System.setProperty("os.name", os);
        System.setProperty("os.arch", arch);
        try {
            assertion.run();
        } finally {
            System.setProperty("os.name", originalOs);
            System.setProperty("os.arch", originalArch);
        }
    }
}
