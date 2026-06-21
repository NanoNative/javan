package javan.dependency;

import javan.util.Files2;
import javan.util.Json;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes deterministic {@code javan.lock} files for resolved local dependencies.
 */
public final class JavanLockWriter {
    /**
     * Writes {@code javan.lock} when {@code javan.mod} is present.
     *
     * @param root project root
     * @param module parsed module
     * @return lock path
     * @throws IOException when filesystem metadata or lock writing fails
     */
    public Path write(final Path root, final JavanModule module) throws IOException {
        final Path lock = root.resolve("javan.lock");
        if (!module.present()) {
            return lock;
        }
        Files2.writeString(lock, render(root, module));
        return lock;
    }

    private static String render(final Path root, final JavanModule module) throws IOException {
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"lockVersion\": 1,\n");
        json.append("  \"module\": ").append(Json.string(module.moduleName())).append(",\n");
        json.append("  \"java\": ").append(Json.string(module.javaVersion())).append(",\n");
        json.append("  \"dependencyCount\": ").append(module.dependencies().size()).append(",\n");
        json.append("  \"dependencies\": [");
        if (!module.dependencies().isEmpty()) {
            json.append('\n');
        }
        for (int index = 0; index < module.dependencies().size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            json.append(dependencyJson(root, module.dependencies().get(index)));
        }
        if (!module.dependencies().isEmpty()) {
            json.append('\n');
        }
        json.append("  ],\n");
        json.append("  \"warnings\": ").append(Json.stringList(module.warnings())).append('\n');
        json.append("}\n");
        return json.toString();
    }

    private static String dependencyJson(final Path root, final JavanDependency dependency) throws IOException {
        final DependencyState state = state(root, dependency);
        final StringBuilder json = new StringBuilder();
        json.append("    {\n");
        appendText(json, "scope", dependency.scope(), true);
        appendText(json, "kind", dependency.kind(), true);
        appendText(json, "notation", dependency.notation(), true);
        appendText(json, "status", state.status(), true);
        appendText(json, "artifactKind", state.artifactKind(), true);
        appendText(json, "path", state.path(), true);
        appendText(json, "relativePath", state.relativePath(), true);
        appendNumber(json, "size", state.size(), true);
        appendText(json, "checksumAlgorithm", state.checksumAlgorithm(), true);
        appendText(json, "checksum", state.checksum(), true);
        appendNumber(json, "line", dependency.line(), false);
        json.append("    }");
        return json.toString();
    }

    private static DependencyState state(final Path root, final JavanDependency dependency) throws IOException {
        if (!dependency.local()) {
            if (dependency.path().isPresent()) {
                final Path path = dependency.path().orElseThrow();
                if (Files.exists(path)) {
                    return new DependencyState(
                        "present",
                        artifactKind(path),
                        path.toString(),
                        relativePath(root, path),
                        size(path),
                        "fnv64",
                        Strings2.hexLong(hash(path))
                    );
                }
                return new DependencyState(
                    "missing-coordinate",
                    artifactKind(path),
                    path.toString(),
                    relativePath(root, path),
                    0L,
                    "none",
                    ""
                );
            }
            return new DependencyState(
                "unsupported-coordinate",
                "coordinate",
                "",
                "",
                0L,
                "none",
                ""
            );
        }
        final Path path = dependency.path().orElseThrow();
        if (!Files.exists(path)) {
            return new DependencyState(
                "missing",
                artifactKind(path),
                path.toString(),
                relativePath(root, path),
                0L,
                "none",
                ""
            );
        }
        return new DependencyState(
            "present",
            artifactKind(path),
            path.toString(),
            relativePath(root, path),
            size(path),
            "fnv64",
            Strings2.hexLong(hash(path))
        );
    }

    private static long size(final Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return Files.size(path);
        }
        long result = 0L;
        for (final Path file : files(path)) {
            result += Files.size(file);
        }
        return result;
    }

    private static long hash(final Path path) throws IOException {
        long result = 0xcbf29ce484222325L;
        if (Files.isRegularFile(path)) {
            return hashFile(path, result);
        }
        for (final Path file : files(path)) {
            result = hashString(slash(path.relativize(file)), result);
            result = hashFile(file, result);
        }
        return result;
    }

    private static long hashFile(final Path file, final long seed) throws IOException {
        long result = seed;
        final byte[] bytes = Files.readAllBytes(file);
        for (int index = 0; index < bytes.length; index++) {
            result ^= bytes[index] & 0xffL;
            result *= 0x100000001b3L;
        }
        return result;
    }

    private static long hashString(final String value, final long seed) {
        long result = seed;
        for (int index = 0; index < value.length(); index++) {
            result ^= value.charAt(index);
            result *= 0x100000001b3L;
        }
        return result;
    }

    private static List<Path> files(final Path root) throws IOException {
        final List<Path> result = new ArrayList<>();
        addFiles(result, Files2.findClassFiles(root));
        addFiles(result, Files2.findResourceFiles(root));
        return List.copyOf(result);
    }

    private static void addFiles(final List<Path> result, final List<Path> files) {
        for (final Path file : files) {
            addFile(result, file);
        }
    }

    private static void addFile(final List<Path> result, final Path file) {
        final String value = file.toString();
        int index = 0;
        while (index < result.size() && Strings2.compareAscii(result.get(index).toString(), value) <= 0) {
            if (result.get(index).toString().equals(value)) {
                return;
            }
            index++;
        }
        result.add(index, file);
    }

    private static String artifactKind(final Path path) {
        if (isJar(path)) {
            return Files.exists(path) ? "jar" : "missing-jar";
        }
        if (Files.isDirectory(path)) {
            return "classes-directory";
        }
        if (Files.exists(path)) {
            return "file";
        }
        return "missing";
    }

    private static boolean isJar(final Path path) {
        final Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        return fileName.toString().endsWith(".jar");
    }

    private static String relativePath(final Path root, final Path path) {
        final Path normalizedRoot = root.toAbsolutePath().normalize();
        final Path normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedPath.startsWith(normalizedRoot)) {
            return slash(normalizedRoot.relativize(normalizedPath));
        }
        return normalizedPath.toString();
    }

    private static String slash(final Path path) {
        return Strings2.replaceChar(path.toString(), java.io.File.separatorChar, '/');
    }

    private static void appendText(final StringBuilder json, final String name, final String value, final boolean comma) {
        json.append("      ").append(Json.string(name)).append(": ").append(Json.string(value));
        appendComma(json, comma);
    }

    private static void appendNumber(final StringBuilder json, final String name, final long value, final boolean comma) {
        json.append("      ").append(Json.string(name)).append(": ").append(value);
        appendComma(json, comma);
    }

    private static void appendComma(final StringBuilder json, final boolean comma) {
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private record DependencyState(
        String status,
        String artifactKind,
        String path,
        String relativePath,
        long size,
        String checksumAlgorithm,
        String checksum
    ) {
    }
}
