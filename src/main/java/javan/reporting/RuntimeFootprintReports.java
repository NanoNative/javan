package javan.reporting;

import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Writes deterministic runtime footprint and OS/architecture coverage reports.
 */
public final class RuntimeFootprintReports {
    private static final List<MatrixTarget> MATRIX_TARGETS = List.of(
        new MatrixTarget("linux-x64", "ubuntu-24.04", "required-ci", "host-native Linux x64 app/library acceptance"),
        new MatrixTarget("linux-aarch64", "ubuntu-24.04-arm", "required-ci", "host-native Linux arm64 app/library acceptance"),
        new MatrixTarget("macos-aarch64", "macos-15", "required-ci", "host-native macOS arm64 app/library acceptance"),
        new MatrixTarget("macos-x64", "macos-15-intel", "required-ci", "host-native macOS x64 app/library acceptance"),
        new MatrixTarget("windows-x64", "windows-2025", "planned-runtime-port", "Windows linker/runtime support is not implemented"),
        new MatrixTarget("windows-aarch64", "windows-11-arm", "planned-runtime-port", "Windows arm64 linker/runtime support is not implemented")
    );

    /**
     * Fails when a requested target would silently produce a host binary.
     *
     * @param requestedTarget requested target triple
     */
    public static void requireHostTarget(final Optional<String> requestedTarget) {
        if (requestedTarget.isEmpty()) {
            return;
        }
        final String requested = requestedTarget.orElseThrow();
        final String normalized = normalizeTarget(requested);
        final String host = hostTarget();
        if (!host.equals(normalized)) {
            throw new IllegalArgumentException(
                "Cross-target native linking is not implemented: requested "
                    + requested
                    + " ("
                    + normalized
                    + "), host is "
                    + host
                    + ". Build on that OS/architecture runner or leave --target unset."
            );
        }
    }

    /**
     * Returns the canonical target for the current host.
     *
     * @return host target
     */
    public static String hostTarget() {
        return normalizedOs(System.getProperty("os.name", "")) + "-" + normalizedArch(System.getProperty("os.arch", ""));
    }

    /**
     * Normalizes common target triple spellings into {@code os-arch}.
     *
     * @param value target triple value
     * @return normalized target
     */
    public static String normalizeTarget(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiLowerCase(value), '_', '-');
        final String os = targetOs(normalized);
        final String arch = targetArch(normalized);
        if (!Strings2.isBlank(os) && !Strings2.isBlank(arch)) {
            return os + "-" + arch;
        }
        return normalized;
    }

    /**
     * Writes footprint reports.
     *
     * @param outputDirectory javan output directory
     * @param artifactKind artifact kind
     * @param artifacts generated artifacts
     * @param requestedTarget requested target triple
     * @param profile selected profile name
     * @param release release flag
     * @return written report
     * @throws IOException when writing fails
     */
    public Report write(
        final Path outputDirectory,
        final String artifactKind,
        final List<Path> artifacts,
        final Optional<String> requestedTarget,
        final String profile,
        final boolean release
    ) throws IOException {
        final String host = hostTarget();
        final String requested = requestedTarget.isPresent() ? normalizeTarget(requestedTarget.orElseThrow()) : host;
        final List<ArtifactFootprint> artifactFootprints = artifacts(artifacts);
        final List<Footprint> footprints = footprints(release);
        final List<TargetCoverage> targets = targetCoverage(host);
        final Path reportsDirectory = outputDirectory.resolve("reports");
        final Path jsonPath = reportsDirectory.resolve("runtime-footprint.json");
        final Path markdownPath = reportsDirectory.resolve("runtime-footprint.md");
        final String json = json(artifactKind, host, requested, profile, release, artifactFootprints, footprints, targets);
        final String markdown = markdown(artifactKind, host, requested, profile, release, artifactFootprints, footprints, targets);
        Files2.writeString(jsonPath, json);
        Files2.writeString(markdownPath, markdown);
        return new Report(jsonPath, markdownPath, host, requested, artifactFootprints, footprints, targets);
    }

    private static List<ArtifactFootprint> artifacts(final List<Path> artifacts) throws IOException {
        final List<ArtifactFootprint> result = new ArrayList<>();
        for (final Path artifact : artifacts) {
            result.add(new ArtifactFootprint(artifact, Files.isRegularFile(artifact) ? Files.size(artifact) : 0L));
        }
        return List.copyOf(result);
    }

    private static List<Footprint> footprints(final boolean release) {
        return List.of(
            new Footprint("system-linked", true, true, "verified-host", "uses host C compiler and system runtime libraries"),
            new Footprint("self-contained", false, false, "not-implemented", "static/self-contained packaging is a release gate"),
            new Footprint("release-conservative", release, release, release ? "accepted-conservative" : "not-requested",
                "release flag is accepted; size/speed specialization remains conservative"),
            new Footprint("debug-symbols", false, false, "not-requested", "native debug symbol policy is not implemented"),
            new Footprint("live-profiling", false, false, "not-linked", "profiling hooks are not linked by default"),
            new Footprint("sanitizer-instrumented", false, false, "external-smoke", "sanitizer smoke uses .github/scripts/sanitizer-smoke.sh")
        );
    }

    private static List<TargetCoverage> targetCoverage(final String host) {
        final List<TargetCoverage> result = new ArrayList<>();
        for (final MatrixTarget target : MATRIX_TARGETS) {
            if (target.target().equals(host)) {
                result.add(new TargetCoverage(target.target(), target.runner(), "verified-host", true, target.notes()));
            } else {
                result.add(new TargetCoverage(target.target(), target.runner(), target.status(), false, target.notes()));
            }
        }
        return List.copyOf(result);
    }

    private static String json(
        final String artifactKind,
        final String host,
        final String requested,
        final String profile,
        final boolean release,
        final List<ArtifactFootprint> artifacts,
        final List<Footprint> footprints,
        final List<TargetCoverage> targets
    ) {
        final StringBuilder result = new StringBuilder();
        result.append("{\n");
        field(result, "schemaVersion", Json.string("1"), true);
        field(result, "artifactKind", Json.string(artifactKind), true);
        field(result, "hostTarget", Json.string(host), true);
        field(result, "requestedTarget", Json.string(requested), true);
        field(result, "actualTarget", Json.string(host), true);
        field(result, "crossCompilation", "false", true);
        field(result, "profile", Json.string(profile), true);
        field(result, "release", bool(release), true);
        field(result, "artifacts", artifactsJson(artifacts), true);
        field(result, "footprints", footprintsJson(footprints), true);
        field(result, "osArchCoverage", targetsJson(targets), false);
        result.append("}\n");
        return result.toString();
    }

    private static String markdown(
        final String artifactKind,
        final String host,
        final String requested,
        final String profile,
        final boolean release,
        final List<ArtifactFootprint> artifacts,
        final List<Footprint> footprints,
        final List<TargetCoverage> targets
    ) {
        final StringBuilder result = new StringBuilder();
        result.append("# Runtime Footprint").append('\n').append('\n');
        result.append("- artifact kind: `").append(artifactKind).append("`\n");
        result.append("- host target: `").append(host).append("`\n");
        result.append("- requested target: `").append(requested).append("`\n");
        result.append("- actual target: `").append(host).append("`\n");
        result.append("- cross compilation: `false`\n");
        result.append("- profile: `").append(profile).append("`\n");
        result.append("- release: `").append(release).append("`\n\n");
        result.append("| artifact | bytes |\n");
        result.append("| --- | ---: |\n");
        for (final ArtifactFootprint artifact : artifacts) {
            result.append("| `").append(artifact.path()).append("` | ").append(artifact.bytes()).append(" |\n");
        }
        result.append("\n| footprint | selected | tested | status | notes |\n");
        result.append("| --- | --- | --- | --- | --- |\n");
        for (final Footprint footprint : footprints) {
            result.append("| `").append(footprint.name()).append("` | `")
                .append(footprint.selected()).append("` | `")
                .append(footprint.tested()).append("` | `")
                .append(footprint.status()).append("` | ")
                .append(footprint.notes()).append(" |\n");
        }
        result.append("\n| target | runner | status | tested here | notes |\n");
        result.append("| --- | --- | --- | --- | --- |\n");
        for (final TargetCoverage target : targets) {
            result.append("| `").append(target.target()).append("` | `")
                .append(target.runner()).append("` | `")
                .append(target.status()).append("` | `")
                .append(target.tested()).append("` | ")
                .append(target.notes()).append(" |\n");
        }
        return result.toString();
    }

    private static String artifactsJson(final List<ArtifactFootprint> artifacts) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < artifacts.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final ArtifactFootprint artifact = artifacts.get(index);
            result.append("{\"path\": ").append(Json.string(artifact.path().toString()))
                .append(", \"bytes\": ").append(artifact.bytes())
                .append("}");
        }
        return result.append("]").toString();
    }

    private static String footprintsJson(final List<Footprint> footprints) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < footprints.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final Footprint footprint = footprints.get(index);
            result.append("{\"name\": ").append(Json.string(footprint.name()))
                .append(", \"selected\": ").append(footprint.selected())
                .append(", \"tested\": ").append(footprint.tested())
                .append(", \"status\": ").append(Json.string(footprint.status()))
                .append(", \"notes\": ").append(Json.string(footprint.notes()))
                .append("}");
        }
        return result.append("]").toString();
    }

    private static String targetsJson(final List<TargetCoverage> targets) {
        final StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < targets.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            final TargetCoverage target = targets.get(index);
            result.append("{\"target\": ").append(Json.string(target.target()))
                .append(", \"runner\": ").append(Json.string(target.runner()))
                .append(", \"status\": ").append(Json.string(target.status()))
                .append(", \"tested\": ").append(target.tested())
                .append(", \"notes\": ").append(Json.string(target.notes()))
                .append("}");
        }
        return result.append("]").toString();
    }

    private static String targetOs(final String value) {
        if (value.contains("linux")) {
            return "linux";
        }
        if (value.contains("darwin") || value.contains("macos") || value.contains("mac-os") || value.contains("osx")) {
            return "macos";
        }
        if (value.contains("windows") || value.contains("mingw") || value.contains("win32") || value.contains("win64")) {
            return "windows";
        }
        return "";
    }

    private static String targetArch(final String value) {
        if (value.contains("aarch64") || value.contains("arm64")) {
            return "aarch64";
        }
        if (value.contains("x86-64") || value.contains("x86_64") || value.contains("amd64") || value.contains("x64")) {
            return "x64";
        }
        return "";
    }

    private static String normalizedOs(final String value) {
        final String normalized = Strings2.toAsciiLowerCase(value);
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return "macos";
        }
        if (normalized.contains("linux")) {
            return "linux";
        }
        if (normalized.contains("windows")) {
            return "windows";
        }
        return Strings2.replaceChar(normalized, ' ', '-');
    }

    private static String normalizedArch(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiLowerCase(value), '_', '-');
        if ("aarch64".equals(normalized) || "arm64".equals(normalized)) {
            return "aarch64";
        }
        if ("x86-64".equals(normalized) || "amd64".equals(normalized) || "x64".equals(normalized)) {
            return "x64";
        }
        return normalized;
    }

    private static void field(final StringBuilder result, final String name, final String value, final boolean comma) {
        result.append("  \"").append(name).append("\": ").append(value);
        if (comma) {
            result.append(',');
        }
        result.append('\n');
    }

    private static String bool(final boolean value) {
        return value ? "true" : "false";
    }

    /**
     * Written footprint report.
     *
     * @param jsonPath JSON report path
     * @param markdownPath Markdown report path
     * @param hostTarget host target
     * @param requestedTarget requested target
     * @param artifacts artifact rows
     * @param footprints footprint rows
     * @param targets OS/architecture coverage rows
     */
    public record Report(
        Path jsonPath,
        Path markdownPath,
        String hostTarget,
        String requestedTarget,
        List<ArtifactFootprint> artifacts,
        List<Footprint> footprints,
        List<TargetCoverage> targets
    ) {
    }

    /**
     * Artifact footprint row.
     *
     * @param path artifact path
     * @param bytes artifact size
     */
    public record ArtifactFootprint(Path path, long bytes) {
    }

    /**
     * Runtime footprint row.
     *
     * @param name footprint name
     * @param selected selected in this build
     * @param tested tested in this build
     * @param status support status
     * @param notes short notes
     */
    public record Footprint(String name, boolean selected, boolean tested, String status, String notes) {
    }

    /**
     * OS/architecture coverage row.
     *
     * @param target target
     * @param runner CI runner
     * @param status support status
     * @param tested tested in this build
     * @param notes short notes
     */
    public record TargetCoverage(String target, String runner, String status, boolean tested, String notes) {
    }

    private record MatrixTarget(String target, String runner, String status, String notes) {
    }
}
