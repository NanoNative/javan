package javan.classfile;

import javan.detect.ProjectLayout;
import javan.util.Files2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans application class folders and jar inputs into parsed class files.
 */
public final class ClassFileScanner {
    private final ClassFileReader reader = new ClassFileReader();

    /**
     * Scans class files from a detected layout.
     *
     * @param layout project layout
     * @return classes keyed by JVM internal class name
     * @throws IOException when class files cannot be read
     */
    public Map<String, ClassFile> scan(final ProjectLayout layout) throws IOException {
        final Map<String, ClassFile> classes = new LinkedHashMap<>();
        for (final Path folder : layout.classFolders()) {
            scanFolder(folder, classes, true);
        }
        for (final Path entry : layout.classpathEntries()) {
            if (entry.getFileName().toString().endsWith(".jar")) {
                scanJar(entry, classes, entry.equals(layout.input()));
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
            try (InputStream input = Files.newInputStream(classFile)) {
                final ClassFile parsed = reader.read(input, classFile);
                classes.put(parsed.name(), parsed.withApplication(application));
            }
        }
    }

    private void scanJar(final Path jar, final Map<String, ClassFile> classes, final boolean application) throws IOException {
        if (!Files.exists(jar)) {
            return;
        }
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            final java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        final ClassFile parsed = reader.read(input, jar.resolve(entry.getName()));
                        classes.putIfAbsent(parsed.name(), parsed.withApplication(application));
                    }
                }
            }
        }
    }
}
