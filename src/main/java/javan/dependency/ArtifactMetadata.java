package javan.dependency;

import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Reads coordinate and license metadata from one resolved dependency artifact.
 *
 * @param coordinate embedded Maven coordinate, or empty when absent
 * @param license detected license metadata
 */
public record ArtifactMetadata(String coordinate, License license) {
    /**
     * Reads metadata from an artifact and its extracted contents.
     *
     * @param artifact original jar, file, or directory
     * @param contents extracted jar directory, or the original directory
     * @return detected metadata without guessed values
     * @throws IOException when metadata cannot be read
     */
    public static ArtifactMetadata read(final Path artifact, final Path contents) throws IOException {
        if (!Files.exists(artifact)) {
            return new ArtifactMetadata("", License.unknown("none", ""));
        }
        if (jar(artifact)) {
            return new ArtifactMetadata(coordinate(contents).orElse(""), jarLicense(artifact, contents));
        }
        if (Files.isDirectory(artifact)) {
            return new ArtifactMetadata("", directoryLicense(artifact));
        }
        return new ArtifactMetadata("", License.unknown("none", ""));
    }

    private static License jarLicense(final Path jar, final Path contents) throws IOException {
        final Optional<License> embedded = pomLicense(contents);
        if (embedded.isPresent()) {
            return embedded.orElseThrow();
        }
        final Optional<License> sibling = siblingPomLicense(jar);
        if (sibling.isPresent()) {
            return sibling.orElseThrow();
        }
        final Optional<String> licenseFile = jarLicenseFile(contents);
        if (licenseFile.isPresent()) {
            return License.unknown("file", licenseFile.orElseThrow());
        }
        return License.unknown("none", "");
    }

    private static Optional<License> siblingPomLicense(final Path jar) throws IOException {
        final Path fileName = jar.getFileName();
        if (fileName == null || !fileName.toString().endsWith(".jar")) {
            return Optional.empty();
        }
        final Path parent = jar.getParent();
        if (parent == null) {
            return Optional.empty();
        }
        final Path pom = parent.resolve(fileName.toString().substring(0, fileName.toString().length() - 4) + ".pom");
        if (!Files.isRegularFile(pom)) {
            return Optional.empty();
        }
        return xmlLicense(Files.readString(pom), "pom.xml", pom.getFileName().toString());
    }

    private static Optional<License> pomLicense(final Path contents) throws IOException {
        if (!Files.isDirectory(contents)) {
            return Optional.empty();
        }
        for (final Path file : Files2.findResourceFiles(contents)) {
            final String name = slash(contents.relativize(file));
            if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.xml")) {
                final Optional<License> license = xmlLicense(Files.readString(file), "pom.xml", name);
                if (license.isPresent()) {
                    return license;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<License> xmlLicense(
        final String xml,
        final String source,
        final String path
    ) {
        final Optional<String> licenses = tagValue(xml, "licenses");
        if (licenses.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> name = tagValue(licenses.orElseThrow(), "name");
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new License(
            name.orElseThrow(),
            name.orElseThrow(),
            tagValue(licenses.orElseThrow(), "url").orElse(""),
            source,
            path
        ));
    }

    private static Optional<String> jarLicenseFile(final Path contents) throws IOException {
        if (!Files.isDirectory(contents)) {
            return Optional.empty();
        }
        for (final Path file : Files2.findResourceFiles(contents)) {
            final String name = slash(contents.relativize(file));
            if (licenseFilename(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static License directoryLicense(final Path directory) {
        for (final String name : List.of("LICENSE", "LICENSE.txt", "LICENSE.md", "NOTICE", "COPYING")) {
            if (Files.isRegularFile(directory.resolve(name))) {
                return License.unknown("file", name);
            }
        }
        return License.unknown("none", "");
    }

    private static Optional<String> coordinate(final Path contents) throws IOException {
        if (!Files.isDirectory(contents)) {
            return Optional.empty();
        }
        for (final Path file : Files2.findResourceFiles(contents)) {
            final String name = slash(contents.relativize(file));
            if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
                final String properties = Files.readString(file);
                final String groupId = propertyValue(properties, "groupId");
                final String artifactId = propertyValue(properties, "artifactId");
                final String version = propertyValue(properties, "version");
                if (!Strings2.isBlank(groupId) && !Strings2.isBlank(artifactId) && !Strings2.isBlank(version)) {
                    return Optional.of(groupId + ":" + artifactId + ":" + version);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> tagValue(final String xml, final String tag) {
        final String open = "<" + tag + ">";
        final String close = "</" + tag + ">";
        final int start = xml.indexOf(open);
        if (start < 0) {
            return Optional.empty();
        }
        final int valueStart = start + open.length();
        final int end = xml.indexOf(close, valueStart);
        if (end < 0) {
            return Optional.empty();
        }
        final String value = Strings2.trimAscii(xml.substring(valueStart, end));
        return Strings2.isBlank(value) ? Optional.empty() : Optional.of(value);
    }

    private static String propertyValue(final String properties, final String key) {
        int start = 0;
        while (start < properties.length()) {
            int end = properties.indexOf('\n', start);
            if (end < 0) {
                end = properties.length();
            }
            final String line = Strings2.trimAscii(properties.substring(start, end));
            final String prefix = key + "=";
            if (line.startsWith(prefix)) {
                return Strings2.trimAscii(line.substring(prefix.length()));
            }
            start = end + 1;
        }
        return "";
    }

    private static boolean licenseFilename(final String value) {
        final String name = upperAscii(value);
        return name.equals("LICENSE")
            || name.equals("NOTICE")
            || name.equals("COPYING")
            || name.startsWith("LICENSE.")
            || name.startsWith("NOTICE.")
            || name.startsWith("META-INF/LICENSE")
            || name.startsWith("META-INF/NOTICE");
    }

    private static String upperAscii(final String value) {
        final StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            result.append(ch >= 'a' && ch <= 'z' ? (char) ('A' + ch - 'a') : ch);
        }
        return result.toString();
    }

    private static boolean jar(final Path path) {
        final Path fileName = path.getFileName();
        return fileName != null && fileName.toString().endsWith(".jar");
    }

    private static String slash(final Path path) {
        return Strings2.replaceChar(path.toString(), java.io.File.separatorChar, '/');
    }

    /**
     * Detected license data.
     *
     * @param id exact metadata name, or {@code unknown}
     * @param name exact metadata name, or {@code unknown}
     * @param url declared license URL, or empty
     * @param source metadata source kind
     * @param path metadata path
     */
    public record License(String id, String name, String url, String source, String path) {
        private static License unknown(final String source, final String path) {
            return new License("unknown", "unknown", "", source, path);
        }

        /**
         * Returns whether exact license metadata was found.
         *
         * @return true when the license is known
         */
        public boolean known() {
            return !"unknown".equals(id);
        }

        /**
         * Returns the compact source and path used in Markdown reports.
         *
         * @return source, optionally followed by its path
         */
        public String sourcePath() {
            return Strings2.isBlank(path) ? source : source + ":" + path;
        }
    }
}
