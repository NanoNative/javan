package javan;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@TestInstance(PER_CLASS)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliStringIsBlankIntegrationTest extends CliIntegrationSupport {
    private static final String MAIN_CLASS = "com.acme.Main";
    private static final String NULL_RECEIVER = "null-receiver";
    private static final String UNPAIRED_SURROGATE = "unpaired-surrogate";

    @TempDir
    private static Path sharedTempDir;

    private Path project;
    private Path binary;

    @BeforeAll
    void buildProbe() throws Exception {
        tempDir = sharedTempDir;
        project = project("string-is-blank-probe");
        writeJava(project, MAIN_CLASS, """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value;
                    if (args.length == 0) {
                        value = "";
                    } else if ("null-receiver".equals(args[0])) {
                        value = null;
                    } else if ("unpaired-surrogate".equals(args[0])) {
                        value = new String(new char[]{0xd800});
                    } else {
                        value = args[0];
                    }
                    System.out.println(value.isBlank());
                }
            }
            """);

        runJvm(project, MAIN_CLASS);
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        binary = project.resolve(".javan/bin/string-is-blank-probe");
    }

    @Test
    void emptyStringIsBlank() {
        assertThat(runBlank("")).isEqualTo(success(true));
    }

    @Test
    void asciiWhitespaceStringIsBlank() {
        assertThat(runBlank(new String(new char[]{0x20, 0x09, 0x0a, 0x0d, 0x1c, 0x1f})))
            .isEqualTo(success(true));
    }

    @Test
    void oghamSpaceMarkIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x1680))).isEqualTo(success(true));
    }

    @Test
    void enQuadIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x2000))).isEqualTo(success(true));
    }

    @Test
    void sixPerEmSpaceIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x2006))).isEqualTo(success(true));
    }

    @Test
    void punctuationSpaceIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x2008))).isEqualTo(success(true));
    }

    @Test
    void hairSpaceIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x200a))).isEqualTo(success(true));
    }

    @Test
    void lineSeparatorIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x2028))).isEqualTo(success(true));
    }

    @Test
    void paragraphSeparatorIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x2029))).isEqualTo(success(true));
    }

    @Test
    void mediumMathematicalSpaceIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x205f))).isEqualTo(success(true));
    }

    @Test
    void ideographicSpaceIsBlank() {
        assertThat(runBlank(String.valueOf((char) 0x3000))).isEqualTo(success(true));
    }

    @Test
    void noBreakSpaceIsNotBlank() {
        assertThat(runBlank(String.valueOf((char) 0x00a0))).isEqualTo(success(false));
    }

    @Test
    void figureSpaceIsNotBlank() {
        assertThat(runBlank(String.valueOf((char) 0x2007))).isEqualTo(success(false));
    }

    @Test
    void narrowNoBreakSpaceIsNotBlank() {
        assertThat(runBlank(String.valueOf((char) 0x202f))).isEqualTo(success(false));
    }

    @Test
    void visibleTextIsNotBlank() {
        assertThat(runBlank("javan")).isEqualTo(success(false));
    }

    @Test
    void supplementaryCodePointIsNotBlank() {
        assertThat(runBlank(new String(Character.toChars(0x1f600)))).isEqualTo(success(false));
    }

    @Test
    void unpairedSurrogateIsNotBlank() {
        assertThat(runBlank(UNPAIRED_SURROGATE)).isEqualTo(success(false));
    }

    @Test
    void nullReceiverFailsAtNativeBoundary() {
        final ProcessResult result = runNative(NULL_RECEIVER);

        assertThat(new NativeFailure(
            result.exitCode() != 0,
            result.stdout(),
            result.stderr().contains("[JAVAN-RUNTIME-PANIC]"),
            result.stderr().contains("detail: null string")
        )).isEqualTo(new NativeFailure(true, "", true, true));
    }

    private BlankResult runBlank(final String value) {
        final ProcessResult nativeRun = runNative(value);
        final ProcessResult jvmRun = process(project, List.of(
            CliTestHarness.currentJavaCommand(),
            "-cp",
            project.resolve("jvm-classes").toString(),
            MAIN_CLASS,
            value
        ));
        return new BlankResult(
            nativeRun.exitCode(),
            nativeRun.stdout(),
            nativeRun.stderr(),
            jvmRun.exitCode(),
            jvmRun.stdout(),
            jvmRun.stderr()
        );
    }

    private ProcessResult runNative(final String value) {
        return process(project, List.of(binary.toString(), value));
    }

    private static BlankResult success(final boolean value) {
        final String output = value + System.lineSeparator();
        return new BlankResult(0, output, "", 0, output, "");
    }

    private record BlankResult(
        int nativeExit,
        String nativeStdout,
        String nativeStderr,
        int jvmExit,
        String jvmStdout,
        String jvmStderr
    ) {
    }

    private record NativeFailure(boolean failed, String stdout, boolean panic, boolean nullString) {
    }
}
