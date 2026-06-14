package javan.classfile;

import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class ClassFileScannerTest {
    @Test
    void scanReadsClassFromJarInput(@TempDir final Path tempDir) throws Exception {
        final String className = "example/app/Main";
        final Path input = jar(tempDir, "app.jar", className);

        final Map<String, ClassFile> classes = new ClassFileScanner().scan(jarInputLayout(tempDir, input));

        assertThat(classes).containsKey(className);
    }

    @Test
    void scanTagsJarInputAsApplicationCode(@TempDir final Path tempDir) throws Exception {
        final String className = "example/app/Main";
        final Path input = jar(tempDir, "app.jar", className);

        final Map<String, ClassFile> classes = new ClassFileScanner().scan(jarInputLayout(tempDir, input));

        assertThat(classes.get(className).application()).isTrue();
    }

    @Test
    void scanReadsClassFromDependencyJar(@TempDir final Path tempDir) throws Exception {
        final String className = "example/dependency/Library";
        final Path dependency = jar(tempDir, "dependency.jar", className);

        final Map<String, ClassFile> classes = new ClassFileScanner().scan(projectLayoutWithClasspath(tempDir, dependency));

        assertThat(classes).containsKey(className);
    }

    @Test
    void scanTagsDependencyJarAsDependencyCode(@TempDir final Path tempDir) throws Exception {
        final String className = "example/dependency/Library";
        final Path dependency = jar(tempDir, "dependency.jar", className);

        final Map<String, ClassFile> classes = new ClassFileScanner().scan(projectLayoutWithClasspath(tempDir, dependency));

        assertThat(classes.get(className).application()).isFalse();
    }

    private static ProjectLayout jarInputLayout(final Path root, final Path input) {
        return new ProjectLayout(
            root,
            input,
            InputKind.JAR_FILE,
            BuildTool.JAR,
            List.of(),
            List.of(),
            List.of(),
            List.of(input),
            root.resolve(".javan"),
            "app",
            List.of()
        );
    }

    private static ProjectLayout projectLayoutWithClasspath(final Path root, final Path classpathEntry) {
        return new ProjectLayout(
            root,
            root,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            List.of(classpathEntry),
            root.resolve(".javan"),
            "app",
            List.of()
        );
    }

    private static Path jar(final Path folder, final String fileName, final String className) throws IOException {
        final Path jar = folder.resolve(fileName);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(className + ".class"));
            output.write(minimalClassfile(className));
            output.closeEntry();
        }
        return jar;
    }

    private static byte[] minimalClassfile(final String className) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8(className)
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("<init>")
            .utf8("()V")
            .utf8("Code")
            .nameAndType(5, 6)
            .methodRef(4, 8)
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0001)
            .u2(5)
            .u2(6)
            .u2(1)
            .u2(7)
            .u4(17)
            .u2(1)
            .u2(1)
            .u4(5)
            .u1(42)
            .u1(183)
            .u2(9)
            .u1(177)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static final class Bytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private Bytes u1(final int value) {
            out.write(value & 0xFF);
            return this;
        }

        private Bytes u2(final int value) {
            return u1(value >>> 8).u1(value);
        }

        private Bytes u4(final long value) {
            return u1((int) (value >>> 24))
                .u1((int) (value >>> 16))
                .u1((int) (value >>> 8))
                .u1((int) value);
        }

        private Bytes utf8(final String value) {
            final byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return u1(1).u2(encoded.length).bytes(encoded);
        }

        private Bytes classInfo(final int nameIndex) {
            return u1(7).u2(nameIndex);
        }

        private Bytes nameAndType(final int nameIndex, final int descriptorIndex) {
            return u1(12).u2(nameIndex).u2(descriptorIndex);
        }

        private Bytes methodRef(final int classIndex, final int nameAndTypeIndex) {
            return u1(10).u2(classIndex).u2(nameAndTypeIndex);
        }

        private Bytes bytes(final byte[] values) {
            out.writeBytes(values);
            return this;
        }

        private byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
