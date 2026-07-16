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

final class ExternalProbeCatalog {
    static final Path REAL_PROBES = Path.of("src/test/resources/projects/external-smoke");

    private ExternalProbeCatalog() {
    }

    static List<ExternalProbe> realProbes() throws IOException {
        return realProbes(REAL_PROBES);
    }

    static List<ExternalProbe> realProbes(final Path probesRoot) throws IOException {
        try (Stream<Path> paths = Files.list(probesRoot)) {
            return paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(ExternalProbeCatalog::loadProbe)
                .toList();
        }
    }

    static List<String> projectNames() throws IOException {
        return realProbes().stream()
            .map(ExternalProbe::project)
            .toList();
    }

    static List<Pattern> identityPatterns() throws IOException {
        return realProbes().stream()
            .flatMap(probe -> patternsFor(probe).stream())
            .distinct()
            .sorted(Comparator.comparing(Pattern::pattern))
            .toList();
    }

    static ExternalProbe loadProbe(final Path projectDirectory) {
        try {
            final Properties properties = new Properties();
            properties.load(new StringReader(Files.readString(projectDirectory.resolve("probe.properties"))));
            return new ExternalProbe(
                require(properties, "project"),
                require(properties, "groupId"),
                require(properties, "artifactId"),
                require(properties, "version"),
                properties.getProperty("mainClass", "com.acme.Main"),
                properties.getProperty("genericEvidence", ""),
                parseOptionalCsv(properties.getProperty("identityPackages", "")),
                Files.readString(projectDirectory.resolve("expected.stdout")),
                projectDirectory.toString().replace('\\', '/')
            );
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to load external probe metadata from " + projectDirectory, exception);
        }
    }

    private static List<Pattern> patternsFor(final ExternalProbe probe) {
        final List<Pattern> patterns = new java.util.ArrayList<>();
        patterns.add(exactLiteral(probe.project()));
        patterns.add(exactLiteral(probe.groupId()));
        final Pattern artifact = artifactPattern(probe.artifactId());
        if (artifact != null) {
            patterns.add(artifact);
        }
        final Pattern normalized = normalizedArtifactWord(probe.artifactId());
        if (normalized != null) {
            patterns.add(normalized);
        }
        for (final String identityPackage : probe.identityPackages()) {
            patterns.add(exactLiteral(identityPackage));
            patterns.add(exactLiteral(identityPackage.replace('.', '/')));
        }
        return patterns;
    }

    private static Pattern exactLiteral(final String value) {
        return Pattern.compile(Pattern.quote(value));
    }

    private static Pattern artifactPattern(final String artifactId) {
        final boolean plainWord = artifactId.chars().allMatch(character -> Character.isLetterOrDigit(character) || character == '_');
        if (plainWord && artifactId.length() < 4) {
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

    private static List<String> parseOptionalCsv(final String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isBlank())
            .distinct()
            .sorted()
            .toList();
    }

    record ExternalProbe(
        String project,
        String groupId,
        String artifactId,
        String version,
        String mainClass,
        String genericEvidence,
        List<String> identityPackages,
        String expectedStdout,
        String projectDirectory
    ) {
        String coordinate() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }
}
