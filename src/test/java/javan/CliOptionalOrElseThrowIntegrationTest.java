package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliOptionalOrElseThrowIntegrationTest extends CliIntegrationSupport {
    @Test
    void presentOptionalSkipsCapturedExceptionSupplier() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-present", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private static int calls;

                private Main() {
                }

                public static void main(final String[] args) {
                    final String name = args.length == 0 ? "missing" : args[0];
                    System.out.println(Optional.of("value")
                        .orElseThrow(() -> failure(name)));
                    System.out.println(calls);
                }

                private static IllegalArgumentException failure(final String name) {
                    calls++;
                    return new IllegalArgumentException(name);
                }
            }
            """);
    }

    @Test
    void emptyOptionalThrowsCapturedSuppliedException() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-empty", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String name = args.length == 0 ? "widget" : args[0];
                    try {
                        Optional.empty()
                            .orElseThrow(() -> new IllegalArgumentException("missing:" + name));
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void emptyOptionalInvokesSupplierExactlyOnce() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-once", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private static int calls;

                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty().orElseThrow(Main::failure);
                    } catch (final IllegalArgumentException ignored) {
                        System.out.println(calls);
                    }
                }

                private static IllegalArgumentException failure() {
                    calls++;
                    return new IllegalArgumentException("missing");
                }
            }
            """);
    }

    @Test
    void superclassCatchReceivesSuppliedException() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-superclass-catch", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty()
                            .orElseThrow(() -> new IllegalArgumentException("missing"));
                    } catch (final RuntimeException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void declaredSupertypeReturningSubtypeIsRejectedByCheck() throws Exception {
        assertCheckRejects("optional-or-else-throw-dynamic-subtype", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.empty().orElseThrow(Main::failure);
                }

                private static RuntimeException failure() {
                    return new IllegalArgumentException("missing");
                }
            }
            """);
    }

    @Test
    void conditionalSubtypeReturnIsRejectedByCheck() throws Exception {
        assertCheckRejects("optional-or-else-throw-conditional-subtype", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String marker = args.length == 0 ? "" : args[0];
                    Optional.empty().orElseThrow(() -> failure(marker));
                }

                private static RuntimeException failure(final String marker) {
                    return marker.isEmpty()
                        ? new IllegalArgumentException("missing")
                        : new RuntimeException("missing");
                }
            }
            """);
    }

    @Test
    void exceptionHandlerReturnShapeRetainsHandlerRejection() throws Exception {
        final Path project = project("optional-or-else-throw-handler-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.empty().orElseThrow(Main::failure);
                }

                private static RuntimeException failure() {
                    try {
                        return new RuntimeException("missing");
                    } catch (final RuntimeException exception) {
                        return new RuntimeException("fallback");
                    }
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN014]")
        )).containsExactly(2, true);
    }

    @Test
    void multiCatchReceivesSuppliedException() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-multi-catch", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty()
                            .orElseThrow(() -> new IllegalArgumentException("missing"));
                    } catch (final IllegalArgumentException | IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void assignmentInsideTryKeepsSuppliedExceptionCatchable() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-assignment", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        final String value = Optional.<String>empty()
                            .orElseThrow(() -> new IllegalArgumentException("missing"));
                        System.out.println(value);
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void nestedInvocationInsideTryKeepsSuppliedExceptionCatchable() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-nested-invocation", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        System.out.println(Optional.<String>empty()
                            .orElseThrow(() -> new IllegalArgumentException("missing")));
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void returnInsideTryKeepsPresentValue() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-return", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(value());
                }

                private static String value() {
                    try {
                        return Optional.of("value")
                            .orElseThrow(() -> new IllegalArgumentException("missing"));
                    } catch (final IllegalArgumentException exception) {
                        return exception.getMessage();
                    }
                }
            }
            """);
    }

    @Test
    void catchLocalSlotCanBeReusedAfterHandler() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-local-reuse", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty()
                            .orElseThrow(() -> new IllegalArgumentException("missing"));
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                    final String reused = "after";
                    System.out.println(reused);
                }
            }
            """);
    }

    @Test
    void unrelatedSwitchDoesNotInvalidateDirectSupplier() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-unrelated-switch", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value;
                    switch (args.length) {
                        case 0 -> value = "zero";
                        case 1 -> value = "one";
                        default -> value = "many";
                    }
                    System.out.println(Optional.of(value)
                        .orElseThrow(() -> new IllegalArgumentException("missing")));
                }
            }
            """);
    }

    @Test
    void nullSupplierResultThrowsCanonicalNullPointerException() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-null", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty().orElseThrow(() -> null);
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void nullMessageSuppliedExceptionRetainsItsExceptionType() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-null-message", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty().orElseThrow(
                            () -> new IllegalArgumentException()
                        );
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void helperSuppliedNullMessageExceptionRetainsItsExceptionType() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-helper-null-message", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty().orElseThrow(Main::failure);
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static IllegalArgumentException failure() {
                    return helper();
                }

                private static IllegalArgumentException helper() {
                    return new IllegalArgumentException();
                }
            }
            """);
    }

    @Test
    void nullableFactoryWithNullSupplierResultRemainsCatchable() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-nullable-factory-supplier", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.ofNullable(null).orElseThrow(() -> null);
                    } catch (final NullPointerException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void checkcastInsideOptionalProtectedRangeIsRejected() throws Exception {
        final Path project = project("optional-or-else-throw-checkcast");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        final String value = (String) Optional.<Object>of(7)
                            .orElseThrow(() -> new IllegalArgumentException("missing"));
                        System.out.println(value);
                    } catch (final ClassCastException exception) {
                        System.out.println("cast");
                    }
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN014]")
        )).containsExactly(2, true);
    }

    @Test
    void nullableLocalReceiverUnderNullPointerCatchIsRejected() throws Exception {
        final Path project = project("optional-or-else-throw-nullable-local");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Optional<String> value = null;
                    try {
                        value.orElseThrow(
                            () -> new IllegalArgumentException("missing")
                        );
                    } catch (final NullPointerException exception) {
                        System.out.println("caught");
                    }
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN014]")
        )).containsExactly(2, true);
    }

    @Test
    void nullableOptionalFactoryUnderNullPointerCatchIsRejected() throws Exception {
        final Path project = project("optional-or-else-throw-nullable-factory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.of(null).orElseThrow(
                            () -> new IllegalArgumentException("missing")
                        );
                    } catch (final NullPointerException exception) {
                        System.out.println("caught");
                    }
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN014]")
        )).containsExactly(2, true);
    }

    @Test
    void supplierInternalCheckcastIsRejectedByCheck() throws Exception {
        final Path project = project("optional-or-else-throw-supplier-checkcast");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty().orElseThrow(Main::failure);
                    } catch (final ClassCastException exception) {
                        System.out.println("cast");
                    }
                }

                private static IllegalArgumentException failure() {
                    final Object value = 7;
                    final String ignored = (String) value;
                    return new IllegalArgumentException(ignored);
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN014]")
        )).containsExactly(2, true);
    }

    @Test
    void recursiveThrowsOnlySupplierIsRejectedByCheck() throws Exception {
        assertCheckRejects("optional-or-else-throw-recursive", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.empty().orElseThrow(Main::failure);
                }

                private static IllegalArgumentException failure() {
                    throw failure();
                }
            }
            """);
    }

    @Test
    void supplierThrownExceptionPropagatesToCallerCatch() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-supplier-failure", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        Optional.empty().orElseThrow(() -> failure());
                    } catch (final IllegalStateException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static IllegalArgumentException failure() {
                    throw new IllegalStateException("supplier");
                }
            }
            """);
    }

    @Test
    void localOptionalReceiverKeepsSuppliedExceptionCatchable() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-local-receiver", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Optional<String> value = Optional.empty();
                    try {
                        value.orElseThrow(() -> new IllegalArgumentException("local"));
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void staticFieldOptionalReceiverKeepsPresentValue() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-static-field", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private static final Optional<String> VALUE = Optional.of("static");

                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(VALUE.orElseThrow(
                        () -> new IllegalArgumentException("missing")
                    ));
                }
            }
            """);
    }

    @Test
    void instanceFieldOptionalReceiverKeepsSuppliedExceptionCatchable() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-instance-field", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private final Optional<String> value = Optional.empty();

                public static void main(final String[] args) {
                    final Main main = new Main();
                    try {
                        main.value.orElseThrow(
                            () -> new IllegalArgumentException("instance")
                        );
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void nullableInstanceFieldReceiverUnderRuntimeCatchIsRejected() throws Exception {
        final Path project = project("optional-or-else-throw-nullable-instance-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private final Optional<String> value = Optional.empty();

                public static void main(final String[] args) {
                    final Main main = args.length == 0 ? null : new Main();
                    try {
                        main.value.orElseThrow(
                            () -> new IllegalArgumentException("instance")
                        );
                    } catch (final RuntimeException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN014]")
        )).containsExactly(2, true);
    }

    @Test
    void applicationStaticMethodOptionalReceiverKeepsPresentValue() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-static-method", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(value().orElseThrow(
                        () -> new IllegalArgumentException("missing")
                    ));
                }

                private static Optional<String> value() {
                    return Optional.of("static-method");
                }
            }
            """);
    }

    @Test
    void applicationVirtualMethodOptionalReceiverKeepsPresentValue() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-virtual-method", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Main().value().orElseThrow(
                        () -> new IllegalArgumentException("missing")
                    ));
                }

                private Optional<String> value() {
                    return Optional.of("virtual-method");
                }
            }
            """);
    }

    @Test
    void applicationInterfaceMethodOptionalReceiverKeepsPresentValue() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-interface-method", """
            package com.acme;

            import java.util.Optional;

            public final class Main implements Values {
                public static void main(final String[] args) {
                    final Values values = new Main();
                    System.out.println(values.value().orElseThrow(
                        () -> new IllegalArgumentException("missing")
                    ));
                }

                @Override
                public Optional<String> value() {
                    return Optional.of("interface-method");
                }
            }

            interface Values {
                Optional<String> value();
            }
            """);
    }

    @Test
    void chainedOptionalReceiverKeepsFilteredValue() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-chain", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.ofNullable("chain")
                        .filter(value -> value.length() == 5)
                        .orElseThrow(() -> new IllegalArgumentException("missing")));
                }
            }
            """);
    }

    @Test
    void referenceArrayCaptureSuppliesException() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-reference-array", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] details = {"reference-array"};
                    try {
                        Optional.empty().orElseThrow(
                            () -> new IllegalArgumentException(details[0])
                        );
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void primitiveArrayCaptureSuppliesException() throws Exception {
        assertNativeMatchesJvm("optional-or-else-throw-primitive-array", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] details = {7};
                    try {
                        Optional.empty().orElseThrow(
                            () -> new IllegalArgumentException("code:" + details[0])
                        );
                    } catch (final IllegalArgumentException exception) {
                        System.out.println(exception.getMessage());
                    }
                }
            }
            """);
    }

    @Test
    void storedSupplierRemainsRejectedByCheck() throws Exception {
        assertCheckRejects("optional-or-else-throw-stored", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<IllegalArgumentException> supplier =
                        () -> new IllegalArgumentException("missing");
                    Optional.empty().orElseThrow(supplier);
                }
            }
            """);
    }

    @Test
    void primitiveCaptureRemainsRejectedByCheck() throws Exception {
        assertCheckRejects("optional-or-else-throw-primitive-capture", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int code = args.length;
                    Optional.empty()
                        .orElseThrow(() -> new IllegalArgumentException("missing:" + code));
                }
            }
            """);
    }

    @Test
    void boundInstanceSupplierRemainsRejectedByCheck() throws Exception {
        assertCheckRejects("optional-or-else-throw-bound-instance", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                public static void main(final String[] args) {
                    Optional.empty().orElseThrow(new Main()::failure);
                }

                private IllegalArgumentException failure() {
                    return new IllegalArgumentException("missing");
                }
            }
            """);
    }

    private void assertNativeMatchesJvm(final String projectName, final String source) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source);
        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final ProcessResult nativeRun = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/" + projectName).toString()))
            : new ProcessResult(-1, "", build.stderr());

        assertThat(List.of(
            build.exitCode(),
            build.stderr(),
            nativeRun.exitCode(),
            nativeRun.stderr(),
            nativeRun.stdout()
        )).containsExactly(0, "", 0, "", jvmOutput);
    }

    private void assertCheckRejects(final String projectName, final String source) throws Exception {
        final Path project = project(projectName);
        writeJava(project, "com.acme.Main", source);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(List.of(
            check.exitCode(),
            check.stderr().contains("error[JAVAN031]"),
            check.stderr().contains(
                "java/util/Optional.orElseThrow(Ljava/util/function/Supplier;)Ljava/lang/Object;"
            )
        )).containsExactly(2, true, true);
    }
}
