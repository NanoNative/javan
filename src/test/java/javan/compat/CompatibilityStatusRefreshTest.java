package javan.compat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
final class CompatibilityStatusRefreshTest {
    @TempDir
    private Path tempDir;

    @Test
    void synchronizeCopiesStaleStatusAndFailsOnceBeforeRepeatPasses() throws Exception {
        final StatusFixture fixture = statusFixture("old matrix\n", "old json\n", "old jdk\n");
        writeGenerated(fixture, "new matrix\n", "new json\n", "new jdk\n");
        final ByteArrayOutputStream firstOutput = new ByteArrayOutputStream();

        final CompatibilityStatusRefresh.RefreshResult first = CompatibilityStatusRefresh.synchronize(
            fixture.root(),
            fixture.classes(),
            printStream(firstOutput),
            true
        );

        assertThat(first.statusChanged()).isTrue();
        assertThat(first.changedFiles()).containsExactly(
            Path.of("doc/status/support-matrix.md"),
            Path.of("doc/status/support-matrix.json"),
            Path.of("doc/status/jdk-compatibility.md")
        );
        assertThat(firstOutput.toString(StandardCharsets.UTF_8)).contains(
            "Refreshed compatibility status documents:",
            "doc/status/support-matrix.md",
            "doc/status/support-matrix.json",
            "doc/status/jdk-compatibility.md"
        );
        assertThatThrownBy(() -> CompatibilityStatusRefresh.failWhenStatusWasStale(first))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                "Compatibility status was stale and has been regenerated.",
                "Review the generated changes, then rerun mvn verify."
            );
        assertThat(Files.readString(fixture.root().resolve("doc/status/support-matrix.md"))).isEqualTo("new matrix\n");
        assertThat(Files.readString(fixture.root().resolve("doc/status/support-matrix.json"))).isEqualTo("new json\n");
        assertThat(Files.readString(fixture.root().resolve("doc/status/jdk-compatibility.md"))).isEqualTo("new jdk\n");

        final ByteArrayOutputStream secondOutput = new ByteArrayOutputStream();
        final CompatibilityStatusRefresh.RefreshResult second = CompatibilityStatusRefresh.synchronize(
            fixture.root(),
            fixture.classes(),
            printStream(secondOutput),
            true
        );

        assertThat(second.statusChanged()).isFalse();
        assertThat(second.changedFiles()).isEmpty();
        assertThat(secondOutput.toString(StandardCharsets.UTF_8)).contains("Compatibility status documents are current.");
        CompatibilityStatusRefresh.failWhenStatusWasStale(second);
    }

    @Test
    void synchronizePreservesPinnedJdkPageOutsideTheReferenceEnvironment() throws Exception {
        final StatusFixture fixture = statusFixture("matrix\n", "json\n", "old jdk\n");
        writeGenerated(fixture, "matrix\n", "json\n", "new jdk\n");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        final CompatibilityStatusRefresh.RefreshResult result = CompatibilityStatusRefresh.synchronize(
            fixture.root(),
            fixture.classes(),
            printStream(output),
            false
        );

        assertThat(result.statusChanged()).isFalse();
        assertThat(result.changedFiles()).isEmpty();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains(
            "Compatibility status documents are current.",
            "Tracked JDK compatibility remains pinned to the reference JDK."
        );
        assertThat(Files.readString(fixture.root().resolve("doc/status/jdk-compatibility.md"))).isEqualTo("old jdk\n");
        CompatibilityStatusRefresh.failWhenStatusWasStale(result);
    }

    @Test
    void synchronizeFailsClearlyWhenTheCanonicalGeneratorMissesAnArtifact() throws Exception {
        final StatusFixture fixture = statusFixture("matrix\n", "json\n", "jdk\n");
        writeGenerated(fixture, "new matrix\n", "json\n", "jdk\n");
        Files.delete(fixture.classes().resolve("doc/status/support-matrix.json"));

        assertThatThrownBy(() -> CompatibilityStatusRefresh.synchronize(
            fixture.root(),
            fixture.classes(),
            printStream(new ByteArrayOutputStream()),
            true
        ))
            .isInstanceOf(IOException.class)
            .hasMessageContaining(
                "Compatibility generator did not write",
                "target/classes/doc/status/support-matrix.json"
            );
        assertThat(Files.readString(fixture.root().resolve("doc/status/support-matrix.md"))).isEqualTo("matrix\n");
    }

    @Test
    void mainRejectsAUnexpectedMavenJdkBeforeGeneration() throws Exception {
        final Path root = tempDir.resolve("wrong-jdk");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        final int actualFeature = Runtime.version().feature();

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(new String[]{
            root.toString(),
            root.resolve("target/classes").toString(),
            Integer.toString(actualFeature + 1),
            System.getProperty("java.vendor"),
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            "false"
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                "Compatibility status requires JDK " + (actualFeature + 1),
                "Maven is running JDK " + actualFeature,
                "matrix keys stay canonical"
            );
    }

    @Test
    void mainRejectsInvalidLifecycleArgumentsBeforeReadingTheProject() {
        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(new String[0]))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("received 0 arguments");

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(new String[]{
            tempDir.toString(),
            "target/classes",
            "not-a-feature",
            "vendor",
            "version",
            "os",
            "arch",
            "false"
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid required JDK feature: not-a-feature");

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(new String[]{
            tempDir.toString(),
            "target/classes",
            "999999999999999999999999999",
            "vendor",
            "version",
            "os",
            "arch",
            "false"
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid required JDK feature: 999999999999999999999999999");

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(new String[]{
            tempDir.toString(),
            "target/classes",
            Integer.toString(Runtime.version().feature()),
            "vendor",
            "version",
            "os",
            "arch",
            "sometimes"
        }))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Reference JDK requirement must be true or false: sometimes");
    }

    @Test
    void mainFailsClosedForEveryStrictReferenceEnvironmentMismatch() {
        final String vendor = System.getProperty("java.vendor");
        final String version = System.getProperty("java.version");
        final String osName = System.getProperty("os.name");
        final String osArch = System.getProperty("os.arch");

        assertStrictReferenceMismatch("other-vendor", version, osName, osArch);
        assertStrictReferenceMismatch(vendor, "other-version", osName, osArch);
        assertStrictReferenceMismatch(vendor, version, "other-os", osArch);
        assertStrictReferenceMismatch(vendor, version, osName, "other-arch");
    }

    private void assertStrictReferenceMismatch(
        final String vendor,
        final String version,
        final String osName,
        final String osArch
    ) {
        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(
            lifecycleArgs(tempDir, "target/classes", vendor, version, osName, osArch, true)
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(
                "Reference compatibility status requires",
                "but Maven is running",
                "Refusing to skip the tracked JDK snapshot."
            );
    }

    @Test
    void mainFailsBeforeGenerationWhenTheMavenProjectDescriptorIsMissing() throws Exception {
        final Path root = tempDir.resolve("missing-pom");
        Files.createDirectories(root);

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(lifecycleArgs(
            root,
            "target/classes",
            System.getProperty("java.vendor"),
            System.getProperty("java.version")
        )))
            .isInstanceOf(IOException.class)
            .hasMessage("Missing Maven project descriptor: pom.xml");
    }

    @Test
    void mainFailsBeforeGenerationWhenCompiledJavanEntrypointIsMissing() throws Exception {
        final Path root = tempDir.resolve("missing-classes");
        final Path classes = root.resolve("target/classes");
        Files.createDirectories(classes);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(lifecycleArgs(
            root,
            "target/classes",
            System.getProperty("java.vendor"),
            "other-version"
        )))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Missing compiled Javan entrypoint", "target/classes/javan/Main.class");
    }

    @Test
    void mainReportsAnExternalMissingClassesPathPortably() throws Exception {
        final Path root = tempDir.resolve("external-classes-project");
        final Path classes = tempDir.resolve("external-classes");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");

        assertThatThrownBy(() -> CompatibilityStatusRefresh.main(lifecycleArgs(
            root,
            classes.toString(),
            "other-vendor",
            System.getProperty("java.version")
        )))
            .isInstanceOf(IOException.class)
            .hasMessage(
                "Missing compiled Javan entrypoint: "
                    + classes.resolve("javan/Main.class").toString().replace('\\', '/')
            );
    }

    @Test
    void mainFailsWithoutSynchronizingWhenCanonicalCompatibilityGenerationFails() throws Exception {
        final Path root = tempDir.resolve("generation-failure");
        final Path classes = root.resolve("target/classes");
        final Path fakeHome = tempDir.resolve("failure-fake-jdk");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        compileClass(classes, "javan.Main", """
            public static native void unsupported();

            public static void main(final String[] args) {
                unsupported();
            }
            """);
        writeSyntheticJmod(fakeHome);

        assertThatThrownBy(() -> withJavaHome(fakeHome, () -> {
            CompatibilityStatusRefresh.main(lifecycleArgs(
                root,
                "target/classes",
                System.getProperty("java.vendor"),
                System.getProperty("java.version")
            ));
            return true;
        }))
            .isInstanceOf(IOException.class)
            .hasMessage("Compatibility report generation failed with exit code 2.");
        assertThat(root.resolve("doc/status")).doesNotExist();
    }

    @Test
    void mainGeneratesRefreshesAndThenAcceptsCanonicalStatusThroughTheCli() throws Exception {
        final Path root = tempDir.resolve("lifecycle");
        final Path classes = root.resolve("target/classes");
        final Path fakeHome = tempDir.resolve("fake-jdk");
        Files.createDirectories(root);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        compileClass(classes, "javan.Main");
        writeSyntheticJmod(fakeHome);
        final String[] args = lifecycleArgs(
            root,
            "target/classes",
            System.getProperty("java.vendor"),
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            true
        );

        assertThatThrownBy(() -> withJavaHome(fakeHome, () -> {
            CompatibilityStatusRefresh.main(args);
            return true;
        }))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Compatibility status was stale and has been regenerated.");

        assertThat(root.resolve("doc/status/support-matrix.md")).isRegularFile();
        assertThat(root.resolve("doc/status/support-matrix.json")).isRegularFile();
        assertThat(root.resolve("doc/status/jdk-compatibility.md")).isRegularFile();
        assertThat(root.resolve("target/.javan/reports/compatibility-summary.json")).isRegularFile();

        assertThat(withJavaHome(fakeHome, () -> {
            CompatibilityStatusRefresh.main(args);
            return true;
        })).isTrue();
    }

    private StatusFixture statusFixture(
        final String matrix,
        final String json,
        final String jdk
    ) throws Exception {
        final Path root = tempDir.resolve("project");
        final Path classes = root.resolve("target/classes");
        final Path tracked = root.resolve("doc/status");
        Files.createDirectories(classes.resolve("doc/status"));
        Files.createDirectories(tracked);
        Files.writeString(root.resolve("pom.xml"), "<project/>\n");
        Files.writeString(tracked.resolve("support-matrix.md"), matrix);
        Files.writeString(tracked.resolve("support-matrix.json"), json);
        Files.writeString(tracked.resolve("jdk-compatibility.md"), jdk);
        return new StatusFixture(root, classes);
    }

    private static void writeGenerated(
        final StatusFixture fixture,
        final String matrix,
        final String json,
        final String jdk
    ) throws Exception {
        final Path generated = fixture.classes().resolve("doc/status");
        Files.writeString(generated.resolve("support-matrix.md"), matrix);
        Files.writeString(generated.resolve("support-matrix.json"), json);
        Files.writeString(generated.resolve("jdk-compatibility.md"), jdk);
    }

    private static PrintStream printStream(final ByteArrayOutputStream output) {
        return new PrintStream(output, true, StandardCharsets.UTF_8);
    }

    private void compileClass(final Path outputDirectory, final String className) throws IOException {
        compileClass(outputDirectory, className, """
            public static void main(final String[] args) {
            }
            """);
    }

    private void compileClass(
        final Path outputDirectory,
        final String className,
        final String members
    ) throws IOException {
        final int split = className.lastIndexOf('.');
        final String packageName = className.substring(0, split);
        final String simpleName = className.substring(split + 1);
        final Path source = tempDir.resolve("sources")
            .resolve(packageName.replace('.', '/'))
            .resolve(simpleName + ".java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package %s;

            public final class %s {
                private %s() {
                }

                %s
            }
            """.formatted(packageName, simpleName, simpleName, members));
        Files.createDirectories(outputDirectory);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        assertThat(compiler.run(null, null, null, "-d", outputDirectory.toString(), source.toString())).isZero();
    }

    private void writeSyntheticJmod(final Path fakeHome) throws IOException {
        final Path classes = tempDir.resolve("jdk-classes");
        compileClass(classes, "jdkfake.ModuleClass");
        final Path archiveRoot = tempDir.resolve("jmod-content");
        final Path classFile = archiveRoot.resolve("classes/jdkfake/ModuleClass.class");
        Files.createDirectories(classFile.getParent());
        Files.copy(classes.resolve("jdkfake/ModuleClass.class"), classFile);
        writeArchive(fakeHome.resolve("jmods/java.base.jmod"), archiveRoot);
    }

    private static void writeArchive(final Path archive, final Path root) throws IOException {
        Files.createDirectories(archive.getParent());
        try (OutputStream out = Files.newOutputStream(archive); JarOutputStream jar = new JarOutputStream(out)) {
            try (var paths = Files.walk(root)) {
                for (final Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    final String entryName = root.relativize(path).toString().replace('\\', '/');
                    jar.putNextEntry(new JarEntry(entryName));
                    jar.write(Files.readAllBytes(path));
                    jar.closeEntry();
                }
            }
        }
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

    private static String[] lifecycleArgs(
        final Path root,
        final String classes,
        final String vendor,
        final String version
    ) {
        return lifecycleArgs(
            root,
            classes,
            vendor,
            version,
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            false
        );
    }

    private static String[] lifecycleArgs(
        final Path root,
        final String classes,
        final String vendor,
        final String version,
        final String osName,
        final String osArch,
        final boolean requireReferenceJdk
    ) {
        return new String[]{
            root.toString(),
            classes,
            Integer.toString(Runtime.version().feature()),
            vendor,
            version,
            osName,
            osArch,
            Boolean.toString(requireReferenceJdk)
        };
    }

    private record StatusFixture(Path root, Path classes) {
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
