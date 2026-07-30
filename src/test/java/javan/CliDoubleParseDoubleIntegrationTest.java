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
final class CliDoubleParseDoubleIntegrationTest extends CliIntegrationSupport {
    @Test
    void parsesFiniteDecimal() throws Exception {
        assertMatchesJvm("double-parse-finite", """
            System.out.println(Double.parseDouble("1.5"));
            """);
    }

    @Test
    void parsesTrimmedDecimal() throws Exception {
        assertMatchesJvm("double-parse-trimmed", """
            System.out.println(Double.parseDouble("\\t 1.25\\n"));
            """);
    }

    @Test
    void parsesLeadingSignAndTypeSuffix() throws Exception {
        assertMatchesJvm("double-parse-sign-suffix", """
            System.out.println(Double.parseDouble("+1.25d"));
            """);
    }

    @Test
    void parsesDecimalExponent() throws Exception {
        assertMatchesJvm("double-parse-exponent", """
            System.out.println(Double.parseDouble("6.022e23") == 6.022e23);
            """);
    }

    @Test
    void parsesHexadecimalLiteral() throws Exception {
        assertMatchesJvm("double-parse-hex", """
            System.out.println(Double.parseDouble("0x1.8p1"));
            """);
    }

    @Test
    void preservesNegativeZero() throws Exception {
        assertMatchesJvm("double-parse-negative-zero", """
            System.out.println(Double.parseDouble("-0.0"));
            """);
    }

    @Test
    void parsesSignedNaN() throws Exception {
        assertMatchesJvm("double-parse-nan", """
            System.out.println(Double.parseDouble("-NaN"));
            """);
    }

    @Test
    void parsesNegativeInfinity() throws Exception {
        assertMatchesJvm("double-parse-infinity", """
            System.out.println(Double.parseDouble("-Infinity"));
            """);
    }

    @Test
    void parsesMaximumFiniteValue() throws Exception {
        assertMatchesJvm("double-parse-max-finite", """
            System.out.println(
                Double.parseDouble("1.7976931348623157E308")
                    == 0x1.fffffffffffffp1023
            );
            """);
    }

    @Test
    void roundsOverflowToInfinity() throws Exception {
        assertMatchesJvm("double-parse-overflow", """
            System.out.println(Double.parseDouble("1.7976931348623159E308"));
            """);
    }

    @Test
    void parsesMinimumNormalValue() throws Exception {
        assertMatchesJvm("double-parse-min-normal", """
            System.out.println(
                Double.parseDouble("2.2250738585072014E-308") == 0x1.0p-1022
            );
            """);
    }

    @Test
    void parsesMinimumSubnormalValue() throws Exception {
        assertMatchesJvm("double-parse-min-subnormal", """
            System.out.println(
                Double.parseDouble("4.9E-324") == 0x0.0000000000001p-1022
            );
            """);
    }

    @Test
    void roundsBelowMinimumSubnormalHalfwayPointToZero() throws Exception {
        assertMatchesJvm("double-parse-underflow-down", """
            System.out.println(Double.parseDouble("2.4703282292062327E-324"));
            """);
    }

    @Test
    void roundsAboveMinimumSubnormalHalfwayPointUp() throws Exception {
        assertMatchesJvm("double-parse-underflow-up", """
            System.out.println(
                Double.parseDouble("2.4703282292062328E-324")
                    == 0x0.0000000000001p-1022
            );
            """);
    }

    @Test
    void roundsExactHalfwayValueToEven() throws Exception {
        assertMatchesJvm("double-parse-half-even", """
            System.out.println(Double.parseDouble(
                "1.00000000000000011102230246251565404236316680908203125"
            ));
            """);
    }

    @Test
    void retainedStickyDigitRoundsAboveHalfwayValueUp() throws Exception {
        final String input = "1.00000000000000011102230246251565404236316680908203125"
            + "0".repeat(1_100)
            + "1";
        assertMatchesJvm("double-parse-sticky", """
            System.out.println(Double.parseDouble("%s"));
            """.formatted(input));
    }

    @Test
    void parsesHexadecimalMinimumSubnormalValue() throws Exception {
        assertMatchesJvm("double-parse-hex-min-subnormal", """
            System.out.println(
                Double.parseDouble("0x0.0000000000001p-1022")
                    == 0x0.0000000000001p-1022
            );
            """);
    }

    @Test
    void roundsHexadecimalSubnormalHalfwayValueToEven() throws Exception {
        assertMatchesJvm("double-parse-hex-half-even", """
            System.out.println(Double.parseDouble("0x0.00000000000008p-1022"));
            """);
    }

    @Test
    void leadingFractionZerosCanCancelHugeExponent() throws Exception {
        final String input = "0." + "0".repeat(1_200) + "1e1201";
        assertMatchesJvm("double-parse-exponent-cancellation", """
            System.out.println(Double.parseDouble("%s"));
            """.formatted(input));
    }

    @Test
    void hugePositiveExponentOverflows() throws Exception {
        assertMatchesJvm("double-parse-huge-positive-exponent", """
            System.out.println(Double.parseDouble("1e999999999999999999999"));
            """);
    }

    @Test
    void hugeNegativeExponentUnderflows() throws Exception {
        assertMatchesJvm("double-parse-huge-negative-exponent", """
            System.out.println(Double.parseDouble("1e-999999999999999999999"));
            """);
    }

    @Test
    void emptyInputThrowsNumberFormatExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("double-parse-empty", """
            try {
                System.out.println(Double.parseDouble(""));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void invalidInputThrowsNumberFormatExceptionWithOriginalJvmMessage() throws Exception {
        assertMatchesJvm("double-parse-invalid", """
            try {
                System.out.println(Double.parseDouble("  1_0  "));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void nullInputThrowsNullPointerExceptionWithJvmMessage() throws Exception {
        assertMatchesJvm("double-parse-null", """
            try {
                System.out.println(Double.parseDouble(null));
            } catch (final NullPointerException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void parseFailureCanBeCaughtAsIllegalArgumentException() throws Exception {
        assertMatchesJvm("double-parse-illegal-argument", """
            try {
                System.out.println(Double.parseDouble("invalid"));
            } catch (final IllegalArgumentException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void dynamicMalformedInputIsCatchableUnderGcPressure() throws Exception {
        assertMatchesJvm(
            "double-parse-gc",
            """
            try {
                System.out.println(Double.parseDouble(value()));
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
    void malformedInputCanBeCaughtAsRuntimeException() throws Exception {
        assertMatchesJvm("double-parse-malformed-runtime", """
            try {
                System.out.println(Double.parseDouble("invalid"));
            } catch (final RuntimeException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void nullInputCanBeCaughtAsRuntimeException() throws Exception {
        assertMatchesJvm("double-parse-null-runtime", """
            try {
                System.out.println(Double.parseDouble(null));
            } catch (final RuntimeException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void interiorAsciiControlCharacterIsRejectedWithJvmMessage() throws Exception {
        assertMatchesJvm("double-parse-interior-control", """
            try {
                System.out.println(Double.parseDouble("1\\u00012"));
            } catch (final NumberFormatException exception) {
                System.out.println(exception.getMessage());
            }
            """);
    }

    @Test
    void embeddedNulLiteralIsRejectedBeforeNativeParsing() throws Exception {
        final Path project = project("double-parse-nul-literal");
        writeJava(project, "com.acme.Main", source("""
            System.out.println(Double.parseDouble("\\u00001"));
            """, ""));

        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode() + "\n" + build.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN046]",
                "embedded NUL string constants require the length-aware string model"
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
            """.formatted(body.replace("\n", "\n        "), members);
    }
}
