package javan;

import javan.testing.TestSuite.PackagingTest;
import javan.testing.TestSuite.WindowsCompatibilityProof;

import javan.build.BuildKind;
import javan.cli.Cli;
import javan.cli.Version;
import javan.reporting.RuntimeFootprintReports;
import javan.util.Files2;
import javan.util.Json;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@PackagingTest
final class CliPackagingIntegrationTest extends CliIntegrationSupport {
    private Path primitiveLiteralBootstrap;

    @Test
    void testDelegatesToMavenWrapperAfterBuildingClasses() throws Exception {
        final Path project = project("maven-test");
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>maven-test</artifactId>
              <version>1.0.0</version>
            </project>
            """);
        writeExecutableScript(project.resolve("mvnw"), """
            #!/bin/sh
            printf '%s\\n' "$*" >> invocations.txt
            if [ "$1" = "test" ]; then
              echo maven-test-ok
            fi
            exit 0
            """);

        final CliRun run = run(tempDir, "test", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Running tests:", "sh ./mvnw test", "maven-test-ok");
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve("invocations.txt"))).contains(
            "-q -DskipTests compile",
            "dependency:build-classpath",
            "test"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"buildTool\": \"MAVEN\"");
    }

    @Test
    void testDelegatesToGradleWrapperAfterBuildingClasses() throws Exception {
        final Path project = project("gradle-test");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");
        writeExecutableScript(project.resolve("gradlew"), """
            #!/bin/sh
            printf '%s\\n' "$*" >> invocations.txt
            if [ "$1" = "test" ]; then
              echo gradle-test-ok
            fi
            exit 0
            """);

        final CliRun run = run(tempDir, "test", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Running tests:", "./gradlew test", "gradle-test-ok");
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve("invocations.txt"))).contains(
            "classes",
            "javanRuntimeClasspath",
            "test"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"buildTool\": \"GRADLE\"");
    }

    @Test
    void testFailsClearlyForPlainJavaProject() throws Exception {
        final Path project = project("plain-test");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("plain");
                }
            }
            """);

        final CliRun run = run(tempDir, "test", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN900]", "No configured test runner for JAVAC projects");
        assertThat(Files.exists(project.resolve(".javan/classes/com/acme/Main.class"))).isTrue();
    }

    @Test
    void runStopsWhenStaticCheckFails() throws Exception {
        final Path project = project("run-no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("error[JAVAN020]");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void runForwardsNativeStderr() throws Exception {
        final Path project = project("run-stderr");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.err.println("native-err");
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("native-err");
        assertThat(run.stderr()).isEmpty();
    }

    @Test
    void buildCreatesNativeExecutable() throws Exception {
        final Path project = project("hello");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("Hello from javan");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final Path binary = project.resolve(".javan/bin/hello");
        assertThat(binary).exists();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("Hello from javan\n");
        final String runtime = Files.readString(project.resolve(".javan/reports/runtime.json"));
        assertThat(runtime).contains(
            "\"artifactKind\": \"app\"",
            "\"linkage\": \"dynamic-executable\"",
            "\"runtimePackaging\": \"monolithic-c-runtime\"",
            "\"allocator\": \"tracked-calloc-free-at-shutdown\"",
            "\"heapMetadata\": true",
            "\"heapAccounting\": true",
            "\"heapReclamation\": true",
            "\"heapReclamationScope\": \"generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage\"",
            "\"typeDescriptors\": true",
            "\"objectFieldDescriptors\": true",
            "\"frameRootInventory\": true",
            "\"managedHeap\": false",
            "\"gc\": \"partial-mark-sweep\"",
            "\"runtimeContainerTraversal\": \"precise-rooted-runtime-container-mark-sweep\"",
            "\"ownedBufferReferenceValidation\": true",
            "\"ownedBufferReferenceValidationScope\": \"list-map-stringbuilder-owned-backing-storage\"",
            "\"operandCallTemporaryRoots\": true",
            "\"allocationPathCollection\": true",
            "\"allocationPathCollectionModel\": \"allocator-gc-retry-before-out-of-memory\"",
            "\"allocationPathCollectionScope\": \"generated-objects-object-arrays-primitive-arrays-boxed-primitive-wrappers-runtime-strings-runtime-containers-and-owned-container-storage\"",
            "\"allocationFailureMode\": \"deterministic-native-panic\"",
            "\"statementSafePoints\": true",
            "\"returnValueRoots\": true",
            "\"protectedObjectReturns\": true",
            "\"staticRootInventory\": true",
            "\"localRootInventory\": true",
            "\"localRootLiveness\": true",
            "\"localRootLivenessModel\": \"cfg-safe-point-dead-root-clearing\"",
            "\"rootModel\": \"generated-static-local-parameter-expression-caller-owned-result-and-registered-platform-worker-root-inventory-no-conservative-heap-scan\"",
            "\"threadRoots\": true",
            "\"threadRootRegistry\": true",
            "\"threadRootScope\": \"parallel-host-thread-bootstrap-live-thread-root-registry-current-thread-root-membership-and-thread-target-field-traversal\"",
            "\"threadLifecycleInventory\": true",
            "\"threadLifecycleInventoryScope\": \"heap-thread-object-thread-root-registry-started-completed-active-non-current-target-current-root-and-completed-target-release-counters\"",
            "\"threads\": \"current-thread-interrupt-state-isalive-isvirtual-entry-interrupted-sleep-start-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-parallel-host-thread-bootstrap-join-same-method-catch-thread-construction-duplicate-start-rejection-current-join-rejection-and-runnable-target-no-virtual-scheduler\"",
            "\"sanitizers\": \"not-enabled\""
        );
        final String footprint = Files.readString(project.resolve(".javan/reports/runtime-footprint.json"));
        assertThat(footprint).contains(
            "\"artifactKind\": \"app\"",
            "\"hostTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"actualTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"name\": \"system-linked\"",
            "\"status\": \"verified-host\"",
            "\"name\": \"self-contained\"",
            "\"status\": \"not-implemented\"",
            "\"target\": \"linux-aarch64\"",
            "\"target\": \"macos-aarch64\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "{\"name\": \"runtime\", \"status\": \"present\"",
            "{\"name\": \"runtime-footprint\", \"status\": \"present\"",
            "\"artifactKind\": \"app\"",
            "\"actualTarget\": \"" + RuntimeFootprintReports.hostTarget() + "\"",
            "\"threadRoots\": \"true\"",
            "\"threadRootRegistry\": \"true\"",
            "\"threadRootScope\": \"parallel-host-thread-bootstrap-live-thread-root-registry-current-thread-root-membership-and-thread-target-field-traversal\"",
            "\"threadLifecycleInventory\": \"true\"",
            "\"threadLifecycleInventoryScope\": \"heap-thread-object-thread-root-registry-started-completed-active-non-current-target-current-root-and-completed-target-release-counters\"",
            "\"threads\": \"current-thread-interrupt-state-isalive-isvirtual-entry-interrupted-sleep-start-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-parallel-host-thread-bootstrap-join-same-method-catch-thread-construction-duplicate-start-rejection-current-join-rejection-and-runnable-target-no-virtual-scheduler\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/report.md"))).contains(
            "threadRoots: `true`",
            "threadRootRegistry: `true`",
            "threadRootScope: `parallel-host-thread-bootstrap-live-thread-root-registry-current-thread-root-membership-and-thread-target-field-traversal`",
            "threadLifecycleInventory: `true`",
            "threadLifecycleInventoryScope: `heap-thread-object-thread-root-registry-started-completed-active-non-current-target-current-root-and-completed-target-release-counters`",
            "threads: `current-thread-interrupt-state-isalive-isvirtual-entry-interrupted-sleep-start-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-parallel-host-thread-bootstrap-join-same-method-catch-thread-construction-duplicate-start-rejection-current-join-rejection-and-runnable-target-no-virtual-scheduler`"
        );
    }

    @BeforeAll
    void buildPrimitiveLiteralBootstrap(@TempDir final Path bootstrapTempDir) throws Exception {
        final Path root = Path.of("").toAbsolutePath().normalize();
        final Path classes = root.resolve("target/classes");
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        final int bootstrapTimeoutSeconds = os.contains("win") ? 240 : 120;
        assertThat(classes.resolve("javan/Main.class")).exists();

        final CliRun bootstrapBuild = runWithTimeout(
            bootstrapTempDir,
            Duration.ofSeconds(bootstrapTimeoutSeconds),
            "build",
            classes.toString(),
            "--main",
            "javan.Main",
            "--output",
            "javan-bootstrap-primitive-literals"
        );
        assertThat(bootstrapBuild.exitCode()).as(bootstrapBuild.stderr()).isZero();

        primitiveLiteralBootstrap = BuildKind.APP.artifactPath(
            root.resolve("target/.javan"), "javan-bootstrap-primitive-literals"
        );
        assertThat(primitiveLiteralBootstrap).isExecutable();
    }

    @Test
    void nativeBuiltJavanBuildsBooleanAndObjectProgram() throws Exception {
        final Path probeProject = tempDir.resolve("selfhost-plain-boolean");
        final Path probeClasses = probeProject.resolve("classes");
        Files.createDirectories(probeClasses);
        writeJava(probeProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static final class Box {
                    private final int value;

                    private Box(final int value) {
                        this.value = value;
                    }

                    private int value() {
                        return value;
                    }
                }

                private static boolean flag(final boolean value) {
                    if (value) {
                        return false;
                    }
                    return true;
                }

                public static void main(final String[] args) {
                    System.out.println(flag(false));
                    System.out.println(flag(true));
                    System.out.println(new Box(7).value());
                }
            }
            """);

        final ProcessResult javac = process(
            tempDir,
            List.of(
                CliTestHarness.currentJavacCommand(),
                "--release",
                "25",
                "-d",
                probeClasses.toString(),
                probeProject.resolve("src/main/java/com/acme/Main.java").toString()
            ),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                probeClasses.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-plain-boolean"
            ),
            Duration.ofSeconds(120)
        );
        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();
        assertThat(nativeBuild.stderr()).doesNotContain("debug[JAVAN040]");

        final Path probeBinary = probeProject.resolve(".javan/bin/selfhost-plain-boolean");
        assertThat(probeBinary).isExecutable();
        assertThat(process(tempDir, List.of(probeBinary.toString())).stdout())
            .as("native-built Javan boolean literal output")
            .isEqualTo("true\nfalse\n7\n");
    }

    @Test
    void nativeBuiltJavanBuildsNestedIntegerExpression() throws Exception {
        final Path probeProject = tempDir.resolve("selfhost-nested-integer-expression");
        final Path probeClasses = probeProject.resolve("classes");
        Files.createDirectories(probeClasses);
        writeJava(probeProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(7, -3));
                }

                private static int score(final int value, final int delta) {
                    return Math.max(value + Math.abs(delta), 0);
                }
            }
            """);

        final ProcessResult javac = process(
            tempDir,
            List.of(
                CliTestHarness.currentJavacCommand(),
                "--release",
                "25",
                "-d",
                probeClasses.toString(),
                probeProject.resolve("src/main/java/com/acme/Main.java").toString()
            ),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                probeClasses.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-nested-integer-expression"
            ),
            Duration.ofSeconds(120)
        );
        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();

        final Path probeBinary = probeProject.resolve(".javan/bin/selfhost-nested-integer-expression");
        assertThat(probeBinary).isExecutable();
        assertThat(process(tempDir, List.of(probeBinary.toString())).stdout()).isEqualTo("10\n");
    }

    @Test
    @WindowsCompatibilityProof
    void nativeBuiltJavanBuildsPlainSourceProject() throws Exception {
        final Path sourceProject = tempDir.resolve("selfhost-source-pr\u00f6ject");
        writeJava(sourceProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("source-project");
                }
            }
            """);

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                sourceProject.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-source-project"
            ),
            Duration.ofSeconds(120)
        );

        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();
        final Path probeBinary = BuildKind.APP.artifactPath(sourceProject.resolve(".javan"), "selfhost-source-project");
        assertThat(probeBinary).isExecutable();
        assertThat(process(tempDir, List.of(probeBinary.toString())).stdout()).isEqualTo("source-project\n");
    }

    @Test
    void nativeBuiltJavanWritesSha256DependencyLock() throws Exception {
        final Path sourceProject = tempDir.resolve("selfhost-sha256-lock");
        writeJava(sourceProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("sha256-lock");
                }
            }
            """);
        Files2.writeString(
            sourceProject.resolve("deps/tool.jar"),
            "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        );
        Files2.writeString(sourceProject.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require tool deps/tool.jar
            """);

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                sourceProject.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-sha256-lock"
            ),
            Duration.ofSeconds(120)
        );

        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();
        assertThat(Files.readString(sourceProject.resolve("javan.lock"))).contains(
            "\"checksumAlgorithm\": \"sha256\"",
            "\"checksum\": \"248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1\""
        );
    }

    @Test
    void nativeBuiltJavanBuildsWithLocalTransitiveCoordinate() throws Exception {
        final Path direct = dependencyJar("selfhost-direct", "dep.Direct", """
            package dep;

            public final class Direct {
                private Direct() {
                }
            }
            """);
        final Path transitive = dependencyJar("selfhost-transitive", "dep.Transitive", """
            package dep;

            public final class Transitive {
                private Transitive() {
                }

                public static int value() {
                    return 31;
                }
            }
            """);
        final Path home = tempDir.resolve("selfhost-home");
        final Path repository = home.resolve(".m2/repository");
        installMavenCoordinate(repository, "com.acme", "direct", "1.0.0", direct);
        installMavenCoordinate(repository, "com.acme", "transitive", "2.0.0", transitive);
        Files2.writeString(repository.resolve("com/acme/direct/1.0.0/direct-1.0.0.pom"), """
            <project><parent>
              <groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version>
            </parent><artifactId>direct</artifactId></project>
            """);
        Files2.writeString(repository.resolve("com/acme/parent/1.0.0/parent-1.0.0.pom"), """
            <project>
              <groupId>com.acme</groupId><artifactId>parent</artifactId><version>1.0.0</version>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>platform</artifactId><version>1.0.0</version>
                <type>pom</type><scope>import</scope>
              </dependency></dependencies></dependencyManagement>
              <dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>transitive</artifactId><scope>runtime</scope>
              </dependency></dependencies>
            </project>
            """);
        Files2.writeString(repository.resolve("com/acme/platform/1.0.0/platform-1.0.0.pom"), """
            <project>
              <groupId>com.acme</groupId><artifactId>platform</artifactId><version>1.0.0</version>
              <properties><transitive.version>2.0.0</transitive.version></properties>
              <dependencyManagement><dependencies><dependency>
                <groupId>com.acme</groupId><artifactId>transitive</artifactId>
                <version>${transitive.version}</version>
              </dependency></dependencies></dependencyManagement>
            </project>
            """);
        final Path sourceProject = tempDir.resolve("selfhost-transitive-coordinate");
        writeJava(sourceProject, "com.acme.Main", """
            package com.acme;

            import dep.Transitive;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Transitive.value());
                }
            }
            """);
        Files2.writeString(sourceProject.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main com.acme:direct:1.0.0
            """);

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                sourceProject.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-transitive-coordinate"
            ),
            Duration.ofSeconds(120),
            Map.of("HOME", home.toString())
        );

        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();
        assertThat(process(
            sourceProject,
            List.of(sourceProject.resolve(".javan/bin/selfhost-transitive-coordinate").toString())
        ).stdout()).isEqualTo("31\n");
        final String lock = Files.readString(sourceProject.resolve("javan.lock"));
        assertThat(lock).contains(
            "\"notation\": \"com.acme:transitive:2.0.0\"",
            "\"direct\": false",
            "\"requestedBy\": \"com.acme:direct:1.0.0\""
        );
        assertThat(home.resolve(".javan/cache/dependencies/com/acme/transitive/2.0.0/transitive-2.0.0.jar"))
            .isRegularFile();
        Files2.deleteRecursive(repository);
        Files2.deleteRecursive(sourceProject.resolve(".javan"));

        final ProcessResult offlineBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                sourceProject.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-transitive-coordinate-offline"
            ),
            Duration.ofSeconds(120),
            Map.of("HOME", home.toString())
        );

        assertThat(offlineBuild.exitCode()).as(offlineBuild.stderr()).isZero();
        assertThat(Files.readString(sourceProject.resolve("javan.lock"))).isEqualTo(lock);
        assertThat(process(
            sourceProject,
            List.of(sourceProject.resolve(".javan/bin/selfhost-transitive-coordinate-offline").toString())
        ).stdout()).isEqualTo("31\n");
    }

    @Test
    void nativeBuiltJavanWritesDependencyLicenseProvenance() throws Exception {
        final Path dependency = dependencyJarWithMavenLicense(
            "selfhost-provenance-tool",
            "dep.Tool",
            """
                package dep;

                public final class Tool {
                    private Tool() {
                    }
                }
                """,
            "com.acme",
            "tool",
            "1.0.0",
            "Apache License 2.0"
        );
        final Path sourceProject = tempDir.resolve("selfhost-dependency-provenance");
        writeJava(sourceProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("dependency-provenance");
                }
            }
            """);
        Files2.writeString(sourceProject.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require tool %s
            """.formatted(sourceProject.relativize(dependency)));

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                sourceProject.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-dependency-provenance"
            ),
            Duration.ofSeconds(120)
        );

        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();
        assertThat(Files.readString(sourceProject.resolve("javan.lock"))).contains(
            "\"licenseName\": \"Apache License 2.0\"",
            "\"licenseUrl\": \"https://example.invalid/license\"",
            "\"licenseSource\": \"pom.xml\"",
            "\"licensePath\": \"META-INF/maven/com/acme/tool/pom.xml\""
        );
    }

    @Test
    void nativeBuiltJavanReadsFiniteJdkMethodMetadata() throws Exception {
        final Path probeProject = tempDir.resolve("selfhost-method-metadata");
        final Path probeClasses = probeProject.resolve("classes");
        Files.createDirectories(probeClasses);
        writeJava(probeProject, "com.acme.Main", """
            package com.acme;

            import java.lang.reflect.Method;
            import java.util.ArrayList;
            import java.util.Collection;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Method declared = String.class.getDeclaredMethod("substring", int.class);
                    final Method inherited = ArrayList.class.getMethod("containsAll", Collection.class);
                    System.out.println(declared.getName());
                    System.out.println(declared.getParameterCount());
                    System.out.println(inherited.getDeclaringClass().getName());
                }
            }
            """);

        final ProcessResult javac = process(
            tempDir,
            List.of(
                CliTestHarness.currentJavacCommand(),
                "--release",
                "25",
                "-d",
                probeClasses.toString(),
                probeProject.resolve("src/main/java/com/acme/Main.java").toString()
            ),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();

        final ProcessResult nativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                probeClasses.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-method-metadata"
            ),
            Duration.ofSeconds(120)
        );
        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();

        final Path probeBinary = probeProject.resolve(".javan/bin/selfhost-method-metadata");
        assertThat(probeBinary).isExecutable();
        assertThat(process(tempDir, List.of(probeBinary.toString())).stdout())
            .isEqualTo("substring\n1\njava.util.AbstractCollection\n");
    }

    @Test
    void nativeBuiltJavanBuildsPlainLongLiteralProgram() throws Exception {
        final Path longProject = tempDir.resolve("selfhost-plain-long");
        final Path longClasses = longProject.resolve("classes");
        Files.createDirectories(longClasses);
        writeJava(longProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static long zero() {
                    return 0L;
                }

                public static void main(final String[] args) {
                    System.out.println(zero());
                    System.out.println(1L);
                }
            }
            """);

        final ProcessResult longJavac = process(
            tempDir,
            List.of(
                CliTestHarness.currentJavacCommand(),
                "--release",
                "25",
                "-d",
                longClasses.toString(),
                longProject.resolve("src/main/java/com/acme/Main.java").toString()
            ),
            Duration.ofSeconds(30)
        );
        assertThat(longJavac.exitCode()).as(longJavac.stderr()).isZero();

        final ProcessResult longNativeBuild = process(
            tempDir,
            List.of(
                primitiveLiteralBootstrap.toString(),
                "build",
                longClasses.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-plain-long"
            ),
            Duration.ofSeconds(120)
        );
        assertThat(longNativeBuild.exitCode()).as(longNativeBuild.stderr()).isZero();
        assertThat(longNativeBuild.stderr()).doesNotContain("debug[JAVAN040]");

        final Path longBinary = longProject.resolve(".javan/bin/selfhost-plain-long");
        assertThat(longBinary).isExecutable();
        assertThat(process(tempDir, List.of(longBinary.toString())).stdout())
            .as("native-built Javan long literal output")
            .isEqualTo("0\n1\n");
    }

    @Test
    void buildRejectsCrossTargetBeforeNativeLinking() throws Exception {
        final Path project = project("target-reject");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("target");
                }
            }
            """);
        final String crossTarget = RuntimeFootprintReports.hostTarget().startsWith("linux-") ? "macos-aarch64" : "linux-x64";

        final CliRun run = run(tempDir, "build", project.toString(), "--target", crossTarget);

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).contains("native target:     " + crossTarget, "toolchain:         cross-target");
        assertThat(run.stderr()).contains("error[JAVAN081]", "Cross-target native linking is not implemented");
        assertThat(Files.readString(project.resolve(".javan/reports/toolchain.json")))
            .contains("\"decision\": \"cross-target\"");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void checkReportsMissingCompilerAndBuildFailsBeforeCGeneration() throws Exception {
        final Path project = project("missing-native-compiler");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ready");
                }
            }
            """);
        final String java = CliTestHarness.currentJavaCommand();
        final String classes = Path.of("target/classes").toAbsolutePath().normalize().toString();
        final Map<String, String> noCompiler = Map.of("PATH", "", "CC", "");

        final ProcessResult check = process(
            project,
            List.of(java, "-cp", classes, "javan.Main", "check", project.toString()),
            defaultProcessTimeout(),
            noCompiler
        );
        final ProcessResult build = process(
            project,
            List.of(java, "-cp", classes, "javan.Main", "build", project.toString()),
            defaultProcessTimeout(),
            noCompiler
        );

        assertThat(check.exitCode()).isZero();
        assertThat(check.stdout()).contains("native target:", "c compiler:        missing");
        assertThat(Files.readString(project.resolve(".javan/reports/toolchain.json")))
            .contains("\"compilerStatus\": \"missing\"");
        assertThat(build.exitCode()).as(build.stderr()).isEqualTo(2);
        assertThat(build.stderr()).contains(
            "error[JAVAN080]: native toolchain unavailable",
            "Install a working cc, clang, or gcc"
        );
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void checkReportsIncompatibleCompilerAndBuildFailsBeforeCGeneration() throws Exception {
        final Path project = project("incompatible-native-compiler");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ready");
                }
            }
            """);
        final String java = CliTestHarness.currentJavaCommand();
        final String classes = Path.of("target/classes").toAbsolutePath().normalize().toString();
        final Map<String, String> incompatibleCompiler = Map.of("PATH", "", "CC", java);

        final ProcessResult check = process(
            project,
            List.of(java, "-cp", classes, "javan.Main", "check", project.toString()),
            defaultProcessTimeout(),
            incompatibleCompiler
        );
        final ProcessResult build = process(
            project,
            List.of(java, "-cp", classes, "javan.Main", "build", project.toString()),
            defaultProcessTimeout(),
            incompatibleCompiler
        );

        assertThat(check.exitCode()).isZero();
        assertThat(check.stdout()).contains("toolchain:         incompatible");
        assertThat(Files.readString(project.resolve(".javan/reports/toolchain.json")))
            .contains("\"decision\": \"incompatible\"", "\"compilerStatus\": \"available\"");
        assertThat(build.exitCode()).as(build.stderr()).isEqualTo(2);
        assertThat(build.stderr()).contains("error[JAVAN080]: native toolchain unavailable");
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void buildAllocatesApplicationClassNamedException() throws Exception {
        final Path project = project("named-exception-value");
        writeJava(project, "com.acme.CodeException", """
            package com.acme;

            public final class CodeException {
                private final int value;

                public CodeException(final int value) {
                    this.value = value;
                }

                public int value() {
                    return value;
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final CodeException value = new CodeException(41);
                    System.out.println(value.value() + 1);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final Path binary = project.resolve(".javan/bin/named-exception-value");
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("42\n");
    }

    @Test
    void staticLibraryExportedIntMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-add");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                static {
                    System.out.println("math-init");
                }

                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Math.add",
            "--bindings",
            "c,rust,go,python"
        );

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-add.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-add.h");
        final Path cAbiTest = project.resolve(".javan/dist/bindings/c/library-add_abi_test.c");
        assertThat(library).exists();
        assertThat(header).exists();
        assertThat(cAbiTest).exists();
        assertThat(project.resolve(".javan/dist/bindings/rust/lib.rs")).exists();
        assertThat(project.resolve(".javan/dist/bindings/go/library_add.go")).exists();
        assertThat(project.resolve(".javan/dist/bindings/python/library_add.py")).exists();
        assertThat(project.resolve(".javan/reports/library-build.json")).exists();
        assertThat(Files.readString(header)).contains(
            "#define JAVAN_ABI_VERSION 2",
            "#define JAVAN_ABI_V1_DIRECT_EXPORTS 1",
            "#define JAVAN_ABI_STRING_UTF8 1",
            "#define JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH 1",
            "#define JAVAN_ABI_RUNTIME_DIAGNOSTICS 1",
            "#define JAVAN_ABI_STRUCTURED_ERROR 1",
            "#define JAVAN_ABI_RESULT_WRAPPERS 1",
            "#define JAVAN_ABI_OBJECT_HANDLES 1",
            "typedef struct {",
            "int ok;",
            "char* message;",
            "} JavanResult;",
            "void javan_result_free(JavanResult* result);",
            "const char* javan_last_error(void);",
            "const char* javan_last_error_code(void);",
            "int javan_last_error_line(void);",
            "const char* javan_last_error_detail(void);",
            "JavanResult javan_try_com_acme_Math_add_int_int(int arg0, int arg1, int* out);",
            "void javan_clear_error(void);"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/library-build.json"))).contains(
            "\"abiVersion\": 2",
            "\"stringOwnership\": \"input-copied-gc-managed-utf8-output-javan-owned-free-with-javan_free\"",
            "\"byteArrayOwnership\": \"input-copied-gc-managed-output-javan-owned-data-free-with-javan_free\"",
            "\"objectHandleOwnership\": \"opaque-refcounted-gc-rooted-c-handle-release-with-javan_object_handle_release\"",
            "\"errorResultAbi\": \"abi-v2-c-owned-javanresult-try-wrappers-v1-direct-exports-compatible\"",
            "\"exceptionMapping\": \"caught-runtime-panic-to-last-error-supported-handler-slice\"",
            "\"threadRuntimeRules\": \"parallel-host-thread-bootstrap-current-thread-interrupt-isalive-sleep-start-join-runnable-target-plus-startvirtualthread-builderstart-builderunstarted-factory-executor-threadlocal-park-parknanos-parkuntil-unpark-and-isvirtual-no-virtual-scheduler\"",
            "\"generatedAbiTests\": \"c-header-compile-test\""
        );
        assertThat(project.resolve(".javan/reports/deduplication-plan.json")).exists();
        assertThat(project.resolve(".javan/reports/optimizations.json")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.json")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 0}",
                "\"unsupportedJdkCallCandidateCount\": 0"
            );
        final Path caller = writeC(project, "call_add.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-add.h"

            int main(void) {
                printf("%d\\n", javan_export_com_acme_Math_add_int_int(2, 5));
                int result_value = 0;
                JavanResult result = javan_try_com_acme_Math_add_int_int(2, 5, &result_value);
                printf("try:%d:%d\\n", result.ok, result_value);
                javan_result_free(&result);
                JavanResult null_out = javan_try_com_acme_Math_add_int_int(2, 5, 0);
                printf("try-null:%d:%s\\n", null_out.ok, null_out.code);
                javan_result_free(&null_out);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-add");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("""
            math-init
            7
            try:1:7
            try-null:0:JAVAN-ABI-NULL-OUT
            """);
        final Path abiTestObject = project.resolve("library-add-abi-test.o");
        assertThat(process(project, List.of("cc", "-c", cAbiTest.toString(), "-o", abiTestObject.toString())).exitCode()).isZero();
        if (commandAvailable("rustc")) {
            final Path rust = project.resolve(".javan/dist/bindings/rust/lib.rs");
            final Path rustOut = project.resolve("bindings.rlib");
            assertThat(processSlow(project, List.of("rustc", "--crate-type", "lib", rust.toString(), "-o", rustOut.toString())).exitCode()).isZero();
        }
        if (commandAvailable("go")) {
            final Path goDir = project.resolve(".javan/dist/bindings/go");
            assertThat(processSlow(project, List.of("sh", "-c", "cd '" + goDir + "' && GO111MODULE=off CGO_ENABLED=1 go test")).exitCode()).isZero();
        }
    }

    @Test
    void nativeLibraryExportPublishesUncaughtNegativeArraySizeException() throws Exception {
        final Path project = project("library-negative-array-size");
        writeJava(project, "com.acme.Failures", """
            package com.acme;

            public final class Failures {
                private Failures() {
                }

                public static int fail() {
                    return new int[-1].length;
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--library",
            "--format",
            "static",
            "--export",
            "com.acme.Failures.fail"
        );

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-negative-array-size.a");
        final Path caller = writeC(project, "call_failure.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-negative-array-size.h"

            int main(void) {
                int direct = javan_export_com_acme_Failures_fail_void();
                printf("direct:%d:%s:%s\\n", direct, javan_last_error_code(), javan_last_error_detail());
                javan_clear_error();
                int value = 42;
                JavanResult result = javan_try_com_acme_Failures_fail_void(&value);
                printf("try:%d:%s:%s:%d\\n", result.ok, result.code, result.detail, value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-failure");

        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("""
            direct:0:JAVAN-RUNTIME-PANIC:-1
            try:0:JAVAN-RUNTIME-PANIC:-1:0
            """);
    }

    @Test
    void nativeLibraryExportPublishesUncaughtArrayIndexOutOfBoundsException() throws Exception {
        final Path project = project("library-array-index");
        writeJava(project, "com.acme.Failures", """
            package com.acme;

            public final class Failures {
                private Failures() {
                }

                public static int fail() {
                    final int[] values = {17};
                    return values[negativeIndex()];
                }

                private static int negativeIndex() {
                    return -1;
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--library",
            "--format",
            "static",
            "--export",
            "com.acme.Failures.fail"
        );

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-array-index.a");
        final Path caller = writeC(project, "call_failure.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-array-index.h"

            int main(void) {
                int direct = javan_export_com_acme_Failures_fail_void();
                printf("direct:%d:%s:%s\\n", direct, javan_last_error_code(), javan_last_error_detail());
                javan_clear_error();
                int value = 42;
                JavanResult result = javan_try_com_acme_Failures_fail_void(&value);
                printf("try:%d:%s:%s:%d\\n", result.ok, result.code, result.detail, value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-failure");

        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("""
            direct:0:JAVAN-RUNTIME-PANIC:Index -1 out of bounds for length 1
            try:0:JAVAN-RUNTIME-PANIC:Index -1 out of bounds for length 1:0
            """);
    }

    @Test
    void nativeLibraryExportPublishesUncaughtArrayStoreException() throws Exception {
        final Path project = project("library-array-store");
        writeJava(project, "com.acme.Failures", """
            package com.acme;

            public final class Failures {
                private Failures() {
                }

                public static int fail() {
                    final Object[] values = new Integer[1];
                    values[0] = "bad";
                    return 1;
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--library",
            "--format",
            "static",
            "--export",
            "com.acme.Failures.fail"
        );

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-array-store.a");
        final Path caller = writeC(project, "call_failure.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-array-store.h"

            int main(void) {
                int direct = javan_export_com_acme_Failures_fail_void();
                printf("direct:%d:%s:%s\\n", direct, javan_last_error_code(), javan_last_error_detail());
                javan_clear_error();
                int value = 42;
                JavanResult result = javan_try_com_acme_Failures_fail_void(&value);
                printf("try:%d:%s:%s:%d\\n", result.ok, result.code, result.detail, value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-failure");

        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("""
            direct:0:JAVAN-RUNTIME-PANIC:java.lang.String
            try:0:JAVAN-RUNTIME-PANIC:java.lang.String:0
            """);
    }

    @Test
    void staticLibraryEmbedsDependencyResourceAndReadsItFromCExport() throws Exception {
        final Path dependency = addJarResource(
            dependencyJar("library-resource", "dep.Library", """
                package dep;

                public final class Library {
                    private Library() {
                    }
                }
                """),
            "dep/value.txt",
            "B"
        );
        final Path project = project("library-resource");
        writeJava(project, "com.acme.Resource", """
            package com.acme;

            import java.io.InputStream;

            public final class Resource {
                private Resource() {
                }

                public static int first() throws Exception {
                    final InputStream stream = ClassLoader.getSystemResourceAsStream("dep/value.txt");
                    final int value = stream.read();
                    stream.close();
                    return value;
                }
            }
            """);
        Files.writeString(project.resolve("javan.mod"), """
            module com.acme.library
            java 25
            require main %s
            """.formatted(pathForMod(project, dependency)), StandardCharsets.UTF_8);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Resource.first"
        );

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-resource.a");
        final Path caller = writeC(project, "call_resource.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-resource.h"

            int main(void) {
                printf("%d\\n", javan_export_com_acme_Resource_first_void());
                return 0;
            }
            """);
        final Path binary = project.resolve("call-resource");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("66\n");
        assertThat(project.resolve(".javan/dist/resources/dep/value.txt")).hasContent("B");
    }

    @Test
    void staticLibraryExportedMathFloorLinksAndRunsFromCWithoutMathLibrary() throws Exception {
        final Path project = project("library-floor");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static double floor(final double value) {
                    return java.lang.Math.floor(value);
                }
            }
            """);

        requireBuildSuccess(run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Math.floor"
        ));
        final Path library = project.resolve(".javan/dist/liblibrary-floor.a");
        final Path caller = writeC(project, "caller.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-floor.h"

            int main(void) {
                printf("%.1f\\n", javan_export_com_acme_Math_floor_double(-7.25));
                return 0;
            }
            """);
        final Path binary = project.resolve("library-floor-caller");

        final ProcessResult link = process(project, List.of(
            "cc",
            caller.toString(),
            library.toString(),
            "-o",
            binary.toString()
        ));

        if (link.exitCode() != 0) {
            throw new AssertionError(link.stderr());
        }
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("-8.0\n");
    }

    @Test
    void staticLibraryExportedDoubleToFloatInitializesAndRunsFromC() throws Exception {
        final Path project = project("library-double-to-float");
        writeJava(project, "com.acme.Narrow", """
            package com.acme;

            public final class Narrow {
                private Narrow() {
                }

                public static float narrow(final double value) {
                    return (float) value;
                }
            }
            """);

        requireBuildSuccess(run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Narrow.narrow"
        ));
        final Path caller = writeC(project, "caller.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-double-to-float.h"

            int main(void) {
                printf("%.1f\\n", javan_export_com_acme_Narrow_narrow_double(1.5));
                return 0;
            }
            """);
        final Path binary = project.resolve("library-double-to-float-caller");
        final ProcessResult link = process(project, List.of(
            "cc",
            caller.toString(),
            project.resolve(".javan/dist/liblibrary-double-to-float.a").toString(),
            "-o",
            binary.toString()
        ));
        if (link.exitCode() != 0) {
            throw new AssertionError(link.stderr());
        }

        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("1.5\n");
    }

    @Test
    void staticLibraryExportedMathCeilLinksAndRunsFromCWithoutMathLibrary() throws Exception {
        final Path project = project("generic-library");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static double ceil(final double value) {
                    return java.lang.Math.ceil(value);
                }
            }
            """);

        final CliRun build = run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Math.ceil"
        );
        final Path library = project.resolve(".javan/dist/libgeneric-library.a");
        final Path caller = writeC(project, "caller.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/generic-library.h"

            int main(void) {
                printf("%.1f\\n", javan_export_com_acme_Math_ceil_double(7.25));
                return 0;
            }
            """);
        final Path binary = project.resolve("generic-library-caller");
        final ProcessResult undefinedSymbols = build.exitCode() == 0
            ? process(project, List.of("nm", "-u", library.toString()))
            : new ProcessResult(-1, "", build.stderr());
        final ProcessResult link = build.exitCode() == 0
            ? process(project, List.of(
                "cc",
                caller.toString(),
                library.toString(),
                "-o",
                binary.toString()
            ))
            : new ProcessResult(-1, "", build.stderr());
        final ProcessResult execution = link.exitCode() == 0
            ? process(project, List.of(binary.toString()))
            : new ProcessResult(-1, "", link.stderr());

        assertThat(new StaticLibraryCeilProof(
            build.exitCode(),
            undefinedSymbols.exitCode(),
            hasExternalCeilSymbol(undefinedSymbols.stdout() + undefinedSymbols.stderr()),
            link.exitCode(),
            execution.exitCode(),
            execution.stdout()
        )).isEqualTo(new StaticLibraryCeilProof(0, 0, false, 0, 0, "8.0\n"));
    }

    private static boolean hasExternalCeilSymbol(final String symbols) {
        return symbols.lines()
            .map(String::strip)
            .anyMatch(symbol -> symbol.matches("(?:U\\s+)?_?ceil(?:@@?\\S+)?"));
    }

    private record StaticLibraryCeilProof(
        int buildExitCode,
        int undefinedSymbolsExitCode,
        boolean externalCeilSymbol,
        int linkExitCode,
        int executionExitCode,
        String executionOutput
    ) {
    }

    @Test
    void libraryAliasBuildsStaticSharedAndLanguageFolders() throws Exception {
        final Path project = project("library-friendly");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--library",
            "--export",
            "com.acme.Math.add",
            "--bindings",
            "c,rust,go,python"
        );

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/liblibrary-friendly.a")).exists();
        assertThat(project.resolve(".javan/dist/" + sharedLibraryName("library-friendly"))).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/c/library-friendly.h")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/c/liblibrary-friendly.a")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/rust/lib.rs")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/rust/liblibrary-friendly.a")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/library-friendly.h")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/library_friendly.go")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/liblibrary-friendly.a")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/python/library_friendly.py")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/python/liblibrary-friendly.a")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/library-build.json")))
            .contains("\"artifacts\"", "liblibrary-friendly.a", sharedLibraryName("library-friendly"));
        final Path caller = writeC(project, "call_friendly.c", """
            #include <stdio.h>
            #include ".javan/dist/lib/library-friendly/c/library-friendly.h"

            int main(void) {
                printf("%d\\n", javan_export_com_acme_Math_add_int_int(4, 6));
                return 0;
            }
            """);
        final Path binary = project.resolve("call-friendly");
        assertThat(process(project, List.of(
            "cc",
            caller.toString(),
            project.resolve(".javan/dist/lib/library-friendly/c/liblibrary-friendly.a").toString(),
            "-o",
            binary.toString()
        )).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("10\n");
    }

    @Test
    void libraryAliasStaticFormatBuildsOnlyStaticArtifact() throws Exception {
        final Path project = project("library-static-format");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--library", "--format", "static", "--release", "--export", "com.acme.Math.add");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/liblibrary-static-format.a")).exists();
        assertThat(project.resolve(".javan/dist/" + sharedLibraryName("library-static-format"))).doesNotExist();
        assertThat(project.resolve(".javan/dist/lib/library-static-format/c/liblibrary-static-format.a")).exists();
    }

    @Test
    void libraryAliasSharedFormatBuildsOnlySharedArtifact() throws Exception {
        final Path project = project("library-shared-format");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--library", "--format", "shared", "--release", "--export", "com.acme.Math.add");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/liblibrary-shared-format.a")).doesNotExist();
        assertThat(project.resolve(".javan/dist/" + sharedLibraryName("library-shared-format"))).exists();
        assertThat(project.resolve(".javan/dist/lib/library-shared-format/c/" + sharedLibraryName("library-shared-format"))).exists();
    }

    @Test
    void staticLibraryStringInputAndOutputOwnsReturnedString() throws Exception {
        final Path project = project("library-string");
        writeJava(project, "com.acme.Text", """
            package com.acme;

            public final class Text {
                private Text() {
                }

                public static String greet(final String name) {
                    return "Hi " + name;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Text.greet");

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-string.a");
        final Path caller = writeC(project, "call_string.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-string.h"

            int main(void) {
                char* value = javan_export_com_acme_Text_greet_string("Yuna");
                puts(value);
                javan_free(value);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-string");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("Hi Yuna\n");
    }

    @Test
    void staticLibraryByteArrayInputAndOutputUsesPointerLengthAbi() throws Exception {
        final Path project = project("library-bytes");
        writeJava(project, "com.acme.Bytes", """
            package com.acme;

            public final class Bytes {
                private Bytes() {
                }

                public static byte[] echo(final byte[] data) {
                    return data;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Bytes.echo");

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-bytes.a");
        final Path caller = writeC(project, "call_bytes.c", """
            #include <stdint.h>
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-bytes.h"

            int main(void) {
                int8_t data[3] = {1, 2, 3};
                JavanByteArray input = {data, 3};
                JavanByteArray output = javan_export_com_acme_Bytes_echo_bytes(input);
                printf("%d %d\\n", output.length, output.data[1]);
                javan_free(output.data);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-bytes");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("3 2\n");
    }

    @Test
    void staticLibraryObjectHandleExportBuildsAndRunsFromC() throws Exception {
        final Path project = project("library-object-handle");
        writeJava(project, "com.acme.Handles", """
            package com.acme;

            public final class Handles {
                private Handles() {
                }

                public static Object create() {
                    return new String("native-handle");
                }

                public static Object identity(final Object value) {
                    return value;
                }

                public static String describe(final Object value) {
                    return String.valueOf(value);
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--export",
            "com.acme.Handles.create():object",
            "--export",
            "com.acme.Handles.identity(object):object",
            "--export",
            "com.acme.Handles.describe(object):String"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-object-handle.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-object-handle.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "typedef struct javan_object_handle JavanObjectHandle;",
            "JavanObjectHandle* javan_export_com_acme_Handles_create_void(void);",
            "JavanObjectHandle* javan_export_com_acme_Handles_identity_object(JavanObjectHandle* arg0);",
            "JavanResult javan_try_com_acme_Handles_create_void(JavanObjectHandle** out);",
            "void javan_object_handle_retain(JavanObjectHandle* handle);",
            "void javan_object_handle_release(JavanObjectHandle* handle);"
        );
        final Path caller = writeC(project, "call_object_handle.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-object-handle.h"

            int main(void) {
                JavanObjectHandle* value = javan_export_com_acme_Handles_create_void();
                if (value == NULL) {
                    return 2;
                }
                char* text = javan_export_com_acme_Handles_describe_object(value);
                printf("%s\\n", text);
                javan_free(text);
                javan_object_handle_retain(value);
                JavanObjectHandle* identity = javan_export_com_acme_Handles_identity_object(value);
                if (identity == NULL) {
                    return 3;
                }
                char* second = javan_export_com_acme_Handles_describe_object(identity);
                printf("%s\\n", second);
                javan_free(second);
                javan_object_handle_release(identity);
                javan_object_handle_release(value);
                javan_object_handle_release(value);
                JavanObjectHandle* try_value = NULL;
                JavanResult result = javan_try_com_acme_Handles_create_void(&try_value);
                printf("try:%d:%d\\n", result.ok, try_value != NULL);
                javan_object_handle_release(try_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-object-handle");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout())
            .isEqualTo("native-handle\nnative-handle\ntry:1:1\n");
    }

    @Test
    void objectHandleExportsRejectNonCBindingsExplicitly() throws Exception {
        final Path project = project("library-object-bindings");
        writeJava(project, "com.acme.Handles", """
            package com.acme;

            public final class Handles {
                private Handles() {
                }

                public static Object create() {
                    return new String("native-handle");
                }
            }
            """);

        final CliRun run = run(
            tempDir,
            "build",
            project.toString(),
            "--kind",
            "staticlib",
            "--bindings",
            "rust",
            "--export",
            "com.acme.Handles.create():object"
        );

        assertThat(run.exitCode()).isNotZero();
        assertThat(run.stderr()).contains("Object-handle exports currently support C bindings only");
    }

    @Test
    void libraryExportsCanComeFromJavanToml() throws Exception {
        final Path project = project("library-config");
        Files.writeString(project.resolve("javan.toml"), """
            [exports]
            methods = ["com.acme.Math.add(int,int):int"]
            """);
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/liblibrary-config.a")).exists();
    }

    @Test
    void sharedLibraryBuildCreatesPlatformLibrary() throws Exception {
        final Path project = project("library-shared");
        writeJava(project, "com.acme.Math", """
            package com.acme;

            public final class Math {
                private Math() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "sharedlib", "--export", "com.acme.Math.add");

        assertThat(run.exitCode()).isZero();
        final Path library = project.resolve(".javan/dist/" + sharedLibraryName("library-shared"));
        assertThat(library).exists();
        if (commandAvailable("python3")) {
            final String script = """
                import ctypes
                lib = ctypes.CDLL(r'%s')
                add = lib.javan_export_com_acme_Math_add_int_int
                add.argtypes = [ctypes.c_int, ctypes.c_int]
                add.restype = ctypes.c_int
                print(add(3, 4))
                """.formatted(library);
            assertThat(process(project, List.of("python3", "-c", script)).stdout()).isEqualTo("7\n");
        }
    }

    @Test
    void shortObjectHandleExportBuilds() throws Exception {
        final Path project = project("library-bad-export");
        writeJava(project, "com.acme.Bad", """
            package com.acme;

            public final class Bad {
                private Bad() {
                }

                public static Object nope(final Object value) {
                    return value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Bad.nope");

        assertThat(run.exitCode()).as(run.stderr()).isZero();
    }
}
