package javan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * External example compatibility smoke only.
 *
 * <p>These probes prove that javan can consume selected real third-party artifacts, but they do
 * not define JDK support rows or compiler-owned scenario coverage. This harness must stay
 * metadata-driven and must not hardcode individual probe identities.
 */
@Execution(SAME_THREAD)
@Tag("external-probe")
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliExternalProbeAcceptanceIntegrationTest extends CliIntegrationSupport {
    @Test
    void realProbesDeclareCompilerOwnedGenericEvidence() throws Exception {
        final List<String> declaredTests = Arrays.stream(CliDependencyProjectIntegrationTest.class.getDeclaredMethods())
            .map(Method::getName)
            .sorted()
            .toList();

        assertThat(realProbes())
            .allSatisfy(probe -> assertThat(probe.genericEvidence())
                .as(probe.project() + " must declare generic compiler-owned evidence")
                .isNotBlank()
                .contains("#"));

        for (final ExternalProbeCatalog.ExternalProbe probe : realProbes()) {
            final String[] parts = probe.genericEvidence().split("#", 2);
            assertThat(parts[0])
                .as(probe.project() + " must point at the compiler-owned dependency test class")
                .isEqualTo("CliDependencyProjectIntegrationTest");
            assertThat(declaredTests)
                .as(probe.project() + " must map to an existing generic compiler-owned regression test")
                .contains(parts[1]);
        }
    }

    @TestFactory
    Stream<DynamicTest> pinnedArtifactProbesBuildAgainstPublishedArtifactsAndMatchJvmOutput() throws Exception {
        return realProbes().stream().map(probe -> DynamicTest.dynamicTest(probe.project(), () -> {
            final Path artifact = pinnedMavenArtifact(probe.groupId(), probe.artifactId(), probe.version());
            Assumptions.assumeTrue(Files.isRegularFile(artifact), "Pinned artifact is not available in the local Maven cache: " + probe.coordinate());
            assertExternalProbeMatchesJvmOutput(probe, artifact);
        }));
    }

    @Test
    void acceptanceRealProbesFailWhenRequiredArtifactsAreMissing() throws Exception {
        final Path probesRoot = tempDir.resolve("external-probes");
        writeProbeProject(
            probesRoot.resolve("alpha"),
            "alpha",
            "com.example",
            "alpha-lib",
            "1.0.0",
            "com.example.alpha.AlphaValue",
            """
                package com.acme;

                import com.example.alpha.AlphaValue;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) {
                        System.out.println(AlphaValue.text());
                    }
                }
                """,
            "alpha\n"
        );
        final Path repo = tempDir.resolve("empty-maven-repo");
        Files.createDirectories(repo);
        final Path wrapper = acceptanceWrapper();

        final ProcessResult run = process(
            tempDir,
            List.of("sh", Path.of(".github/scripts/acceptance.sh").toAbsolutePath().normalize().toString()),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_BIN", wrapper.toString(),
                "JAVAN_ACCEPTANCE_ONLY", "external-probe",
                "JAVAN_REQUIRE_EXTERNAL_SMOKE", "true",
                "JAVAN_MAVEN_REPO", repo.toString(),
                "JAVAN_EXTERNAL_SMOKE_DIR", probesRoot.toString()
            )
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("not ok - " + probesRoot.resolve("alpha").toString().replace('\\', '/') + " missing dependency");
    }

    @Test
    void acceptanceRealProbesHonorConfiguredMavenRepository() throws Exception {
        final Path probesRoot = tempDir.resolve("external-probes");
        writeProbeProject(
            probesRoot.resolve("alpha"),
            "alpha",
            "com.example",
            "alpha-lib",
            "1.0.0",
            "com.example.alpha.AlphaValue",
            """
                package com.acme;

                import com.example.alpha.AlphaValue;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) {
                        System.out.println(AlphaValue.text());
                    }
                }
                """,
            "alpha\n"
        );
        writeProbeProject(
            probesRoot.resolve("beta"),
            "beta",
            "com.example",
            "beta-lib",
            "2.0.0",
            "com.example.beta.BetaNumber",
            """
                package com.acme;

                import com.example.beta.BetaNumber;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) {
                        System.out.println(BetaNumber.value());
                    }
                }
                """,
            "42\n"
        );
        final Path repo = tempDir.resolve("custom-maven-repo");
        installMavenCoordinate(
            repo,
            "com.example",
            "alpha-lib",
            "1.0.0",
            dependencyJar("fake-alpha-lib", Map.of(
                "com.example.alpha.AlphaValue", """
                    package com.example.alpha;

                    public final class AlphaValue {
                        private AlphaValue() {
                        }

                        public static String text() {
                            return "alpha";
                        }
                    }
                    """
            ))
        );
        installMavenCoordinate(
            repo,
            "com.example",
            "beta-lib",
            "2.0.0",
            dependencyJar("fake-beta-lib", Map.of(
                "com.example.beta.BetaNumber", """
                    package com.example.beta;

                    public final class BetaNumber {
                        private BetaNumber() {
                        }

                        public static int value() {
                            return 42;
                        }
                    }
                    """
            ))
        );
        final Path wrapper = acceptanceWrapper();

        final ProcessResult run = process(
            tempDir,
            List.of("sh", Path.of(".github/scripts/acceptance.sh").toAbsolutePath().normalize().toString()),
            Duration.ofSeconds(60),
            Map.of(
                "JAVAN_BIN", wrapper.toString(),
                "JAVAN_ACCEPTANCE_ONLY", "external-probe",
                "JAVAN_REQUIRE_EXTERNAL_SMOKE", "true",
                "JAVAN_MAVEN_REPO", repo.toString(),
                "JAVAN_EXTERNAL_SMOKE_DIR", probesRoot.toString()
            )
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        final List<ExternalProbeCatalog.ExternalProbe> probes = realProbes(probesRoot);
        for (int index = 0; index < probes.size(); index++) {
            final ExternalProbeCatalog.ExternalProbe probe = probes.get(index);
            assertThat(run.stdout()).contains("ok " + (index + 1) + " - " + probe.projectDirectory() + " native probe");
        }
        assertThat(run.stdout()).contains("Acceptance passed: " + probes.size() + " checks");
    }

    @Test
    void realProbeDiscoveryIsDirectoryMetadataDriven() throws Exception {
        final Path probesRoot = tempDir.resolve("external-probes");
        writeProbe(probesRoot.resolve("zeta-dir"), "beta", "com.example", "beta-lib", "1.0.0", "beta-out\n");
        writeProbe(probesRoot.resolve("alpha-dir"), "alpha", "com.example", "alpha-lib", "2.0.0", "alpha-out\n");

        final List<ExternalProbeCatalog.ExternalProbe> probes = realProbes(probesRoot);

        assertThat(probes)
            .extracting(ExternalProbeCatalog.ExternalProbe::project)
            .containsExactly("alpha", "beta");
        assertThat(probes)
            .extracting(ExternalProbeCatalog.ExternalProbe::projectDirectory)
            .containsExactly(
                probesRoot.resolve("alpha-dir").toString().replace('\\', '/'),
                probesRoot.resolve("zeta-dir").toString().replace('\\', '/')
            );
        assertThat(probes)
            .extracting(ExternalProbeCatalog.ExternalProbe::groupId)
            .containsExactly("com.example", "com.example");
        assertThat(probes)
            .extracting(ExternalProbeCatalog.ExternalProbe::artifactId)
            .containsExactly("alpha-lib", "beta-lib");
        assertThat(probes)
            .extracting(ExternalProbeCatalog.ExternalProbe::version)
            .containsExactly("2.0.0", "1.0.0");
        assertThat(probes)
            .extracting(ExternalProbeCatalog.ExternalProbe::expectedStdout)
            .containsExactly("alpha-out\n", "beta-out\n");
    }

    @Test
    void realProbeArtifactListingIsMetadataDrivenAndDeduplicated() throws Exception {
        final Path probesRoot = tempDir.resolve("external-probes");
        writeProbe(probesRoot.resolve("beta"), "beta", "com.example", "beta-lib", "1.0.0", "beta-out\n");
        writeProbe(probesRoot.resolve("alpha"), "alpha", "com.example", "alpha-lib", "2.0.0", "alpha-out\n");
        writeProbe(probesRoot.resolve("alpha-copy"), "alpha-copy", "com.example", "alpha-lib", "2.0.0", "alpha-copy-out\n");

        final ProcessResult run = process(
            tempDir,
            List.of("sh", Path.of(".github/scripts/list-external-probe-artifacts.sh").toAbsolutePath().normalize().toString(), probesRoot.toString()),
            Duration.ofSeconds(20)
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).isEqualTo("""
            com.example:alpha-lib:2.0.0
            com.example:beta-lib:1.0.0
            """);
    }

    @Test
    void realProbeArtifactListingFailsClearlyWhenMetadataIsIncomplete() throws Exception {
        final Path probesRoot = tempDir.resolve("external-probes");
        final Path brokenProbe = probesRoot.resolve("broken");
        Files.createDirectories(brokenProbe);
        Files.writeString(
            brokenProbe.resolve("probe.properties"),
            """
                project=broken
                groupId=com.example
                artifactId=broken-lib
                """
        );

        final ProcessResult run = process(
            tempDir,
            List.of("sh", Path.of(".github/scripts/list-external-probe-artifacts.sh").toAbsolutePath().normalize().toString(), probesRoot.toString()),
            Duration.ofSeconds(20)
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Incomplete probe metadata");
    }

    @Test
    void sharedRealProbeBuildScriptResolvesMetadataDrivenArtifactAndBuilds() throws Exception {
        final Path repo = tempDir.resolve("custom-maven-repo");
        installMavenCoordinate(
            repo,
            "com.example",
            "alpha-lib",
            "1.0.0",
            dependencyJar("fake-alpha-lib", Map.of(
                "com.example.alpha.AlphaValue", """
                    package com.example.alpha;

                    public final class AlphaValue {
                        private AlphaValue() {
                        }

                        public static String text() {
                            return "alpha";
                        }
                    }
                    """
            ))
        );

        final Path probesRoot = tempDir.resolve("external-probes");
        final Path probe = probesRoot.resolve("alpha");
        writeProbeProject(
            probe,
            "alpha",
            "com.example",
            "alpha-lib",
            "1.0.0",
            "com.example.alpha.AlphaValue",
            """
                package com.acme;

                import com.example.alpha.AlphaValue;

                public final class Main {
                    private Main() {
                    }

                    public static void main(final String[] args) {
                        System.out.println(AlphaValue.text());
                    }
                }
                """,
            "alpha\n"
        );

        final Path helper = probe.getParent().resolve("build-external-probe.sh");
        Files.copy(Path.of("src/test/resources/external-probes/build-external-probe.sh"), helper);
        final Path wrapper = probe.resolve("build-example.sh");
        writeExecutableScript(wrapper, """
            #!/bin/sh
            set -eu

            ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
            exec "$ROOT/../build-external-probe.sh" "$ROOT"
            """);
        assertThat(helper.toFile().setExecutable(true)).isTrue();

        final ProcessResult run = process(
            probe,
            List.of("sh", wrapper.toString()),
            Duration.ofSeconds(60),
            Map.of(
                "JAVAN", acceptanceWrapper().toString(),
                "JAVAN_MAVEN_REPO", repo.toString()
            )
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).isEqualTo("alpha\n");
        assertThat(probe.resolve(".javan")).doesNotExist();
    }

    private void assertExternalProbeMatchesJvmOutput(final ExternalProbeCatalog.ExternalProbe probe, final Path artifact) throws Exception {
        final Path project = copyProjectDirectory(Path.of(probe.projectDirectory()), probe.project());
        final String jvmOutput = runJvm(project, probe.mainClass(), List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", probe.project());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/" + probe.project()).toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo(probe.expectedStdout());
    }

    private static List<ExternalProbeCatalog.ExternalProbe> realProbes() throws IOException {
        return ExternalProbeCatalog.realProbes();
    }

    private static List<ExternalProbeCatalog.ExternalProbe> realProbes(final Path probesRoot) throws IOException {
        return ExternalProbeCatalog.realProbes(probesRoot);
    }

    @Test
    void realProbesStayOutOfCompilerOwnedDependencyTests() throws Exception {
        final String genericTests = Files.readString(Path.of("src/test/java/javan/CliDependencyProjectIntegrationTest.java"));
        for (final Pattern pattern : ExternalProbeCatalog.identityPatterns()) {
            assertThat(pattern.matcher(genericTests).find())
                .as("compiler-owned dependency coverage should stay free of external probe identities")
                .isFalse();
        }
    }

    private static void writeProbe(
        final Path probeDirectory,
        final String project,
        final String groupId,
        final String artifactId,
        final String version,
        final String expectedStdout
    ) throws IOException {
        Files.createDirectories(probeDirectory);
        Files.writeString(
            probeDirectory.resolve("probe.properties"),
            "project=" + project + "\n"
                + "groupId=" + groupId + "\n"
                + "artifactId=" + artifactId + "\n"
                + "version=" + version + "\n"
                + "genericEvidence=CliDependencyProjectIntegrationTest#dependencyJarStaticIntMethodBuilds\n"
        );
        Files.writeString(probeDirectory.resolve("expected.stdout"), expectedStdout);
    }

    private static void writeProbeProject(
        final Path probeDirectory,
        final String project,
        final String groupId,
        final String artifactId,
        final String version,
        final String dependencyClassName,
        final String mainSource,
        final String expectedStdout
    ) throws IOException {
        writeProbe(probeDirectory, project, groupId, artifactId, version, expectedStdout);
        final Path source = probeDirectory.resolve("src/main/java/com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, mainSource);
        final String dependencyPath = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
        final String script = """
            #!/bin/sh
            set -eu

            ROOT=$(pwd)
            MAVEN_REPO=${JAVAN_MAVEN_REPO:-"$HOME/.m2/repository"}
            DEP_JAR="$MAVEN_REPO/%s"

            if [ ! -f "$DEP_JAR" ]; then
              echo "Dependency not found. Set JAVAN_MAVEN_REPO or install %s." >&2
              exit 3
            fi

            "$JAVAN" build "$ROOT" --classpath "$DEP_JAR" --output %s >/dev/null
            "$ROOT/.javan/bin/%s"
            """.formatted(dependencyPath, dependencyClassName, project, project);
        final Path scriptPath = probeDirectory.resolve("build-example.sh");
        Files.writeString(scriptPath, script);
        scriptPath.toFile().setExecutable(true, false);
    }

}
