package javan.dependency;

import javan.classfile.JarCache;
import javan.util.Files2;
import javan.util.Json;
import javan.util.Sha256;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes deterministic {@code javan.lock} files for resolved local dependencies.
 */
public final class JavanLockWriter {
    private final JarCache jarCache = new JarCache();

    /**
     * Writes {@code javan.lock} when {@code javan.mod} is present.
     *
     * @param root project root
     * @param module parsed module
     * @return lock path
     * @throws IOException when filesystem metadata, lock verification, or lock writing fails
     * @throws InterruptedException when jar metadata extraction is interrupted
     */
    public Path write(final Path root, final JavanModule module) throws IOException, InterruptedException {
        return write(root, root.resolve(".javan"), module);
    }

    /**
     * Writes {@code javan.lock} using a specific build output for extracted artifact metadata.
     *
     * @param root project root
     * @param outputDirectory build output directory
     * @param module parsed module
     * @return lock path
     * @throws IOException when filesystem metadata, lock verification, or lock writing fails
     * @throws InterruptedException when jar metadata extraction is interrupted
     */
    public Path write(
        final Path root,
        final Path outputDirectory,
        final JavanModule module
    ) throws IOException, InterruptedException {
        final Path lock = root.resolve("javan.lock");
        if (!module.present()) {
            return lock;
        }
        final List<DependencyState> states = states(root, outputDirectory, module);
        final String rendered = render(module, states);
        if (Files.isRegularFile(lock)) {
            final String existing = Files.readString(lock);
            verify(module, states, existing);
            if (existing.equals(rendered)) {
                return lock;
            }
        }
        Files2.writeString(lock, rendered);
        return lock;
    }

    private List<DependencyState> states(
        final Path root,
        final Path outputDirectory,
        final JavanModule module
    ) throws IOException, InterruptedException {
        final List<DependencyState> result = new ArrayList<>();
        for (final JavanDependency dependency : module.dependencies()) {
            result.add(state(root, outputDirectory, dependency));
        }
        return List.copyOf(result);
    }

    private static String render(final JavanModule module, final List<DependencyState> states) {
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"lockVersion\": 2,\n");
        json.append("  \"module\": ").append(Json.string(module.moduleName())).append(",\n");
        json.append("  \"java\": ").append(Json.string(module.javaVersion())).append(",\n");
        json.append("  \"dependencyCount\": ").append(module.dependencies().size()).append(",\n");
        json.append("  \"dependencies\": [");
        if (module.dependencies().isEmpty()) {
            json.append("],\n");
        } else {
            json.append('\n');
        }
        for (int index = 0; index < module.dependencies().size(); index++) {
            if (index > 0) {
                json.append(",\n");
            }
            json.append(dependencyJson(module.dependencies().get(index), states.get(index)));
        }
        if (!module.dependencies().isEmpty()) {
            json.append("\n  ],\n");
        }
        json.append("  \"warnings\": ").append(Json.stringList(module.warnings())).append('\n');
        json.append("}\n");
        return json.toString();
    }

    private static String dependencyJson(final JavanDependency dependency, final DependencyState state) {
        final StringBuilder json = new StringBuilder();
        json.append("    {\n");
        appendText(json, "scope", dependency.scope(), true);
        appendText(json, "kind", dependency.kind(), true);
        appendText(json, "notation", dependency.notation(), true);
        appendBoolean(json, "direct", dependency.direct(), true);
        appendText(json, "requestedBy", dependency.requestedBy(), true);
        appendText(json, "status", state.status(), true);
        appendText(json, "artifactKind", state.artifactKind(), true);
        appendText(json, "path", state.path(), true);
        appendText(json, "relativePath", state.relativePath(), true);
        appendNumber(json, "size", state.size(), true);
        appendText(json, "checksumAlgorithm", state.checksumAlgorithm(), true);
        appendText(json, "checksum", state.checksum(), true);
        appendText(json, "repositoryOrigin", state.repositoryOrigin(), true);
        appendText(json, "licenseName", state.license().name(), true);
        appendText(json, "licenseUrl", state.license().url(), true);
        appendText(json, "licenseSource", state.license().source(), true);
        appendText(json, "licensePath", state.license().path(), true);
        appendNumber(json, "line", dependency.line(), false);
        json.append("    }");
        return json.toString();
    }

    private static void verify(
        final JavanModule module,
        final List<DependencyState> states,
        final String existing
    ) throws IOException {
        final boolean versionOne = existing.contains("  \"lockVersion\": 1,");
        final boolean versionTwo = existing.contains("  \"lockVersion\": 2,");
        if (!versionOne && !versionTwo) {
            throw new IOException("Unsupported or malformed javan.lock: expected lockVersion 1 or 2");
        }
        if (!existing.contains("  \"module\": " + Json.string(module.moduleName()) + ",")
            || !existing.contains("  \"java\": " + Json.string(module.javaVersion()) + ",")) {
            return;
        }
        final List<String> blocks = dependencyBlocks(existing);
        if (versionOne) {
            verifyVersionOne(module, states, blocks);
            return;
        }
        if (!sameDirectDeclarations(blocks, module.dependencies())) {
            return;
        }
        if (blocks.size() != module.dependencies().size()
            || !sameDeclarations(blocks, module.dependencies())
            || !existing.contains("  \"warnings\": " + Json.stringList(module.warnings()) + "\n")) {
            throw new IOException(
                "Dependency lock graph mismatch for unchanged javan.mod"
                    + "\nFix: Restore the locked local Maven metadata or change javan.mod to update the lock."
            );
        }
        for (int index = 0; index < blocks.size(); index++) {
            verifyChecksum(module.dependencies().get(index), states.get(index), blocks.get(index));
            verifyMetadata(module.dependencies().get(index), states.get(index), blocks.get(index));
        }
    }

    private static void verifyVersionOne(
        final JavanModule module,
        final List<DependencyState> states,
        final List<String> blocks
    ) throws IOException {
        final List<JavanDependency> directDependencies = directDependencies(module.dependencies());
        if (blocks.size() != directDependencies.size() || !sameDeclarations(blocks, directDependencies)) {
            return;
        }
        int blockIndex = 0;
        int stateIndex = 0;
        for (final JavanDependency dependency : module.dependencies()) {
            if (dependency.direct()) {
                verifyChecksum(dependency, states.get(stateIndex), blocks.get(blockIndex));
                verifyMetadata(dependency, states.get(stateIndex), blocks.get(blockIndex));
                blockIndex++;
            }
            stateIndex++;
        }
    }

    private static boolean sameDirectDeclarations(
        final List<String> blocks,
        final List<JavanDependency> dependencies
    ) {
        final List<String> directBlocks = new ArrayList<>();
        for (final String block : blocks) {
            if (!block.contains(Json.string("direct")) || hasBoolean(block, "direct", true)) {
                directBlocks.add(block);
            }
        }
        return sameDeclarations(directBlocks, directDependencies(dependencies));
    }

    private static List<JavanDependency> directDependencies(final List<JavanDependency> dependencies) {
        final List<JavanDependency> result = new ArrayList<>();
        for (final JavanDependency dependency : dependencies) {
            if (dependency.direct()) {
                result.add(dependency);
            }
        }
        return List.copyOf(result);
    }

    private static boolean sameDeclarations(
        final List<String> blocks,
        final List<JavanDependency> dependencies
    ) {
        if (blocks.size() != dependencies.size()) {
            return false;
        }
        for (int index = 0; index < blocks.size(); index++) {
            if (!sameDeclaration(blocks.get(index), dependencies.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static void verifyMetadata(
        final JavanDependency dependency,
        final DependencyState state,
        final String block
    ) throws IOException {
        if (!block.contains(Json.string("repositoryOrigin"))) {
            return;
        }
        if (hasText(block, "status", "missing")
            || hasText(block, "status", "missing-coordinate")
            || hasText(block, "status", "unsupported-coordinate")) {
            return;
        }
        final ArtifactMetadata.License license = state.license();
        if (hasText(block, "repositoryOrigin", state.repositoryOrigin())
            && hasText(block, "licenseName", license.name())
            && hasText(block, "licenseUrl", license.url())
            && hasText(block, "licenseSource", license.source())
            && hasText(block, "licensePath", license.path())) {
            return;
        }
        throw new IOException(
            "Dependency lock provenance mismatch for "
                + dependency.notation()
                + "\nFix: Restore the locked repository/license metadata or change javan.mod to update the lock."
        );
    }

    private static List<String> dependencyBlocks(final String lock) throws IOException {
        final String startMarker = "  \"dependencies\": [";
        final int start = lock.indexOf(startMarker);
        if (start < 0) {
            throw new IOException("Malformed javan.lock: missing dependencies");
        }
        if (lock.startsWith("],", start + startMarker.length())) {
            return List.of();
        }
        final int end = lock.indexOf("\n  ],", start + startMarker.length());
        if (end < 0) {
            throw new IOException("Malformed javan.lock: unterminated dependencies");
        }
        final String dependencies = lock.substring(start + startMarker.length(), end);
        final List<String> result = new ArrayList<>();
        int cursor = 0;
        while (true) {
            final int blockStart = dependencies.indexOf("    {\n", cursor);
            if (blockStart < 0) {
                break;
            }
            final int blockEnd = dependencies.indexOf("\n    }", blockStart + 6);
            if (blockEnd < 0) {
                throw new IOException("Malformed javan.lock: unterminated dependency");
            }
            result.add(dependencies.substring(blockStart, blockEnd + 6));
            cursor = blockEnd + 6;
        }
        return List.copyOf(result);
    }

    private static boolean sameDeclaration(final String block, final JavanDependency dependency) {
        return hasText(block, "scope", dependency.scope())
            && hasText(block, "kind", dependency.kind())
            && hasText(block, "notation", dependency.notation())
            && (!block.contains(Json.string("direct")) || hasBoolean(block, "direct", dependency.direct()))
            && (!block.contains(Json.string("requestedBy")) || hasText(block, "requestedBy", dependency.requestedBy()));
    }

    private static boolean hasBoolean(final String block, final String name, final boolean value) {
        return block.contains("      " + Json.string(name) + ": " + value);
    }

    private static void verifyChecksum(
        final JavanDependency dependency,
        final DependencyState state,
        final String block
    ) throws IOException {
        final String algorithm = text(block, "checksumAlgorithm");
        final String checksum = text(block, "checksum");
        if ("fnv64".equals(algorithm) && "sha256".equals(state.checksumAlgorithm())) {
            final String found = Strings2.hexLong(legacyHash(Path.of(state.path())));
            if (checksum.equals(found)) {
                return;
            }
            throw mismatch(dependency, algorithm, checksum, algorithm, found);
        }
        if ("none".equals(algorithm)) {
            if (hasText(block, "status", "missing")
                || hasText(block, "status", "missing-coordinate")
                || hasText(block, "status", "unsupported-coordinate")) {
                return;
            }
            throw new IOException("Malformed javan.lock: present dependency has no checksum");
        }
        if (algorithm.equals(state.checksumAlgorithm()) && checksum.equals(state.checksum())) {
            return;
        }
        throw mismatch(
            dependency,
            algorithm,
            checksum,
            state.checksumAlgorithm(),
            state.checksum()
        );
    }

    private static IOException mismatch(
        final JavanDependency dependency,
        final String lockedAlgorithm,
        final String lockedChecksum,
        final String foundAlgorithm,
        final String foundChecksum
    ) {
        return new IOException(
            "Dependency lock checksum mismatch for "
                + dependency.notation()
                + "\nLocked: "
                + lockedAlgorithm
                + ":"
                + lockedChecksum
                + "\nFound: "
                + foundAlgorithm
                + ":"
                + foundChecksum
                + "\nFix: Restore the locked artifact or change javan.mod to update the lock."
        );
    }

    private static boolean hasText(final String block, final String name, final String value) {
        return block.contains("      " + Json.string(name) + ": " + Json.string(value));
    }

    private static String text(final String block, final String name) throws IOException {
        final String prefix = "      " + Json.string(name) + ": \"";
        final int start = block.indexOf(prefix);
        if (start < 0) {
            throw new IOException("Malformed javan.lock: missing " + name);
        }
        final int valueStart = start + prefix.length();
        final int end = block.indexOf('"', valueStart);
        if (end < 0) {
            throw new IOException("Malformed javan.lock: unterminated " + name);
        }
        return block.substring(valueStart, end);
    }

    private DependencyState state(
        final Path root,
        final Path outputDirectory,
        final JavanDependency dependency
    ) throws IOException, InterruptedException {
        if (!dependency.local()) {
            if (dependency.path().isPresent()) {
                final Path path = dependency.path().orElseThrow();
                if (Files.exists(path)) {
                    final Integrity integrity = integrity(path);
                    return new DependencyState(
                        "present",
                        artifactKind(path),
                        path.toString(),
                        relativePath(root, path),
                        size(path),
                        "sha256",
                        integrity.checksum(),
                        JavanCoordinateResolver.repositoryOrigin(dependency),
                        metadata(path, outputDirectory, integrity)
                    );
                }
                return new DependencyState(
                    "missing-coordinate",
                    artifactKind(path),
                    path.toString(),
                    relativePath(root, path),
                    0L,
                    "none",
                    "",
                    "",
                    unknownLicense()
                );
            }
            return new DependencyState(
                "unsupported-coordinate",
                "coordinate",
                "",
                "",
                0L,
                "none",
                "",
                "",
                unknownLicense()
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
                "",
                "",
                unknownLicense()
            );
        }
        final Integrity integrity = integrity(path);
        return new DependencyState(
            "present",
            artifactKind(path),
            path.toString(),
            relativePath(root, path),
            size(path),
            "sha256",
            integrity.checksum(),
            "",
            metadata(path, outputDirectory, integrity)
        );
    }

    private ArtifactMetadata.License metadata(
        final Path artifact,
        final Path outputDirectory,
        final Integrity integrity
    ) throws IOException, InterruptedException {
        final Path contents = isJar(artifact) && integrity.zip()
            ? jarCache.extract(artifact, outputDirectory, integrity.checksum())
            : artifact;
        return ArtifactMetadata.read(artifact, contents).license();
    }

    private static ArtifactMetadata.License unknownLicense() {
        return new ArtifactMetadata.License("unknown", "unknown", "", "none", "");
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

    private static Integrity integrity(final Path path) throws IOException {
        final Sha256 digest = new Sha256();
        if (Files.isRegularFile(path)) {
            final byte[] content = Files.readAllBytes(path);
            final boolean zip = content.length >= 4 && content[0] == 'P' && content[1] == 'K';
            return new Integrity(digest.update(content).hex(), zip);
        }
        digest.update("javan-directory-sha256-v1\n".getBytes(StandardCharsets.UTF_8));
        for (final Path file : files(path)) {
            final byte[] name = slash(path.relativize(file)).getBytes(StandardCharsets.UTF_8);
            final byte[] content = Files.readAllBytes(file);
            digest.updateInt(name.length).update(name).updateLong(content.length).update(content);
        }
        return new Integrity(digest.hex(), false);
    }

    private static long legacyHash(final Path path) throws IOException {
        long result = 0xcbf29ce484222325L;
        if (Files.isRegularFile(path)) {
            return legacyHash(Files.readAllBytes(path), result);
        }
        for (final Path file : files(path)) {
            result = legacyHashString(slash(path.relativize(file)), result);
            result = legacyHash(Files.readAllBytes(file), result);
        }
        return result;
    }

    private static long legacyHash(final byte[] value, final long seed) {
        long result = seed;
        for (int index = 0; index < value.length; index++) {
            result ^= value[index] & 0xffL;
            result *= 0x100000001b3L;
        }
        return result;
    }

    private static long legacyHashString(final String value, final long seed) {
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

    private static void appendBoolean(final StringBuilder json, final String name, final boolean value, final boolean comma) {
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
        String checksum,
        String repositoryOrigin,
        ArtifactMetadata.License license
    ) {
    }

    private record Integrity(String checksum, boolean zip) {
    }

}
