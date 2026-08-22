package javan.dependency;

import javan.util.Files2;
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
    void resolveIncludesLocalRuntimeTransitiveDependencies() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        final Path library = artifact(repository, "com.acme", "library", "2.0.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), """
            <project>
              <dependencies>
                <dependency>
                  <groupId>com.acme</groupId>
                  <artifactId>library</artifactId>
                  <version>2.0.0</version>
                  <scope>runtime</scope>
                </dependency>
              </dependencies>
            </project>
            """);
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.acme:app:1.0.0", "coordinate", Optional.empty(), 3)),
            List.of()
        );

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module);

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:library:2.0.0");
        assertThat(resolved.dependencies()).extracting(JavanDependency::path)
            .containsExactly(Optional.of(app), Optional.of(library));
    }

    @Test
    void resolvePropagatesScopeAndHonorsOptionalExcludedAndNonRuntimeDependencies() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "runtime", "1.0.0");
        artifact(repository, "com.acme", "excluded", "1.0.0");
        artifact(repository, "com.acme", "optional", "1.0.0");
        artifact(repository, "com.acme", "test-only", "1.0.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), """
            <project>
              <dependencies>
                <dependency>
                  <groupId>com.acme</groupId><artifactId>runtime</artifactId><version>1.0.0</version>
                  <exclusions>
                    <exclusion><groupId>com.acme</groupId><artifactId>excluded</artifactId></exclusion>
                  </exclusions>
                </dependency>
                <dependency>
                  <groupId>com.acme</groupId><artifactId>optional</artifactId><version>1.0.0</version>
                  <optional>true</optional>
                </dependency>
                <dependency>
                  <groupId>com.acme</groupId><artifactId>test-only</artifactId><version>1.0.0</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """);
        Files.writeString(
            repository.resolve("com/acme/runtime/1.0.0/runtime-1.0.0.pom"),
            """
                <project><dependencies><dependency>
                  <groupId>com.acme</groupId><artifactId>excluded</artifactId><version>1.0.0</version>
                </dependency></dependencies></project>
                """
        );
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("tool", "com.acme:app:1.0.0", "coordinate", Optional.empty(), 3)),
            List.of()
        );

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module);

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:runtime:1.0.0");
        assertThat(resolved.dependencies()).extracting(JavanDependency::scope).containsOnly("tool");
        assertThat(resolved.dependencies().get(1).direct()).isFalse();
        assertThat(resolved.dependencies().get(1).requestedBy()).isEqualTo("com.acme:app:1.0.0");
    }

    @Test
    void resolveUsesPomPropertiesAndLocalDependencyManagement() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "2.1.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), """
            <project>
              <properties><library.version>2.1.0</library.version></properties>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
                <version>${library.version}</version>
              </dependency></dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
              </dependency></dependencies>
            </project>
            """);
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.acme:app:1.0.0", "coordinate", Optional.empty(), 3)),
            List.of()
        );

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module);

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:library:2.1.0");
    }

    @Test
    void resolveUsesParentPropertiesManagementAndDependencies() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "2.1.0");
        artifact(repository, "com.acme", "inherited", "3.1.0");
        pom(repository, "com.acme", "parent", "1.0.0", """
            <project>
              <groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version>
              <properties>
                <library.version>2.0.0</library.version>
                <inherited.version>3.0.0</inherited.version>
              </properties>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
                <version>${library.version}</version>
              </dependency></dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>inherited</artifactId><version>${inherited.version}</version>
              </dependency></dependencies>
            </project>
            """);
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project>
              <parent>
                <groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version>
              </parent>
              <artifactId>app</artifactId>
              <properties>
                <library.version>2.1.0</library.version>
                <inherited.version>3.1.0</inherited.version>
              </properties>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
              </dependency></dependencies>
            </project>
            """);

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0"));

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:inherited:3.1.0", "com.acme:library:2.1.0");
    }

    @Test
    void resolveUsesImportedBomManagement() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "4.0.0");
        pom(repository, "com.acme", "platform", "1.0.0", """
            <project>
              <groupId>com.acme</groupId><artifactId>platform</artifactId><version>1.0.0</version>
              <properties><library.version>4.0.0</library.version></properties>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
                <version>${library.version}</version>
              </dependency></dependencies></dependencyManagement>
            </project>
            """);
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>platform</artifactId><version>1.0.0</version>
                <type>pom</type><scope>import</scope>
              </dependency></dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
              </dependency></dependencies>
            </project>
            """);

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0"));

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:library:4.0.0");
    }

    @Test
    void resolveIgnoresInactiveProfilePropertiesAndManagement() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "2.0.0");
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project>
              <profiles><profile>
                <properties><library.version>9.0.0</library.version></properties>
                <dependencyManagement><dependencies><dependency>
                  <groupId>com.acme</groupId><artifactId>library</artifactId><version>9.0.0</version>
                </dependency></dependencies></dependencyManagement>
              </profile></profiles>
              <properties><library.version>2.0.0</library.version></properties>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
                <version>${library.version}</version>
              </dependency></dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
              </dependency></dependencies>
            </project>
            """);

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0"));

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:library:2.0.0");
    }

    @Test
    void resolveAppliesDependencyManagementThroughTheTransitivePath() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        final Path bridge = artifact(repository, "com.acme", "bridge", "1.0.0");
        artifact(repository, "com.acme", "library", "2.0.0");
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId><version>2.0.0</version>
              </dependency></dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>bridge</artifactId><version>1.0.0</version>
              </dependency></dependencies>
            </project>
            """);
        Files.writeString(bridge.getParent().resolve("bridge-1.0.0.pom"), """
            <project><dependencies><dependency>
              <groupId>com.acme</groupId><artifactId>library</artifactId><version>1.0.0</version>
            </dependency></dependencies></project>
            """);

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0"));

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:bridge:1.0.0", "com.acme:library:2.0.0");
    }

    @Test
    void resolveKeepsFirstImportedBomVersion() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "1.0.0");
        pom(repository, "com.acme", "platform-a", "1.0.0", managedPom("library", "1.0.0"));
        pom(repository, "com.acme", "platform-b", "1.0.0", managedPom("library", "2.0.0"));
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project>
              <dependencyManagement><dependencies>
                <dependency>
                  <groupId>com.acme</groupId><artifactId>platform-a</artifactId><version>1.0.0</version>
                  <type>pom</type><scope>import</scope>
                </dependency>
                <dependency>
                  <groupId>com.acme</groupId><artifactId>platform-b</artifactId><version>1.0.0</version>
                  <type>pom</type><scope>import</scope>
                </dependency>
              </dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
              </dependency></dependencies>
            </project>
            """);

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0"));

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:library:1.0.0");
    }

    @Test
    void resolveInterpolatesInheritedBomImportAfterChildPropertyOverride() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "2.0.0");
        pom(repository, "com.acme", "platform", "1.0.0", managedPom("library", "1.0.0"));
        pom(repository, "com.acme", "platform", "2.0.0", managedPom("library", "2.0.0"));
        pom(repository, "com.acme", "parent", "1.0.0", """
            <project>
              <groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version>
              <properties><platform.version>1.0.0</platform.version></properties>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>platform</artifactId>
                <version>${platform.version}</version><type>pom</type><scope>import</scope>
              </dependency></dependencies></dependencyManagement>
            </project>
            """);
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project>
              <parent>
                <groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version>
              </parent>
              <artifactId>app</artifactId>
              <properties><platform.version>2.0.0</platform.version></properties>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>library</artifactId>
              </dependency></dependencies>
            </project>
            """);

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0"));

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:library:2.0.0");
    }

    @Test
    void resolveRejectsMissingParentPom() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project><parent>
              <groupId>com.acme</groupId><artifactId>missing-parent</artifactId><version>1.0.0</version>
            </parent></project>
            """);

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0")))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Missing local parent POM")
            .hasMessageContaining("com.acme:missing-parent:1.0.0");
    }

    @Test
    void resolveRejectsMissingImportedBomPom() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project><dependencyManagement><dependencies><dependency>
              <groupId>com.acme</groupId><artifactId>missing-platform</artifactId><version>1.0.0</version>
              <type>pom</type><scope>import</scope>
            </dependency></dependencies></dependencyManagement></project>
            """);

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0")))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Missing local imported BOM POM")
            .hasMessageContaining("com.acme:missing-platform:1.0.0");
    }

    @Test
    void resolveRejectsCyclicParentPom() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        pom(repository, "com.acme", "parent-a", "1.0.0", """
            <project><parent>
              <groupId>com.acme</groupId><artifactId>parent-b</artifactId><version>1.0.0</version>
            </parent></project>
            """);
        pom(repository, "com.acme", "parent-b", "1.0.0", """
            <project><parent>
              <groupId>com.acme</groupId><artifactId>parent-a</artifactId><version>1.0.0</version>
            </parent></project>
            """);
        Files.writeString(app.getParent().resolve("app-1.0.0.pom"), """
            <project><parent>
              <groupId>com.acme</groupId><artifactId>parent-a</artifactId><version>1.0.0</version>
            </parent></project>
            """);

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(repository)).resolve(module("com.acme:app:1.0.0")))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Cyclic local POM metadata")
            .hasMessageContaining("com.acme:parent-a:1.0.0");
    }

    @Test
    void resolveIgnoresCommentedAndReportingDependencies() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "ignored", "1.0.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), """
            <project>
              <!-- <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>ignored</artifactId><version>1.0.0</version>
              </dependency></dependencies> -->
              <reporting><plugins><plugin><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>ignored</artifactId><version>1.0.0</version>
              </dependency></dependencies></plugin></plugins></reporting>
            </project>
            """);
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", "com.acme:app:1.0.0", "coordinate", Optional.empty(), 3)),
            List.of()
        );

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module);

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0");
    }

    @Test
    void resolveKeepsNearestVersionAndReportsMediation() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        final Path other = artifact(repository, "com.acme", "other", "1.0.0");
        artifact(repository, "com.acme", "library", "1.0.0");
        artifact(repository, "com.acme", "library", "2.0.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), pomDependency("library", "1.0.0"));
        Files.writeString(other.resolveSibling("other-1.0.0.pom"), pomDependency("library", "2.0.0"));
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(
                new JavanDependency("main", "com.acme:app:1.0.0", "coordinate", Optional.empty(), 3),
                new JavanDependency("main", "com.acme:other:1.0.0", "coordinate", Optional.empty(), 4)
            ),
            List.of()
        );

        final JavanModule resolved = new JavanCoordinateResolver(List.of(repository)).resolve(module);

        assertThat(resolved.dependencies()).extracting(JavanDependency::notation)
            .containsExactly("com.acme:app:1.0.0", "com.acme:other:1.0.0", "com.acme:library:1.0.0");
        assertThat(resolved.warnings()).containsExactly(
            "Dependency mediation kept com.acme:library:1.0.0 and omitted com.acme:library:2.0.0 requested by com.acme:other:1.0.0"
        );
    }

    @Test
    void resolveRejectsDuplicateDirectCoordinateFamily() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final JavanModule module = new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(
                new JavanDependency("main", "com.acme:library:1.0.0", "coordinate", Optional.empty(), 3),
                new JavanDependency("main", "com.acme:library:2.0.0", "coordinate", Optional.empty(), 4)
            ),
            List.of()
        );

        assertThatThrownBy(() -> new JavanCoordinateResolver(List.of(repository)).resolve(module))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Duplicate javan.mod coordinate family")
            .hasMessageContaining("com.acme:library:1.0.0")
            .hasMessageContaining("com.acme:library:2.0.0");
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

    @Test
    void resolveCachesCompletePomClosureForOfflineReplay() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path cache = tempDir.resolve("cache");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        artifact(repository, "com.acme", "library", "2.0.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), pomDependency("library", "2.0.0"));

        final JavanModule first = new JavanCoordinateResolver(List.of(repository), cache)
            .resolve(module("com.acme:app:1.0.0"));

        assertThat(first.dependencies()).extracting(JavanDependency::path).containsExactly(
            Optional.of(cache.resolve("com/acme/app/1.0.0/app-1.0.0.jar").toAbsolutePath().normalize()),
            Optional.of(cache.resolve("com/acme/library/2.0.0/library-2.0.0.jar").toAbsolutePath().normalize())
        );
        assertThat(cache.resolve("com/acme/app/1.0.0/app-1.0.0.jar.sha256")).isRegularFile();
        assertThat(cache.resolve("com/acme/app/1.0.0/app-1.0.0.pom.sha256")).isRegularFile();
        Files2.deleteRecursive(repository);

        final JavanModule replayed = new JavanCoordinateResolver(List.of(repository), cache)
            .resolve(module("com.acme:app:1.0.0"));

        assertThat(replayed).isEqualTo(first);
    }

    @Test
    void resolveRejectsTamperedCachedJar() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path cache = tempDir.resolve("cache");
        artifact(repository, "com.acme", "app", "1.0.0");
        final JavanDependency dependency = module("com.acme:app:1.0.0").dependencies().getFirst();
        final JavanCoordinateResolver resolver = new JavanCoordinateResolver(List.of(repository), cache);
        final Path cached = resolver.resolve(dependency).path().orElseThrow();
        Files.writeString(cached, "tampered");

        assertThatThrownBy(() -> resolver.resolve(dependency))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Cached dependency checksum mismatch")
            .hasMessageContaining("app-1.0.0.jar")
            .hasMessageContaining("Delete the cached file");
    }

    @Test
    void resolveKeepsCachedArtifactWhenRepositoryChanges() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path cache = tempDir.resolve("cache");
        final Path source = artifact(repository, "com.acme", "app", "1.0.0");
        final JavanDependency dependency = module("com.acme:app:1.0.0").dependencies().getFirst();
        final JavanCoordinateResolver resolver = new JavanCoordinateResolver(List.of(repository), cache);
        final Path cached = resolver.resolve(dependency).path().orElseThrow();
        Files.writeString(source, "changed-source");

        assertThat(resolver.resolve(dependency).path()).contains(cached);
        assertThat(cached).hasContent("app");
    }

    @Test
    void resolveRejectsTamperedCachedPom() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path cache = tempDir.resolve("cache");
        final Path app = artifact(repository, "com.acme", "app", "1.0.0");
        Files.writeString(app.resolveSibling("app-1.0.0.pom"), "<project/>");
        final JavanCoordinateResolver resolver = new JavanCoordinateResolver(List.of(repository), cache);
        resolver.resolve(module("com.acme:app:1.0.0"));
        Files.writeString(cache.resolve("com/acme/app/1.0.0/app-1.0.0.pom"), "<project><broken/></project>");

        assertThatThrownBy(() -> resolver.resolve(module("com.acme:app:1.0.0")))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Cached dependency checksum mismatch")
            .hasMessageContaining("app-1.0.0.pom");
    }

    @Test
    void resolveRepairsIncompleteCacheFromAvailableRepository() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path cache = tempDir.resolve("cache");
        artifact(repository, "com.acme", "app", "1.0.0");
        final JavanDependency dependency = module("com.acme:app:1.0.0").dependencies().getFirst();
        final JavanCoordinateResolver resolver = new JavanCoordinateResolver(List.of(repository), cache);
        final Path cached = resolver.resolve(dependency).path().orElseThrow();
        Files.delete(cached.resolveSibling("app-1.0.0.jar.sha256"));

        assertThat(resolver.resolve(dependency).path()).contains(cached);
        assertThat(cached.resolveSibling("app-1.0.0.jar.sha256")).isRegularFile();
    }

    @Test
    void resolveRejectsIncompleteCacheWithoutRepository() throws Exception {
        final Path repository = tempDir.resolve("repo");
        final Path cache = tempDir.resolve("cache");
        artifact(repository, "com.acme", "app", "1.0.0");
        final JavanDependency dependency = module("com.acme:app:1.0.0").dependencies().getFirst();
        final JavanCoordinateResolver resolver = new JavanCoordinateResolver(List.of(repository), cache);
        final Path cached = resolver.resolve(dependency).path().orElseThrow();
        Files.delete(cached.resolveSibling("app-1.0.0.jar.sha256"));
        Files2.deleteRecursive(repository);

        assertThatThrownBy(() -> resolver.resolve(dependency))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("missing SHA-256 metadata")
            .hasMessageContaining("app-1.0.0.jar");
    }

    private static Path artifact(
        final Path repository,
        final String groupId,
        final String artifactId,
        final String version
    ) throws Exception {
        final Path jar = repository.resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".jar")
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, artifactId);
        return jar;
    }

    private static Path pom(
        final Path repository,
        final String groupId,
        final String artifactId,
        final String version,
        final String content
    ) throws Exception {
        final Path pom = repository.resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".pom")
            .toAbsolutePath()
            .normalize();
        Files.createDirectories(pom.getParent());
        Files.writeString(pom, content);
        return pom;
    }

    private static JavanModule module(final String coordinate) {
        return new JavanModule(
            true,
            "com.acme.app",
            "25",
            List.of(new JavanDependency("main", coordinate, "coordinate", Optional.empty(), 3)),
            List.of()
        );
    }

    private static String pomDependency(final String artifactId, final String version) {
        return """
            <project><dependencies><dependency>
              <groupId>com.acme</groupId><artifactId>%s</artifactId><version>%s</version>
            </dependency></dependencies></project>
            """.formatted(artifactId, version);
    }

    private static String managedPom(final String artifactId, final String version) {
        return """
            <project><dependencyManagement><dependencies><dependency>
              <groupId>com.acme</groupId><artifactId>%s</artifactId><version>%s</version>
            </dependency></dependencies></dependencyManagement></project>
            """.formatted(artifactId, version);
    }
}
