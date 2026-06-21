package javan.dependency;

import javan.util.Files2;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the first deterministic {@code javan.mod} dependency format.
 */
public final class JavanModuleParser {
    private static final String MAIN = "main";
    private static final String TEST = "test";
    private static final String TOOL = "tool";

    /**
     * Reads {@code javan.mod} from a project root.
     *
     * @param root project root
     * @return parsed module or absent module
     * @throws IOException when the file cannot be read
     */
    public JavanModule read(final Path root) throws IOException {
        final Path file = root.resolve("javan.mod");
        if (!Files.isRegularFile(file)) {
            return JavanModule.absent();
        }
        return parse(root, Files2.readStringIfExists(file));
    }

    /**
     * Parses module text.
     *
     * @param root project root used to resolve local dependencies
     * @param text module text
     * @return parsed module
     */
    public JavanModule parse(final Path root, final String text) {
        String moduleName = "";
        String javaVersion = "";
        final List<JavanDependency> dependencies = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int lineStart = 0;
        int lineNumber = 1;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            final String line = Strings2.trimAscii(stripComment(Strings2.slice(text, lineStart, lineEnd)));
            if (!Strings2.isBlank(line)) {
                final List<String> tokens = words(line);
                if (!tokens.isEmpty()) {
                    final ParsedLine parsed = parseLine(root, tokens, lineNumber);
                    if (!Strings2.isBlank(parsed.moduleName())) {
                        moduleName = parsed.moduleName();
                    }
                    if (!Strings2.isBlank(parsed.javaVersion())) {
                        javaVersion = parsed.javaVersion();
                    }
                    if (parsed.dependency().isPresent()) {
                        dependencies.add(parsed.dependency().orElseThrow());
                    }
                    warnings.addAll(parsed.warnings());
                }
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
            lineNumber++;
        }
        return new JavanModule(true, moduleName, javaVersion, List.copyOf(dependencies), List.copyOf(warnings));
    }

    private static ParsedLine parseLine(final Path root, final List<String> tokens, final int lineNumber) {
        final String keyword = tokens.get(0);
        if ("module".equals(keyword)) {
            if (tokens.size() == 2) {
                return ParsedLine.module(tokens.get(1));
            }
            return ParsedLine.warning(lineNumber, "module expects exactly one name");
        }
        if ("java".equals(keyword)) {
            if (tokens.size() == 2) {
                return ParsedLine.java(tokens.get(1));
            }
            return ParsedLine.warning(lineNumber, "java expects exactly one feature version");
        }
        if ("require".equals(keyword)) {
            return parseRequire(root, tokens, lineNumber);
        }
        return ParsedLine.warning(lineNumber, "unsupported javan.mod directive: " + keyword);
    }

    private static ParsedLine parseRequire(final Path root, final List<String> tokens, final int lineNumber) {
        if (tokens.size() < 2) {
            return ParsedLine.warning(lineNumber, "require expects a dependency");
        }
        String scope = MAIN;
        int notationIndex = 1;
        if (scope(tokens.get(1))) {
            scope = tokens.get(1);
            notationIndex = 2;
        }
        if (notationIndex >= tokens.size()) {
            return ParsedLine.warning(lineNumber, "require " + scope + " expects a dependency");
        }
        if (tokens.size() - notationIndex == 1) {
            return ParsedLine.dependency(dependency(root, scope, tokens.get(notationIndex), lineNumber));
        }
        if (tokens.size() - notationIndex == 2) {
            if (!coordinateLike(tokens.get(notationIndex))) {
                return ParsedLine.warning(lineNumber, "unknown dependency scope or unsupported require form");
            }
            final String notation = tokens.get(notationIndex) + " " + tokens.get(notationIndex + 1);
            return ParsedLine.dependency(new JavanDependency(scope, notation, "coordinate", Optional.empty(), lineNumber));
        }
        return ParsedLine.warning(lineNumber, "require supports local paths or coordinate plus version");
    }

    private static JavanDependency dependency(final Path root, final String scope, final String notation, final int lineNumber) {
        if (coordinateLike(notation)) {
            return new JavanDependency(scope, notation, "coordinate", Optional.empty(), lineNumber);
        }
        final Path path = root.resolve(notation).toAbsolutePath().normalize();
        return new JavanDependency(scope, notation, "local", Optional.of(path), lineNumber);
    }

    private static boolean coordinateLike(final String notation) {
        if (notation.startsWith(".") || notation.startsWith("/") || notation.indexOf('\\') >= 0 || notation.indexOf('/') >= 0) {
            return false;
        }
        return notation.indexOf(':') >= 0;
    }

    private static boolean scope(final String value) {
        return MAIN.equals(value) || TEST.equals(value) || TOOL.equals(value);
    }

    private static String stripComment(final String line) {
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            final char ch = line.charAt(index);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == '#' && !quoted) {
                return Strings2.slice(line, 0, index);
            }
        }
        return line;
    }

    private static List<String> words(final String line) {
        final List<String> result = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            final char ch = line.charAt(index);
            if (ch == '"') {
                quoted = !quoted;
            } else if (asciiWhitespace(ch) && !quoted) {
                addWord(result, current);
            } else {
                current.append(ch);
            }
        }
        addWord(result, current);
        return List.copyOf(result);
    }

    private static void addWord(final List<String> result, final StringBuilder current) {
        if (current.length() > 0) {
            result.add(current.toString());
            current.setLength(0);
        }
    }

    private static boolean asciiWhitespace(final char ch) {
        return ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f';
    }

    private record ParsedLine(
        String moduleName,
        String javaVersion,
        Optional<JavanDependency> dependency,
        List<String> warnings
    ) {
        private static ParsedLine module(final String moduleName) {
            return new ParsedLine(moduleName, "", Optional.empty(), List.of());
        }

        private static ParsedLine java(final String javaVersion) {
            return new ParsedLine("", javaVersion, Optional.empty(), List.of());
        }

        private static ParsedLine dependency(final JavanDependency dependency) {
            return new ParsedLine("", "", Optional.of(dependency), List.of());
        }

        private static ParsedLine warning(final int lineNumber, final String warning) {
            return new ParsedLine("", "", Optional.empty(), List.of("javan.mod line " + lineNumber + ": " + warning));
        }
    }
}
