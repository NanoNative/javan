package javan;

import javan.cli.Cli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class CliCompatIntegrationTest {
    @TempDir
    private Path tempDir;

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
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "{\"name\": \"compatibility\", \"status\": \"present\"",
            "\"status\": \"pass\"",
            "\"javaFeatureVersion\": " + jdk
        );
        assertThat(project.resolve(".javan/reports/jdk-" + jdk + "-inventory.json")).exists();
        assertThat(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve(".javan/jdk-inventory/jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve(".javan/bytecode-patterns/jdk-" + jdk + ".json")).exists();
        assertThat(project.resolve("doc/status/support-matrix.md")).exists();
        assertThat(project.resolve("doc/status/support-matrix.json")).exists();
        assertThat(project.resolve("doc/status/jdk-compatibility.md")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/compatibility-summary.json")))
            .contains("\"status\": \"pass\"", "\"unknownFatalOpcodeUses\": 0");
        assertThat(Files.readString(project.resolve(".javan/reports/bytecode-patterns-jdk-" + jdk + ".json")))
            .contains("\"mnemonic\": \"anewarray\"", "\"mnemonic\": \"arraylength\"", "\"support\": \"NATIVE_SUPPORTED\"");
        assertThat(Files.readString(project.resolve("doc/status/support-matrix.md")))
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
    void compatPrintsFirstStaticErrorWhenCompatibilityFails() throws Exception {
        final Path project = project("compat-native-fail");
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

        final CliRun run = runSlow(tempDir, "compat", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).isEmpty();
        assertThat(run.stdout()).contains("Compatibility:", "status:          fail", "error[JAVAN013]");
        assertThat(Files.readString(project.resolve(".javan/reports/compatibility-summary.json")))
            .contains("\"status\": \"fail\"");
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

    private static CliRun runSlow(final Path cwd, final String... args) {
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final int exitCode = assertTimeoutPreemptively(Duration.ofSeconds(90), () ->
            new Cli().run(cwd, new PrintStream(stdout, true, StandardCharsets.UTF_8), new PrintStream(stderr, true, StandardCharsets.UTF_8), args)
        );
        return new CliRun(
            exitCode,
            stdout.toString(StandardCharsets.UTF_8),
            stderr.toString(StandardCharsets.UTF_8)
        );
    }

    private record CliRun(int exitCode, String stdout, String stderr) {
    }
}
