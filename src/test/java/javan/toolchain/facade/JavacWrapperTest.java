package javan.toolchain.facade;

import javan.util.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class JavacWrapperTest {
    @TempDir
    private Path tempDir;

    @Test
    void delegatesToTheResolvedJavacWithUnchangedArgumentsAndOutput() throws Exception {
        final RecordingProcessRunner runner = new RecordingProcessRunner(new ProcessRunner.Result(17, "out", "err"));
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final Path javac = tempDir.resolve("jdk/bin/javac");

        final int exitCode = new JavacWrapper(runner).run(
            tempDir,
            new PrintStream(stdout, true, StandardCharsets.UTF_8),
            new PrintStream(stderr, true, StandardCharsets.UTF_8),
            javac,
            List.of("--release", "25", "Main.java")
        );

        assertThat(exitCode).isEqualTo(17);
        assertThat(stdout.toString(StandardCharsets.UTF_8)).isEqualTo("out");
        assertThat(stderr.toString(StandardCharsets.UTF_8)).isEqualTo("err");
        assertThat(runner.command()).containsExactly(
            javac.toAbsolutePath().normalize().toString(),
            "--release",
            "25",
            "Main.java"
        );
    }

    @Test
    void facadeParserRemovesImplementedJavanArgumentsAndPreservesJavacArguments() {
        final JavacWrapper.FacadeArguments parsed = JavacWrapper.parseFacadeArguments(List.of(
            "--jn-help",
            "-jn-version",
            "--release",
            "25",
            "-d",
            "target/classes",
            "--class-path",
            "libs/*",
            "--jn-end",
            "--jn-build"
        ));

        assertThat(parsed.pass()).isTrue();
        assertThat(parsed.javanHelp()).isTrue();
        assertThat(parsed.javanVersion()).isTrue();
        assertThat(parsed.analysisEnabled()).isTrue();
        assertThat(parsed.classOutput()).contains("target/classes");
        assertThat(parsed.classpath()).contains("libs/*");
        assertThat(parsed.javacArgs()).containsExactly(
            "--release", "25", "-d", "target/classes", "--class-path", "libs/*", "--jn-build"
        );
    }

    @Test
    void facadeParserParsesNativeBuildArgumentsWithoutForwardingThemToJavac() {
        final JavacWrapper.FacadeArguments parsed = JavacWrapper.parseFacadeArguments(List.of(
            "--jn-build",
            "--jn-main",
            "com.acme.Main",
            "--jn-out",
            "acme",
            "--jn-target",
            "darwin/arm64",
            "--jn-diag",
            "jsonl",
            "-d",
            "target/classes",
            "Main.java"
        ));

        assertThat(parsed.pass()).isTrue();
        assertThat(parsed.mode()).isEqualTo(FacadeMode.BUILD);
        assertThat(parsed.mainClass()).contains("com.acme.Main");
        assertThat(parsed.outputName()).contains("acme");
        assertThat(parsed.targets()).containsExactly("darwin/arm64");
        assertThat(parsed.diagnosticFormat()).contains("jsonl");
        assertThat(parsed.javacArgs()).containsExactly("-d", "target/classes", "Main.java");
    }

    @Test
    void facadeParserDisablesPostCompileWorkOnlyWhenExplicitlyRequested() {
        final JavacWrapper.FacadeArguments parsed = JavacWrapper.parseFacadeArguments(List.of("-jn-off", "Main.java"));

        assertThat(parsed.pass()).isTrue();
        assertThat(parsed.analysisEnabled()).isFalse();
        assertThat(parsed.javacArgs()).containsExactly("Main.java");
    }

    @Test
    void facadeParserRejectsConflictingModes() {
        final JavacWrapper.FacadeArguments parsed = JavacWrapper.parseFacadeArguments(List.of("--jn-warn", "--jn-build"));

        assertThat(parsed.pass()).isFalse();
        assertThat(parsed.error()).isEqualTo("Conflicting Javan compiler modes: --jn-warn and --jn-build");
    }

    private static final class RecordingProcessRunner extends ProcessRunner {
        private final ProcessRunner.Result result;
        private List<String> command = List.of();

        private RecordingProcessRunner(final ProcessRunner.Result result) {
            this.result = result;
        }

        @Override
        public ProcessRunner.Result run(final Path workingDirectory, final List<String> command) {
            this.command = List.copyOf(command);
            return result;
        }

        private List<String> command() {
            return command;
        }
    }
}
