package javan;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

abstract class CliIntegrationSupport {
    @TempDir
    protected Path tempDir;

    protected final Path project(final String name) throws Exception {
        final Path project = tempDir.resolve(name);
        Files.createDirectories(project.resolve("src/main/java"));
        return project;
    }

    protected final void assertBuildRejectsDisabledRuntimeModule(
        final String projectName,
        final String module,
        final String source
    ) throws Exception {
        final Path project = project(projectName);
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            disabled = ["%s"]
            """.formatted(module));
        writeJava(project, "com.acme.Main", source);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN060]", module);
        assertThat(project.resolve(".javan/generated")).doesNotExist();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"disabledReachableRuntimeModules\": [\"" + module + "\"]",
            "\"status\": \"fail\""
        );
    }

    protected static void writeJava(final Path project, final String className, final String source) throws Exception {
        final Path file = project.resolve("src/main/java").resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    protected static Path writeResource(final Path project, final String name, final String source) throws Exception {
        final Path file = project.resolve("src/main/resources").resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    protected static String runJvmWithResources(final Path project, final String mainClass) throws Exception {
        final Path output = project.resolve("jvm-classes");
        final Path sourceRoot = project.resolve("src/main/java");
        final List<String> compile = new ArrayList<>(List.of(CliTestHarness.currentJavacCommand(), "-d", output.toString()));
        Files.createDirectories(output);
        try (var sources = Files.walk(sourceRoot)) {
            sources.filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().endsWith(".java"))
                .map(Path::toString)
                .forEach(compile::add);
        }
        assertThat(process(project, compile).exitCode()).isZero();
        final Path resources = project.resolve("src/main/resources");
        if (Files.isDirectory(resources)) {
            try (var files = Files.walk(resources)) {
                for (final Path file : files.toList()) {
                    final Path target = output.resolve(resources.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        final ProcessResult run = process(project, List.of(
            CliTestHarness.currentJavaCommand(), "-cp", output.toString(), mainClass
        ));
        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        return run.stdout();
    }

    protected static Path writeC(final Path project, final String filename, final String source) throws Exception {
        final Path file = project.resolve(filename);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    protected final Path copyResourceProject(final String resourceName, final String projectName) throws Exception {
        final Path source = Path.of("src/test/resources/projects").resolve(resourceName);
        return copyProjectDirectory(source, projectName);
    }

    protected final Path copyProjectDirectory(final Path source, final String projectName) throws Exception {
        final Path target = tempDir.resolve(projectName);
        try (var paths = Files.walk(source)) {
            for (final Path path : paths.toList()) {
                final Path relative = source.relativize(path);
                final Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
        return target;
    }

    protected static Path pinnedMavenArtifact(final String groupId, final String artifactId, final String version) {
        final String configuredRepository = System.getProperty("maven.repo.local",
            System.getenv().getOrDefault("JAVAN_MAVEN_REPO",
                System.getenv().getOrDefault("MAVEN_REPO_LOCAL",
                    Path.of(System.getProperty("user.home")).resolve(".m2/repository").toString())));
        return Path.of(configuredRepository)
            .resolve(groupId.replace('.', '/'))
            .resolve(artifactId)
            .resolve(version)
            .resolve(artifactId + "-" + version + ".jar");
    }

    protected static String pathForMod(final Path project, final Path dependency) {
        return project.toAbsolutePath().normalize().relativize(dependency.toAbsolutePath().normalize()).toString();
    }

    protected static void installMavenCoordinate(
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

    protected final Path acceptanceWrapper() throws Exception {
        final Path wrapper = tempDir.resolve("javan-acceptance");
        writeExecutableScript(wrapper, """
            #!/bin/sh
            exec "%s" -cp "%s" javan.Main "$@"
            """.formatted(CliTestHarness.currentJavaCommand(), Path.of("target/classes").toAbsolutePath().normalize()));
        return wrapper;
    }

    protected static void writeExecutableScript(final Path script, final String source) throws Exception {
        Files.writeString(script, source.stripIndent(), StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
    }

    protected static String sharedLibraryName(final String name) {
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return name + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + name + ".dylib";
        }
        return "lib" + name + ".so";
    }

    protected static boolean commandAvailable(final String command) {
        return CliTestHarness.commandAvailable(command);
    }

    protected static String mainClass(final String value) {
        return """
            package com.acme;

            public final class %s {
                private %s() {
                }

                public static void main(final String[] args) {
                    System.out.println("%s");
                }
            }
            """.formatted("main".equals(value) ? "Main" : "Tool", "main".equals(value) ? "Main" : "Tool", value);
    }

    protected static CliRun run(final Path cwd, final String... args) {
        return runWithTimeout(cwd, defaultCliTimeout(), args);
    }

    protected static CliRun runSlow(final Path cwd, final String... args) {
        return runWithTimeout(cwd, Duration.ofSeconds(90), args);
    }

    protected static CliRun requireBuildSuccess(final CliRun run) {
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }
        return run;
    }

    protected static CliRun runWithTimeout(final Path cwd, final Duration timeout, final String... args) {
        final CliTestHarness.CliResult result = CliTestHarness.run(cwd, timeout, args);
        return new CliRun(
            result.exitCode(),
            result.stdout(),
            result.stderr()
        );
    }

    protected static ProcessResult process(final Path cwd, final List<String> command) {
        return process(cwd, command, defaultProcessTimeout());
    }

    protected static ProcessResult processSlow(final Path cwd, final List<String> command) {
        return process(cwd, command, Duration.ofSeconds(60));
    }

    protected static Duration defaultCliTimeout() {
        return Duration.ofSeconds(45);
    }

    protected static Duration defaultProcessTimeout() {
        return Duration.ofSeconds(20);
    }

    protected static ProcessResult process(final Path cwd, final List<String> command, final Duration timeout) {
        return process(cwd, command, timeout, Map.of());
    }

    protected static ProcessResult process(final Path cwd, final List<String> command, final Duration timeout, final Map<String, String> environment) {
        final CliTestHarness.ProcessResult result = CliTestHarness.process(cwd, command, timeout, environment);
        return new ProcessResult(
            result.exitCode(),
            result.stdout(),
            result.stderr()
        );
    }

    protected static int freeTcpPort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    protected static void connectLoopback(final int port) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                socket.getOutputStream().flush();
                return;
            } catch (final IOException exception) {
                try {
                    Thread.sleep(25);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for loopback socket on port " + port, interrupted);
                }
            }
        }
        throw new IllegalStateException("Timed out waiting for loopback socket on port " + port);
    }

    protected static void connectLoopbackIpv6(final int port) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try (java.net.Socket socket = new java.net.Socket("::1", port)) {
                socket.getOutputStream().flush();
                return;
            } catch (final IOException exception) {
                try {
                    Thread.sleep(25);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for IPv6 loopback socket on port " + port, interrupted);
                }
            }
        }
        throw new IllegalStateException("Timed out waiting for IPv6 loopback socket on port " + port);
    }

    protected static void writeLoopbackBytes(final int port, final byte[] bytes) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
                socket.getOutputStream().write(bytes);
                socket.getOutputStream().flush();
                return;
            } catch (final IOException exception) {
                try {
                    Thread.sleep(25);
                } catch (final InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while writing to loopback socket on port " + port, interrupted);
                }
            }
        }
        throw new IllegalStateException("Timed out writing to loopback socket on port " + port);
    }

    protected static String readStream(final InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    protected static String runJvm(final Path project, final String mainClass) {
        return runJvm(project, mainClass, List.of());
    }

    protected static String runJvm(final Path project, final String mainClass, final List<Path> classpathEntries) {
        final Path classes = project.resolve("jvm-classes");
        final Path sourceRoot = project.resolve("src/main/java");
        final List<String> compile = new java.util.ArrayList<>(List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString()));
        if (!classpathEntries.isEmpty()) {
            compile.add("-classpath");
            compile.add(String.join(java.io.File.pathSeparator, classpathEntries.stream().map(Path::toString).toList()));
        }
        try {
            Files.createDirectories(classes);
            try (java.util.stream.Stream<Path> sources = Files.walk(sourceRoot)) {
                sources
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .map(Path::toString)
                    .forEach(compile::add);
            }
        } catch (final Exception exception) {
            throw new IllegalStateException("Unable to prepare JVM run", exception);
        }
        final ProcessResult compilation = process(project, compile);
        assertThat(compilation.exitCode()).as(compilation.stderr()).isZero();
        final List<Path> runtimeClasspath = new java.util.ArrayList<>();
        runtimeClasspath.add(classes);
        runtimeClasspath.addAll(classpathEntries);
        final ProcessResult run = process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            String.join(java.io.File.pathSeparator, runtimeClasspath.stream().map(Path::toString).toList()),
            mainClass
        ));
        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(run.stderr()).isEmpty();
        return run.stdout();
    }

    protected final Path dependencyJar(final String name, final String className, final String source) throws Exception {
        return dependencyJar(name, Map.of(className, source));
    }

    protected final Path dependencyJar(final String name, final Map<String, String> sources) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path jar = root.resolve(name + ".jar");
        Files.createDirectories(classes);
        final java.util.ArrayList<String> javac = new java.util.ArrayList<>(List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString()));
        for (final Map.Entry<String, String> entry : sources.entrySet()) {
            final Path sourceFile = sourceRoot.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, entry.getValue(), StandardCharsets.UTF_8);
            javac.add(sourceFile.toString());
        }
        assertThat(process(root, javac).exitCode()).isZero();
        assertThat(process(root, List.of(CliTestHarness.currentJarCommand(), "--create", "--file", jar.toString(), "-C", classes.toString(), ".")).exitCode()).isZero();
        return jar;
    }

    protected final Path addJarResource(
        final Path jar,
        final String path,
        final String content
    ) throws Exception {
        final Path resources = tempDir.resolve(jar.getFileName().toString() + "-resources");
        final Path resource = resources.resolve(path);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, content, StandardCharsets.UTF_8);
        assertThat(process(tempDir, List.of(
            CliTestHarness.currentJarCommand(),
            "--update",
            "--file",
            jar.toString(),
            "-C",
            resources.toString(),
            path
        )).exitCode()).isZero();
        return jar;
    }

    protected final Path dependencyJarWithMavenLicense(
        final String name,
        final String className,
        final String source,
        final String groupId,
        final String artifactId,
        final String version,
        final String license
    ) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path metadata = root.resolve("metadata/META-INF/maven")
            .resolve(groupId.replace('.', '/'))
            .resolve(artifactId);
        final Path jar = root.resolve(name + ".jar");
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.createDirectories(metadata);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        Files.writeString(metadata.resolve("pom.properties"), """
            groupId=%s
            artifactId=%s
            version=%s
            """.formatted(groupId, artifactId, version), StandardCharsets.UTF_8);
        Files.writeString(metadata.resolve("pom.xml"), """
            <project>
              <licenses>
                <license>
                  <name>%s</name>
                  <url>https://example.invalid/license</url>
                </license>
              </licenses>
            </project>
            """.formatted(license), StandardCharsets.UTF_8);
        assertThat(process(root, List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        assertThat(process(root, List.of(
            CliTestHarness.currentJarCommand(),
            "--create",
            "--file",
            jar.toString(),
            "-C",
            classes.toString(),
            ".",
            "-C",
            root.resolve("metadata").toString(),
            "."
        )).exitCode()).isZero();
        return jar;
    }

    protected final Path dependencyClasses(final String name, final String className, final String source) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(process(root, List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        return classes;
    }

    protected static String repeatedReflectionSource(final int count) {
        final StringBuilder calls = new StringBuilder();
        for (int index = 0; index < count; index++) {
            calls.append("        Class.forName(\"com.acme.Plugin")
                .append(index)
                .append("\", true, ClassLoader.getSystemClassLoader());\n");
        }
        return """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void load() throws ClassNotFoundException {
            %s    }
            }
            """.formatted(calls);
    }

    protected record CliRun(int exitCode, String stdout, String stderr) {
    }

    protected record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
