package javan;

import javan.testing.TestSuite.NativeTest;

import javan.cli.Cli;
import javan.cli.Version;
import javan.toolchain.ToolchainManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(Resources.SYSTEM_PROPERTIES)
@NativeTest
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
            "--jobs <count>",
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
    void helpListsJdkResolveCommand() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).contains("javan jdk resolve");
    }

    @Test
    void helpDoesNotAdvertiseLowLevelJdkFacadePlumbing() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).doesNotContain("javan jdk facade <directory>");
    }

    @Test
    void jdkFacadeCreatesALinkedSdkLayoutForTheSelectedLocalJdk() {
        assumeFalse(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win"));
        final Path facade = tempDir.resolve("javan-jdk");

        final CliRun run = run(tempDir, "jdk", "facade", facade.getFileName().toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("JDK facade", facade.toString());
        assertThat(facade.resolve("release")).isRegularFile();
        assertThat(facade.resolve("bin/javac")).isExecutable();
        assertThat(facade.resolve("lib")).isSymbolicLink();
    }

    @Test
    void helpDoesNotAdvertiseTheLegacyJavacEntryPoint() {
        final CliRun run = run(tempDir, "--help");

        assertThat(run.stdout()).doesNotContain("javan javac [javac args...]");
    }

    @Test
    void javacVersionDelegatesToJavac() throws Exception {
        final String javacExecutable = new ToolchainManager()
            .resolveLocalJdk(java.util.Optional.empty())
            .selected()
            .orElseThrow()
            .javacExecutable()
            .toString();
        final ProcessResult javac = process(tempDir, List.of(javacExecutable, "-version"));

        final CliRun run = run(tempDir, "javac", "-version");

        assertThat(run.exitCode()).isEqualTo(javac.exitCode());
        assertThat(run.stdout()).startsWith(javac.stdout());
        assertThat(run.stdout()).contains("Javan facade " + Version.number(), "Backend:", "Javac:");
        assertThat(run.stderr()).isEqualTo(javac.stderr());
        assertThat(tempDir.resolve(".javan")).doesNotExist();
    }

    @Test
    void javacHelpAppendsTheImplementedJavanExtensionSection() throws Exception {
        final String javacExecutable = new ToolchainManager()
            .resolveLocalJdk(java.util.Optional.empty())
            .selected()
            .orElseThrow()
            .javacExecutable()
            .toString();
        final ProcessResult javac = process(tempDir, List.of(javacExecutable, "--help"));

        final CliRun run = run(tempDir, "javac", "--help");

        assertThat(run.exitCode()).isEqualTo(javac.exitCode());
        assertThat(run.stdout()).startsWith(javac.stdout());
        assertThat(run.stdout()).contains("Javan extensions", "--jn-help", "--jn-end");
        assertThat(run.stderr()).isEqualTo(javac.stderr());
        assertThat(tempDir.resolve(".javan")).doesNotExist();
    }

    @Test
    void javacJavanHelpDoesNotInvokeTheBackendCompiler() {
        final CliRun run = run(tempDir, "javac", "--jn-help");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Javan extensions", "--jn-version", "--jn-build", "javan check");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void javacJavanVersionReportsFacadeAndBackendIdentity() {
        final CliRun run = run(tempDir, "javac", "--jn-version");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Javan facade " + Version.number(), "Backend:", "Javac:");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void javacRejectsAnUnknownJavanExtensionBeforeInvokingTheBackendCompiler() {
        final CliRun run = run(tempDir, "javac", "--jn-future");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("Unsupported Javan compiler option: --jn-future");
    }

    @Test
    void javacReleaseCompilesSourceIntoCurrentDirectory() throws Exception {
        final Path source = tempDir.resolve("JavacWrapperSmoke.java");
        final Path classes = tempDir.resolve("classes");
        Files.writeString(source, """
            public final class JavacWrapperSmoke {
                private JavacWrapperSmoke() {
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "javac",
            "--release",
            "25",
            "-d",
            classes.toString(),
            source.getFileName().toString()
        );

        assertThat(run.exitCode()).isZero();
        assertThat(classes.resolve("JavacWrapperSmoke.class")).exists();
        assertThat(tempDir.resolve(".javan/reports/report.json")).exists();
        assertThat(Files.readString(tempDir.resolve(".javan/reports/javac-invocation.json")))
            .contains("\"analysis\": \"completed\"");
        assertThat(run.stdout()).contains("Javan report:");
    }

    @Test
    void javacWithoutOutputDirectoryWritesAnUnavailableInvocationReport() throws Exception {
        final Path source = tempDir.resolve("JavacWrapperNoOutput.java");
        Files.writeString(source, "public final class JavacWrapperNoOutput { }\n");

        final CliRun run = run(tempDir, "javac", source.getFileName().toString());

        assertThat(run.exitCode()).isZero();
        assertThat(tempDir.resolve("JavacWrapperNoOutput.class")).exists();
        assertThat(Files.readString(tempDir.resolve(".javan/reports/javac-invocation.json")))
            .contains("\"analysis\": \"unavailable\"", "fresh class output cannot be proven");
    }

    @Test
    void javacOffSkipsEveryJavanReport() throws Exception {
        final Path source = tempDir.resolve("JavacWrapperOff.java");
        final Path classes = tempDir.resolve("classes");
        Files.writeString(source, "public final class JavacWrapperOff { }\n");

        final CliRun run = run(tempDir, "javac", "--jn-off", "-d", classes.toString(), source.getFileName().toString());

        assertThat(run.exitCode()).isZero();
        assertThat(classes.resolve("JavacWrapperOff.class")).exists();
        assertThat(tempDir.resolve(".javan")).doesNotExist();
    }

    @Test
    void javacBuildCreatesAndRunsANativeAppFromTheFreshClassOutput() throws Exception {
        final Path source = tempDir.resolve("FacadeNativeMain.java");
        final Path classes = tempDir.resolve("classes");
        Files.writeString(source, """
            package com.acme;

            public final class FacadeNativeMain {
                public static void main(final String[] args) {
                    System.out.println("facade-native");
                }
            }
            """);

        final CliRun compile = run(
            tempDir,
            "javac",
            "--jn-build",
            "--jn-main",
            "com.acme.FacadeNativeMain",
            "--jn-out",
            "facade-native",
            "-d",
            classes.toString(),
            source.getFileName().toString()
        );
        final Path binary = tempDir.resolve(".javan/bin/facade-native");

        assertThat(compile.exitCode()).isZero();
        assertThat(binary).isExecutable();
        assertThat(Files.readString(tempDir.resolve(".javan/reports/javac-invocation.json")))
            .contains("\"analysis\": \"built\"");
        assertThat(process(binary.getParent(), List.of(binary.toString())).stdout()).isEqualTo("facade-native\n");

        final CliRun rebuilt = run(
            tempDir,
            "javac",
            "--jn-build",
            "--jn-main",
            "com.acme.FacadeNativeMain",
            "--jn-out",
            "facade-native",
            "-d",
            classes.toString(),
            source.getFileName().toString()
        );

        assertThat(rebuilt.exitCode()).isZero();
        assertThat(Files.readString(tempDir.resolve(".javan/reports/native-object-cache.json")))
            .contains("\"source\": \"main.c\", \"decision\": \"reused\"")
            .contains("\"source\": \"javan_runtime.c\", \"decision\": \"reused\"");
        assertThat(Files.readString(tempDir.resolve(".javan/reports/report.md"))).contains("`native-object-cache` | present");
    }

    @Test
    void buildRecordsBoundedNativeWorkerEvidence() throws Exception {
        final Path project = project("native-workers");
        final Path source = project.resolve("src/main/java/com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(message());
                }

                static String message() {
                    return "native-workers";
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString(), "--main", "com.acme.Main", "--jobs", "1");

        assertThat(build.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/native-object-cache.json"))).contains(
            "\"requestedJobs\": 1",
            "\"effectiveJobs\": 1",
            "\"queued\": 2",
            "\"backoffs\": 0",
            "\"outcome\": \"succeeded\""
        );
    }

    @Test
    void javacStrictFailsOnlyAfterSuccessfulJavaCompilationAndCanEmitJsonlDiagnostics() throws Exception {
        final Path source = tempDir.resolve("FacadeStrictMain.java");
        final Path classes = tempDir.resolve("classes");
        Files.writeString(source, """
            public final class FacadeStrictMain {
                public static void main(final String[] args) throws Exception {
                    Class.forName("com.acme.OptionalPlugin", true, ClassLoader.getSystemClassLoader());
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "javac",
            "--jn-strict",
            "--jn-diag",
            "jsonl",
            "-d",
            classes.toString(),
            source.getFileName().toString()
        );

        assertThat(classes.resolve("FacadeStrictMain.class")).exists();
        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("{\"schemaVersion\":1,\"severity\":\"error\"", "\"code\":\"JAVAN031\"");
        assertThat(Files.readString(tempDir.resolve(".javan/reports/javac-invocation.json")))
            .contains("\"analysis\": \"completed\"");
    }

    @Test
    void failedJavacWritesAnInvocationReportWithoutInspectingClasses() throws Exception {
        final Path source = tempDir.resolve("JavacWrapperBroken.java");
        final Path classes = tempDir.resolve("classes");
        Files.writeString(source, "public final class JavacWrapperBroken { Missing value; }\n");

        final CliRun run = run(tempDir, "javac", "-d", classes.toString(), source.getFileName().toString());

        assertThat(run.exitCode()).isNotZero();
        assertThat(Files.readString(tempDir.resolve(".javan/reports/javac-invocation.json")))
            .contains("\"analysis\": \"not-run\"", "javac failed; Javan did not inspect class output");
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
    void checkWritesAVisualizableCallGraphAndUnifiedSummary() throws Exception {
        final Path project = project("call-graph-report");
        final Path source = project.resolve("src/main/java/com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    helper();
                }
                static void helper() {
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        final Path reports = project.resolve(".javan/reports");
        assertThat(Files.readString(reports.resolve("call-graph.json")))
            .contains(
                "\"entryPoint\": \"com/acme/Main.main([Ljava/lang/String;)V\"",
                "\"caller\": \"com/acme/Main.main([Ljava/lang/String;)V\"",
                "\"callee\": \"com/acme/Main.helper()V\"",
                "\"kind\": \"call\""
            );
        assertThat(Files.readString(reports.resolve("call-graph.dot")))
            .contains("digraph javan_call_graph", "com/acme/Main.main([Ljava/lang/String;)V");
        assertThat(Files.readString(reports.resolve("call-flow.html")))
            .contains("Call flow", "Main", "main(String[])")
            .doesNotContain("com/acme/Main.main([Ljava/lang/String;)V");
        assertThat(Files.readString(reports.resolve("report.json")))
            .contains("\"name\": \"call-graph\"", "\"edgeCount\": 1");
    }

    @Test
    void checkSynchronizesVerifierFindingsWithEveryCallGraphOutput() throws Exception {
        final Path project = project("call-graph-findings");
        final Path source = project.resolve("src/main/java/com/acme/Main.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = "not-a-locale";
                    System.out.println("JAVAN".toLowerCase((Locale) value));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isNotZero();
        final Path reports = project.resolve(".javan/reports");
        assertThat(Files.readString(reports.resolve("call-graph.json")))
            .contains(
                "\"diagnostics\": 1",
                "\"errors\": 1",
                "\"methodsWithFindings\": 1",
                "\"codes\": [\"JAVAN045\"]"
            );
        assertThat(Files.readString(reports.resolve("call-graph.md")))
            .contains("## Static Findings", "error `[JAVAN045]` unsupported checkcast target");
        assertThat(Files.readString(reports.resolve("call-flow.html")))
            .contains("class=\"node entry error\"", "[JAVAN045]", "unsupported checkcast target");
        assertThat(Files.readString(reports.resolve("call-graph.dot")))
            .contains("fillcolor=\"#fef2f2\"", "JAVAN045");
        assertThat(Files.readString(reports.resolve("report.json")))
            .contains("\"name\": \"call-graph\"", "\"methodsWithFindings\": 1");
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
    void jdkResolveReportsTheSelectedLocalJdk() {
        final CliRun run = run(tempDir, "jdk", "resolve");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).contains("JDK resolution", "selected:", "java:", "javac:");
    }

    @Test
    void jdkResolveSelectsAnExplicitUsableJdkHome() throws Exception {
        final Path home = Files.createDirectories(tempDir.resolve("explicit-jdk"));
        final Path bin = Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("release"));
        assertThat(Files.createFile(bin.resolve("java")).toFile().setExecutable(true)).isTrue();
        assertThat(Files.createFile(bin.resolve("javac")).toFile().setExecutable(true)).isTrue();

        final CliRun run = run(tempDir, "jdk", "resolve", home.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).contains("selected: explicit", "home:     " + home.toAbsolutePath().normalize());
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
        final int exitCode = assertTimeoutPreemptively(defaultCliTimeout(), () ->
            new Cli().run(cwd, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), args)
        );
        return new CliRun(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static ProcessResult process(final Path cwd, final List<String> command) {
        return process(cwd, command, defaultProcessTimeout());
    }

    private static Duration defaultCliTimeout() {
        return isCiEnvironment() ? Duration.ofSeconds(45) : Duration.ofSeconds(20);
    }

    private static Duration defaultProcessTimeout() {
        return isCiEnvironment() ? Duration.ofSeconds(20) : Duration.ofSeconds(10);
    }

    private static boolean isCiEnvironment() {
        return "true".equalsIgnoreCase(System.getenv("CI"));
    }

    private static ProcessResult process(final Path cwd, final List<String> command, final Duration timeout) {
        try {
            final Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
            final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));
            final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(1, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(1, TimeUnit.SECONDS);
                }
                return new ProcessResult(
                    124,
                    stdout.join(),
                    stderr.join() + "Timed out after " + timeout.toSeconds() + " seconds: " + String.join(" ", command) + "\n"
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
