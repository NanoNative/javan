package javan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class ExternalProbeIsolationTest {
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path DOC_STATUS = Path.of("doc/status");
    private static final Path EXTERNAL_PROBES = Path.of("src/test/resources/projects/real-probes");
    private static final Path EXTERNAL_ACCEPTANCE_TEST =
        TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java");
    private static final Path SUPPORT_MATRIX = DOC_STATUS.resolve("support-matrix.md");
    private static final Path JDK_COMPATIBILITY = DOC_STATUS.resolve("jdk-compatibility.md");
    private static final Path ROADMAP_PROGRESS = DOC_STATUS.resolve("roadmap-progress.md");

    @Test
    void productionSourcesStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(PRODUCTION_SOURCES, List.of());
    }

    @Test
    void compilerOwnedTestsStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(TEST_SOURCES, List.of(EXTERNAL_ACCEPTANCE_TEST));
    }

    @Test
    void coreStatusDocsStayIndependentOfExternalProbeIdentities() throws Exception {
        assertTextExcludesExternalProbeIdentities(Files.readString(SUPPORT_MATRIX), SUPPORT_MATRIX);
        assertTextExcludesExternalProbeIdentities(Files.readString(JDK_COMPATIBILITY), JDK_COMPATIBILITY);
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

    private static void assertSourcesExcludeExternalProbeIdentities(final Path root, final List<Path> excludedFiles) throws Exception {
        final List<Pattern> forbiddenPatterns = externalProbeIdentityPatterns();
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
        for (final Pattern pattern : externalProbeIdentityPatterns()) {
            assertThat(pattern.matcher(content).find())
                .as(file + " should stay free of external probe identity pattern " + pattern)
                .isFalse();
        }
    }

    private static List<Pattern> externalProbeIdentityPatterns() throws IOException {
        try (Stream<Path> paths = Files.list(EXTERNAL_PROBES)) {
            return paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(ExternalProbeIsolationTest::loadProbeProperties)
                .flatMap(properties -> Stream.of(
                    exactLiteral(require(properties, "project")),
                    exactLiteral(require(properties, "groupId")),
                    artifactPattern(require(properties, "artifactId")),
                    normalizedArtifactWord(require(properties, "artifactId"))
                ))
                .distinct()
                .sorted(Comparator.comparing(Pattern::pattern))
                .toList();
        }
    }

    private static Pattern exactLiteral(final String value) {
        return Pattern.compile(Pattern.quote(value));
    }

    private static Pattern artifactPattern(final String artifactId) {
        final boolean plainWord = artifactId.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_');
        if (plainWord) {
            return Pattern.compile("\\b" + Pattern.quote(artifactId) + "\\b", Pattern.CASE_INSENSITIVE);
        }
        return exactLiteral(artifactId);
    }

    private static Pattern normalizedArtifactWord(final String artifactId) {
        final String normalized = artifactId.replaceAll("[^A-Za-z0-9]+", "");
        return Pattern.compile("\\b" + Pattern.quote(normalized) + "\\b", Pattern.CASE_INSENSITIVE);
    }

    private static Properties loadProbeProperties(final Path directory) {
        final Path metadata = directory.resolve("probe.properties");
        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(Files.readString(metadata)));
            return properties;
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to read probe metadata from " + metadata, exception);
        }
    }

    private static String require(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing external probe property: " + key);
        }
        return value;
    }
}
