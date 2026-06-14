package javan.util;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * File helpers for deterministic project scanning and output generation.
 */
public final class Files2 {
    private static final int MATCH_JAVA = 1;
    private static final int MATCH_CLASS = 2;
    private static final int MATCH_RESOURCE = 3;
    private static final String[] IGNORED_DIRECTORIES = {
        ".git", ".idea", ".gradle", ".mvn", ".javan", "target", "build", "out", "node_modules"
    };

    private Files2() {
    }

    /**
     * Creates the parent directory and writes UTF-8 text.
     *
     * @param path target file
     * @param value text content
     * @return the target file
     * @throws IOException when the file cannot be written
     */
    public static Path writeString(final Path path, final String value) throws IOException {
        final Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.writeString(path, value);
    }

    /**
     * Reads UTF-8 text or returns an empty string when the file does not exist.
     *
     * @param path source file
     * @return file content or empty string
     * @throws IOException when the file exists but cannot be read
     */
    public static String readStringIfExists(final Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    /**
     * Deletes a file tree if it exists.
     *
     * @param path file or directory to delete
     * @return the deleted path
     * @throws IOException when deletion fails
     */
    public static Path deleteRecursive(final Path path) throws IOException {
        if (!Files.exists(path)) {
            return path;
        }
        final List<Path> files = new ArrayList<>();
        collectTree(path, files);
        sortPaths(files);
        for (int index = files.size() - 1; index >= 0; index--) {
            Files.deleteIfExists(files.get(index));
        }
        return path;
    }

    /**
     * Finds files below a root while skipping build and VCS directories.
     *
     * @param root scan root
     * @param matchKind file match kind
     * @return sorted matching files
     * @throws IOException when scanning fails
     */
    private static List<Path> findFiles(final Path root, final int matchKind) throws IOException {
        if (!Files.exists(root)) {
            return new ArrayList<>();
        }
        final List<Path> result = new ArrayList<>();
        collectMatchingFiles(root, root, matchKind, result);
        sortPaths(result);
        return result;
    }

    private static void collectTree(final Path path, final List<Path> result) throws IOException {
        result.add(path);
        if (!Files.isDirectory(path)) {
            return;
        }
        final DirectoryStream<Path> children = Files.newDirectoryStream(path);
        for (final Path child : children) {
            collectTree(child, result);
        }
        children.close();
    }

    private static void collectMatchingFiles(
        final Path root,
        final Path current,
        final int matchKind,
        final List<Path> result
    ) throws IOException {
        if (Files.isDirectory(current)) {
            final Path name = current.getFileName();
            if (name != null && ignoredDirectory(name.toString()) && topLevelChild(root, current)) {
                return;
            }
            final DirectoryStream<Path> children = Files.newDirectoryStream(current);
            for (final Path child : children) {
                collectMatchingFiles(root, child, matchKind, result);
            }
            children.close();
            return;
        }
        if (Files.isRegularFile(current) && matches(current, matchKind)) {
            result.add(current);
        }
    }

    private static boolean matches(final Path file, final int matchKind) {
        final String name = file.getFileName().toString();
        if (matchKind == MATCH_JAVA) {
            if (name.endsWith(".java")) {
                return true;
            }
            return false;
        }
        if (matchKind == MATCH_CLASS) {
            if (name.endsWith(".class")) {
                return true;
            }
            return false;
        }
        if (matchKind == MATCH_RESOURCE) {
            if (name.endsWith(".java")) {
                return false;
            }
            if (name.endsWith(".class")) {
                return false;
            }
            return true;
        }
        throw new IllegalArgumentException("Unsupported file match kind");
    }

    private static boolean ignoredDirectory(final String name) {
        for (int index = 0; index < IGNORED_DIRECTORIES.length; index++) {
            if (IGNORED_DIRECTORIES[index].equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean topLevelChild(final Path root, final Path dir) {
        if (dir.equals(root)) {
            return false;
        }
        final Path absoluteRoot = root.toAbsolutePath().normalize();
        final Path absoluteDir = dir.toAbsolutePath().normalize();
        final Path parent = absoluteDir.getParent();
        if (parent == null) {
            return false;
        }
        if (!parent.equals(absoluteRoot)) {
            return false;
        }
        return true;
    }

    private static void sortPaths(final List<Path> paths) {
        for (int index = 1; index < paths.size(); index++) {
            final Path value = paths.get(index);
            int position = index - 1;
            while (position >= 0 && Strings2.compareAscii(paths.get(position).toString(), value.toString()) > 0) {
                paths.set(position + 1, paths.get(position));
                position--;
            }
            paths.set(position + 1, value);
        }
    }

    /**
     * Finds Java source files below a root.
     *
     * @param root scan root
     * @return sorted Java source files
     * @throws IOException when scanning fails
     */
    public static List<Path> findJavaSources(final Path root) throws IOException {
        return findFiles(root, MATCH_JAVA);
    }

    /**
     * Finds class files below a root.
     *
     * @param root scan root
     * @return sorted class files
     * @throws IOException when scanning fails
     */
    public static List<Path> findClassFiles(final Path root) throws IOException {
        return findFiles(root, MATCH_CLASS);
    }

    /**
     * Finds non-source, non-class resource files below a root.
     *
     * @param root scan root
     * @return sorted resource files
     * @throws IOException when scanning fails
     */
    public static List<Path> findResourceFiles(final Path root) throws IOException {
        return findFiles(root, MATCH_RESOURCE);
    }

    /**
     * Returns true when any Java source is newer than every class file.
     *
     * @param sourceRoots source roots
     * @param classRoots class roots
     * @return true when compilation is likely needed
     * @throws IOException when scanning fails
     */
    public static boolean sourcesNewerThanClasses(final List<Path> sourceRoots, final List<Path> classRoots) throws IOException {
        final long newestSource = newestModified(sourceRoots, ".java");
        final long newestClass = newestModified(classRoots, ".class");
        if (newestSource <= 0) {
            return false;
        }
        if (newestClass == 0) {
            return true;
        }
        if (newestSource > newestClass) {
            return true;
        }
        return false;
    }

    /**
     * Returns the newest matching file timestamp below the given roots.
     *
     * @param roots scan roots
     * @param suffix filename suffix
     * @return epoch milliseconds or zero
     * @throws IOException when scanning fails
     */
    public static long newestModified(final List<Path> roots, final String suffix) throws IOException {
        long newest = 0;
        for (final Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            newest = Math.max(newest, newestModified(root, suffix));
        }
        return newest;
    }

    private static long newestModified(final Path path, final String suffix) throws IOException {
        if (Files.isDirectory(path)) {
            long newest = 0;
            final DirectoryStream<Path> children = Files.newDirectoryStream(path);
            for (final Path child : children) {
                newest = Math.max(newest, newestModified(child, suffix));
            }
            children.close();
            return newest;
        }
        if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(suffix)) {
            return Files.getLastModifiedTime(path).toMillis();
        }
        return 0;
    }

    /**
     * Returns true when a directory contains at least one class file.
     *
     * @param root directory to inspect
     * @return true when a class file exists below the directory
     * @throws IOException when scanning fails
     */
    public static boolean containsClassFile(final Path root) throws IOException {
        if (findClassFiles(root).isEmpty()) {
            return false;
        }
        return true;
    }

}
