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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliEscapeClassificationIntegrationTest extends CliIntegrationSupport {
    @Test
    void reportsEscapeScopesWithoutChangingJvmBehavior() throws Exception {
        final Path project = project("escape-classification");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private static int[] saved;

                private Main() {
                }

                public static void main(final String[] args) {
                    int[] local = new int[1];
                    local[0] = 7;
                    System.out.println(local[0]);

                    int[] argument = new int[2];
                    System.out.println(length(argument));
                    saved = create();
                    System.out.println(saved.length);
                }

                private static int length(final int[] value) {
                    return value.length;
                }

                private static int[] create() {
                    return new int[3];
                }
            }
            """);
        final String jvm = runJvm(project, "com.acme.Main");

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/escape-classification");
        assertThat(process(project, List.of(binary.toString())).stdout()).isEqualTo(jvm);
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json"))).contains(
            "\"escapeAnalysis\"",
            "\"allocationSites\":",
            "\"noEscape\":",
            "\"argumentEscape\":",
            "\"globalEscape\":",
            "\"stackAllocated\":"
        );
        assertThat(generatedProgramSource(project)).contains(
            "JAVAN_ARRAY_KIND_INT",
            "= (void*) &javan_stack_array_"
        );
    }

    @Test
    void releaseStackArrayPreservesBehaviorWithoutManagedAllocation() throws Exception {
        final Path project = project("stack-array-allocation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static native long allocations();

                public static void main(final String[] args) {
                    long before = allocations();
                    int[] ints = new int[4];
                    long[] longs = new long[4];
                    float[] floats = new float[4];
                    double[] doubles = new double[4];
                    byte[] bytes = new byte[4];
                    boolean[] booleans = new boolean[4];
                    short[] shorts = new short[4];
                    char[] chars = new char[4];
                    int[] other = new int[4];
                    ints[0] = 7;
                    longs[0] = 8L;
                    floats[0] = 1.5f;
                    doubles[0] = 2.5d;
                    bytes[0] = 3;
                    booleans[0] = true;
                    shorts[0] = 4;
                    chars[0] = 'A';
                    long after = allocations();
                    System.out.println(ints[0]);
                    System.out.println(longs[0]);
                    System.out.println(floats[0]);
                    System.out.println(doubles[0]);
                    System.out.println(bytes[0]);
                    System.out.println(booleans[0]);
                    System.out.println(shorts[0]);
                    System.out.println(chars[0] + 0);
                    System.out.println(ints == other);
                    System.out.println(after - before);
                }
            }
            """);
        Files.createDirectories(project.resolve("native"));
        writeC(project, "native/allocations.c", """
            #include "javan_runtime.h"
            long long native_allocations(void) { return (long long) javan_heap_total_allocations(); }
            """);
        Files.writeString(project.resolve("javan.toml"), """
            [native]
            imports = ["com.acme.Main.allocations():long -> native_allocations"]
            sources = ["native/allocations.c"]
            """);

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/stack-array-allocation");
        assertThat(process(
            project,
            List.of(binary.toString()),
            defaultProcessTimeout(),
            Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        ).stdout()).isEqualTo("7\n8\n1.5\n2.5\n3\ntrue\n4\n65\nfalse\n0\n");
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json")))
            .contains("\"stackAllocated\": 9");
    }

    @Test
    void releaseStackArrayPreservesBoundsFailure() throws Exception {
        final Path project = project("stack-array-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int[] values = new int[1];
                    int index = 1;
                    if (args.length == 1) {
                        index = 0;
                    }
                    System.out.println(values[index]);
                }
            }
            """);

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(
            project, List.of(project.resolve(".javan/bin/stack-array-bounds").toString())
        );
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("[JAVAN-RUNTIME-PANIC]", "detail: array index out of bounds");
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json")))
            .contains("\"stackAllocated\": 1");
    }

    @Test
    void releaseStackObjectPreservesIdentityAndManagedFields() throws Exception {
        final Path project = project("stack-object-allocation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                private static native long allocations();

                public static void main(final String[] args) {
                    long before = allocations();
                    Box first = new Box(7, 8L);
                    Box second = new Box(9, 10L);
                    long after = allocations();
                    System.out.println(first == second);
                    System.out.println(first.sum() + second.sum());
                    System.out.println(after - before);

                    Child child = new Child(42);
                    long referenceBefore = allocations();
                    RefBox reference = new RefBox(child);
                    child = null;
                    long referenceAfter = allocations();
                    System.out.println(reference.value().value());
                    System.out.println(referenceAfter - referenceBefore);
                    reference.value(new Child(84));
                    System.out.println(reference.value().value());
                    reference.value(null);
                    System.out.println(reference.value() == null);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            final class Box {
                private final int first;
                private final long second;

                Box(final int first, final long second) {
                    this.first = first;
                    this.second = second;
                }

                long sum() {
                    return first + second;
                }
            }
            """);
        writeJava(project, "com.acme.RefBox", """
            package com.acme;

            final class RefBox {
                private Child value;

                RefBox(final Child value) {
                    this.value = value;
                }

                Child value() {
                    return value;
                }

                void value(final Child value) {
                    this.value = value;
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            final class Child {
                private final int value;

                Child(final int value) {
                    this.value = value;
                }

                int value() {
                    return value;
                }
            }
            """);
        Files.createDirectories(project.resolve("native"));
        writeC(project, "native/allocations.c", """
            #include "javan_runtime.h"
            long long native_allocations(void) { return (long long) javan_heap_total_allocations(); }
            """);
        Files.writeString(project.resolve("javan.toml"), """
            [native]
            imports = ["com.acme.Main.allocations():long -> native_allocations"]
            sources = ["native/allocations.c"]
            """);

        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(
            project,
            List.of(project.resolve(".javan/bin/stack-object-allocation").toString()),
            defaultProcessTimeout(),
            Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );
        assertThat(result.stdout()).isEqualTo("false\n34\n0\n42\n0\n84\ntrue\n");
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json")))
            .contains("\"stackAllocated\": 3");
        assertThat(generatedProgramSource(project)).contains(
            "javan_stack_object_",
            "(void**) &javan_stack_object_",
            ".field_value"
        );
    }

    @Test
    void releaseStackObjectPreservesCaughtConstructorException() throws Exception {
        final Path project = project("stack-object-constructor-exception");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    try {
                        new FailingBox(true);
                    } catch (IllegalArgumentException expected) {
                        System.out.println(expected.getMessage());
                    }
                }
            }
            """);
        writeJava(project, "com.acme.FailingBox", """
            package com.acme;

            final class FailingBox {
                private Object value;

                FailingBox(final boolean fail) {
                    value = new int[] {1};
                    if (fail) {
                        throw new IllegalArgumentException("boom");
                    }
                }
            }
            """);
        final CliRun build = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(
            project,
            List.of(project.resolve(".javan/bin/stack-object-constructor-exception").toString()),
            defaultProcessTimeout(),
            Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );
        assertThat(result.stdout()).isEqualTo("boom\n");
        assertThat(Files.readString(project.resolve(".javan/reports/optimizations.json")))
            .contains("\"stackAllocated\": 1");
    }
}
