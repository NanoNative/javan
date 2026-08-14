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
    void buildsAndRunsLegacyJsrRetBytecode() throws Exception {
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
        assertThat(process(tempDir, java.util.List.of(executable.toString())).exitCode()).isZero();
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
            out.writeShort(8);
            utf8(out, "legacy/Subroutine");
            out.writeByte(7);
            out.writeShort(1);
            utf8(out, "java/lang/Object");
            out.writeByte(7);
            out.writeShort(3);
            utf8(out, "main");
            utf8(out, "([Ljava/lang/String;)V");
            utf8(out, "Code");
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
            final byte[] code = new byte[]{(byte) 168, 0, 6, (byte) 177, 0, 0, 76, (byte) 169, 1};
            out.writeInt(12 + code.length);
            out.writeShort(1);
            out.writeShort(2);
            out.writeInt(code.length);
            out.write(code);
            out.writeShort(0);
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
