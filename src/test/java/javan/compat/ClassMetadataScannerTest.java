package javan.compat;

import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ClassMetadataScannerTest {
    @TempDir
    private Path tempDir;

    @Test
    void scanLayoutReadsApplicationFoldersClasspathDirectoriesAndJarDependencies() throws Exception {
        final Path root = tempDir.resolve("layout");
        final Path appClasses = root.resolve("app-classes");
        final Path dependencyClasses = root.resolve("dependency-classes");
        final Path jarClasses = root.resolve("jar-classes");
        compileClass(appClasses, "com.acme.Main");
        compileClass(dependencyClasses, "com.acme.Dependency");
        compileClass(jarClasses, "com.acme.Library");
        final Path libraryJar = root.resolve("libs/library.jar");
        writeJar(libraryJar, jarClasses);

        final ProjectLayout layout = new ProjectLayout(
            root,
            appClasses,
            InputKind.CLASSES_DIRECTORY,
            BuildTool.CLASSES,
            List.of(),
            List.of(),
            List.of(appClasses),
            List.of(root.resolve("missing.jar"), dependencyClasses, libraryJar),
            root.resolve("target"),
            "demo",
            List.of()
        );

        final List<ClassMetadata> metadata = new ClassMetadataScanner().scanLayout(layout);

        assertThat(metadata).extracting(ClassMetadata::name)
            .containsExactly("com/acme/Dependency", "com/acme/Library", "com/acme/Main");
        assertThat(metadata).extracting(ClassMetadata::application)
            .containsExactly(false, false, true);
        assertThat(metadata).extracting(value -> value.source().getFileName().toString())
            .containsExactly("Dependency.class", "Library.class", "Main.class");
    }

    @Test
    void scanCurrentJdkReadsSyntheticJmodInventory() throws Exception {
        final Path fakeHome = tempDir.resolve("fake-jdk-jmods");
        final Path classes = tempDir.resolve("jdk-module-classes");
        compileClass(classes, "jdkfake.ModuleClass");
        final Path jmods = fakeHome.resolve("jmods");
        final Path archiveRoot = tempDir.resolve("jmod-content");
        Files.createDirectories(archiveRoot.resolve("jdkfake"));
        Files.createDirectories(archiveRoot.resolve("classes/jdkfake"));
        Files.copy(classes.resolve("jdkfake/ModuleClass.class"), archiveRoot.resolve("classes/jdkfake/ModuleClass.class"));
        writeArchive(jmods.resolve("java.base.jmod"), archiveRoot);

        final List<ClassMetadata> metadata = withJavaHome(fakeHome, () ->
            new ClassMetadataScanner().scanCurrentJdk(tempDir.resolve("out-jmods"))
        );

        assertThat(metadata).singleElement().satisfies(value -> {
            assertThat(value.moduleName()).isEqualTo("java.base");
            assertThat(value.application()).isFalse();
            assertThat(value.name()).isEqualTo("jdkfake/ModuleClass");
        });
    }

    @Test
    void scanCurrentJdkReadsSyntheticJimageInventory() throws Exception {
        final Path fakeHome = tempDir.resolve("fake-jdk-jimage");
        final Path modulesImage = fakeHome.resolve("lib/modules");
        final Path jimage = fakeHome.resolve("bin/jimage");
        final Path classes = tempDir.resolve("jimage-classes");
        compileClass(classes, "imgfake.ImageClass");
        final Path classFile = classes.resolve("imgfake/ImageClass.class");
        Files.createDirectories(modulesImage.getParent());
        Files.writeString(modulesImage, "synthetic");
        writeExecutableScript(
            jimage,
            """
                #!/bin/sh
                set -eu
                if [ "$1" != "extract" ] || [ "$2" != "--dir" ]; then
                  exit 9
                fi
                dest="$3"
                mkdir -p "$dest/fake.module/imgfake"
                cp "%s" "$dest/fake.module/imgfake/ImageClass.class"
                """
                .formatted(classFile.toAbsolutePath())
        );

        final List<ClassMetadata> metadata = withJavaHome(fakeHome, () ->
            new ClassMetadataScanner().scanCurrentJdk(tempDir.resolve("out-jimage"))
        );

        assertThat(metadata).singleElement().satisfies(value -> {
            assertThat(value.moduleName()).isEqualTo("fake.module");
            assertThat(value.application()).isFalse();
            assertThat(value.name()).isEqualTo("imgfake/ImageClass");
        });
    }

    @Test
    void scanCurrentJdkFailsWhenNoInventorySourceExists() {
        final Path fakeHome = tempDir.resolve("missing-jdk");

        assertThatThrownBy(() -> withJavaHome(fakeHome, () -> new ClassMetadataScanner().scanCurrentJdk(tempDir.resolve("out-missing"))))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Unable to locate runtime JDK inventory source");
    }

    @Test
    void scanLayoutFailsWhenJarExtractionFails() throws Exception {
        final Path root = tempDir.resolve("broken-jar-layout");
        final Path brokenJar = root.resolve("libs/broken.jar");
        Files.createDirectories(brokenJar.getParent());
        Files.writeString(brokenJar, "not-a-jar");
        final ProjectLayout layout = new ProjectLayout(
            root,
            root.resolve("classes"),
            InputKind.CLASSES_DIRECTORY,
            BuildTool.CLASSES,
            List.of(),
            List.of(),
            List.of(root.resolve("classes")),
            List.of(brokenJar),
            root.resolve("target"),
            "broken",
            List.of()
        );

        assertThatThrownBy(() -> new ClassMetadataScanner().scanLayout(layout))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Unable to extract archive")
            .hasMessageContaining("broken.jar");
    }

    private void compileClass(final Path outputDirectory, final String className) throws IOException {
        final int split = className.lastIndexOf('.');
        final String packageName = split >= 0 ? className.substring(0, split) : "";
        final String simpleName = split >= 0 ? className.substring(split + 1) : className;
        final Path sourceFile;
        if (packageName.isEmpty()) {
            sourceFile = tempDir.resolve(simpleName + ".java");
            Files.writeString(sourceFile, source(simpleName, ""));
        } else {
            sourceFile = tempDir.resolve("sources").resolve(packageName.replace('.', '/')).resolve(simpleName + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source(simpleName, packageName));
        }
        Files.createDirectories(outputDirectory);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        final int exit = compiler.run(null, null, null, "-d", outputDirectory.toString(), sourceFile.toString());
        assertThat(exit).isZero();
    }

    private static String source(final String simpleName, final String packageName) {
        if (packageName.isEmpty()) {
            return """
                public final class %s {
                    public String value() {
                        return "%s";
                    }
                }
                """.formatted(simpleName, simpleName);
        }
        return """
            package %s;

            public final class %s {
                public String value() {
                    return "%s";
                }
            }
            """.formatted(packageName, simpleName, simpleName);
    }

    private static void writeJar(final Path jarFile, final Path root) throws IOException {
        writeArchive(jarFile, root);
    }

    private static void writeArchive(final Path archive, final Path root) throws IOException {
        Files.createDirectories(archive.getParent());
        try (OutputStream out = Files.newOutputStream(archive); JarOutputStream jar = new JarOutputStream(out)) {
            Files.walk(root)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    final String entryName = root.relativize(path).toString().replace('\\', '/');
                    try {
                        jar.putNextEntry(new JarEntry(entryName));
                        jar.write(Files.readAllBytes(path));
                        jar.closeEntry();
                    } catch (final IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
        } catch (final IllegalStateException exception) {
            if (exception.getCause() instanceof IOException io) {
                throw io;
            }
            throw exception;
        }
    }

    private static void writeExecutableScript(final Path path, final String script) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, script);
        Files.setPosixFilePermissions(path, Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        ));
    }

    private static <T> T withJavaHome(final Path javaHome, final ThrowingSupplier<T> action) throws Exception {
        final String original = System.getProperty("java.home");
        try {
            System.setProperty("java.home", javaHome.toString());
            return action.get();
        } finally {
            if (original == null) {
                System.clearProperty("java.home");
            } else {
                System.setProperty("java.home", original);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
