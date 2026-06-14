package javan.classfile;

import javan.detect.ProjectLayout;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scans application class folders and jar inputs into parsed class files.
 */
public final class ClassFileScanner {
    private final ClassFileReader reader = new ClassFileReader();
    private final ProcessRunner processRunner;

    /**
     * Creates a scanner using the local toolchain.
     */
    public ClassFileScanner() {
        this(new ProcessRunner());
    }

    /**
     * Creates a scanner.
     *
     * @param processRunner process runner used for JDK jar extraction
     */
    public ClassFileScanner(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Scans class files from a detected layout.
     *
     * @param layout project layout
     * @return classes keyed by JVM internal class name
     * @throws IOException when class files cannot be read
     * @throws InterruptedException when interrupted while extracting jars
     */
    public Map<String, ClassFile> scan(final ProjectLayout layout) throws IOException, InterruptedException {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        for (final Path folder : layout.classFolders()) {
            scanFolder(folder, classes, true);
        }
        for (final Path entry : layout.classpathEntries()) {
            if (entry.getFileName().toString().endsWith(".jar")) {
                scanJar(entry, classes, entry.equals(layout.input()), layout.outputDirectory());
            } else if (Files.isDirectory(entry)) {
                scanFolder(entry, classes, entry.equals(layout.input()));
            }
        }
        return Map.copyOf(classes);
    }

    private void scanFolder(final Path folder, final Map<String, ClassFile> classes, final boolean application) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        for (final Path classFile : Files2.findClassFiles(folder)) {
            final ClassFile parsed = reader.read(Files.readAllBytes(classFile), classFile);
            classes.put(parsed.name(), parsed.withApplication(application));
        }
    }

    private void scanJar(
        final Path jar,
        final Map<String, ClassFile> classes,
        final boolean application,
        final Path outputDirectory
    ) throws IOException, InterruptedException {
        if (!Files.exists(jar)) {
            return;
        }
        final Path cache = outputDirectory.resolve("jar-cache").resolve(cacheName(jar));
        Files2.deleteRecursive(cache);
        Files.createDirectories(cache);
        final ProcessRunner.Result result = processRunner.run(cache, List.of("jar", "--extract", "--file", jar.toAbsolutePath().toString()));
        if (result.exitCode() != 0) {
            throw new IOException("Unable to extract jar " + jar.toString() + ": " + result.stderr());
        }
        scanFolder(cache, classes, application);
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
}
