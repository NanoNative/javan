package javan.toolchain;

import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the minimal JDK identity required to list and select discovered JDK homes.
 *
 * <p>The inventory reads the JDK's own {@code release} file. It does not use folder names,
 * which vary between vendors, package managers, and IDEs.</p>
 */
public final class JdkInventory {
    /**
     * Reads one deduplicated inventory entry for each discovered candidate.
     *
     * @param resolution local JDK discovery result
     * @return discovered JDKs in resolver precedence order
     */
    public List<Entry> inspect(final JdkResolver.Resolution resolution) throws IOException {
        Objects.requireNonNull(resolution, "resolution");
        final List<Path> homes = new ArrayList<>();
        final List<Entry> entries = new ArrayList<>();
        for (final JdkResolver.Candidate candidate : resolution.candidates()) {
            final Path home = canonicalHome(candidate.home());
            if (homes.contains(home)) {
                continue;
            }
            homes.add(home);
            entries.add(entry(candidate));
        }
        return List.copyOf(entries);
    }

    /**
     * Selects the first facade-ready JDK matching a feature version or vendor/version selector.
     *
     * @param entries ordered inventory entries
     * @param selector feature version such as {@code 25}, or selector such as {@code corretto@25}
     * @return selected JDK when an installed matching JDK exists
     */
    public Optional<Entry> select(final List<Entry> entries, final String selector) {
        Objects.requireNonNull(entries, "entries");
        final Selector requested = Selector.parse(selector);
        for (final Entry entry : entries) {
            if (entry.facadeReady() && requested.matches(entry)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static Entry entry(final JdkResolver.Candidate candidate) throws IOException {
        final Release release = readRelease(candidate.home());
        return new Entry(
            candidate,
            release.vendor(),
            release.version(),
            featureVersion(release.version()),
            candidate.usable() && release.available()
        );
    }

    private static Release readRelease(final Path home) throws IOException {
        final Path release = home.resolve("release");
        if (!Files.isRegularFile(release)) {
            return Release.unavailable();
        }
        String vendor = "unknown";
        String version = "unknown";
        final String content = Files.readString(release);
        int start = 0;
        for (int index = 0; index <= content.length(); index++) {
            if (index == content.length() || content.charAt(index) == '\n') {
                final String line = Strings2.trimAscii(Strings2.slice(content, start, index));
                final int separator = line.indexOf('=');
                if (separator > 0) {
                    final String key = Strings2.trimAscii(Strings2.slice(line, 0, separator));
                    final String value = unquote(Strings2.trimAscii(Strings2.slice(line, separator + 1, line.length())));
                    if ("JAVA_VERSION".equals(key) && !Strings2.isBlank(value)) {
                        version = value;
                    }
                    if (("IMPLEMENTOR".equals(key) || "JAVA_VENDOR".equals(key)) && !Strings2.isBlank(value)) {
                        vendor = value;
                    }
                }
                start = index + 1;
            }
        }
        return new Release(vendor, version, !"unknown".equals(version));
    }

    private static String unquote(final String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return Strings2.slice(value, 1, value.length() - 1);
        }
        return value;
    }

    private static String featureVersion(final String version) {
        if (Strings2.isBlank(version) || "unknown".equals(version)) {
            return "unknown";
        }
        int start = 0;
        if (version.startsWith("1.")) {
            start = 2;
        }
        int end = start;
        while (end < version.length() && asciiDigit(version.charAt(end))) {
            end++;
        }
        if (end == start) {
            return "unknown";
        }
        return Strings2.slice(version, start, end);
    }

    private static Path canonicalHome(final Path home) {
        return home.toAbsolutePath().normalize();
    }

    /**
     * One discovered JDK, including the identity declared by its own release metadata.
     *
     * @param candidate discovered tool paths and origin
     * @param vendor JDK vendor from release metadata
     * @param version full JDK version from release metadata
     * @param featureVersion Java feature version
     * @param facadeReady whether this JDK can back a complete Javan facade
     */
    public record Entry(
        JdkResolver.Candidate candidate,
        String vendor,
        String version,
        String featureVersion,
        boolean facadeReady
    ) {
        /**
         * Creates a validated inventory entry.
         */
        public Entry {
            candidate = Objects.requireNonNull(candidate, "candidate");
            vendor = text(vendor, "vendor");
            version = text(version, "version");
            featureVersion = text(featureVersion, "featureVersion");
        }

        private static String text(final String value, final String name) {
            if (Strings2.isBlank(value)) {
                throw new IllegalArgumentException(name);
            }
            return Strings2.trimAscii(value);
        }
    }

    private record Release(String vendor, String version, boolean available) {
        private static Release unavailable() {
            return new Release("unknown", "unknown", false);
        }
    }

    private record Selector(String vendor, String featureVersion, boolean vendorSpecified) {
        private static Selector parse(final String value) {
            if (Strings2.isBlank(value)) {
                throw new IllegalArgumentException("Missing JDK selector; use a feature version such as 25");
            }
            final String selector = Strings2.trimAscii(value);
            final int separator = selector.indexOf('@');
            if (separator < 0) {
                return new Selector("", JdkInventory.featureVersion(selector), false);
            }
            if (separator == 0 || separator != selector.lastIndexOf('@') || separator == selector.length() - 1) {
                throw new IllegalArgumentException("Invalid JDK selector: " + selector);
            }
            return new Selector(
                Strings2.slice(selector, 0, separator),
                JdkInventory.featureVersion(Strings2.slice(selector, separator + 1, selector.length())),
                true
            );
        }

        private boolean matches(final Entry entry) {
            if (!featureVersion.equals(entry.featureVersion())) {
                return false;
            }
            return !vendorSpecified || vendorMatches(vendor, entry.vendor());
        }

        private static boolean vendorMatches(final String requested, final String actual) {
            if (containsIgnoringAsciiCase(actual, requested) || containsIgnoringAsciiCase(requested, actual)) {
                return true;
            }
            return (equalsIgnoringAsciiCase(requested, "temurin") && containsIgnoringAsciiCase(actual, "adoptium"))
                || (equalsIgnoringAsciiCase(requested, "corretto") && containsIgnoringAsciiCase(actual, "amazon"))
                || (equalsIgnoringAsciiCase(requested, "zulu") && containsIgnoringAsciiCase(actual, "azul"))
                || (equalsIgnoringAsciiCase(requested, "liberica") && containsIgnoringAsciiCase(actual, "bellsoft"));
        }

        private static boolean containsIgnoringAsciiCase(final String value, final String query) {
            if (query.isEmpty()) {
                return true;
            }
            if (query.length() > value.length()) {
                return false;
            }
            for (int start = 0; start <= value.length() - query.length(); start++) {
                int index = 0;
                while (index < query.length()
                    && asciiLower(value.charAt(start + index)) == asciiLower(query.charAt(index))) {
                    index++;
                }
                if (index == query.length()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean equalsIgnoringAsciiCase(final String first, final String second) {
            return first.length() == second.length() && containsIgnoringAsciiCase(first, second);
        }

    }

    private static boolean asciiDigit(final char value) {
        return value >= '0' && value <= '9';
    }

    private static char asciiLower(final char value) {
        if (value >= 'A' && value <= 'Z') {
            return (char) (value + ('a' - 'A'));
        }
        return value;
    }
}
