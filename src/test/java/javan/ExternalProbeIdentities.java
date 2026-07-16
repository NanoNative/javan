package javan;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ExternalProbeIdentities {
    private static final Path EXTERNAL_PROBES = Path.of("src/test/resources/projects/real-probes");

    private ExternalProbeIdentities() {
    }

    public static List<String> projectNames() throws IOException {
        try (Stream<Path> paths = Files.list(EXTERNAL_PROBES)) {
            return paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(ExternalProbeIdentities::loadProbeProperties)
                .map(properties -> require(properties, "project"))
                .toList();
        }
    }

    public static List<Pattern> identityPatterns() throws IOException {
        try (Stream<Path> paths = Files.list(EXTERNAL_PROBES)) {
            return paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(ExternalProbeIdentities::loadProbeProperties)
                .flatMap(properties -> patternsFor(properties).stream())
                .distinct()
                .sorted(Comparator.comparing(Pattern::pattern))
                .toList();
        }
    }

    private static List<Pattern> patternsFor(final Properties properties) {
        final String artifactId = require(properties, "artifactId");
        final List<Pattern> patterns = new java.util.ArrayList<>();
        patterns.add(exactLiteral(require(properties, "project")));
        patterns.add(exactLiteral(require(properties, "groupId")));
        final Pattern artifact = artifactPattern(artifactId);
        if (artifact != null) {
            patterns.add(artifact);
        }
        final Pattern normalized = normalizedArtifactWord(artifactId);
        if (normalized != null) {
            patterns.add(normalized);
        }
        return patterns;
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

    private static Pattern exactLiteral(final String value) {
        return Pattern.compile(Pattern.quote(value));
    }

    private static Pattern artifactPattern(final String artifactId) {
        final boolean plainWord = artifactId.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_');
        if (plainWord && artifactId.length() < 6) {
            return null;
        }
        if (plainWord) {
            return Pattern.compile("\\b" + Pattern.quote(artifactId) + "\\b", Pattern.CASE_INSENSITIVE);
        }
        return exactLiteral(artifactId);
    }

    private static Pattern normalizedArtifactWord(final String artifactId) {
        final String normalized = artifactId.replaceAll("[^A-Za-z0-9]+", "");
        if (normalized.length() < 6 || normalized.equalsIgnoreCase(artifactId)) {
            return null;
        }
        return Pattern.compile("\\b" + Pattern.quote(normalized) + "\\b", Pattern.CASE_INSENSITIVE);
    }

    private static String require(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing external probe property: " + key);
        }
        return value;
    }
}
