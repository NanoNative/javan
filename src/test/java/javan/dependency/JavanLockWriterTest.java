package javan.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class JavanLockWriterTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeAbsentModuleDoesNotCreateLockFile() throws Exception {
        final Path lock = new JavanLockWriter().write(tempDir, JavanModule.absent());

        assertThat(lock).isEqualTo(tempDir.resolve("javan.lock"));
        assertThat(lock).doesNotExist();
    }

    @Test
    void writeVerifiesPresentModuleWithoutDependencies() throws Exception {
        final JavanModule module = new JavanModule(true, "com.acme.app", "25", List.of(), List.of());
        final JavanLockWriter writer = new JavanLockWriter();

        final Path lock = writer.write(tempDir, module);
        writer.write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"dependencyCount\": 0",
            "\"dependencies\": []"
        );
    }

    @Test
    void writeRecordsLocalDependencyStateDeterministically() throws Exception {
        final Path jar = tempDir.resolve("libs/app.jar");
        final Path classes = tempDir.resolve("classes");
        Files.createDirectories(jar.getParent());
        Files.createDirectories(classes.resolve("com/acme"));
        Files.writeString(jar, "jar");
        Files.writeString(classes.resolve("com/acme/App.class"), "class");
        Files.writeString(classes.resolve("resource.txt"), "resource");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(
                new JavanDependency("main", "libs/app.jar", "local", Optional.of(jar), 3),
                new JavanDependency("test", "classes", "local", Optional.of(classes), 4)
            ),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);
        final String first = Files.readString(lock);
        new JavanLockWriter().write(tempDir, module);
        final String second = Files.readString(lock);

        assertThat(first).isEqualTo(second);
        assertThat(first).contains(
            "\"lockVersion\": 1",
            "\"module\": \"com.acme.app\"",
            "\"java\": \"25\"",
            "\"scope\": \"main\"",
            "\"artifactKind\": \"jar\"",
            "\"relativePath\": \"libs/app.jar\"",
            "\"checksumAlgorithm\": \"sha256\"",
            "\"checksum\": \"0163f1eea7894350060624d315234d40c508ab251ba121714e234503045faadd\"",
            "\"scope\": \"test\"",
            "\"artifactKind\": \"classes-directory\""
        );
    }

    @Test
    void writeRecordsMissingJarAndPlainFileStates() throws Exception {
        final Path file = tempDir.resolve("config.txt");
        final Path missingJar = tempDir.resolve("libs/missing.jar");
        final Path missing = tempDir.resolve("libs/missing-dir");
        Files.writeString(file, "config");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(
                new JavanDependency("main", "config.txt", "local", Optional.of(file), 3),
                new JavanDependency("main", "libs/missing.jar", "local", Optional.of(missingJar), 4),
                new JavanDependency("main", "libs/missing-dir", "local", Optional.of(missing), 5)
            ),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"artifactKind\": \"file\"",
            "\"status\": \"present\"",
            "\"relativePath\": \"config.txt\"",
            "\"artifactKind\": \"missing-jar\"",
            "\"status\": \"missing\"",
            "\"artifactKind\": \"missing\"",
            "\"checksumAlgorithm\": \"none\""
        );
    }

    @Test
    void writeUsesStandardSha256AcrossDigestBlocks() throws Exception {
        final Path jar = tempDir.resolve("libs/vector.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "libs/vector.jar", "local", Optional.of(jar), 3)),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"checksumAlgorithm\": \"sha256\"",
            "\"checksum\": \"248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1\""
        );
    }

    @Test
    void writeRecordsExternalLocalPathAsAbsoluteRelativePath() throws Exception {
        final Path external = tempDir.getParent().resolve("external-javan-lock-file.txt");
        Files.writeString(external, "external");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", external.toString(), "local", Optional.of(external), 3)),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"path\": " + javan.util.Json.string(external.toString()),
            "\"relativePath\": " + javan.util.Json.string(external.toAbsolutePath().normalize().toString())
        );
    }

    @Test
    void writeRecordsUnsupportedCoordinateWithoutResolvingIt() throws Exception {
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.example:native-lib 2026.1", "coordinate", Optional.empty(), 3)),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"kind\": \"coordinate\"",
            "\"status\": \"unsupported-coordinate\"",
            "\"checksumAlgorithm\": \"none\""
        );
    }

    @Test
    void writeRecordsResolvedCoordinateState() throws Exception {
        final Path jar = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.of(jar), 3)),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"kind\": \"coordinate\"",
            "\"status\": \"present\"",
            "\"artifactKind\": \"jar\"",
            "\"relativePath\": \"repo/com/acme/math/1.2.3/math-1.2.3.jar\"",
            "\"checksumAlgorithm\": \"sha256\""
        );
    }

    @Test
    void writeRejectsChangedArtifactForUnchangedDeclaration() throws Exception {
        final Path jar = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "first");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.of(jar), 3)),
            List.of()
        );
        final JavanLockWriter writer = new JavanLockWriter();
        final Path lock = writer.write(tempDir, module);
        final String original = Files.readString(lock);
        Files.writeString(jar, "second");

        assertThatThrownBy(() -> writer.write(tempDir, module))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Dependency lock checksum mismatch")
            .hasMessageContaining("com.acme:math:1.2.3")
            .hasMessageContaining("Locked: sha256:")
            .hasMessageContaining("Found: sha256:");
        assertThat(Files.readString(lock)).isEqualTo(original);
    }

    @Test
    void writeUpgradesLegacyFnvLockToSha256() throws Exception {
        final Path jar = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "first");
        final JavanLockWriter writer = new JavanLockWriter();
        final Path lock = writer.write(tempDir, module("com.acme:math:1.2.3", jar));
        Files.writeString(lock, Files.readString(lock)
            .replace("\"checksumAlgorithm\": \"sha256\"", "\"checksumAlgorithm\": \"fnv64\"")
            .replace("\"checksum\": \"a7937b64b8caa58f03721bb6bacf5c78cb235febe0e70b1b84cd99541461a08e\"",
                "\"checksum\": \"89d7ed7f996f1d41\""));

        writer.write(tempDir, module("com.acme:math:1.2.3", jar));

        assertThat(Files.readString(lock))
            .contains("\"checksumAlgorithm\": \"sha256\"")
            .contains("\"checksum\": \"a7937b64b8caa58f03721bb6bacf5c78cb235febe0e70b1b84cd99541461a08e\"")
            .doesNotContain("fnv64", "89d7ed7f996f1d41");
    }

    @Test
    void writeRejectsChangedArtifactWhileUpgradingLegacyFnvLock() throws Exception {
        final Path jar = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "first");
        final JavanLockWriter writer = new JavanLockWriter();
        final Path lock = writer.write(tempDir, module("com.acme:math:1.2.3", jar));
        final String legacy = Files.readString(lock)
            .replace("\"checksumAlgorithm\": \"sha256\"", "\"checksumAlgorithm\": \"fnv64\"")
            .replace("\"checksum\": \"a7937b64b8caa58f03721bb6bacf5c78cb235febe0e70b1b84cd99541461a08e\"",
                "\"checksum\": \"89d7ed7f996f1d41\"");
        Files.writeString(lock, legacy);
        Files.writeString(jar, "second");

        assertThatThrownBy(() -> writer.write(tempDir, module("com.acme:math:1.2.3", jar)))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Dependency lock checksum mismatch")
            .hasMessageContaining("Locked: fnv64:89d7ed7f996f1d41")
            .hasMessageContaining("Found: fnv64:a49985ef4cee20bd");
        assertThat(Files.readString(lock)).isEqualTo(legacy);
    }

    @Test
    void writeRegeneratesLockWhenDeclarationChanges() throws Exception {
        final Path first = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        final Path second = tempDir.resolve("repo/com/acme/math/2.0.0/math-2.0.0.jar");
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        Files.writeString(first, "first");
        Files.writeString(second, "second");
        final JavanLockWriter writer = new JavanLockWriter();
        writer.write(tempDir, module("com.acme:math:1.2.3", first));

        final Path lock = writer.write(tempDir, module("com.acme:math:2.0.0", second));

        assertThat(Files.readString(lock))
            .contains("\"notation\": \"com.acme:math:2.0.0\"")
            .doesNotContain("\"notation\": \"com.acme:math:1.2.3\"");
    }

    @Test
    void writeResolvesPreviouslyMissingArtifactWithoutChangingDeclaration() throws Exception {
        final Path jar = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        final JavanLockWriter writer = new JavanLockWriter();
        writer.write(tempDir, module("com.acme:math:1.2.3", jar));
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "available");

        final Path lock = writer.write(tempDir, module("com.acme:math:1.2.3", jar));

        assertThat(Files.readString(lock)).contains(
            "\"status\": \"present\"",
            "\"checksumAlgorithm\": \"sha256\""
        );
    }

    @Test
    void writeRecordsMissingCoordinateState() throws Exception {
        final Path jar = tempDir.resolve("repo/com/acme/math/1.2.3/math-1.2.3.jar");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.of(jar), 3)),
            List.of()
        );

        final Path lock = new JavanLockWriter().write(tempDir, module);

        assertThat(Files.readString(lock)).contains(
            "\"kind\": \"coordinate\"",
            "\"status\": \"missing-coordinate\"",
            "\"artifactKind\": \"missing-jar\"",
            "\"checksumAlgorithm\": \"none\""
        );
    }

    private static JavanModule module(final String notation, final Path jar) {
        return new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", notation, "coordinate", Optional.of(jar), 3)),
            List.of()
        );
    }
}
