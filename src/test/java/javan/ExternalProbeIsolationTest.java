package javan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

final class ExternalProbeIsolationTest {
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final Path EXTERNAL_PROBES = Path.of("src/test/resources/projects/real-probes");
    private static final Path EXTERNAL_ACCEPTANCE_TEST =
        TEST_SOURCES.resolve("javan/CliExternalProbeAcceptanceIntegrationTest.java");

    @Test
    void productionSourcesStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(PRODUCTION_SOURCES, List.of());
    }

    @Test
    void compilerOwnedTestsStayIndependentOfExternalProbeIdentities() throws Exception {
        assertSourcesExcludeExternalProbeIdentities(TEST_SOURCES, List.of(EXTERNAL_ACCEPTANCE_TEST));
    }

    private static void assertSourcesExcludeExternalProbeIdentities(final Path root, final List<Path> excludedFiles) throws Exception {
        final List<String> forbiddenTokens = externalProbeIdentityTokens();
        try (Stream<Path> files = Files.walk(root)) {
            final List<Path> sourceFiles = files
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> excludedFiles.stream().noneMatch(path::endsWith))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
            for (final Path file : sourceFiles) {
                final String content = Files.readString(file);
                assertThat(content)
                    .as(file.toString())
                    .doesNotContain(forbiddenTokens.toArray(String[]::new));
            }
        }
    }

    private static List<String> externalProbeIdentityTokens() throws IOException {
        try (Stream<Path> paths = Files.list(EXTERNAL_PROBES)) {
            return paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(ExternalProbeIsolationTest::loadProbeProperties)
                .flatMap(properties -> Stream.of(
                    require(properties, "project"),
                    require(properties, "groupId")
                ))
                .distinct()
                .sorted()
                .toList();
        }
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
