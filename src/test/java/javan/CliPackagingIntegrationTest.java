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
    void nativeBuiltJavanTracksInheritedInterfaceArguments() throws Exception {
        final Path project = tempDir.resolve("selfhost-inherited-interface-query");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                interface Finder { boolean find(List<Object> values, Object query); }
                static class Base {
                    public final boolean find(List<Object> values, Object query) { return values.contains(query); }
                }
                static final class Child extends Base implements Finder { }
                static final class First {
                    public boolean equals(Object other) { return other instanceof First; }
                    public int hashCode() { return 1; }
                }
                static final class Second {
                    public boolean equals(Object other) { return other instanceof Second; }
                    public int hashCode() { return 2; }
                }
                public static void main(String[] args) {
                    List<Object> values = new ArrayList<>(); values.add(new First()); values.add(new Second());
                    System.out.println(new Base().find(values, new First()));
                    Finder finder = new Child();
                    System.out.println(finder.find(values, new Second()));
                }
            }
            """);
        final ProcessResult javac = process(tempDir, List.of(CliTestHarness.currentJavacCommand(), "--release", "25",
            "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir, List.of(primitiveLiteralBootstrap.toString(), "build",
            classes.toString(), "--main", "com.acme.Main", "--output", "selfhost-inherited-interface-query"),
            Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-inherited-interface-query").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("true\ntrue\n");
    }

    @Test
    void nativeBuiltJavanNarrowsCollectionQueryAfterStringCall() throws Exception {
        final Path project = tempDir.resolve("selfhost-collection-query");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            import java.nio.file.Path;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    public boolean equals(Object other) { return other instanceof Key; }
                    public int hashCode() { return 1; }
                }
                static boolean find(List<Object> values, String input) {
                    return values.contains(input.substring(1));
                }
                static boolean findObject(List<Object> values, Object input) {
                    return values.contains(input);
                }
                public static void main(String[] args) {
                    new Unsupported(Optional.empty());
                    List<Object> values = new ArrayList<>();
                    values.add("key");
                    System.out.println(find(values, "_key"));
                    values.add(new Key());
                    System.out.println(findObject(values, new Key()));
                    Path path = Path.of("item");
                    values.add(path);
                    System.out.println(findObject(values, path.normalize()));
                }
            }
            """);
        final ProcessResult javac = process(tempDir, List.of(CliTestHarness.currentJavacCommand(), "--release", "25",
            "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir, List.of(primitiveLiteralBootstrap.toString(), "build",
            classes.toString(), "--main", "com.acme.Main", "--output", "selfhost-collection-query"),
            Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(tempDir, List.of(project.resolve(".javan/bin/selfhost-collection-query").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("true\ntrue\ntrue\n");
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
    void nativeBuiltJavanBuildsClassLookupInsideCatch() throws Exception {
        final Path project = tempDir.resolve("selfhost-class-lookup-catch");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    try {
                        System.out.println(Class.forName("java.lang.String").getName());
                    } catch (ClassNotFoundException expected) {
                        System.out.println("missing");
                    }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-class-lookup-catch"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-class-lookup-catch");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("java.lang.String\n");
    }

    @Test
    void nativeBuiltJavanPreservesMutableStaticReadOrder() throws Exception {
        final Path project = tempDir.resolve("selfhost-static-read-order");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.security.SecureRandom;
            public final class Main {
                static int value = 10;
                static SecureRandom random = new SecureRandom();
                static int replace() { value = 20; return 2; }
                static int clear() { random = null; return 1; }
                public static void main(final String[] args) {
                    System.out.println(value + replace());
                    System.out.println(random.nextInt(clear()));
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-static-read-order"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-static-read-order");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("12\n0\n");
    }

    @Test
    void nativeBuiltJavanPreservesMutableReceiverInTypedCatch() throws Exception {
        final Path project = tempDir.resolve("selfhost-mutable-receiver-catch");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.security.SecureRandom;
            public final class Main {
                static SecureRandom random = new SecureRandom();
                static int clear() { random = null; return 0; }
                static int restore() { random = new SecureRandom(); return 1; }
                public static void main(final String[] args) {
                    try { random.nextInt(clear()); }
                    catch (IllegalArgumentException expected) { System.out.println("invalid"); }
                    try { random.nextInt(restore()); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-mutable-receiver-catch"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-mutable-receiver-catch");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("invalid\nnull\n");
    }

    @Test
    void nativeBuiltJavanRejectsSecureRandomSubclass() throws Exception {
        final Path project = tempDir.resolve("selfhost-secure-random-subclass");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.security.SecureRandom;
            public final class Main {
                static final class Custom extends SecureRandom {
                    @Override public int nextInt() { return 123; }
                }
                public static void main(String[] args) {
                    SecureRandom random = new Custom();
                    System.out.println(random.nextInt());
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult check = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "check", classes.toString(), "--main", "com.acme.Main"),
            Duration.ofSeconds(120)
        );
        assertThat(check.exitCode()).isEqualTo(2);
        assertThat(check.stderr()).contains("unsupported SecureRandom subclass");
    }

    @Test
    void nativeBuiltJavanBuildsIntegerRadixStrings() throws Exception {
        final Path project = tempDir.resolve("selfhost-integer-radix-strings");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Integer.toString(Integer.MIN_VALUE, 2));
                    System.out.println(Integer.toUnsignedString(-1, 36));
                    System.out.println(Integer.toUnsignedString(-1));
                    System.out.println(Integer.toHexString(-1));
                    System.out.println(Integer.toOctalString(-1));
                    System.out.println(Integer.toBinaryString(-1));
                    System.out.println(Long.toString(Long.MIN_VALUE, 2));
                    System.out.println(Long.toUnsignedString(-1L, 36));
                    System.out.println(Long.toUnsignedString(-1L));
                    System.out.println(Long.toHexString(-1L));
                    System.out.println(Long.toOctalString(-1L));
                    System.out.println(Long.toBinaryString(-1L));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-integer-radix-strings"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-integer-radix-strings").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanParsesDynamicNullCharacters() throws Exception {
        final Path project = tempDir.resolve("selfhost-double-parse-null");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    String[] inputs = {
                        new String(new char[] { 0, '1', 0 }),
                        new String(new char[] { 0, '-', '0', 'x', '1', 'p', '2', 0 }),
                        new String(new char[] { '1', 0, '2' }),
                        new String(new char[] { 0 })
                    };
                    for (String input : inputs) {
                        try {
                            System.out.println(Double.parseDouble(input));
                        } catch (NumberFormatException exception) {
                            System.out.println(exception.getMessage());
                        }
                    }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-double-parse-null"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-double-parse-null").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsUtf16LibraryExports() throws Exception {
        final Path project = tempDir.resolve("selfhost-utf16-library");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Text", """
            package com.acme;
            public final class Text {
                public static String echo(String value) { return value; }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Text.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--kind", "staticlib",
                "--export", "com.acme.Text.echo"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_utf16_selfhost.c", """
            #include <stdint.h>
            #include <string.h>
            #include ".javan/dist/bindings/c/classes.h"
            int main(void) {
                uint16_t data[] = { 'x', 0, 0xD800, 0xDC00, 0xDC00 };
                JavanUtf16View input = { data, 5 };
                JavanUtf16String output = { 0 };
                JavanResult result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                int failed = !result.ok || output.length != 5 || memcmp(data, output.data, sizeof(data));
                javan_result_free(&result);
                javan_free(output.data);
                return failed;
            }
            """);
        final Path binary = project.resolve("utf16-selfhost");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/libclasses.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(binary.toString()), Duration.ofSeconds(30),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
    }

    @Test
    void nativeBuiltJavanBuildsNestedReferenceSelection() throws Exception {
        final Path project = tempDir.resolve("selfhost-nested-reference-selection");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Box {
                    final int value;
                    Box(int value) { this.value = value; }
                }
                static Box first(boolean fail) {
                    if (fail) throw new IllegalArgumentException();
                    return new Box(10);
                }
                static int later() { System.out.println("later"); return 1; }
                static Box combine(Box box, int ignored) { return box; }
                static Box literal(int value) { return new Box(value); }
                static int select(boolean outer, boolean inner, boolean fail) {
                    Box box = outer ? combine(first(fail), later()) : literal(inner ? 20 : 30);
                    return box.value;
                }
                public static void main(String[] args) {
                    System.out.println(select(true, true, false));
                    System.out.println(select(false, true, false));
                    System.out.println(select(false, false, false));
                    try { System.out.println(select(true, false, true)); }
                    catch (IllegalArgumentException ex) { System.out.println("caught"); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-nested-reference-selection"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-nested-reference-selection").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsGuardedConditionalArguments() throws Exception {
        final Path project = tempDir.resolve("selfhost-guarded-arguments");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static boolean check() { return false; }
                static String fallback() { return "fallback"; }
                public static void main(String[] args) {
                    boolean flag = args.length > 0;
                    StringBuilder value = new StringBuilder();
                    value.append(flag && check() ? "selected" : fallback());
                    System.out.println(value.toString());
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-guarded-arguments"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-guarded-arguments").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("fallback\n");
    }

    @Test
    void nativeBuiltJavanBuildsNullLiteralsAndRejectsInvalidPaths() throws Exception {
        final Path project = tempDir.resolve("selfhost-null-literals");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.nio.file.Path;
            import java.nio.file.InvalidPathException;
            public final class Main {
                public static void main(String[] args) {
                    String text = "x\\0" + args.length;
                    System.out.println(text.length());
                    System.out.println((int) text.charAt(1));
                    String[] parts = new String[0];
                    try { Path.of(text, parts); }
                    catch (InvalidPathException e) { System.out.println(e.getMessage()); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-null-literals"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-null-literals").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsTypedPathResolveFailures() throws Exception {
        final Path project = tempDir.resolve("selfhost-path-resolve-errors");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.nio.file.Path;
            import java.nio.file.InvalidPathException;
            public final class Main {
                public static void main(String[] args) {
                    Path base = Path.of("safe");
                    String invalid = new String(new char[]{'x', 0, 'y'});
                    try { base.resolve(invalid); }
                    catch (InvalidPathException e) { System.out.println(e.getMessage()); }
                    System.out.println(base.resolve("child"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-path-resolve-errors"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-path-resolve-errors").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsDerivedPaths() throws Exception {
        final Path project = tempDir.resolve("selfhost-derived-paths");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.nio.file.Path;
            public final class Main {
                public static void main(String[] args) {
                    Path base = Path.of("a", "b", "c");
                    System.out.println(base.getFileName());
                    System.out.println(base.getParent().getFileName());
                    System.out.println(base.getName(0));
                    System.out.println(base.resolve("..").normalize().getFileName());
                    System.out.println(base.toAbsolutePath().isAbsolute());
                    System.out.println(base.relativize(base.resolve("d")));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-derived-paths"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-derived-paths").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsDerivedStrings() throws Exception {
        final Path project = tempDir.resolve("selfhost-derived-strings");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Locale;
            public final class Main {
                public static void main(String[] args) {
                    String value = new String(new char[]{' ', 'A', 'b', 'c', ' '});
                    System.out.println(value.substring(1, 4));
                    System.out.println(value.trim());
                    System.out.println(value.stripLeading());
                    System.out.println(value.stripTrailing());
                    System.out.println(value.replace('A', 'X'));
                    System.out.println(value.repeat(2));
                    System.out.println(value.toLowerCase(Locale.ROOT));
                    System.out.println(value.toUpperCase());
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-derived-strings"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-derived-strings").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsUnicodeStrip() throws Exception {
        final Path project = tempDir.resolve("selfhost-unicode-strip");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    String value = new String(new char[]{0x2003, 'A', 0x3000});
                    System.out.println(value.strip().length());
                    System.out.println((int) value.strip().charAt(0));
                    System.out.println(value.trim().length());
                    String spaces = " ".repeat(100000);
                    String unicode = String.valueOf((char) 0x2003).repeat(100000);
                    System.out.println(("x" + spaces).trim().equals("x"));
                    System.out.println(("x" + unicode).stripTrailing().equals("x"));
                    System.out.println((unicode + "x").stripLeading().equals("x"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-unicode-strip"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-unicode-strip").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCatchableStringCharAt() throws Exception {
        final Path project = tempDir.resolve("selfhost-char-at-exceptions");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static void print(String value, int index) {
                    try { System.out.println((int) value.charAt(index)); }
                    catch (StringIndexOutOfBoundsException ex) { System.out.println("index"); }
                    catch (NullPointerException ex) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    print("A", -1); print("A", 1); print(null, 0); print("A", 0);
                    System.out.println((int) "\\0\\ud800\\udc00\\udfff".charAt(0));
                    System.out.println((int) "\\0\\ud800\\udc00\\udfff".charAt(1));
                    System.out.println((int) "\\0\\ud800\\udc00\\udfff".charAt(2));
                    System.out.println((int) "\\0\\ud800\\udc00\\udfff".charAt(3));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-char-at-exceptions"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-char-at-exceptions").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterToCharsIntoArray() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-to-chars-array");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static void write(int value, int index) {
                    char[] chars = {'x', 'y', 'z'};
                    try { System.out.println(Character.toChars(value, chars, index)); }
                    catch (IllegalArgumentException ex) { System.out.println("invalid"); }
                    catch (IndexOutOfBoundsException ex) { System.out.println("index"); }
                    for (char ch : chars) System.out.println((int) ch);
                }
                public static void main(String[] args) {
                    int[] values = {-1, 0, 0xd800, 0xffff, 0x10000, 0x1f600, 0x10ffff, 0x110000};
                    for (int value : values) for (int index = -1; index <= 3; index++) write(value, index);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-to-chars-array"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-to-chars-array").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterToCharsArrayLengthInsideTry() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-to-chars-handler-length");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static void print(int value) {
                    try { System.out.println(Character.toChars(value).length); }
                    catch (IllegalArgumentException ex) { System.out.println("invalid"); }
                }
                static void printSuper(int value) {
                    try { System.out.println(Character.toChars(value).length); }
                    catch (RuntimeException ex) { System.out.println("runtime"); }
                }
                public static void main(String[] args) {
                    print(-1); print(0); print(0x1f600); print(0x110000); printSuper(-1); printSuper(0x1f600);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-to-chars-handler-length"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-to-chars-handler-length").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringGetChars() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-get-chars");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int copy(String value, int begin, int end, char[] target, int destination) {
                    try { value.getChars(begin, end, target, destination); return 0; }
                    catch (StringIndexOutOfBoundsException ex) { return -1; }
                    catch (NullPointerException ex) { return -2; }
                }
                public static void main(String[] args) {
                    String value = "a\\0\\ud800\\udc00\\udfff\\udbffz";
                    for (int begin = -1; begin <= value.length() + 1; begin++)
                        for (int end = -1; end <= value.length() + 1; end++) {
                            char[] target = {'A','B','C','D','E','F','G'};
                            System.out.println(copy(value, begin, end, target, 1));
                            for (int i = 0; i < target.length; i++) System.out.println((int) target[i]);
                        }
                    System.out.println(copy(null, -1, -1, null, -1));
                    System.out.println(copy(value, -1, 0, null, 0));
                    System.out.println(copy(value, 0, 0, null, 0));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-get-chars"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-get-chars").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsUnicodeStringLength() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-length-unicode");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int length(String value) {
                    try { return value.length(); }
                    catch (NullPointerException ex) { return -1; }
                }
                public static void main(String[] args) {
                    System.out.println("".length()); System.out.println("aé𐀀".length());
                    System.out.println("\\0\\ud800\\udc00\\udfff\\udbff".length());
                    System.out.println(length(null)); System.out.println(length("abc"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-length-unicode"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-length-unicode").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsUnicodeStringRepeat() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-repeat-unicode");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static String repeat(String value, int count) {
                    try { return value.repeat(count); }
                    catch (NullPointerException ex) { System.out.println("null"); return null; }
                    catch (IllegalArgumentException ex) { System.out.println("negative"); return null; }
                }
                static void print(String value) {
                    if (value == null) return;
                    char[] chars = value.toCharArray();
                    System.out.println(chars.length);
                    for (int i = 0; i < chars.length; i++) System.out.println((int) chars[i]);
                }
                public static void main(String[] args) {
                    print("\\0\\ud800\\udc00\\udfff\\udbff".repeat(2));
                    print(repeat("\\0\\ud800z", 0)); print(repeat("\\0\\ud800z", 1));
                    print(repeat("\\0\\ud800z", 2)); print(repeat("a", -1)); print(repeat(null, -1));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-repeat-unicode"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-repeat-unicode").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListGetFailures() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-get-errors");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;
            public final class Main {
                public static void main(String[] args) {
                    ArrayList<String> concrete = new ArrayList<>();
                    List<String> values = concrete;
                    AbstractList<String> abstractValues = concrete;
                    List<String> missing = args.length == 0 ? null : values;
                    try { values.get(0); } catch (IndexOutOfBoundsException expected) { System.out.println("empty"); }
                    try { abstractValues.get(-1); } catch (IndexOutOfBoundsException expected) { System.out.println("negative"); }
                    try { missing.get(0); } catch (NullPointerException expected) { System.out.println("null"); }
                    try { values.set(0, "x"); } catch (IndexOutOfBoundsException expected) { System.out.println("set empty"); }
                    try { missing.set(0, "x"); } catch (NullPointerException expected) { System.out.println("set null"); }
                    try { List.of().set(0, "x"); } catch (UnsupportedOperationException expected) { System.out.println("set immutable"); }
                    try { values.remove(0); } catch (IndexOutOfBoundsException expected) { System.out.println("remove empty"); }
                    try { missing.remove(0); } catch (NullPointerException expected) { System.out.println("remove null"); }
                    try { List.of().remove(0); } catch (UnsupportedOperationException expected) { System.out.println("remove immutable"); }
                    try { values.removeFirst(); } catch (java.util.NoSuchElementException expected) { System.out.println("remove first empty"); }
                    try { missing.removeLast(); } catch (NullPointerException expected) { System.out.println("remove last null"); }
                    try { List.of().removeFirst(); } catch (UnsupportedOperationException expected) { System.out.println("remove first immutable"); }
                    try { java.util.Collections.emptyList().removeLast(); } catch (java.util.NoSuchElementException expected) { System.out.println("remove last legacy empty"); }
                    try { missing.addFirst("x"); } catch (NullPointerException expected) { System.out.println("add first null"); }
                    try { List.of().addLast("x"); } catch (UnsupportedOperationException expected) { System.out.println("add last immutable"); }
                    concrete.add("first"); concrete.add("last");
                    System.out.println(values.get(0)); System.out.println(concrete.get(1));
                    System.out.println(abstractValues.set(0, "changed"));
                    System.out.println(values.get(0));
                    System.out.println(abstractValues.remove(0));
                    System.out.println(values.get(0));
                    System.out.println(values.removeFirst());
                    values.addFirst("new first"); values.addLast("new last");
                    System.out.println(values.getFirst()); System.out.println(values.getLast());
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-get-errors"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-get-errors").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCollectionFilterAll() throws Exception {
        final Path project = tempDir.resolve("selfhost-collection-filter-all");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.Collections;
            import java.util.HashSet;
            import java.util.List;
            import java.util.Set;
            public final class Main {
                public static void main(String[] args) {
                    Collection<String> values = new ArrayList<>(); values.add("x"); values.add("y");
                    Set<String> set = new HashSet<>(); set.addAll(values);
                    System.out.println(values.removeAll(Collections.unmodifiableSet(Set.of("x"))));
                    System.out.println(set.retainAll(List.of("y"))); System.out.println(set.size());
                    try { values.removeAll(null); } catch (NullPointerException expected) { System.out.println("null"); }
                    try { Set.of("x").retainAll(List.of("x")); } catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-collection-filter-all"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-collection-filter-all").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCollectionContainsAll() throws Exception {
        final Path project = tempDir.resolve("selfhost-collection-contains-all");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.Collections;
            import java.util.HashSet;
            import java.util.List;
            import java.util.Set;
            public final class Main {
                public static void main(String[] args) {
                    Collection<String> values = new ArrayList<>(); values.add("x"); values.add("y");
                    Set<String> set = new HashSet<>(); set.addAll(values);
                    System.out.println(set.containsAll(Collections.unmodifiableCollection(values)));
                    System.out.println(values.containsAll(List.of("z")));
                    try { set.containsAll(null); } catch (NullPointerException expected) { System.out.println("null"); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-collection-contains-all"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-collection-contains-all").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCollectionAddAll() throws Exception {
        final Path project = tempDir.resolve("selfhost-collection-add-all");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.HashSet;
            import java.util.List;
            import java.util.Set;
            public final class Main {
                public static void main(String[] args) {
                    Collection<String> values = new ArrayList<>(); values.add("x");
                    System.out.println(values.addAll(List.of("y")));
                    Set<String> set = new HashSet<>(); System.out.println(set.addAll(values));
                    System.out.println(set.size()); System.out.println(set.contains("y"));
                    try { set.addAll(null); } catch (NullPointerException expected) { System.out.println("null"); }
                    try { Set.of().addAll(List.of()); } catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-collection-add-all"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-collection-add-all").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListAddAll() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-add-all");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;
            public final class Main {
                public static void main(String[] args) {
                    List<String> values = new ArrayList<>(); values.add("x");
                    System.out.println(values.addAll(Collections.unmodifiableList(values)));
                    System.out.println(values.size()); System.out.println(values.get(1));
                    try { values.addAll(null); } catch (NullPointerException expected) { System.out.println("null"); }
                    try { List.of().addAll(List.of()); } catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-add-all"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-add-all").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListEndpointFailures() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-endpoint-errors");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.NoSuchElementException;
            public final class Main {
                public static void main(String[] args) {
                    ArrayList<String> concrete = new ArrayList<>();
                    List<String> values = concrete;
                    List<String> missing = args.length == 0 ? null : values;
                    try { values.getFirst(); } catch (NoSuchElementException expected) { System.out.println("first empty"); }
                    try { concrete.getLast(); } catch (NoSuchElementException expected) { System.out.println("last empty"); }
                    try { missing.getFirst(); } catch (NullPointerException expected) { System.out.println("null"); }
                    concrete.add("first"); concrete.add("last");
                    System.out.println(values.getFirst()); System.out.println(concrete.getLast());
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-endpoint-errors"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-endpoint-errors").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListOfNullFailures() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-of-null");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.List;
            public final class Main {
                public static void main(String[] args) {
                    String missing = args.length == 0 ? null : "value";
                    String[] missingArray = args.length == 0 ? null : new String[] {"value"};
                    String[] elements = {"first", missing, "last"};
                    try { List.of(missing); } catch (NullPointerException expected) { System.out.println("single"); }
                    try { List.of("first", missing); } catch (NullPointerException expected) { System.out.println("pair"); }
                    try { List.of(elements); } catch (NullPointerException expected) { System.out.println("element"); }
                    try { List.of(missingArray); } catch (NullPointerException expected) { System.out.println("array"); }
                    System.out.println(List.of("after", "after").size());
                    System.out.println(java.util.Collections.singletonList(null).get(0) == null);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-of-null"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-of-null").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsEmptyCollectionClear() throws Exception {
        final Path project = tempDir.resolve("selfhost-empty-clear");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void check(Collection<?> values) {
                    try { values.clear(); System.out.println("cleared"); }
                    catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
                public static void main(String[] args) {
                    check(Collections.emptyList()); check(Collections.emptySet());
                    check(List.of()); check(Set.of());
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-empty-clear"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-empty-clear").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsSingletonEmptyBulkFiltering() throws Exception {
        final Path project = tempDir.resolve("selfhost-singleton-empty-filter");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void check(Collection<String> values, Collection<String> probe) {
                    try { System.out.println(values.removeAll(probe)); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    check(Collections.singletonList("x"), List.of());
                    check(Collections.singleton("x"), Set.of());
                    check(Collections.singletonList("x"), null);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-singleton-empty-filter"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-singleton-empty-filter").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsEmptyCollectionBulkFiltering() throws Exception {
        final Path project = tempDir.resolve("selfhost-empty-bulk-filter");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void remove(Collection<String> values, Collection<String> probe) {
                    try { System.out.println(values.removeAll(probe)); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
                static void retain(Collection<String> values, Collection<String> probe) {
                    try { System.out.println(values.retainAll(probe)); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    remove(Collections.emptyList(), List.of("x")); retain(Collections.emptyList(), null);
                    remove(Collections.emptySet(), null); retain(Collections.emptySet(), Set.of("x"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-empty-bulk-filter"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-empty-bulk-filter").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsEmptyCollectionAddAll() throws Exception {
        final Path project = tempDir.resolve("selfhost-empty-add-all");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void add(Collection<String> values, Collection<String> source) {
                    try { System.out.println(values.addAll(source)); }
                    catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
                static void addAt(List<String> values, int index, Collection<String> source) {
                    try { System.out.println(values.addAll(index, source)); }
                    catch (IndexOutOfBoundsException expected) { System.out.println("index"); }
                }
                public static void main(String[] args) {
                    add(Collections.emptyList(), List.of()); add(Collections.singleton("x"), Set.of());
                    add(Collections.emptySet(), null); add(Collections.singletonList("x"), List.of("x"));
                    addAt(Collections.emptyList(), 1, null);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-empty-add-all"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-empty-add-all").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListBulkRemovalNullProbeFailures() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-filter-null");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void remove(List<String> values, Collection<String> probes) {
                    try { values.removeAll(probes); } catch (NullPointerException expected) { System.out.println("null"); }
                }
                static void retain(List<String> values, Collection<String> probes) {
                    try { values.retainAll(probes); } catch (NullPointerException expected) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    List<String> removed = new ArrayList<>(); removed.add("x"); removed.add(null); removed.add("z");
                    remove(removed, List.of("x"));
                    System.out.println(removed.size()); System.out.println(removed.get(0)); System.out.println(removed.get(1));
                    List<String> retained = new ArrayList<>(); retained.add("x"); retained.add(null); retained.add("z");
                    retain(retained, Set.of("z"));
                    System.out.println(retained.size()); System.out.println(retained.get(0)); System.out.println(retained.get(1));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-filter-null"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-filter-null").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsEmptyAndSingletonListRemoval() throws Exception {
        final Path project = tempDir.resolve("selfhost-singleton-list-remove");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                static void check(List<Key> values, Key query) {
                    try { System.out.println(values.remove(query)); }
                    catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
                public static void main(String[] args) {
                    System.out.println(Collections.emptyList().remove(new Unsupported(Optional.empty())));
                    check(Collections.singletonList(new Key(7)), new Key(3));
                    check(Collections.singletonList(new Key(7)), new Key(7));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-singleton-list-remove"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-singleton-list-remove").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsSingletonSetContains() throws Exception {
        final Path project = tempDir.resolve("selfhost-singleton-set-contains");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                public static void main(String[] args) {
                    System.out.println(Collections.emptySet().contains(new Unsupported(Optional.empty())));
                    Set<Key> singleton = Collections.singleton(new Key(7));
                    System.out.println(singleton.contains(new Key(3)));
                    System.out.println(Collections.unmodifiableSet(singleton).contains(new Key(7)));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-singleton-set-contains"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-singleton-set-contains").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsSingletonSetContainsAll() throws Exception {
        final Path project = tempDir.resolve("selfhost-singleton-set-contains-all");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                public static void main(String[] args) {
                    System.out.println(Collections.emptySet().containsAll(List.of(new Unsupported(Optional.empty()))));
                    Set<Key> singleton = Collections.singleton(new Key(7));
                    System.out.println(singleton.containsAll(List.of(new Key(3))));
                    System.out.println(Collections.unmodifiableSet(singleton).containsAll(List.of(new Key(7))));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-singleton-set-contains-all"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-singleton-set-contains-all").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsEmptyAndSingletonSetRemoval() throws Exception {
        final Path project = tempDir.resolve("selfhost-singleton-set-remove");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                static void check(Set<Key> values, Key query) {
                    try { System.out.println(values.remove(query)); }
                    catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
                public static void main(String[] args) {
                    System.out.println(Collections.emptySet().remove(new Unsupported(Optional.empty())));
                    check(Collections.singleton(new Key(7)), new Key(3));
                    check(Collections.singleton(new Key(7)), new Key(7));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-singleton-set-remove"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-singleton-set-remove").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListBulkFilteringApplicationEquality() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-filter-equality");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                public static void main(String[] args) {
                    List<Key> values = new ArrayList<>(); values.add(new Key(7)); values.add(new Key(3));
                    System.out.println(values.removeAll(List.of(new Key(7))));
                    System.out.println(values.size()); System.out.println(values.get(0).id);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-filter-equality"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-filter-equality").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListObjectRemovalApplicationEquality() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-remove-equality");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                static boolean remove(List<Object> values, Object query) { return values.remove(query); }
                public static void main(String[] args) {
                    Unsupported unrelated = new Unsupported(Optional.empty());
                    System.out.println(unrelated != null);
                    List<Object> values = new ArrayList<>(); values.add(new Key(7));
                    System.out.println(remove(values, new Key(3)));
                    System.out.println(remove(values, new Key(7)));
                    System.out.println(values.size());
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-remove-equality"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-remove-equality").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsListContainsAllApplicationEquality() throws Exception {
        final Path project = tempDir.resolve("selfhost-contains-all-equality");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                record Unsupported(Optional<String> value) { }
                static final class Key {
                    final int id;
                    Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                static boolean check(List<Object> values, Collection<Object> probes) { return values.containsAll(probes); }
                public static void main(String[] args) {
                    Unsupported unrelated = new Unsupported(Optional.empty());
                    System.out.println(unrelated != null);
                    System.out.println(check(List.of(new Key(7)), List.of(new Key(7))));
                    System.out.println(check(List.of(new Key(7)), List.of(new Key(3))));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-contains-all-equality"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-contains-all-equality").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsImmutableContainsAllNullChecks() throws Exception {
        final Path project = tempDir.resolve("selfhost-contains-all-null");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void check(Collection<String> values, Collection<String> probes) {
                    try { System.out.println(values.containsAll(probes)); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    List<String> missingFirst = new ArrayList<>(); missingFirst.add("missing"); missingFirst.add(null);
                    check(List.of("x"), Collections.singletonList(null));
                    check(Set.of("x"), missingFirst);
                    check(Collections.singletonList(null), Collections.singletonList(null));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-contains-all-null"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-contains-all-null").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsImmutableMapNullSearches() throws Exception {
        final Path project = tempDir.resolve("selfhost-map-null-search");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void check(Map<String, String> values) {
                    try { System.out.println(values.containsKey(null)); } catch (NullPointerException expected) { System.out.println("key"); }
                    try { System.out.println(values.containsValue(null)); } catch (NullPointerException expected) { System.out.println("value"); }
                    try { System.out.println((Object) values.get(null)); } catch (NullPointerException expected) { System.out.println("get"); }
                    try { System.out.println((Object) values.getOrDefault(null, "x")); } catch (NullPointerException expected) { System.out.println("default"); }
                }
                public static void main(String[] args) {
                    check(Map.of());
                    check(Collections.unmodifiableMap(Map.copyOf(Map.of("x", "y"))));
                    check(Collections.emptyMap());
                    check(Collections.singletonMap(null, null));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-map-null-search"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-map-null-search").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsImmutableSetNullSearches() throws Exception {
        final Path project = tempDir.resolve("selfhost-set-null-search");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void check(Set<String> values) {
                    try { System.out.println(values.contains(null)); } catch (NullPointerException expected) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    check(Set.of());
                    check(Collections.unmodifiableSet(Set.copyOf(Set.of("x"))));
                    check(Collections.emptySet());
                    check(Collections.singleton(null));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-set-null-search"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-set-null-search").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsImmutableListNullSearches() throws Exception {
        final Path project = tempDir.resolve("selfhost-list-null-search");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static void check(List<String> values) {
                    try { System.out.println(values.contains(null)); } catch (NullPointerException expected) { System.out.println("contains"); }
                    try { System.out.println(values.indexOf(null)); } catch (NullPointerException expected) { System.out.println("first"); }
                    try { System.out.println(values.lastIndexOf(null)); } catch (NullPointerException expected) { System.out.println("last"); }
                }
                public static void main(String[] args) {
                    check(List.of());
                    check(Collections.unmodifiableList(List.copyOf(List.of("x"))));
                    check(Collections.singletonList(null));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-list-null-search"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-list-null-search").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringIsBlank() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-is-blank");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static boolean blank(String value) {
                    try { return value.isBlank(); }
                    catch (NullPointerException ex) { System.out.println("null"); return false; }
                }
                public static void main(String[] args) {
                    System.out.println("".isBlank());
                    System.out.println(" \\t\\u2003".isBlank());
                    System.out.println("\\0\\ud800".isBlank());
                    System.out.println(blank(null));
                    System.out.println(blank("aé𐀀"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-is-blank"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-is-blank").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringIsEmpty() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-is-empty");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static boolean empty(String value) {
                    try { return value.isEmpty(); }
                    catch (NullPointerException ex) { System.out.println("null"); return false; }
                }
                public static void main(String[] args) {
                    System.out.println("".isEmpty());
                    System.out.println("\\0\\ud800".isEmpty());
                    System.out.println(empty(null));
                    System.out.println(empty("aé𐀀"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-is-empty"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-is-empty").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringHashCode() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-hash");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int hash(String value) {
                    try { return value.hashCode(); }
                    catch (NullPointerException ex) { System.out.println("null"); return -1; }
                }
                public static void main(String[] args) {
                    System.out.println("\\0\\ud800\\udc00\\udfff\\udbff".hashCode());
                    System.out.println(hash(null));
                    System.out.println(hash("aé𐀀"));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-hash"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-hash").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringToCharArray() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-to-char-array");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static char[] convert(String value) {
                    try { return value.toCharArray(); }
                    catch (NullPointerException ex) { System.out.println("null"); return null; }
                }
                static void print(String value) {
                    char[] chars = convert(value);
                    if (chars == null) return;
                    System.out.println(chars.length);
                    for (int i = 0; i < chars.length; i++) System.out.println((int) chars[i]);
                    System.out.println(value.equals(new String(chars)));
                }
                public static void main(String[] args) {
                    print(null); print(""); print("abc"); print("\\0\\ud800\\udc00\\udfff\\udbffz");
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-to-char-array"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-to-char-array").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void staticLibraryNestedReferenceSelectionTransportsFailures() throws Exception {
        final Path project = project("library-nested-reference-selection");
        writeJava(project, "com.acme.Text", """
            package com.acme;
            public final class Text {
                static final class Box {
                    final int value;
                    Box(int value) { this.value = value; }
                }
                static Box first(int fail) {
                    if (fail != 0) throw new IllegalArgumentException();
                    return new Box(10);
                }
                static Box box(int value) { return new Box(value); }
                public static int select(int outer, int inner, int fail) {
                    Box selected = outer != 0 ? first(fail) : box(inner != 0 ? 20 : 30);
                    return selected.value;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Text.select");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-nested-reference-selection.a");
        final Path caller = writeC(project, "call_selection.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-nested-reference-selection.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    int value = -1;
                    JavanResult result = javan_try_com_acme_Text_select_int_int_int(1, 0, 0, &value);
                    if (!result.ok || value != 10) return 1;
                    javan_result_free(&result);
                    result = javan_try_com_acme_Text_select_int_int_int(0, 1, 0, &value);
                    if (!result.ok || value != 20) return 2;
                    javan_result_free(&result);
                    result = javan_try_com_acme_Text_select_int_int_int(0, 0, 0, &value);
                    if (!result.ok || value != 30) return 3;
                    javan_result_free(&result);
                    result = javan_try_com_acme_Text_select_int_int_int(1, 0, 1, &value);
                    if (result.ok || value != 0 || result.message == NULL
                        || strstr(result.message, "java/lang/IllegalArgumentException") == NULL) return 4;
                    javan_result_free(&result);
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-selection");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void staticLibraryCharacterArrayOffsetTransportsFailures() throws Exception {
        final Path project = project("library-character-offset");
        writeJava(project, "com.acme.Text", """
            package com.acme;
            public final class Text {
                public static int offset(int start, int count, int index, int distance) {
                    char[] chars = {'a', '\\ud800', '\\udc00', 'z'};
                    return Character.offsetByCodePoints(chars, start, count, index, distance);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Text.offset");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-character-offset.a");
        final Path caller = writeC(project, "call_offset.c", """
            #include ".javan/dist/bindings/c/library-character-offset.h"
            int main(void) {
                int value = 99;
                JavanResult result = javan_try_com_acme_Text_offset_int_int_int_int(0, 4, 1, 1, &value);
                if (!result.ok || value != 3) return 1;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_offset_int_int_int_int(0, 4, 4, 1, &value);
                if (result.ok || value != 0) return 2;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_offset_int_int_int_int(0, 4, 3, -1, &value);
                if (!result.ok || value != 1) return 3;
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-offset");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void nativeBuiltJavanBuildsCharacterArrayOffsetByCodePoints() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-array-codepoint-offset");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int offset(char[] value, int start, int count, int index, int shift) {
                    try { return Character.offsetByCodePoints(value, start, count, index, shift); }
                    catch (NullPointerException ex) { return -1; }
                    catch (IndexOutOfBoundsException ex) { return -2; }
                }
                public static void main(String[] args) {
                    char[] value = {'a', '\\ud800', '\\udc00', '\\udfff', 0};
                    for (int start = -1; start <= value.length + 1; start++)
                        for (int count = -1; count <= value.length + 1; count++)
                            for (int index = -1; index <= value.length + 1; index++)
                                for (int shift = -2; shift <= 2; shift++) {
                                    System.out.println(offset(value, start, count, index, shift));
                                    System.out.println(offset(null, start, count, index, shift));
                                }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-array-codepoint-offset"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-array-codepoint-offset").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void staticLibraryCharacterArrayCodePointCountTransportsFailures() throws Exception {
        final Path project = project("library-character-count");
        writeJava(project, "com.acme.Text", """
            package com.acme;
            public final class Text {
                public static int count(int offset, int length) {
                    char[] chars = {'a', '\\ud800', '\\udc00', 'z'};
                    return Character.codePointCount(chars, offset, length);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib", "--export", "com.acme.Text.count");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-character-count.a");
        final Path caller = writeC(project, "call_count.c", """
            #include ".javan/dist/bindings/c/library-character-count.h"
            int main(void) {
                int value = 99;
                JavanResult result = javan_try_com_acme_Text_count_int_int(1, 2, &value);
                if (!result.ok || value != 1) return 1;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_count_int_int(-1, 2, &value);
                if (result.ok || value != 0) return 2;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_count_int_int(0, 4, &value);
                if (!result.ok || value != 3) return 3;
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-count");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void nativeBuiltJavanBuildsCharacterArrayCodePointCount() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-array-codepoint-count");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int count(char[] value, int offset, int length) {
                    try { return Character.codePointCount(value, offset, length); }
                    catch (NullPointerException ex) { return -1; }
                    catch (IndexOutOfBoundsException ex) { return -2; }
                }
                public static void main(String[] args) {
                    char[] value = {'a', '\\ud800', '\\udc00', '\\udfff', 0};
                    for (int i = -1; i <= value.length + 1; i++)
                        for (int length = -1; length <= value.length + 1; length++) {
                            System.out.println(count(value, i, length));
                            System.out.println(count(null, i, length));
                        }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-array-codepoint-count"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-array-codepoint-count").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterArrayCodePointAccessWithLimits() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-array-codepoint-limits");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int at(char[] value, int index, int limit) {
                    try { return Character.codePointAt(value, index, limit); }
                    catch (NullPointerException ex) { return -1; }
                    catch (IndexOutOfBoundsException ex) { return -2; }
                }
                static int before(char[] value, int index, int start) {
                    try { return Character.codePointBefore(value, index, start); }
                    catch (NullPointerException ex) { return -1; }
                    catch (IndexOutOfBoundsException ex) { return -2; }
                }
                public static void main(String[] args) {
                    char[] value = {'a', '\\ud800', '\\udc00', '\\udfff', 0};
                    for (int i = -1; i <= value.length + 1; i++)
                        for (int bound = -1; bound <= value.length + 1; bound++) {
                            System.out.println(at(value, i, bound));
                            System.out.println(before(value, i, bound));
                            System.out.println(at(null, i, bound));
                            System.out.println(before(null, i, bound));
                        }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-array-codepoint-limits"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-array-codepoint-limits").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterArrayCodePointAccess() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-array-codepoints");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static int at(char[] value, int index) {
                    try { return Character.codePointAt(value, index); }
                    catch (NullPointerException ex) { return -1; }
                    catch (ArrayIndexOutOfBoundsException ex) { return -2; }
                }
                static int before(char[] value, int index) {
                    try { return Character.codePointBefore(value, index); }
                    catch (NullPointerException ex) { return -1; }
                    catch (ArrayIndexOutOfBoundsException ex) { return -2; }
                }
                public static void main(String[] args) {
                    char[] value = {'a', '\\ud800', '\\udc00', '\\udfff', 0};
                    for (int i = -1; i <= value.length + 1; i++) {
                        System.out.println(at(value, i));
                        System.out.println(before(value, i));
                    }
                    System.out.println(at(null, 0));
                    System.out.println(before(null, 0));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-array-codepoints"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-array-codepoints").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterCodePointToString() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-codepoint-string");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static String convert(int value) {
                    try { return Character.toString(value); }
                    catch (IllegalArgumentException ex) { System.out.println("invalid"); return null; }
                }
                public static void main(String[] args) {
                    int[] values = {-1, 0, 0xd800, 0xffff, 0x10000, 0x1f600, 0x10ffff, 0x110000};
                    for (int value : values) {
                        String text = convert(value);
                        if (text != null) {
                            System.out.println(text.length());
                            for (int i = 0; i < text.length(); i++) System.out.println((int) text.charAt(i));
                        }
                    }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-codepoint-string"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-codepoint-string").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterToChars() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-to-chars");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static char[] convert(int value) {
                    try { return Character.toChars(value); }
                    catch (IllegalArgumentException ex) { System.out.println("invalid"); return null; }
                }
                public static void main(String[] args) {
                    int[] values = {-1, 0, 0xd800, 0xffff, 0x10000, 0x1f600, 0x10ffff, 0x110000};
                    for (int value : values) {
                        char[] chars = convert(value);
                        if (chars != null) {
                            System.out.println(chars.length);
                            for (char ch : chars) System.out.println((int) ch);
                        }
                    }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-to-chars"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-to-chars").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringCodePointOffset() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-codepoint-offset");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static void offset(String value, int index, int distance) {
                    try { System.out.println(value.offsetByCodePoints(index, distance)); }
                    catch (IndexOutOfBoundsException ex) { System.out.println("index"); }
                    catch (NullPointerException ex) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    String value = new String(new char[]{'a', (char) 0xd83d, (char) 0xde00, (char) 0xd800});
                    for (int index = -1; index <= 5; index++) for (int distance = -5; distance <= 5; distance++) offset(value, index, distance);
                    offset(null, 0, 0);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-codepoint-offset"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-codepoint-offset").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringCodePointCount() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-codepoint-count");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static void count(String value, int begin, int end) {
                    try { System.out.println(value.codePointCount(begin, end)); }
                    catch (IndexOutOfBoundsException ex) { System.out.println("index"); }
                    catch (NullPointerException ex) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    String value = new String(new char[]{'a', (char) 0xd83d, (char) 0xde00, (char) 0xd800});
                    for (int begin = -1; begin <= 5; begin++) for (int end = -1; end <= 5; end++) count(value, begin, end);
                    count(null, 0, 0);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-codepoint-count"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-codepoint-count").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringCodePointAccess() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-codepoint-access");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static void at(String value, int index) {
                    try { System.out.println(value.codePointAt(index)); }
                    catch (IndexOutOfBoundsException ex) { System.out.println("index"); }
                    catch (NullPointerException ex) { System.out.println("null"); }
                }
                static void before(String value, int index) {
                    try { System.out.println(value.codePointBefore(index)); }
                    catch (IndexOutOfBoundsException ex) { System.out.println("index"); }
                    catch (NullPointerException ex) { System.out.println("null"); }
                }
                public static void main(String[] args) {
                    String value = new String(new char[]{'a', (char) 0xd83d, (char) 0xde00, (char) 0xd800});
                    for (int index = -1; index <= 5; index++) { at(value, index); before(value, index); }
                    at(null, 0); before(null, 0);
                    System.out.println("\\0\\ud800\\udc00\\udfff".codePointAt(1));
                    System.out.println("\\0\\ud800\\udc00\\udfff".codePointBefore(3));
                    System.out.println("\\0\\ud800\\udc00\\udfff".codePointCount(0, 4));
                    System.out.println("\\0\\ud800\\udc00\\udfff".offsetByCodePoints(1, 1));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-codepoint-access"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-codepoint-access").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsStringCodePointSearch() throws Exception {
        final Path project = tempDir.resolve("selfhost-string-codepoint-search");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Sequence implements CharSequence {
                    public int length() { return 3; }
                    public char charAt(int index) { return 'x'; }
                    public CharSequence subSequence(int begin, int end) { return this; }
                    public String toString() { return "pha"; }
                }
                static final class CustomGenerator implements java.util.random.RandomGenerator {
                    public long nextLong() { return 42L; }
                    public void nextBytes(byte[] bytes) { if (bytes != null && bytes.length > 0) bytes[0] = 42; }
                    public int nextInt(int bound) { return bound + 42; }
                }
                static String nullReplace(String value) {
                    try { return value.replace('a', 'b'); } catch (NullPointerException ex) { return "null"; }
                }
                static String nullConcat(String value) {
                    try { return value.concat("x"); } catch (NullPointerException ex) { return "null"; }
                }
                static int nullSearch(String value) {
                    try { return value.lastIndexOf(-1, -1); } catch (NullPointerException ex) { return -9; }
                }
                static int nullSubstring(String value) {
                    try { return "text".indexOf(value); } catch (NullPointerException ex) { return -9; }
                }
                static boolean nullEquals(String value) {
                    try { return value.equals(null); } catch (NullPointerException ex) { return false; }
                }
                public static void main(String[] args) {
                    String value = new String(new char[]{'a', (char) 0xd83d, (char) 0xde00, 'b', (char) 0xd83d, (char) 0xde00});
                    int[] targets = {-1, 0xd83d, 0xde00, 0x1f600, 0x110000};
                    for (int target : targets) {
                        System.out.println(value.indexOf(target));
                        System.out.println(value.lastIndexOf(target));
                        System.out.println(value.indexOf(target, 2));
                        System.out.println(value.lastIndexOf(target, 2));
                    }
                    System.out.println("a".repeat(100000).lastIndexOf('z'));
                    System.out.println("\\0\\ud800\\udc00\\udfff".indexOf(0x10000));
                    System.out.println("\\0\\ud800\\udc00\\udfff".indexOf(0x10000, 1));
                    System.out.println("\\0\\ud800\\udc00\\udfff".lastIndexOf(0x10000));
                    System.out.println("\\0\\ud800\\udc00\\udfff".lastIndexOf(0x10000, 1));
                    System.out.println(nullSearch(null));
                    System.out.println("\\0\\ud800\\udc00\\udfff".indexOf("\\ud800\\udc00"));
                    System.out.println("\\0\\ud800\\udc00\\udfff".lastIndexOf("\\udfff", 3));
                    System.out.println("\\0\\ud800\\udc00\\udfff".indexOf(0x10000, 1, 3));
                    System.out.println("\\0\\ud800\\udc00\\udfff".indexOf(0x10000, 1, 2));
                    System.out.println("\\0\\ud800\\udc00\\udfff".indexOf("\\udfff", 1, 4));
                    System.out.println("\\0\\ud800\\udc00\\udfff".substring(1, 3).length());
                    System.out.println("\\0\\ud800\\udc00\\udfff".substring(2).charAt(0));
                    System.out.println("caf\\u00e9".subSequence(1, 4));
                    System.out.println("\\0\\ud800\\udc00".startsWith("\\ud800", 1));
                    System.out.println("\\0\\ud800\\udc00".endsWith("\\udc00"));
                    System.out.println("x".startsWith(null, -1));
                    System.out.println("".equals(new int[0]));
                    System.out.println("\\0\\ud800\\udc00".equals(new String(new char[] {0, (char) 0xd800, (char) 0xdc00})));
                    System.out.println(nullEquals(null));
                    System.out.println("alphabet".contains(new StringBuilder("pha")));
                    System.out.println("alphabet".contains(new StringBuilder()));
                    System.out.println("alphabet".contains(new Sequence()));
                    System.out.println("alphabet".replace(new Sequence(), new StringBuilder("X")));
                    System.out.println("\\0\\ud800\\udc00\\udfff".getBytes().length);
                    System.out.println("\\0\\ud800".getBytes(java.nio.charset.Charset.defaultCharset()).length);
                    System.out.println(new String(new byte[] {65, 0, (byte) 0xf0, (byte) 0x9f, (byte) 0x98, (byte) 0x80, (byte) 0xed, (byte) 0xa0, (byte) 0x80}).length());
                    System.out.println(new String(new byte[] {0, (byte) 0xc3, (byte) 0xa9}, java.nio.charset.StandardCharsets.UTF_8).length());
                    System.out.println(java.nio.charset.Charset.defaultCharset().name());
                    System.out.println(java.nio.charset.StandardCharsets.UTF_8.displayName());
                    java.util.Random random = new java.security.SecureRandom();
                    byte[] randomBytes = new byte[0];
                    random.nextBytes(randomBytes);
                    System.out.println(randomBytes.length);
                    System.out.println(random.nextInt(1));
                    java.util.random.RandomGenerator generator = new java.security.SecureRandom();
                    generator.nextBytes(randomBytes);
                    System.out.println(generator.nextInt(1));
                    java.util.random.RandomGenerator custom = new CustomGenerator();
                    System.out.println(custom.nextLong());
                    byte[] customBytes = new byte[1]; custom.nextBytes(customBytes);
                    System.out.println((int) customBytes[0]);
                    System.out.println(custom.nextInt(5));
                    System.out.println("\\0\\ud800\\udc00".replace((char) 0xd800, (char) 1).charAt(1));
                    System.out.println(value.replace('z', 'q') == value);
                    System.out.println(nullReplace(null));
                    System.out.println("\\0\\ud800".concat("\\udc00").length());
                    System.out.println(nullConcat(null));
                    System.out.println(nullSubstring(null));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-string-codepoint-search"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-string-codepoint-search").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsCharacterWhitespaceInt() throws Exception {
        final Path project = tempDir.resolve("selfhost-character-whitespace-int");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int[] values = {-1, 0x09, 0x20, 0xa0, 0x2003, 0xd800, 0xdc00, 0x1f600, 0x110000};
                    for (int value : values) {
                        System.out.println(Character.isWhitespace(value));
                        System.out.println(Character.isSpaceChar(value));
                        System.out.println(Character.isSpaceChar((char) value));
                        System.out.println(Character.isValidCodePoint(value));
                        System.out.println(Character.isBmpCodePoint(value));
                        System.out.println(Character.isSupplementaryCodePoint(value));
                        System.out.println(Character.charCount(value));
                        System.out.println(Character.isISOControl(value));
                        char ch = (char) value;
                        System.out.println(Character.isISOControl(ch));
                        System.out.println(Character.isHighSurrogate(ch));
                        System.out.println(Character.isLowSurrogate(ch));
                        System.out.println(Character.isSurrogate(ch));
                        System.out.println(Character.isSurrogatePair(ch, (char) 0xdc00));
                        System.out.println(Character.toCodePoint(ch, (char) 0xdc00));
                        System.out.println((int) Character.highSurrogate(value));
                        System.out.println((int) Character.lowSurrogate(value));
                        System.out.println((int) Character.reverseBytes(ch));
                        System.out.println(Character.hashCode(ch));
                        System.out.println(Character.compare(ch, (char) 0xffff));
                    }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-character-whitespace-int"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-character-whitespace-int").toString()), Duration.ofSeconds(30));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsFloatingDecimalOutput() throws Exception {
        final Path project = tempDir.resolve("selfhost-floating-decimal-output");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Double.toString(1.0 / 3.0));
                    System.out.println(Float.toString(0.1f));
                    System.out.println(Double.toString(Double.MIN_VALUE));
                    System.out.println(Float.toString(Float.MIN_VALUE));
                    System.out.println(new StringBuilder().append(10000000.0).toString());
                    float value = -0.0f;
                    System.out.println("f=" + value);
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult expected = process(tempDir,
            List.of(CliTestHarness.currentJavaCommand(), "-cp", classes.toString(), "com.acme.Main"), Duration.ofSeconds(30));
        assertThat(expected.exitCode()).as(expected.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-floating-decimal-output"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-floating-decimal-output").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(expected.stdout());
    }

    @Test
    void nativeBuiltJavanBuildsIntegerBitOperations() throws Exception {
        final Path project = tempDir.resolve("selfhost-integer-bit-operations");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Integer.highestOneBit(-1));
                    System.out.println(Integer.lowestOneBit(Integer.MIN_VALUE));
                    System.out.println(Integer.reverse(1));
                    System.out.println(Integer.reverseBytes(0x12345678));
                    System.out.println(Integer.signum(-1));
                    System.out.println(Integer.rotateLeft(1, -1));
                    System.out.println(Integer.rotateRight(1, -1));
                    System.out.println(Long.highestOneBit(-1L));
                    System.out.println(Long.lowestOneBit(Long.MIN_VALUE));
                    System.out.println(Long.reverse(1L));
                    System.out.println(Long.reverseBytes(0x0102030405060708L));
                    System.out.println(Long.signum(-1L));
                    System.out.println(Long.rotateLeft(1L, -1));
                    System.out.println(Long.rotateRight(1L, -1));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-integer-bit-operations"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-integer-bit-operations").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("-2147483648\n-2147483648\n-2147483648\n2018915346\n-1\n-2147483648\n2\n"
            + "-9223372036854775808\n-9223372036854775808\n-9223372036854775808\n578437695752307201\n-1\n-9223372036854775808\n2\n");
    }

    @Test
    void nativeBuiltJavanBuildsIntegerBitCounts() throws Exception {
        final Path project = tempDir.resolve("selfhost-integer-bit-counts");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Integer.bitCount(-1));
                    System.out.println(Integer.numberOfLeadingZeros(0));
                    System.out.println(Integer.numberOfTrailingZeros(Integer.MIN_VALUE));
                    System.out.println(Long.bitCount(-1L));
                    System.out.println(Long.numberOfLeadingZeros(0L));
                    System.out.println(Long.numberOfTrailingZeros(Long.MIN_VALUE));
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-integer-bit-counts"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-integer-bit-counts").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("32\n32\n31\n64\n64\n63\n");
    }

    @Test
    void nativeBuiltJavanBuildsUnsignedIntegerArithmetic() throws Exception {
        final Path project = tempDir.resolve("selfhost-unsigned-integer");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Integer.toUnsignedLong(-1));
                    System.out.println(Integer.compareUnsigned(-1, 1));
                    System.out.println(Integer.divideUnsigned(-1, 3));
                    System.out.println(Integer.remainderUnsigned(-1, 2));
                    System.out.println(Long.divideUnsigned(-1L, 3L));
                    System.out.println(Long.remainderUnsigned(-1L, 2L));
                    try { System.out.println(Long.divideUnsigned(1L, 0L)); }
                    catch (ArithmeticException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-unsigned-integer"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-unsigned-integer").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("4294967295\n1\n1431655765\n1\n6148914691236517205\n1\n/ by zero\n");
    }

    @Test
    void nativeBuiltJavanBuildsMathClamp() throws Exception {
        final Path project = tempDir.resolve("selfhost-math-clamp");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Math.clamp(Long.MAX_VALUE, -2, 3));
                    System.out.println(StrictMath.clamp(Long.MIN_VALUE, -2L, 3L));
                    System.out.println(1.0 / Math.clamp(-0.0, 0.0, 1.0));
                    System.out.println(Float.floatToRawIntBits(StrictMath.clamp(0.0f, -1.0f, -0.0f)));
                    try { System.out.println(Math.clamp(1L, 2, 1)); }
                    catch (IllegalArgumentException expected) { System.out.println("invalid"); }
                }
            }
            """);
        final ProcessResult javac = process(tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()), Duration.ofSeconds(30));
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-math-clamp"), Duration.ofSeconds(120));
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(tempDir,
            List.of(project.resolve(".javan/bin/selfhost-math-clamp").toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("3\n-2\nInfinity\n-2147483648\ninvalid\n");
    }

    @Test
    void nativeBuiltJavanBuildsStrictMathDivision() throws Exception {
        final Path project = tempDir.resolve("selfhost-strict-math-division");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(StrictMath.floorDiv(-4, 3));
                    System.out.println(StrictMath.floorMod(-4, 3));
                    System.out.println(StrictMath.ceilDiv(4, 3));
                    System.out.println(StrictMath.ceilMod(4, 3));
                    System.out.println(StrictMath.divideExact(-4L, 3L));
                    System.out.println(StrictMath.floorDivExact(-4, 3));
                    System.out.println(StrictMath.ceilDivExact(-4L, 3L));
                    try { System.out.println(StrictMath.ceilDivExact(Integer.MIN_VALUE, -1)); }
                    catch (ArithmeticException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-strict-math-division"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-strict-math-division");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("-2\n2\n2\n-2\n-1\n-2\n-1\ninteger overflow\n");
    }

    @Test
    void nativeBuiltJavanBuildsMathExactDivision() throws Exception {
        final Path project = tempDir.resolve("selfhost-math-exact-division");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Math.divideExact(-4, 3));
                    System.out.println(Math.floorDivExact(-4L, 3L));
                    System.out.println(Math.ceilDivExact(-4, 3));
                    try { System.out.println(Math.divideExact(1L, 0L)); }
                    catch (ArithmeticException expected) { System.out.println(expected.getMessage()); }
                    try { System.out.println(Math.ceilDivExact(Integer.MIN_VALUE, -1)); }
                    catch (ArithmeticException expected) { System.out.println(expected.getMessage()); }
                    try { System.out.println(Math.floorDivExact(Long.MIN_VALUE, -1L)); }
                    catch (ArithmeticException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-math-exact-division"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-math-exact-division");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("-1\n-2\n-1\n/ by zero\ninteger overflow\nlong overflow\n");
    }

    @Test
    void nativeBuiltJavanBuildsMathCeilingOperations() throws Exception {
        final Path project = tempDir.resolve("selfhost-math-ceiling");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    System.out.println(Math.ceilDiv(4, 3));
                    System.out.println(Math.ceilMod(4, 3));
                    System.out.println(Math.ceilDiv(Long.MIN_VALUE, -1L));
                    System.out.println(Math.ceilMod(Long.MIN_VALUE, -1));
                    try { System.out.println(Math.ceilDiv(1, 0)); }
                    catch (ArithmeticException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-math-ceiling"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-math-ceiling");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("2\n-2\n-9223372036854775808\n0\n/ by zero\n");
    }

    @Test
    void nativeBuiltJavanBuildsSecureRandomNumbers() throws Exception {
        final Path project = tempDir.resolve("selfhost-secure-random-integers");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.security.SecureRandom;
            public final class Main {
                private static final SecureRandom RANDOM = new SecureRandom();
                private static SecureRandom EMPTY;
                static final class Derived extends SecureRandom {
                    static { System.out.println("unexpected-random-init"); }
                }
                static final class ThreadChild extends Thread {
                    static { System.out.println("unexpected-thread-init"); }
                }
                public static void main(final String[] args) {
                    final SecureRandom random = RANDOM;
                    final int value = random.nextInt(17);
                    final int wide = random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
                    final long longValue = random.nextLong(17L);
                    final long longWide = random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
                    final boolean bit = random.nextBoolean();
                    final float fraction = random.nextFloat();
                    final double doubleFraction = random.nextDouble();
                    final float narrow = random.nextFloat(Float.MIN_VALUE);
                    final double wideFraction = random.nextDouble(-Double.MAX_VALUE, Double.MAX_VALUE);
                    final byte[] seed = random.generateSeed(16);
                    final byte[] staticSeed = SecureRandom.getSeed(17);
                    final byte[] inheritedSeed = Derived.getSeed(0);
                    final boolean threadPresent = ThreadChild.currentThread() != null;
                    System.out.println(value >= 0 && value < 17 && wide < Integer.MAX_VALUE
                        && longValue >= 0 && longValue < 17 && longWide < Long.MAX_VALUE
                        && (bit || !bit) && fraction >= 0.0f && fraction < 1.0f
                        && doubleFraction >= 0.0 && doubleFraction < 1.0 && narrow == 0.0f
                        && wideFraction >= -Double.MAX_VALUE && wideFraction < Double.MAX_VALUE
                        && seed.length == 16 && staticSeed.length == 17 && seed != staticSeed
                        && inheritedSeed.length == 0 && threadPresent);
                    try { random.nextInt(0); }
                    catch (IllegalArgumentException expected) { System.out.println("invalid"); }
                    try { RANDOM.nextInt(1, 1); }
                    catch (IllegalArgumentException expected) { System.out.println("range-invalid"); }
                    try { random.nextLong(1L, 1L); }
                    catch (IllegalArgumentException expected) { System.out.println("long-range-invalid"); }
                    try { EMPTY.nextDouble(); }
                    catch (NullPointerException expected) { System.out.println("null-scalar"); }
                    try { random.nextDouble(Double.NaN); }
                    catch (IllegalArgumentException expected) { System.out.println("floating-invalid"); }
                    try { random.generateSeed(-1); }
                    catch (IllegalArgumentException expected) { System.out.println("seed-invalid"); }
                    try { SecureRandom.getSeed(-1); }
                    catch (IllegalArgumentException expected) { System.out.println("static-seed-invalid"); }
                    try { EMPTY.generateSeed(-1); }
                    catch (NullPointerException expected) { System.out.println("null-seed"); }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-secure-random-integers"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-secure-random-integers");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()),
            Duration.ofSeconds(30), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("true\ninvalid\nrange-invalid\nlong-range-invalid\nnull-scalar\nfloating-invalid\nseed-invalid\nstatic-seed-invalid\nnull-seed\n");
    }

    @Test
    void nativeBuiltJavanBuildsResourceNullCatch() throws Exception {
        final Path project = tempDir.resolve("selfhost-resource-null-catch");
        final Path classes = project.resolve("classes");
        Files.createDirectories(classes);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(final String[] args) {
                    try {
                        ClassLoader.getSystemResourceAsStream(null);
                    } catch (NullPointerException expected) {
                        System.out.println("null name");
                    }
                }
            }
            """);
        final ProcessResult javac = process(
            tempDir,
            List.of(CliTestHarness.currentJavacCommand(), "--release", "25", "-d", classes.toString(),
                project.resolve("src/main/java/com/acme/Main.java").toString()),
            Duration.ofSeconds(30)
        );
        assertThat(javac.exitCode()).as(javac.stderr()).isZero();
        final ProcessResult build = process(
            tempDir,
            List.of(primitiveLiteralBootstrap.toString(), "build", classes.toString(), "--main", "com.acme.Main",
                "--output", "selfhost-resource-null-catch"),
            Duration.ofSeconds(120)
        );
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path executable = project.resolve(".javan/bin/selfhost-resource-null-catch");
        final ProcessResult nativeRun = process(tempDir, List.of(executable.toString()));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo("null name\n");
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
    void nativeLibraryEmptyCollectionClearTransportsFailures() throws Exception {
        final Path project = project("library-empty-clear");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int clear(int mode) {
                    Collection<?> values = switch (mode) {
                        case 0 -> Collections.emptyList(); case 1 -> Collections.emptySet();
                        case 2 -> List.of(); case 3 -> Set.of(); case 4 -> null;
                        default -> new ArrayList<>();
                    };
                    values.clear();
                    return 42;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.clear");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_empty_clear.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-empty-clear.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 6; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_clear_int(mode, &value);
                        if ((mode < 2 || mode == 5) && (!result.ok || value != 42)) return 1;
                        if ((mode == 2 || mode == 3) && (result.ok || result.message == 0
                            || strstr(result.message, "UnsupportedOperationException") == 0)) return 2;
                        if (mode == 4 && (result.ok || result.message == 0
                            || strstr(result.message, "NullPointerException") == 0)) return 3;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-empty-clear");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-empty-clear.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibrarySingletonEmptyBulkFilteringTransportsFailures() throws Exception {
        final Path project = project("library-singleton-empty-filter");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int filter(int mode) {
                    Collection<String> values = mode < 3 ? Collections.singletonList("x") : Collections.singleton("x");
                    if (mode == 0 || mode == 3) return values.removeAll(List.of()) ? 1 : 0;
                    if (mode == 1 || mode == 4) return values.removeAll(null) ? 1 : 0;
                    return values.retainAll(List.of()) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.filter");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_singleton_empty_filter.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-singleton-empty-filter.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 6; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_filter_int(mode, &value);
                        if (mode % 3 == 0 && (!result.ok || value != 0)) return 1;
                        if (mode % 3 == 1 && (result.ok || result.message == 0
                            || strstr(result.message, "NullPointerException") == 0)) return 2;
                        if (mode % 3 == 2 && (result.ok || result.message == 0
                            || strstr(result.message, "UnsupportedOperationException") == 0)) return 3;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-singleton-empty-filter");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-singleton-empty-filter.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryEmptyCollectionBulkFilteringTransportsNullFailure() throws Exception {
        final Path project = project("library-empty-bulk-filter");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int filter(int mode) {
                    Collection<String> values = mode < 4 ? Collections.emptyList() : Collections.emptySet();
                    Collection<String> probe = mode % 4 < 2 ? List.of("x") : null;
                    if (mode % 2 == 0) return values.removeAll(probe) ? 1 : 0;
                    return values.retainAll(probe) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.filter");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_empty_bulk_filter.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-empty-bulk-filter.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 8; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_filter_int(mode, &value);
                        if (mode % 4 < 2 && (!result.ok || value != 0)) return 1;
                        if (mode % 4 >= 2 && (result.ok || result.message == 0
                            || strstr(result.message, "NullPointerException") == 0)) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-empty-bulk-filter");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-empty-bulk-filter.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryEmptyCollectionAddAllTransportsFailures() throws Exception {
        final Path project = project("library-empty-add-all");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int add(int mode) {
                    if (mode == 0) return Collections.emptyList().addAll(List.of()) ? 1 : 0;
                    if (mode == 1) return Collections.singleton("x").addAll(Set.of()) ? 1 : 0;
                    if (mode == 2) return Collections.emptySet().addAll(null) ? 1 : 0;
                    if (mode == 3) return Collections.emptyList().addAll(List.of("x")) ? 1 : 0;
                    if (mode == 4) return Collections.emptyList().addAll(1, null) ? 1 : 0;
                    return List.of().addAll(List.of()) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.add");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_empty_add_all.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-empty-add-all.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 6; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_add_int(mode, &value);
                        if (mode < 2 && (!result.ok || value != 0)) return 1;
                        if (mode >= 2 && (result.ok || result.message == 0)) return 2;
                        if (mode == 2 && strstr(result.message, "NullPointerException") == 0) return 3;
                        if ((mode == 3 || mode == 5) && strstr(result.message, "UnsupportedOperationException") == 0) return 4;
                        if (mode == 4 && strstr(result.message, "IndexOutOfBoundsException") == 0) return 5;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-empty-add-all");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-empty-add-all.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryListBulkRemovalTransportsNullProbeFailure() throws Exception {
        final Path project = project("library-list-filter-null");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static int state(List<String> values) {
                    return values.size() == 2 && values.get(0) == null && "z".equals(values.get(1)) ? 7 : -1;
                }
                static int remove(List<String> values, Collection<String> probes) {
                    try { values.removeAll(probes); } catch (NullPointerException expected) { return state(values); }
                    return -2;
                }
                static int retain(List<String> values, Collection<String> probes) {
                    try { values.retainAll(probes); } catch (NullPointerException expected) { return state(values); }
                    return -2;
                }
                public static int filter(int mode) {
                    List<String> values = new ArrayList<>(); values.add("x"); values.add(null); values.add("z");
                    if (mode == 0) return values.removeAll(List.of("x")) ? 1 : 0;
                    if (mode == 3) return values.removeAll(Collections.singletonList(null)) ? 1 : 0;
                    if (mode == 1) return remove(values, List.of("x"));
                    return retain(values, Set.of("z"));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.filter");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_filter_null.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-list-filter-null.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_filter_int(mode, &value);
                        if (mode == 0 && (result.ok || result.message == 0
                            || strstr(result.message, "NullPointerException") == 0)) return 1;
                        if (mode > 0 && (!result.ok || value != (mode == 3 ? 1 : 7))) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-filter-null");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-list-filter-null.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryEmptyAndSingletonListRemovalMatchesJvm() throws Exception {
        final Path project = project("library-singleton-list-remove");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) {
                        if (id < 0) throw new Failure();
                        return other instanceof Key && id == ((Key) other).id;
                    }
                    public int hashCode() { return id; }
                }
                public static int remove(int mode) {
                    List<Key> values = mode == 0 ? Collections.emptyList() : Collections.singletonList(new Key(7));
                    Key query = new Key(mode == 1 ? 3 : mode == 2 ? 7 : -1);
                    return values.remove(query) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.remove");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_singleton_remove.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-singleton-list-remove.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_remove_int(mode, &value);
                        if (mode < 2 && (!result.ok || value != 0)) return 1;
                        if (mode == 2 && (result.ok || result.message == 0
                            || strstr(result.message, "UnsupportedOperationException") == 0)) return 2;
                        if (mode == 3 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 3;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-singleton-remove");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-singleton-list-remove.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibrarySingletonSetContainsUsesApplicationEquality() throws Exception {
        final Path project = project("library-singleton-set-contains");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) {
                        if (id < 0) throw new Failure();
                        return other instanceof Key && id == ((Key) other).id;
                    }
                    public int hashCode() { return id; }
                }
                public static int contains(int mode) {
                    Set<Key> values = mode == 0 ? Collections.emptySet() : Collections.singleton(new Key(7));
                    if (mode == 2) values = Collections.unmodifiableSet(values);
                    Key query = new Key(mode == 1 ? 3 : mode == 2 ? 7 : -1);
                    return values.contains(query) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.contains");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_singleton_contains.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-singleton-set-contains.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_contains_int(mode, &value);
                        if (mode < 3 && (!result.ok || value != (mode == 2 ? 1 : 0))) return 1;
                        if (mode == 3 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-singleton-contains");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-singleton-set-contains.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibrarySingletonSetContainsAllUsesApplicationEquality() throws Exception {
        final Path project = project("library-singleton-set-contains-all");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) {
                        if (id < 0) throw new Failure();
                        return other instanceof Key && id == ((Key) other).id;
                    }
                    public int hashCode() { return id; }
                }
                public static int containsAll(int mode) {
                    Set<Key> values = mode == 0 ? Collections.emptySet() : Collections.singleton(new Key(7));
                    if (mode == 2) values = Collections.unmodifiableSet(values);
                    Key query = new Key(mode == 1 ? 3 : mode == 2 ? 7 : -1);
                    return values.containsAll(List.of(query)) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.containsAll");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_singleton_contains_all.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-singleton-set-contains-all.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_containsAll_int(mode, &value);
                        if (mode < 3 && (!result.ok || value != (mode == 2 ? 1 : 0))) return 1;
                        if (mode == 3 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-singleton-contains-all");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-singleton-set-contains-all.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryEmptyAndSingletonSetRemovalMatchesJvm() throws Exception {
        final Path project = project("library-singleton-set-remove");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) {
                        if (id < 0) throw new Failure();
                        return other instanceof Key && id == ((Key) other).id;
                    }
                    public int hashCode() { return id; }
                }
                public static int remove(int mode) {
                    Set<Key> values = mode == 0 ? Collections.emptySet() : Collections.singleton(new Key(7));
                    Key query = new Key(mode == 1 ? 3 : mode == 2 ? 7 : -1);
                    return values.remove(query) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.remove");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_singleton_remove.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-singleton-set-remove.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_remove_int(mode, &value);
                        if (mode < 2 && (!result.ok || value != 0)) return 1;
                        if (mode == 2 && (result.ok || result.message == 0
                            || strstr(result.message, "UnsupportedOperationException") == 0)) return 2;
                        if (mode == 3 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 3;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-singleton-remove");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-singleton-set-remove.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryListBulkFilteringUsesApplicationEquality() throws Exception {
        final Path project = project("library-list-filter-equality");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) { return other instanceof Key && id == ((Key) other).id; }
                    public int hashCode() { return id; }
                }
                static final class Bomb extends Key {
                    Bomb(int id) { super(id); }
                    public boolean equals(Object other) { throw new Failure(); }
                }
                static int state(List<Key> values) {
                    return values.size() == 2 && values.get(0).id == 2 && values.get(1).id == 3 ? 7 : -1;
                }
                static int remove(List<Key> values, Collection<Key> probes) {
                    try { values.removeAll(probes); } catch (Failure expected) { return state(values); }
                    return -2;
                }
                static int retain(List<Key> values, Collection<Key> probes) {
                    try { values.retainAll(probes); } catch (Failure expected) { return state(values); }
                    return -2;
                }
                public static int filter(int mode) {
                    List<Key> values = new ArrayList<>(); values.add(new Key(1)); values.add(new Bomb(2)); values.add(new Key(3));
                    if (mode == 0) return values.removeAll(List.of(new Key(1))) ? 1 : 0;
                    if (mode == 1) return remove(values, List.of(new Key(1)));
                    if (mode == 2) return retain(values, List.of(new Key(2)));
                    return values.removeAll(List.of()) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.filter");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_filter_equality.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-list-filter-equality.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_filter_int(mode, &value);
                        if (mode == 0 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 1;
                        if (mode > 0 && (!result.ok || value != (mode == 3 ? 0 : 7))) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-filter-equality");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-list-filter-equality.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryListObjectRemovalUsesApplicationEquality() throws Exception {
        final Path project = project("library-list-remove-equality");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static final class Key {
                    final int id; Key(int id) { this.id = id; }
                    public boolean equals(Object other) {
                        if (id < 0) throw new Failure();
                        return other instanceof Key && id == ((Key) other).id;
                    }
                    public int hashCode() { return id; }
                }
                public static int remove(int id) {
                    List<Key> values = new ArrayList<>(); values.add(new Key(7));
                    return values.remove(new Key(id)) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.remove");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_remove_equality.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-list-remove-equality.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int id = -1; id <= 7; id += 4) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_remove_int(id, &value);
                        if (id < 0 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 1;
                        if (id >= 0 && (!result.ok || value != (id == 7 ? 1 : 0))) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-remove-equality");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-list-remove-equality.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryListContainsAllUsesApplicationEquality() throws Exception {
        final Path project = project("library-contains-all-equality");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Failure extends RuntimeException { }
                static final class Key {
                    final int id;
                    Key(int id) { this.id = id; }
                    public boolean equals(Object other) {
                        if (id < 0) throw new Failure();
                        return other instanceof Key && id == ((Key) other).id;
                    }
                    public int hashCode() { return id; }
                }
                public static int search(int id) {
                    List<Key> values = List.of(new Key(7));
                    return values.containsAll(List.of(new Key(id))) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.search");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_contains_all_equality.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-contains-all-equality.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int id = -1; id <= 7; id += 4) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_search_int(id, &value);
                        if (id < 0 && (result.ok || result.message == 0 || strstr(result.message, "Failure") == 0)) return 1;
                        if (id >= 0 && (!result.ok || value != (id == 7 ? 1 : 0))) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-contains-all-equality");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-contains-all-equality.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryImmutableContainsAllTransportsNullFailure() throws Exception {
        final Path project = project("library-contains-all-null");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int search(int kind) {
                    Collection<String> values = kind == 2 ? Collections.singletonList(null) : List.of("x");
                    List<String> probes = new ArrayList<>();
                    if (kind == 1) probes.add("missing");
                    probes.add(null);
                    return values.containsAll(probes) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.search");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_contains_all_null.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-contains-all-null.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int kind = 0; kind < 3; kind++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_search_int(kind, &value);
                        if (kind == 0 && (result.ok || result.message == 0
                            || strstr(result.message, "NullPointerException") == 0)) return 1;
                        if (kind > 0 && (!result.ok || value != (kind == 2 ? 1 : 0))) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-contains-all-null");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-contains-all-null.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryImmutableMapSearchRejectsNull() throws Exception {
        final Path project = project("library-map-null-search");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int search(int kind, int mode) {
                    Map<String, String> values;
                    if (kind == 0) values = Collections.unmodifiableMap(Map.copyOf(Map.of("x", "y")));
                    else values = Collections.singletonMap(null, null);
                    if (mode == 0) return values.containsKey(null) ? 1 : 0;
                    if (mode == 1) return values.containsValue(null) ? 1 : 0;
                    if (mode == 2) return values.get(null) == null ? 1 : 0;
                    return values.getOrDefault(null, "default") == null ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.search");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_map_null.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-map-null-search.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 4; mode++) {
                        for (int kind = 0; kind < 2; kind++) {
                            int value = -1;
                            JavanResult result = javan_try_com_acme_Api_search_int_int(kind, mode, &value);
                            if (kind == 0 && (result.ok || result.message == 0
                                || strstr(result.message, "NullPointerException") == 0)) return 1;
                            if (kind == 1 && (!result.ok || value != 1)) return 2;
                            javan_result_free(&result);
                        }
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-map-null");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-map-null-search.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
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
    void nativeLibraryImmutableSetSearchRejectsNull() throws Exception {
        final Path project = project("library-set-null-search");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static boolean search(int kind) {
                    Set<String> values;
                    if (kind == 0) values = Set.of();
                    else if (kind == 1) values = Collections.unmodifiableSet(Set.copyOf(Set.of("x")));
                    else values = Collections.singleton(null);
                    return values.contains(null);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.search");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_set_null.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-set-null-search.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int kind = 0; kind < 3; kind++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_search_int(kind, &value);
                        if (kind < 2 && (result.ok || result.message == 0
                            || strstr(result.message, "NullPointerException") == 0)) return 1;
                        if (kind == 2 && (!result.ok || value != 1)) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-set-null");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-set-null-search.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryImmutableListSearchRejectsNull() throws Exception {
        final Path project = project("library-list-null-search");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                public static int search(int kind, int mode) {
                    List<String> values;
                    if (kind == 0) values = List.of("x");
                    else values = Collections.singletonList(null);
                    if (mode == 0) return values.contains(null) ? 1 : 0;
                    if (mode == 1) return values.indexOf(null);
                    return values.lastIndexOf(null);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.search");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_list_null.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-list-null-search.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 3; mode++) {
                        for (int kind = 0; kind < 2; kind++) {
                            int value = -1;
                            JavanResult result = javan_try_com_acme_Api_search_int_int(kind, mode, &value);
                            if (kind == 0 && (result.ok || result.message == 0
                                || strstr(result.message, "NullPointerException") == 0)) return 1;
                            if (kind == 1 && (!result.ok || value != (mode == 0 ? 1 : 0))) return 2;
                            javan_result_free(&result);
                        }
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-list-null");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-list-null-search.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryListIndexSearchUsesApplicationEquality() throws Exception {
        final Path project = project("library-list-index-equality");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.*;
            public final class Api {
                static final class Key {
                    final int value;
                    Key(int value) { this.value = value; }
                    public boolean equals(Object other) {
                        if (value == 9) throw new IllegalArgumentException("equality");
                        return other instanceof Key && value == ((Key) other).value;
                    }
                    public int hashCode() { return value; }
                }
                public static int search(int value) {
                    List<Object> values = new ArrayList<>();
                    values.add(new Key(7)); values.add(new Key(8)); values.add(new Key(7));
                    Key query = new Key(value);
                    return (values.indexOf(query) + 1) * 10 + values.lastIndexOf(query) + 1;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.search");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_list_index.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-list-index-equality.h"
            int main(void) {
                const int queries[] = { 7, 8, 4, 9 };
                const int expected[] = { 13, 22, 0, 0 };
                for (int i = 0; i < 100; i++) {
                    for (int q = 0; q < 4; q++) {
                        int value = -1;
                        JavanResult result = javan_try_com_acme_Api_search_int(queries[q], &value);
                        if (q != 3 && (!result.ok || value != expected[q])) return 1;
                        if (q == 3 && (result.ok || result.message == 0
                            || strstr(result.message, "IllegalArgumentException") == 0)) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-list-index");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-list-index-equality.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryListContainsUsesApplicationEquality() throws Exception {
        final Path project = project("library-list-equality");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.List;
            public final class Api {
                static final class Key {
                    final int value;
                    Key(int value) { this.value = value; }
                    public boolean equals(Object other) {
                        return other instanceof Key && value == ((Key) other).value;
                    }
                    public int hashCode() { return value; }
                }
                static final class Throwing {
                    public boolean equals(Object other) { throw new IllegalArgumentException("equality"); }
                }
                public static Object key(int value) { return new Key(value); }
                public static Object throwing() { return new Throwing(); }
                public static int contains(Object query) {
                    List<Object> values = new ArrayList<>();
                    values.add(new Key(7));
                    return values.contains(query) ? 1 : 0;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.key(int):object", "--export", "com.acme.Api.throwing():object",
            "--export", "com.acme.Api.contains(object):int");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_list_equality.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-list-equality.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int query = 7; query <= 8; query++) {
                        int value = -1;
                        JavanObjectHandle *key = 0;
                        JavanResult result = javan_try_com_acme_Api_key_int(query, &key);
                        if (!result.ok || key == 0) return 1;
                        javan_result_free(&result);
                        result = javan_try_com_acme_Api_contains_object(key, &value);
                        if (!result.ok || value != (query == 7)) return 2;
                        javan_result_free(&result);
                        javan_object_handle_release(key);
                    }
                }
                JavanObjectHandle *throwing = 0;
                JavanResult result = javan_try_com_acme_Api_throwing_void(&throwing);
                if (!result.ok || throwing == 0) return 3;
                javan_result_free(&result);
                int value = -1;
                result = javan_try_com_acme_Api_contains_object(throwing, &value);
                if (result.ok || result.message == 0 || strstr(result.message, "IllegalArgumentException") == 0) return 4;
                javan_result_free(&result);
                javan_object_handle_release(throwing);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-list-equality");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-list-equality.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(30),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryFileReadsAndFailuresSurviveCollection() throws Exception {
        final Path project = project("library-file-read");
        Files.writeString(project.resolve("data.bin"), "ABC", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.nio.file.Files;
            import java.nio.file.Path;
            public final class Api {
                public static int read(int mode) throws Exception {
                    Path path = Path.of(mode == 2 ? "missing.bin" : "data.bin");
                    if (mode == 0) return Files.readAllBytes(path).length;
                    return Files.readString(path).length();
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Api.read");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_file_read.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-file-read.h"
            int main(void) {
                for (int i = 0; i < 100; i++) {
                    for (int mode = 0; mode < 3; mode++) {
                        int value = 0;
                        JavanResult result = javan_try_com_acme_Api_read_int(mode, &value);
                        if (mode < 2 && (!result.ok || value != 3)) return 1;
                        if (mode == 2 && (result.ok || result.message == 0
                            || strstr(result.message, "IOException") == 0)) return 2;
                        javan_result_free(&result);
                    }
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("call-file-read");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-file-read.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(30),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void nativeLibraryFailedClassInitializationStaysFailedAcrossExports() throws Exception {
        assertFailedClassInitialization(-1, "java/lang/ExceptionInInitializerError");
    }

    @Test
    void nativeLibraryPanickingClassInitializationStaysFailedAcrossExports() throws Exception {
        assertFailedClassInitialization(65_536, "out of memory");
    }

    private void assertFailedClassInitialization(final int length, final String failure) throws Exception {
        final Path project = project("library-failed-initialization");
        writeJava(project, "com.acme.Broken", """
            package com.acme;

            public final class Broken {
                private static final int VALUE = new int[ARRAY_LENGTH].length;

                public static int read() {
                    System.out.println("entered-failed-class");
                    return VALUE;
                }
            }
            """.replace("ARRAY_LENGTH", Integer.toString(length)));

        final CliRun run = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Broken.read");
        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path caller = writeC(project, "call_initialization.c", """
            #include <stdio.h>
            #include <string.h>
            #include ".javan/dist/bindings/c/library-failed-initialization.h"

            int main(void) {
                int value = javan_export_com_acme_Broken_read_void();
                const char* summary = javan_last_error();
                printf("first:%d:%d\\n", value,
                    summary != NULL && strstr(summary, "EXPECTED_FAILURE") != NULL);
                javan_clear_error();
                for (int attempt = 0; attempt < 3; attempt++) {
                    value = 42;
                    JavanResult result = javan_try_com_acme_Broken_read_void(&value);
                    printf("repeat:%d:%d:%d\\n", result.ok, value,
                        result.summary != NULL && strstr(result.summary, "java/lang/NoClassDefFoundError") != NULL);
                    javan_result_free(&result);
                    javan_clear_error();
                }
                return 0;
            }
            """.replace("EXPECTED_FAILURE", failure));
        final Path binary = project.resolve("call-initialization");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-failed-initialization.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(binary.toString()), Duration.ofSeconds(10),
            Map.of("JAVAN_HEAP_LIMIT_BYTES", "8192"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stderr()).isEmpty();
        assertThat(nativeRun.stdout()).isEqualTo("""
            first:0:1
            repeat:0:0:1
            repeat:0:0:1
            repeat:0:0:1
            """);
    }

    @Test
    void nativeLibraryConcurrentInitializationFailureReleasesBothCallers() throws Exception {
        final Path project = project("library-concurrent-initialization");
        writeJava(project, "com.acme.Warmup", """
            package com.acme;
            public final class Warmup { public static int ping() { return 1; } }
            """);
        writeJava(project, "com.acme.Broken", """
            package com.acme;
            public final class Broken {
                private static final int VALUE = new int[-1].length;
                public static int read() {
                    System.out.println("entered-failed-class");
                    return VALUE;
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--export", "com.acme.Warmup.ping", "--export", "com.acme.Broken.read");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_concurrent.c", """
            #include <pthread.h>
            #include <stdint.h>
            #include <stdio.h>
            #include <string.h>
            #include ".javan/dist/bindings/c/library-concurrent-initialization.h"

            void javan_thread_detach_current(void);
            void javan_gc_collect(void);
            unsigned long javan_heap_live_allocations(void);
            int javan_heap_root_frame_depth(void);
            int javan_heap_frame_root_count(void);
            static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
            static pthread_cond_t changed = PTHREAD_COND_INITIALIZER;
            static int ready = 0;
            static int released = 0;

            static void* call(void* unused) {
                (void) unused;
                pthread_mutex_lock(&lock);
                ready++;
                pthread_cond_broadcast(&changed);
                while (!released) pthread_cond_wait(&changed, &lock);
                pthread_mutex_unlock(&lock);
                int value = 42;
                JavanResult result = javan_try_com_acme_Broken_read_void(&value);
                intptr_t outcome = 0;
                if (!result.ok && value == 0 && result.summary != NULL) {
                    if (strstr(result.summary, "ExceptionInInitializerError")) outcome = 1;
                    if (strstr(result.summary, "NoClassDefFoundError")) outcome = 2;
                }
                javan_result_free(&result);
                javan_thread_detach_current();
                return (void*) outcome;
            }

            int main(void) {
                if (javan_export_com_acme_Warmup_ping_void() != 1) return 1;
                pthread_t first, second;
                if (pthread_create(&first, NULL, call, NULL) != 0
                    || pthread_create(&second, NULL, call, NULL) != 0) return 2;
                pthread_mutex_lock(&lock);
                while (ready != 2) pthread_cond_wait(&changed, &lock);
                released = 1;
                pthread_cond_broadcast(&changed);
                pthread_mutex_unlock(&lock);
                void* first_result = NULL;
                void* second_result = NULL;
                if (pthread_join(first, &first_result) != 0 || pthread_join(second, &second_result) != 0) return 3;
                if ((intptr_t) first_result + (intptr_t) second_result != 3
                    || (intptr_t) first_result * (intptr_t) second_result != 2) return 4;
                javan_thread_detach_current();
                javan_gc_collect();
                if (javan_heap_live_allocations() != 0 || javan_heap_root_frame_depth() != 0
                    || javan_heap_frame_root_count() != 0) return 5;
                pthread_cond_destroy(&changed);
                pthread_mutex_destroy(&lock);
                puts("one-initializer-error:one-class-failed:no-leaks");
                return 0;
            }
            """);
        final Path binary = project.resolve("call-concurrent");
        final ProcessResult compile = process(project, List.of("cc", "-pthread", caller.toString(),
            project.resolve(".javan/dist/liblibrary-concurrent-initialization.a").toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(binary.toString()), Duration.ofSeconds(10));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stderr()).isEmpty();
        assertThat(nativeRun.stdout()).isEqualTo("one-initializer-error:one-class-failed:no-leaks\n");
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
    void staticLibraryFloatingFormattingPreservesCallerEnvironment() throws Exception {
        final Path project = project("library-floating-environment");
        writeJava(project, "com.acme.Text", """
            package com.acme;
            public final class Text {
                public static String render(double value) { return Double.toString(value); }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Text.render");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_environment.c", """
            #include <errno.h>
            #include <fenv.h>
            #include <string.h>
            #include ".javan/dist/bindings/c/library-floating-environment.h"
            int main(void) {
                int modes[] = { FE_TONEAREST, FE_DOWNWARD, FE_UPWARD, FE_TOWARDZERO };
                for (unsigned i = 0; i < sizeof(modes) / sizeof(modes[0]); i++) {
                    if (fesetround(modes[i]) || feclearexcept(FE_ALL_EXCEPT) || feraiseexcept(FE_INVALID)) return 1;
                    int flags = fetestexcept(FE_ALL_EXCEPT);
                    errno = EDOM;
                    char* text = javan_export_com_acme_Text_render_double(0x1.5555555555555p-2);
                    if (!text || strcmp(text, "0.3333333333333333") || fegetround() != modes[i]
                        || fetestexcept(FE_ALL_EXCEPT) != flags || errno != EDOM) return 2;
                    javan_free(text);
                }
                return 0;
            }
            """);
        final Path binary = project.resolve("environment");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(),
            project.resolve(".javan/dist/liblibrary-floating-environment.a").toString(), "-o", binary.toString(), "-lm"));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void staticLibraryUtf16StringsPreserveEveryCodeUnitAlongsideUtf8Abi() throws Exception {
        final Path project = project("library-utf16");
        writeJava(project, "com.acme.Text", """
            package com.acme;
            public final class Text {
                public static String echo(String value) { return value; }
                public static int length(String value) { return value == null ? -1 : value.length(); }
                public static int strictLength(String value) { return value.length(); }
                public static int strictHash(String value) { return value.hashCode(); }
                public static int strictEmpty(String value) { return value.isEmpty() ? 1 : 0; }
                public static int strictBlank(String value) { return value.isBlank() ? 1 : 0; }
                public static int literalCharAt(int index) { return "\\0\\ud800\\udc00\\udfff".charAt(index); }
                public static int literalCodePointAt(int index) { return "\\0\\ud800\\udc00\\udfff".codePointAt(index); }
                public static int literalCodePointBefore(int index) { return "\\0\\ud800\\udc00\\udfff".codePointBefore(index); }
                public static int literalCodePointCount(int begin) { return "\\0\\ud800\\udc00\\udfff".codePointCount(begin, 4); }
                public static int literalCodePointOffset(int index) { return "\\0\\ud800\\udc00\\udfff".offsetByCodePoints(index, 1); }
                public static int search(String value, int point) { return value.indexOf(point); }
                public static int lastSearch(String value, int point, int from) { return value.lastIndexOf(point, from); }
                public static int substringFirst(String value, String needle) { return value.indexOf(needle); }
                public static int substringLast(String value, String needle, int from) { return value.lastIndexOf(needle, from); }
                public static int boundedPoint(String value, int point, int begin, int end) { return value.indexOf(point, begin, end); }
                public static int boundedString(String value, String needle, int begin, int end) { return value.indexOf(needle, begin, end); }
                public static String slice(String value, int begin, int end) { return value.substring(begin, end); }
                public static String tail(String value, int begin) { return value.substring(begin); }
                public static String sequence(String value, int begin, int end) { return (String) value.subSequence(begin, end); }
                public static int prefix(String value, String prefix, int from) { return value.startsWith(prefix, from) ? 1 : 0; }
                public static int suffix(String value, String suffix) { return value.endsWith(suffix) ? 1 : 0; }
                public static int same(String value, String other) { return value.equals(other) ? 1 : 0; }
                public static int contains(String value, String needle) { return value.contains(needle) ? 1 : 0; }
                static final class Needle implements CharSequence {
                    public int length() { return 2; }
                    public char charAt(int index) { return 'x'; }
                    public CharSequence subSequence(int begin, int end) { return this; }
                    public String toString() { return new String(new char[] {(char) 0xd83d, (char) 0xde00}); }
                }
                public static int containsCustom(String value) { return value.contains(new Needle()) ? 1 : 0; }
                public static String replaceChar(String value, int oldValue, int newValue) { return value.replace((char) oldValue, (char) newValue); }
                public static String replaceSequence(String value, String target, String replacement) { return value.replace(target, replacement); }
                public static byte[] defaultBytes(String value) { return value.getBytes(); }
                public static String fromDefaultBytes(byte[] value) { return new String(value); }
                public static String fromExplicitBytes(byte[] value) { return new String(value, 0, value.length, java.nio.charset.StandardCharsets.UTF_8); }
                public static String charsetName() { return java.nio.charset.Charset.defaultCharset().name(); }
                public static String join(String value, String other) { return value.concat(other); }
                public static String fail(String value) { throw new IllegalArgumentException(value); }
                public static String literal() { return "a\\0b"; }
                public static String fromCodePoint(int value) { return Character.toString(value); }
                public static String copy(String value) { return new String(value.toCharArray()); }
                public static String repeat(String value, int count) { return value.repeat(count); }
                public static String getChars(String value, int begin, int end, int offset) {
                    char[] chars = {'a','b','c','d','e','f','g','h','i'};
                    value.getChars(begin, end, chars, offset);
                    return new String(chars);
                }
            }
            """);
        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Text.echo", "--export", "com.acme.Text.length", "--export", "com.acme.Text.fail",
            "--export", "com.acme.Text.literal", "--export", "com.acme.Text.fromCodePoint", "--export", "com.acme.Text.copy",
            "--export", "com.acme.Text.repeat", "--export", "com.acme.Text.strictLength", "--export", "com.acme.Text.getChars",
            "--export", "com.acme.Text.strictHash", "--export", "com.acme.Text.strictEmpty", "--export", "com.acme.Text.strictBlank",
            "--export", "com.acme.Text.literalCharAt", "--export", "com.acme.Text.literalCodePointAt",
            "--export", "com.acme.Text.literalCodePointBefore", "--export", "com.acme.Text.literalCodePointCount",
            "--export", "com.acme.Text.literalCodePointOffset", "--export", "com.acme.Text.search",
            "--export", "com.acme.Text.lastSearch", "--export", "com.acme.Text.substringFirst",
            "--export", "com.acme.Text.substringLast", "--export", "com.acme.Text.boundedPoint",
            "--export", "com.acme.Text.boundedString", "--export", "com.acme.Text.slice", "--export", "com.acme.Text.tail",
            "--export", "com.acme.Text.sequence", "--export", "com.acme.Text.prefix", "--export", "com.acme.Text.suffix",
            "--export", "com.acme.Text.same", "--export", "com.acme.Text.contains", "--export", "com.acme.Text.containsCustom",
            "--export", "com.acme.Text.replaceChar", "--export", "com.acme.Text.replaceSequence", "--export", "com.acme.Text.defaultBytes", "--export", "com.acme.Text.fromDefaultBytes", "--export", "com.acme.Text.fromExplicitBytes", "--export", "com.acme.Text.charsetName", "--export", "com.acme.Text.join");
        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-utf16.a");
        final Path caller = writeC(project, "call_utf16.c", """
            #include <stdint.h>
            #include <string.h>
            #include <limits.h>
            #include ".javan/dist/bindings/c/library-utf16.h"
            int main(void) {
                uint16_t data[] = { 'A', 0, 0xD83D, 0xDE00, 0xD800, 'Z', 0xDC00 };
                JavanUtf16View input = { data, 7 };
                JavanUtf16String output = { 0 };
                JavanResult result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                if (!result.ok || output.length != 7 || memcmp(data, output.data, sizeof(data))) return 1;
                javan_result_free(&result);
                javan_free(output.data);
                const int literal_units[] = {0, 0xd800, 0xdc00, 0xdfff};
                for (int i = 0; i < 4; i++) {
                    int character = -1;
                    result = javan_try_com_acme_Text_literalCharAt_int(i, &character);
                    if (!result.ok || character != literal_units[i]) return 36;
                    javan_result_free(&result);
                }
                int invalid_character = -1;
                result = javan_try_com_acme_Text_literalCharAt_int(4, &invalid_character);
                if (result.ok || invalid_character != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/StringIndexOutOfBoundsException") == NULL) return 37;
                javan_result_free(&result);
                int search = -1;
                result = javan_utf16_try_com_acme_Text_search_string_int(input, 0, &search);
                if (!result.ok || search != 1) return 43;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_lastSearch_string_int_int(input, 0x1f600, 6, &search);
                if (!result.ok || search != 2) return 44;
                javan_result_free(&result);
                JavanUtf16View needle = { data + 2, 2 };
                result = javan_utf16_try_com_acme_Text_substringFirst_string_string(input, needle, &search);
                if (!result.ok || search != 2) return 46;
                javan_result_free(&result);
                needle.data = data + 6; needle.length = 1;
                result = javan_utf16_try_com_acme_Text_substringLast_string_string_int(input, needle, 6, &search);
                if (!result.ok || search != 6) return 47;
                javan_result_free(&result);
                needle.data = NULL; needle.length = 0;
                result = javan_utf16_try_com_acme_Text_substringFirst_string_string(input, needle, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 48;
                javan_result_free(&result);
                needle.data = data + 2; needle.length = 2;
                result = javan_utf16_try_com_acme_Text_boundedPoint_string_int_int_int(input, 0x1f600, 2, 4, &search);
                if (!result.ok || search != 2) return 50;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_boundedPoint_string_int_int_int(input, 0x1f600, 2, 3, &search);
                if (!result.ok || search != -1) return 51;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_boundedString_string_string_int_int(input, needle, 1, 4, &search);
                if (!result.ok || search != 2) return 52;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_boundedString_string_string_int_int(input, needle, -1, 4, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/StringIndexOutOfBoundsException") == NULL) return 53;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_slice_string_int_int(input, 1, 5, &output);
                if (!result.ok || output.length != 4 || memcmp(data + 1, output.data, 4 * sizeof(uint16_t))) return 54;
                javan_result_free(&result); javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_tail_string_int(input, 3, &output);
                if (!result.ok || output.length != 4 || memcmp(data + 3, output.data, 4 * sizeof(uint16_t))) return 55;
                javan_result_free(&result); javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_slice_string_int_int(input, -1, 2, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "StringIndexOutOfBoundsException") == NULL) return 56;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_sequence_string_int_int(input, 2, 5, &output);
                if (!result.ok || output.length != 3 || memcmp(data + 2, output.data, 3 * sizeof(uint16_t))) return 58;
                javan_result_free(&result); javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_sequence_string_int_int(input, 4, 2, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "StringIndexOutOfBoundsException") == NULL) return 59;
                javan_result_free(&result);
                needle.data = data + 2; needle.length = 2;
                result = javan_utf16_try_com_acme_Text_prefix_string_string_int(input, needle, 2, &search);
                if (!result.ok || search != 1) return 60;
                javan_result_free(&result);
                needle.data = data + 6; needle.length = 1;
                result = javan_utf16_try_com_acme_Text_suffix_string_string(input, needle, &search);
                if (!result.ok || search != 1) return 61;
                javan_result_free(&result);
                needle.data = NULL; needle.length = 0;
                result = javan_utf16_try_com_acme_Text_prefix_string_string_int(input, needle, -1, &search);
                if (!result.ok || search != 0) return 62;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_suffix_string_string(input, needle, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 63;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_same_string_string(input, input, &search);
                if (!result.ok || search != 1) return 64;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_same_string_string(input, needle, &search);
                if (!result.ok || search != 0) return 65;
                javan_result_free(&result);
                needle.data = data + 2; needle.length = 2;
                result = javan_utf16_try_com_acme_Text_contains_string_string(input, needle, &search);
                if (!result.ok || search != 1) return 67;
                javan_result_free(&result);
                needle.data = NULL; needle.length = 0;
                result = javan_utf16_try_com_acme_Text_contains_string_string(input, needle, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 68;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_containsCustom_string(input, &search);
                if (!result.ok || search != 1) return 70;
                javan_result_free(&result);
                JavanByteArray encoded = { 0 };
                int8_t expected_bytes[] = { 'A', 0, (int8_t) 0xf0, (int8_t) 0x9f, (int8_t) 0x98, (int8_t) 0x80, '?', 'Z', '?' };
                int8_t bytes_input[] = { 'A', 0, (int8_t) 0xf0, (int8_t) 0x9f, (int8_t) 0x98, (int8_t) 0x80, (int8_t) 0xed, (int8_t) 0xa0, (int8_t) 0x80 };
                JavanByteArray byte_view = { bytes_input, 9 };
                uint16_t decoded[] = { 'A', 0, 0xd83d, 0xde00, 0xfffd };
                uint16_t charset_name[] = {'U', 'T', 'F', '-', '8'};
                result = javan_utf16_try_com_acme_Text_charsetName_void(&output);
                if (!result.ok || output.length != 5 || memcmp(charset_name, output.data, sizeof(charset_name))) return 79;
                javan_result_free(&result); javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_fromDefaultBytes_bytes(byte_view, &output);
                if (!result.ok || output.length != 5 || memcmp(decoded, output.data, sizeof(decoded))) return 77;
                javan_result_free(&result); javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_fromExplicitBytes_bytes(byte_view, &output);
                if (!result.ok || output.length != 5 || memcmp(decoded, output.data, sizeof(decoded))) return 78;
                javan_result_free(&result); javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_defaultBytes_string(input, &encoded);
                if (!result.ok || encoded.length != 9 || memcmp(expected_bytes, encoded.data, sizeof(expected_bytes))) return 76;
                javan_result_free(&result); javan_free(encoded.data);
                uint16_t replaced[] = { 'A', 0, 0, 0xDE00, 0xD800, 'Z', 0xDC00 };
                result = javan_utf16_try_com_acme_Text_replaceChar_string_int_int(input, 0xd83d, 0, &output);
                if (!result.ok || output.length != 7 || memcmp(replaced, output.data, sizeof(replaced))) return 71;
                javan_result_free(&result); javan_free(output.data);
                needle.data = data + 2; needle.length = 1;
                JavanUtf16View replacement = { data + 1, 1 };
                result = javan_utf16_try_com_acme_Text_replaceSequence_string_string_string(input, needle, replacement, &output);
                if (!result.ok || output.length != 7 || memcmp(replaced, output.data, sizeof(replaced))) return 75;
                javan_result_free(&result); javan_free(output.data);
                needle.data = data + 2; needle.length = 2;
                uint16_t joined[] = { 'A', 0, 0xd83d, 0xde00, 0xd800, 'Z', 0xdc00, 0xd83d, 0xde00 };
                result = javan_utf16_try_com_acme_Text_join_string_string(input, needle, &output);
                if (!result.ok || output.length != 9 || memcmp(joined, output.data, sizeof(joined))) return 73;
                javan_result_free(&result); javan_free(output.data);
                needle.data = NULL; needle.length = 0;
                result = javan_utf16_try_com_acme_Text_join_string_string(input, needle, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 74;
                javan_result_free(&result);
                int point = -1;
                result = javan_try_com_acme_Text_literalCodePointAt_int(1, &point);
                if (!result.ok || point != 0x10000) return 38;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_literalCodePointBefore_int(3, &point);
                if (!result.ok || point != 0x10000) return 39;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_literalCodePointCount_int(0, &point);
                if (!result.ok || point != 3) return 40;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_literalCodePointOffset_int(1, &point);
                if (!result.ok || point != 3) return 41;
                javan_result_free(&result);
                result = javan_try_com_acme_Text_literalCodePointCount_int(5, &point);
                if (result.ok || point != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/IndexOutOfBoundsException") == NULL) return 42;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_copy_string(input, &output);
                if (!result.ok || output.length != 7 || memcmp(data, output.data, sizeof(data))) return 16;
                javan_result_free(&result);
                javan_free(output.data);
                uint16_t expected_chars[] = { 'a', 'b', 0, 0xD83D, 0xDE00, 0xD800, 'Z', 'h', 'i' };
                result = javan_utf16_try_com_acme_Text_getChars_string_int_int_int(input, 1, 6, 2, &output);
                if (!result.ok || output.length != 9 || memcmp(expected_chars, output.data, sizeof(expected_chars))) return 23;
                javan_result_free(&result);
                javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_getChars_string_int_int_int(input, -1, 0, 0, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/StringIndexOutOfBoundsException") == NULL) return 24;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_repeat_string_int(input, 2, &output);
                if (!result.ok || output.length != 14 || memcmp(data, output.data, sizeof(data))
                    || memcmp(data, output.data + 7, sizeof(data))) return 18;
                javan_result_free(&result);
                javan_free(output.data);
                result = javan_utf16_try_com_acme_Text_repeat_string_int(input, -1, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/IllegalArgumentException") == NULL) return 19;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_repeat_string_int(input, INT_MAX, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/OutOfMemoryError") == NULL) return 22;
                javan_result_free(&result);
                int length = 0;
                result = javan_utf16_try_com_acme_Text_length_string(input, &length);
                if (!result.ok || length != 7) return 2;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_strictLength_string(input, &length);
                if (!result.ok || length != 7) return 20;
                javan_result_free(&result);
                int hash = 0;
                uint32_t expected_hash = 0;
                for (int i = 0; i < 7; i++) expected_hash = expected_hash * 31U + data[i];
                result = javan_utf16_try_com_acme_Text_strictHash_string(input, &hash);
                if (!result.ok || (uint32_t) hash != expected_hash) return 24;
                javan_result_free(&result);
                int empty = -1;
                result = javan_utf16_try_com_acme_Text_strictEmpty_string(input, &empty);
                if (!result.ok || empty != 0) return 30;
                javan_result_free(&result);
                int blank = -1;
                result = javan_utf16_try_com_acme_Text_strictBlank_string(input, &blank);
                if (!result.ok || blank != 0) return 33;
                javan_result_free(&result);
                input.data = NULL; input.length = 0;
                result = javan_utf16_try_com_acme_Text_tail_string_int(input, 0, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 57;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_same_string_string(input, input, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 66;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_contains_string_string(input, input, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 69;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_replaceChar_string_int_int(input, 0, 1, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 72;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_join_string_string(input, input, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "NullPointerException") == NULL) return 75;
                javan_result_free(&result);
                needle.data = data; needle.length = 1;
                result = javan_utf16_try_com_acme_Text_substringLast_string_string_int(input, needle, -1, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 49;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_search_string_int(input, -1, &search);
                if (result.ok || search != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 45;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_getChars_string_int_int_int(input, 0, 0, 0, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 25;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_strictLength_string(input, &length);
                if (result.ok || length != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 21;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_strictHash_string(input, &hash);
                if (result.ok || hash != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 25;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_strictEmpty_string(input, &empty);
                if (result.ok || empty != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 31;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_strictBlank_string(input, &blank);
                if (result.ok || blank != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 34;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_copy_string(input, &output);
                if (result.ok || output.data != NULL || output.length != 0 || result.message == NULL
                    || strstr(result.message, "java/lang/NullPointerException") == NULL) return 17;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                if (!result.ok || output.data != NULL || output.length != 0) return 3;
                javan_result_free(&result);
                input.data = data;
                result = javan_utf16_try_com_acme_Text_strictEmpty_string(input, &empty);
                if (!result.ok || empty != 1) return 32;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_strictBlank_string(input, &blank);
                if (!result.ok || blank != 1) return 35;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                if (!result.ok || output.data == NULL || output.length != 0) return 4;
                javan_result_free(&result);
                javan_free(output.data);
                input.data = NULL; input.length = 1;
                result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                if (result.ok || output.data != NULL || output.length != 0) return 5;
                javan_result_free(&result);
                input.data = data; input.length = -1;
                result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                if (result.ok || output.data != NULL || output.length != 0) return 6;
                javan_result_free(&result);
                result = javan_utf16_try_com_acme_Text_echo_string(input, NULL);
                if (result.ok) return 7;
                javan_result_free(&result);
                input.length = 7;
                result = javan_utf16_try_com_acme_Text_fail_string(input, &output);
                if (result.ok || output.data != NULL || output.length != 0) return 8;
                javan_result_free(&result);
                for (int i = 0; i < 100; i++) {
                    result = javan_utf16_try_com_acme_Text_echo_string(input, &output);
                    if (!result.ok || output.length != 7 || memcmp(data, output.data, sizeof(data))) return 9;
                    javan_result_free(&result);
                    javan_free(output.data);
                    result = javan_utf16_try_com_acme_Text_getChars_string_int_int_int(input, 1, 6, 2, &output);
                    if (!result.ok || output.length != 9 || memcmp(expected_chars, output.data, sizeof(expected_chars))) return 26;
                    javan_result_free(&result);
                    javan_free(output.data);
                }
                result = javan_utf16_try_com_acme_Text_literal_void(&output);
                uint16_t expected[] = { 'a', 0, 'b' };
                if (!result.ok || output.length != 3 || memcmp(expected, output.data, sizeof(expected))) return 11;
                javan_result_free(&result);
                javan_free(output.data);
                int code_points[] = { 0, 0xD800, 0x1F600 };
                uint16_t code_units[][2] = { { 0, 0 }, { 0xD800, 0 }, { 0xD83D, 0xDE00 } };
                int code_lengths[] = { 1, 1, 2 };
                for (int i = 0; i < 3; i++) {
                    result = javan_utf16_try_com_acme_Text_fromCodePoint_int(code_points[i], &output);
                    if (!result.ok || output.length != code_lengths[i]
                        || memcmp(code_units[i], output.data, (size_t) output.length * sizeof(uint16_t))) return 13;
                    javan_result_free(&result);
                    javan_free(output.data);
                }
                result = javan_utf16_try_com_acme_Text_fromCodePoint_int(-1, &output);
                if (result.ok || output.data != NULL || output.length != 0) return 14;
                javan_result_free(&result);
                char* code_point_nul = javan_export_com_acme_Text_fromCodePoint_int(0);
                if (code_point_nul != NULL || javan_last_error() == NULL) return 15;
                char* rejected = javan_export_com_acme_Text_literal_void();
                if (rejected != NULL || javan_last_error() == NULL) return 12;
                char* legacy = javan_export_com_acme_Text_echo_string("legacy");
                if (!legacy || strcmp(legacy, "legacy") || javan_last_error() != NULL) return 10;
                javan_free(legacy);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-utf16");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(30),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
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
    void staticLibraryRandomTypedSecureRandomRejectsOtherHandles() throws Exception {
        final Path project = project("library-random-handle");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.security.SecureRandom;
            import java.util.Random;
            public final class Api {
                public static Object good() { return new SecureRandom(); }
                public static Object wrong() { return new String("not random"); }
                static final class Custom implements java.util.random.RandomGenerator {
                    public long nextLong() { return 42L; }
                    public void nextBytes(byte[] bytes) { if (bytes != null && bytes.length > 0) bytes[0] = 42; }
                    public int nextInt(int bound) { return bound + 42; }
                    public int nextInt() { return 41; }
                }
                public static Object custom() { return new Custom(); }
                public static int fill(Object value) {
                    byte[] bytes = new byte[8];
                    ((Random) value).nextBytes(bytes);
                    return bytes.length;
                }
                public static int next(Object value) { return ((Random) value).nextInt(7); }
                public static int nextGenerator(Object value) { return ((java.util.random.RandomGenerator) value).nextInt(7); }
                public static int nextGeneratorInt(Object value) { return ((java.util.random.RandomGenerator) value).nextInt(); }
                public static long nextGeneratorLong(Object value) { return ((java.util.random.RandomGenerator) value).nextLong(); }
                public static int fillGenerator(Object value) { byte[] bytes = new byte[1]; ((java.util.random.RandomGenerator) value).nextBytes(bytes); return bytes[0]; }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Api.good():object", "--export", "com.acme.Api.wrong():object",
            "--export", "com.acme.Api.fill(object):int", "--export", "com.acme.Api.next(object):int", "--export", "com.acme.Api.nextGenerator(object):int",
            "--export", "com.acme.Api.custom():object", "--export", "com.acme.Api.nextGeneratorLong(object):long",
            "--export", "com.acme.Api.fillGenerator(object):int", "--export", "com.acme.Api.nextGeneratorInt(object):int");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_random.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-random-handle.h"
            int main(void) {
                JavanObjectHandle *good = 0, *wrong = 0, *custom = 0;
                int size = -1;
                long long generated = 0;
                JavanResult result = javan_try_com_acme_Api_good_void(&good);
                if (!result.ok || good == 0) return 1;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_fill_object(good, &size);
                if (!result.ok || size != 8) return 2;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_next_object(good, &size);
                if (!result.ok || size < 0 || size >= 7) return 5;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGenerator_object(good, &size);
                if (!result.ok || size < 0 || size >= 7) return 7;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGeneratorLong_object(good, &generated);
                if (!result.ok) return 9;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGeneratorInt_object(good, &size);
                if (!result.ok) return 14;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_custom_void(&custom);
                if (!result.ok || custom == 0) return 10;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGeneratorLong_object(custom, &generated);
                if (!result.ok || generated != 42) return 11;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_fillGenerator_object(custom, &size);
                if (!result.ok || size != 42) return 12;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGenerator_object(custom, &size);
                if (!result.ok || size != 49) return 13;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGeneratorInt_object(custom, &size);
                if (!result.ok || size != 41) return 15;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_wrong_void(&wrong);
                if (!result.ok || wrong == 0) return 3;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_fill_object(wrong, &size);
                if (result.ok || result.message == 0 || strstr(result.message, "unsupported Random receiver") == 0) return 4;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_next_object(wrong, &size);
                if (result.ok || result.message == 0 || strstr(result.message, "unsupported Random receiver") == 0) return 6;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGenerator_object(wrong, &size);
                if (result.ok || result.message == 0 || strstr(result.message, "unsupported dispatch target") == 0) return 8;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_nextGeneratorInt_object(wrong, &size);
                if (result.ok || result.message == 0 || strstr(result.message, "unsupported dispatch target") == 0) return 16;
                javan_result_free(&result);
                javan_object_handle_release(good);
                javan_object_handle_release(wrong);
                javan_object_handle_release(custom);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-random");
        final Path library = project.resolve(".javan/dist/liblibrary-random-handle.a");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void staticLibraryPlainObjectHandlesRetainIdentity() throws Exception {
        final Path project = project("library-plain-object");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            public final class Api {
                public static Object create() { return new Object(); }
                public static int same(Object left, Object right) { return left == right ? 1 : 0; }
                public static int plain(Object value) { return value.getClass() == Object.class ? 1 : 0; }
                public static int hash(Object value) { return System.identityHashCode(value); }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Api.create():object", "--export", "com.acme.Api.same(object,object):int",
            "--export", "com.acme.Api.plain(object):int", "--export", "com.acme.Api.hash(object):int");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_plain.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-plain-object.h"
            int main(void) {
                JavanObjectHandle *first = 0, *second = 0;
                int value = 0, hash = 0;
                JavanResult result = javan_try_com_acme_Api_create_void(&first);
                if (!result.ok || first == 0) return 1;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_create_void(&second);
                if (!result.ok || second == 0) return 2;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(first, first, &value);
                if (!result.ok || value != 1) return 3;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(first, second, &value);
                if (!result.ok || value != 0) return 4;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_plain_object(first, &value);
                if (!result.ok || value != 1) return 5;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_hash_object(first, &hash);
                if (!result.ok) return 6;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_hash_object(first, &value);
                if (!result.ok || value != hash) return 7;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_hash_object(0, &value);
                if (!result.ok || value != 0) return 8;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_plain_object(0, &value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 9;
                javan_result_free(&result);
                javan_object_handle_release(first);
                javan_object_handle_release(second);
                return 0;
            }
            """);
        final Path library = project.resolve(".javan/dist/liblibrary-plain-object.a");
        final Path binary = project.resolve("call-plain-object");
        final ProcessResult compiler = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compiler.exitCode()).as(compiler.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void staticLibraryCollectionOperationsReportNullReceiverAndCanBeReused() throws Exception {
        final Path project = project("library-null-search");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.ArrayList;
            public final class Api {
                public static int hit() {
                    ArrayList<Object> list = new ArrayList<>(); list.add("item");
                    return list.contains("item") ? 1 : 0;
                }
                public static int missing() {
                    ArrayList<Object> list = null;
                    return list.contains("item") ? 1 : 0;
                }
                public static int missingSize() { ArrayList<Object> list = null; return list.size(); }
                public static int missingEmpty() { ArrayList<Object> list = null; return list.isEmpty() ? 1 : 0; }
                public static int missingAdd() { ArrayList<Object> list = null; return list.add(System.err) ? 1 : 0; }
                public static int immutableAdd() { java.util.List<Object> list = java.util.List.of(); return list.add("item") ? 1 : 0; }
                public static int cleared() { java.util.List<Object> list = new ArrayList<>(); list.add("item"); list.clear(); return list.size(); }
                public static int missingClear() { java.util.List<Object> list = null; list.clear(); return 0; }
                public static int immutableClear() { java.util.List<Object> list = java.util.List.of(); list.clear(); return 0; }
                public static int mapCleared() { java.util.Map<String, String> map = new java.util.HashMap<>(); map.put("a", "b"); map.clear(); return map.size(); }
                public static int missingMapClear() { java.util.Map<String, String> map = null; map.clear(); return 0; }
                public static int immutableMapClear() { java.util.Map<String, String> map = java.util.Map.of(); map.clear(); return 0; }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Api.hit():int", "--export", "com.acme.Api.missing():int",
            "--export", "com.acme.Api.missingSize():int", "--export", "com.acme.Api.missingEmpty():int",
            "--export", "com.acme.Api.missingAdd():int", "--export", "com.acme.Api.immutableAdd():int",
            "--export", "com.acme.Api.cleared():int", "--export", "com.acme.Api.missingClear():int",
            "--export", "com.acme.Api.immutableClear():int", "--export", "com.acme.Api.mapCleared():int",
            "--export", "com.acme.Api.missingMapClear():int", "--export", "com.acme.Api.immutableMapClear():int");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_null_search.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-null-search.h"
            int main(void) {
                int value = 0;
                JavanResult result = javan_try_com_acme_Api_hit_void(&value);
                if (!result.ok || value != 1) return 1;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_missing_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 2;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_missingSize_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 3;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_missingEmpty_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 4;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_missingAdd_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 5;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_immutableAdd_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "UnsupportedOperationException") == 0) return 6;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_cleared_void(&value);
                if (!result.ok || value != 0) return 7;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_missingClear_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 8;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_immutableClear_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "UnsupportedOperationException") == 0) return 9;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_mapCleared_void(&value);
                if (!result.ok || value != 0) return 10;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_missingMapClear_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 11;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_immutableMapClear_void(&value);
                if (result.ok || result.message == 0 || strstr(result.message, "UnsupportedOperationException") == 0) return 12;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_hit_void(&value);
                if (!result.ok || value != 1) return 13;
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path library = project.resolve(".javan/dist/liblibrary-null-search.a");
        final Path binary = project.resolve("call-null-search");
        final ProcessResult compiler = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compiler.exitCode()).as(compiler.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void staticLibraryConcurrentMapWritesRemainUsableAcrossCalls() throws Exception {
        final Path project = project("library-concurrent-map");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Api {
                static final Map<Integer, Integer> values = new ConcurrentHashMap<>();
                static final AtomicInteger ready = new AtomicInteger();
                static final class Worker implements Runnable {
                    final int start;
                    Worker(int start) { this.start = start; }
                    public void run() {
                        ready.incrementAndGet();
                        while (ready.get() < 2) Thread.yield();
                        for (int i = 0; i < 200; i++) values.put(start + i, i);
                    }
                }
                public static int size() throws InterruptedException {
                    values.clear(); ready.set(0);
                    Thread a = new Thread(new Worker(0)); Thread b = new Thread(new Worker(200));
                    a.start(); b.start(); a.join(); b.join();
                    return values.size();
                }
                public static int nullKey() { values.put(null, 1); return 0; }
                public static int nullValue() { values.put(1, null); return 0; }
                public static int nullGet() { values.get(null); return 0; }
                public static int nullContainsKey() { values.containsKey(null); return 0; }
                public static int nullContainsValue() { values.containsValue(null); return 0; }
                public static int nullDefaultKey() { values.getOrDefault(null, null); return 0; }
                public static int absentDefault() { return values.getOrDefault(-1, null) == null ? 1 : 0; }
                public static int nullAbsentKey() { values.putIfAbsent(null, 1); return 0; }
                public static int nullAbsentValue() { values.putIfAbsent(1, null); return 0; }
                public static int missingAbsentReceiver() {
                    Map<Integer, Integer> map = null; map.putIfAbsent(1, 2); return 0;
                }
                public static int nullRemoveKey() { values.remove(null); return 0; }
                public static int nullRemovePairKey() { values.remove(null, null); return 0; }
                public static int nullRemoveValue() { return values.remove(-1, null) ? 1 : 0; }
                public static int missingRemoveReceiver() {
                    Map<Integer, Integer> map = null; map.remove(1, 2); return 0;
                }
                public static int immutableRemove() { Map.of().remove(1); return 0; }
                public static int nullReplaceKey() { values.replace(null, 1); return 0; }
                public static int nullReplaceValue() { values.replace(-1, null); return 0; }
                public static int nullReplaceOld() { values.replace(-1, null, 1); return 0; }
                public static int nullReplaceNew() { values.replace(-1, 1, null); return 0; }
                public static int missingReplaceReceiver() {
                    Map<Integer, Integer> map = null; map.replace(1, 2, 3); return 0;
                }
                public static int immutableReplace() { Map.of().replace(1, 2); return 0; }
                public static int nullPutAllValue() {
                    Map<Integer, Integer> source = new java.util.HashMap<>(); source.put(999, null);
                    values.putAll(source); return 0;
                }
                public static int nullPutAllSource() { values.putAll(null); return 0; }
                public static int immutablePutAll() { Map.of().putAll(null); return 0; }
                public static int nullCopyValue() {
                    Map<Integer, Integer> source = new java.util.HashMap<>(); source.put(999, null);
                    new ConcurrentHashMap<Integer, Integer>(source); return 0;
                }
                public static int nullCopySource() { new ConcurrentHashMap<Integer, Integer>((Map<Integer, Integer>) null); return 0; }
                public static int nullHashCopySource() { new java.util.HashMap<Integer, Integer>((Map<Integer, Integer>) null); return 0; }
                public static int nullLinkedCopySource() { new java.util.LinkedHashMap<Integer, Integer>((Map<Integer, Integer>) null); return 0; }
                public static int nullCopyOfValue() {
                    Map<Integer, Integer> source = new java.util.HashMap<>(); source.put(999, null);
                    Map.copyOf(source); return 0;
                }
                public static int nullCopyOfSource() { Map.copyOf(null); return 0; }
                public static int nullMapOfKey() { Map.of(null, 1); return 0; }
                public static int nullMapEntryValue() { Map.entry(1, null); return 0; }
                public static int singletonAllowsNull() { return java.util.Collections.singletonMap(null, null).containsKey(null) ? 1 : 0; }
                public static int nullMapPair() { Map.of(1, 1, 2, null); return 0; }
                public static int duplicateMapPair() { Map.of(1, 1, 1, 2); return 0; }
                public static int nullMapTriple() { Map.of(1, 1, 2, 2, 3, null); return 0; }
                public static int duplicateMapTriple() { Map.of(1, 1, 2, 2, 1, 3); return 0; }
                public static int nullMapDecuple() { Map.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, null); return 0; }
                public static int duplicateMapDecuple() { Map.of(1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 1, 10); return 0; }
                public static int nullMapEntryArray() { Map.Entry<Integer,Integer>[] entries = null; Map.ofEntries(entries); return 0; }
                public static int nullMapArrayEntry() { Map.Entry<Integer,Integer>[] entries = new Map.Entry[] { null }; Map.ofEntries(entries); return 0; }
                public static int duplicateMapArrayEntry() { Map.Entry<Integer,Integer>[] entries = new Map.Entry[] { Map.entry(1, 1), Map.entry(1, 2) }; Map.ofEntries(entries); return 0; }
                public static int nullSetElement() { Object value = null; java.util.Set.of(value); return 0; }
                public static int duplicateSetElements() { java.util.Set.of(1, 1); return 0; }
                public static int nullSetArray() { Object[] values = null; java.util.Set.of(values); return 0; }
                public static int nullSetArrayElement() { Object[] values = new Object[] { 1, null }; java.util.Set.of(values); return 0; }
                public static int duplicateSetArray() { Object[] values = new Object[] { 1, 1 }; java.util.Set.of(values); return 0; }
                public static int nullSetCopySource() { java.util.Collection<Integer> source = null; java.util.Set.copyOf(source); return 0; }
                public static int nullSetCopyElement() { java.util.ArrayList<Integer> source = new java.util.ArrayList<>(); source.add(null); java.util.Set.copyOf(source); return 0; }
                public static int duplicateSetCopy() { return java.util.Set.copyOf(java.util.List.of(1, 1)).size(); }
                public static int nullListCopySource() { java.util.Collection<Integer> source = null; java.util.List.copyOf(source); return 0; }
                public static int nullListCopyElement() { java.util.ArrayList<Integer> source = new java.util.ArrayList<>(); source.add(null); java.util.List.copyOf(source); return 0; }
                public static int nullListElement() { Object value = null; java.util.List.of(value); return 0; }
                public static int nullListArray() { Object[] values = null; java.util.List.of(values); return 0; }
                public static int nullListArrayElement() { Object[] values = new Object[] { 1, null }; java.util.List.of(values); return 0; }
                public static int singletonListAllowsNull() { return java.util.Collections.singletonList(null).get(0) == null ? 1 : 0; }
                public static int emptyListFirst() { new java.util.ArrayList<String>().getFirst(); return 0; }
                public static int emptyListLast() { java.util.List.<String>of().getLast(); return 0; }
                public static int nullListFirst(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.getFirst(); return 0; }
                public static int nullListLast(int selector) { java.util.ArrayList<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.getLast(); return 0; }
                public static int listGetBadIndex() { new java.util.ArrayList<String>().get(0); return 0; }
                public static int listGetNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.get(0); return 0; }
                public static int listSetBadIndex() { new java.util.ArrayList<String>().set(0, "x"); return 0; }
                public static int listSetNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.set(0, "x"); return 0; }
                public static int listSetImmutable() { java.util.List.of().set(0, "x"); return 0; }
                public static int listRemoveBadIndex() { new java.util.ArrayList<String>().remove(0); return 0; }
                public static int listRemoveNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.remove(0); return 0; }
                public static int listRemoveImmutable() { java.util.List.of().remove(0); return 0; }
                public static int listRemoveObjectNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); return values.remove("x") ? 1 : 0; }
                public static int listRemoveObjectImmutable() { return java.util.List.of("x").remove("x") ? 1 : 0; }
                public static int setRemoveObjectNull(int selector) { java.util.Set<String> values = selector == 0 ? null : new java.util.HashSet<>(); return values.remove("x") ? 1 : 0; }
                public static int setRemoveObjectImmutable() { return java.util.Set.of("x").remove("x") ? 1 : 0; }
                public static int listAddIndexNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.add(0, "x"); return 0; }
                public static int listAddIndexBounds() { new java.util.ArrayList<String>().add(1, "x"); return 0; }
                public static int listAddIndexImmutable() { java.util.List.of().add(0, "x"); return 0; }
                public static int listAddAllIndexNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); return values.addAll(0, java.util.List.of()) ? 1 : 0; }
                public static int listAddAllIndexSourceNull() { return new java.util.ArrayList<String>().addAll(0, null) ? 1 : 0; }
                public static int listAddAllIndexBounds() { return new java.util.ArrayList<String>().addAll(1, null) ? 1 : 0; }
                public static int listAddAllIndexImmutable() { return java.util.List.of().addAll(0, java.util.List.of()) ? 1 : 0; }
                public static int listAddAllNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); return values.addAll(java.util.List.of()) ? 1 : 0; }
                public static int listAddAllSourceNull() { return new java.util.ArrayList<String>().addAll(null) ? 1 : 0; }
                public static int listAddAllImmutable() { return java.util.List.of().addAll(java.util.List.of()) ? 1 : 0; }
                public static int listAddAllSuccess() { java.util.List<String> values = new java.util.ArrayList<>(); return values.addAll(java.util.List.of("x")) && values.size() == 1 ? 1 : 0; }
                public static int collectionAddAllNull(int selector) { java.util.Collection<String> values = selector == 0 ? null : new java.util.ArrayList<>(); return values.addAll(java.util.List.of()) ? 1 : 0; }
                public static int setAddAllSourceNull() { java.util.Set<String> values = new java.util.HashSet<>(); return values.addAll(null) ? 1 : 0; }
                public static int setAddAllImmutable() { java.util.Set<String> values = java.util.Set.of(); return values.addAll(java.util.List.of()) ? 1 : 0; }
                public static int setAddAllSuccess() { java.util.Collection<String> values = new java.util.LinkedHashSet<>(); return values.addAll(java.util.List.of("x")) && values.size() == 1 ? 1 : 0; }
                public static int containsAllNull(int selector) { java.util.Collection<String> values = selector == 0 ? null : new java.util.ArrayList<>(); return values.containsAll(java.util.List.of()) ? 1 : 0; }
                public static int containsAllSourceNull() { java.util.Set<String> values = new java.util.HashSet<>(); return values.containsAll(null) ? 1 : 0; }
                public static int containsAllSuccess() { java.util.Collection<String> values = new java.util.HashSet<>(); values.add("x"); return values.containsAll(java.util.List.of("x")) ? 1 : 0; }
                public static int removeAllNull(int selector) { java.util.Collection<String> values = selector == 0 ? null : new java.util.ArrayList<>(); return values.removeAll(java.util.List.of()) ? 1 : 0; }
                public static int retainAllSourceNull() { java.util.Set<String> values = new java.util.HashSet<>(); return values.retainAll(null) ? 1 : 0; }
                public static int removeAllImmutable() { java.util.Set<String> values = java.util.Set.of("x"); return values.removeAll(java.util.List.of("x")) ? 1 : 0; }
                public static int retainAllSuccess() { java.util.Collection<String> values = new java.util.ArrayList<>(); values.add("x"); values.add("y"); return values.retainAll(java.util.List.of("x")) && values.size() == 1 ? 1 : 0; }
                public static int listRemoveFirstEmpty() { new java.util.ArrayList<String>().removeFirst(); return 0; }
                public static int listRemoveLastNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.removeLast(); return 0; }
                public static int listRemoveFirstImmutable() { java.util.List.of().removeFirst(); return 0; }
                public static int listRemoveLastLegacyEmpty() { java.util.Collections.emptyList().removeLast(); return 0; }
                public static int listAddFirstNull(int selector) { java.util.List<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.addFirst("x"); return 0; }
                public static int listAddLastNull(int selector) { java.util.ArrayList<String> values = selector == 0 ? null : new java.util.ArrayList<>(); values.addLast("x"); return 0; }
                public static int listAddFirstImmutable() { java.util.List.of().addFirst("x"); return 0; }
                public static int listAddLastImmutable() { java.util.Collections.emptyList().addLast("x"); return 0; }
                public static int replacesNull() {
                    Map<Integer, Integer> map = new java.util.HashMap<>();
                    map.put(1, null); map.putIfAbsent(1, 7); return map.get(1);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Api.size():int", "--export", "com.acme.Api.nullKey():int",
            "--export", "com.acme.Api.nullValue():int", "--export", "com.acme.Api.nullGet():int",
            "--export", "com.acme.Api.nullContainsKey():int", "--export", "com.acme.Api.nullContainsValue():int",
            "--export", "com.acme.Api.nullDefaultKey():int", "--export", "com.acme.Api.absentDefault():int",
            "--export", "com.acme.Api.nullAbsentKey():int", "--export", "com.acme.Api.nullAbsentValue():int",
            "--export", "com.acme.Api.missingAbsentReceiver():int", "--export", "com.acme.Api.replacesNull():int",
            "--export", "com.acme.Api.nullRemoveKey():int", "--export", "com.acme.Api.nullRemovePairKey():int",
            "--export", "com.acme.Api.nullRemoveValue():int", "--export", "com.acme.Api.missingRemoveReceiver():int",
            "--export", "com.acme.Api.immutableRemove():int", "--export", "com.acme.Api.nullReplaceKey():int",
            "--export", "com.acme.Api.nullReplaceValue():int", "--export", "com.acme.Api.nullReplaceOld():int",
            "--export", "com.acme.Api.nullReplaceNew():int", "--export", "com.acme.Api.missingReplaceReceiver():int",
            "--export", "com.acme.Api.immutableReplace():int", "--export", "com.acme.Api.nullPutAllValue():int",
            "--export", "com.acme.Api.nullPutAllSource():int", "--export", "com.acme.Api.immutablePutAll():int",
            "--export", "com.acme.Api.nullCopyValue():int", "--export", "com.acme.Api.nullCopySource():int",
            "--export", "com.acme.Api.nullHashCopySource():int", "--export", "com.acme.Api.nullLinkedCopySource():int",
            "--export", "com.acme.Api.nullCopyOfValue():int", "--export", "com.acme.Api.nullCopyOfSource():int",
            "--export", "com.acme.Api.nullMapOfKey():int", "--export", "com.acme.Api.nullMapEntryValue():int",
            "--export", "com.acme.Api.singletonAllowsNull():int", "--export", "com.acme.Api.nullMapPair():int",
            "--export", "com.acme.Api.duplicateMapPair():int", "--export", "com.acme.Api.nullMapTriple():int",
            "--export", "com.acme.Api.duplicateMapTriple():int", "--export", "com.acme.Api.nullMapDecuple():int",
            "--export", "com.acme.Api.duplicateMapDecuple():int", "--export", "com.acme.Api.nullMapEntryArray():int",
            "--export", "com.acme.Api.nullMapArrayEntry():int", "--export", "com.acme.Api.duplicateMapArrayEntry():int",
            "--export", "com.acme.Api.nullSetElement():int", "--export", "com.acme.Api.duplicateSetElements():int",
            "--export", "com.acme.Api.nullSetArray():int", "--export", "com.acme.Api.nullSetArrayElement():int",
            "--export", "com.acme.Api.duplicateSetArray():int", "--export", "com.acme.Api.nullSetCopySource():int",
            "--export", "com.acme.Api.nullSetCopyElement():int", "--export", "com.acme.Api.duplicateSetCopy():int",
            "--export", "com.acme.Api.nullListCopySource():int", "--export", "com.acme.Api.nullListCopyElement():int",
            "--export", "com.acme.Api.nullListElement():int", "--export", "com.acme.Api.nullListArray():int",
            "--export", "com.acme.Api.nullListArrayElement():int", "--export", "com.acme.Api.singletonListAllowsNull():int",
            "--export", "com.acme.Api.emptyListFirst():int", "--export", "com.acme.Api.emptyListLast():int",
            "--export", "com.acme.Api.nullListFirst(int):int", "--export", "com.acme.Api.nullListLast(int):int",
            "--export", "com.acme.Api.listGetBadIndex():int", "--export", "com.acme.Api.listGetNull(int):int",
            "--export", "com.acme.Api.listSetBadIndex():int", "--export", "com.acme.Api.listSetNull(int):int",
            "--export", "com.acme.Api.listSetImmutable():int", "--export", "com.acme.Api.listRemoveBadIndex():int",
            "--export", "com.acme.Api.listRemoveNull(int):int", "--export", "com.acme.Api.listRemoveImmutable():int",
            "--export", "com.acme.Api.listRemoveObjectNull(int):int", "--export", "com.acme.Api.listRemoveObjectImmutable():int",
            "--export", "com.acme.Api.setRemoveObjectNull(int):int", "--export", "com.acme.Api.setRemoveObjectImmutable():int",
            "--export", "com.acme.Api.listAddIndexNull(int):int", "--export", "com.acme.Api.listAddIndexBounds():int", "--export", "com.acme.Api.listAddIndexImmutable():int",
            "--export", "com.acme.Api.listAddAllIndexNull(int):int", "--export", "com.acme.Api.listAddAllIndexSourceNull():int", "--export", "com.acme.Api.listAddAllIndexBounds():int", "--export", "com.acme.Api.listAddAllIndexImmutable():int",
            "--export", "com.acme.Api.listAddAllNull(int):int", "--export", "com.acme.Api.listAddAllSourceNull():int", "--export", "com.acme.Api.listAddAllImmutable():int", "--export", "com.acme.Api.listAddAllSuccess():int",
            "--export", "com.acme.Api.collectionAddAllNull(int):int", "--export", "com.acme.Api.setAddAllSourceNull():int", "--export", "com.acme.Api.setAddAllImmutable():int", "--export", "com.acme.Api.setAddAllSuccess():int",
            "--export", "com.acme.Api.containsAllNull(int):int", "--export", "com.acme.Api.containsAllSourceNull():int", "--export", "com.acme.Api.containsAllSuccess():int",
            "--export", "com.acme.Api.removeAllNull(int):int", "--export", "com.acme.Api.retainAllSourceNull():int", "--export", "com.acme.Api.removeAllImmutable():int", "--export", "com.acme.Api.retainAllSuccess():int",
            "--export", "com.acme.Api.listRemoveFirstEmpty():int", "--export", "com.acme.Api.listRemoveLastNull(int):int",
            "--export", "com.acme.Api.listRemoveFirstImmutable():int", "--export", "com.acme.Api.listRemoveLastLegacyEmpty():int",
            "--export", "com.acme.Api.listAddFirstNull(int):int", "--export", "com.acme.Api.listAddLastNull(int):int",
            "--export", "com.acme.Api.listAddFirstImmutable():int", "--export", "com.acme.Api.listAddLastImmutable():int");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_map.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-concurrent-map.h"
            int main(void) {
                for (int i = 0; i < 3; i++) {
                    int value = 0;
                    JavanResult failure = javan_try_com_acme_Api_nullKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 2;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 3;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullGet_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 4;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullContainsKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 5;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullContainsValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 6;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullDefaultKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 7;
                    javan_result_free(&failure);
                    JavanResult present = javan_try_com_acme_Api_absentDefault_void(&value);
                    if (!present.ok || value != 1) return 8;
                    javan_result_free(&present);
                    failure = javan_try_com_acme_Api_nullAbsentKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 9;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullAbsentValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 10;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_missingAbsentReceiver_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 11;
                    javan_result_free(&failure);
                    present = javan_try_com_acme_Api_replacesNull_void(&value);
                    if (!present.ok || value != 7) return 12;
                    javan_result_free(&present);
                    failure = javan_try_com_acme_Api_nullRemoveKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 13;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullRemovePairKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 14;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_missingRemoveReceiver_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 15;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_immutableRemove_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 16;
                    javan_result_free(&failure);
                    present = javan_try_com_acme_Api_nullRemoveValue_void(&value);
                    if (!present.ok || value != 0) return 17;
                    javan_result_free(&present);
                    failure = javan_try_com_acme_Api_nullReplaceKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 18;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullReplaceValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 19;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullReplaceOld_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 20;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullReplaceNew_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 21;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_missingReplaceReceiver_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 22;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_immutableReplace_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 23;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullPutAllValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 24;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullPutAllSource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 25;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_immutablePutAll_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 26;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullCopyValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 27;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullCopySource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 28;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullHashCopySource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 29;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullLinkedCopySource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 30;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullCopyOfValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 31;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullCopyOfSource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 32;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullMapOfKey_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 33;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullMapEntryValue_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 34;
                    javan_result_free(&failure);
                    present = javan_try_com_acme_Api_singletonAllowsNull_void(&value);
                    if (!present.ok || value != 1) return 35;
                    javan_result_free(&present);
                    failure = javan_try_com_acme_Api_nullMapPair_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 36;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_duplicateMapPair_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IllegalArgumentException") == 0) return 37;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullMapTriple_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 38;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_duplicateMapTriple_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IllegalArgumentException") == 0) return 39;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullMapDecuple_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 40;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_duplicateMapDecuple_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IllegalArgumentException") == 0) return 41;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullMapEntryArray_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 42;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullMapArrayEntry_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 43;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_duplicateMapArrayEntry_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IllegalArgumentException") == 0) return 44;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullSetElement_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 45;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_duplicateSetElements_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IllegalArgumentException") == 0) return 46;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullSetArray_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 47;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullSetArrayElement_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 48;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_duplicateSetArray_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IllegalArgumentException") == 0) return 49;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullSetCopySource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 50;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullSetCopyElement_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 51;
                    javan_result_free(&failure);
                    present = javan_try_com_acme_Api_duplicateSetCopy_void(&value);
                    if (!present.ok || value != 1) return 52;
                    javan_result_free(&present);
                    failure = javan_try_com_acme_Api_nullListCopySource_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 53;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullListCopyElement_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 54;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullListElement_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 55;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullListArray_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 56;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullListArrayElement_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 57;
                    javan_result_free(&failure);
                    present = javan_try_com_acme_Api_singletonListAllowsNull_void(&value);
                    if (!present.ok || value != 1) return 58;
                    javan_result_free(&present);
                    failure = javan_try_com_acme_Api_emptyListFirst_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NoSuchElementException") == 0) return 59;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_emptyListLast_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NoSuchElementException") == 0) return 60;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullListFirst_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 61;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_nullListLast_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 62;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listGetBadIndex_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IndexOutOfBoundsException") == 0) return 63;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listGetNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 64;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listSetBadIndex_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IndexOutOfBoundsException") == 0) return 65;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listSetNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 66;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listSetImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 67;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveBadIndex_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IndexOutOfBoundsException") == 0) return 68;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 69;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 70;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveObjectNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 79;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveObjectImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 80;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_setRemoveObjectNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 81;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_setRemoveObjectImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 82;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddIndexNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 83;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddIndexBounds_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IndexOutOfBoundsException") == 0) return 84;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddIndexImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 85;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllIndexNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 86;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 90;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllSourceNull_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 91;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 92;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllSuccess_void(&value);
                    if (!failure.ok || value != 1) return 93;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_collectionAddAllNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 94;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_setAddAllSourceNull_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 95;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_setAddAllImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 96;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_setAddAllSuccess_void(&value);
                    if (!failure.ok || value != 1) return 97;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_containsAllNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 98;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_containsAllSourceNull_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 99;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_containsAllSuccess_void(&value);
                    if (!failure.ok || value != 1) return 100;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_removeAllNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 101;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_retainAllSourceNull_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 102;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_removeAllImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 103;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_retainAllSuccess_void(&value);
                    if (!failure.ok || value != 1) return 104;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllIndexSourceNull_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 87;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllIndexBounds_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "IndexOutOfBoundsException") == 0) return 88;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddAllIndexImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 89;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveFirstEmpty_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NoSuchElementException") == 0) return 71;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveLastNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 72;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveFirstImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 73;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listRemoveLastLegacyEmpty_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NoSuchElementException") == 0) return 74;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddFirstNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 75;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddLastNull_int(0, &value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "NullPointerException") == 0) return 76;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddFirstImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 77;
                    javan_result_free(&failure);
                    failure = javan_try_com_acme_Api_listAddLastImmutable_void(&value);
                    if (failure.ok || failure.message == 0 || strstr(failure.message, "UnsupportedOperationException") == 0) return 78;
                    javan_result_free(&failure);
                    JavanResult result = javan_try_com_acme_Api_size_void(&value);
                    if (!result.ok || value != 400) return 1;
                    javan_result_free(&result);
                }
                return 0;
            }
            """);
        final Path library = project.resolve(".javan/dist/liblibrary-concurrent-map.a");
        final Path binary = project.resolve("call-map");
        final ProcessResult compiler = process(project, List.of("cc", "-pthread", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compiler.exitCode()).as(compiler.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
    }

    @Test
    void staticLibraryObjectEqualsDispatchesAndReportsFailures() throws Exception {
        final Path project = project("library-object-equals");
        writeJava(project, "com.acme.Api", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.concurrent.TimeUnit;
            public final class Api {
                static int calls;
                static final class Custom {
                    public boolean equals(Object other) { calls = calls + 1; return false; }
                }
                static final class Throwing {
                    public boolean equals(Object other) { throw new IllegalStateException(); }
                }
                public static Object custom() { return new Custom(); }
                public static Object throwing() { return new Throwing(); }
                public static Object plain() { return new Object(); }
                public static Object list() { return new ArrayList<>(); }
                public static Object unit() { return TimeUnit.SECONDS; }
                public static Object name() { return "SECONDS"; }
                public static int isEnum(Object value) { return value.getClass().isEnum() ? 1 : 0; }
                public static int same(Object left, Object right) { return left.equals(right) ? 1 : 0; }
                public static int calls() { return calls; }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString(), "--kind", "staticlib",
            "--export", "com.acme.Api.custom():object", "--export", "com.acme.Api.throwing():object",
            "--export", "com.acme.Api.plain():object", "--export", "com.acme.Api.list():object",
            "--export", "com.acme.Api.same(object,object):int", "--export", "com.acme.Api.calls():int",
            "--export", "com.acme.Api.unit():object", "--export", "com.acme.Api.name():object",
            "--export", "com.acme.Api.isEnum(object):int");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path caller = writeC(project, "call_equals.c", """
            #include <string.h>
            #include ".javan/dist/bindings/c/library-object-equals.h"
            int main(void) {
                JavanObjectHandle *custom = 0, *throwing = 0, *plain = 0, *list1 = 0, *list2 = 0, *unit = 0, *name = 0;
                int value = 0;
                JavanResult result = javan_try_com_acme_Api_custom_void(&custom);
                if (!result.ok || custom == 0) return 1;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_throwing_void(&throwing);
                if (!result.ok || throwing == 0) return 2;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_plain_void(&plain);
                if (!result.ok || plain == 0) return 3;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_list_void(&list1);
                if (!result.ok || list1 == 0) return 4;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_list_void(&list2);
                if (!result.ok || list2 == 0) return 5;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_unit_void(&unit);
                if (!result.ok || unit == 0) return 12;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_name_void(&name);
                if (!result.ok || name == 0) return 13;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(unit, name, &value);
                if (!result.ok || value != 0) return 14;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(name, unit, &value);
                if (!result.ok || value != 0) return 15;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(unit, unit, &value);
                if (!result.ok || value != 1) return 16;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_isEnum_object(unit, &value);
                if (!result.ok || value != 1) return 17;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(custom, custom, &value);
                if (!result.ok || value != 0) return 6;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_calls_void(&value);
                if (!result.ok || value != 1) return 7;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(plain, plain, &value);
                if (!result.ok || value != 1) return 8;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(0, plain, &value);
                if (result.ok || result.message == 0 || strstr(result.message, "NullPointerException") == 0) return 9;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(throwing, plain, &value);
                if (result.ok || result.message == 0 || strstr(result.message, "IllegalStateException") == 0) return 10;
                javan_result_free(&result);
                result = javan_try_com_acme_Api_same_object_object(list1, list2, &value);
                if (result.ok || result.message == 0 || strstr(result.message, "unsupported Object.equals") == 0) return 11;
                javan_result_free(&result);
                javan_object_handle_release(custom);
                javan_object_handle_release(throwing);
                javan_object_handle_release(plain);
                javan_object_handle_release(list1);
                javan_object_handle_release(list2);
                javan_object_handle_release(unit);
                javan_object_handle_release(name);
                return 0;
            }
            """);
        final Path library = project.resolve(".javan/dist/liblibrary-object-equals.a");
        final Path binary = project.resolve("call-object-equals");
        final ProcessResult compiler = process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString()));
        assertThat(compiler.exitCode()).as(compiler.stderr()).isZero();
        final ProcessResult result = process(project, List.of(binary.toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
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

    @Test
    void staticLibraryObjectPendingExceptionReleasesExportRootsAndRecovers() throws Exception {
        final Path project = project("library-object-pending");
        writeJava(project, "com.acme.Handles", """
            package com.acme;

            public final class Handles {
                public static Object create(final String text, final byte[] data, final int length) {
                    final byte[] values = new byte[length];
                    return text + ":" + data.length + ":" + values.length;
                }

                public static String describe(final Object value) {
                    return String.valueOf(value);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--library", "--format", "static",
            "--bindings", "c", "--export", "com.acme.Handles.create", "--export", "com.acme.Handles.describe");
        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-object-pending.a");
        final Path caller = writeC(project, "call_object_pending.c", """
            #include <stddef.h>
            #include <stdio.h>
            #include <string.h>
            #include ".javan/dist/bindings/c/library-object-pending.h"

            int javan_heap_root_frame_depth(void);
            int javan_heap_frame_root_count(void);
            unsigned long javan_heap_live_allocations(void);
            unsigned long javan_heap_live_bytes(void);
            void javan_thread_detach_current(void);
            void javan_gc_collect(void);
            void javan_validate_heap_metadata(void);

            int main(void) {
                int8_t data[3] = {1, 2, 3};
                JavanByteArray input = {data, 3};
                max_align_t sentinel;
                for (int attempt = 0; attempt < 128; attempt++) {
                    for (int owned = 0; owned < 2; owned++) {
                        JavanObjectHandle* value = (JavanObjectHandle*) &sentinel;
                        JavanResult result = {0};
                        if (owned) {
                            result = javan_try_com_acme_Handles_create_string_bytes_int("fail", input, -1, &value);
                        } else {
                            value = javan_export_com_acme_Handles_create_string_bytes_int("fail", input, -1);
                        }
                        const char* error = javan_last_error();
                        if (value != NULL || error == NULL || strstr(error, "NegativeArraySizeException") == NULL
                                || javan_heap_root_frame_depth() != 0 || javan_heap_frame_root_count() != 0) {
                            fprintf(stderr, "pending Object failure: attempt=%d owned=%d value=%p roots=%d/%d error=%s\\n",
                                attempt, owned, (void*) value, javan_heap_root_frame_depth(),
                                javan_heap_frame_root_count(), error == NULL ? "<none>" : error);
                            return 1;
                        }
                        if (owned) {
                            if (result.ok != 0 || result.summary == NULL
                                    || strstr(result.summary, "NegativeArraySizeException") == NULL
                                    || result.code == NULL || strcmp(result.code, "JAVAN-RUNTIME-PANIC") != 0
                                    || result.detail == NULL || strcmp(result.detail, "-1") != 0) {
                                fprintf(stderr, "pending Object result lost diagnostics: attempt=%d\\n", attempt);
                                return 1;
                            }
                            javan_result_free(&result);
                            if (result.message != NULL || result.code != NULL || result.detail != NULL) {
                                fputs("pending Object result did not clear after free\\n", stderr);
                                return 1;
                            }
                            result = javan_try_com_acme_Handles_create_string_bytes_int("ok", input, 1, &value);
                            if (result.ok != 1 || result.message != NULL) {
                                fprintf(stderr, "Object try recovery failed: attempt=%d\\n", attempt);
                                return 1;
                            }
                            javan_result_free(&result);
                        } else {
                            value = javan_export_com_acme_Handles_create_string_bytes_int("ok", input, 1);
                        }
                        if (value == NULL || javan_last_error() != NULL) {
                            fprintf(stderr, "Object recovery failed: attempt=%d owned=%d\\n", attempt, owned);
                            return 1;
                        }
                        char* text = javan_export_com_acme_Handles_describe_object(value);
                        if (text == NULL || strcmp(text, "ok:3:1") != 0 || javan_last_error() != NULL) {
                            fprintf(stderr, "Object recovery contents failed: attempt=%d owned=%d\\n", attempt, owned);
                            return 1;
                        }
                        javan_free(text);
                        javan_object_handle_release(value);
                        if (javan_heap_root_frame_depth() != 0 || javan_heap_frame_root_count() != 0) {
                            fprintf(stderr, "Object recovery retained roots: attempt=%d owned=%d\\n", attempt, owned);
                            return 1;
                        }
                    }
                }
                javan_thread_detach_current();
                javan_gc_collect();
                javan_validate_heap_metadata();
                if (javan_heap_live_allocations() != 0 || javan_heap_live_bytes() != 0
                        || javan_heap_root_frame_depth() != 0 || javan_heap_frame_root_count() != 0) {
                    fprintf(stderr, "Object pending cleanup leaked: allocations=%lu bytes=%lu roots=%d/%d\\n",
                        javan_heap_live_allocations(), javan_heap_live_bytes(),
                        javan_heap_root_frame_depth(), javan_heap_frame_root_count());
                    return 1;
                }
                puts("pending-object-ok");
                return 0;
            }
            """);
        final Path binary = project.resolve("call-object-pending");
        final ProcessResult compile = process(project, List.of("cc", caller.toString(), library.toString(),
            "-o", binary.toString()));
        assertThat(compile.exitCode()).as(compile.stderr()).isZero();
        final ProcessResult nativeRun = process(project, List.of(binary.toString()), Duration.ofSeconds(10),
            Map.of("JAVAN_HEAP_LIMIT_BYTES", "2048", "JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1"));
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stderr()).isEmpty();
        assertThat(nativeRun.stdout()).isEqualTo("pending-object-ok\n");
    }
}
