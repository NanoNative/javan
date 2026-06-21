package javan;

import javan.cli.Cli;
import javan.cli.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class CliCommandIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void helpPrintsUsage() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("javan " + Version.number());
        assertThat(run.stdout()).contains(
            "javan --version",
            "javan inspect",
            "javan check",
            "javan test",
            "javan build",
            "javan report",
            "--jar",
            "--library",
            "--format <formats>",
            "--kind <kind>",
            "--profile <profile>",
            "core, service, library, or strict"
        );
        assertThat(run.stdout()).doesNotContain("--no-build");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void versionPrintsProjectVersion() {
        final CliRun run = run(tempDir, "--version");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).isEqualTo("javan " + Version.number() + "\n");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void helpListsToolchainListCommand() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).contains("javan toolchain list");
    }

    @Test
    void helpMentionsJavacWrapper() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).contains("javan javac [javac args...]");
    }

    @Test
    void javacVersionDelegatesToJavac() {
        final ProcessResult javac = process(tempDir, List.of("javac", "-version"));

        final CliRun run = run(tempDir, "javac", "-version");

        assertThat(run.exitCode()).isEqualTo(javac.exitCode());
        assertThat(run.stdout()).isEqualTo(javac.stdout());
        assertThat(run.stderr()).isEqualTo(javac.stderr());
    }

    @Test
    void javacReleaseCompilesSourceIntoCurrentDirectory() throws Exception {
        final Path source = tempDir.resolve("JavacWrapperSmoke.java");
        Files.writeString(source, """
            public final class JavacWrapperSmoke {
                private JavacWrapperSmoke() {
                }
            }
            """);

        final CliRun run = run(tempDir, "javac", "--release", "25", source.getFileName().toString());

        assertThat(run.exitCode()).isZero();
        assertThat(tempDir.resolve("JavacWrapperSmoke.class")).exists();
    }

    @Test
    void reportWritesAndPrintsUnifiedSummary() throws Exception {
        final Path project = project("report-writes");
        final Path reports = project.resolve(".javan/reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("project.json"), """
            {
              "buildTool": "JAVAC",
              "profile": "service",
              "sourceFolders": ["src/main/java"],
              "resourceFolders": [],
              "classFolders": [".javan/classes"],
              "classpathEntries": [],
              "warnings": []
            }
            """, StandardCharsets.UTF_8);

        final CliRun run = run(tempDir, "report", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).isEqualTo(Files.readString(reports.resolve("report.md")));
        assertThat(reports.resolve("report.json")).exists();
    }

    @Test
    void reportReadsRelativeTargetFromCurrentDirectory() throws Exception {
        final Path project = project("report-relative");
        final Path reports = project.resolve(".javan/reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("project.json"), """
            {
              "buildTool": "JAVAC",
              "profile": "core",
              "sourceFolders": ["src/main/java"],
              "resourceFolders": [],
              "classFolders": [".javan/classes"],
              "classpathEntries": [],
              "warnings": []
            }
            """, StandardCharsets.UTF_8);

        final CliRun run = run(tempDir, "report", project.getFileName().toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(project.resolve(".javan/reports/report.json")).exists();
    }

    @Test
    void reportCountsExistingDiagnosticsFile() throws Exception {
        final Path project = project("report-diagnostics");
        final Path reports = project.resolve(".javan/reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("diagnostics.txt"), """
            error[JAVAN031]: unsupported API

            warning[JAVAN145]: unreachable bytecode
            """, StandardCharsets.UTF_8);

        final CliRun run = run(tempDir, "report", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(reports.resolve("report.json")))
            .contains("\"diagnostics\": 2", "\"errors\": 1", "\"warnings\": 1");
    }

    @Test
    void reportMarksMissingFamiliesAbsent() throws Exception {
        final Path project = project("report-missing");
        final Path reports = project.resolve(".javan/reports");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve("project.json"), """
            {
              "buildTool": "JAVAC",
              "profile": "core",
              "sourceFolders": [],
              "resourceFolders": [],
              "classFolders": [],
              "classpathEntries": [],
              "warnings": []
            }
            """, StandardCharsets.UTF_8);

        final CliRun run = run(tempDir, "report", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(reports.resolve("report.json")))
            .contains("{\"name\": \"diagnostics\", \"status\": \"absent\"");
    }

    @Test
    void reportFailsWhenReportsDirectoryIsMissing() throws Exception {
        final Path project = project("report-empty");

        final CliRun run = run(tempDir, "report", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("No .javan/reports directory");
    }

    @Test
    void doctorReportsToolchain() {
        final CliRun run = run(tempDir, "doctor");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("javan home:", "java.home:", "java.version:", "javac:", "c compiler:", "global settings:");
    }

    @Test
    void toolchainListPrintsToolchainHeader() {
        final CliRun run = run(tempDir, "toolchain", "list");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Toolchains");
    }

    @Test
    void toolchainDoctorPrintsDoctorReport() {
        final CliRun run = run(tempDir, "toolchain", "doctor");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Toolchain");
    }

    @Test
    void toolchainFailsWhenSubcommandIsMissing() {
        final CliRun run = run(tempDir, "toolchain");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Missing toolchain command: list or doctor");
    }

    @Test
    void toolchainFailsWhenExtraArgumentsAreProvided() {
        final CliRun run = run(tempDir, "toolchain", "list", "extra", "now");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Unexpected toolchain arguments: extra now");
    }

    @Test
    void toolchainFailsWhenSubcommandIsUnsupported() {
        final CliRun run = run(tempDir, "toolchain", "install");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Unsupported toolchain command: install");
    }

    private Path project(final String name) throws Exception {
        final Path project = tempDir.resolve(name);
        Files.createDirectories(project.resolve("src/main/java"));
        return project;
    }

    private static CliRun run(final Path cwd, final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
            new Cli().run(cwd, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), args)
        );
        return new CliRun(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static ProcessResult process(final Path cwd, final List<String> command) {
        try {
            final Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
            final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(1, TimeUnit.SECONDS);
                }
                return new ProcessResult(
                    124,
                    stdout.join(),
                    stderr.join() + "Timed out after 10 seconds: " + String.join(" ", command) + "\n"
                );
            }
            return new ProcessResult(
                process.exitValue(),
                stdout.join(),
                stderr.join()
            );
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running process: " + String.join(" ", command), exception);
        }
    }

    private static String readStream(final InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
