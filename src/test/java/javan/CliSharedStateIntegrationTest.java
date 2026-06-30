package javan;

import javan.cli.Cli;
import javan.util.Files2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

final class CliSharedStateIntegrationTest {
    @TempDir
    private Path tempDir;

    @Test
    void selfHostNativeCheckPassesCurrentClasses() throws Exception {
        final Path classes = Path.of("").toAbsolutePath().resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();
        Files2.deleteRecursive(classes.resolve(".javan"));

        final CliRun run = run(classes, "check", classes.toString(), "--main", "javan.Main");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(classes.resolve(".javan")).doesNotExist();
        assertThat(run.stdout()).contains("Checking static Java profile", "reachable classes:", "diagnostics:");
        assertThat(run.stdout())
            .contains("diagnostics:       0")
            .doesNotContain("warning[", "error[");
        assertThat(Files.readString(classes.getParent().resolve(".javan/reports/diagnostics.txt")))
            .doesNotContain(
                "java/util/List.stream()Ljava/util/stream/Stream;",
                "java/util/regex/Pattern.compile(Ljava/lang/String;)Ljava/util/regex/Pattern;",
                "error[JAVAN",
                "java/util/List.add(Ljava/lang/Object;)Z",
                "java/util/List.of()Ljava/util/List;"
            )
            .doesNotContain("warning[JAVAN130]")
            .doesNotContain("JAVAN015")
            .doesNotContain("JAVAN011")
            .doesNotContain("JAVAN012")
            .doesNotContain("javan/build/BuildInvoker.<init>()V");
    }

    @Test
    void selfHostNativeCheckPassesFromChildJvm() throws Exception {
        final Path root = Path.of("").toAbsolutePath();
        final Path classes = root.resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();

        final ProcessResult run = processSlow(root, List.of(
            "java",
            "-cp",
            classes.toString(),
            "javan.Main",
            "check",
            classes.toString(),
            "--main",
            "javan.Main"
        ));

        assertThat(run.exitCode()).describedAs(run.stderr()).isZero();
        assertThat(run.stdout()).contains("Checking static Java profile", "reachable classes:", "diagnostics:");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void selfHostCheckRejectsReachableDisabledProcessSubstitution() throws Exception {
        final Path classes = Path.of("").toAbsolutePath().resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();
        final Path config = classes.resolve("javan.toml");
        Files.deleteIfExists(config);

        try {
            Files.writeString(config, """
                [runtime]
                disabled = ["process"]
                """);

            final CliRun run = run(classes, "check", classes.toString(), "--main", "javan.Main");

            assertThat(run.exitCode()).isEqualTo(2);
            assertThat(run.stderr()).contains("error[JAVAN060]", "process");
            assertThat(Files.readString(classes.getParent().resolve(".javan/reports/runtime-features.json"))).contains(
                "\"disabledReachableRuntimeModules\": [\"process\"]",
                "\"status\": \"fail\""
            );
        } finally {
            Files.deleteIfExists(config);
        }
    }

    @Test
    void javanModCoordinateDependencyCompilesFromLocalMavenRepository() throws Exception {
        final Path dependency = dependencyJar("mod-coordinate-mathlib", "dep.ModCoordinateMath", """
            package dep;

            public final class ModCoordinateMath {
                private ModCoordinateMath() {
                }

                public static int value() {
                    return 19;
                }
            }
            """);
        final Path repository = tempDir.resolve("local-maven-repository");
        installMavenCoordinate(repository, "com.acme", "mod-coordinate-mathlib", "1.0.0", dependency);
        final Path project = project("javan-mod-coordinate-dependency");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.ModCoordinateMath;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(ModCoordinateMath.value());
                }
            }
            """);
        Files.writeString(project.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main com.acme:mod-coordinate-mathlib:1.0.0
            """, StandardCharsets.UTF_8);

        final String previous = System.getProperty("javan.maven.localRepository");
        System.setProperty("javan.maven.localRepository", repository.toString());
        try {
            final CliRun run = run(tempDir, "check", project.toString());

            assertThat(run.exitCode()).describedAs(run.stderr()).isZero();
            assertThat(Files.readString(project.resolve("javan.lock"))).contains(
                "\"kind\": \"coordinate\"",
                "\"notation\": \"com.acme:mod-coordinate-mathlib:1.0.0\"",
                "\"status\": \"present\"",
                "\"checksumAlgorithm\": \"fnv64\""
            );
            assertThat(Files.readString(project.resolve(".javan/reports/dependencies.json"))).contains(
                "\"source\": \"javan.mod\"",
                "\"scope\": \"main\"",
                "\"used\": true",
                "\"reachableClasses\": [\"dep/ModCoordinateMath\"]"
            );
        } finally {
            if (previous == null) {
                System.clearProperty("javan.maven.localRepository");
            } else {
                System.setProperty("javan.maven.localRepository", previous);
            }
        }
    }

    private Path project(final String name) throws Exception {
        final Path project = tempDir.resolve(name);
        Files.createDirectories(project.resolve("src/main/java"));
        return project;
    }

    private static void writeJava(final Path project, final String className, final String source) throws Exception {
        final Path file = project.resolve("src/main/java").resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static void installMavenCoordinate(
        final Path repository,
        final String groupId,
        final String artifactId,
        final String version,
        final Path jar
    ) throws Exception {
        final Path target = repository
            .resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".jar");
        Files.createDirectories(target.getParent());
        Files.copy(jar, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private Path dependencyJar(final String name, final String className, final String source) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path jar = root.resolve(name + ".jar");
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(process(root, List.of("javac", "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        assertThat(process(root, List.of("jar", "--create", "--file", jar.toString(), "-C", classes.toString(), ".")).exitCode()).isZero();
        return jar;
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

    private static ProcessResult processSlow(final Path cwd, final List<String> command) {
        return process(cwd, command, Duration.ofSeconds(60));
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
            final List<String> actualCommand = childCoverageCommand(command);
            final Process process = new ProcessBuilder(actualCommand).directory(cwd.toFile()).start();
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
                    stderr.join() + "Timed out after " + timeout.toSeconds() + " seconds: " + String.join(" ", actualCommand) + "\n"
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

    private static List<String> childCoverageCommand(final List<String> command) {
        final String agent = childCoverageAgentTemplate();
        final String directory = System.getProperty("javan.childJacocoDir", "");
        if (agent.isBlank() || directory.isBlank() || !isJavanMainJavaCommand(command)) {
            return command;
        }
        try {
            final Path coverageDirectory = Path.of(directory);
            Files.createDirectories(coverageDirectory);
            final Path exec = coverageDirectory.resolve("child-" + java.util.UUID.randomUUID() + ".exec");
            final List<String> result = new java.util.ArrayList<>();
            result.add(command.getFirst());
            result.add(childCoverageAgent(agent, exec));
            result.addAll(command.subList(1, command.size()));
            return List.copyOf(result);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String childCoverageAgentTemplate() {
        final String configured = System.getProperty("javan.childJacocoArgLine", "");
        if (!configured.isBlank()) {
            return configured;
        }
        for (final String argument : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("org.jacoco.agent")) {
                return argument;
            }
        }
        return "";
    }

    private static boolean isJavanMainJavaCommand(final List<String> command) {
        if (command.isEmpty()) {
            return false;
        }
        final Path executable = Path.of(command.getFirst()).getFileName();
        if (executable == null) {
            return false;
        }
        final String name = executable.toString();
        if (!"java".equals(name) && !"java.exe".equals(name)) {
            return false;
        }
        for (int index = 1; index < command.size(); index++) {
            final String argument = command.get(index);
            if ("-cp".equals(argument) || "-classpath".equals(argument) || "--class-path".equals(argument)) {
                index++;
            } else if (!argument.startsWith("-")) {
                return "javan.Main".equals(argument);
            }
        }
        return false;
    }

    private static String childCoverageAgent(final String agent, final Path exec) {
        final String key = "destfile=";
        final int start = agent.indexOf(key);
        if (start < 0) {
            return agent;
        }
        final int valueStart = start + key.length();
        final int valueEnd = agent.indexOf(',', valueStart);
        if (valueEnd < 0) {
            return agent.substring(0, valueStart) + exec;
        }
        return agent.substring(0, valueStart) + exec + agent.substring(valueEnd);
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
