package javan.verify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class DiagnosticTest {
    @Test
    void formatTreatsShortCodesAsWarnings() {
        final Diagnostic diagnostic = Diagnostic.error("JAVA", "message", "Class", "method", "subject", "reason", "fix");

        assertThat(diagnostic.format()).contains("warning[JAVA]");
    }

    @Test
    void formatTreatsNonZeroAndNonNineFallbackCodesAsWarnings() {
        final Diagnostic diagnostic = Diagnostic.warning("JAVAN177", "message", "Class", "method", "subject", "reason", "fix");

        assertThat(diagnostic.format()).contains("warning[JAVAN177]");
    }
}
