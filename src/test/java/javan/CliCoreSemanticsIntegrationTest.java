package javan;

import javan.testing.TestSuite.NativeTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliCoreSemanticsIntegrationTest extends CliIntegrationSupport {
    @Test
    void buildOmitsUnusedClassMetadataWithoutRenumberingLiveTypes() throws Exception {
        final Path project = project("reachable-class-metadata");
        writeJava(project, "com.acme.AlphaUnused", """
            package com.acme;

            final class AlphaUnused {
                Object payload;
            }
            """);
        writeJava(project, "com.acme.Used", """
            package com.acme;

            final class Used {
                Object payload;
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Used().getClass().getName());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/reachable-class-metadata").toString())).stdout())
            .isEqualTo("com.acme.Used\n");
        assertThat(generatedProgramSource(project))
            .contains("javan_class_com_acme_Used", "\"com.acme.Used\"")
            .doesNotContain("javan_class_com_acme_AlphaUnused", "\"com.acme.AlphaUnused\"");
    }

    @Test
    void staticHelperCallBuilds() throws Exception {
        final Path project = project("helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Helper.print();
                }
            }
            """);
        writeJava(project, "com.acme.Helper", """
            package com.acme;

            public final class Helper {
                private Helper() {
                }

                public static void print() {
                    System.out.println("helper-output");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/helper").toString())).stdout()).isEqualTo("helper-output\n");
    }

    @Test
    void buildKeepsManagedRootsLiveAcrossLivenessWordBoundaries() throws Exception {
        final Path project = project("root-liveness-word-boundaries");
        final StringBuilder source = new StringBuilder("""
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
            """);
        for (int index = 0; index < 130; index++) {
            source.append("        final int[] root_").append(index).append(" = new int[] { ").append(index).append(" };\n");
        }
        source.append("""
                    System.out.println(root_0[0] + root_64[0] + root_129[0]);
                }
            }
            """);
        writeJava(project, "com.acme.Main", source.toString());

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/root-liveness-word-boundaries").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("193\n");
    }

    @Test
    void arrayAccessesPreserveCatchableJavaExceptions() throws Exception {
        final Path project = project("array-access-exception-semantics");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int index = args.length - 1;
                    System.out.println(read(index));
                    System.out.println(write(index));
                    System.out.println(nullLength());
                    System.out.println(objectWrite(index));
                }

                private static String read(final int index) {
                    final int[] values = {17};
                    final int value;
                    try {
                        value = values[index];
                    } catch (final ArrayIndexOutOfBoundsException exception) {
                        return "read:" + exception.getMessage();
                    }
                    return "read:" + value;
                }

                private static String write(final int index) {
                    final int[] values = {17};
                    int wrote = 0;
                    try {
                        values[index] = 42;
                        wrote = 1;
                    } catch (final ArrayIndexOutOfBoundsException exception) {
                        return "write:" + exception.getMessage();
                    }
                    return wrote == 1 ? "write:ok" : "write:missing";
                }

                private static String nullLength() {
                    final int[] values = null;
                    final int length;
                    try {
                        length = values.length;
                    } catch (final NullPointerException exception) {
                        return "length:null";
                    }
                    return "length:" + length;
                }

                private static String objectWrite(final int index) {
                    final Object[] values = new String[1];
                    final Object value = "ok";
                    int wrote = 0;
                    try {
                        values[index] = value;
                        wrote = 1;
                    } catch (final ArrayIndexOutOfBoundsException exception) {
                        return "object:" + exception.getMessage();
                    }
                    return wrote == 1 ? "object:ok" : "object:unreachable";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/array-access-exception-semantics").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("""
            read:Index -1 out of bounds for length 1
            write:Index -1 out of bounds for length 1
            length:null
            object:Index -1 out of bounds for length 1
            """);
        assertThat(generatedProgramSource(project)).contains("javan_array_index_out_of_bounds_message");
    }

    @Test
    void arrayAccessExceptionsPropagateAcrossApplicationMethods() throws Exception {
        final Path project = project("array-access-exception-propagation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(readOrCatch(args.length - 1));
                    System.out.println(lengthOrCatch(null));
                }

                private static String readOrCatch(final int index) {
                    final int value;
                    try {
                        value = read(index);
                    } catch (final ArrayIndexOutOfBoundsException exception) {
                        return exception.getMessage();
                    }
                    return "value:" + value;
                }

                private static String lengthOrCatch(final int[] values) {
                    final int length;
                    try {
                        length = lengthOf(values);
                    } catch (final NullPointerException exception) {
                        return "null";
                    }
                    return "length:" + length;
                }

                private static int read(final int index) {
                    final int[] values = {17};
                    return values[index];
                }

                private static int lengthOf(final int[] values) {
                    return values.length;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/array-access-exception-propagation").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("""
            Index -1 out of bounds for length 1
            null
            """);
    }

    @Test
    void staticFieldsAndClassInitializerBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("static-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(State.count);
                    System.out.println(State.total);
                    System.out.println(State.label);
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public final class State {
                static int count;
                static long total;
                static String label;

                static {
                    count = 41;
                    count = count + 1;
                    total = 80L + 4L;
                    label = "ready";
                }

                private State() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/static-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n84\nready\n");
        final String generated = generatedProgramSource(project);
        assertThat(generated).contains(
            "static void** javan_static_roots[] = {",
            "(void**) &javan_static_com_acme_State_field_label",
            "javan_register_static_roots(javan_static_roots, 1);"
        );
        final int mainStart = generated.indexOf("int JAVAN_PROGRAM_MAIN");
        assertThat(generated.indexOf("    javan_register_generated_roots();", mainStart))
            .isLessThan(generated.indexOf("    javan_initialize_com_acme_State();", mainStart));
    }

    @Test
    void classInitializationIsLazyAndDependencyOrdered() throws Exception {
        final Path project = project("class-initialization-order");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                static {
                    System.out.println("main");
                }

                private Main() {
                }

                public static void main(final String[] args) {
                    if (args.length > 0) {
                        Unused.touch();
                    }
                    InheritedChild.touch();
                    System.out.println(Child.value);
                    new Constructed();
                    Written.value = 7;
                    System.out.println(Written.value);
                    Called.touch();
                    Called.touch();
                    EvaluationTarget.value = EvaluationSource.value();
                    EvaluationCall.accept(EvaluationSource.next());
                }
            }
            """);
        writeJava(project, "com.acme.InheritedBase", """
            package com.acme;

            class InheritedBase {
                static {
                    System.out.println("inherited-base");
                }

                static void touch() {
                    System.out.println("inherited-call");
                }
            }
            """);
        writeJava(project, "com.acme.InheritedChild", """
            package com.acme;

            final class InheritedChild extends InheritedBase {
                static {
                    System.out.println("wrong-owner");
                }
            }
            """);
        writeJava(project, "com.acme.Defaulted", """
            package com.acme;

            interface Defaulted {
                int READY = ready();

                private static int ready() {
                    System.out.println("interface");
                    return 1;
                }

                default int marker() {
                    return 0;
                }
            }
            """);
        writeJava(project, "com.acme.Parent", """
            package com.acme;

            class Parent {
                static {
                    System.out.println("parent");
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            final class Child extends Parent implements Defaulted {
                static int value = initialize();

                private static int initialize() {
                    System.out.println("child");
                    return READY;
                }
            }
            """);
        writeJava(project, "com.acme.Constructed", """
            package com.acme;

            final class Constructed {
                static {
                    System.out.println("constructed");
                }
            }
            """);
        writeJava(project, "com.acme.Written", """
            package com.acme;

            final class Written {
                static int value;

                static {
                    System.out.println("written");
                }
            }
            """);
        writeJava(project, "com.acme.Called", """
            package com.acme;

            final class Called {
                static {
                    System.out.println("called-init");
                }

                static void touch() {
                    System.out.println("called");
                }
            }
            """);
        writeJava(project, "com.acme.Unused", """
            package com.acme;

            final class Unused {
                static {
                    System.out.println("unused");
                }

                static void touch() {
                }
            }
            """);
        writeJava(project, "com.acme.EvaluationSource", """
            package com.acme;

            final class EvaluationSource {
                static {
                    System.out.println("source-init");
                }

                static int value() {
                    System.out.println("value");
                    return 8;
                }

                static int next() {
                    System.out.println("next");
                    return 9;
                }
            }
            """);
        writeJava(project, "com.acme.EvaluationTarget", """
            package com.acme;

            final class EvaluationTarget {
                static int value;

                static {
                    System.out.println("target-init");
                }
            }
            """);
        writeJava(project, "com.acme.EvaluationCall", """
            package com.acme;

            final class EvaluationCall {
                static {
                    System.out.println("call-init");
                }

                static void accept(final int value) {
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(jvmOutput).isEqualTo(
            "main\ninherited-base\ninherited-call\nparent\ninterface\nchild\n1\nconstructed\nwritten\n7\ncalled-init\ncalled\ncalled\n"
                + "source-init\nvalue\ntarget-init\nnext\ncall-init\n9\n"
        );
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-initialization-order").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/class-initialization.json"))).contains(
            "\"strategy\": \"lazy-runtime-once\"",
            "\"kind\": \"getstatic\"",
            "\"kind\": \"putstatic\"",
            "\"kind\": \"invokestatic\"",
            "\"kind\": \"new\"",
            "\"target\": \"com/acme/Unused\""
        );
    }

    @Test
    void enumClassInitializationIsLazyAndPrecedesTheFirstConstantUse() throws Exception {
        final Path project = project("enum-class-initialization");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("before");
                    final Class<?> ignored = Color.class;
                    System.out.println("after-literal");
                    System.out.println(Color.RED);
                }
            }
            """);
        writeJava(project, "com.acme.Color", """
            package com.acme;

            enum Color {
                RED;

                static {
                    System.out.println("color-init");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(jvmOutput).isEqualTo("before\nafter-literal\ncolor-init\nRED\n");
        assertThat(process(project, List.of(project.resolve(".javan/bin/enum-class-initialization").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void classInitializationHandlesReentryCycles() throws Exception {
        final Path project = project("class-initialization-cycle");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(First.value);
                }
            }
            """);
        writeJava(project, "com.acme.First", """
            package com.acme;

            final class First {
                static int value = initialize();

                private static int initialize() {
                    System.out.println("first-start");
                    final int result = Second.value + 1;
                    System.out.println("first-end");
                    return result;
                }
            }
            """);
        writeJava(project, "com.acme.Second", """
            package com.acme;

            final class Second {
                static int value = initialize();

                private static int initialize() {
                    System.out.println("second-start");
                    final int result = First.value + 1;
                    System.out.println("second-end");
                    return result;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(jvmOutput).isEqualTo("first-start\nsecond-start\nsecond-end\nfirst-end\n2\n");
        assertThat(process(project, List.of(project.resolve(".javan/bin/class-initialization-cycle").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void staticIntMethodBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("primitive-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(add(7, 5));
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-int").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("12\n");
    }

    @Test
    void intLocalsArithmeticAndLargeConstantsBuild() throws Exception {
        final Path project = project("int-locals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int value = calculate(40000, 9);
                    System.out.println(value);
                }

                public static int calculate(final int left, final int right) {
                    final int sum = left + right;
                    final int product = sum * 2;
                    return product - 3;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-locals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("80015\n");
    }

    @Test
    void intBitwiseAndBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-bitwise-and");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(0b1110, 0b1011));
                }

                public static int mask(final int left, final int right) {
                    return left & right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-bitwise-and").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n");
    }

    @Test
    void longBitwiseAndBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-bitwise-and");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(14L, 11L));
                }

                public static long mask(final long left, final long right) {
                    return left & right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-bitwise-and").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intBitwiseOrBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-bitwise-or");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(0b1100, 0b0011));
                }

                public static int mask(final int left, final int right) {
                    return left | right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-bitwise-or").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longBitwiseOrBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-bitwise-or");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(12L, 3L));
                }

                public static long mask(final long left, final long right) {
                    return left | right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-bitwise-or").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intBitwiseXorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-bitwise-xor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(0b1110, 0b1011));
                }

                public static int mask(final int left, final int right) {
                    return left ^ right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-bitwise-xor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longBitwiseXorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-bitwise-xor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(mask(14L, 11L));
                }

                public static long mask(final long left, final long right) {
                    return left ^ right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-bitwise-xor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longCompareBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(compare(12L));
                    System.out.println(compare(10L));
                    System.out.println(compare(7L));
                }

                public static int compare(final long value) {
                    if (value > 10L) {
                        return 1;
                    }
                    if (value == 10L) {
                        return 0;
                    }
                    return -1;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-compare").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n0\n-1\n");
    }

    @Test
    void staticVoidMethodWithIntArgumentBuilds() throws Exception {
        final Path project = project("int-void-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    print(42);
                }

                public static void print(final int value) {
                    System.out.println(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-void-helper").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void printStringBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("print-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.print("ja");
                    System.out.println("van");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/print-string").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n");
    }

    @Test
    void ifElseIntReturnBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("if-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(max(10, 7));
                    System.out.println(max(2, 9));
                }

                public static int max(final int left, final int right) {
                    if (left > right) {
                        return left;
                    }
                    return right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-return").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n9\n");
    }

    @Test
    void ifElsePrintBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("if-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    printSign(-3);
                    printSign(0);
                    printSign(5);
                }

                public static void printSign(final int value) {
                    if (value < 0) {
                        System.out.println(-1);
                    } else if (value == 0) {
                        System.out.println(0);
                    } else {
                        System.out.println(1);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-print").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-1\n0\n1\n");
    }

    @Test
    void ifWithAllIntComparisonOperatorsBuilds() throws Exception {
        final Path project = project("if-comparisons");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(3, 3));
                    System.out.println(score(4, 3));
                    System.out.println(score(2, 3));
                }

                public static int score(final int left, final int right) {
                    if (left == right) {
                        return 10;
                    }
                    if (left != right) {
                        if (left >= right) {
                            return 20;
                        }
                        if (left <= right) {
                            return 30;
                        }
                    }
                    return 40;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/if-comparisons").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("10\n20\n30\n");
    }

    @Test
    void whileLoopPrintsAndMatchesJvmOutput() throws Exception {
        final Path project = project("while-print");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int index = 0;
                    while (index < 3) {
                        System.out.println(index);
                        index++;
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-print").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n1\n2\n");
    }

    @Test
    void postIncrementArgumentUsesValueBeforeIncrement() throws Exception {
        final Path project = project("post-increment-argument");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<Integer> values = List.of(7, 9);
                    int index = 0;
                    System.out.println(values.get(index++));
                    System.out.println(index);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/post-increment-argument").toString())).stdout())
            .isEqualTo(jvmOutput)
            .isEqualTo("7\n1\n");
    }

    @Test
    void whileLoopAccumulatorMatchesJvmOutput() throws Exception {
        final Path project = project("while-sum");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(sum(5));
                    System.out.println(sum(0));
                }

                public static int sum(final int limit) {
                    int total = 0;
                    int index = 1;
                    while (index <= limit) {
                        total = total + index;
                        index++;
                    }
                    return total;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-sum").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("15\n0\n");
    }

    @Test
    void whileLoopDecrementMatchesJvmOutput() throws Exception {
        final Path project = project("while-decrement");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = 3;
                    while (value > 0) {
                        System.out.println(value);
                        value--;
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/while-decrement").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n2\n1\n");
    }

    @Test
    void objectConstructorFieldsAndInstanceMethodsMatchJvmOutput() throws Exception {
        final Path project = project("object-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Point point = new Point(10, 5);
                    System.out.println(point.sum());
                    System.out.println(PointOps.weighted(point, 3));
                }
            }
            """);
        writeJava(project, "com.acme.Point", """
            package com.acme;

            public final class Point {
                private final int x;
                private final int y;

                public Point(final int x, final int y) {
                    this.x = x;
                    this.y = y;
                }

                public int sum() {
                    return x + y;
                }

                public int score(final int factor) {
                    return sum() * factor;
                }
            }
            """);
        writeJava(project, "com.acme.PointOps", """
            package com.acme;

            public final class PointOps {
                private PointOps() {
                }

                public static int weighted(final Point point, final int factor) {
                    return point.score(factor);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("15\n45\n");
    }

    @Test
    void objectStringFieldReturnAndNullBranchMatchJvmOutput() throws Exception {
        final Path project = project("object-string-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("ready");
                    System.out.println(message.text());
                    System.out.println(label(null));
                    System.out.println(label(message));
                }

                public static String label(final Message message) {
                    if (message == null) {
                        return "missing";
                    }
                    return message.text();
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public final class Message {
                private final String text;

                public Message(final String text) {
                    this.text = text;
                }

                public String text() {
                    return text;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-string-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ready\nmissing\nready\n");
    }

    @Test
    void nonFinalClassWithoutKnownSubclassInstanceCallBuilds() throws Exception {
        final Path project = project("non-final-exact");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("exact");
                    System.out.println(message.text());
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public class Message {
                private final String text;

                public Message(final String text) {
                    this.text = text;
                }

                public String text() {
                    return text;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/non-final-exact").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("exact\n");
    }

    @Test
    void simpleRecordConstructorAndAccessorBuilds() throws Exception {
        final Path project = project("simple-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("record");
                    System.out.println(message.text());
                }
            }
            """);
        writeJava(project, "com.acme.Message", """
            package com.acme;

            public record Message(String text) {
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/simple-record").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("record\n");
    }

    @Test
    void objectArrayInitializerLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("object-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[]{"zero", "one"};
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\none\n");
    }

    @Test
    void intArrayInitializerLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("int-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[]{2, 3};
                    values[1] = 9;
                    System.out.println(values.length);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n9\n");
    }

    @Test
    void intArrayStaticReturnAndParameterBuilds() throws Exception {
        final Path project = project("int-array-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = values();
                    System.out.println(second(values));
                }

                public static int[] values() {
                    return new int[]{4, 8};
                }

                public static int second(final int[] values) {
                    return values[1];
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-array-helper").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("8\n");
    }

    @Test
    void booleanFieldReturnBranchAndPrintBuilds() throws Exception {
        final Path project = project("boolean-basic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Flag flag = new Flag(true);
                    System.out.println(flag.value());
                    System.out.println(invert(flag.value()));
                }

                public static boolean invert(final boolean value) {
                    if (value) {
                        return false;
                    }
                    return true;
                }
            }
            """);
        writeJava(project, "com.acme.Flag", """
            package com.acme;

            public final class Flag {
                private boolean value;

                public Flag(final boolean value) {
                    this.value = value;
                }

                public boolean value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-basic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void floatArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("float-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final float[] values = new float[]{1.25f, 2.5f};
                    values[1] = 3.75f;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1.25\n3.75\n");
    }

    @Test
    void booleanArrayLoadStoreAndLengthBuilds() throws Exception {
        final Path project = project("boolean-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] values = new boolean[]{false, true};
                    values[0] = true;
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boolean-array").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\ntrue\n");
    }

    @Test
    void byteShortAndCharArraysBuild() throws Exception {
        final Path project = project("small-primitive-arrays");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final byte[] bytes = new byte[]{-2, 3};
                    bytes[1] = -5;
                    final short[] shorts = new short[]{300, -7};
                    final char[] chars = new char[]{'A', 'B'};
                    chars[1] = 'C';
                    System.out.println(bytes[0]);
                    System.out.println(bytes[1]);
                    System.out.println(shorts[0]);
                    System.out.println(shorts[1]);
                    System.out.println(chars[0] + 1);
                    System.out.println(chars[1] + 0);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/small-primitive-arrays").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-2\n-5\n300\n-7\n66\n67\n");
    }

    @Test
    void longArithmeticReturnAndPrintBuilds() throws Exception {
        final Path project = project("long-arithmetic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(calculate(40L, 2L));
                }

                public static long calculate(final long left, final long right) {
                    final long sum = left + right;
                    return (sum * 2L) - 4L;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-arithmetic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("80\n");
    }

    @Test
    void intToLongConversionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-to-long-conversion");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = -7;
                    long widened = value;
                    System.out.println(widened);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-to-long-conversion").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longFieldsConstructorAndGetterBuild() throws Exception {
        final Path project = project("long-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Counter counter = new Counter(7L);
                    System.out.println(counter.value());
                }
            }
            """);
        writeJava(project, "com.acme.Counter", """
            package com.acme;

            public final class Counter {
                private long value;

                public Counter(final long value) {
                    this.value = value;
                }

                public long value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-field").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void longParameterSlotWidthBuilds() throws Exception {
        final Path project = project("long-slots");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(pick(3L, "ignored", 4L));
                }

                public static long pick(final long first, final String ignored, final long second) {
                    return first + second;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-slots").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
    }

    @Test
    void floatAndDoubleArithmeticReturnAndPrintBuilds() throws Exception {
        final Path project = project("float-double-arithmetic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(scale(1.25f, 2.5f));
                    System.out.println(measure(4.0, 0.25));
                }

                public static float scale(final float left, final float right) {
                    return (left + right) * 2.0f;
                }

                public static double measure(final double left, final double right) {
                    return (left / 2.0) + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-arithmetic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7.5\n2.25\n");
    }

    @Test
    void floatAndDoubleFieldsConstructorAndGetterBuild() throws Exception {
        final Path project = project("float-double-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Reading reading = new Reading(1.25f, 2.5);
                    System.out.println(reading.ratio());
                    System.out.println(reading.total());
                }
            }
            """);
        writeJava(project, "com.acme.Reading", """
            package com.acme;

            public final class Reading {
                private float ratio;
                private double total;

                public Reading(final float ratio, final double total) {
                    this.ratio = ratio;
                    this.total = total;
                }

                public float ratio() {
                    return ratio;
                }

                public double total() {
                    return total;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-field").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void floatAndDoubleComparisonsBuild() throws Exception {
        final Path project = project("float-double-compare");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(score(2.5f, 1.25f));
                    System.out.println(rank(1.0, 2.0));
                }

                public static int score(final float left, final float right) {
                    if (left > right) {
                        return 1;
                    }
                    return 0;
                }

                public static int rank(final double left, final double right) {
                    if (left < right) {
                        return -1;
                    }
                    return 0;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-compare").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n-1\n");
    }

    @Test
    void staticFloatAndDoubleFieldsBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("static-float-double-fields");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(State.ratio);
                    System.out.println(State.total);
                }
            }
            """);
        writeJava(project, "com.acme.State", """
            package com.acme;

            public final class State {
                static float ratio;
                static double total;

                static {
                    ratio = 1.25f;
                    total = 2.5;
                }

                private State() {
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/static-float-double-fields").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
    }

    @Test
    void floatAndDoubleIndexedLocalsAndUnaryBuild() throws Exception {
        final Path project = project("float-double-indexed-locals");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    printInts(args.length);
                    printLongs(0L);
                    printFloats(0.25f);
                    printDoubles(0.25);
                }

                public static void printInts(final int seed) {
                    int i0 = seed;
                    int i1 = i0 + 1;
                    int i2 = i1 + 1;
                    int i3 = i2 + 1;
                    int i4 = i3 + 1;
                    System.out.println(i4);
                }

                public static void printLongs(final long seed) {
                    long l0 = seed;
                    long l1 = l0 + 1L;
                    long l2 = l1 + 1L;
                    long l3 = l2 + 1L;
                    long l4 = l3 + 6L;
                    System.out.println(l4 % 4L);
                }

                public static void printFloats(final float seed) {
                    float f0 = seed;
                    float f1 = f0 + 1.0f;
                    float f2 = f1 + 1.0f;
                    float f3 = f2 + 1.0f;
                    float f4 = f3 + 0.5f;
                    System.out.println(-f4);
                    System.out.println(f4 - f1);
                }

                public static void printDoubles(final double seed) {
                    double d0 = seed;
                    double d1 = d0 + 1.0;
                    double d2 = d1 + 1.0;
                    double d3 = d2 + 1.0;
                    double d4 = d3 + 1.0;
                    System.out.println(-d4);
                    System.out.println(d4 - d1);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/float-double-indexed-locals").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("4\n1\n-3.75\n2.5\n-4.25\n3.0\n");
    }

    @Test
    void multiImplementationInterfaceDispatchReturnsFloatAndDouble() throws Exception {
        final Path project = project("interface-float-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Metric metric = new FastMetric();
                    System.out.println(metric.ratio());
                    System.out.println(metric.total());
                }
            }
            """);
        writeJava(project, "com.acme.Metric", """
            package com.acme;

            public interface Metric {
                float ratio();

                double total();
            }
            """);
        writeJava(project, "com.acme.FastMetric", """
            package com.acme;

            public final class FastMetric implements Metric {
                public FastMetric() {
                }

                public float ratio() {
                    return 1.25f;
                }

                public double total() {
                    return 2.5;
                }
            }
            """);
        writeJava(project, "com.acme.SlowMetric", """
            package com.acme;

            public final class SlowMetric implements Metric {
                public SlowMetric() {
                }

                public float ratio() {
                    return 3.75f;
                }

                public double total() {
                    return 4.5;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-float-double").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1.25\n2.5\n");
        assertThat(Files.readString(project.resolve(".javan/reports/reachability.txt")))
            .contains("com/acme/FastMetric.ratio()F", "com/acme/FastMetric.total()D")
            .doesNotContain("com/acme/SlowMetric.ratio()F", "com/acme/SlowMetric.total()D");
        assertThat(Files.readString(project.resolve(".javan/reports/instantiated-types.json")))
            .contains("\"strategy\": \"reachable-construction-fixpoint\"", "\"type\": \"com/acme/FastMetric\"", "\"allocation\"")
            .doesNotContain("com/acme/SlowMetric");
    }

    @Test
    void instantiatedSubclassDispatchesToInheritedInterfaceMethod() throws Exception {
        final Path project = project("inherited-instantiated-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Value value = new Child();
                    System.out.println(value.value());
                }
            }
            """);
        writeJava(project, "com.acme.Value", """
            package com.acme;

            public interface Value {
                int value();
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base implements Value {
                public int value() {
                    return 7;
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base {
            }
            """);
        writeJava(project, "com.acme.Dead", """
            package com.acme;

            public final class Dead implements Value {
                public int value() {
                    return 9;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/inherited-instantiated-dispatch").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\n");
        assertThat(Files.readString(project.resolve(".javan/reports/reachability.txt")))
            .contains("com/acme/Base.value()I")
            .doesNotContain("com/acme/Dead.value()I");
        assertThat(Files.readString(project.resolve(".javan/reports/instantiated-types.json")))
            .contains("\"type\": \"com/acme/Child\"")
            .doesNotContain("com/acme/Base", "com/acme/Dead");
    }

    @Test
    void specializedCollectionCallbackUsesOnlyInstantiatedImplementations() throws Exception {
        final Path project = project("instantiated-collection-callback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("first");
                    values.add("second");
                    values.iterator().forEachRemaining(new LiveConsumer());
                }
            }
            """);
        writeJava(project, "com.acme.LiveConsumer", """
            package com.acme;

            import java.util.function.Consumer;

            public final class LiveConsumer implements Consumer<String> {
                public void accept(final String value) {
                    System.out.println(value);
                }
            }
            """);
        writeJava(project, "com.acme.DeadConsumer", """
            package com.acme;

            import java.util.function.Consumer;

            public final class DeadConsumer implements Consumer<String> {
                public void accept(final String value) {
                    System.out.println("dead:" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/instantiated-collection-callback").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("first\nsecond\n");
        assertThat(Files.readString(project.resolve(".javan/reports/reachability.txt")))
            .contains("com/acme/LiveConsumer.accept(Ljava/lang/Object;)V")
            .doesNotContain("com/acme/DeadConsumer.accept(Ljava/lang/Object;)V");
        assertThat(Files.readString(project.resolve(".javan/reports/instantiated-types.json")))
            .contains("\"type\": \"com/acme/LiveConsumer\"")
            .doesNotContain("com/acme/DeadConsumer");
    }

    @Test
    void primitiveArrayNegativeLengthThrowsAndPreservesJavaMessage() throws Exception {
        final Path project = project("primitive-negative-array-size");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(result(args.length));
                    System.out.println(result(args.length - 1));
                }

                private static String result(final int length) {
                    try {
                        final int[] values = new int[length];
                        return "ok";
                    } catch (final NegativeArraySizeException exception) {
                        return exception.getMessage();
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/primitive-negative-array-size").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ok\n-1\n");
    }

    @Test
    void objectArrayNegativeLengthThrowsAndPreservesJavaMessage() throws Exception {
        final Path project = project("object-negative-array-size");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(result(args.length));
                    System.out.println(result(args.length - 1));
                }

                private static String result(final int length) {
                    try {
                        final String[] values = new String[length];
                        return "ok";
                    } catch (final NegativeArraySizeException exception) {
                        return exception.getMessage();
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/object-negative-array-size").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("ok\n-1\n");
    }

    @Test
    void negativeArraySizeExceptionPropagatesAcrossApplicationMethods() throws Exception {
        final Path project = project("negative-array-size-propagation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        allocate(-1);
                        System.out.println("missing");
                    } catch (final NegativeArraySizeException exception) {
                        System.out.println(exception.getMessage());
                    }
                }

                private static void allocate(final int length) {
                    final byte[] values = new byte[length];
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/negative-array-size-propagation").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("-1\n");
    }

    @Test
    void uncaughtRuntimeExceptionLiteralBuildsAsNativePanic() throws Exception {
        final Path project = project("exception-panic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    throw new IllegalStateException("boom");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/exception-panic").toString()));
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stdout()).isEmpty();
        assertThat(nativeRun.stderr()).contains(
            "[JAVAN-RUNTIME-PANIC] uncaught Java exception",
            "Where:",
            "com.acme.Main.main([Ljava/lang/String;)V(Main.java:",
            "Code:",
            "throw new IllegalStateException(\"boom\");",
            "^ here",
            "Why:",
            "detail: boom",
            "Fix:"
        );
        assertThat(project.resolve(".javan/reports/exceptions.json")).exists();
        assertThat(project.resolve(".javan/reports/debug-map.json")).exists();
    }

}
