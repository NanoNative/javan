package javan.build;

import javan.util.Strings2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Native library artifact formats produced by one library build.
 */
public enum LibraryFormat {
    STATIC,
    SHARED;

    /**
     * Parses a library format value.
     *
     * @param value raw CLI value
     * @return parsed format when supported
     */
    public static Optional<LibraryFormat> parse(final String value) {
        final String normalized = Strings2.replaceChar(Strings2.toAsciiUpperCase(value), '-', '_');
        for (final LibraryFormat format : values()) {
            if (format.name().equals(normalized)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /**
     * Parses comma-separated library format values.
     *
     * @param value raw CLI value
     * @return requested formats
     */
    public static List<LibraryFormat> parseList(final String value) {
        final String trimmed = Strings2.trimAscii(value);
        if (Strings2.equalsAsciiIgnoreCase("both", trimmed) || Strings2.equalsAsciiIgnoreCase("all", trimmed)) {
            return List.of(STATIC, SHARED);
        }
        final List<LibraryFormat> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == ',') {
                addFormat(result, value, start, index);
                start = index + 1;
            }
        }
        return List.copyOf(result);
    }

    /**
     * Computes the legacy artifact path for this format.
     *
     * @param outputDirectory javan output directory
     * @param outputName logical library name
     * @return artifact path
     */
    public Path artifactPath(final Path outputDirectory, final String outputName) {
        if (this == STATIC) {
            return outputDirectory.resolve("dist").resolve("lib" + outputName + ".a");
        }
        if (this == SHARED) {
            return outputDirectory.resolve("dist").resolve(sharedLibraryName(outputName));
        }
        throw new IllegalStateException("Unsupported library format");
    }

    private static String sharedLibraryName(final String outputName) {
        final String os = Strings2.toAsciiLowerCase(System.getProperty("os.name", ""));
        if (os.contains("win")) {
            return outputName + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + outputName + ".dylib";
        }
        return "lib" + outputName + ".so";
    }

    private static void addFormat(final List<LibraryFormat> result, final String value, final int start, final int end) {
        final StringBuilder raw = new StringBuilder();
        for (int index = start; index < end; index++) {
            raw.append(value.charAt(index));
        }
        final String entry = Strings2.trimAscii(raw.toString());
        if (Strings2.isBlank(entry)) {
            return;
        }
        final Optional<LibraryFormat> parsed = parse(entry);
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException(unsupportedLibraryFormat(entry));
        }
        final LibraryFormat format = parsed.orElseThrow();
        if (!contains(result, format)) {
            result.add(format);
        }
    }

    private static String unsupportedLibraryFormat(final String entry) {
        return new StringBuilder()
            .append("Unsupported library format: ")
            .append(entry)
            .toString();
    }

    private static boolean contains(final List<LibraryFormat> values, final LibraryFormat target) {
        for (final LibraryFormat value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }
}
