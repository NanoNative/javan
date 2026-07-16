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
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

/**
 * External example compatibility smoke only.
 *
 * <p>These probes prove that javan can consume selected real third-party artifacts, but they do
 * not define JDK support rows or compiler-owned scenario coverage.
 */
@Execution(SAME_THREAD)
@Tag("external-probe")
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliExternalProbeAcceptanceIntegrationTest extends CliIntegrationSupport {
    private static final Path REAL_PROBES = Path.of("src/test/resources/projects/real-probes");

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
        final Path repo = tempDir.resolve("empty-maven-repo");
        Files.createDirectories(repo);
        final Path wrapper = acceptanceWrapper();
        final ExternalProbe firstProbe = realProbes().getFirst();

        final ProcessResult run = process(
            tempDir,
            List.of("sh", Path.of(".github/scripts/acceptance.sh").toAbsolutePath().normalize().toString()),
            Duration.ofSeconds(20),
            Map.of(
                "JAVAN_BIN", wrapper.toString(),
                "JAVAN_ACCEPTANCE_ONLY", "real-probes",
                "JAVAN_REQUIRE_REAL_PROBES", "true",
                "JAVAN_MAVEN_REPO", repo.toString()
            )
        );

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("not ok - " + firstProbe.projectDirectory() + " missing dependency");
    }

    @Test
    void acceptanceRealProbesHonorConfiguredMavenRepository() throws Exception {
        final Path repo = tempDir.resolve("custom-maven-repo");
        final Path typeMapJar = dependencyJar("fake-typemap", Map.of(
            "berlin.yuna.typemap.model.Pair", """
                package berlin.yuna.typemap.model;

                public final class Pair<L, R> {
                    private final L key;
                    private final R value;

                    public Pair(final L key, final R value) {
                        this.key = key;
                        this.value = value;
                    }

                    public L getKey() {
                        return key;
                    }

                    public R getValue() {
                        return value;
                    }
                }
                """
        ));
        final Path nanoJar = dependencyJar("fake-nano", Map.of(
            "org.nanonative.nano.services.metric.model.MetricUpdate", """
                package org.nanonative.nano.services.metric.model;

                public record MetricUpdate(Object timestamp, String name, Object value, Object tags) {
                }
                """,
            "org.nanonative.nano.helper.NanoUtils", """
                package org.nanonative.nano.helper;

                public final class NanoUtils {
                    private NanoUtils() {
                    }

                    public static String formatDuration(final long nanos) {
                        return "1m 5s";
                    }
                }
                """,
            "org.nanonative.nano.core.model.Scheduler", """
                package org.nanonative.nano.core.model;

                import java.util.concurrent.ScheduledThreadPoolExecutor;

                public final class Scheduler extends ScheduledThreadPoolExecutor {
                    public Scheduler(final String name) {
                        super(1);
                    }
                }
                """
        ));

        for (final ExternalProbe probe : realProbes()) {
            final Path jar = "type-map".equals(probe.artifactId()) ? typeMapJar : nanoJar;
            installMavenCoordinate(repo, probe.groupId(), probe.artifactId(), probe.version(), jar);
        }
        final Path wrapper = acceptanceWrapper();

        final ProcessResult run = process(
            tempDir,
            List.of("sh", Path.of(".github/scripts/acceptance.sh").toAbsolutePath().normalize().toString()),
            Duration.ofSeconds(60),
            Map.of(
                "JAVAN_BIN", wrapper.toString(),
                "JAVAN_ACCEPTANCE_ONLY", "real-probes",
                "JAVAN_REQUIRE_REAL_PROBES", "true",
                "JAVAN_MAVEN_REPO", repo.toString()
            )
        );

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        for (int index = 0; index < realProbes().size(); index++) {
            final ExternalProbe probe = realProbes().get(index);
            assertThat(run.stdout()).contains("ok " + (index + 1) + " - " + probe.projectDirectory() + " native probe");
        }
        assertThat(run.stdout()).contains("Acceptance passed: " + realProbes().size() + " checks");
    }

    @Test
    void realProbeDiscoveryIsDirectoryMetadataDriven() throws Exception {
        final Path probesRoot = tempDir.resolve("real-probes");
        writeProbe(probesRoot.resolve("beta"), "beta", "com.example", "beta-lib", "1.0.0", "beta-out\n");
        writeProbe(probesRoot.resolve("alpha"), "alpha", "com.example", "alpha-lib", "2.0.0", "alpha-out\n");

        final List<ExternalProbe> probes = realProbes(probesRoot);

        assertThat(probes)
            .extracting(ExternalProbe::project)
            .containsExactly("alpha", "beta");
        assertThat(probes)
            .extracting(ExternalProbe::groupId)
            .containsExactly("com.example", "com.example");
        assertThat(probes)
            .extracting(ExternalProbe::artifactId)
            .containsExactly("alpha-lib", "beta-lib");
        assertThat(probes)
            .extracting(ExternalProbe::version)
            .containsExactly("2.0.0", "1.0.0");
        assertThat(probes)
            .extracting(ExternalProbe::expectedStdout)
            .containsExactly("alpha-out\n", "beta-out\n");
    }

    private void assertExternalProbeMatchesJvmOutput(final ExternalProbe probe, final Path artifact) throws Exception {
        final Path project = copyResourceProject("real-probes/" + probe.project(), probe.project());
        final String jvmOutput = runJvm(project, probe.mainClass(), List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", probe.project());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/" + probe.project()).toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo(probe.expectedStdout());
    }

    private static List<ExternalProbe> realProbes() throws IOException {
        return realProbes(REAL_PROBES);
    }

    private static List<ExternalProbe> realProbes(final Path probesRoot) throws IOException {
        try (Stream<Path> paths = Files.list(probesRoot)) {
            return paths
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .map(CliExternalProbeAcceptanceIntegrationTest::loadProbe)
                .toList();
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
        );
        Files.writeString(probeDirectory.resolve("expected.stdout"), expectedStdout);
    }

    private static ExternalProbe loadProbe(final Path projectDirectory) {
        try {
            final Properties properties = new Properties();
            properties.load(new StringReader(Files.readString(projectDirectory.resolve("probe.properties"))));
            return new ExternalProbe(
                property(properties, "project"),
                property(properties, "groupId"),
                property(properties, "artifactId"),
                property(properties, "version"),
                properties.getProperty("mainClass", "com.acme.Main"),
                Files.readString(projectDirectory.resolve("expected.stdout")),
                projectDirectory.toString().replace('\\', '/')
            );
        } catch (final IOException exception) {
            throw new IllegalStateException("Unable to load external probe metadata from " + projectDirectory, exception);
        }
    }

    private static String property(final Properties properties, final String key) {
        final String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing probe property: " + key);
        }
        return value;
    }

    private record ExternalProbe(
        String project,
        String groupId,
        String artifactId,
        String version,
        String mainClass,
        String expectedStdout,
        String projectDirectory
    ) {
        private String coordinate() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }
}
