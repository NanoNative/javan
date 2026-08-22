package javan.dependency;

import javan.util.Files2;
import javan.util.Json;
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
    /**
     * Writes {@code javan.lock} when {@code javan.mod} is present.
     *
     * @param root project root
     * @param module parsed module
     * @return lock path
     * @throws IOException when filesystem metadata, lock verification, or lock writing fails
     */
    public Path write(final Path root, final JavanModule module) throws IOException {
        final Path lock = root.resolve("javan.lock");
        if (!module.present()) {
            return lock;
        }
        final List<DependencyState> states = states(root, module);
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

    private static List<DependencyState> states(final Path root, final JavanModule module) throws IOException {
        final List<DependencyState> result = new ArrayList<>();
        for (final JavanDependency dependency : module.dependencies()) {
            result.add(state(root, dependency));
        }
        return List.copyOf(result);
    }

    private static String render(final JavanModule module, final List<DependencyState> states) {
        final StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"lockVersion\": 1,\n");
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

    private static void verify(
        final JavanModule module,
        final List<DependencyState> states,
        final String existing
    ) throws IOException {
        if (!existing.contains("  \"lockVersion\": 1,")) {
            throw new IOException("Unsupported or malformed javan.lock: expected lockVersion 1");
        }
        if (!existing.contains("  \"module\": " + Json.string(module.moduleName()) + ",")
            || !existing.contains("  \"java\": " + Json.string(module.javaVersion()) + ",")) {
            return;
        }
        final List<String> blocks = dependencyBlocks(existing);
        if (blocks.size() != module.dependencies().size()) {
            return;
        }
        for (int index = 0; index < blocks.size(); index++) {
            if (!sameDeclaration(blocks.get(index), module.dependencies().get(index))) {
                return;
            }
        }
        for (int index = 0; index < blocks.size(); index++) {
            verifyChecksum(module.dependencies().get(index), states.get(index), blocks.get(index));
        }
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
            && hasText(block, "notation", dependency.notation());
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
                        "sha256",
                        checksum(path)
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
            "sha256",
            checksum(path)
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

    private static String checksum(final Path path) throws IOException {
        final Sha256 digest = new Sha256();
        if (Files.isRegularFile(path)) {
            return digest.update(Files.readAllBytes(path)).hex();
        }
        digest.update("javan-directory-sha256-v1\n".getBytes(StandardCharsets.UTF_8));
        for (final Path file : files(path)) {
            final byte[] name = slash(path.relativize(file)).getBytes(StandardCharsets.UTF_8);
            final byte[] content = Files.readAllBytes(file);
            digest.updateInt(name.length).update(name).updateLong(content.length).update(content);
        }
        return digest.hex();
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

    private static final class Sha256 {
        private static final String HEX = "0123456789abcdef";
        private static final int[] ROUND = {
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
        };
        private final int[] state = {
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        };
        private final byte[] block = new byte[64];
        private long length;
        private int blockLength;
        private boolean finished;

        private Sha256 update(final byte[] value) {
            for (int index = 0; index < value.length; index++) {
                updateByte(value[index] & 0xff);
            }
            return this;
        }

        private Sha256 updateInt(final int value) {
            updateByte(value >>> 24);
            updateByte(value >>> 16);
            updateByte(value >>> 8);
            updateByte(value);
            return this;
        }

        private Sha256 updateLong(final long value) {
            updateInt((int) (value >>> 32));
            updateInt((int) value);
            return this;
        }

        private void updateByte(final int value) {
            if (finished) {
                throw new IllegalStateException("SHA-256 is already finished");
            }
            block[blockLength] = (byte) value;
            blockLength++;
            length++;
            if (blockLength == block.length) {
                compress();
            }
        }

        private String hex() {
            finish();
            final StringBuilder result = new StringBuilder(64);
            for (int index = 0; index < state.length; index++) {
                for (int shift = 28; shift >= 0; shift -= 4) {
                    result.append(HEX.charAt((state[index] >>> shift) & 15));
                }
            }
            return result.toString();
        }

        private void finish() {
            if (finished) {
                return;
            }
            final long bits = length * 8L;
            updateByte(0x80);
            while (blockLength != 56) {
                updateByte(0);
            }
            for (int shift = 56; shift >= 0; shift -= 8) {
                updateByte((int) (bits >>> shift));
            }
            finished = true;
        }

        private void compress() {
            final int[] words = new int[64];
            for (int index = 0; index < 16; index++) {
                final int offset = index * 4;
                words[index] = ((block[offset] & 0xff) << 24)
                    | ((block[offset + 1] & 0xff) << 16)
                    | ((block[offset + 2] & 0xff) << 8)
                    | (block[offset + 3] & 0xff);
            }
            for (int index = 16; index < words.length; index++) {
                final int first = rotateRight(words[index - 15], 7)
                    ^ rotateRight(words[index - 15], 18)
                    ^ (words[index - 15] >>> 3);
                final int second = rotateRight(words[index - 2], 17)
                    ^ rotateRight(words[index - 2], 19)
                    ^ (words[index - 2] >>> 10);
                words[index] = words[index - 16] + first + words[index - 7] + second;
            }
            int a = state[0];
            int b = state[1];
            int c = state[2];
            int d = state[3];
            int e = state[4];
            int f = state[5];
            int g = state[6];
            int h = state[7];
            for (int index = 0; index < words.length; index++) {
                final int first = h
                    + (rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25))
                    + ((e & f) ^ (~e & g))
                    + ROUND[index]
                    + words[index];
                final int second = (rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22))
                    + ((a & b) ^ (a & c) ^ (b & c));
                h = g;
                g = f;
                f = e;
                e = d + first;
                d = c;
                c = b;
                b = a;
                a = first + second;
            }
            state[0] += a;
            state[1] += b;
            state[2] += c;
            state[3] += d;
            state[4] += e;
            state[5] += f;
            state[6] += g;
            state[7] += h;
            blockLength = 0;
        }

        private static int rotateRight(final int value, final int distance) {
            return (value >>> distance) | (value << (32 - distance));
        }
    }
}
