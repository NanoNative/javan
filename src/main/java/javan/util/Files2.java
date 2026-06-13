package javan.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * File helpers for deterministic project scanning and output generation.
 */
public final class Files2 {
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
        ".git", ".idea", ".gradle", ".mvn", ".javan", "target", "build", "out", "node_modules"
    );

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
        return Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    /**
     * Reads UTF-8 text or returns an empty string when the file does not exist.
     *
     * @param path source file
     * @return file content or empty string
     * @throws IOException when the file exists but cannot be read
     */
    public static String readStringIfExists(final Path path) throws IOException {
        return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
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
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (final IOException exception) {
                    throw new IllegalStateException("Unable to delete " + file, exception);
                }
            });
        return path;
    }

    /**
     * Finds files below a root while skipping build and VCS directories.
     *
     * @param root scan root
     * @param matcher file predicate
     * @return sorted matching files
     * @throws IOException when scanning fails
     */
    public static List<Path> findFiles(final Path root, final Predicate<Path> matcher) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        final List<Path> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                final Path name = dir.getFileName();
                if (name != null && IGNORED_DIRECTORIES.contains(name.toString()) && !dir.equals(root)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                if (matcher.test(file)) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result.stream().sorted().toList();
    }

    /**
     * Finds Java source files below a root.
     *
     * @param root scan root
     * @return sorted Java source files
     * @throws IOException when scanning fails
     */
    public static List<Path> findJavaSources(final Path root) throws IOException {
        return findFiles(root, file -> file.getFileName().toString().endsWith(".java"));
    }

    /**
     * Finds class files below a root.
     *
     * @param root scan root
     * @return sorted class files
     * @throws IOException when scanning fails
     */
    public static List<Path> findClassFiles(final Path root) throws IOException {
        return findFiles(root, file -> file.getFileName().toString().endsWith(".class"));
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
        return newestSource > 0 && (newestClass == 0 || newestSource > newestClass);
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
            try (Stream<Path> stream = Files.walk(root)) {
                final long rootNewest = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(suffix))
                    .mapToLong(file -> {
                        try {
                            return Files.getLastModifiedTime(file).toMillis();
                        } catch (final IOException exception) {
                            throw new IllegalStateException("Unable to read timestamp for " + file, exception);
                        }
                    })
                    .max()
                    .orElse(0);
                newest = Math.max(newest, rootNewest);
            }
        }
        return newest;
    }

    /**
     * Returns true when a directory contains at least one class file.
     *
     * @param root directory to inspect
     * @return true when a class file exists below the directory
     * @throws IOException when scanning fails
     */
    public static boolean containsClassFile(final Path root) throws IOException {
        return !findClassFiles(root).isEmpty();
    }
}
