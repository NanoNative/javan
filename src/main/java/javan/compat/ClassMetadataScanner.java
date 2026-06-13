package javan.compat;

import javan.detect.ProjectLayout;
import javan.util.Files2;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Scans classfile metadata from project inputs, dependencies, and the runtime JDK.
 */
public final class ClassMetadataScanner {
    private final ClassMetadataReader reader = new ClassMetadataReader();

    /**
     * Scans application and dependency classfiles from a detected layout.
     *
     * @param layout project layout
     * @return metadata sorted by class name
     * @throws IOException when classfiles cannot be read
     */
    public List<ClassMetadata> scanLayout(final ProjectLayout layout) throws IOException {
        final List<ClassMetadata> result = new ArrayList<>();
        for (final Path folder : layout.classFolders()) {
            scanFolder(folder, true, "", result);
        }
        for (final Path entry : layout.classpathEntries()) {
            if (entry.getFileName().toString().endsWith(".jar")) {
                scanJar(entry, entry.equals(layout.input()), result);
            } else if (Files.isDirectory(entry)) {
                scanFolder(entry, entry.equals(layout.input()), "", result);
            }
        }
        return sorted(result);
    }

    /**
     * Scans the current runtime JDK image through the {@code jrt:/} filesystem.
     *
     * @return JDK class metadata
     * @throws IOException when the runtime image cannot be read
     */
    public List<ClassMetadata> scanCurrentJdk() throws IOException {
        try {
            return scanCurrentJdkImage();
        } catch (final FileSystemNotFoundException | ProviderNotFoundException exception) {
            return scanCurrentJdkJmods();
        }
    }

    private List<ClassMetadata> scanCurrentJdkImage() throws IOException {
        final List<ClassMetadata> result = new ArrayList<>();
        final Path modules = jrtFileSystem().getPath("/modules");
        try (Stream<Path> files = Files.walk(modules)) {
            final List<Path> classFiles = files
                .filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".class"))
                .sorted()
                .toList();
            for (final Path classFile : classFiles) {
                final Path relative = modules.relativize(classFile);
                final String moduleName = relative.getName(0).toString();
                try (InputStream input = Files.newInputStream(classFile)) {
                    result.add(reader.read(input, classFile).withModuleName(moduleName).withApplication(false));
                }
            }
        }
        return sorted(result);
    }

    private List<ClassMetadata> scanCurrentJdkJmods() throws IOException {
        final String javaHome = javaHome();
        final Path jmods = Path.of(javaHome).resolve("jmods");
        if (!Files.isDirectory(jmods)) {
            throw new IOException("Unable to locate runtime JDK inventory source: missing jrt:/ provider and " + jmods);
        }
        final List<ClassMetadata> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(jmods)) {
            final List<Path> modules = files
                .filter(file -> file.getFileName().toString().endsWith(".jmod"))
                .sorted()
                .toList();
            for (final Path module : modules) {
                scanJmod(module, result);
            }
        }
        return sorted(result);
    }

    private static String javaHome() throws IOException {
        final String property = System.getProperty("java.home");
        if (property != null && !property.isBlank()) {
            return property;
        }
        final String environment = System.getenv("JAVA_HOME");
        if (environment != null && !environment.isBlank()) {
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
            try (InputStream input = Files.newInputStream(classFile)) {
                result.add(reader.read(input, classFile).withApplication(application).withModuleName(moduleName));
            }
        }
    }

    private void scanJar(final Path jar, final boolean application, final List<ClassMetadata> result) throws IOException {
        if (!Files.exists(jar)) {
            return;
        }
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            final java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        result.add(reader.read(input, jar.resolve(entry.getName())).withApplication(application));
                    }
                }
            }
        }
    }

    private void scanJmod(final Path jmod, final List<ClassMetadata> result) throws IOException {
        final String fileName = jmod.getFileName().toString();
        final String moduleName = fileName.substring(0, fileName.length() - ".jmod".length());
        try (JarFile jarFile = new JarFile(jmod.toFile())) {
            final java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith("classes/") && entry.getName().endsWith(".class")) {
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        result.add(reader.read(input, jmod.resolve(entry.getName())).withModuleName(moduleName).withApplication(false));
                    }
                }
            }
        }
    }

    private static FileSystem jrtFileSystem() throws IOException {
        final URI uri = URI.create("jrt:/");
        try {
            return FileSystems.getFileSystem(uri);
        } catch (final FileSystemNotFoundException ignored) {
            return FileSystems.newFileSystem(uri, Map.of());
        }
    }

    private static List<ClassMetadata> sorted(final List<ClassMetadata> values) {
        return values.stream()
            .sorted(Comparator.comparing(ClassMetadata::name).thenComparing(metadata -> metadata.source().toString()))
            .toList();
    }
}
