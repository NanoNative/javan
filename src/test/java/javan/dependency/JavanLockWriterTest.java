package javan.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
            "\"checksumAlgorithm\": \"fnv64\"",
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
            List.of(new JavanDependency("main", "org.nanonative:nano 2026.1", "coordinate", Optional.empty(), 3)),
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
            "\"checksumAlgorithm\": \"fnv64\""
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
}
