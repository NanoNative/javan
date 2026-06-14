package javan.detect;

import javan.util.Files2;
import javan.util.Strings2;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers production class output directories created by javac, Maven, Gradle, and IDE builds.
 */
public final class ClassOutputDiscovery {
    private ClassOutputDiscovery() {
    }

    /**
     * Discovers class output folders below a project root.
     *
     * @param root project root
     * @return sorted class folders
     * @throws IOException when scanning fails
     */
    public static List<Path> discover(final Path root) throws IOException {
        final List<Path> result = new ArrayList<>();
        final Path normalized = root.toAbsolutePath().normalize();
        collectProjectOutputs(normalized, result);
        return result;
    }

    private static void addCommon(final Path root, final List<Path> result) throws IOException {
        addIfClassFolder(result, root.resolve("target/classes").normalize());
        addIfClassFolder(result, root.resolve("build/classes/java/main").normalize());
        addIfClassFolder(result, root.resolve("build/classes/kotlin/main").normalize());
        addIfClassFolder(result, root.resolve("out/production/classes").normalize());
        addIfClassFolder(result, root.resolve("bin").normalize());
        addIfClassFolder(result, root.resolve("classes").normalize());
        addIfClassFolder(result, root.resolve(".javan/classes").normalize());
    }

    private static void collectProjectOutputs(final Path current, final List<Path> result) throws IOException {
        if (!Files.isDirectory(current)) {
            return;
        }
        if (ignoredDirectory(current)) {
            return;
        }
        addCommon(current, result);
        final DirectoryStream<Path> children = Files.newDirectoryStream(current);
        for (final Path child : children) {
            collectProjectOutputs(child.toAbsolutePath().normalize(), result);
        }
        children.close();
    }

    private static void addIfClassFolder(final List<Path> result, final Path candidate) throws IOException {
        final Path normalized = candidate.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return;
        }
        if (!Files2.containsClassFile(normalized)) {
            return;
        }
        if (!containsPath(result, normalized)) {
            insertSorted(result, normalized);
        }
    }

    private static boolean ignoredDirectory(final Path current) {
        final Path name = current.getFileName();
        if (name == null) {
            return false;
        }
        final String value = name.toString();
        if (".git".equals(value)) {
            return true;
        }
        if (".idea".equals(value)) {
            return true;
        }
        if (".gradle".equals(value)) {
            return true;
        }
        if (".mvn".equals(value)) {
            return true;
        }
        if (".javan".equals(value)) {
            return true;
        }
        if ("node_modules".equals(value)) {
            return true;
        }
        if ("target".equals(value)) {
            return true;
        }
        if ("build".equals(value)) {
            return true;
        }
        if ("out".equals(value)) {
            return true;
        }
        if ("bin".equals(value)) {
            return true;
        }
        if ("classes".equals(value)) {
            return true;
        }
        return false;
    }

    private static boolean containsPath(final List<Path> values, final Path target) {
        final String normalized = target.toAbsolutePath().normalize().toString();
        for (final Path value : values) {
            if (value.toAbsolutePath().normalize().toString().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static void insertSorted(final List<Path> values, final Path value) {
        int index = 0;
        while (index < values.size() && Strings2.compareAscii(values.get(index).toString(), value.toString()) <= 0) {
            index++;
        }
        values.add(index, value);
    }
}
