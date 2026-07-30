package javan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliDecimalParseIntegrationTest extends CliIntegrationSupport {
    @Test
    void parsesMaximumInt() throws Exception {
        assertMatchesJvm("integer-parse-max", """
            System.out.println(Integer.parseInt("2147483647"));
            """);
    }

    @Test
    void parsesMinimumInt() throws Exception {
        assertMatchesJvm("integer-parse-min", """
            System.out.println(Integer.parseInt("-2147483648"));
            """);
    }

    @Test
    void parsesLeadingPlusSign() throws Exception {
        assertMatchesJvm("integer-parse-plus", """
            System.out.println(Integer.parseInt("+42"));
            """);
    }

    @Test
    void parsesUnicodeDecimalDigits() throws Exception {
        assertMatchesJvm("integer-parse-unicode", """
            System.out.println(Integer.parseInt("\\u0661\\u0662"));
            """);
    }

    @Test
    void emptyInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("integer-parse-empty", """
            try {
                System.out.println(Integer.parseInt(""));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void signOnlyInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("integer-parse-sign-only", """
            try {
                System.out.println(Integer.parseInt("-"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void overflowThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("integer-parse-overflow", """
            try {
                System.out.println(Integer.parseInt("2147483648"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void nullInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("integer-parse-null", """
            try {
                System.out.println(Integer.parseInt(null));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void parseFailureCanBeCaughtAndWrappedWithCause() throws Exception {
        assertMatchesJvm("integer-parse-wrapped", """
            try {
                System.out.println(parse());
            } catch (final IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
            """, """
            private static int parse() {
                try {
                    return Integer.parseInt("invalid");
                } catch (final NumberFormatException exception) {
                    throw new IllegalArgumentException("wrapped", exception);
                }
            }
            """);
    }

    @Test
    void dynamicMalformedInputIsCatchableUnderGcPressure() throws Exception {
        assertMatchesJvm(
            "integer-parse-gc",
            """
            try {
                System.out.println(Integer.parseInt(value()));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """,
            """
            private static String value() {
                return "dynamic-invalid";
            }
            """,
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );
    }

    @Test
    void parsesMaximumLong() throws Exception {
        assertMatchesJvm("long-parse-max", """
            System.out.println(Long.parseLong("9223372036854775807"));
            """);
    }

    @Test
    void parsesMinimumLong() throws Exception {
        assertMatchesJvm("long-parse-min", """
            System.out.println(Long.parseLong("-9223372036854775808"));
            """);
    }

    @Test
    void parsesLeadingPlusSignAsLong() throws Exception {
        assertMatchesJvm("long-parse-plus", """
            System.out.println(Long.parseLong("+42"));
            """);
    }

    @Test
    void parsesUnicodeDecimalDigitsAsLong() throws Exception {
        assertMatchesJvm("long-parse-unicode", """
            System.out.println(Long.parseLong("\\u0661\\u0662"));
            """);
    }

    @Test
    void supplementaryUnicodeDecimalDigitsAreRejectedWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-supplementary-unicode", """
            final String digits = new String(new char[] {
                (char) 0xD801,
                (char) 0xDCA1,
                (char) 0xD801,
                (char) 0xDCA2
            });
            try {
                System.out.println(Long.parseLong(digits));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void emptyLongInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-empty", """
            try {
                System.out.println(Long.parseLong(""));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void signOnlyLongInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-sign-only", """
            try {
                System.out.println(Long.parseLong("-"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void nonDigitLongInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-non-digit", """
            try {
                System.out.println(Long.parseLong("12x"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void positiveLongOverflowThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-positive-overflow", """
            try {
                System.out.println(Long.parseLong("9223372036854775808"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void negativeLongOverflowThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-negative-overflow", """
            try {
                System.out.println(Long.parseLong("-9223372036854775809"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void nullLongInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("long-parse-null", """
            try {
                System.out.println(Long.parseLong(null));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
        """);
    }

    @Test
    void longParseFailureCanBeCaughtAsIllegalArgumentException() throws Exception {
        assertMatchesJvm("long-parse-illegal-argument", """
            try {
                System.out.println(Long.parseLong("invalid"));
            } catch (final IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void longParseFailureCanBeCaughtAndWrappedWithCause() throws Exception {
        assertMatchesJvm("long-parse-wrapped", """
            try {
                System.out.println(parseLong());
            } catch (final IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
            """, """
            private static long parseLong() {
                try {
                    return Long.parseLong("invalid");
                } catch (final NumberFormatException exception) {
                    throw new IllegalArgumentException("wrapped", exception);
                }
            }
            """);
    }

    @Test
    void dynamicMalformedLongInputIsCatchableUnderGcPressure() throws Exception {
        assertMatchesJvm(
            "long-parse-gc",
            """
            try {
                System.out.println(Long.parseLong(longValue()));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """,
            """
            private static String longValue() {
                return "dynamic-invalid";
            }
            """,
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );
    }

    private void assertMatchesJvm(final String projectName, final String body) throws Exception {
        assertMatchesJvm(projectName, body, "");
    }

    private void assertMatchesJvm(
        final String projectName,
        final String body,
        final String members
    ) throws Exception {
        assertMatchesJvm(projectName, body, members, Map.of());
    }

    private void assertMatchesJvm(
        final String projectName,
        final String body,
        final String members,
        final Map<String, String> environment
    ) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source(body, members));

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        String nativeOutput = "";
        String nativeError = build.stderr();
        if (build.exitCode() == 0) {
            final ProcessResult nativeRun = process(
                project,
                List.of(project.resolve(".javan/bin").resolve(projectName).toString()),
                Duration.ofSeconds(30),
                environment
            );
            nativeOutput = nativeRun.stdout();
            nativeError = nativeRun.stderr();
        }

        assertThat(nativeOutput).as(nativeError).isEqualTo(jvmOutput);
    }

    private static String source(final String body, final String members) {
        return """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
            %s
                }

            %s
            }
            """.formatted(indent(body), members);
    }

    private static String indent(final String value) {
        return value.replace("\n", "\n        ");
    }
}
