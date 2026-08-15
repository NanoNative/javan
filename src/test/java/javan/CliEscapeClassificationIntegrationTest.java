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
        assertThat(Files.readString(project.resolve(".javan/generated/main.c"))).contains(
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
                    System.out.println(values[1]);
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
}
