package javan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class CliTestHarnessTest {

    @Test
    void childCoverageCommandInjectsExplicitJacocoAgentForJavanMain() {
        final String originalAgent = System.getProperty("javan.childJacocoArgLine");
        final String originalDirectory = System.getProperty("javan.childJacocoDir");
        try {
            System.setProperty(
                "javan.childJacocoArgLine",
                "-javaagent:/tmp/org.jacoco.agent.jar=destfile=/tmp/parent.exec,append=true"
            );
            System.setProperty("javan.childJacocoDir", "target/jacoco-child-test");

            final List<String> command = CliTestHarness.childCoverageCommandForTesting(List.of(
                "java",
                "-cp",
                "target/classes",
                "javan.Main",
                "--version"
            ));

            assertThat(command).hasSize(6);
            assertThat(command.get(0)).isEqualTo("java");
            assertThat(command.get(1))
                .startsWith("-javaagent:/tmp/org.jacoco.agent.jar=destfile=")
                .contains("target/jacoco-child-test/child-")
                .endsWith(".exec,append=true");
            assertThat(command.subList(2, command.size())).containsExactly(
                "-cp",
                "target/classes",
                "javan.Main",
                "--version"
            );
        } finally {
            restoreProperty("javan.childJacocoArgLine", originalAgent);
            restoreProperty("javan.childJacocoDir", originalDirectory);
        }
    }

    @Test
    void childCoverageCommandLeavesNonJavanCommandUntouched() {
        final String originalAgent = System.getProperty("javan.childJacocoArgLine");
        final String originalDirectory = System.getProperty("javan.childJacocoDir");
        try {
            System.setProperty(
                "javan.childJacocoArgLine",
                "-javaagent:/tmp/org.jacoco.agent.jar=destfile=/tmp/parent.exec,append=true"
            );
            System.setProperty("javan.childJacocoDir", "target/jacoco-child-test");

            final List<String> command = List.of("java", "-version");
            assertThat(CliTestHarness.childCoverageCommandForTesting(command)).isEqualTo(command);
        } finally {
            restoreProperty("javan.childJacocoArgLine", originalAgent);
            restoreProperty("javan.childJacocoDir", originalDirectory);
        }
    }

    private static void restoreProperty(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
