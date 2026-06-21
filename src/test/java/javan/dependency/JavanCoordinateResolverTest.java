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
final class JavanCoordinateResolverTest {
    @TempDir
    private Path tempDir;

    @Test
    void resolveMapsTripleColonCoordinateToLocalJarPath() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(repository)).resolve(dependency);

        assertThat(resolved.path()).contains(repository.resolve("com/acme/math/1.2.3/math-1.2.3.jar").toAbsolutePath().normalize());
    }

    @Test
    void resolveMapsGroupArtifactPlusVersionCoordinateToLocalJarPath() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math 1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(repository)).resolve(dependency);

        assertThat(resolved.path()).contains(repository.resolve("com/acme/math/1.2.3/math-1.2.3.jar").toAbsolutePath().normalize());
    }

    @Test
    void resolveMapsTabSeparatedCoordinateToLocalJarPath() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math\t1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(repository)).resolve(dependency);

        assertThat(resolved.path()).contains(repository.resolve("com/acme/math/1.2.3/math-1.2.3.jar").toAbsolutePath().normalize());
    }

    @Test
    void resolvePrefersFirstRepositoryWithExistingArtifact() throws Exception {
        final Path missingRepository = tempDir.resolve("missing-repo");
        final Path existingRepository = tempDir.resolve("existing-repo");
        final Path jar = existingRepository.resolve("com/acme/math/1.2.3/math-1.2.3.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "jar");
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(missingRepository, existingRepository)).resolve(dependency);

        assertThat(resolved.path()).contains(jar.toAbsolutePath().normalize());
    }

    @Test
    void resolveUsesFirstRepositoryCandidateWhenArtifactIsMissing() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(repository)).resolve(dependency);

        assertThat(resolved.path()).contains(repository.resolve("com/acme/math/1.2.3/math-1.2.3.jar").toAbsolutePath().normalize());
    }

    @Test
    void resolveIgnoresBlankRepositoryConfiguration() throws Exception {
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(Path.of(""))).resolve(dependency);

        assertThat(resolved.path()).contains(Path.of("com/acme/math/1.2.3/math-1.2.3.jar").toAbsolutePath().normalize());
    }

    @Test
    void resolveIgnoresDuplicateRepositoryConfiguration() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math:1.2.3", "coordinate", Optional.empty(), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(repository, repository)).resolve(dependency);

        assertThat(resolved.path()).contains(repository.resolve("com/acme/math/1.2.3/math-1.2.3.jar").toAbsolutePath().normalize());
    }

    @Test
    void resolveRejectsClassifierCoordinateUntilSupported() {
        final JavanDependency dependency = new JavanDependency("main", "com.acme:math:1.2.3:sources", "coordinate", Optional.empty(), 4);

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(tempDir)).resolve(dependency))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Invalid javan.mod coordinate")
            .hasMessageContaining("group:artifact:version");
    }

    @Test
    void resolveRejectsWhitespaceCoordinateWithoutGroupArtifactSeparator() {
        final JavanDependency dependency = new JavanDependency("main", "com.acme.math 1.2.3", "coordinate", Optional.empty(), 4);

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(tempDir)).resolve(dependency))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Invalid javan.mod coordinate")
            .hasMessageContaining("com.acme.math 1.2.3");
    }

    @Test
    void resolveRejectsCoordinateWithEmptyArtifactId() {
        final JavanDependency dependency = new JavanDependency("main", "com.acme::1.2.3", "coordinate", Optional.empty(), 4);

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(tempDir)).resolve(dependency))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Invalid javan.mod coordinate")
            .hasMessageContaining("com.acme::1.2.3");
    }

    @Test
    void resolveLeavesLocalDependencyUnchanged() throws Exception {
        final Path jar = tempDir.resolve("libs/math.jar");
        final JavanDependency dependency = new JavanDependency("main", "libs/math.jar", "local", Optional.of(jar), 4);

        final JavanDependency resolved = new JavanCoordinateResolver(List.of(tempDir.resolve("repo"))).resolve(dependency);

        assertThat(resolved).isSameAs(dependency);
    }
}
