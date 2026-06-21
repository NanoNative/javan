package javan.reporting;

import javan.analysis.CallGraph;
import javan.analysis.EntryPoint;
import javan.classfile.ClassFile;
import javan.classfile.ClassFileScanner;
import javan.detect.BuildTool;
import javan.detect.InputKind;
import javan.detect.ProjectLayout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class DependencyReportsTest {
    @TempDir
    private Path tempDir;

    @Test
    void writeReportsMissingClasspathJar() throws Exception {
        final Path project = project("missing-jar");
        final Path missing = project.resolve("libs/missing.jar");
        final ProjectLayout layout = layout(project, List.of(missing));

        new DependencyReports().write(layout, Map.of(), emptyCallGraph());

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        final String licenses = Files.readString(project.resolve(".javan/reports/licenses.json"));
        assertThat(dependencies).contains(
            "\"dependencyCount\": 1",
            "\"presentDependencies\": 0",
            "\"missingDependencies\": 1",
            "\"source\": \"classpath\"",
            "\"kind\": \"missing-jar\"",
            "\"status\": \"missing\"",
            "\"classCount\": 0",
            "\"reachableClassCount\": 0"
        );
        assertThat(licenses).contains(
            "\"unknownLicenses\": 1",
            "\"source\": \"none\"",
            "\"policy\": \"warning\""
        );
    }

    @Test
    void writeReportsReachableClassesDirectoryDependency() throws Exception {
        final Path project = project("classes-directory");
        final Path dependency = compileDependency(project, "classes", "dep.DirLib", """
            package dep;

            public final class DirLib {
                private DirLib() {
                }

                public static int value() {
                    return 5;
                }
            }
            """);
        Files.writeString(dependency.resolve("LICENSE"), "custom\n", StandardCharsets.UTF_8);
        final ProjectLayout layout = layout(project, List.of(dependency));
        final Map<String, ClassFile> classes = new ClassFileScanner().scan(layout);

        new DependencyReports().write(layout, classes, callGraph("dep/DirLib", "value", "()I"));

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        final String licenses = Files.readString(project.resolve(".javan/reports/licenses.json"));
        assertThat(dependencies).contains(
            "\"kind\": \"classes-directory\"",
            "\"used\": true",
            "\"classCount\": 1",
            "\"reachableClassCount\": 1",
            "\"reachableClasses\": [\"dep/DirLib\"]"
        );
        assertThat(licenses).contains(
            "\"source\": \"file\"",
            "\"path\": \"LICENSE\""
        );
    }

    @Test
    void writeReportsJarLicenseFileWhenPomLicenseIsAbsent() throws Exception {
        final Path project = project("jar-license-file");
        final Path classes = compileDependency(project, "jar-classes", "dep.JarLib", """
            package dep;

            public final class JarLib {
                private JarLib() {
                }

                public static int value() {
                    return 9;
                }
            }
            """);
        final Path metadata = project.resolve("metadata");
        Files.createDirectories(metadata);
        Files.writeString(metadata.resolve("LICENSE"), "custom\n", StandardCharsets.UTF_8);
        final Path jar = project.resolve("lib.jar");
        assertThat(process(project, List.of(
            "jar",
            "--create",
            "--file",
            jar.toString(),
            "-C",
            classes.toString(),
            ".",
            "-C",
            metadata.toString(),
            "."
        )).exitCode()).isZero();
        final ProjectLayout layout = layout(project, List.of(jar));
        final Map<String, ClassFile> scanned = new ClassFileScanner().scan(layout);

        new DependencyReports().write(layout, scanned, emptyCallGraph());

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        final String licenses = Files.readString(project.resolve(".javan/reports/licenses.json"));
        assertThat(dependencies).contains(
            "\"kind\": \"jar\"",
            "\"used\": false",
            "\"classCount\": 1",
            "\"classes\": [\"dep/JarLib\"]"
        );
        assertThat(licenses).contains(
            "\"id\": \"unknown\"",
            "\"source\": \"file\"",
            "\"path\": \"LICENSE\""
        );
    }

    @Test
    void writeReportsExistingJarWithoutScannerCacheAsPresentUnknown() throws Exception {
        final Path project = project("jar-cache-missing");
        final Path classes = compileDependency(project, "cache-missing-classes", "dep.CacheMiss", """
            package dep;

            public final class CacheMiss {
                private CacheMiss() {
                }
            }
            """);
        final Path jar = project.resolve("cached-later.jar");
        assertThat(process(project, List.of("jar", "--create", "--file", jar.toString(), "-C", classes.toString(), ".")).exitCode())
            .isZero();
        final ProjectLayout layout = layout(project, List.of(jar));

        new DependencyReports().write(layout, Map.of(), emptyCallGraph());

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        final String licenses = Files.readString(project.resolve(".javan/reports/licenses.json"));
        assertThat(dependencies).contains(
            "\"presentDependencies\": 1",
            "\"missingDependencies\": 0",
            "\"kind\": \"jar\"",
            "\"classCount\": 0"
        );
        assertThat(licenses).contains(
            "\"unknownLicenses\": 1",
            "\"source\": \"none\""
        );
    }

    @Test
    void writeReportsPlainClasspathFileAsPresentFile() throws Exception {
        final Path project = project("plain-file");
        final Path file = project.resolve("notes.txt");
        Files.writeString(file, "not class output\n", StandardCharsets.UTF_8);
        final ProjectLayout layout = layout(project, List.of(file));

        new DependencyReports().write(layout, Map.of(), emptyCallGraph());

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        assertThat(dependencies).contains(
            "\"presentDependencies\": 1",
            "\"kind\": \"file\"",
            "\"classCount\": 0"
        );
    }

    @Test
    void writeReportsMissingNonJarClasspathEntryAsMissing() throws Exception {
        final Path project = project("missing-file");
        final Path missing = project.resolve("missing-classes");
        final ProjectLayout layout = layout(project, List.of(missing));

        new DependencyReports().write(layout, Map.of(), emptyCallGraph());

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        assertThat(dependencies).contains(
            "\"missingDependencies\": 1",
            "\"kind\": \"missing\""
        );
    }

    @Test
    void writeIgnoresReachableClassWhenClassIsUnknown() throws Exception {
        final Path project = project("unknown-reachable");
        final Path dependency = compileDependency(project, "known-classes", "dep.Known", """
            package dep;

            public final class Known {
                private Known() {
                }
            }
            """);
        final ProjectLayout layout = layout(project, List.of(dependency));
        final Map<String, ClassFile> classes = new ClassFileScanner().scan(layout);

        new DependencyReports().write(layout, classes, callGraph("dep/Missing", "value", "()I"));

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        assertThat(dependencies).contains(
            "\"usedDependencies\": 0",
            "\"reachableDependencyClasses\": 0"
        );
    }

    @Test
    void writeFallsBackToLicenseFileWhenPomHasNoLicenseName() throws Exception {
        final Path project = project("pom-without-license-name");
        final Path classes = compileDependency(project, "fallback-classes", "dep.Fallback", """
            package dep;

            public final class Fallback {
                private Fallback() {
                }
            }
            """);
        final Path metadata = project.resolve("metadata/META-INF/maven/com/acme/fallback");
        Files.createDirectories(metadata);
        Files.writeString(metadata.resolve("pom.xml"), "<project><licenses><license></license></licenses></project>\n", StandardCharsets.UTF_8);
        Files.writeString(project.resolve("metadata/NOTICE"), "notice\n", StandardCharsets.UTF_8);
        final Path jar = project.resolve("fallback.jar");
        assertThat(process(project, List.of(
            "jar",
            "--create",
            "--file",
            jar.toString(),
            "-C",
            classes.toString(),
            ".",
            "-C",
            project.resolve("metadata").toString(),
            "."
        )).exitCode()).isZero();
        final ProjectLayout layout = layout(project, List.of(jar));
        final Map<String, ClassFile> scanned = new ClassFileScanner().scan(layout);

        new DependencyReports().write(layout, scanned, emptyCallGraph());

        final String licenses = Files.readString(project.resolve(".javan/reports/licenses.json"));
        assertThat(licenses).contains(
            "\"id\": \"unknown\"",
            "\"source\": \"file\"",
            "\"path\": \"NOTICE\""
        );
    }

    @Test
    void writeKeepsCoordinateEmptyWhenPomPropertiesAreIncomplete() throws Exception {
        final Path project = project("incomplete-coordinate");
        final Path classes = compileDependency(project, "incomplete-classes", "dep.Incomplete", """
            package dep;

            public final class Incomplete {
                private Incomplete() {
                }
            }
            """);
        final Path metadata = project.resolve("metadata/META-INF/maven/com/acme/incomplete");
        Files.createDirectories(metadata);
        Files.writeString(metadata.resolve("pom.properties"), """
            groupId=com.acme
            artifactId=incomplete
            """, StandardCharsets.UTF_8);
        final Path jar = project.resolve("incomplete.jar");
        assertThat(process(project, List.of(
            "jar",
            "--create",
            "--file",
            jar.toString(),
            "-C",
            classes.toString(),
            ".",
            "-C",
            project.resolve("metadata").toString(),
            "."
        )).exitCode()).isZero();
        final ProjectLayout layout = layout(project, List.of(jar));
        final Map<String, ClassFile> scanned = new ClassFileScanner().scan(layout);

        new DependencyReports().write(layout, scanned, emptyCallGraph());

        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        assertThat(dependencies).contains("\"coordinate\": \"\"");
    }

    private Path project(final String name) throws IOException {
        final Path project = tempDir.resolve(name);
        Files.createDirectories(project);
        return project;
    }

    private static ProjectLayout layout(final Path project, final List<Path> classpathEntries) {
        return new ProjectLayout(
            project,
            project,
            InputKind.PROJECT_DIRECTORY,
            BuildTool.JAVAC,
            List.of(),
            List.of(),
            List.of(),
            classpathEntries,
            project.resolve(".javan"),
            "app",
            List.of()
        );
    }

    private static Path compileDependency(final Path project, final String name, final String className, final String source) throws IOException {
        final Path sourceRoot = project.resolve(name + "-src");
        final Path classes = project.resolve(name);
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(process(project, List.of("javac", "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        return classes;
    }

    private static CallGraph emptyCallGraph() {
        return new CallGraph(new EntryPoint("", "", ""), List.of(), List.of());
    }

    private static CallGraph callGraph(final String className, final String methodName, final String descriptor) {
        final EntryPoint entry = new EntryPoint(className, methodName, descriptor);
        return new CallGraph(entry, List.of(entry), List.of());
    }

    private static ProcessResult process(final Path cwd, final List<String> command) {
        try {
            final Process process = new ProcessBuilder(command).directory(cwd.toFile()).start();
            final CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            final CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            if (!process.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return new ProcessResult(124, stdout.join(), stderr.join());
            }
            return new ProcessResult(process.exitValue(), stdout.join(), stderr.join());
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running process", exception);
        }
    }

    private static String read(final java.io.InputStream input) {
        try {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
