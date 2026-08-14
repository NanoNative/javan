package javan;

import javan.testing.TestSuite.PlatformTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
@PlatformTest
final class ControlFlowCliTest extends CliIntegrationSupport {
    @Test
    void buildsAndRunsRepeatedNestedAndExceptionalLegacySubroutines() throws Exception {
        final Path classes = tempDir.resolve("legacy-classes");
        final Path classFile = classes.resolve("legacy/Subroutine.class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, legacySubroutineClassfile());

        final Path executable = tempDir.resolve(".javan/bin/legacy-subroutine");
        final CliRun run = runSlow(
            tempDir,
            "build",
            classes.toString(),
            "--main",
            "legacy.Subroutine",
            "--output",
            "legacy-subroutine"
        );

        assertThat(run.exitCode()).withFailMessage(run.stderr()).isZero();
        assertThat(run.stderr()).isEmpty();
        assertThat(Files.readString(tempDir.resolve(".javan/reports/control-flow.json")))
            .contains("\"status\": \"pass\"", "\"class\": \"legacy/Subroutine\"")
            .doesNotContain("jsr", "ret");
        final ProcessResult nativeRun = process(tempDir, java.util.List.of(executable.toString()));
        assertThat(nativeRun.exitCode()).withFailMessage(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).isEmpty();
    }

    @Test
    void checkWritesStableBranchAndSwitchGraph() throws Exception {
        final Path project = project("control-flow-report");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                public static void main(final String[] args) {
                    final int value = args.length;
                    if (value == 0) {
                        System.out.println("none");
                    } else {
                        switch (value) {
                            case 1 -> System.out.println("one");
                            case 2 -> System.out.println("two");
                            default -> System.out.println("many");
                        }
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stderr()).isEmpty();
        final String json = Files.readString(project.resolve(".javan/reports/control-flow.json"));
        assertThat(json)
            .contains("\"schemaVersion\": 1", "\"status\": \"pass\"", "\"class\": \"com/acme/Main\"")
            .contains("\"kind\":\"branch\"", "\"kind\":\"fallthrough\"", "\"kind\":\"switch\"");
        assertThat(Files.readString(project.resolve(".javan/reports/control-flow.md")))
            .contains("# Bytecode Control Flow", "- blocks: `", "- edges: `",
                "Exact method graphs, block offsets, and typed edges are in `control-flow.json`.");
        assertThat(Files.readString(project.resolve(".javan/reports/report.json")))
            .contains("\"name\": \"control-flow\"", "\"status\": \"pass\"");
    }

    private static byte[] legacySubroutineClassfile() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(49);
            out.writeShort(10);
            utf8(out, "legacy/Subroutine");
            out.writeByte(7);
            out.writeShort(1);
            utf8(out, "java/lang/Object");
            out.writeByte(7);
            out.writeShort(3);
            utf8(out, "main");
            utf8(out, "([Ljava/lang/String;)V");
            utf8(out, "Code");
            utf8(out, "java/lang/NullPointerException");
            out.writeByte(7);
            out.writeShort(8);
            out.writeShort(0x0021);
            out.writeShort(2);
            out.writeShort(4);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(1);
            out.writeShort(0x0009);
            out.writeShort(5);
            out.writeShort(6);
            out.writeShort(1);
            out.writeShort(7);
            final byte[] code = new byte[]{
                3, 60, // count = 0
                // Call one subroutine twice.
                (byte) 168, 0, 22,
                (byte) 168, 0, 19,
                // Call a subroutine which calls another subroutine.
                (byte) 168, 0, 22,
                // Call a subroutine whose explicit throw is caught inside it.
                (byte) 168, 0, 35,
                // All paths increment count once; fail natively unless count == 5.
                27, 8,
                (byte) 159, 0, 6,
                1, (byte) 191, 0, (byte) 177, 0,
                // Repeated subroutine.
                77, (byte) 132, 1, 1, (byte) 169, 2,
                // Outer then nested subroutine.
                77, (byte) 168, 0, 9, (byte) 132, 1, 1, (byte) 169, 2, 0,
                78, (byte) 132, 1, 1, (byte) 169, 3,
                // Exceptional subroutine and its handler.
                77, 1, (byte) 191,
                58, 4, (byte) 132, 1, 1, (byte) 169, 2
            };
            out.writeInt(20 + code.length);
            out.writeShort(2);
            out.writeShort(5);
            out.writeInt(code.length);
            out.write(code);
            out.writeShort(1);
            out.writeShort(47);
            out.writeShort(49);
            out.writeShort(49);
            out.writeShort(9);
            out.writeShort(0);
            out.writeShort(0);
        }
        return bytes.toByteArray();
    }

    private static void utf8(final DataOutputStream out, final String value) throws Exception {
        out.writeByte(1);
        out.writeUTF(value);
    }
}
