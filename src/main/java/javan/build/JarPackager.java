package javan.build;

import javan.detect.InputKind;
import javan.detect.ProjectLayout;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Builds deterministic Java jar output from compiled classes and copied resources.
 */
public final class JarPackager {
    /**
     * Packages a Java jar.
     *
     * @param layout detected project layout
     * @param output target jar
     * @param mainClass optional manifest main class
     * @return target jar
     * @throws IOException when packaging fails
     */
    public Path packageJar(final ProjectLayout layout, final Path output, final Optional<String> mainClass) throws IOException {
        Files.createDirectories(output.getParent());
        if (layout.inputKind() == InputKind.JAR_FILE) {
            Files.copy(layout.input(), output, StandardCopyOption.REPLACE_EXISTING);
            return output;
        }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output), manifest(mainClass))) {
            final LinkedHashSet<String> written = new LinkedHashSet<>();
            for (final Path root : layout.classFolders()) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                final List<Path> files = javan.util.Files2.findFiles(root, Files::isRegularFile);
                for (final Path file : files) {
                    final String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                    if (name.equals("META-INF/MANIFEST.MF") || !written.add(name)) {
                        continue;
                    }
                    final JarEntry entry = new JarEntry(name);
                    entry.setTime(0);
                    jar.putNextEntry(entry);
                    try (InputStream input = Files.newInputStream(file)) {
                        input.transferTo(jar);
                    }
                    jar.closeEntry();
                }
            }
        }
        return output;
    }

    private static Manifest manifest(final Optional<String> mainClass) {
        final Manifest manifest = new Manifest();
        final Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainClass.ifPresent(value -> attributes.put(Attributes.Name.MAIN_CLASS, value));
        return manifest;
    }
}
