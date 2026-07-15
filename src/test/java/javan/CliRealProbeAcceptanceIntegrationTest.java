package javan;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliRealProbeAcceptanceIntegrationTest extends CliIntegrationSupport {
    @Test
    void typeMapPairProbeBuildsAgainstPinnedMavenArtifactAndMatchesJvmOutput() throws Exception {
        final Path artifact = pinnedMavenArtifact("berlin.yuna", "type-map", "2025.06.1521025");
        Assumptions.assumeTrue(Files.isRegularFile(artifact), "Pinned TypeMap artifact is not available in the local Maven cache");
        final Path project = copyResourceProject("real-probes/typemap-pair", "typemap-pair");

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", "typemap-pair");

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typemap-pair").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void nanoMetricProbeBuildsAgainstPinnedMavenArtifactAndMatchesJvmOutput() throws Exception {
        final Path artifact = pinnedMavenArtifact("org.nanonative", "nano", "2025.11.3131219");
        Assumptions.assumeTrue(Files.isRegularFile(artifact), "Pinned Nano artifact is not available in the local Maven cache");
        final Path project = copyResourceProject("real-probes/nano-metric", "nano-metric");

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", "nano-metric");

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/nano-metric").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("requests\n");
    }

    @Test
    void nanoDurationProbeBuildsAgainstPinnedMavenArtifactAndMatchesJvmOutput() throws Exception {
        final Path artifact = pinnedMavenArtifact("org.nanonative", "nano", "2025.11.3131219");
        Assumptions.assumeTrue(Files.isRegularFile(artifact), "Pinned Nano artifact is not available in the local Maven cache");
        final Path project = copyResourceProject("real-probes/nano-duration", "nano-duration");

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", "nano-duration");

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/nano-duration").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1m 5s\n");
    }

    @Test
    void nanoSchedulerProbeBuildsAgainstPinnedMavenArtifactAndMatchesJvmOutput() throws Exception {
        final Path artifact = pinnedMavenArtifact("org.nanonative", "nano", "2025.11.3131219");
        Assumptions.assumeTrue(Files.isRegularFile(artifact), "Pinned Nano artifact is not available in the local Maven cache");
        final Path project = copyResourceProject("real-probes/nano-scheduler", "nano-scheduler");

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", "nano-scheduler");

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/nano-scheduler").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("tick\ntrue\n");
    }

    @Test
    void nanoFixedRateSchedulerProbeBuildsAgainstPinnedMavenArtifactAndMatchesJvmOutput() throws Exception {
        final Path artifact = pinnedMavenArtifact("org.nanonative", "nano", "2025.11.3131219");
        Assumptions.assumeTrue(Files.isRegularFile(artifact), "Pinned Nano artifact is not available in the local Maven cache");
        final Path project = copyResourceProject("real-probes/nano-scheduler-fixed-rate", "nano-scheduler-fixed-rate");

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(artifact));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", artifact.toString(), "--output", "nano-scheduler-fixed-rate");

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/nano-scheduler-fixed-rate").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\ndone\n");
    }

    @Test
    void acceptanceRealProbesFailWhenRequiredArtifactsAreMissing() throws Exception {
        final Path repo = tempDir.resolve("empty-maven-repo");
        Files.createDirectories(repo);
        final Path wrapper = acceptanceWrapper();

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
        assertThat(run.stderr()).contains("not ok - src/test/resources/projects/real-probes/typemap-pair missing TYPEMAP_JAR");
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
        installMavenCoordinate(repo, "berlin.yuna", "type-map", "2025.06.1521025", typeMapJar);
        installMavenCoordinate(repo, "org.nanonative", "nano", "2025.11.3131219", nanoJar);
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
        assertThat(run.stdout()).contains(
            "ok 1 - src/test/resources/projects/real-probes/typemap-pair native probe",
            "ok 2 - src/test/resources/projects/real-probes/nano-metric native probe",
            "ok 3 - src/test/resources/projects/real-probes/nano-duration native probe",
            "ok 4 - src/test/resources/projects/real-probes/nano-scheduler native probe",
            "ok 5 - src/test/resources/projects/real-probes/nano-scheduler-fixed-rate native probe",
            "Acceptance passed: 5 checks"
        );
    }
}
