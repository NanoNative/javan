package javan;

import javan.compat.ClassMetadata;
import javan.compat.CompatibilityReports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class ExternalProbeIsolationTest {
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path SCRIPT_SOURCES = Path.of(".github/scripts");
    private static final Path DOC_STATUS = Path.of("doc/status");
    private static final Path TEST_RESOURCES = Path.of("src/test/resources");
    private static final Path EXTERNAL_PROBES = TEST_RESOURCES.resolve("external-probes");
    private static final Path EXTERNAL_ARTIFACTS = TEST_RESOURCES.resolve("external-artifacts");
    private static final Path PUBLIC_EXAMPLE = Path.of("example");
    private static final Path SUPPORT_MATRIX = DOC_STATUS.resolve("support-matrix.md");
    private static final Path SUPPORT_MATRIX_JSON = DOC_STATUS.resolve("support-matrix.json");
    private static final Path JDK_COMPATIBILITY = DOC_STATUS.resolve("jdk-compatibility.md");
    private static final Path ROADMAP_PROGRESS = DOC_STATUS.resolve("roadmap-progress.md");

    @TempDir
    private Path tempDir;

    @Test
    void productionSourcesStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(PRODUCTION_SOURCES, List.of());
    }

    @Test
    void testSourcesStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(TEST_SOURCES, List.of());
    }

    @Test
    void workflowScriptsStayIndependentOfExternalProbeIdentities() throws Exception {
        try (Stream<Path> files = Files.walk(SCRIPT_SOURCES)) {
            final List<Path> scripts = files
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : scripts) {
                assertTextExcludesExternalProbeIdentities(Files.readString(file), file);
            }
        }
    }

    @Test
    void compilerOwnedProjectsTreeDoesNotContainExternalProbeRoot() {
        assertThat(TEST_RESOURCES.resolve("projects/external-probes"))
            .as("external probes must stay outside the compiler-owned test project tree")
            .doesNotExist();
        assertThat(EXTERNAL_PROBES)
            .as("dedicated external probe root must exist")
            .isDirectory();
        assertThat(EXTERNAL_ARTIFACTS)
            .as("dedicated bundled external artifact root must exist")
            .isDirectory();
    }

    @Test
    void onlyDedicatedExternalSmokeResourcesMayReferenceExternalProbeIdentities() throws Exception {
        final Set<Path> allowedRoots = Set.of(EXTERNAL_PROBES, EXTERNAL_ARTIFACTS);
        try (Stream<Path> files = Files.walk(TEST_RESOURCES)) {
            final List<Path> resourceFiles = files
                .filter(Files::isRegularFile)
                .filter(file -> allowedRoots.stream().noneMatch(file::startsWith))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : resourceFiles) {
                assertTextExcludesExternalProbeIdentitiesIfText(file);
            }
        }
    }

    @Test
    void publicExampleStaysIndependentOfExternalProbeIdentities() throws Exception {
        try (Stream<Path> files = Files.walk(PUBLIC_EXAMPLE)) {
            final List<Path> exampleFiles = files
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : exampleFiles) {
                assertTextExcludesExternalProbeIdentitiesIfText(file);
            }
        }
    }

    @Test
    void onlyDedicatedExternalSmokeDocsMayNameExternalProbeIdentities() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("doc"))) {
            final List<Path> markdownFiles = files
                .filter(path -> path.toString().endsWith(".md"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : markdownFiles) {
                assertTextExcludesExternalProbeIdentities(Files.readString(file), file);
            }
        }
    }

    @Test
    void onlyDedicatedExternalSmokeDocsMayReferenceProbeInfrastructure() throws Exception {
        final Set<Path> allowedDocs = Set.of(
            Path.of("doc/status/real-project-readiness.md"),
            Path.of("doc/spec/examples-and-test-projects.md"),
            Path.of("src/test/resources/projects/README.md")
        );
        final List<Path> scanned = new ArrayList<>();
        scanned.addAll(markdownFiles(Path.of("doc")));
        scanned.add(TEST_RESOURCES.resolve("projects/README.md"));
        scanned.add(SUPPORT_MATRIX_JSON);
        for (final Path file : scanned) {
            final String content = Files.readString(file);
            if (allowedDocs.contains(file)) {
                assertThat(content)
                    .as(file + " should remain the dedicated place that documents external probe infrastructure")
                    .contains("external-probes");
                continue;
            }
            assertThat(content)
                .as(file + " should not reference the dedicated external probe directory")
                .doesNotContain("external-probes");
            assertThat(content)
                .as(file + " should not reference probe metadata files")
                .doesNotContain("probe.properties");
            assertThat(content)
                .as(file + " should not reference the shared external probe build helper")
                .doesNotContain("build-external-probe.sh");
        }
    }

    @Test
    void coreStatusDocsStayIndependentOfExternalProbeIdentities() throws Exception {
        assertTextExcludesExternalProbeIdentities(Files.readString(SUPPORT_MATRIX), SUPPORT_MATRIX);
        assertTextExcludesExternalProbeIdentities(Files.readString(SUPPORT_MATRIX_JSON), SUPPORT_MATRIX_JSON);
        assertTextExcludesExternalProbeIdentities(Files.readString(JDK_COMPATIBILITY), JDK_COMPATIBILITY);
        assertTextExcludesExternalProbeIdentities(Files.readString(ROADMAP_PROGRESS), ROADMAP_PROGRESS);
    }

    @Test
    void coreStatusDocsStayIndependentOfExternalProbeProjectLabels() throws Exception {
        assertTextExcludesExternalProbeProjectLabels(Files.readString(SUPPORT_MATRIX), SUPPORT_MATRIX);
        assertTextExcludesExternalProbeProjectLabels(Files.readString(SUPPORT_MATRIX_JSON), SUPPORT_MATRIX_JSON);
        assertTextExcludesExternalProbeProjectLabels(Files.readString(JDK_COMPATIBILITY), JDK_COMPATIBILITY);
        assertTextExcludesExternalProbeProjectLabels(Files.readString(ROADMAP_PROGRESS), ROADMAP_PROGRESS);
    }

    @Test
    void supportRowsStayCompilerOwnedAndFreeOfSmokeBoundaryVocabulary() throws Exception {
        final List<String> forbiddenFragments = List.of("probe", "artifact", "external", "example");
        for (final String feature : supportMatrixFeatures(Files.readString(SUPPORT_MATRIX_JSON))) {
            final String normalized = feature.toLowerCase(java.util.Locale.ROOT);
            for (final String forbiddenFragment : forbiddenFragments) {
                assertThat(normalized)
                    .as("support row " + feature + " must stay JDK/runtime-shaped, not smoke-boundary-shaped")
                    .doesNotContain(forbiddenFragment);
            }
        }
    }

    @Test
    void milestoneHistoryStaysIndependentOfExternalProbeIdentities() throws Exception {
        final String roadmap = Files.readString(ROADMAP_PROGRESS);
        final int start = roadmap.indexOf("## Recent Milestones");
        final int end = roadmap.indexOf("## Honest Targets Today");
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(start);
        assertTextExcludesExternalProbeIdentities(roadmap.substring(start, end), ROADMAP_PROGRESS);
    }

    @Test
    void generatedCompatibilityOutputsStayIndependentOfExternalProbeIdentities() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(minimalClass("", "com/acme/Main")),
            List.of(minimalClass("java.base", "java/lang/Object")),
            List.of()
        );

        assertTextExcludesExternalProbeIdentities(Files.readString(tempDir.resolve("doc/status/support-matrix.md")), SUPPORT_MATRIX);
        assertTextExcludesExternalProbeIdentities(Files.readString(tempDir.resolve("doc/status/support-matrix.json")), SUPPORT_MATRIX_JSON);
        assertTextExcludesExternalProbeIdentities(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md")), JDK_COMPATIBILITY);
    }

    @Test
    void generatedCompatibilityOutputsStayIndependentOfExternalProbeProjectLabels() throws Exception {
        new CompatibilityReports().write(
            tempDir,
            tempDir.resolve(".javan"),
            List.of(minimalClass("", "com/acme/Main")),
            List.of(minimalClass("java.base", "java/lang/Object")),
            List.of()
        );

        assertTextExcludesExternalProbeProjectLabels(Files.readString(tempDir.resolve("doc/status/support-matrix.md")), SUPPORT_MATRIX);
        assertTextExcludesExternalProbeProjectLabels(Files.readString(tempDir.resolve("doc/status/support-matrix.json")), SUPPORT_MATRIX_JSON);
        assertTextExcludesExternalProbeProjectLabels(Files.readString(tempDir.resolve("doc/status/jdk-compatibility.md")), JDK_COMPATIBILITY);
    }

    @Test
    void externalProbeMetadataLoadsFromDedicatedSmokeProjects() throws Exception {
        assertThat(ExternalProbeIdentities.projectNames())
            .isNotEmpty()
            .doesNotHaveDuplicates()
            .allSatisfy(project -> assertThat(project).isNotBlank());
    }

    @Test
    void shortPlainArtifactIdsStillProduceForbiddenIdentityPatterns() throws Exception {
        final List<ExternalProbeCatalog.ExternalProbe> probes = ExternalProbeCatalog.realProbes();
        assertThat(probes).isNotEmpty();
        final String sample = probes.get(0).artifactId();
        assertThat(ExternalProbeIdentities.identityPatterns())
            .anySatisfy(pattern -> assertThat(pattern.matcher(sample).find()).isTrue());
    }

    @Test
    void externalProbeIdentityPatternsIncludeOptionalMetadataAliases() throws Exception {
        final Path probesRoot = tempDir.resolve("external-probes");
        final Path probeDirectory = probesRoot.resolve("artifact-smoke");
        Files.createDirectories(probeDirectory.resolve("src/main/java/com/acme"));
        Files.writeString(probeDirectory.resolve("probe.properties"), """
            project=artifact-smoke
            groupId=com.example
            artifactId=example-lib
            version=1.0.0
            mainClass=com.acme.Main
            genericEvidence=CliDependencyProjectIntegrationTest#dependencyJarStaticIntMethodBuilds
            identityAliases=upstream/service-alpha,service-alpha,ServiceAlpha
            identityPackages=com.example.lib
            """);
        Files.writeString(probeDirectory.resolve("expected.stdout"), "ok\n");
        Files.writeString(probeDirectory.resolve("src/main/java/com/acme/Main.java"), """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }
            }
            """);

        final List<Pattern> patterns = ExternalProbeCatalog.identityPatterns(probesRoot);

        assertThat(patterns)
            .anySatisfy(pattern -> assertThat(pattern.matcher("upstream/service-alpha").find()).isTrue())
            .anySatisfy(pattern -> assertThat(pattern.matcher("service-alpha").find()).isTrue())
            .anySatisfy(pattern -> assertThat(pattern.matcher("ServiceAlpha").find()).isTrue());
    }

    @Test
    void externalProbeProjectLabelsStayGenericAndProjectNeutral() throws Exception {
        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            assertThat(probe.project())
                .as(probe.projectDirectory() + " must keep a generic artifact-smoke label")
                .startsWith("artifact-");
            assertThat(probe.project().toLowerCase(java.util.Locale.ROOT))
                .as(probe.projectDirectory() + " project label must stay free of upstream group and artifact names")
                .doesNotContain(probe.groupId().toLowerCase(java.util.Locale.ROOT))
                .doesNotContain(probe.artifactId().toLowerCase(java.util.Locale.ROOT));
            for (final String identityPackage : probe.identityPackages()) {
                assertThat(probe.project().toLowerCase(java.util.Locale.ROOT))
                    .as(probe.projectDirectory() + " project label must stay free of upstream package names")
                    .doesNotContain(identityPackage.toLowerCase(java.util.Locale.ROOT))
                    .doesNotContain(identityPackage.replace('.', '/').toLowerCase(java.util.Locale.ROOT))
                    .doesNotContain(identityPackage.replace(".", "").toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    @Test
    void externalProbeGenericEvidenceTargetsStayProjectNeutral() throws Exception {
        final Path genericCoverageSource = TEST_SOURCES.resolve("javan/CliDependencyProjectIntegrationTest.java");
        final String genericCoverage = Files.readString(genericCoverageSource);

        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            final String[] parts = probe.genericEvidence().split("#", 2);
            assertThat(parts)
                .as(probe.project() + " must point at a generic evidence class and method")
                .hasSize(2);
            assertThat(parts[0])
                .as(probe.project() + " must keep generic evidence in the compiler-owned dependency suite")
                .isEqualTo("CliDependencyProjectIntegrationTest");
            assertThat(genericCoverage)
                .as(probe.project() + " must point at an existing compiler-owned generic regression")
                .contains("void " + parts[1] + "(");
            for (final Pattern pattern : ExternalProbeIdentities.identityPatterns()) {
                assertThat(pattern.matcher(parts[1]).find())
                    .as(probe.project() + " generic evidence method name must stay free of external probe identities")
                    .isFalse();
            }
        }
    }

    @Test
    void externalProbeGenericEvidenceMethodNamesStayBehaviorShaped() throws Exception {
        final List<String> forbiddenFragments = List.of("probe", "artifact", "external");
        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            final String[] parts = probe.genericEvidence().split("#", 2);
            assertThat(parts)
                .as(probe.project() + " must point at a generic evidence class and method")
                .hasSize(2);
            final String methodName = parts[1].toLowerCase(java.util.Locale.ROOT);
            for (final String forbiddenFragment : forbiddenFragments) {
                assertThat(methodName)
                    .as(probe.project() + " generic evidence method name must stay behavior-shaped, not smoke-boundary-shaped")
                    .doesNotContain(forbiddenFragment);
            }
        }
    }

    @Test
    void onlyDedicatedAcceptanceBoundaryTestsMayReferenceRealProbeInfrastructure() throws Exception {
        final Set<Path> allowed = Set.of(
            TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java"),
            TEST_SOURCES.resolve("javan/ExternalProbeCatalog.java"),
            TEST_SOURCES.resolve("javan/ExternalProbeIdentities.java"),
            TEST_SOURCES.resolve("javan/ExternalProbeIsolationTest.java")
        );
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            final List<Path> sourceFiles = files
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : sourceFiles) {
                if (allowed.contains(file)) {
                    continue;
                }
                final String content = Files.readString(file);
                assertThat(content)
                    .as(file + " should not reference the dedicated external-probes directory")
                    .doesNotContain("external-probes");
                assertThat(content)
                    .as(file + " should not reference probe metadata files")
                    .doesNotContain("probe.properties");
                assertThat(content)
                    .as(file + " should not use the external probe identity helper")
                    .doesNotContain("ExternalProbeIdentities");
                assertThat(content)
                    .as(file + " should not use the external probe catalog helper")
                    .doesNotContain("ExternalProbeCatalog");
                assertThat(content)
                    .as(file + " should not define or materialize external probe records")
                    .doesNotContain("record ExternalProbe(")
                    .doesNotContain("new ExternalProbe(")
                    .doesNotContain("ExternalProbe::");
            }
        }
    }

    @Test
    void onlyDedicatedAcceptanceBoundaryTestMayUseExternalProbeTag() throws Exception {
        final Set<Path> allowed = Set.of(TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java"));
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            final List<Path> sourceFiles = files
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : sourceFiles) {
                final String content = Files.readString(file);
                if (allowed.contains(file)) {
                    assertThat(content)
                        .as(file + " must stay explicitly marked as the dedicated external probe acceptance boundary")
                        .contains("@Tag(\"external-probe\")");
                    continue;
                }
                assertThat(content)
                    .as(file + " should not use the external probe acceptance tag")
                    .doesNotContain("@Tag(\"external-probe\")");
            }
        }
    }

    @Test
    void acceptanceBoundaryHarnessStaysMetadataDrivenWithoutHardcodedProbeIdentities() throws Exception {
        final Path acceptanceTest = TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java");
        assertTextExcludesExternalProbeIdentities(Files.readString(acceptanceTest), acceptanceTest);
    }

    @Test
    void externalProbeBuildScriptsStayMetadataDrivenAndProjectNeutral() throws Exception {
        final Path helper = EXTERNAL_PROBES.resolve("build-external-probe.sh");
        final String helperContent = Files.readString(helper);

        assertThat(helperContent)
            .contains("probe.properties")
            .contains("JAVAN_PROBE_CLASSPATH")
            .contains("JAVAN_PROBE_ARTIFACT")
            .contains("JAVAN_PROBE_CLASSES");
        assertOnlyGenericProbeOverrides(helperContent, helper);
        assertTextExcludesExternalProbeIdentities(helperContent, helper);

        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            final Path script = Path.of(probe.projectDirectory()).resolve("build-example.sh");
            final String content = Files.readString(script);
            assertThat(content)
                .as(script + " should delegate to the shared metadata-driven resolver")
                .contains("../build-external-probe.sh")
                .doesNotContain("JAVAN_PROBE_MAVEN_COORDINATE");
            assertOnlyGenericProbeOverrides(content, script);
            assertTextExcludesExternalProbeIdentities(content, script);
        }
    }

    @Test
    void externalProbeNonSourceFilesStayGenericOutsideProbeMetadata() throws Exception {
        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            final Path root = Path.of(probe.projectDirectory());
            try (Stream<Path> files = Files.walk(root)) {
                final List<Path> nonSourceFiles = files
                    .filter(Files::isRegularFile)
                    .filter(file -> !isGeneratedProbeArtifact(root, file))
                    .filter(file -> !file.toString().endsWith(".java"))
                    .filter(file -> !file.getFileName().toString().equals("probe.properties"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
                for (final Path file : nonSourceFiles) {
                    assertTextExcludesExternalProbeIdentitiesIfText(file);
                }
            }
        }
    }

    @Test
    void externalProbeReadmesStayGenericAndMetadataDriven() throws Exception {
        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            final Path readme = Path.of(probe.projectDirectory()).resolve("README.md");
            final String content = Files.readString(readme);
            assertThat(content)
                .as(readme + " should describe the generic probe override surface")
                .contains("probe.properties")
                .contains("JAVAN_MAVEN_REPO");
            assertOnlyGenericProbeOverrides(content, readme);
            assertTextExcludesExternalProbeIdentities(content, readme);
        }
    }

    @Test
    void realProbeResourceDirectoriesStayFreeOfGeneratedBuildOutputs() throws Exception {
        for (final ExternalProbeCatalog.ExternalProbe probe : ExternalProbeCatalog.realProbes()) {
            final Path root = Path.of(probe.projectDirectory());
            assertThat(root.resolve(".javan"))
                .as(root + " should not keep generated .javan output in source control or local test resources")
                .doesNotExist();
            assertThat(root.resolve("target"))
                .as(root + " should not keep generated target output in local test resources")
                .doesNotExist();
            assertThat(root.resolve("build"))
                .as(root + " should not keep generated build output in local test resources")
                .doesNotExist();
            assertThat(root.resolve("out"))
                .as(root + " should not keep generated out output in local test resources")
                .doesNotExist();
        }
    }

    private static void assertOnlyGenericProbeOverrides(final String content, final Path file) {
        final Pattern probeOverride = Pattern.compile("\\b[A-Z][A-Z0-9_]*_(?:JAR|COORDINATE|CLASSPATH|CLASSES)\\b");
        final List<String> matches = new ArrayList<>();
        final java.util.regex.Matcher matcher = probeOverride.matcher(content);
        while (matcher.find()) {
            final String token = matcher.group();
            if (!token.equals("JAVAN_PROBE_CLASSPATH")
                && !token.equals("JAVAN_PROBE_ARTIFACT")
                && !token.equals("JAVAN_PROBE_CLASSES")) {
                matches.add(token);
            }
        }
        assertThat(matches)
            .as(file + " should keep only generic probe override variables")
            .isEmpty();
    }

    private static void assertSourcesExcludeExternalProbeIdentities(final Path root, final List<Path> excludedFiles) throws Exception {
        final List<Pattern> forbiddenPatterns = ExternalProbeIdentities.identityPatterns();
        try (Stream<Path> files = Files.walk(root)) {
            final List<Path> sourceFiles = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> excludedFiles.stream().noneMatch(path::endsWith))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : sourceFiles) {
                assertTextExcludesExternalProbeIdentities(Files.readString(file), file);
            }
        }
    }

    private static List<Path> markdownFiles(final Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files
                .filter(path -> path.toString().endsWith(".md"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
    }

    private static List<String> supportMatrixFeatures(final String json) {
        final Pattern pattern = Pattern.compile("\"feature\"\\s*:\\s*\"([^\"]+)\"");
        final List<String> features = new ArrayList<>();
        final java.util.regex.Matcher matcher = pattern.matcher(json);
        while (matcher.find()) {
            features.add(matcher.group(1));
        }
        return features;
    }

    private static boolean isGeneratedProbeArtifact(final Path root, final Path file) {
        final Path relative = root.relativize(file);
        for (final Path segment : relative) {
            final String name = segment.toString();
            if (".javan".equals(name) || "target".equals(name) || "build".equals(name) || "out".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void assertTextExcludesExternalProbeIdentities(final String content, final Path file) throws IOException {
        for (final Pattern pattern : ExternalProbeIdentities.identityPatterns()) {
            assertThat(pattern.matcher(content).find())
                .as(file + " should stay free of external probe identity pattern " + pattern)
                .isFalse();
        }
    }

    private static void assertTextExcludesExternalProbeProjectLabels(final String content, final Path file) throws IOException {
        for (final String projectLabel : ExternalProbeIdentities.projectNames()) {
            assertThat(content)
                .as(file + " should stay free of external probe project label " + projectLabel)
                .doesNotContain(projectLabel);
        }
    }

    private static void assertTextExcludesExternalProbeIdentitiesIfText(final Path file) throws IOException {
        try {
            assertTextExcludesExternalProbeIdentities(Files.readString(file), file);
        } catch (final MalformedInputException ignored) {
            // Binary fixtures and generated artifacts are allowed to exist here; identity checks are for text resources.
        }
    }

    private static ClassMetadata minimalClass(final String moduleName, final String name) {
        return new ClassMetadata(
            null,
            false,
            moduleName,
            0,
            69,
            0,
            name,
            "java/lang/Object",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }
}
