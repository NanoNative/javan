package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.detect.ProjectLayout;
import javan.dependency.JavanDependency;
import javan.dependency.JavanModule;
import javan.dependency.JavanModuleParser;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes dependency usage and license reports from the resolved classpath.
 */
public final class DependencyReports {
    private final JavanModuleParser moduleParser = new JavanModuleParser();

    /**
     * Writes dependency and license reports.
     *
     * @param layout detected project layout
     * @param classes parsed class files keyed by JVM internal class name
     * @param callGraph reachable methods
     * @return generated report paths
     * @throws IOException when classpath entries or reports cannot be read or written
     */
    public WrittenReports write(
        final ProjectLayout layout,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        final List<DependencyEntry> entries = entries(layout, classes, callGraph);
        final Path reports = layout.outputDirectory().resolve("reports");
        final Path dependenciesJson = reports.resolve("dependencies.json");
        final Path dependenciesMarkdown = reports.resolve("dependencies.md");
        final Path licensesJson = reports.resolve("licenses.json");
        final Path licensesMarkdown = reports.resolve("licenses.md");
        Files2.writeString(dependenciesJson, dependenciesJson(entries));
        Files2.writeString(dependenciesMarkdown, dependenciesMarkdown(entries));
        Files2.writeString(licensesJson, licensesJson(entries));
        Files2.writeString(licensesMarkdown, licensesMarkdown(entries));
        return new WrittenReports(dependenciesJson, dependenciesMarkdown, licensesJson, licensesMarkdown);
    }

    private List<DependencyEntry> entries(
        final ProjectLayout layout,
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) throws IOException {
        final List<EntryClasses> entryClasses = entryClasses(layout);
        final Map<String, Path> classOwners = classOwners(entryClasses);
        final List<String> reachableDependencyClasses = reachableDependencyClasses(classes, callGraph);
        final List<DeclaredPath> declaredPaths = declaredPaths(layout.root());
        final List<DependencyEntry> result = new ArrayList<>();
        for (int index = 0; index < entryClasses.size(); index++) {
            final EntryClasses entry = entryClasses.get(index);
            final DeclaredPath declaredPath = declaredPath(declaredPaths, entry.path());
            final List<String> reachable = reachableFor(entry.path(), reachableDependencyClasses, classOwners);
            final boolean present = Files.exists(entry.path());
            final String coordinate = coordinate(layout.outputDirectory(), entry.path()).orElse("");
            result.add(new DependencyEntry(
                index,
                entry.path(),
                kind(entry.path()),
                declaredPath.scope(),
                present ? "present" : "missing",
                declaredPath.source(),
                coordinate,
                entry.classNames(),
                reachable,
                license(layout.outputDirectory(), entry.path())
            ));
        }
        return List.copyOf(result);
    }

    private List<DeclaredPath> declaredPaths(final Path root) throws IOException {
        final JavanModule module = new javan.dependency.JavanCoordinateResolver().resolve(moduleParser.read(root));
        if (!module.present()) {
            return List.of();
        }
        final List<DeclaredPath> result = new ArrayList<>();
        for (final JavanDependency dependency : module.dependencies()) {
            if (dependency.path().isPresent()) {
                result.add(new DeclaredPath(dependency.path().orElseThrow(), dependency.scope(), "javan.mod"));
            }
        }
        return List.copyOf(result);
    }

    private static DeclaredPath declaredPath(final List<DeclaredPath> declaredPaths, final Path path) {
        final String normalized = path.toAbsolutePath().normalize().toString();
        for (final DeclaredPath declaredPath : declaredPaths) {
            if (declaredPath.path().toAbsolutePath().normalize().toString().equals(normalized)) {
                return declaredPath;
            }
        }
        return new DeclaredPath(path, "main", "classpath");
    }

    private static List<EntryClasses> entryClasses(final ProjectLayout layout) throws IOException {
        final List<EntryClasses> result = new ArrayList<>();
        for (final Path entry : layout.classpathEntries()) {
            result.add(new EntryClasses(entry, classNames(layout.outputDirectory(), entry)));
        }
        return List.copyOf(result);
    }

    private static List<String> classNames(final Path outputDirectory, final Path entry) throws IOException {
        if (!Files.exists(entry)) {
            return List.of();
        }
        if (isJar(entry)) {
            return directoryClassNames(jarCache(outputDirectory, entry));
        }
        if (Files.isDirectory(entry)) {
            return directoryClassNames(entry);
        }
        return List.of();
    }

    private static List<String> directoryClassNames(final Path root) throws IOException {
        final List<String> result = new ArrayList<>();
        for (final Path classFile : Files2.findClassFiles(root)) {
            final Path relative = root.relativize(classFile);
            final String name = relative.toString().replace('\\', '/');
            insertSortedUnique(result, name.substring(0, name.length() - ".class".length()));
        }
        return List.copyOf(result);
    }

    private static Map<String, Path> classOwners(final List<EntryClasses> entries) {
        final Map<String, Path> result = new LinkedHashMap<>();
        for (final EntryClasses entry : entries) {
            for (final String className : entry.classNames()) {
                result.put(className, entry.path());
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> reachableDependencyClasses(
        final Map<String, ClassFile> classes,
        final CallGraph callGraph
    ) {
        final List<String> result = new ArrayList<>();
        for (final EntryPoint method : callGraph.reachableMethods()) {
            final ClassFile classFile = classes.get(method.className());
            if (classFile != null && !classFile.application()) {
                insertSortedUnique(result, method.className());
            }
        }
        return List.copyOf(result);
    }

    private static List<String> reachableFor(
        final Path entry,
        final List<String> reachableDependencyClasses,
        final Map<String, Path> classOwners
    ) {
        final List<String> result = new ArrayList<>();
        for (final String className : reachableDependencyClasses) {
            final Path owner = classOwners.get(className);
            if (entry.equals(owner)) {
                insertSortedUnique(result, className);
            }
        }
        return List.copyOf(result);
    }

    private static LicenseInfo license(final Path outputDirectory, final Path entry) throws IOException {
        if (!Files.exists(entry)) {
            return LicenseInfo.unknown("none", "");
        }
        if (isJar(entry)) {
            return jarLicense(jarCache(outputDirectory, entry));
        }
        if (Files.isDirectory(entry)) {
            return directoryLicense(entry);
        }
        return LicenseInfo.unknown("none", "");
    }

    private static LicenseInfo jarLicense(final Path extractedJar) throws IOException {
        final Optional<LicenseInfo> pomLicense = pomLicense(extractedJar);
        if (pomLicense.isPresent()) {
            return pomLicense.orElseThrow();
        }
        final Optional<String> licenseFile = jarLicenseFile(extractedJar);
        if (licenseFile.isPresent()) {
            return LicenseInfo.unknown("file", licenseFile.orElseThrow());
        }
        return LicenseInfo.unknown("none", "");
    }

    private static Optional<LicenseInfo> pomLicense(final Path extractedJar) throws IOException {
        if (!Files.isDirectory(extractedJar)) {
            return Optional.empty();
        }
        for (final Path file : Files2.findResourceFiles(extractedJar)) {
            final String name = slashPath(extractedJar.relativize(file));
            if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.xml")) {
                final String xml = Files.readString(file);
                final Optional<String> licenseName = tagValue(xml, "name");
                if (licenseName.isPresent()) {
                    return Optional.of(new LicenseInfo(
                        licenseName.orElseThrow(),
                        licenseName.orElseThrow(),
                        tagValue(xml, "url").orElse(""),
                        "pom.xml",
                        name
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> jarLicenseFile(final Path extractedJar) throws IOException {
        if (!Files.isDirectory(extractedJar)) {
            return Optional.empty();
        }
        for (final Path file : Files2.findResourceFiles(extractedJar)) {
            final String name = slashPath(extractedJar.relativize(file));
            if (licenseFilename(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static LicenseInfo directoryLicense(final Path directory) {
        for (final String name : List.of("LICENSE", "LICENSE.txt", "LICENSE.md", "NOTICE", "COPYING")) {
            final Path file = directory.resolve(name);
            if (Files.isRegularFile(file)) {
                return LicenseInfo.unknown("file", name);
            }
        }
        return LicenseInfo.unknown("none", "");
    }

    private static Optional<String> coordinate(final Path outputDirectory, final Path entry) throws IOException {
        if (!Files.exists(entry) || !isJar(entry)) {
            return Optional.empty();
        }
        final Path extractedJar = jarCache(outputDirectory, entry);
        if (!Files.isDirectory(extractedJar)) {
            return Optional.empty();
        }
        for (final Path file : Files2.findResourceFiles(extractedJar)) {
            final String name = slashPath(extractedJar.relativize(file));
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

    private static String dependenciesJson(final List<DependencyEntry> entries) {
        final Summary summary = summary(entries);
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendNumber(json, "dependencyCount", entries.size(), true);
        appendNumber(json, "presentDependencies", summary.presentDependencies(), true);
        appendNumber(json, "missingDependencies", summary.missingDependencies(), true);
        appendNumber(json, "usedDependencies", summary.usedDependencies(), true);
        appendNumber(json, "unusedDependencies", summary.unusedDependencies(), true);
        appendNumber(json, "reachableDependencyClasses", summary.reachableDependencyClasses(), true);
        json.append("  \"dependencies\": [");
        if (!entries.isEmpty()) {
            json.append('\n');
        }
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            json.append(dependencyJson(entries.get(index)));
        }
        if (!entries.isEmpty()) {
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String dependencyJson(final DependencyEntry entry) {
        final StringBuilder json = new StringBuilder();
        json.append("    {\n");
        appendNumber(json, "index", entry.index(), true, 6);
        appendText(json, "path", path(entry.path()), true, 6);
        appendText(json, "kind", entry.kind(), true, 6);
        appendText(json, "scope", entry.scope(), true, 6);
        appendText(json, "status", entry.status(), true, 6);
        appendText(json, "source", entry.source(), true, 6);
        appendText(json, "coordinate", entry.coordinate(), true, 6);
        appendBoolean(json, "used", entry.used(), true, 6);
        appendNumber(json, "classCount", entry.classes().size(), true, 6);
        appendNumber(json, "reachableClassCount", entry.reachableClasses().size(), true, 6);
        appendStringList(json, "classes", entry.classes(), true, 6);
        appendStringList(json, "reachableClasses", entry.reachableClasses(), false, 6);
        json.append("    }");
        return json.toString();
    }

    private static String licensesJson(final List<DependencyEntry> entries) {
        final Summary summary = summary(entries);
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        appendNumber(json, "licenseCount", entries.size(), true);
        appendNumber(json, "knownLicenses", summary.knownLicenses(), true);
        appendNumber(json, "unknownLicenses", summary.unknownLicenses(), true);
        appendNumber(json, "warningLicenses", summary.unknownLicenses(), true);
        appendNumber(json, "blockedLicenses", 0, true);
        json.append("  \"licenses\": [");
        if (!entries.isEmpty()) {
            json.append('\n');
        }
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            json.append(licenseJson(entries.get(index)));
        }
        if (!entries.isEmpty()) {
            json.append('\n');
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String licenseJson(final DependencyEntry entry) {
        final StringBuilder json = new StringBuilder();
        final LicenseInfo license = entry.license();
        json.append("    {\n");
        appendNumber(json, "index", entry.index(), true, 6);
        appendText(json, "dependency", path(entry.path()), true, 6);
        appendText(json, "coordinate", entry.coordinate(), true, 6);
        appendText(json, "id", license.id(), true, 6);
        appendText(json, "name", license.name(), true, 6);
        appendText(json, "url", license.url(), true, 6);
        appendText(json, "source", license.source(), true, 6);
        appendText(json, "path", license.path(), true, 6);
        appendText(json, "policy", license.known() ? "known" : "warning", false, 6);
        json.append("    }");
        return json.toString();
    }

    private static String dependenciesMarkdown(final List<DependencyEntry> entries) {
        final Summary summary = summary(entries);
        final StringBuilder markdown = new StringBuilder();
        markdown.append("# Dependency Report\n\n");
        markdown.append("- dependency count: `").append(entries.size()).append("`\n");
        markdown.append("- present dependencies: `").append(summary.presentDependencies()).append("`\n");
        markdown.append("- missing dependencies: `").append(summary.missingDependencies()).append("`\n");
        markdown.append("- used dependencies: `").append(summary.usedDependencies()).append("`\n");
        markdown.append("- unused dependencies: `").append(summary.unusedDependencies()).append("`\n");
        markdown.append("- reachable dependency classes: `").append(summary.reachableDependencyClasses()).append("`\n\n");
        markdown.append("| Dependency | Kind | Scope | Status | Used | Classes | Reachable classes |\n");
        markdown.append("| --- | --- | --- | --- | --- | ---: | ---: |\n");
        for (final DependencyEntry entry : entries) {
            markdown
                .append("| `").append(path(entry.path())).append("` | `").append(entry.kind())
                .append("` | `").append(entry.scope()).append("` | `").append(entry.status())
                .append("` | `").append(entry.used()).append("` | `").append(entry.classes().size())
                .append("` | `").append(entry.reachableClasses().size()).append("` |\n");
        }
        if (entries.isEmpty()) {
            markdown.append("| _none_ | - | - | - | - | `0` | `0` |\n");
        }
        return markdown.toString();
    }

    private static String licensesMarkdown(final List<DependencyEntry> entries) {
        final Summary summary = summary(entries);
        final StringBuilder markdown = new StringBuilder();
        markdown.append("# License Report\n\n");
        markdown.append("- license entries: `").append(entries.size()).append("`\n");
        markdown.append("- known licenses: `").append(summary.knownLicenses()).append("`\n");
        markdown.append("- unknown licenses: `").append(summary.unknownLicenses()).append("`\n");
        markdown.append("- blocked licenses: `0`\n\n");
        markdown.append("Unknown licenses are reported, not blocked. Policy enforcement is not implemented yet.\n\n");
        markdown.append("| Dependency | Coordinate | License | Source | Policy |\n");
        markdown.append("| --- | --- | --- | --- | --- |\n");
        for (final DependencyEntry entry : entries) {
            final LicenseInfo license = entry.license();
            markdown
                .append("| `").append(path(entry.path())).append("` | `").append(entry.coordinate())
                .append("` | `").append(license.id()).append("` | `").append(license.sourcePath())
                .append("` | `").append(license.known() ? "known" : "warning").append("` |\n");
        }
        if (entries.isEmpty()) {
            markdown.append("| _none_ | - | - | - | - |\n");
        }
        return markdown.toString();
    }

    private static Summary summary(final List<DependencyEntry> entries) {
        int present = 0;
        int missing = 0;
        int used = 0;
        int unused = 0;
        int reachableClasses = 0;
        int knownLicenses = 0;
        int unknownLicenses = 0;
        for (final DependencyEntry entry : entries) {
            if ("present".equals(entry.status())) {
                present++;
            } else {
                missing++;
            }
            if (entry.used()) {
                used++;
            } else {
                unused++;
            }
            reachableClasses += entry.reachableClasses().size();
            if (entry.license().known()) {
                knownLicenses++;
            } else {
                unknownLicenses++;
            }
        }
        return new Summary(present, missing, used, unused, reachableClasses, knownLicenses, unknownLicenses);
    }

    private static void appendText(
        final StringBuilder json,
        final String name,
        final String value,
        final boolean comma,
        final int indent
    ) {
        spaces(json, indent);
        json.append(Json.string(name)).append(": ").append(Json.string(value));
        appendCommaNewline(json, comma);
    }

    private static void appendNumber(final StringBuilder json, final String name, final long value, final boolean comma) {
        appendNumber(json, name, value, comma, 2);
    }

    private static void appendNumber(
        final StringBuilder json,
        final String name,
        final long value,
        final boolean comma,
        final int indent
    ) {
        spaces(json, indent);
        json.append(Json.string(name)).append(": ").append(value);
        appendCommaNewline(json, comma);
    }

    private static void appendBoolean(
        final StringBuilder json,
        final String name,
        final boolean value,
        final boolean comma,
        final int indent
    ) {
        spaces(json, indent);
        json.append(Json.string(name)).append(": ").append(value);
        appendCommaNewline(json, comma);
    }

    private static void appendStringList(
        final StringBuilder json,
        final String name,
        final List<String> values,
        final boolean comma,
        final int indent
    ) {
        spaces(json, indent);
        json.append(Json.string(name)).append(": ").append(Json.stringList(values));
        appendCommaNewline(json, comma);
    }

    private static void appendCommaNewline(final StringBuilder json, final boolean comma) {
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void spaces(final StringBuilder builder, final int count) {
        for (int index = 0; index < count; index++) {
            builder.append(' ');
        }
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
        if (Strings2.isBlank(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
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

    private static String slashPath(final Path path) {
        return path.toString().replace('\\', '/');
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

    private static String kind(final Path entry) {
        if (!Files.exists(entry)) {
            return isJar(entry) ? "missing-jar" : "missing";
        }
        if (isJar(entry)) {
            return "jar";
        }
        if (Files.isDirectory(entry)) {
            return "classes-directory";
        }
        return "file";
    }

    private static boolean isJar(final Path path) {
        final Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        return lowerAscii(fileName.toString()).endsWith(".jar");
    }

    private static Path jarCache(final Path outputDirectory, final Path jar) throws IOException {
        return outputDirectory.resolve("jar-cache").resolve(cacheName(jar));
    }

    private static String cacheName(final Path jar) throws IOException {
        final Path fileName = jar.getFileName();
        final String base = fileName == null ? "dependency.jar" : fileName.toString();
        final String normalized = Strings2.executableName(base);
        return normalized + "-" + Strings2.hexLong(pathHash(jar)) + "-" + Files.size(jar);
    }

    private static long pathHash(final Path path) {
        final String value = path.toAbsolutePath().normalize().toString();
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String path(final Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static void insertSortedUnique(final List<String> values, final String value) {
        int index = 0;
        while (index < values.size()) {
            final int comparison = Strings2.compareAscii(values.get(index), value);
            if (comparison == 0) {
                return;
            }
            if (comparison > 0) {
                break;
            }
            index++;
        }
        values.add(index, value);
    }

    private static String lowerAscii(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch >= 'A' && ch <= 'Z') {
                result.append((char) ('a' + (ch - 'A')));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private static String upperAscii(final String value) {
        final StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final char ch = value.charAt(index);
            if (ch >= 'a' && ch <= 'z') {
                result.append((char) ('A' + (ch - 'a')));
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    /**
     * Paths written by dependency report generation.
     *
     * @param dependenciesJson dependencies JSON path
     * @param dependenciesMarkdown dependencies Markdown path
     * @param licensesJson licenses JSON path
     * @param licensesMarkdown licenses Markdown path
     */
    public record WrittenReports(
        Path dependenciesJson,
        Path dependenciesMarkdown,
        Path licensesJson,
        Path licensesMarkdown
    ) {
    }

    private record EntryClasses(Path path, List<String> classNames) {
    }

    private record DeclaredPath(Path path, String scope, String source) {
    }

    private record DependencyEntry(
        int index,
        Path path,
        String kind,
        String scope,
        String status,
        String source,
        String coordinate,
        List<String> classes,
        List<String> reachableClasses,
        LicenseInfo license
    ) {
        private boolean used() {
            return !reachableClasses.isEmpty();
        }
    }

    private record LicenseInfo(String id, String name, String url, String source, String path) {
        private static LicenseInfo unknown(final String source, final String path) {
            return new LicenseInfo("unknown", "unknown", "", source, path);
        }

        private boolean known() {
            return !"unknown".equals(id);
        }

        private String sourcePath() {
            if (Strings2.isBlank(path)) {
                return source;
            }
            return source + ":" + path;
        }
    }

    private record Summary(
        int presentDependencies,
        int missingDependencies,
        int usedDependencies,
        int unusedDependencies,
        int reachableDependencyClasses,
        int knownLicenses,
        int unknownLicenses
    ) {
    }
}
