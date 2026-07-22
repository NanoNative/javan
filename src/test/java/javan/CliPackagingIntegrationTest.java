package javan;

import javan.cli.Cli;
import javan.cli.Version;
import javan.reporting.RuntimeFootprintReports;
import javan.util.Files2;
import javan.util.Json;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
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
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliPackagingIntegrationTest extends CliIntegrationSupport {
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
        assertThat(run.stdout()).contains("Running tests:", "./mvnw test", "maven-test-ok");
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
        assertThat(run.stdout()).doesNotContain("native-err");
        assertThat(run.stderr()).contains("native-err");
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
            "\"rootModel\": \"generated-static-frame-return-and-expression-root-inventory-no-heap-scan\"",
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
            "\"status\": \"supported-linux-windows\"",
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

    @Test
    void buildRejectsSelfContainedContainmentOnMacos() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("mac"));
        final Path project = project("self-contained-macos");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            containment = "self-contained"
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("self-contained");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stderr()).contains(
            "Self-contained native linking is unsupported on macOS; use system-linked containment."
        );
    }

    @Test
    void nativeBuiltJavanBuildsPlainBooleanLiteralProgram() throws Exception {
        final Path root = Path.of("").toAbsolutePath().normalize();
        final Path classes = root.resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();

        final CliRun bootstrapBuild = runWithTimeout(
            tempDir,
            Duration.ofSeconds(120),
            "build",
            classes.toString(),
            "--main",
            "javan.Main",
            "--output",
            "javan-bootstrap-literal"
        );
        assertThat(bootstrapBuild.exitCode()).as(bootstrapBuild.stderr()).isZero();

        final Path bootstrapBinary = root.resolve("target/.javan/bin/javan-bootstrap-literal");
        assertThat(bootstrapBinary).isExecutable();

        final Path probeProject = tempDir.resolve("selfhost-plain-boolean");
        final Path probeClasses = probeProject.resolve("classes");
        Files.createDirectories(probeClasses);
        writeJava(probeProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
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
                bootstrapBinary.toString(),
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
        assertThat(process(tempDir, List.of(probeBinary.toString())).stdout()).isEqualTo("true\nfalse\n");
    }

    @Test
    void nativeBuiltJavanBuildsPlainLongLiteralProgram() throws Exception {
        final Path root = Path.of("").toAbsolutePath().normalize();
        final Path classes = root.resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();

        final CliRun bootstrapBuild = runWithTimeout(
            tempDir,
            Duration.ofSeconds(120),
            "build",
            classes.toString(),
            "--main",
            "javan.Main",
            "--output",
            "javan-bootstrap-long-literal"
        );
        assertThat(bootstrapBuild.exitCode()).as(bootstrapBuild.stderr()).isZero();

        final Path bootstrapBinary = root.resolve("target/.javan/bin/javan-bootstrap-long-literal");
        assertThat(bootstrapBinary).isExecutable();

        final Path probeProject = tempDir.resolve("selfhost-plain-long");
        final Path probeClasses = probeProject.resolve("classes");
        Files.createDirectories(probeClasses);
        writeJava(probeProject, "com.acme.Main", """
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
                bootstrapBinary.toString(),
                "build",
                probeClasses.toString(),
                "--main",
                "com.acme.Main",
                "--output",
                "selfhost-plain-long"
            ),
            Duration.ofSeconds(120)
        );
        assertThat(nativeBuild.exitCode()).as(nativeBuild.stderr()).isZero();
        assertThat(nativeBuild.stderr()).doesNotContain("debug[JAVAN040]");

        final Path probeBinary = probeProject.resolve(".javan/bin/selfhost-plain-long");
        assertThat(probeBinary).isExecutable();
        assertThat(process(tempDir, List.of(probeBinary.toString())).stdout()).isEqualTo("0\n1\n");
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
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("error[JAVAN900]", "Cross-target native linking is not implemented");
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
        final Path manifest = project.resolve(".javan/dist/library-manifest.json");
        assertThat(manifest).exists();
        assertThat(Files.readString(manifest)).contains(
            "\"schemaVersion\": 1",
            "\"abiVersion\": 2",
            "dist/liblibrary-add.a",
            "dist/bindings/c/library-add.h",
            "com/acme/Math.add(II)I"
        );
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
            "\"exceptionMapping\": \"caught-runtime-panic-to-last-error-limited-same-method-catch\"",
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
    void staticLibraryExportedBooleanMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-boolean");
        writeJava(project, "com.acme.Flags", """
            package com.acme;

            public final class Flags {
                private Flags() {
                }

                public static boolean identity(final boolean value) {
                    return value;
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
            "com.acme.Flags.identity(boolean):boolean"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-boolean.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-boolean.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "int javan_export_com_acme_Flags_identity_int(int arg0);",
            "JavanResult javan_try_com_acme_Flags_identity_int(int arg0, int* out);"
        );
        final Path caller = writeC(project, "call_boolean.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-boolean.h"

            int main(void) {
                printf("%d:%d\\n", javan_export_com_acme_Flags_identity_int(0), javan_export_com_acme_Flags_identity_int(1));
                int result_value = 0;
                JavanResult result = javan_try_com_acme_Flags_identity_int(1, &result_value);
                printf("try:%d:%d\\n", result.ok, result_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-boolean");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("0:1\ntry:1:1\n");
    }

    @Test
    void staticLibraryExportedVoidMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-void");
        writeJava(project, "com.acme.Actions", """
            package com.acme;

            public final class Actions {
                private Actions() {
                }

                public static void ping() {
                    System.out.print("native-void");
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
            "com.acme.Actions.ping():void"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-void.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-void.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "void javan_export_com_acme_Actions_ping_void(void);",
            "JavanResult javan_try_com_acme_Actions_ping_void(void);"
        );
        final Path caller = writeC(project, "call_void.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-void.h"

            int main(void) {
                javan_export_com_acme_Actions_ping_void();
                JavanResult result = javan_try_com_acme_Actions_ping_void();
                printf("|try:%d\\n", result.ok);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-void");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("native-voidnative-void|try:1\n");
    }

    @Test
    void staticLibraryExportedMixedPrimitiveMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-mixed-primitives");
        writeJava(project, "com.acme.Numbers", """
            package com.acme;

            public final class Numbers {
                private Numbers() {
                }

                public static double combine(final boolean enabled, final long value, final double scale) {
                    return enabled ? value * scale : -value * scale;
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
            "com.acme.Numbers.combine(boolean,long,double):double"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-mixed-primitives.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-mixed-primitives.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "double javan_export_com_acme_Numbers_combine_int_long_double(int arg0, long long arg1, double arg2);",
            "JavanResult javan_try_com_acme_Numbers_combine_int_long_double(int arg0, long long arg1, double arg2, double* out);"
        );
        final Path caller = writeC(project, "call_mixed_primitives.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-mixed-primitives.h"

            int main(void) {
                printf("%.1f:%.1f\\n",
                    javan_export_com_acme_Numbers_combine_int_long_double(1, 7LL, 0.5),
                    javan_export_com_acme_Numbers_combine_int_long_double(0, 7LL, 0.5));
                double result_value = 0.0;
                JavanResult result = javan_try_com_acme_Numbers_combine_int_long_double(1, 7LL, 0.5, &result_value);
                printf("try:%d:%.1f\\n", result.ok, result_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-mixed-primitives");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("3.5:-3.5\ntry:1:3.5\n");
    }

    @Test
    void staticLibraryExportedFloatMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-float");
        writeJava(project, "com.acme.Numbers", """
            package com.acme;

            public final class Numbers {
                private Numbers() {
                }

                public static float scale(final float value) {
                    return value * 1.5f;
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
            "com.acme.Numbers.scale(float):float"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-float.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-float.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "float javan_export_com_acme_Numbers_scale_float(float arg0);",
            "JavanResult javan_try_com_acme_Numbers_scale_float(float arg0, float* out);"
        );
        final Path caller = writeC(project, "call_float.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-float.h"

            int main(void) {
                printf("%.2f\\n", javan_export_com_acme_Numbers_scale_float(2.0f));
                float result_value = 0.0f;
                JavanResult result = javan_try_com_acme_Numbers_scale_float(2.0f, &result_value);
                printf("try:%d:%.2f\\n", result.ok, result_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-float");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("3.00\ntry:1:3.00\n");
    }

    @Test
    void staticLibraryExportedDoubleMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-double");
        writeJava(project, "com.acme.Numbers", """
            package com.acme;

            public final class Numbers {
                private Numbers() {
                }

                public static double scale(final double value) {
                    return value * 1.5d;
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
            "com.acme.Numbers.scale(double):double"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-double.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-double.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "double javan_export_com_acme_Numbers_scale_double(double arg0);",
            "JavanResult javan_try_com_acme_Numbers_scale_double(double arg0, double* out);"
        );
        final Path caller = writeC(project, "call_double.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-double.h"

            int main(void) {
                printf("%.2f\\n", javan_export_com_acme_Numbers_scale_double(2.0));
                double result_value = 0.0;
                JavanResult result = javan_try_com_acme_Numbers_scale_double(2.0, &result_value);
                printf("try:%d:%.2f\\n", result.ok, result_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-double");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("3.00\ntry:1:3.00\n");
    }

    @Test
    void staticLibraryExportedLongMethodBuildsWithoutMainAndRunsFromC() throws Exception {
        final Path project = project("library-long");
        writeJava(project, "com.acme.Numbers", """
            package com.acme;

            public final class Numbers {
                private Numbers() {
                }

                public static long scale(final long value) {
                    return value * 3L;
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
            "com.acme.Numbers.scale(long):long"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/liblibrary-long.a");
        final Path header = project.resolve(".javan/dist/bindings/c/library-long.h");
        assertThat(library).exists();
        assertThat(Files.readString(header)).contains(
            "long long javan_export_com_acme_Numbers_scale_long(long long arg0);",
            "JavanResult javan_try_com_acme_Numbers_scale_long(long long arg0, long long* out);"
        );
        final Path caller = writeC(project, "call_long.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-long.h"

            int main(void) {
                printf("%lld\\n", javan_export_com_acme_Numbers_scale_long(3000000000LL));
                long long result_value = 0;
                JavanResult result = javan_try_com_acme_Numbers_scale_long(3000000000LL, &result_value);
                printf("try:%d:%lld\\n", result.ok, result_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-long");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("9000000000\ntry:1:9000000000\n");
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
        assertThat(project.resolve(".javan/dist/lib/library-friendly/rust/Cargo.toml")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/rust/liblibrary-friendly.a")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/library-friendly.h")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/library_friendly.go")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/go.mod")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/go/liblibrary-friendly.a")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/python/library_friendly.py")).exists();
        assertThat(project.resolve(".javan/dist/lib/library-friendly/python/pyproject.toml")).exists();
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
    void libraryAndJarBuildProduceNativeArtifactsAndJvmJar() throws Exception {
        final Path project = project("library-with-jar");
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
            "--jar",
            "--export",
            "com.acme.Math.add"
        );

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/liblibrary-with-jar.a")).exists();
        assertThat(project.resolve(".javan/dist/" + sharedLibraryName("library-with-jar"))).exists();
        assertThat(project.resolve(".javan/dist/library-with-jar.jar")).exists();
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

        final CliRun run = run(tempDir, "build", project.toString(), "--library", "--format", "static", "--export", "com.acme.Math.add");

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

        final CliRun run = run(tempDir, "build", project.toString(), "--library", "--format", "shared", "--export", "com.acme.Math.add");

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
        final Path header = project.resolve(".javan/dist/bindings/c/library-string.h");
        assertThat(Files.readString(header)).contains(
            "char* javan_export_com_acme_Text_greet_string(const char* arg0);",
            "JavanResult javan_try_com_acme_Text_greet_string(const char* arg0, char** out);"
        );
        final Path caller = writeC(project, "call_string.c", """
            #include <stdio.h>
            #include ".javan/dist/bindings/c/library-string.h"

            int main(void) {
                char* value = javan_export_com_acme_Text_greet_string("Yuna");
                puts(value);
                javan_free(value);
                char* try_value = NULL;
                JavanResult result = javan_try_com_acme_Text_greet_string("Yuna", &try_value);
                printf("try:%d:%s\\n", result.ok, try_value);
                javan_free(try_value);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-string");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("Hi Yuna\ntry:1:Hi Yuna\n");
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
        final Path header = project.resolve(".javan/dist/bindings/c/library-bytes.h");
        assertThat(Files.readString(header)).contains(
            "JavanByteArray javan_export_com_acme_Bytes_echo_bytes(JavanByteArray arg0);",
            "JavanResult javan_try_com_acme_Bytes_echo_bytes(JavanByteArray arg0, JavanByteArray* out);"
        );
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
                JavanByteArray try_output = {0};
                JavanResult result = javan_try_com_acme_Bytes_echo_bytes(input, &try_output);
                printf("try:%d:%d:%d\\n", result.ok, try_output.length, try_output.data[1]);
                javan_free(try_output.data);
                javan_result_free(&result);
                return 0;
            }
            """);
        final Path binary = project.resolve("call-bytes");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("3 2\ntry:1:3:2\n");
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
    void sharedLibraryPythonBindingLoadsAndCallsTryWrapper() throws Exception {
        Assumptions.assumeTrue(commandAvailable("python3"));
        final Path project = project("library-python-binding");
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
            "--kind",
            "sharedlib",
            "--bindings",
            "python",
            "--export",
            "com.acme.Math.add"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/" + sharedLibraryName("library-python-binding"));
        final Path binding = project.resolve(".javan/dist/bindings/python/library_python_binding.py");
        assertThat(library).exists();
        assertThat(binding).exists();
        final String script = """
            import importlib.util
            spec = importlib.util.spec_from_file_location("binding", r"%s")
            binding = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(binding)
            lib = binding.load(r"%s")
            print(binding.try_javan_export_com_acme_Math_add_int_int(lib, 2, 5))
            """.formatted(binding, library);
        assertThat(process(project, List.of("python3", "-c", script)).stdout()).isEqualTo("7\n");
    }

    @Test
    void sharedLibraryGoBindingCallsTryWrapper() throws Exception {
        Assumptions.assumeTrue(commandAvailable("go"));
        final Path project = project("library-go-binding");
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
            "--kind",
            "sharedlib",
            "--bindings",
            "go",
            "--export",
            "com.acme.Math.add"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/" + sharedLibraryName("library-go-binding"));
        final Path goDirectory = project.resolve(".javan/dist/bindings/go");
        assertThat(library).exists();
        assertThat(goDirectory.resolve("library_go_binding.go")).exists();
        Files.writeString(goDirectory.resolve("library_go_binding_test.go"), """
            package library_go_binding

            import "testing"

            func TestTryWrapper(t *testing.T) {
                value, err := TryJavanExportComAcmeMathAddIntInt(2, 5)
                if err != nil || value != 7 {
                    t.Fatalf("unexpected result: value=%d err=%v", value, err)
                }
            }
            """);
        assertThat(processSlow(project, List.of("sh", "-c", "cd '" + goDirectory + "' && GO111MODULE=off CGO_ENABLED=1 go test")).exitCode())
            .isZero();
    }

    @Test
    void sharedLibraryRustBindingCallsTryWrapper() throws Exception {
        Assumptions.assumeTrue(commandAvailable("rustc"));
        final Path project = project("library-rust-binding");
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
            "--kind",
            "sharedlib",
            "--bindings",
            "rust",
            "--export",
            "com.acme.Math.add"
        );

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path library = project.resolve(".javan/dist/" + sharedLibraryName("library-rust-binding"));
        final Path rust = project.resolve(".javan/dist/bindings/rust/lib.rs");
        assertThat(library).exists();
        assertThat(rust).exists();
        Files.writeString(rust, """
            #[cfg(test)]
            mod generated_binding_test {
                #[test]
                fn try_wrapper_returns_expected_value() {
                    let value = unsafe { super::try_javan_export_com_acme_Math_add_int_int(2, 5) }
                        .expect("native result");
                    assert_eq!(value, 7);
                }
            }
            """, java.nio.file.StandardOpenOption.APPEND);
        final Path binary = project.resolve("rust-binding-test");
        assertThat(processSlow(project, List.of(
            "rustc",
            "--edition=2021",
            "--test",
            rust.toString(),
            "-L",
            "native=" + project.resolve(".javan/dist"),
            "-o",
            binary.toString()
        )).exitCode()).isZero();
        assertThat(processSlow(project, List.of(binary.toString())).exitCode()).isZero();
    }

    @Test
    void unsupportedExportSignatureFailsClearly() throws Exception {
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

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("Unsupported export object type");
    }
}
