package javan.classfile;

import javan.detect.ProjectLayout;
import javan.toolchain.CurrentJdkTools;
import javan.util.Files2;
import javan.util.ProcessRunner;
import javan.util.Strings2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
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
        return scanAll(layout).classes();
    }

    /**
     * Scans classes and standard service-provider declarations in one classpath traversal.
     *
     * @param layout project layout
     * @return immutable closed-world scan
     * @throws IOException when inputs cannot be read
     * @throws InterruptedException when interrupted while extracting jars
     */
    public ScanResult scanAll(final ProjectLayout layout) throws IOException, InterruptedException {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        final Map<String, LinkedHashSet<ServiceProvider>> providers = new LinkedHashMap<>();
        for (final Path folder : layout.classFolders()) {
            scanFolder(folder, classes, providers, true);
        }
        for (final Path entry : layout.classpathEntries()) {
            if (entry.getFileName().toString().endsWith(".jar")) {
                scanJar(entry, classes, providers, entry.equals(layout.input()), layout.outputDirectory());
            } else if (Files.isDirectory(entry)) {
                scanFolder(entry, classes, providers, entry.equals(layout.input()));
            }
        }
        final Map<String, List<ServiceProvider>> immutableProviders = new LinkedHashMap<>();
        for (final Map.Entry<String, LinkedHashSet<ServiceProvider>> entry : providers.entrySet()) {
            immutableProviders.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new ScanResult(
            Collections.unmodifiableMap(new LinkedHashMap<>(classes)),
            Collections.unmodifiableMap(immutableProviders)
        );
    }

    /**
     * Reads one class from the current JDK without inventorying every module.
     *
     * @param jvmName JVM internal class name
     * @param outputDirectory build output used for native-compiler extraction fallback
     * @return parsed class file
     * @throws IOException when the class or runtime image cannot be read
     * @throws InterruptedException when runtime-image extraction is interrupted
     */
    public ClassFile readRuntimeClass(final String jvmName, final Path outputDirectory)
        throws IOException, InterruptedException {
        final String resource = jvmName + ".class";
        final Path javaHome = CurrentJdkTools.home();
        final Path modules = javaHome.resolve("lib/modules");
        final Path cache = outputDirectory.resolve("reflection-jimage-cache");
        Files.createDirectories(cache);
        Path classFile = extractedRuntimeClass(cache, resource);
        if (classFile == null) {
            final ProcessRunner.Result extraction = processRunner.run(cache, List.of(
                CurrentJdkTools.jimage(),
                "extract",
                "--dir",
                cache.toAbsolutePath().toString(),
                "--include",
                "glob:**/" + resource,
                modules.toAbsolutePath().toString()
            ));
            if (extraction.exitCode() != 0) {
                throw new IOException("Unable to extract runtime class " + jvmName + ": " + extraction.stderr());
            }
            classFile = extractedRuntimeClass(cache, resource);
        }
        if (classFile == null) {
            throw new IOException("Missing reflected class metadata: " + jvmName);
        }
        return reader.read(Files.readAllBytes(classFile), classFile);
    }

    private static Path extractedRuntimeClass(final Path cache, final String resource) throws IOException {
        for (final Path classFile : Files2.findClassFiles(cache)) {
            if (classFile.toString().replace('\\', '/').endsWith("/" + resource)) {
                return classFile;
            }
        }
        return null;
    }

    private void scanFolder(
        final Path folder,
        final Map<String, ClassFile> classes,
        final Map<String, LinkedHashSet<ServiceProvider>> providers,
        final boolean application
    ) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        for (final Path classFile : Files2.findClassFiles(folder)) {
            final ClassFile parsed = reader.read(Files.readAllBytes(classFile), classFile);
            for (final ServiceProvider provider : parsed.serviceProviders()) {
                providers.computeIfAbsent(provider.service(), ignored -> new LinkedHashSet<>()).add(provider);
            }
            classes.put(parsed.name(), parsed.withApplication(application));
        }
        final Path descriptors = folder.resolve("META-INF/services");
        if (Files.isDirectory(descriptors)) {
            for (final Path descriptor : Files2.findResourceFiles(descriptors)) {
                if (descriptors.equals(descriptor.getParent())) {
                    readServiceDescriptor(descriptor, providers);
                }
            }
        }
    }

    private static void readServiceDescriptor(
        final Path descriptor,
        final Map<String, LinkedHashSet<ServiceProvider>> providers
    ) throws IOException {
        final String service = descriptor.getFileName().toString().replace('.', '/');
        final LinkedHashSet<ServiceProvider> declarations = providers.computeIfAbsent(service, ignored -> new LinkedHashSet<>());
        final String content = Files.readString(descriptor);
        int start = 0;
        while (start <= content.length()) {
            int end = start;
            while (end < content.length() && content.charAt(end) != '\n' && content.charAt(end) != '\r') {
                end++;
            }
            final String line = Strings2.slice(content, start, end);
            final int comment = line.indexOf('#');
            final String provider = Strings2.trimAscii(comment < 0 ? line : Strings2.slice(line, 0, comment));
            if (!provider.isEmpty()) {
                declarations.add(new ServiceProvider(service, Strings2.replaceChar(provider, '.', '/'), false));
            }
            while (end < content.length() && (content.charAt(end) == '\n' || content.charAt(end) == '\r')) {
                end++;
            }
            if (end == content.length()) {
                break;
            }
            start = end;
        }
    }

    private void scanJar(
        final Path jar,
        final Map<String, ClassFile> classes,
        final Map<String, LinkedHashSet<ServiceProvider>> providers,
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
        scanFolder(cache, classes, providers, application);
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

    /** Immutable classes and service providers discovered from the same classpath snapshot. */
    public record ScanResult(Map<String, ClassFile> classes, Map<String, List<ServiceProvider>> serviceProviders) {
    }
}
