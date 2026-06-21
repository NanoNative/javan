package javan.codegen;

import javan.detect.ProjectLayout;
import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic source-line lookup for source-mapped runtime diagnostics.
 */
public final class SourceLineIndex {
    private final Map<String, List<String>> linesByRelativePath;
    private final Map<String, List<String>> linesByFileName;

    private SourceLineIndex(
        final Map<String, List<String>> linesByRelativePath,
        final Map<String, List<String>> linesByFileName
    ) {
        this.linesByRelativePath = linesByRelativePath;
        this.linesByFileName = linesByFileName;
    }

    /**
     * Returns an empty index for class-only or jar-only inputs.
     *
     * @return empty source-line index
     */
    public static SourceLineIndex empty() {
        return new SourceLineIndex(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /**
     * Builds an index from detected project source folders.
     *
     * @param layout detected project layout
     * @return source-line index
     * @throws IOException when source scanning fails
     */
    public static SourceLineIndex from(final ProjectLayout layout) throws IOException {
        return from(layout.sourceFolders());
    }

    /**
     * Builds an index from source folders.
     *
     * @param sourceFolders source roots
     * @return source-line index
     * @throws IOException when source scanning fails
     */
    public static SourceLineIndex from(final List<Path> sourceFolders) throws IOException {
        final Map<String, List<String>> byRelativePath = new LinkedHashMap<>();
        final Map<String, List<String>> byFileName = new LinkedHashMap<>();
        for (final Path sourceFolder : sourceFolders) {
            final Path root = sourceFolder.toAbsolutePath().normalize();
            for (final Path source : Files2.findJavaSources(root)) {
                final Path normalized = source.toAbsolutePath().normalize();
                final String relativePath = normalizePath(root.relativize(normalized).toString());
                final List<String> lines = lines(Files2.readStringIfExists(normalized));
                putIfAbsent(byRelativePath, relativePath, lines);
                putIfAbsent(byFileName, fileName(relativePath), lines);
            }
        }
        return new SourceLineIndex(Map.copyOf(byRelativePath), Map.copyOf(byFileName));
    }

    /**
     * Finds source line text for a source-mapped class location.
     *
     * @param className JVM internal class name
     * @param sourceFile source file attribute
     * @param lineNumber source line number
     * @return source line text when available
     */
    public Optional<String> line(
        final String className,
        final Optional<String> sourceFile,
        final Optional<Integer> lineNumber
    ) {
        if (sourceFile.isEmpty() || lineNumber.isEmpty()) {
            return Optional.empty();
        }
        final int line = lineNumber.orElseThrow();
        if (line < 1) {
            return Optional.empty();
        }
        final String sourceName = normalizePath(sourceFile.orElseThrow());
        final List<String> lines = sourceLines(className, sourceName);
        if (lines.isEmpty() || line > lines.size()) {
            return Optional.empty();
        }
        return Optional.of(lines.get(line - 1));
    }

    private List<String> sourceLines(final String className, final String sourceFile) {
        final String relative = relativeSourcePath(className, sourceFile);
        final List<String> byRelative = linesByRelativePath.get(relative);
        if (byRelative != null) {
            return byRelative;
        }
        final List<String> byName = linesByFileName.get(fileName(sourceFile));
        if (byName != null) {
            return byName;
        }
        return List.of();
    }

    private static String relativeSourcePath(final String className, final String sourceFile) {
        final int slash = className.lastIndexOf('/');
        if (slash < 0) {
            return fileName(sourceFile);
        }
        return new StringBuilder(className.substring(0, slash))
            .append('/')
            .append(fileName(sourceFile))
            .toString();
    }

    private static void putIfAbsent(
        final Map<String, List<String>> values,
        final String key,
        final List<String> lines
    ) {
        if (!values.containsKey(key)) {
            values.put(key, lines);
        }
    }

    private static String normalizePath(final String value) {
        return Strings2.replaceChar(value, '\\', '/');
    }

    private static String fileName(final String value) {
        final int slash = value.lastIndexOf('/');
        if (slash < 0) {
            return value;
        }
        return value.substring(slash + 1);
    }

    private static List<String> lines(final String value) {
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch == '\n' || ch == '\r') {
                result.add(Strings2.slice(value, start, index));
                if (ch == '\r' && index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                    index++;
                }
                start = index + 1;
            }
        }
        if (start < value.length()) {
            result.add(Strings2.slice(value, start, value.length()));
        }
        return List.copyOf(result);
    }
}
