package javan;

import javan.compat.ClassMetadata;
import javan.compat.CompatibilityReports;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Path EXTERNAL_ACCEPTANCE_TEST =
        TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java");
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
    void compilerOwnedTestsStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(TEST_SOURCES, List.of(EXTERNAL_ACCEPTANCE_TEST));
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
    void onlyDedicatedExternalSmokeResourcesMayReferenceExternalProbeIdentities() throws Exception {
        final Path allowedRoot = TEST_RESOURCES.resolve("projects/real-probes");
        try (Stream<Path> files = Files.walk(TEST_RESOURCES)) {
            final List<Path> resourceFiles = files
                .filter(Files::isRegularFile)
                .filter(file -> !file.startsWith(allowedRoot))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : resourceFiles) {
                assertTextExcludesExternalProbeIdentitiesIfText(file);
            }
        }
    }

    @Test
    void onlyDedicatedExternalSmokeDocsMayNameExternalProbeIdentities() throws Exception {
        final Set<Path> allowed = Set.of(
            DOC_STATUS.resolve("real-project-readiness.md"),
            Path.of("src/test/resources/projects/README.md")
        );
        try (Stream<Path> files = Files.walk(Path.of("doc"))) {
            final List<Path> markdownFiles = files
                .filter(path -> path.toString().endsWith(".md"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : markdownFiles) {
                if (allowed.contains(file)) {
                    continue;
                }
                assertTextExcludesExternalProbeIdentities(Files.readString(file), file);
            }
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
    void externalProbeMetadataLoadsFromDedicatedSmokeProjects() throws Exception {
        assertThat(ExternalProbeIdentities.projectNames())
            .isNotEmpty()
            .doesNotHaveDuplicates()
            .allSatisfy(project -> assertThat(project).isNotBlank());
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
    void onlyDedicatedAcceptanceBoundaryTestsMayReferenceRealProbeInfrastructure() throws Exception {
        final Set<Path> allowed = Set.of(
            TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java"),
            TEST_SOURCES.resolve("javan/ExternalProbeCatalog.java"),
            TEST_SOURCES.resolve("javan/ExternalProbeIdentities.java"),
            TEST_SOURCES.resolve("javan/ExternalProbeIsolationTest.java"),
            TEST_SOURCES.resolve("javan/compat/CompatibilityReportsTest.java")
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
                    .as(file + " should not reference the external real-probes directory")
                    .doesNotContain("real-probes");
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

    private static void assertTextExcludesExternalProbeIdentities(final String content, final Path file) throws IOException {
        for (final Pattern pattern : ExternalProbeIdentities.identityPatterns()) {
            assertThat(pattern.matcher(content).find())
                .as(file + " should stay free of external probe identity pattern " + pattern)
                .isFalse();
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
