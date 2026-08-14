package javan;

import javan.testing.TestSuite.PlatformTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
@PlatformTest
final class ControlFlowCliTest extends CliIntegrationSupport {
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
}
