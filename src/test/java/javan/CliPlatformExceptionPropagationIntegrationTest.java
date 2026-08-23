package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliPlatformExceptionPropagationIntegrationTest extends CliIntegrationSupport {
    @Test
    void generatedConstructorFailureIsCaughtAndWrapped() throws Exception {
        final Path project = project("platform-catch-generated-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(state(-1, 0, 0L));
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static State state(final int start, final int length, final long revision) {
                    try {
                        return new State("value", new Range(start, length), revision);
                    } catch (final IllegalArgumentException exception) {
                        throw new IllegalArgumentException("invalid state", exception);
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Range", """
            package com.acme;

            public record Range(int start, int length) {
                public Range {
                    if (start < 0 || length < 0) {
                        throw new IllegalArgumentException("invalid range");
                    }
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public record State(String value, Range range, long revision) {
                public State {
                    if (revision < 0L) {
                        throw new IllegalArgumentException("invalid revision");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void calledIllegalArgumentExceptionIsCaughtByCaller() throws Exception {
        final Path project = project("platform-catch-cross-call-message");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(readMessage());
                }

                private static String readMessage() {
                    try {
                        return Helper.throwIllegalArgumentException();
                    } catch (final IllegalArgumentException exception) {
                        return exception.getMessage();
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static String throwIllegalArgumentException() {
                    throw new IllegalArgumentException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveCalledNullPointerExceptionIsCaughtByCaller() throws Exception {
        final Path project = project("platform-catch-transitive-call-message");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    return Leaf.fail();
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveCaughtRethrowPreservesConcreteTypeForCaller() throws Exception {
        final Path project = project("platform-catch-transitive-caught-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    try {
                        return Leaf.fail();
                    } catch (final RuntimeException exception) {
                        throw exception;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveCaughtAliasRethrowPreservesConcreteTypeForCaller() throws Exception {
        final Path project = project("platform-catch-transitive-caught-alias-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    try {
                        return Leaf.fail();
                    } catch (final RuntimeException exception) {
                        final RuntimeException alias = exception;
                        throw alias;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveCaughtCheckcastRethrowPreservesConcreteTypeForCaller() throws Exception {
        final Path project = project("platform-catch-transitive-caught-checkcast-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    try {
                        return Leaf.fail();
                    } catch (final RuntimeException exception) {
                        throw (NullPointerException) exception;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveCaughtAssignmentRethrowPreservesConcreteTypeForCaller() throws Exception {
        final Path project = project("platform-catch-transitive-caught-assignment-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    RuntimeException alias;
                    try {
                        return Leaf.fail();
                    } catch (final RuntimeException exception) {
                        throw (alias = exception);
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveCaughtAliasRethrowCannotDisappearAtNativeBoundary() throws Exception {
        final Path project = project("platform-catch-transitive-caught-alias-uncaught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Middle.read());
                    System.out.println("after");
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    try {
                        return Leaf.fail();
                    } catch (final RuntimeException exception) {
                        final RuntimeException alias = exception;
                        throw alias;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).exitCode()).isEqualTo(1);
    }

    @Test
    void materializedSupplierExceptionCannotDisappearAtNativeBoundary() throws Exception {
        final Path project = project("platform-catch-materialized-supplier-uncaught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> supplier = () -> fail();
                    System.out.println(supplier.get());
                    System.out.println("after");
                }

                private static String fail() {
                    throw new NullPointerException("supplier");
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).exitCode()).isEqualTo(1);
    }

    @Test
    void nestedMaterializedLambdaExceptionCannotDisappearAtNativeBoundary() throws Exception {
        final Path project = project("platform-catch-nested-materialized-lambda-uncaught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Object> supplier = () -> fail();
                    final Function<Object, Object> function = ignored -> supplier.get();
                    System.out.println(function.apply("value"));
                    System.out.println("after");
                }

                private static Object fail() {
                    throw new NullPointerException("nested");
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).exitCode()).isEqualTo(1);
    }

    @Test
    void nestedMaterializedLambdaExceptionCanBeCaughtByConcreteType() throws Exception {
        final Path project = project("platform-catch-nested-materialized-lambda-caught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Object> supplier = () -> fail();
                    final Function<Object, Object> function = ignored -> supplier.get();
                    try {
                        System.out.println(function.apply("value"));
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static Object fail() {
                    throw new NullPointerException("nested");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void materializedSupplierExceptionCanBeCaughtByConcreteType() throws Exception {
        final Path project = project("platform-catch-materialized-supplier-caught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Object> supplier = () -> fail();
                    try {
                        System.out.println(supplier.get());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static Object fail() {
                    throw new NullPointerException("supplier");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveFallbackPlatformThrowableTypeCanBeCaught() throws Exception {
        final Path project = project("platform-catch-transitive-fallback-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Thread.currentThread().interrupt();
                    try {
                        Helper.pause();
                        System.out.println("after");
                    } catch (final InterruptedException exception) {
                        System.out.println(exception.getMessage() == null);
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void pause() throws InterruptedException {
                    Thread.sleep(1L);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void stringIndexOutOfBoundsExceptionMatchesIndexOutOfBoundsCatch() throws Exception {
        final Path project = project("platform-catch-string-index-supertype");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Helper.fail();
                    } catch (final IndexOutOfBoundsException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new StringIndexOutOfBoundsException("outside");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveMathToIntExactOverflowCanBeCaught() throws Exception {
        final Path project = project("platform-catch-transitive-to-int-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Helper.narrow());
                    } catch (final ArithmeticException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static int narrow() {
                    return Math.toIntExact(2147483648L);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void stringToLowerCaseNullLocaleCanBeCaught() throws Exception {
        final Path project = project("platform-catch-string-lower-null-locale");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Locale locale = null;
                    try {
                        System.out.println("JAVAN".toLowerCase(locale));
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage() == null);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void stringToLowerCaseNullReceiverCanBeCaught() throws Exception {
        final Path project = project("platform-catch-string-lower-null-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Locale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = null;
                    try {
                        System.out.println(value.toLowerCase(Locale.ROOT));
                    } catch (final NullPointerException exception) {
                        System.out.println("caught");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveStringToLowerCaseNullLocaleCanBeCaught() throws Exception {
        final Path project = project("platform-catch-transitive-string-lower-null-locale");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Helper.lower());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage() == null);
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            import java.util.Locale;

            public final class Helper {
                private Helper() {
                }

                public static String lower() {
                    final Locale locale = null;
                    return "JAVAN".toLowerCase(locale);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveStringToLowerCaseNullReceiverCannotDisappearAtNativeBoundary() throws Exception {
        final Path project = project("platform-uncaught-transitive-string-lower-null-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.lower();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            import java.util.Locale;

            public final class Helper {
                private Helper() {
                }

                public static String lower() {
                    final String value = null;
                    return value.toLowerCase(Locale.ROOT);
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).stderr()).contains("value.toLowerCase(Locale.ROOT)");
    }

    @Test
    void locallyCaughtMathOverflowRethrowKeepsOriginalSource() throws Exception {
        final Path project = project("platform-catch-local-math-rethrow-source");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    narrow();
                }

                private static int narrow() {
                    try {
                        return Math.toIntExact(2147483648L);
                    } catch (final ArithmeticException exception) {
                        throw exception;
                    }
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).stderr()).contains("Math.toIntExact(2147483648L);");
    }

    @Test
    void transitiveFinallyRethrowPreservesConcreteTypeForCaller() throws Exception {
        final Path project = project("platform-catch-transitive-finally-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read());
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    try {
                        return Leaf.fail();
                    } finally {
                        System.out.println("cleanup");
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void conditionalFinallyTakenPreservesConcreteTypeForCaller() throws Exception {
        final Path project = conditionalFinallyProject("platform-catch-conditional-finally-taken", true);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void conditionalFinallyFallthroughPreservesConcreteTypeForCaller() throws Exception {
        final Path project = conditionalFinallyProject("platform-catch-conditional-finally-fallthrough", false);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void tableSwitchFinallyPreservesConcreteTypeForCaller() throws Exception {
        final Path project = switchFinallyProject(
            "platform-catch-table-switch-finally",
            1,
            """
                case 0:
                    System.out.println("zero");
                    break;
                case 1:
                    System.out.println("one");
                    break;
                default:
                    System.out.println("other");
            """
        );

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void lookupSwitchFinallyPreservesConcreteTypeForCaller() throws Exception {
        final Path project = switchFinallyProject(
            "platform-catch-lookup-switch-finally",
            1_000,
            """
                case 1:
                    System.out.println("one");
                    break;
                case 1000:
                    System.out.println("thousand");
                    break;
                default:
                    System.out.println("other");
            """
        );

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void transitiveFinallyRethrowCannotDisappearAtNativeBoundary() throws Exception {
        final Path project = project("platform-catch-transitive-finally-uncaught");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Middle.read());
                    System.out.println("after");
                }
            }
            """);
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read() {
                    try {
                        return Leaf.fail();
                    } finally {
                        System.out.println("cleanup");
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).exitCode()).isEqualTo(1);
    }

    private Path conditionalFinallyProject(final String name, final boolean cleanupEnabled) throws Exception {
        final Path project = project(name);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read(%s));
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """.formatted(cleanupEnabled));
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read(final boolean cleanupEnabled) {
                    try {
                        return Leaf.fail();
                    } finally {
                        if (cleanupEnabled) {
                            System.out.println("cleanup");
                        }
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);
        return project;
    }

    private Path switchFinallyProject(
        final String name,
        final int cleanupMode,
        final String switchCases
    ) throws Exception {
        final Path project = project(name);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Middle.read(%d));
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """.formatted(cleanupMode));
        writeJava(project, "com.acme.Middle", """
            package com.acme;

            public final class Middle {
                private Middle() {
                }

                public static String read(final int cleanupMode) {
                    try {
                        return Leaf.fail();
                    } finally {
                        switch (cleanupMode) {
            %s
                        }
                    }
                }
            }
            """.formatted(switchCases.indent(12)));
        writeJava(project, "com.acme.Leaf", """
            package com.acme;

            public final class Leaf {
                private Leaf() {
                }

                public static String fail() {
                    throw new NullPointerException("leaf");
                }
            }
            """);
        return project;
    }

    @Test
    void caughtIllegalArgumentExceptionCanBeRethrownWithCause() throws Exception {
        final Path project = project("platform-catch-cross-call-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(readMessage());
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static String readMessage() {
                    try {
                        return Helper.throwIllegalArgumentException();
                    } catch (final IllegalArgumentException cause) {
                        throw new IllegalArgumentException("outer: " + cause.getMessage(), cause);
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static String throwIllegalArgumentException() {
                    throw new IllegalArgumentException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void nonMatchingCatchFallsThroughToOuterThrowableCatch() throws Exception {
        final Path project = project("platform-catch-cross-call-nonmatching");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(readMessage());
                }

                private static String readMessage() {
                    try {
                        return Helper.throwNullPointerException();
                    } catch (final IllegalArgumentException ignored) {
                        return "wrong";
                    } catch (final Throwable throwable) {
                        return throwable.getMessage();
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static String throwNullPointerException() {
                    throw new NullPointerException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void normalCallThroughProtectedRangeMatchesJvmOutput() throws Exception {
        final Path project = project("platform-catch-cross-call-normal");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(readValue());
                }

                private static String readValue() {
                    try {
                        return Helper.returnValue();
                    } catch (final IllegalArgumentException exception) {
                        return exception.getMessage();
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static String returnValue() {
                    return "normal";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void dynamicExceptionMessageSurvivesGcStressUntilTheCatchHandlerRuns() throws Exception {
        final Path project = project("platform-catch-cross-call-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Helper.fail(42);
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail(final int value) {
                    throw new IllegalArgumentException("inner-" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project, Map.of(
            "JAVAN_GC_STRESS", "1",
            "JAVAN_GC_SAFEPOINT_INTERVAL", "1"
        ))).isEqualTo(jvmOutput);
    }

    @Test
    void callerCatchesArithmeticExceptionFromExactMathInCalledMethod() throws Exception {
        final Path project = project("platform-catch-cross-call-math-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Helper.overflow());
                    } catch (final ArithmeticException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static int overflow() {
                    return Math.addExact(Integer.MAX_VALUE, 1);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void outerCatchHandlesCauseWrappingInsideItsProtectedRange() throws Exception {
        final Path project = project("platform-catch-cross-call-nested-cause");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            Helper.fail();
                        } catch (final IllegalArgumentException cause) {
                            throw new IllegalArgumentException("outer: " + cause.getMessage(), cause);
                        }
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new IllegalArgumentException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void rethrowFromThrowableCatchPreservesConcreteTypeForOuterCatch() throws Exception {
        final Path project = project("platform-catch-cross-call-typed-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            Helper.fail();
                        } catch (final Throwable throwable) {
                            throw throwable;
                        }
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new NullPointerException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void rethrowFromRuntimeExceptionCatchPreservesConcreteTypeForOuterCatch() throws Exception {
        final Path project = project("platform-catch-cross-call-runtime-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            Helper.fail();
                        } catch (final RuntimeException exception) {
                            throw exception;
                        }
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new NullPointerException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void rethrowFromExceptionCatchPreservesConcreteTypeForOuterCatch() throws Exception {
        final Path project = project("platform-catch-cross-call-exception-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            Helper.fail();
                        } catch (final Exception exception) {
                            throw exception;
                        }
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new NullPointerException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void multiCatchRethrowPreservesConcreteTypeForOuterCatch() throws Exception {
        final Path project = project("platform-catch-cross-call-multi-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            Helper.fail();
                        } catch (final IllegalArgumentException | NullPointerException exception) {
                            throw exception;
                        }
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new NullPointerException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void sameMessageCaughtAliasesKeepIndependentConcreteTypes() throws Exception {
        final Path project = project("platform-catch-cross-call-independent-aliases");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String shared = "shared";
                    try {
                        Helper.failIllegalArgument(shared);
                    } catch (final RuntimeException first) {
                        try {
                            Helper.failNullPointer(shared);
                        } catch (final RuntimeException second) {
                            try {
                                throw first;
                            } catch (final IllegalArgumentException firstExpected) {
                                try {
                                    throw second;
                                } catch (final NullPointerException secondExpected) {
                                    System.out.println(firstExpected.getMessage() + ":" + secondExpected.getMessage());
                                }
                            }
                        }
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void failIllegalArgument(final String message) {
                    throw new IllegalArgumentException(message);
                }

                public static void failNullPointer(final String message) {
                    throw new NullPointerException(message);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void caughtAliasKeepsDynamicMessageDuringGcStress() throws Exception {
        final Path project = project("platform-catch-cross-call-alias-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Helper.fail(42);
                    } catch (final RuntimeException exception) {
                        final RuntimeException alias = exception;
                        System.out.println(alias.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail(final int value) {
                    throw new IllegalArgumentException("inner-" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project, Map.of(
            "JAVAN_GC_STRESS", "1",
            "JAVAN_GC_SAFEPOINT_INTERVAL", "1"
        ))).isEqualTo(jvmOutput);
    }

    @Test
    void caughtThrowableReturnEscapeIsRejected() throws Exception {
        final Path project = project("platform-catch-return-escape");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(caught().getMessage());
                }

                private static Throwable caught() {
                    try {
                        Helper.fail();
                        return new IllegalStateException("unreachable");
                    } catch (final RuntimeException exception) {
                        return exception;
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new IllegalArgumentException("inner");
                }
            }
            """);

        assertThat(run(tempDir, "build", project.toString()).stderr())
            .contains("A managed caught throwable cannot escape its catch method");
    }

    @Test
    void caughtThrowableArgumentEscapeIsRejected() throws Exception {
        final Path project = project("platform-catch-argument-escape");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Helper.fail();
                    } catch (final RuntimeException exception) {
                        System.out.println(Helper.message(exception));
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new IllegalArgumentException("inner");
                }

                public static String message(final Throwable throwable) {
                    return throwable.getMessage();
                }
            }
            """);

        assertThat(run(tempDir, "build", project.toString()).stderr())
            .contains("A managed caught throwable cannot escape its catch method");
    }

    @Test
    void rethrowSkipsNonMatchingCatchBeforeMatchingSuperclassCatch() throws Exception {
        final Path project = project("platform-catch-cross-call-supertype-rethrow");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        try {
                            Helper.fail();
                        } catch (final Throwable throwable) {
                            throw throwable;
                        }
                    } catch (final IllegalArgumentException ignored) {
                        System.out.println("wrong");
                    } catch (final RuntimeException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new NullPointerException("inner");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void uncaughtWorkerExceptionCannotDisappearAtTheThreadBoundary() throws Exception {
        final Path project = project("platform-catch-cross-call-worker");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Thread worker = new Thread(new Worker());
                    worker.start();
                    worker.join();
                    System.out.println("joined");
                }
            }
            """);
        writeJava(project, "com.acme.Worker", """
            package com.acme;

            public final class Worker implements Runnable {
                @Override
                public void run() {
                    Helper.fail();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void fail() {
                    throw new IllegalStateException("worker failed");
                }
            }
            """);

        build(project);
        assertThat(nativeRun(project).exitCode()).isEqualTo(1);
    }

    @Test
    void branchedMixedLocalAndTransportedHandlerIsRejected() throws Exception {
        final Path project = project("platform-catch-mixed-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(read(true));
                    System.out.println(read(false));
                }

                private static String read(final boolean local) {
                    try {
                        if (local) {
                            throw new IllegalArgumentException("local");
                        }
                        return Helper.fail();
                    } catch (final IllegalArgumentException exception) {
                        return exception.getMessage();
                    }
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static String fail() {
                    throw new IllegalArgumentException("transported");
                }
            }
            """);

        assertThat(run(tempDir, "check", project.toString()).exitCode()).isEqualTo(2);
    }

    @Test
    void fourDisjointTypedCatchRangesMatchJvm() throws Exception {
        final Path project = project("platform-catch-four-disjoint-ranges");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(
                        flow("bad") + "|" + flow("-1") + "|" + flow("boom") + "|" + flow("13") + "|" + flow("4")
                    );
                }

                private static String flow(final String input) {
                    final int decoded;
                    try {
                        decoded = decode(input);
                    } catch (final IllegalArgumentException exception) {
                        return "decode:" + exception.getMessage();
                    }

                    final Optional<Box> box = Optional.of(new Box(decoded));
                    final int incremented;
                    try {
                        incremented = box.orElseThrow().incremented();
                    } catch (final IllegalArgumentException exception) {
                        return "increment:" + exception.getMessage();
                    }

                    final Result dispatched;
                    try {
                        dispatched = box.map(value -> dispatch(input, value)).orElseGet(Result::zero);
                    } catch (final RuntimeException exception) {
                        return "dispatch:" + exception.getMessage();
                    }

                    final State state;
                    try {
                        state = new State(dispatched.value()).withValue(incremented);
                    } catch (final IllegalStateException exception) {
                        return "state:" + exception.getMessage();
                    }
                    return "ok:" + state.value();
                }

                private static int decode(final String input) {
                    if ("bad".equals(input)) {
                        throw new IllegalArgumentException("bad");
                    }
                    if ("boom".equals(input)) {
                        return 2;
                    }
                    return Integer.parseInt(input);
                }

                private static Result dispatch(final String input, final Box box) {
                    if ("boom".equals(input)) {
                        throw new IllegalStateException("boom");
                    }
                    return new Result(box.value());
                }

                private record Box(int value) {
                    private int incremented() {
                        if (value < 0) {
                            throw new IllegalArgumentException("negative");
                        }
                        return value + 1;
                    }
                }

                private record Result(int value) {
                    private static Result zero() {
                        return new Result(0);
                    }
                }

                private record State(int value) {
                    private State withValue(final int next) {
                        if (next == 14) {
                            throw new IllegalStateException("blocked");
                        }
                        return new State(next);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
    }

    @Test
    void fiveDisjointTypedCatchRangesMatchJvm() throws Exception {
        final Path project = project("platform-catch-five-disjoint-ranges");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Optional<Value> value = Optional.of(new Value(-1));
                    int result = 0;
                    try {
                        result += Helper.increment(value.orElseThrow().number());
                    } catch (final IllegalArgumentException exception) {
                        result++;
                    }
                    try {
                        result += Helper.increment(value.orElseThrow().number());
                    } catch (final IllegalArgumentException exception) {
                        result++;
                    }
                    try {
                        result += Helper.increment(value.orElseThrow().number());
                    } catch (final IllegalArgumentException exception) {
                        result++;
                    }
                    try {
                        result += Helper.increment(value.orElseThrow().number());
                    } catch (final IllegalArgumentException exception) {
                        result++;
                    }
                    try {
                        result += Helper.increment(value.orElseThrow().number());
                    } catch (final IllegalArgumentException exception) {
                        result++;
                    }
                    System.out.println(result);
                }

                private record Value(int number) {
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static int increment(final int value) {
                    if (value < 0) {
                        throw new IllegalArgumentException("negative");
                    }
                    return value + 1;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("5\n");
    }

    @Test
    void finallyExceptionReplacesPendingIOException() throws Exception {
        final Path project = project("finally-replaces-pending-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.io.IOException;
            import java.nio.file.CopyOption;
            import java.nio.file.Files;
            import java.nio.file.LinkOption;
            import java.nio.file.OpenOption;
            import java.nio.file.Path;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws IOException {
                    final Path source = Path.of("source");
                    final Path target = Path.of("copy");
                    Files.deleteIfExists(target);
                    Files.writeString(source, "value", new OpenOption[0]);
                    System.out.println(result(source, target, new CopyOption[0]));
                    System.out.println(Files.exists(target, new LinkOption[0]));
                    System.out.println(result(
                        Path.of("missing"),
                        Path.of("missing-copy"),
                        new CopyOption[0]
                    ));
                }

                private static String result(
                    final Path source,
                    final Path target,
                    final CopyOption[] options
                ) throws IOException {
                    try {
                        copy(source, target, options);
                        return "wrong";
                    } catch (final IllegalStateException exception) {
                        return exception.getMessage();
                    }
                }

                private static void copy(
                    final Path source,
                    final Path target,
                    final CopyOption[] options
                ) throws IOException {
                    try {
                        Files.copy(source, target, options);
                    } finally {
                        System.out.println("cleanup");
                        throw new IllegalStateException("replacement");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        assertThat(nativeOutput(project)).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("""
            cleanup
            replacement
            true
            cleanup
            replacement
            """);
    }

    private String nativeOutput(final Path project) {
        return nativeOutput(project, Map.of());
    }

    private String nativeOutput(final Path project, final Map<String, String> environment) {
        build(project);
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin").resolve(project.getFileName()).toString()),
            defaultProcessTimeout(),
            environment
        );
        if (nativeRun.exitCode() != 0) {
            throw new AssertionError(nativeRun.stderr());
        }
        return nativeRun.stdout();
    }

    private void build(final Path project) {
        final CliRun build = run(tempDir, "build", project.toString());
        if (build.exitCode() != 0) {
            throw new AssertionError(build.stderr());
        }
    }

    private ProcessResult nativeRun(final Path project) {
        return process(
            project,
            List.of(project.resolve(".javan/bin").resolve(project.getFileName()).toString())
        );
    }
}
