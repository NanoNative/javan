package javan.compat;

import javan.detect.ProjectLayout;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans classfile metadata from project inputs, dependencies, and the runtime JDK.
 */
public final class ClassMetadataScanner {
    private final ClassMetadataReader reader = new ClassMetadataReader();
    private final ProcessRunner processRunner;

    /**
     * Creates a scanner using the local toolchain.
     */
    public ClassMetadataScanner() {
        this(new ProcessRunner());
    }

    /**
     * Creates a scanner.
     *
     * @param processRunner process runner used for JDK archive extraction
     */
    public ClassMetadataScanner(final ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    /**
     * Scans application and dependency classfiles from a detected layout.
     *
     * @param layout project layout
     * @return metadata sorted by class name
     * @throws IOException when classfiles cannot be read
     * @throws InterruptedException when interrupted while extracting archives
     */
    public List<ClassMetadata> scanLayout(final ProjectLayout layout) throws IOException, InterruptedException {
        final List<ClassMetadata> result = new ArrayList<>();
        for (final Path folder : layout.classFolders()) {
            scanFolder(folder, true, "", result);
        }
        for (final Path entry : layout.classpathEntries()) {
            if (entry.getFileName().toString().endsWith(".jar")) {
                scanArchive(entry, entry.equals(layout.input()), "", layout.outputDirectory().resolve("metadata-jar-cache"), result);
            } else if (Files.isDirectory(entry)) {
                scanFolder(entry, entry.equals(layout.input()), "", result);
            }
        }
        return sorted(result);
    }

    /**
     * Scans the current runtime JDK modules from {@code $java.home/jmods}.
     *
     * @return JDK class metadata
     * @throws IOException when the runtime image cannot be read
     * @throws InterruptedException when interrupted while extracting jmods
     */
    public List<ClassMetadata> scanCurrentJdk(final Path outputDirectory) throws IOException, InterruptedException {
        final String javaHome = javaHome();
        final Path jmods = Path.of(javaHome).resolve("jmods");
        final List<ClassMetadata> result = new ArrayList<>();
        if (Files.isDirectory(jmods)) {
            scanJmods(jmods, outputDirectory, result);
            return sorted(result);
        }
        final Path modulesImage = Path.of(javaHome).resolve("lib/modules");
        final Path jimage = Path.of(javaHome).resolve("bin/jimage");
        if (Files.isRegularFile(modulesImage) && Files.isRegularFile(jimage)) {
            scanJimage(modulesImage, jimage, outputDirectory.resolve("metadata-jimage-cache"), result);
            return sorted(result);
        }
        throw new IOException(
            "Unable to locate runtime JDK inventory source: missing "
                + jmods.toString()
                + " and "
                + modulesImage.toString()
                + " with "
                + jimage.toString()
        );
    }

    private void scanJmods(final Path jmods, final Path outputDirectory, final List<ClassMetadata> result)
        throws IOException, InterruptedException {
        final List<Path> modules = new ArrayList<>();
        final DirectoryStream<Path> files = Files.newDirectoryStream(jmods);
        for (final Path file : files) {
            if (file.getFileName().toString().endsWith(".jmod")) {
                insertPath(modules, file);
            }
        }
        files.close();
        for (final Path module : modules) {
            scanJmod(module, outputDirectory.resolve("metadata-jmod-cache"), result);
        }
    }

    /**
     * Scans the current runtime JDK modules into the default local work directory.
     *
     * @return JDK class metadata
     * @throws IOException when the runtime image cannot be read
     * @throws InterruptedException when interrupted while extracting jmods
     */
    public List<ClassMetadata> scanCurrentJdk() throws IOException, InterruptedException {
        return scanCurrentJdk(Path.of(".javan"));
    }

    private static String javaHome() throws IOException {
        final String property = System.getProperty("java.home");
        if (property != null && !Strings2.isBlank(property)) {
            return property;
        }
        final String environment = System.getenv("JAVA_HOME");
        if (environment != null && !Strings2.isBlank(environment)) {
            return environment;
        }
        throw new IOException("Unable to locate Java home: java.home and JAVA_HOME are both unset");
    }

    private void scanFolder(
        final Path folder,
        final boolean application,
        final String moduleName,
        final List<ClassMetadata> result
    ) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        for (final Path classFile : Files2.findClassFiles(folder)) {
            result.add(reader.read(Files.readAllBytes(classFile), classFile).withApplication(application).withModuleName(moduleName));
        }
    }

    private void scanArchive(
        final Path archive,
        final boolean application,
        final String moduleName,
        final Path cacheRoot,
        final List<ClassMetadata> result
    ) throws IOException, InterruptedException {
        if (!Files.exists(archive)) {
            return;
        }
        final Path cache = extractArchive(archive, cacheRoot, "");
        scanFolder(cache, application, moduleName, result);
    }

    private void scanJmod(final Path jmod, final Path cacheRoot, final List<ClassMetadata> result) throws IOException, InterruptedException {
        final String fileName = jmod.getFileName().toString();
        final String moduleName = fileName.substring(0, fileName.length() - ".jmod".length());
        final Path cache = extractArchive(jmod, cacheRoot, "classes");
        scanFolder(cache.resolve("classes"), false, moduleName, result);
    }

    private void scanJimage(
        final Path modulesImage,
        final Path jimage,
        final Path cacheRoot,
        final List<ClassMetadata> result
    ) throws IOException, InterruptedException {
        final Path cache = cacheRoot.resolve(cacheName(modulesImage));
        Files2.deleteRecursive(cache);
        Files.createDirectories(cache);
        final List<String> command = List.of(
            jimage.toAbsolutePath().toString(),
            "extract",
            "--dir",
            cache.toAbsolutePath().toString(),
            modulesImage.toAbsolutePath().toString()
        );
        final ProcessRunner.Result extracted = processRunner.run(cache, command);
        if (extracted.exitCode() != 0) {
            throw new IOException("Unable to extract runtime image " + modulesImage.toString() + ": " + extracted.stderr());
        }
        final List<Path> modules = new ArrayList<>();
        final DirectoryStream<Path> files = Files.newDirectoryStream(cache);
        for (final Path file : files) {
            if (Files.isDirectory(file)) {
                insertPath(modules, file);
            }
        }
        files.close();
        for (final Path module : modules) {
            scanFolder(module, false, module.getFileName().toString(), result);
        }
    }

    private Path extractArchive(final Path archive, final Path cacheRoot, final String entryPrefix)
        throws IOException, InterruptedException {
        final Path cache = cacheRoot.resolve(cacheName(archive));
        Files2.deleteRecursive(cache);
        Files.createDirectories(cache);
        final List<String> command = extractionCommand(archive, entryPrefix);
        final ProcessRunner.Result extracted = processRunner.run(cache, command);
        if (extracted.exitCode() != 0) {
            throw new IOException("Unable to extract archive " + archive.toString() + ": " + extracted.stderr());
        }
        return cache;
    }

    private static List<String> extractionCommand(final Path archive, final String entryPrefix) {
        if (Strings2.isBlank(entryPrefix)) {
            return List.of("jar", "--extract", "--file", archive.toAbsolutePath().toString());
        }
        return List.of("jar", "--extract", "--file", archive.toAbsolutePath().toString(), entryPrefix);
    }

    private static String cacheName(final Path archive) throws IOException {
        final Path fileName = archive.getFileName();
        final String base = fileName == null ? "archive" : fileName.toString();
        return Strings2.executableName(base) + "-" + Strings2.hexLong(pathHash(archive)) + "-" + Files.size(archive);
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

    private static List<ClassMetadata> sorted(final List<ClassMetadata> values) {
        final List<ClassMetadata> result = new ArrayList<>(values);
        if (result.size() > 1) {
            final List<ClassMetadata> buffer = new ArrayList<>(values);
            sortMetadata(result, buffer, 0, result.size());
        }
        return List.copyOf(result);
    }

    private static void insertPath(final List<Path> values, final Path value) {
        int index = 0;
        while (index < values.size() && Strings2.compareAscii(values.get(index).toString(), value.toString()) <= 0) {
            index++;
        }
        values.add(index, value);
    }

    private static void sortMetadata(
        final List<ClassMetadata> values,
        final List<ClassMetadata> buffer,
        final int start,
        final int end
    ) {
        if (end - start < 2) {
            return;
        }
        final int middle = start + ((end - start) / 2);
        sortMetadata(values, buffer, start, middle);
        sortMetadata(values, buffer, middle, end);
        mergeMetadata(values, buffer, start, middle, end);
    }

    private static void mergeMetadata(
        final List<ClassMetadata> values,
        final List<ClassMetadata> buffer,
        final int start,
        final int middle,
        final int end
    ) {
        int left = start;
        int right = middle;
        int output = start;
        while (left < middle && right < end) {
            if (compareMetadata(values.get(left), values.get(right)) <= 0) {
                buffer.set(output, values.get(left));
                left++;
            } else {
                buffer.set(output, values.get(right));
                right++;
            }
            output++;
        }
        while (left < middle) {
            buffer.set(output, values.get(left));
            left++;
            output++;
        }
        while (right < end) {
            buffer.set(output, values.get(right));
            right++;
            output++;
        }
        for (int index = start; index < end; index++) {
            values.set(index, buffer.get(index));
        }
    }

    private static int compareMetadata(final ClassMetadata left, final ClassMetadata right) {
        final int name = Strings2.compareAscii(left.name(), right.name());
        if (name != 0) {
            return name;
        }
        return Strings2.compareAscii(left.source().toString(), right.source().toString());
    }
}
