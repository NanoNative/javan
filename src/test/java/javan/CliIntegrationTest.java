package javan;

import javan.cli.Cli;
import javan.cli.Version;
import javan.reporting.RuntimeFootprintReports;
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
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

final class CliIntegrationTest {
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
    void selfHostNativeCheckPassesCurrentClasses() throws Exception {
        final Path classes = Path.of("").toAbsolutePath().resolve("target/classes");
        assertThat(classes.resolve("javan/Main.class")).exists();
        Files2.deleteRecursive(classes.resolve(".javan"));

        final CliRun run = run(classes, "check", classes.toString(), "--main", "javan.Main");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(classes.resolve(".javan")).doesNotExist();
        assertThat(run.stdout()).contains("Checking static Java profile", "reachable classes:", "diagnostics:");
        assertThat(run.stdout()).contains("Warnings:", "warning[JAVAN130] unsupported bytecode in unreachable code:");
        assertThat(Files.readString(classes.getParent().resolve(".javan/reports/diagnostics.txt")))
            .doesNotContain(
                "java/util/List.stream()Ljava/util/stream/Stream;",
                "java/util/regex/Pattern.compile(Ljava/lang/String;)Ljava/util/regex/Pattern;",
                "error[JAVAN",
                "java/util/List.add(Ljava/lang/Object;)Z",
                "java/util/List.of()Ljava/util/List;"
            )
            .doesNotContain("JAVAN015")
            .doesNotContain("JAVAN011")
            .doesNotContain("JAVAN012")
            .doesNotContain("javan/build/BuildInvoker.<init>()V");
    }

    @Test
    void inspectWritesProjectReport() throws Exception {
        final Path project = project("inspect");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("inspect");
                }
            }
            """);

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: JAVAC");
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"buildTool\": \"JAVAC\"");
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
    void checkCompilesPlainJavaProject() throws Exception {
        final Path project = project("check");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private static long sink;

                private Main() {
                }

                public static void main(final String[] args) {
                    Objects.requireNonNull(args);
                    sink = System.nanoTime();
                    sink = System.currentTimeMillis();
                    final int distance = Math.abs(args.length - 4);
                    final int value = Math.max(distance, Math.min(args.length, 10));
                    System.out.println(value);
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--profile", "service");

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("profile:           service", "reachable methods: 1");
        assertThat(Files.readString(project.resolve(".javan/reports/project.json"))).contains("\"profile\": \"service\"");
        assertThat(Files.exists(project.resolve(".javan/classes/com/acme/Main.class"))).isTrue();
        assertThat(project.resolve(".javan/reports/optimizations.json")).exists();
        assertThat(project.resolve(".javan/reports/optimizations.md")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.json")).exists();
        assertThat(project.resolve(".javan/reports/intrinsics.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 1}",
                "{\"name\": \"Math.abs\", \"count\": 1}",
                "{\"name\": \"Math.min\", \"count\": 1}",
                "{\"name\": \"Math.max\", \"count\": 1}",
                "{\"name\": \"System.nanoTime\", \"count\": 1}",
                "{\"name\": \"System.currentTimeMillis\", \"count\": 1}",
                "\"unsupportedJdkCallCandidateCount\":"
            );
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.md")))
            .contains("| `Objects.requireNonNull` | 1 |", "| `System.nanoTime` | 1 |", "| `System.currentTimeMillis` | 1 |");
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json")))
            .contains(
                "\"redundantNullChecks\": 0",
                "\"redundantBoundsChecks\": 0",
                "\"redundantTypeChecks\": 0",
                "\"redundantRangeChecks\": 0",
                "\"deadBranches\": 0",
                "\"specializedMethods\": 0",
                "\"skippedCandidates\": 0"
            );
    }

    @Test
    void checkRejectsReachableDisabledRuntimeModule() throws Exception {
        final Path project = project("disabled-time-check");
        Files.writeString(project.resolve("javan.toml"), """
            [build.runtime]
            disabled = ["time"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.nanoTime());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "error[JAVAN060]",
            "disabled runtime module is reachable",
            "time"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"disabledReachableRuntimeModules\": [\"time\"]",
            "\"status\": \"fail\""
        );
    }

    @Test
    void buildRejectsReachableDisabledRuntimeModuleBeforeCodegen() throws Exception {
        final Path project = project("disabled-time-build");
        Files.writeString(project.resolve("javan.toml"), """
            [runtime]
            disabled = ["time"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.currentTimeMillis());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(project.resolve(".javan/generated")).doesNotExist();
        assertThat(run.stderr()).contains("error[JAVAN060]", "time");
    }

    @Test
    void checkReportsUnusedDisabledRuntimeModule() throws Exception {
        final Path project = project("disabled-unused-check");
        Files.writeString(project.resolve("javan.toml"), """
            [build.runtime]
            disabled = ["thread-profiling"]
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("small");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/reports/runtime-features.json"))).contains(
            "\"disabledRuntimeModules\": [\"thread-profiling\"]",
            "\"disabledReachableRuntimeModules\": []",
            "\"disabledUnusedRuntimeModules\": [\"thread-profiling\"]",
            "\"status\": \"pass\""
        );
    }

    @Test
    void unknownProfileFailsAtCliBoundary() {
        final CliRun run = run(tempDir, "check", "--profile", "enterprise");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stdout()).isEmpty();
        assertThat(run.stderr()).contains("error[JAVAN900]: Unsupported profile: enterprise");
    }

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
            "\"heapReclamationScope\": \"generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage\"",
            "\"typeDescriptors\": true",
            "\"objectFieldDescriptors\": true",
            "\"frameRootInventory\": true",
            "\"managedHeap\": false",
            "\"gc\": \"partial-mark-sweep\"",
            "\"runtimeContainerTraversal\": \"precise-rooted-runtime-container-mark-sweep\"",
            "\"operandCallTemporaryRoots\": true",
            "\"allocationPathCollection\": true",
            "\"allocationPathCollectionModel\": \"allocator-gc-retry-before-out-of-memory\"",
            "\"allocationPathCollectionScope\": \"generated-objects-object-arrays-primitive-arrays-runtime-strings-runtime-containers-and-owned-container-storage\"",
            "\"allocationFailureMode\": \"deterministic-native-panic\"",
            "\"statementSafePoints\": true",
            "\"returnValueRoots\": true",
            "\"protectedObjectReturns\": true",
            "\"staticRootInventory\": true",
            "\"localRootInventory\": true",
            "\"rootModel\": \"generated-static-frame-return-and-expression-root-inventory-no-heap-scan\"",
            "\"threads\": \"none\"",
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
        assertThat(project.resolve(".javan/reports/library-build.json")).exists();
        assertThat(Files.readString(header)).contains(
            "#define JAVAN_ABI_VERSION 1",
            "#define JAVAN_ABI_STRING_UTF8 1",
            "#define JAVAN_ABI_BYTE_ARRAY_POINTER_LENGTH 1"
        );
        assertThat(Files.readString(project.resolve(".javan/reports/library-build.json"))).contains(
            "\"abiVersion\": 1",
            "\"stringOwnership\": \"input-borrowed-utf8-output-javan-owned-free-with-javan_free\"",
            "\"byteArrayOwnership\": \"input-copied-output-javan-owned-data-free-with-javan_free\"",
            "\"errorResultAbi\": \"not-yet-stable-panics-abort-current-call\"",
            "\"exceptionMapping\": \"uncaught-native-panic-limited-same-method-catch\"",
            "\"threadRuntimeRules\": \"single-threaded-native-profile-no-thread-api-yet\"",
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
                return 0;
            }
            """);
        final Path binary = project.resolve("call-add");
        assertThat(process(project, List.of("cc", caller.toString(), library.toString(), "-o", binary.toString())).exitCode()).isZero();
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo("7\n");
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

    @Test
    void runExecutesNativeExecutable() throws Exception {
        final Path project = project("run");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("run-native");
                }
            }
            """);

        final CliRun run = run(tempDir, "run", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("run-native");
    }

    @Test
    void staticHelperCallBuilds() throws Exception {
        final Path project = project("helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.print();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void print() {
                    System.out.println("helper-output");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/helper").toString())).stdout()).isEqualTo("helper-output\n");
    }

    @Test
    void staticFieldsAndClassInitializerBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("static-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(State.count);
                    System.out.println(State.total);
                    System.out.println(State.label);
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public final class State {
                static int count;
                static long total;
                static String label;

                static {
                    count = 41;
                    count = count + 1;
                    total = 80L + 4L;
                    label = "ready";
                }

                private State() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/static-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n84\nready\n");
        final String generated = Files.readString(project.resolve(".javan/generated/main.c"));
        assertThat(generated).contains(
            "static void** javan_static_roots[] = {",
            "(void**) &javan_static_com_acme_State_field_label",
            "javan_register_static_roots(javan_static_roots, 1);"
        );
        final int mainStart = generated.indexOf("int main");
        assertThat(generated.indexOf("    javan_register_generated_roots();", mainStart))
            .isLessThan(generated.indexOf("    javan_com_acme_State__clinit____V();", mainStart));
    }

    @Test
    void staticIntMethodBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(add(7, 5));
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-int").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("12\n");
    }

    @Test
    void intLocalsArithmeticAndLargeConstantsBuild() throws Exception {
        final Path project = project("int-locals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int value = calculate(40000, 9);
                    System.out.println(value);
                }

                public static int calculate(final int left, final int right) {
                    final int sum = left + right;
                    final int product = sum * 2;
                    return product - 3;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-locals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("80015\n");
    }

    @Test
    void intBitwiseAndBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-bitwise-and");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(0b1110, 0b1011));
                }

                public static int mask(final int left, final int right) {
                    return left & right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-bitwise-and").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n");
    }

    @Test
    void longBitwiseAndBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-bitwise-and");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(14L, 11L));
                }

                public static long mask(final long left, final long right) {
                    return left & right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-bitwise-and").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intBitwiseOrBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-bitwise-or");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(0b1100, 0b0011));
                }

                public static int mask(final int left, final int right) {
                    return left | right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-bitwise-or").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longBitwiseOrBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-bitwise-or");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(12L, 3L));
                }

                public static long mask(final long left, final long right) {
                    return left | right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-bitwise-or").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intBitwiseXorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-bitwise-xor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(0b1110, 0b1011));
                }

                public static int mask(final int left, final int right) {
                    return left ^ right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-bitwise-xor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longBitwiseXorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-bitwise-xor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(14L, 11L));
                }

                public static long mask(final long left, final long right) {
                    return left ^ right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-bitwise-xor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longCompareBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(compare(12L));
                    System.out.println(compare(10L));
                    System.out.println(compare(7L));
                }

                public static int compare(final long value) {
                    if (value > 10L) {
                        return 1;
                    }
                    if (value == 10L) {
                        return 0;
                    }
                    return -1;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-compare").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n0\n-1\n");
    }

    @Test
    void staticVoidMethodWithIntArgumentBuilds() throws Exception {
        final Path project = project("int-void-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    print(42);
                }

                public static void print(final int value) {
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-void-helper").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("print-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print("ja");
                    System.out.println("van");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/print-string").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n");
    }

    @Test
    void ifElseIntReturnBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("if-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(max(10, 7));
                    System.out.println(max(2, 9));
                }

                public static int max(final int left, final int right) {
                    if (left > right) {
                        return left;
                    }
                    return right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-return").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n9\n");
    }

    @Test
    void ifElsePrintBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("if-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    printSign(-3);
                    printSign(0);
                    printSign(5);
                }

                public static void printSign(final int value) {
                    if (value < 0) {
                        System.out.println(-1);
                    } else if (value == 0) {
                        System.out.println(0);
                    } else {
                        System.out.println(1);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-print").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-1\n0\n1\n");
    }

    @Test
    void ifWithAllIntComparisonOperatorsBuilds() throws Exception {
        final Path project = project("if-comparisons");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(3, 3));
                    System.out.println(score(4, 3));
                    System.out.println(score(2, 3));
                }

                public static int score(final int left, final int right) {
                    if (left == right) {
                        return 10;
                    }
                    if (left != right) {
                        if (left >= right) {
                            return 20;
                        }
                        if (left <= right) {
                            return 30;
                        }
                    }
                    return 40;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-comparisons").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n20\n30\n");
    }

    @Test
    void whileLoopPrintsAndMatchesJvmOutput() throws Exception {
        final Path project = project("while-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int index = 0;
                    while (index < 3) {
                        System.out.println(index);
                        index++;
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-print").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n1\n2\n");
    }

    @Test
    void whileLoopAccumulatorMatchesJvmOutput() throws Exception {
        final Path project = project("while-sum");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(sum(5));
                    System.out.println(sum(0));
                }

                public static int sum(final int limit) {
                    int total = 0;
                    int index = 1;
                    while (index <= limit) {
                        total = total + index;
                        index++;
                    }
                    return total;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-sum").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("15\n0\n");
    }

    @Test
    void whileLoopDecrementMatchesJvmOutput() throws Exception {
        final Path project = project("while-decrement");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = 3;
                    while (value > 0) {
                        System.out.println(value);
                        value--;
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-decrement").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n2\n1\n");
    }

    @Test
    void objectConstructorFieldsAndInstanceMethodsMatchJvmOutput() throws Exception {
        final Path project = project("object-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Point point = new Point(10, 5);
                    System.out.println(point.sum());
                    System.out.println(PointOps.weighted(point, 3));
                }
            }
            """);
        writeJava(project, "com.acme.Point", """
            package com.acme;

            public final class Point {
                private final int x;
                private final int y;

                public Point(final int x, final int y) {
                    this.x = x;
                    this.y = y;
                }

                public int sum() {
                    return x + y;
                }

                public int score(final int factor) {
                    return sum() * factor;
                }
            }
            """);
        writeJava(project, "com.acme.PointOps", """
            package com.acme;

            public final class PointOps {
                private PointOps() {
                }

                public static int weighted(final Point point, final int factor) {
                    return point.score(factor);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("15\n45\n");
    }

    @Test
    void objectStringFieldReturnAndNullBranchMatchJvmOutput() throws Exception {
        final Path project = project("object-string-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("ready");
                    System.out.println(message.text());
                    System.out.println(label(null));
                    System.out.println(label(message));
                }

                public static String label(final Message message) {
                    if (message == null) {
                        return "missing";
                    }
                    return message.text();
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public final class Message {
                private final String text;

                public Message(final String text) {
                    this.text = text;
                }

                public String text() {
                    return text;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-string-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ready\nmissing\nready\n");
    }

    @Test
    void nonFinalClassWithoutKnownSubclassInstanceCallBuilds() throws Exception {
        final Path project = project("non-final-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("exact");
                    System.out.println(message.text());
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public class Message {
                private final String text;

                public Message(final String text) {
                    this.text = text;
                }

                public String text() {
                    return text;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/non-final-exact").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("exact\n");
    }

    @Test
    void simpleRecordConstructorAndAccessorBuilds() throws Exception {
        final Path project = project("simple-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("record");
                    System.out.println(message.text());
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public record Message(String text) {
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/simple-record").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("record\n");
    }

    @Test
    void objectArrayInitializerLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("object-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[]{"zero", "one"};
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\none\n");
    }

    @Test
    void intArrayInitializerLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("int-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[]{2, 3};
                    values[1] = 9;
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n9\n");
    }

    @Test
    void intArrayStaticReturnAndParameterBuilds() throws Exception {
        final Path project = project("int-array-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = values();
                    System.out.println(second(values));
                }

                public static int[] values() {
                    return new int[]{4, 8};
                }

                public static int second(final int[] values) {
                    return values[1];
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array-helper").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("8\n");
    }

    @Test
    void booleanFieldReturnBranchAndPrintBuilds() throws Exception {
        final Path project = project("boolean-basic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Flag flag = new Flag(true);
                    System.out.println(flag.value());
                    System.out.println(invert(flag.value()));
                }

                public static boolean invert(final boolean value) {
                    if (value) {
                        return false;
                    }
                    return true;
                }
            }
            """);
        writeJava(project, "com.acme.Flag", """
            package com.acme;

            public final class Flag {
                private boolean value;

                public Flag(final boolean value) {
                    this.value = value;
                }

                public boolean value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-basic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void floatArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("float-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final float[] values = new float[]{1.25f, 2.5f};
                    values[1] = 3.75f;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1.25\n3.75\n");
    }

    @Test
    void booleanArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("boolean-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] values = new boolean[]{false, true};
                    values[0] = true;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\ntrue\n");
    }

    @Test
    void byteShortAndCharArraysBuild() throws Exception {
        final Path project = project("small-primitive-arrays");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final byte[] bytes = new byte[]{-2, 3};
                    bytes[1] = -5;
                    final short[] shorts = new short[]{300, -7};
                    final char[] chars = new char[]{'A', 'B'};
                    chars[1] = 'C';
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                    System.out.println(shorts[0]);
                    System.out.println(shorts[1]);
                    System.out.println(chars[0] + 1);
                    System.out.println(chars[1] + 0);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/small-primitive-arrays").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-2\n-5\n300\n-7\n66\n67\n");
    }

    @Test
    void longArithmeticReturnAndPrintBuilds() throws Exception {
        final Path project = project("long-arithmetic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(calculate(40L, 2L));
                }

                public static long calculate(final long left, final long right) {
                    final long sum = left + right;
                    return (sum * 2L) - 4L;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-arithmetic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("80\n");
    }

    @Test
    void intToLongConversionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-long-conversion");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = -7;
                    long widened = value;
                    System.out.println(widened);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-long-conversion").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longFieldsConstructorAndGetterBuild() throws Exception {
        final Path project = project("long-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Counter counter = new Counter(7L);
                    System.out.println(counter.value());
                }
            }
            """);
        writeJava(project, "com.acme.Counter", """
            package com.acme;

            public final class Counter {
                private long value;

                public Counter(final long value) {
                    this.value = value;
                }

                public long value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-field").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void longParameterSlotWidthBuilds() throws Exception {
        final Path project = project("long-slots");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(pick(3L, "ignored", 4L));
                }

                public static long pick(final long first, final String ignored, final long second) {
                    return first + second;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-slots").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void floatAndDoubleArithmeticReturnAndPrintBuilds() throws Exception {
        final Path project = project("float-double-arithmetic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(scale(1.25f, 2.5f));
                    System.out.println(measure(4.0, 0.25));
                }

                public static float scale(final float left, final float right) {
                    return (left + right) * 2.0f;
                }

                public static double measure(final double left, final double right) {
                    return (left / 2.0) + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-arithmetic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7.5\n2.25\n");
    }

    @Test
    void floatAndDoubleFieldsConstructorAndGetterBuild() throws Exception {
        final Path project = project("float-double-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Reading reading = new Reading(1.25f, 2.5);
                    System.out.println(reading.ratio());
                    System.out.println(reading.total());
                }
            }
            """);
        writeJava(project, "com.acme.Reading", """
            package com.acme;

            public final class Reading {
                private float ratio;
                private double total;

                public Reading(final float ratio, final double total) {
                    this.ratio = ratio;
                    this.total = total;
                }

                public float ratio() {
                    return ratio;
                }

                public double total() {
                    return total;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-field").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void floatAndDoubleComparisonsBuild() throws Exception {
        final Path project = project("float-double-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(2.5f, 1.25f));
                    System.out.println(rank(1.0, 2.0));
                }

                public static int score(final float left, final float right) {
                    if (left > right) {
                        return 1;
                    }
                    return 0;
                }

                public static int rank(final double left, final double right) {
                    if (left < right) {
                        return -1;
                    }
                    return 0;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-compare").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n-1\n");
    }

    @Test
    void staticFloatAndDoubleFieldsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("static-float-double-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(State.ratio);
                    System.out.println(State.total);
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public final class State {
                static float ratio;
                static double total;

                static {
                    ratio = 1.25f;
                    total = 2.5;
                }

                private State() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/static-float-double-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void floatAndDoubleIndexedLocalsAndUnaryBuild() throws Exception {
        final Path project = project("float-double-indexed-locals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    printInts(args.length);
                    printLongs(0L);
                    printFloats(0.25f);
                    printDoubles(0.25);
                }

                public static void printInts(final int seed) {
                    int i0 = seed;
                    int i1 = i0 + 1;
                    int i2 = i1 + 1;
                    int i3 = i2 + 1;
                    int i4 = i3 + 1;
                    System.out.println(i4);
                }

                public static void printLongs(final long seed) {
                    long l0 = seed;
                    long l1 = l0 + 1L;
                    long l2 = l1 + 1L;
                    long l3 = l2 + 1L;
                    long l4 = l3 + 6L;
                    System.out.println(l4 % 4L);
                }

                public static void printFloats(final float seed) {
                    float f0 = seed;
                    float f1 = f0 + 1.0f;
                    float f2 = f1 + 1.0f;
                    float f3 = f2 + 1.0f;
                    float f4 = f3 + 0.5f;
                    System.out.println(-f4);
                    System.out.println(f4 - f1);
                }

                public static void printDoubles(final double seed) {
                    double d0 = seed;
                    double d1 = d0 + 1.0;
                    double d2 = d1 + 1.0;
                    double d3 = d2 + 1.0;
                    double d4 = d3 + 1.0;
                    System.out.println(-d4);
                    System.out.println(d4 - d1);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-indexed-locals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\n1\n-3.75\n2.5\n-4.25\n3.0\n");
    }

    @Test
    void multiImplementationInterfaceDispatchReturnsFloatAndDouble() throws Exception {
        final Path project = project("interface-float-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Metric metric = new FastMetric();
                    System.out.println(metric.ratio());
                    System.out.println(metric.total());
                }
            }
            """);
        writeJava(project, "com.acme.Metric", """
            package com.acme;

            public interface Metric {
                float ratio();

                double total();
            }
            """);
        writeJava(project, "com.acme.FastMetric", """
            package com.acme;

            public final class FastMetric implements Metric {
                public FastMetric() {
                }

                public float ratio() {
                    return 1.25f;
                }

                public double total() {
                    return 2.5;
                }
            }
            """);
        writeJava(project, "com.acme.SlowMetric", """
            package com.acme;

            public final class SlowMetric implements Metric {
                public SlowMetric() {
                }

                public float ratio() {
                    return 3.75f;
                }

                public double total() {
                    return 4.5;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-float-double").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void uncaughtRuntimeExceptionLiteralBuildsAsNativePanic() throws Exception {
        final Path project = project("exception-panic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new IllegalStateException("boom");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/exception-panic").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).isEqualTo("boom\n");
    }

    @Test
    void reachableExplicitThrowCatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("try-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException("boom");
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/try-catch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("boom\n");
    }

    @Test
    void typedCatchSkipsNonMatchingSpecificHandler() throws Exception {
        final Path project = project("typed-catch-specific-miss");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new Error("typed");
                    } catch (final IllegalStateException exception) {
                        System.out.println("wrong");
                    } catch (final Throwable throwable) {
                        System.out.println("right:" + throwable.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-specific-miss").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("right:typed\n");
    }

    @Test
    void typedCatchMatchesRuntimeExceptionSuperclass() throws Exception {
        final Path project = project("typed-catch-runtime-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException("runtime");
                    } catch (final RuntimeException exception) {
                        System.out.println("runtime:" + exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-runtime-superclass").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("runtime:runtime\n");
    }

    @Test
    void typedCatchMatchesIoExceptionSuperclass() throws Exception {
        final Path project = project("typed-catch-io-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.FileNotFoundException;
            import java.io.IOException;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new FileNotFoundException("missing");
                    } catch (final IOException exception) {
                        System.out.println("io:" + exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-io-superclass").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("io:missing\n");
    }

    @Test
    void typedCatchMatchesUtilRuntimeExceptionSuperclass() throws Exception {
        final Path project = project("typed-catch-util-runtime-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.NoSuchElementException;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new NoSuchElementException("empty");
                    } catch (final RuntimeException exception) {
                        System.out.println("runtime:" + exception.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-util-runtime-superclass").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("runtime:empty\n");
    }

    @Test
    void typedCatchDoesNotMatchErrorAsException() throws Exception {
        final Path project = project("typed-catch-error-not-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new Error("error");
                    } catch (final Exception exception) {
                        System.out.println("wrong");
                    } catch (final Throwable throwable) {
                        System.out.println("throwable:" + throwable.getMessage());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/typed-catch-error-not-exception").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("throwable:error\n");
    }

    @Test
    void defaultConstructedExceptionMessageIsNullWhenCaught() throws Exception {
        final Path project = project("exception-default-message-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException();
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage() == null);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/exception-default-message-null").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void defaultConstructedUncaughtExceptionPanicIsDeterministic() throws Exception {
        final Path project = project("exception-default-panic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new IllegalStateException();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/exception-default-panic").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).isEqualTo("javan panic\n");
    }

    @Test
    void broadTryCatchWithoutDirectThrowStillFailsDuringCheck() throws Exception {
        final Path project = project("try-catch-broad");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println("safe");
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN014]", "direct explicit athrow");
    }

    @Test
    void throwableConstructorWithCauseFailsDuringCheck() throws Exception {
        final Path project = project("exception-cause-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        throw new IllegalStateException("outer", new RuntimeException("cause"));
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN014]");
    }

    @Test
    void basicEnumNameBuilds() throws Exception {
        final Path project = project("enum-basic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color selected = Color.RED;
                    System.out.println(selected.name());
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-basic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("RED\n");
    }

    @Test
    void enumValueOfFailsDuringCheck() throws Exception {
        final Path project = project("enum-value-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Color.valueOf("RED").name());
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN015]", "Enum.valueOf");
    }

    @Test
    void enumOrdinalBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-ordinal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color selected = Color.BLUE;
                    System.out.println(selected.ordinal());
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-ordinal").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void enumValuesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color[] values = Color.values();
                    System.out.println(values.length);
                    System.out.println(values[1].name());
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-values").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\nBLUE\n");
    }

    @Test
    void enumSwitchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("enum-switch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Color selected = Color.BLUE;
                    switch (selected) {
                        case RED -> System.out.println("red");
                        case BLUE -> System.out.println("blue");
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            public enum Color {
                RED,
                BLUE
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-switch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("blue\n");
    }

    @Test
    void stringIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("string-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = "javan";
                    final int code = value.charAt(1);
                    System.out.println(value.length());
                    System.out.println(code);
                    System.out.println(value.equals("javan"));
                    System.out.println(value.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("5\n97\ntrue\nfalse\n");
    }

    @Test
    void stringContainsIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".contains("native"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-contains").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void stringFromCharArrayRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-from-char-array-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final char[] chars = new char[] {'j', 'a', 'v', 'a', 'n'};
                    System.out.println(new String(chars, 1, 3));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-from-char-array-range").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ava\n");
    }

    @Test
    void stringStartsWithBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-starts-with");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".startsWith("javan"));
                    System.out.println("javan native".startsWith("native"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-starts-with").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void stringIndexOfCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".indexOf('v'));
                    System.out.println("javan".indexOf('x'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-char").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n-1\n");
    }

    @Test
    void stringIndexOfCharFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-char-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".indexOf('a', 2));
                    System.out.println("javan".indexOf('a', -2));
                    System.out.println("javan".indexOf('a', 9));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-char-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringIndexOfStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".indexOf("native"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringIndexOfStringFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-index-of-string-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native native".indexOf("native", 7));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-index-of-string-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLastIndexOfCharFromIndexBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-last-index-of-char-from-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".lastIndexOf('a', 3));
                    System.out.println("javan".lastIndexOf('a', -1));
                    System.out.println("javan".lastIndexOf('a', 9));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-last-index-of-char-from-index").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLastIndexOfCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-last-index-of-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".lastIndexOf('a'));
                    System.out.println("javan".lastIndexOf('x'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-last-index-of-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringSubstringBeginBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-substring-begin");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".substring(6));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-substring-begin").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringSubstringRangeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-substring-range");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".substring(0, 5));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-substring-range").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringEndsWithBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-ends-with");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan native".endsWith("native"));
                    System.out.println("javan native".endsWith("javan"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-ends-with").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void stringReplaceCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-replace-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("com.acme.Main".replace('.', '/'));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-replace-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringTrimBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-trim");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("\\t javan \\n".trim());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-trim").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n");
    }

    @Test
    void stringInternBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-intern");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("javan".intern());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-intern").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void stringLiteralWithControlCharacterBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-literal-control-character");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("A\\001B");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-literal-control-character").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void systemLineSeparatorIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-line-separator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.lineSeparator().length());
                    System.out.println("a" + System.lineSeparator() + "b");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-line-separator").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\na\nb\n");
    }

    @Test
    void fileSeparatorCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("file-separator-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println((int) java.io.File.separatorChar);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/file-separator-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filePathSeparatorCharBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("file-path-separator-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println((int) java.io.File.pathSeparatorChar);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/file-path-separator-char").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filePathSeparatorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("file-path-separator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(java.io.File.pathSeparator);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/file-path-separator").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void systemGetenvIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-getenv");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.getenv("PATH"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-getenv").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void systemGetPropertyDefaultBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-get-property-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.getProperty("javan.missing", "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-get-property-default").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void stringBuilderAppendBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append("javan");
                    builder.append('-');
                    builder.append(25);
                    builder.append('-');
                    builder.append(9L);
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan-25-9\n");
    }

    @Test
    void stringBuilderAppendBooleanBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-append-boolean");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder();
                    builder.append(true);
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-append-boolean").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void stringBuilderSetLengthBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("stringbuilder-set-length");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final StringBuilder builder = new StringBuilder("javan");
                    builder.setLength(4);
                    System.out.println(builder.length());
                    System.out.println(builder.toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/stringbuilder-set-length").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\njava\n");
    }

    @Test
    void optionalOrElseBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").orElse("fallback"));
                    System.out.println(Optional.empty().orElse("fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nfallback\n");
    }

    @Test
    void optionalGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-get").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void optionalEmptyOrElseThrowFailsAtRuntime() throws Exception {
        final Path project = project("optional-empty-or-else-throw");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.empty().orElseThrow();
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/optional-empty-or-else-throw").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("optional is empty");
    }

    @Test
    void integerLongToStringIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("number-to-string-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.toString(123));
                    System.out.println(Integer.toString(Integer.MIN_VALUE));
                    System.out.println(Long.toString(9876543210L));
                    System.out.println(Long.toString(Long.MIN_VALUE));
                    System.out.println(Integer.toString(-7).equals("-7"));
                    System.out.println(Long.toString(-9L).length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/number-to-string-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("123\n-2147483648\n9876543210\n-9223372036854775808\ntrue\n2\n");
    }

    @Test
    void floatToStringIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("float-to-string-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Float.toString(1.5f));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-to-string-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void doubleToStringIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("double-to-string-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Double.toString(1.5d));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/double-to-string-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void floatIntBitsToFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("float-int-bits-to-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Float.intBitsToFloat(1069547520));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-int-bits-to-float").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void doubleLongBitsToDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("double-long-bits-to-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Double.longBitsToDouble(4609434218613702656L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/double-long-bits-to-double").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void jdkMathIntrinsicsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("jdk-math-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.abs(-9));
                    System.out.println(Math.abs(Integer.MIN_VALUE));
                    System.out.println(Math.min(4, -7));
                    System.out.println(Math.max(4, -7));
                    System.out.println(Math.abs(-12L));
                    System.out.println(Math.abs(Long.MIN_VALUE));
                    System.out.println(Math.min(100L, -200L));
                    System.out.println(Math.max(100L, -200L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/jdk-math-intrinsics").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("9\n-2147483648\n-7\n4\n12\n-9223372036854775808\n-200\n100\n");
    }

    @Test
    void mathToIntExactBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("math-to-int-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.toIntExact(123456789L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/math-to-int-exact").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("123456789\n");
    }

    @Test
    void mathToIntExactOverflowFailsAtRuntime() throws Exception {
        final Path project = project("math-to-int-exact-overflow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.toIntExact(2147483648L));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/math-to-int-exact-overflow").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("integer overflow");
    }

    @Test
    void jdkSystemTimeIntrinsicsBuildAndReturnLongValues() throws Exception {
        final Path project = project("jdk-time-intrinsics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(System.nanoTime());
                    System.out.println(System.currentTimeMillis());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final String nativeOutput = process(project, List.of(project.resolve(".javan/bin/jdk-time-intrinsics").toString())).stdout();
        assertThat(nativeOutput).matches("[0-9]+\\n[0-9]+\\n");
    }

    @Test
    void systemExitBuildsAndReturnsStatusCode() throws Exception {
        final Path project = project("system-exit");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.exit(7);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/system-exit").toString()));

        assertThat(nativeRun.exitCode()).isEqualTo(7);
    }

    @Test
    void objectsRequireNonNullIntrinsicBuildsAndChecksNull() throws Exception {
        final Path project = project("objects-require-non-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = Objects.requireNonNull("javan");
                    System.out.println(value);
                    System.out.println(value.length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n5\n");

        final Path nullProject = project("objects-require-non-null-null");
        writeJava(nullProject, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    Objects.requireNonNull(value);
                    System.out.println("unreachable");
                }
            }
            """);
        final CliRun nullBuild = run(tempDir, "build", nullProject.toString());
        assertThat(nullBuild.exitCode()).isZero();

        final ProcessResult nullRun = process(
            nullProject,
            List.of(nullProject.resolve(".javan/bin/objects-require-non-null-null").toString())
        );
        assertThat(nullRun.exitCode()).isEqualTo(1);
        assertThat(nullRun.stdout()).isEmpty();
        assertThat(nullRun.stderr()).contains("null object");
    }

    @Test
    void objectsRequireNonNullMessageBuildsAndReportsMessage() throws Exception {
        final Path project = project("objects-require-non-null-message");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    Objects.requireNonNull(value, "value");
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-message").toString()));

        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stderr()).contains("value");
    }

    @Test
    void systemArraycopyIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-arraycopy-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[] {1, 2, 3, 4};
                    System.arraycopy(values, 1, values, 2, 2);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                    System.out.println(values[2]);
                    System.out.println(values[3]);

                    final byte[] bytes = new byte[] {7, 8, 9};
                    final byte[] targetBytes = new byte[4];
                    System.arraycopy(bytes, 0, targetBytes, 1, 3);
                    System.out.println(targetBytes[0]);
                    System.out.println(targetBytes[1]);
                    System.out.println(targetBytes[3]);

                    final String[] names = new String[] {"a", "b", null};
                    final String[] targetNames = new String[3];
                    System.arraycopy(names, 0, targetNames, 0, 3);
                    System.out.println(targetNames[0]);
                    System.out.println(targetNames[1]);
                    System.out.println(targetNames.length);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-arraycopy-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains("{\"name\": \"System.arraycopy\", \"count\": 3}");
    }

    @Test
    void arraysCopyOfIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] ints = Arrays.copyOf(new int[] {4, 5}, 4);
                    System.out.println(ints.length);
                    System.out.println(ints[0]);
                    System.out.println(ints[2]);

                    final long[] longs = Arrays.copyOf(new long[] {8L, 9L}, 1);
                    System.out.println(longs.length);
                    System.out.println(longs[0]);

                    final byte[] bytes = Arrays.copyOf(new byte[] {1, 2}, 3);
                    System.out.println(bytes[2]);

                    final short[] shorts = Arrays.copyOf(new short[] {3, 4}, 1);
                    System.out.println(shorts[0]);

                    final char[] chars = Arrays.copyOf(new char[] {'j'}, 2);
                    System.out.println((int) chars[0]);
                    System.out.println((int) chars[1]);

                    final float[] floats = Arrays.copyOf(new float[] {1.5f}, 2);
                    System.out.println(floats[0]);
                    System.out.println(floats[1]);

                    final double[] doubles = Arrays.copyOf(new double[] {2.25d}, 2);
                    System.out.println(doubles[0]);
                    System.out.println(doubles[1]);

                    final Object[] objects = Arrays.copyOf(new Object[] {"x", "y"}, 3);
                    System.out.println(objects.length);
                    System.out.println(objects[0]);
                    System.out.println(objects[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains("{\"name\": \"Arrays.copyOf\", \"count\": 8}");
    }

    @Test
    void arraysCopyOfRangeByteBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-range-byte");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final byte[] values = Arrays.copyOfRange(new byte[] {4, 5}, 1, 4);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                    System.out.println(values[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-range-byte").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n5\n0\n0\n");
    }

    @Test
    void arraysCopyOfRangeObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-range-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = Arrays.copyOfRange(new String[] {"a", "b", "c"}, 1, 3);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-range-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\nb\nc\n");
    }

    @Test
    void arrayListAddAndGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-add-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    System.out.println(values.get(0));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-add-get").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\n");
    }

    @Test
    void arrayListInitialCapacityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-initial-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(4);
                    values.add("capacity");
                    System.out.println(values.get(0));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-initial-capacity").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("capacity\n");
    }

    @Test
    void listContainsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("Synthetic");
                    System.out.println(values.contains("Synthetic"));
                    System.out.println(values.contains("Deprecated"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-contains").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapStringGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-string-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-string-get").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapContainsKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-contains-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.containsKey("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-contains-key").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapGetOrDefaultMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-get-or-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.getOrDefault("missing", "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-get-or-default").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapPutIfAbsentMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-put-if-absent-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    values.putIfAbsent("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-put-if-absent-missing").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapPutIfAbsentExistingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-put-if-absent-existing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    values.put("left", "right");
                    values.putIfAbsent("left", "changed");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-put-if-absent-existing").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapValuesBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("a", "one");
                    values.put("b", "two");
                    for (final String value : values.values()) {
                        System.out.println(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-values").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void mapCopyOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-copy-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> mutable = new HashMap<>();
                    mutable.put("left", "right");
                    final Map<String, String> snapshot = Map.copyOf(mutable);
                    mutable.put("left", "changed");
                    System.out.println(snapshot.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-copy-of").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void arrayListIndexedAddBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-indexed-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("right");
                    values.add(1, "middle");
                    System.out.println(values.get(0));
                    System.out.println(values.get(1));
                    System.out.println(values.get(2));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-indexed-add").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nmiddle\nright\n");
    }

    @Test
    void listCopyOfSnapshotsSourceList() throws Exception {
        final Path project = project("list-copy-of-snapshot");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    final List<String> snapshot = List.copyOf(values);
                    values.add("right");
                    System.out.println(snapshot.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-copy-of-snapshot").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void arrayListCollectionConstructorCopiesSourceList() throws Exception {
        final Path project = project("arraylist-copy-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> copy = new ArrayList<>(List.of("left"));
                    System.out.println(copy.getFirst());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-copy-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\n");
    }

    @Test
    void listOfNineArgumentsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-of-nine-arguments");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i");
                    System.out.println(values.get(8));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-of-nine-arguments").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("i\n");
    }

    @Test
    void listIteratorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-iterator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("right");
                    for (final String value : values) {
                        System.out.println(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-iterator").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void listOfImmutableAddFailsAtRuntime() throws Exception {
        final Path project = project("list-of-immutable-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = List.of("left");
                    values.add("right");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/list-of-immutable-add").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("unsupported operation on immutable list");
    }

    @Test
    void pathResolveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("path-resolve");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Path.of("data").resolve("message.txt").toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-resolve").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void pathOperationsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("path-operations");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Path path = Path.of("data", "message.txt");
                    System.out.println(path.getFileName().toString());
                    System.out.println(path.getParent().toString());
                    System.out.println(path.getNameCount());
                    System.out.println(path.getName(0).toString());
                    System.out.println(path.startsWith(Path.of("data")));
                    System.out.println(Path.of("data").relativize(path).toString());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/path-operations").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesReadStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-read-string");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/message.txt"), "file-ok", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.readString(Path.of("data").resolve("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-read-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesWriteStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-write-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Files.writeString(Path.of("message.txt"), "written");
                    System.out.println(Files.readString(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.deleteIfExists(project.resolve("message.txt"));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-write-string").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesReadAllBytesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-read-all-bytes");
        Files.createDirectories(project.resolve("data"));
        Files.write(project.resolve("data/message.bin"), new byte[]{7, 8, 9});
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final byte[] bytes = Files.readAllBytes(Path.of("data").resolve("message.bin"));
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-read-all-bytes").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesWriteBytesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-write-bytes");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Files.write(Path.of("message.bin"), new byte[] {65, 66});
                    final byte[] bytes = Files.readAllBytes(Path.of("message.bin"));
                    System.out.println(bytes.length);
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.deleteIfExists(project.resolve("message.bin"));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-write-bytes").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n65\n66\n");
    }

    @Test
    void filesNewDirectoryStreamBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-directory-stream");
        Files.createDirectories(project.resolve("data"));
        Files.writeString(project.resolve("data/a.txt"), "a", StandardCharsets.UTF_8);
        Files.writeString(project.resolve("data/b.txt"), "b", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.DirectoryStream;
            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of("data"));
                    int count = 0;
                    int sawA = 0;
                    int sawB = 0;
                    for (final Path child : stream) {
                        final String name = child.getFileName().toString();
                        count++;
                        if ("a.txt".equals(name)) {
                            sawA = 1;
                        }
                        if ("b.txt".equals(name)) {
                            sawB = 1;
                        }
                    }
                    stream.close();
                    System.out.println(count);
                    System.out.println(sawA);
                    System.out.println(sawB);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-directory-stream").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesCreateDirectoriesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-create-directories");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Path target = Path.of(".javan").resolve("created").resolve("child");
                    Files.createDirectories(target);
                    System.out.println(Files.isDirectory(target));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.deleteIfExists(project.resolve(".javan/created/child"));
        Files.deleteIfExists(project.resolve(".javan/created"));
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-create-directories").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesIsDirectoryNoFollowLinksBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-is-directory-no-follow");
        Files.createDirectories(project.resolve("target-dir"));
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.LinkOption;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Files.isDirectory(Path.of("target-dir"), LinkOption.NOFOLLOW_LINKS));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-is-directory-no-follow").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesIsExecutableBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-is-executable");
        Files.writeString(project.resolve("plain.txt"), "plain", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Files.isExecutable(Path.of("plain.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-is-executable").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesSizeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-size");
        Files.writeString(project.resolve("message.txt"), "javan-size", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Files.size(Path.of("message.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-size").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void filesCopyWithReplaceExistingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("files-copy");
        Files.writeString(project.resolve("source.txt"), "copy-ok", StandardCharsets.UTF_8);
        Files.writeString(project.resolve("target.txt"), "old", StandardCharsets.UTF_8);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.nio.file.StandardCopyOption;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    Files.copy(Path.of("source.txt"), Path.of("target.txt"), StandardCopyOption.REPLACE_EXISTING);
                    System.out.println(Files.readString(Path.of("target.txt")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        Files.writeString(project.resolve("target.txt"), "old", StandardCharsets.UTF_8);
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/files-copy").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void unsupportedJdkIntrinsicOverloadsFailClearly() throws Exception {
        final Path mathProject = project("unsupported-math-overload");
        writeJava(mathProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Math.abs(1.25f));
                }
            }
            """);

        final CliRun mathRun = run(tempDir, "build", mathProject.toString());

        assertThat(mathRun.exitCode()).isEqualTo(2);
        assertThat(mathRun.stderr()).contains("error[JAVAN031]", "java/lang/Math.abs(F)F");
        assertThat(Files.readString(mathProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Math.abs\", \"count\": 0}",
                "{\"target\": \"java/lang/Math.abs(F)F\", \"count\": 1}"
            );

        final Path objectsProject = project("unsupported-objects-overload");
        writeJava(objectsProject, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Objects.requireNonNullElse(null, "fallback");
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun objectsRun = run(tempDir, "build", objectsProject.toString());

        assertThat(objectsRun.exitCode()).isEqualTo(2);
        assertThat(objectsRun.stderr()).contains(
            "error[JAVAN031]",
            "java/util/Objects.requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        assertThat(Files.readString(objectsProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Objects.requireNonNull\", \"count\": 0}",
                "{\"target\": \"java/util/Objects.requireNonNullElse(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;\", \"count\": 1}"
            );

        final Path arraysProject = project("unsupported-arrays-copy-of-overload");
        writeJava(arraysProject, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] values = Arrays.copyOf(new boolean[] {true}, 2);
                    System.out.println(values[0]);
                }
            }
            """);

        final CliRun arraysRun = run(tempDir, "build", arraysProject.toString());

        assertThat(arraysRun.exitCode()).isEqualTo(2);
        assertThat(arraysRun.stderr()).contains("error[JAVAN031]", "java/util/Arrays.copyOf([ZI)[Z");
        assertThat(Files.readString(arraysProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Arrays.copyOf\", \"count\": 0}",
                "{\"target\": \"java/util/Arrays.copyOf([ZI)[Z\", \"count\": 1}"
            );

        final Path numberProject = project("unsupported-number-to-string-overload");
        writeJava(numberProject, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Integer.toString(15, 16));
                    System.out.println(Long.toString(31L, 16));
                }
            }
            """);

        final CliRun numberRun = run(tempDir, "build", numberProject.toString());

        assertThat(numberRun.exitCode()).isEqualTo(2);
        assertThat(numberRun.stderr()).contains("error[JAVAN031]");
        assertThat(Files.readString(numberProject.resolve(".javan/reports/intrinsics.json")))
            .contains(
                "{\"name\": \"Integer.toString\", \"count\": 0}",
                "{\"name\": \"Long.toString\", \"count\": 0}",
                "{\"target\": \"java/lang/Integer.toString(II)Ljava/lang/String;\", \"count\": 1}",
                "{\"target\": \"java/lang/Long.toString(JI)Ljava/lang/String;\", \"count\": 1}"
            );
    }

    @Test
    void systemArraycopyPrimitiveTypeMismatchFailsAtRuntime() throws Exception {
        final Path project = project("system-arraycopy-type-mismatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object source = new byte[] {1};
                    final Object target = new boolean[1];
                    System.arraycopy(source, 0, target, 0, 1);
                    System.out.println("unreachable");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/system-arraycopy-type-mismatch").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains("array copy type mismatch");
    }

    @Test
    void longArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("long-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final long[] values = new long[]{1L, 2L};
                    values[1] = 9L;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1\n9\n");
    }

    @Test
    void instanceOfExactApplicationClassBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new Box();
                    System.out.println(value instanceof Box);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box {
                public Box() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-exact").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfNullReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    System.out.println(value instanceof Box);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box {
                public Box() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-null").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfSuperclassBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new Child();
                    System.out.println(value instanceof Base);
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                public Base() {
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base {
                public Child() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-superclass").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void instanceOfInterfaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("instanceof-interface");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = new EnglishGreeter();
                    System.out.println(value instanceof Greeter);
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                public EnglishGreeter() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instanceof-interface").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedIntegerUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-integer-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Integer value = Integer.valueOf(7);
                    System.out.println(value.intValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-integer-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedLongUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-long-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Long value = Long.valueOf(7L);
                    System.out.println(value.longValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-long-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedFloatUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-float-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Float value = Float.valueOf(1.5f);
                    System.out.println(value.floatValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-float-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedDoubleUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-double-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Double value = Double.valueOf(1.5d);
                    System.out.println(value.doubleValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-double-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedIntegerInstanceOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-integer-instanceof");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = Integer.valueOf(3);
                    System.out.println(value instanceof Integer);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-integer-instanceof").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intShiftLeftBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-shift-left");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = 1;
                    int shift = 33;
                    System.out.println(value << shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-shift-left").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longShiftLeftBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-shift-left");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = 1L;
                    int shift = 65;
                    System.out.println(value << shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-shift-left").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intSignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-signed-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = -8;
                    int shift = 1;
                    System.out.println(value >> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-signed-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longSignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-signed-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = -8L;
                    int shift = 1;
                    System.out.println(value >> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-signed-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intUnsignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-unsigned-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = -1;
                    int shift = 1;
                    System.out.println(value >>> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-unsigned-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longUnsignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-unsigned-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = -1L;
                    int shift = 1;
                    System.out.println(value >>> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-unsigned-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void polymorphicSuperclassVirtualCallBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("polymorphic-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Base value = new Child();
                    System.out.println(value.text());
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                public Base() {
                }

                public String text() {
                    return "base";
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base {
                public Child() {
                }

                public String text() {
                    return "child";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/polymorphic-call").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("child\n");
    }

    @Test
    void singleImplementationInterfaceDispatchBuilds() throws Exception {
        final Path project = project("interface-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new EnglishGreeter("Ada");
                    System.out.println(greeter.greet());
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                String greet();
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                private final String name;

                public EnglishGreeter(final String name) {
                    this.name = name;
                }

                public String greet() {
                    return name;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-dispatch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("Ada\n");
    }

    @Test
    void multiImplementationInterfaceDispatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("interface-ambiguous");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new EnglishGreeter();
                    System.out.println(greeter.greet());
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                String greet();
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                public EnglishGreeter() {
                }

                public String greet() {
                    return "hello";
                }
            }
            """);
        writeJava(project, "com.acme.GermanGreeter", """
            package com.acme;

            public final class GermanGreeter implements Greeter {
                public GermanGreeter() {
                }

                public String greet() {
                    return "hallo";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-ambiguous").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("hello\n");
    }

    @Test
    void dependencyJarStaticIntMethodBuilds() throws Exception {
        final Path dependency = dependencyJar("mathlib", "dep.MathLib", """
            package dep;

            public final class MathLib {
                private MathLib() {
                }

                public static int twice(final int value) {
                    return value * 2;
                }
            }
            """);
        final Path project = project("dependency-jar");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.MathLib;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(MathLib.twice(21));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-jar").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n");
    }

    @Test
    void dependencyJarObjectConstructorAndInstanceMethodBuilds() throws Exception {
        final Path dependency = dependencyJar("scalelib", "dep.Scale", """
            package dep;

            public final class Scale {
                private final int base;

                public Scale(final int base) {
                    this.base = base;
                }

                public int apply(final int value) {
                    return base * value;
                }
            }
            """);
        final Path project = project("dependency-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Scale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Scale scale = new Scale(7);
                    System.out.println(scale.apply(6));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n");
    }

    @Test
    void dependencyJarObjectStringReturnBuilds() throws Exception {
        final Path dependency = dependencyJar("messagelib", "dep.Message", """
            package dep;

            public final class Message {
                private final String value;

                public Message(final String value) {
                    this.value = value;
                }

                public String value() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-string-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Message;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("from-dependency");
                    System.out.println(message.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-string-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("from-dependency\n");
    }

    @Test
    void dependencyClassDirectoryObjectStringReturnBuilds() throws Exception {
        final Path dependency = dependencyClasses("messageclasses", "dep.Message", """
            package dep;

            public class Message {
                private final String value;

                public Message(final String value) {
                    this.value = value;
                }

                public String value() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-class-directory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Message;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("from-classes");
                    System.out.println(message.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-class-directory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("from-classes\n");
    }

    @Test
    void dependencyJarMainDoesNotConfuseMainDetection() throws Exception {
        final Path dependency = dependencyJar("dep-main", "dep.Tool", """
            package dep;

            public final class Tool {
                private Tool() {
                }

                public static void main(final String[] args) {
                    System.out.println("dependency-main");
                }

                public static int value() {
                    return 7;
                }
            }
            """);
        final Path project = project("dependency-main");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Tool;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Tool.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-main").toString())).stdout()).isEqualTo("7\n");
    }

    @Test
    void unreachableForbiddenApiInsideDependencyDoesNotWarn() throws Exception {
        final Path dependency = dependencyJar("dep-dead-reflection", "dep.Safe", """
            package dep;

            public final class Safe {
                private Safe() {
                }

                public static int value() {
                    return 5;
                }

                public static void dead() throws ClassNotFoundException {
                    Class.forName("dep.Plugin");
                }
            }
            """);
        final Path project = project("dependency-dead-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Safe;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Safe.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).doesNotContain("warning[JAVAN101]");
    }

    @Test
    void reachableForbiddenApiInsideDependencyFails() throws Exception {
        final Path dependency = dependencyJar("dep-live-reflection", "dep.Loader", """
            package dep;

            public final class Loader {
                private Loader() {
                }

                public static void load() throws ClassNotFoundException {
                    Class.forName("dep.Plugin");
                }
            }
            """);
        final Path project = project("dependency-live-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Loader;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws ClassNotFoundException {
                    Loader.load();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "java/lang/Class.forName");
    }

    @Test
    void reachableReflectionFails() throws Exception {
        final Path project = project("reachable-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws ClassNotFoundException {
                    Class.forName("com.acme.Plugin");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "dynamic class loading is not supported");
    }

    @Test
    void unreachableReflectionWarnsOnly() throws Exception {
        final Path project = project("unreachable-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void load() throws ClassNotFoundException {
                    Class.forName("com.acme.Plugin");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN101]");
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.txt"))).contains("warning[JAVAN101]");
    }

    @Test
    void noMainFails() throws Exception {
        final Path project = project("no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN020]");
    }

    @Test
    void multipleMainFailsWithoutGuessing() throws Exception {
        final Path project = project("multiple-main");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeJava(project, "com.acme.Tool", mainClass("tool"));

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN022]", "com.acme.Main", "com.acme.Tool");
    }

    @Test
    void explicitMainSelectsCandidate() throws Exception {
        final Path project = project("explicit-main");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeJava(project, "com.acme.Tool", mainClass("tool"));

        final CliRun run = run(tempDir, "build", project.toString(), "--main", "com.acme.Tool", "--output", "selected");

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/selected").toString())).stdout()).isEqualTo("tool\n");
    }

    @Test
    void cleanRemovesOutputDirectory() throws Exception {
        final Path project = project("clean");
        Files.createDirectories(project.resolve(".javan/reports"));
        Files.writeString(project.resolve(".javan/reports/project.json"), "{}");

        final CliRun run = run(tempDir, "clean", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan")).doesNotExist();
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
    void compatWritesDeterministicReports() throws Exception {
        final Path project = project("compat-pass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[] {"zero", "one"};
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final CliRun run = runSlow(tempDir, "compat", project.toString());

        final int jdk = Runtime.version().feature();
        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Compatibility:", "status:          pass", "jdk classes:");
        assertThat(project.resolve(".javan/reports/compatibility-summary.md")).exists();
        assertThat(project.resolve(".javan/reports/compatibility-summary.json")).exists();
        assertThat(project.resolve(".javan/reports/jdk-" + jdk + "-inventory.json")).exists();
        assertThat(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve(".javan/jdk-inventory/jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve(".javan/bytecode-patterns/jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve("doc/support-matrix.md")).exists();
        assertThat(project.resolve("doc/support-matrix.json")).exists();
        assertThat(project.resolve("doc/jdk-compatibility.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/compatibility-summary.json")))
            .contains("\"status\": \"pass\"", "\"unknownFatalOpcodeUses\": 0");
        assertThat(Files.readString(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")))
            .contains("\"mnemonic\": \"anewarray\"", "\"mnemonic\": \"arraylength\"", "\"support\": \"NATIVE_SUPPORTED\"");
        assertThat(Files.readString(project.resolve("doc/support-matrix.md")))
            .contains(
                "object-array",
                "long-array",
                "float-double",
                "static-fields",
                "string-concat",
                "try-catch",
                "polymorphic-virtual",
                "interface-polymorphic",
                "JDK" + jdk
            );
    }

    @Test
    void compatReportsSupportedInvokedynamicStringConcat() throws Exception {
        final Path project = project("compat-string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("value " + args.length);
                }
            }
            """);

        final CliRun run = runSlow(tempDir, "compat", project.toString());

        final int jdk = Runtime.version().feature();
        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("status:          pass");
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(project.resolve(".javan/reports/compatibility-summary.json")))
            .contains("\"status\": \"pass\"");
        assertThat(Files.readString(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")))
            .contains("\"mnemonic\": \"invokedynamic\"", "\"support\": \"NATIVE_SUPPORTED\"");
    }

    @Test
    void sourceFileInputBuilds() throws Exception {
        final Path source = tempDir.resolve("Single.java");
        Files.writeString(source, """
            public final class Single {
                private Single() {
                }

                public static void main(final String[] args) {
                    System.out.println("single");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", source.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(tempDir, List.of(tempDir.resolve(".javan/bin/single").toString())).stdout()).isEqualTo("single\n");
    }

    @Test
    void classesDirectoryInputChecks() throws Exception {
        final Path project = project("classes-input");
        writeJava(project, "com.acme.Main", mainClass("main"));
        final Path classes = project.resolve("manual-classes");
        Files.createDirectories(classes);
        assertThat(process(project, List.of("javac", "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString())).exitCode())
            .isZero();

        final CliRun run = run(tempDir, "check", classes.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 1");
    }

    @Test
    void jarInputBuilds() throws Exception {
        final Path project = project("jar-input");
        writeJava(project, "com.acme.Main", mainClass("main"));
        final Path classes = project.resolve("classes");
        final Path jar = project.resolve("app.jar");
        Files.createDirectories(classes);
        assertThat(process(project, List.of("javac", "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of("jar", "--create", "--file", jar.toString(), "--main-class", "com.acme.Main", "-C", classes.toString(), ".")).exitCode())
            .isZero();

        final CliRun run = run(tempDir, "build", jar.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/app").toString())).stdout()).isEqualTo("main\n");
    }

    @Test
    void jarBuildDoesNotRequireMain() throws Exception {
        final Path project = project("jar-no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-no-main.jar")).exists();
    }

    @Test
    void jarAliasBuildDoesNotRequireMain() throws Exception {
        final Path project = project("jar-alias");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--jar");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-alias.jar")).exists();
    }

    @Test
    void jarBuildIncludesResourceFile() throws Exception {
        final Path project = project("jar-resource");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);
        writeResource(project, "public/index.html", "<h1>javan</h1>\n");

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-resource.jar").toFile())) {
            assertThat(jar.getEntry("public/index.html")).isNotNull();
            assertThat(new String(jar.getInputStream(jar.getEntry("public/index.html")).readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<h1>javan</h1>\n");
        }
    }

    @Test
    void jarBuildRemovesDeletedResourceFile() throws Exception {
        final Path project = project("jar-resource-delete");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);
        final Path resource = writeResource(project, "public/stale.txt", "stale\n");
        assertThat(run(tempDir, "build", project.toString(), "--kind", "jar").exitCode()).isZero();
        Files.delete(resource);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-resource-delete.jar").toFile())) {
            assertThat(jar.getEntry("public/stale.txt")).isNull();
        }
    }

    @Test
    void jarBuildWritesMainClassManifestWhenMainExplicit() throws Exception {
        final Path project = project("jar-manifest");
        writeJava(project, "com.acme.Main", mainClass("main"));

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar", "--main", "com.acme.Main");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-manifest.jar").toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class")).isEqualTo("com.acme.Main");
        }
    }

    @Test
    void jarBuildAllowsJvmOnlyBytecode() throws Exception {
        final Path project = project("jar-jvm-only");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    synchronized (Main.class) {
                        System.out.println("jar");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar", "--main", "com.acme.Main");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-jvm-only.jar")).exists();
    }

    @Test
    void nativeBuildCopiesResourcesToDistribution() throws Exception {
        final Path project = project("native-resources");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeResource(project, "assets/logo.txt", "logo\n");

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/resources/assets/logo.txt"))).isEqualTo("logo\n");
        assertThat(Files.readString(project.resolve(".javan/dist/resources/assets/logo.txt"))).isEqualTo("logo\n");
        assertThat(Files.readString(project.resolve(".javan/reports/resources.json")))
            .contains("\"resourceCount\": 1", "\"path\": \"assets/logo.txt\"");
    }

    @Test
    void inspectMavenProjectUsesArtifactId() throws Exception {
        final Path project = project("maven-project");
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>sharp-tool</artifactId>
              <version>1.0.0</version>
            </project>
            """);

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: MAVEN", ".javan/bin/sharp-tool");
    }

    @Test
    void mavenProjectCheckFindsClassesAfterCompile() throws Exception {
        final Path project = project("maven-check");
        Files.writeString(project.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>maven-check</artifactId>
              <version>1.0.0</version>
              <properties>
                <maven.compiler.release>25</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
            </project>
            """);
        writeJava(project, "com.acme.Main", mainClass("main"));

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 1");
        assertThat(project.resolve("target/classes/com/acme/Main.class")).exists();
    }

    @Test
    void inspectGradleProjectUsesRootProjectName() throws Exception {
        final Path project = project("gradle-project");
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'blade-tool'\n");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: GRADLE", ".javan/bin/blade-tool");
    }

    @Test
    void unknownOptionFails() {
        final CliRun run = run(tempDir, "check", "--wat");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("Unknown option");
    }

    @Test
    void missingOptionValueFails() {
        final CliRun run = run(tempDir, "build", "--main");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("Missing value for --main");
    }

    @Test
    void escapedStringLiteralBuilds() throws Exception {
        final Path project = project("escaped");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("quote: \\" slash: \\\\ tab:\\t");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/escaped").toString())).stdout()).isEqualTo("quote: \" slash: \\ tab:\t\n");
    }

    @Test
    void intToCharNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-char");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 65537 + args.length;
                    final char value = (char) raw;
                    System.out.println((int) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-char").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void intToFloatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 42 + args.length;
                    final float value = raw;
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-float").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42.0\n");
    }

    @Test
    void intToDoubleBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 42 + args.length;
                    final double value = raw;
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-double").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42.0\n");
    }

    @Test
    void longToIntNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-to-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final long raw = 4_294_967_299L + args.length;
                    final int value = (int) raw;
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-to-int").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n");
    }

    @Test
    void intToByteNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-byte");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 130 + args.length;
                    final byte value = (byte) raw;
                    System.out.println((int) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-byte").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-126\n");
    }

    @Test
    void intToShortNarrowingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-short");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int raw = 32769 + args.length;
                    final short value = (short) raw;
                    System.out.println((int) value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-short").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-32767\n");
    }

    @Test
    void reachableStringConcatBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("string-concat");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("value " + args.length);
                    System.out.println("long " + 42L);
                    System.out.println("float " + 1.25f);
                    System.out.println("double " + 2.5);
                    System.out.println("bool " + true);
                    System.out.println("char " + 'A');
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/string-concat").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value 0\nlong 42\nfloat 1.25\ndouble 2.5\nbool true\nchar A\n");
    }

    @Test
    void reachableSystemLoadFails() throws Exception {
        final Path project = project("system-load");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.loadLibrary("danger");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "loading native libraries");
    }

    @Test
    void reachableInterfaceApplicationVoidCallBuilds() throws Exception {
        final Path project = project("interface-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new ConsoleGreeter();
                    greeter.hello();
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                void hello();
            }
            """);
        writeJava(project, "com.acme.ConsoleGreeter", """
            package com.acme;

            public final class ConsoleGreeter implements Greeter {
                public ConsoleGreeter() {
                }

                public void hello() {
                    System.out.println("virtual");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-call").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("virtual\n");
    }

    @Test
    void unreachableUnsupportedBytecodeWarnsOnly() throws Exception {
        final Path project = project("unreachable-bytecode");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void dead(final int value) {
                    synchronized (Main.class) {
                        final int[][] matrix = new int[1][1];
                        switch (value) {
                            case 1 -> System.out.println("one " + matrix.length);
                            default -> System.out.println("other");
                        }
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN130]");
    }

    @Test
    void missingReachableClassFailsClosedWorldAnalysis() throws Exception {
        final Path project = project("missing-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Missing.call();
                }
            }
            """);
        writeJava(project, "com.acme.Missing", """
            package com.acme;

            public final class Missing {
                private Missing() {
                }

                public static void call() {
                    System.out.println("missing");
                }
            }
            """);

        final CliRun compiled = run(tempDir, "check", project.toString());
        assertThat(compiled.exitCode()).isZero();
        Files.delete(project.resolve(".javan/classes/com/acme/Missing.class"));

        final CliRun run = run(tempDir, "check", project.resolve(".javan/classes").toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN011]");
    }

    @Test
    void unreachableNativeMethodDeclarationDoesNotFail() throws Exception {
        final Path project = project("unreachable-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static native void dead();
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
    }

    @Test
    void mainArgsArrayLengthBuildsAndUsesRuntimeArgs() throws Exception {
        final Path project = project("main-args");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(args.length);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/main-args").toString(), "left", "right")).stdout()).isEqualTo("2\n");
    }

    @Test
    void objectArrayCloneBuildsAndRuns() throws Exception {
        final Path project = project("object-array-clone");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[1];
                    values[0] = "left";
                    final String[] copy = values.clone();
                    values[0] = "right";
                    System.out.println(copy[0]);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array-clone").toString())).stdout()).isEqualTo("left\n");
    }

    @Test
    void intArrayCloneBuildsAndRuns() throws Exception {
        final Path project = project("int-array-clone");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[1];
                    values[0] = 7;
                    final int[] copy = values.clone();
                    values[0] = 9;
                    System.out.println(copy[0]);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array-clone").toString())).stdout()).isEqualTo("7\n");
    }

    @Test
    void objectReferenceCompareBuildsAndRuns() throws Exception {
        final Path project = project("object-reference-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    if (args == args) {
                        System.out.println("same");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-reference-compare").toString())).stdout()).isEqualTo("same\n");
    }

    @Test
    void denseIntSwitchBuildsAndRuns() throws Exception {
        final Path project = project("dense-int-switch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    switch (args.length) {
                        case 1 -> System.out.println("one");
                        case 2 -> System.out.println("two");
                        case 3 -> System.out.println("three");
                        default -> System.out.println("other");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dense-int-switch").toString(), "a", "b")).stdout()).isEqualTo("two\n");
    }

    @Test
    void sparseIntSwitchBuildsAndRuns() throws Exception {
        final Path project = project("sparse-int-switch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    switch (args.length) {
                        case 1 -> System.out.println("one");
                        case 1000 -> System.out.println("many");
                        default -> System.out.println("other");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/sparse-int-switch").toString(), "a")).stdout()).isEqualTo("one\n");
    }

    @Test
    void duplicateStaticCallsAreAnalyzedOnce() throws Exception {
        final Path project = project("duplicate-static");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.print();
                    Helper.print();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void print() {
                    System.out.println("twice");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 2");
    }

    @Test
    void reachableUnsupportedJdkOwnerFailsAsUnsupportedJdkCall() throws Exception {
        final Path project = project("jdk-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import jdk.jfr.FlightRecorder;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    FlightRecorder.isAvailable();
                    System.out.println("ok");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN031]", "jdk/jfr/FlightRecorder.isAvailable()Z");
        assertThat(run.stderr()).doesNotContain("JAVAN011", "JAVAN012");
    }

    @Test
    void reachableUnsupportedSunOwnerFailsAsUnsupportedJdkCall() throws Exception {
        final Path project = project("sun-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import sun.misc.Unsafe;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Unsafe.getUnsafe();
                    System.out.println("ok");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN031]", "sun/misc/Unsafe.getUnsafe()Lsun/misc/Unsafe;");
        assertThat(run.stderr()).doesNotContain("JAVAN011", "JAVAN012");
    }

    @Test
    void reachableNativeMethodDeclarationFails() throws Exception {
        final Path project = project("reachable-native");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    dead();
                }

                public static native void dead();
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN013]");
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

    private static Path writeResource(final Path project, final String name, final String source) throws Exception {
        final Path file = project.resolve("src/main/resources").resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static Path writeC(final Path project, final String filename, final String source) throws Exception {
        final Path file = project.resolve(filename);
        Files.writeString(file, source, StandardCharsets.UTF_8);
        return file;
    }

    private static void writeExecutableScript(final Path script, final String source) throws Exception {
        Files.writeString(script, source.stripIndent(), StandardCharsets.UTF_8);
        assertThat(script.toFile().setExecutable(true)).isTrue();
    }

    private static String sharedLibraryName(final String name) {
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return name + ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "lib" + name + ".dylib";
        }
        return "lib" + name + ".so";
    }

    private static boolean commandAvailable(final String command) {
        try {
            return new ProcessBuilder(command, "--version").start().waitFor() == 0;
        } catch (final Exception exception) {
            return false;
        }
    }

    private static String mainClass(final String value) {
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

    private static CliRun run(final Path cwd, final String... args) {
        return runWithTimeout(cwd, Duration.ofSeconds(20), args);
    }

    private static CliRun runSlow(final Path cwd, final String... args) {
        return runWithTimeout(cwd, Duration.ofSeconds(90), args);
    }

    private static CliRun runWithTimeout(final Path cwd, final Duration timeout, final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = assertTimeoutPreemptively(timeout, () ->
            new Cli().run(cwd, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), args)
        );
        return new CliRun(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private static ProcessResult process(final Path cwd, final List<String> command) {
        return process(cwd, command, Duration.ofSeconds(10));
    }

    private static ProcessResult processSlow(final Path cwd, final List<String> command) {
        return process(cwd, command, Duration.ofSeconds(60));
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

    private static String runJvm(final Path project, final String mainClass) {
        return runJvm(project, mainClass, List.of());
    }

    private static String runJvm(final Path project, final String mainClass, final List<Path> classpathEntries) {
        final Path classes = project.resolve("jvm-classes");
        final Path sourceRoot = project.resolve("src/main/java");
        final List<String> compile = new java.util.ArrayList<>(List.of("javac", "-d", classes.toString()));
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
        assertThat(process(project, compile).exitCode()).isZero();
        final List<Path> runtimeClasspath = new java.util.ArrayList<>();
        runtimeClasspath.add(classes);
        runtimeClasspath.addAll(classpathEntries);
        final ProcessResult run = process(project, List.of(
            "java",
            "-cp",
            String.join(java.io.File.pathSeparator, runtimeClasspath.stream().map(Path::toString).toList()),
            mainClass
        ));
        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        return run.stdout();
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

    private Path dependencyClasses(final String name, final String className, final String source) throws Exception {
        final Path root = tempDir.resolve(name + "-dependency");
        final Path sourceRoot = root.resolve("src");
        final Path classes = root.resolve("classes");
        final Path sourceFile = sourceRoot.resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
        assertThat(process(root, List.of("javac", "-d", classes.toString(), sourceFile.toString())).exitCode()).isZero();
        return classes;
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}
