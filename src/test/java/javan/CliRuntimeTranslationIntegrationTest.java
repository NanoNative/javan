package javan;

import javan.testing.TestSuite.NativeTest;

import javan.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
@NativeTest
final class CliRuntimeTranslationIntegrationTest extends CliIntegrationSupport {
    @Test
    void boundedFunctionReceiverProvenanceBuildsAndFallsBackConservatively() throws Exception {
        final Path project = project("bounded-function-receiver-provenance");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private static final Function<String, String> STORED = new Prefix();

                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> local = returned();
                    final Function<String, String> merged = args.length == 0 ? local : new Suffix();
                    final Function<String, String> wide;
                    if (args.length == 0) {
                        wide = new First();
                    } else if (args.length == 1) {
                        wide = new Second();
                    } else if (args.length == 2) {
                        wide = new Third();
                    } else if (args.length == 3) {
                        wide = new Fourth();
                    } else {
                        wide = new Fifth();
                    }
                    System.out.println(local.apply("local"));
                    System.out.println(merged.apply("merged"));
                    System.out.println(wide.apply("wide"));
                    System.out.println(new Unused() != null);
                }

                @SuppressWarnings("unchecked")
                private static Function<String, String> returned() {
                    return pass((Function<String, String>) (Object) STORED);
                }

                private static Function<String, String> pass(final Function<String, String> callback) {
                    return callback;
                }

                private abstract static class Named implements Function<String, String> {
                    private final String name;

                    private Named(final String name) {
                        this.name = name;
                    }

                    @Override
                    public final String apply(final String value) {
                        return name + "-" + value;
                    }
                }

                private static final class Prefix extends Named { private Prefix() { super("prefix"); } }
                private static final class Suffix extends Named { private Suffix() { super("suffix"); } }
                private static final class First extends Named { private First() { super("first"); } }
                private static final class Second extends Named { private Second() { super("second"); } }
                private static final class Third extends Named { private Third() { super("third"); } }
                private static final class Fourth extends Named { private Fourth() { super("fourth"); } }
                private static final class Fifth extends Named { private Fifth() { super("fifth"); } }
                private static final class Unused extends Named { private Unused() { super("unused"); } }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        final String nativeOutput = build.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/bounded-function-receiver-provenance").toString())).stdout()
            : build.stderr();

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        assertThat(nativeOutput).isEqualTo(jvmOutput).isEqualTo("prefix-local\nprefix-merged\nfirst-wide\ntrue\n");
        assertThat(Files.readString(project.resolve(".javan/reports/receiver-provenance.json")))
            .contains(
                "\"maxExactTypes\": 4",
                "\"unknown\": false, \"types\": [\"com/acme/Main$Prefix\"]",
                "\"unknown\": false, \"types\": [\"com/acme/Main$Prefix\", \"com/acme/Main$Suffix\"]",
                "\"unknown\": true, \"types\": []"
            );
        assertThat(Files.readString(project.resolve(".javan/reports/instantiated-types.json")))
            .contains("com/acme/Main$Unused");
    }

    @Test
    void objectsRequireNonNullIntrinsicBuildsAndChecksNull() throws Exception {
        final Path project = project("objects-require-non-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String value = Objects.requireNonNull("javan");
                    System.out.println(value);
                    System.out.println(value.length());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("javan\n5\n");

        final Path nullProject = project("objects-require-non-null-null");
        writeJava(nullProject, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    Objects.requireNonNull(value);
                    System.out.println("unreachable");
                }
            }
            """);
        final CliRun nullBuild = run(tempDir, "build", nullProject.toString());
        assertThat(nullBuild.exitCode()).isZero();

        final ProcessResult nullRun = process(
            nullProject,
            List.of(nullProject.resolve(".javan/bin/objects-require-non-null-null").toString())
        );
        assertThat(nullRun.exitCode()).isEqualTo(1);
        assertThat(nullRun.stdout()).isEmpty();
        assertThat(nullRun.stderr()).contains("null object");
    }

    @Test
    void objectsRequireNonNullMessageBuildsAndReportsMessage() throws Exception {
        final Path project = project("objects-require-non-null-message");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = null;
                    Objects.requireNonNull(value, "value");
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-message").toString()));

        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stderr()).contains("value");
    }

    @Test
    void objectsRequireNonNullElseGetInlineSupplierLambdaReturnsPrimaryValue() throws Exception {
        final Path project = project("objects-require-non-null-else-get-lambda-primary");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElseGet("value", () -> "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-lambda-primary").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void objectsRequireNonNullElseGetInlineSupplierLambdaReturnsFallbackValue() throws Exception {
        final Path project = project("objects-require-non-null-else-get-lambda-fallback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElseGet(null, () -> "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-lambda-fallback").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void objectsRequireNonNullElseGetConcreteSupplierReturnsPrimaryValue() throws Exception {
        final Path project = project("objects-require-non-null-else-get-concrete-primary");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElseGet("value", new FallbackSupplier()));
                }
            }
            """);
        writeJava(project, "com.acme.FallbackSupplier", """
            package com.acme;

            import java.util.function.Supplier;

            public final class FallbackSupplier implements Supplier<Object> {
                @Override
                public Object get() {
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-concrete-primary").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void objectsRequireNonNullElseGetConcreteSupplierReturnsFallbackValue() throws Exception {
        final Path project = project("objects-require-non-null-else-get-concrete-fallback");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Objects.requireNonNullElseGet(null, new FallbackSupplier()));
                }
            }
            """);
        writeJava(project, "com.acme.FallbackSupplier", """
            package com.acme;

            import java.util.function.Supplier;

            public final class FallbackSupplier implements Supplier<Object> {
                @Override
                public Object get() {
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-concrete-fallback").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("fallback\n");
    }

    @Test
    void systemArraycopyIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("system-arraycopy-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int[] values = new int[] {1, 2, 3, 4};
                    System.arraycopy(values, 1, values, 2, 2);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                    System.out.println(values[2]);
                    System.out.println(values[3]);

                    final byte[] bytes = new byte[] {7, 8, 9};
                    final byte[] targetBytes = new byte[4];
                    System.arraycopy(bytes, 0, targetBytes, 1, 3);
                    System.out.println(targetBytes[0]);
                    System.out.println(targetBytes[1]);
                    System.out.println(targetBytes[3]);

                    final String[] names = new String[] {"a", "b", null};
                    final String[] targetNames = new String[3];
                    System.arraycopy(names, 0, targetNames, 0, 3);
                    System.out.println(targetNames[0]);
                    System.out.println(targetNames[1]);
                    System.out.println(targetNames.length);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/system-arraycopy-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains("{\"name\": \"System.arraycopy\", \"count\": 3}");
    }

    @Test
    void arraysCopyOfIntrinsicBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-intrinsic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final boolean[] booleans = Arrays.copyOf(new boolean[] {true}, 2);
                    System.out.println(booleans.length);
                    System.out.println(booleans[0]);
                    System.out.println(booleans[1]);

                    final int[] ints = Arrays.copyOf(new int[] {4, 5}, 4);
                    System.out.println(ints.length);
                    System.out.println(ints[0]);
                    System.out.println(ints[2]);

                    final long[] longs = Arrays.copyOf(new long[] {8L, 9L}, 1);
                    System.out.println(longs.length);
                    System.out.println(longs[0]);

                    final byte[] bytes = Arrays.copyOf(new byte[] {1, 2}, 3);
                    System.out.println(bytes[2]);

                    final short[] shorts = Arrays.copyOf(new short[] {3, 4}, 1);
                    System.out.println(shorts[0]);

                    final char[] chars = Arrays.copyOf(new char[] {'j'}, 2);
                    System.out.println((int) chars[0]);
                    System.out.println((int) chars[1]);

                    final float[] floats = Arrays.copyOf(new float[] {1.5f}, 2);
                    System.out.println(floats[0]);
                    System.out.println(floats[1]);

                    final double[] doubles = Arrays.copyOf(new double[] {2.25d}, 2);
                    System.out.println(doubles[0]);
                    System.out.println(doubles[1]);

                    final Object[] objects = Arrays.copyOf(new Object[] {"x", "y"}, 3);
                    System.out.println(objects.length);
                    System.out.println(objects[0]);
                    System.out.println(objects[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-intrinsic").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(Files.readString(project.resolve(".javan/reports/intrinsics.json")))
            .contains("{\"name\": \"Arrays.copyOf\", \"count\": 9}");
    }

    @Test
    void arraysCopyOfRangeByteBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-range-byte");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final byte[] values = Arrays.copyOfRange(new byte[] {4, 5}, 1, 4);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                    System.out.println(values[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-range-byte").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n5\n0\n0\n");
    }

    @Test
    void arraysCopyOfRangeObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arrays-copy-of-range-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Arrays;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = Arrays.copyOfRange(new String[] {"a", "b", "c"}, 1, 3);
                    System.out.println(values.length);
                    System.out.println(values[0]);
                    System.out.println(values[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arrays-copy-of-range-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\nb\nc\n");
    }

    @Test
    void arrayListAddAndGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-add-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    System.out.println(values.get(0));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-add-get").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\n");
    }

    @Test
    void arrayListInitialCapacityBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-initial-capacity");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(4);
                    values.add("capacity");
                    System.out.println(values.get(0));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-initial-capacity").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("capacity\n");
    }

    @Test
    void listContainsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("Synthetic");
                    System.out.println(values.contains("Synthetic"));
                    System.out.println(values.contains("Deprecated"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-contains").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void listAddAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-add-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    System.out.println(values.addAll(List.of("left", "right")));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-add-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void listAddAllAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-add-all-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "right"));
                    System.out.println(values.addAll(1, List.of("middle-a", "middle-b")));
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                    System.out.println(values.get(2));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-add-all-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n4\nmiddle-a\nmiddle-b\n");
    }

    @Test
    void abstractListDirectOwnerIndexedSurfaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-indexed-surface");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left", "right"));
                    values.add(1, "middle-0");
                    System.out.println(values.addAll(2, List.of("middle-1", "middle-2")));
                    System.out.println(values.get(2));
                    System.out.println(values.set(2, "updated"));
                    System.out.println(values.remove(1));
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-indexed-surface").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nmiddle-1\nmiddle-1\nmiddle-0\n4\nupdated\n");
    }

    @Test
    void abstractListDirectOwnerAddBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left"));
                    System.out.println(values.add("right"));
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-add").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nright\n");
    }

    @Test
    void abstractListDirectOwnerIndexOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-index-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.indexOf("middle"));
                    System.out.println(values.indexOf("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-index-of").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n-1\n");
    }

    @Test
    void abstractListDirectOwnerLastIndexOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-last-index-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.lastIndexOf("middle"));
                    System.out.println(values.lastIndexOf("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-last-index-of").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n-1\n");
    }

    @Test
    void abstractListDirectOwnerClearBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-clear");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left", "right"));
                    values.clear();
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-clear").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n");
    }

    @Test
    void abstractListDirectOwnerIteratorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-iterator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left", "right"));
                    final Iterator<String> iterator = values.iterator();
                    while (iterator.hasNext()) {
                        System.out.println(iterator.next());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-iterator").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void collectionAddAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-add-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new ArrayList<>();
                    System.out.println(values.addAll(List.of("left", "right")));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-add-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\n");
    }

    @Test
    void collectionAddBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new ArrayList<>();
                    System.out.println(values.add("left"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-add").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n1\n");
    }

    @Test
    void collectionRemoveAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-remove-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.Collections;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> mutable = new ArrayList<>();
                    mutable.add("left");
                    mutable.add("right");
                    final Collection<String> values = mutable;
                    final Collection<String> probe = Collections.unmodifiableCollection(mutable);
                    System.out.println(values.removeAll(probe));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-remove-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\n");
    }

    @Test
    void collectionRetainAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-retain-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.Collections;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> mutable = new ArrayList<>();
                    mutable.add("left");
                    mutable.add("right");
                    final Collection<String> values = mutable;
                    final Collection<String> probe = Collections.unmodifiableCollection(mutable);
                    System.out.println(values.retainAll(probe));
                    System.out.println(values.size());
                    System.out.println(values.contains("left"));
                    System.out.println(values.contains("right"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-retain-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n2\ntrue\ntrue\n");
    }

    @Test
    void listAddFirstBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-add-first");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("right");
                    values.addFirst("left");
                    System.out.println(values.getFirst());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-add-first").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\n");
    }

    @Test
    void listRemoveAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-remove-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("a", "b", "c", "b"));
                    System.out.println(values.removeAll(List.of("b", "x")));
                    System.out.println(values.size());
                    System.out.println(values.get(0));
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-remove-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\na\nc\n");
    }

    @Test
    void listRetainAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-retain-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("a", "b", "c", "b"));
                    System.out.println(values.retainAll(List.of("b", "x")));
                    System.out.println(values.size());
                    System.out.println(values.get(0));
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-retain-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nb\nb\n");
    }

    @Test
    void listSetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("old");
                    System.out.println(values.set(0, "new"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-set").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("old\n");
    }

    @Test
    void listRemoveLastBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-remove-last");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("right");
                    System.out.println(values.removeLast());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-remove-last").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("right\n");
    }

    @Test
    void listGetLastBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-get-last");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("right");
                    System.out.println(values.getLast());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-get-last").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("right\n");
    }

    @Test
    void listIsEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-is-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    System.out.println(values.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-is-empty").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void hashMapStringGetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-string-get");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-string-get").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapContainsKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-contains-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.containsKey("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-contains-key").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapGetOrDefaultMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-get-or-default");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.getOrDefault("missing", "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-get-or-default").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapContainsValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-contains-value");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    values.put("nullable", null);
                    System.out.println(values.containsValue("right"));
                    System.out.println(values.containsValue("missing"));
                    System.out.println(values.containsValue(null));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-contains-value").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapReplaceExistingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-replace-existing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.replace("left", "changed"));
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-replace-existing").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapReplaceMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-replace-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.replace("left", "changed"));
                    System.out.println(values.containsKey("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-replace-missing").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapReplaceKeyValueMatchingBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-replace-key-value-matching");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.replace("left", "right", "changed"));
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-replace-key-value-matching").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapReplaceKeyValueMismatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-replace-key-value-mismatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.replace("left", "wrong", "changed"));
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-replace-key-value-mismatch").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapClearBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-clear");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    values.put("other", "value");
                    values.clear();
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-clear").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n");
    }

    @Test
    void hashMapSizeBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-size");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-size").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void hashMapIsEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-is-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-is-empty").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n");
    }

    @Test
    void linkedHashMapPutIfAbsentMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-put-if-absent-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    values.putIfAbsent("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-put-if-absent-missing").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapPutIfAbsentExistingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-put-if-absent-existing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    values.put("left", "right");
                    values.putIfAbsent("left", "changed");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-put-if-absent-existing").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapCapacityConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-capacity-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>(16);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-capacity-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapCapacityConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-capacity-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>(8);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-capacity-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void concurrentHashMapCapacityConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("concurrenthashmap-capacity-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ConcurrentHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>(4);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/concurrenthashmap-capacity-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapLoadFactorConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-load-factor-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>(16, 0.5f);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-load-factor-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapLoadFactorConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-load-factor-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>(8, 0.5f);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-load-factor-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void concurrentHashMapLoadFactorConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("concurrenthashmap-load-factor-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ConcurrentHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>(4, 0.5f);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/concurrenthashmap-load-factor-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void concurrentHashMapConcurrencyLevelConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("concurrenthashmap-concurrency-level-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.concurrent.ConcurrentHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ConcurrentHashMap<String, String> values = new ConcurrentHashMap<>(4, 0.75f, 2);
                    values.put("left", "right");
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/concurrenthashmap-concurrency-level-constructor").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void hashMapNewHashMapStaticFactoryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-static-factory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, Integer> values = HashMap.newHashMap(3);
                    values.put("a", 1);
                    values.put("b", 2);
                    System.out.println(values.size());
                    System.out.println(values.get("a"));
                    System.out.println(values.containsKey("b"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-static-factory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\n1\ntrue\n");
    }

    @Test
    void linkedHashMapNewLinkedHashMapStaticFactoryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-static-factory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = LinkedHashMap.newLinkedHashMap(3);
                    values.put("third", "c");
                    values.put("first", "a");
                    values.put("second", "b");
                    final Object[] keys = values.keySet().toArray();
                    System.out.println(values.size());
                    System.out.println(keys[0]);
                    System.out.println(keys[1]);
                    System.out.println(keys[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-static-factory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\nthird\nfirst\nsecond\n");
    }

    @Test
    void hashSetNewHashSetStaticFactoryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-static-factory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = HashSet.newHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    System.out.println(values.contains("alpha"));
                    System.out.println(values.contains("beta"));
                    System.out.println(values.contains("gamma"));
                    System.out.println(values.contains("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-static-factory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\ntrue\ntrue\nfalse\n");
    }

    @Test
    void linkedHashSetNewLinkedHashSetStaticFactoryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-static-factory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = LinkedHashSet.newLinkedHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    final Object[] elements = values.toArray();
                    System.out.println(elements[0]);
                    System.out.println(elements[1]);
                    System.out.println(elements[2]);
                    System.out.println(values.contains("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-static-factory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("alpha\nbeta\ngamma\nfalse\n");
    }

    @Test
    void arrayListDirectOwnerRemoveAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("a", "b", "c", "b"));
                    final List<String> view = values;
                    System.out.println(values.removeAll(List.of("b", "x")));
                    System.out.println(view.size());
                    System.out.println(view.get(0));
                    System.out.println(view.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\na\nc\n");
    }

    @Test
    void arrayListDirectOwnerRetainAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-retain-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("a", "b", "c", "b"));
                    final List<String> view = values;
                    System.out.println(values.retainAll(List.of("b", "x")));
                    System.out.println(view.size());
                    System.out.println(view.get(0));
                    System.out.println(view.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-retain-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nb\nb\n");
    }

    @Test
    void hashSetCapacityConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-capacity-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = new HashSet<>(16);
                    values.add("alpha");
                    values.add("beta");
                    System.out.println(values.contains("alpha"));
                    System.out.println(values.contains("beta"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-capacity-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\ntrue\n2\n");
    }

    @Test
    void linkedHashSetCapacityConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-capacity-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = new LinkedHashSet<>(8);
                    values.add("alpha");
                    values.add("beta");
                    final Object[] elements = values.toArray();
                    System.out.println(elements[0]);
                    System.out.println(elements[1]);
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-capacity-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("alpha\nbeta\n2\n");
    }

    @Test
    void hashSetLoadFactorConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-load-factor-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = new HashSet<>(16, 0.75f);
                    values.add("alpha");
                    values.add("beta");
                    System.out.println(values.contains("alpha"));
                    System.out.println(values.contains("beta"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-load-factor-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\ntrue\n2\n");
    }

    @Test
    void linkedHashSetLoadFactorConstructorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-load-factor-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = new LinkedHashSet<>(8, 0.75f);
                    values.add("alpha");
                    values.add("beta");
                    final Object[] elements = values.toArray();
                    System.out.println(elements[0]);
                    System.out.println(elements[1]);
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-load-factor-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("alpha\nbeta\n2\n");
    }

    @Test
    void arrayListDirectOwnerReadSurfaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-read-surface");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("alpha", "beta", "gamma"));
                    System.out.println(values.size());
                    System.out.println(values.isEmpty());
                    System.out.println(values.contains("beta"));
                    System.out.println(values.get(1));
                    final Iterator<String> iterator = values.iterator();
                    System.out.println(iterator.next());
                    System.out.println(iterator.next());
                    System.out.println(iterator.next());
                    final Object[] array = values.toArray();
                    System.out.println(array[0]);
                    System.out.println(array[1]);
                    System.out.println(array[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-read-surface").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\nfalse\ntrue\nbeta\nalpha\nbeta\ngamma\nalpha\nbeta\ngamma\n");
    }

    @Test
    void arrayListDirectOwnerGetFirstBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-get-first");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("alpha", "beta", "gamma"));
                    System.out.println(values.getFirst());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-get-first").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("alpha\n");
    }

    @Test
    void arrayListDirectOwnerGetLastBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-get-last");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("alpha", "beta", "gamma"));
                    System.out.println(values.getLast());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-get-last").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("gamma\n");
    }

    @Test
    void arrayListDirectOwnerSetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("old", "right"));
                    System.out.println(values.set(0, "left"));
                    System.out.println(values.getFirst());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-set").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("old\nleft\n");
    }

    @Test
    void arrayListDirectOwnerRemoveLastBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-last");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "right"));
                    System.out.println(values.removeLast());
                    System.out.println(values.getLast());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-last").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("right\nleft\n");
    }

    @Test
    void arrayListDirectOwnerAddFirstBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-add-first");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("right"));
                    values.addFirst("left");
                    System.out.println(values.getFirst());
                    System.out.println(values.getLast());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-add-first").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void arrayListDirectOwnerAddLastBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-add-last");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left"));
                    values.addLast("right");
                    System.out.println(values.getFirst());
                    System.out.println(values.getLast());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-add-last").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void arrayListDirectOwnerRemoveFirstBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-first");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "right"));
                    System.out.println(values.removeFirst());
                    System.out.println(values.getFirst());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-first").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void listRemoveAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-remove-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right"));
                    System.out.println(values.remove(1));
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-remove-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("middle\nright\n");
    }

    @Test
    void arrayListDirectOwnerRemoveAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "middle", "right"));
                    System.out.println(values.remove(1));
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("middle\nright\n");
    }

    @Test
    void listRemoveObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-remove-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.remove("middle"));
                    System.out.println(values.remove("missing"));
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-remove-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n3\nright\n");
    }

    @Test
    void arrayListDirectOwnerRemoveObjectBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-remove-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.remove("middle"));
                    System.out.println(values.remove("missing"));
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-remove-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n3\nright\n");
    }

    @Test
    void arrayListDirectOwnerAddAllAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-add-all-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "right"));
                    System.out.println(values.addAll(1, List.of("middle-a", "middle-b")));
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                    System.out.println(values.get(2));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-add-all-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n4\nmiddle-a\nmiddle-b\n");
    }

    @Test
    void listIndexOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-index-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.indexOf("middle"));
                    System.out.println(values.indexOf("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-index-of").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n-1\n");
    }

    @Test
    void listLastIndexOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-last-index-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.lastIndexOf("middle"));
                    System.out.println(values.lastIndexOf("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-last-index-of").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n-1\n");
    }

    @Test
    void arrayListDirectOwnerIndexOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-index-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.indexOf("middle"));
                    System.out.println(values.indexOf("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-index-of").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n-1\n");
    }

    @Test
    void arrayListDirectOwnerLastIndexOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-direct-owner-last-index-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>(List.of("left", "middle", "right", "middle"));
                    System.out.println(values.lastIndexOf("middle"));
                    System.out.println(values.lastIndexOf("missing"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-direct-owner-last-index-of").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\n-1\n");
    }

    @Test
    void hashSetDirectOwnerReadSurfaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-direct-owner-read-surface");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.Iterator;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = HashSet.newHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    System.out.println(values.size());
                    System.out.println(values.isEmpty());
                    System.out.println(values.contains("alpha"));
                    System.out.println(values.containsAll(List.of("alpha", "beta")));
                    final Iterator<String> iterator = values.iterator();
                    int seen = 0;
                    int alpha = 0;
                    int beta = 0;
                    int gamma = 0;
                    while (iterator.hasNext()) {
                        final String value = iterator.next();
                        seen++;
                        if ("alpha".equals(value)) {
                            alpha++;
                        } else if ("beta".equals(value)) {
                            beta++;
                        } else if ("gamma".equals(value)) {
                            gamma++;
                        }
                    }
                    final Object[] array = values.toArray();
                    System.out.println(seen);
                    System.out.println(alpha);
                    System.out.println(beta);
                    System.out.println(gamma);
                    System.out.println(array.length);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-direct-owner-read-surface").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\nfalse\ntrue\ntrue\n3\n1\n1\n1\n3\n");
    }

    @Test
    void linkedHashSetDirectOwnerReadSurfaceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-direct-owner-read-surface");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Iterator;
            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = LinkedHashSet.newLinkedHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    System.out.println(values.size());
                    System.out.println(values.isEmpty());
                    System.out.println(values.contains("beta"));
                    System.out.println(values.containsAll(List.of("alpha", "gamma")));
                    final Iterator<String> iterator = values.iterator();
                    System.out.println(iterator.next());
                    System.out.println(iterator.next());
                    System.out.println(iterator.next());
                    final Object[] array = values.toArray();
                    System.out.println(array[0]);
                    System.out.println(array[1]);
                    System.out.println(array[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-direct-owner-read-surface").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\nfalse\ntrue\ntrue\nalpha\nbeta\ngamma\nalpha\nbeta\ngamma\n");
    }

    @Test
    void setAddAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-add-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new LinkedHashSet<>(List.of("a", "b"));
                    System.out.println(values.addAll(List.of("b", "c")));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-add-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n3\n");
    }

    @Test
    void setRemoveAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-remove-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new LinkedHashSet<>(List.of("a", "b", "c"));
                    System.out.println(values.removeAll(List.of("a", "x")));
                    System.out.println(values.size());
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-remove-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nb\nc\n");
    }

    @Test
    void setRetainAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-retain-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = new LinkedHashSet<>(List.of("a", "b", "c"));
                    System.out.println(values.retainAll(List.of("c", "a")));
                    System.out.println(values.size());
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-retain-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\na\nc\n");
    }

    @Test
    void hashSetDirectOwnerAddAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-direct-owner-add-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = new HashSet<>(List.of("a", "b"));
                    System.out.println(values.addAll(List.of("b", "c")));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-direct-owner-add-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n3\n");
    }

    @Test
    void hashSetDirectOwnerRemoveAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-direct-owner-remove-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = new HashSet<>(List.of("alpha", "beta", "gamma"));
                    System.out.println(values.removeAll(List.of("beta", "delta")));
                    System.out.println(values.contains("beta"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-direct-owner-remove-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void hashSetDirectOwnerRetainAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-direct-owner-retain-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = new HashSet<>(List.of("alpha", "beta", "gamma"));
                    System.out.println(values.retainAll(List.of("gamma", "delta")));
                    System.out.println(values.contains("alpha"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-direct-owner-retain-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n1\n");
    }

    @Test
    void linkedHashSetDirectOwnerAddAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-direct-owner-add-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = new LinkedHashSet<>(List.of("b", "a"));
                    System.out.println(values.addAll(List.of("a", "c")));
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                    System.out.println(snapshot[2]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-direct-owner-add-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nb\na\nc\n");
    }

    @Test
    void linkedHashSetDirectOwnerRemoveAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-direct-owner-remove-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = new LinkedHashSet<>(List.of("b", "a", "c"));
                    System.out.println(values.removeAll(List.of("a", "x")));
                    System.out.println(values.size());
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-direct-owner-remove-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nb\nc\n");
    }

    @Test
    void linkedHashSetDirectOwnerRetainAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-direct-owner-retain-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = new LinkedHashSet<>(List.of("b", "a", "c"));
                    System.out.println(values.retainAll(List.of("c", "b")));
                    System.out.println(values.size());
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-direct-owner-retain-all").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nb\nc\n");
    }

    @Test
    void collectionRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.HashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> base = HashSet.newHashSet(3);
                    base.add("alpha");
                    base.add("beta");
                    base.add("gamma");
                    final Collection<String> values = base;
                    System.out.println(values.remove("beta"));
                    System.out.println(values.contains("beta"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-remove").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void collectionRetainAllWithSetReceiverBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-retain-all-set-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.LinkedHashSet;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new LinkedHashSet<>(List.of("b", "a", "c"));
                    System.out.println(values.retainAll(List.of("c", "b", "x")));
                    System.out.println(values.size());
                    final Object[] snapshot = values.toArray();
                    System.out.println(snapshot[0]);
                    System.out.println(snapshot[1]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-retain-all-set-receiver").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n2\nb\nc\n");
    }

    @Test
    void collectionClearBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-clear");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collection;
            import java.util.HashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> base = HashSet.newHashSet(3);
                    base.add("alpha");
                    base.add("beta");
                    base.add("gamma");
                    final Collection<String> values = base;
                    values.clear();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-clear").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\n");
    }

    @Test
    void setRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = HashSet.newHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    System.out.println(values.remove("alpha"));
                    System.out.println(values.contains("alpha"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-remove").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void setClearBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("set-clear");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = HashSet.newHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    values.clear();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/set-clear").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\n");
    }

    @Test
    void hashSetDirectOwnerRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-direct-owner-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = HashSet.newHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    System.out.println(values.remove("gamma"));
                    System.out.println(values.contains("gamma"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-direct-owner-remove").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void hashSetDirectOwnerClearBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashset-direct-owner-clear");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashSet<String> values = HashSet.newHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    values.clear();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashset-direct-owner-clear").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\n");
    }

    @Test
    void linkedHashSetDirectOwnerRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-direct-owner-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = LinkedHashSet.newLinkedHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    System.out.println(values.remove("beta"));
                    System.out.println(values.contains("beta"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-direct-owner-remove").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void linkedHashSetDirectOwnerClearBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashset-direct-owner-clear");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashSet;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> values = LinkedHashSet.newLinkedHashSet(3);
                    values.add("alpha");
                    values.add("beta");
                    values.add("gamma");
                    values.clear();
                    System.out.println(values.isEmpty());
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashset-direct-owner-clear").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n0\n");
    }

    @Test
    void linkedHashMapValuesBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-values");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("a", "one");
                    values.put("b", "two");
                    for (final String value : values.values()) {
                        System.out.println(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-values").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void linkedHashMapPutAllBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-put-all");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("first", "keep");
                    final Map<String, String> incoming = new LinkedHashMap<>();
                    incoming.put("second", "two");
                    incoming.put("first", "override");
                    values.putAll(incoming);
                    System.out.println(values.get("first"));
                    System.out.println(values.get("second"));
                    for (final String key : values.keySet()) {
                        System.out.println(key);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-put-all").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void mapRemoveExistingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-remove-existing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("first", "a");
                    values.put("second", "b");
                    System.out.println(values.remove("first"));
                    System.out.println(values.size());
                    System.out.println(values.containsKey("first"));
                    System.out.println(values.keySet().toArray()[0]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-remove-existing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("a\n1\nfalse\nsecond\n");
    }

    @Test
    void mapRemoveMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-remove-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.remove("missing"));
                    System.out.println(values.size());
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-remove-missing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("null\n1\nright\n");
    }

    @Test
    void mapRemoveKeyValueMatchingEntryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-remove-key-value-match");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("first", "a");
                    values.put("second", "b");
                    System.out.println(values.remove("first", "a"));
                    System.out.println(values.size());
                    System.out.println(values.containsKey("first"));
                    System.out.println(values.keySet().toArray()[0]);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-remove-key-value-match").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\n1\nfalse\nsecond\n");
    }

    @Test
    void mapRemoveKeyValueMismatchedValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-remove-key-value-mismatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("left", "right");
                    System.out.println(values.remove("left", "wrong"));
                    System.out.println(values.size());
                    System.out.println(values.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-remove-key-value-mismatch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n1\nright\n");
    }

    @Test
    void mapRemoveKeyValuePresentNullBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-remove-key-value-present-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("nullable", null);
                    System.out.println(values.remove("nullable", null));
                    System.out.println(values.containsKey("nullable"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-remove-key-value-present-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n0\n");
    }

    @Test
    void mapRemoveKeyValueMissingNullBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-remove-key-value-missing-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.remove("missing", null));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-remove-key-value-missing-null").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("false\n0\n");
    }

    @Test
    void linkedHashMapDirectKeySetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-direct-key-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    values.put("left", "1");
                    values.put("right", "2");
                    System.out.println(values.keySet().contains("left"));
                    for (final String key : values.keySet()) {
                        System.out.println(key);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-direct-key-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nleft\nright\n");
    }

    @Test
    void linkedHashMapDirectEntrySetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-direct-entry-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    values.put("left", "1");
                    values.put("right", "2");
                    for (final Map.Entry<String, String> entry : values.entrySet()) {
                        System.out.println(entry.getKey() + "=" + entry.getValue());
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-direct-entry-set").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left=1\nright=2\n");
    }

    @Test
    void mapCopyOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-copy-of");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> mutable = new HashMap<>();
                    mutable.put("left", "right");
                    final Map<String, String> snapshot = Map.copyOf(mutable);
                    mutable.put("left", "changed");
                    System.out.println(snapshot.get("left"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-copy-of").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void arrayListIndexedAddBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("arraylist-indexed-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("right");
                    values.add(1, "middle");
                    System.out.println(values.get(0));
                    System.out.println(values.get(1));
                    System.out.println(values.get(2));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-indexed-add").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nmiddle\nright\n");
    }

    @Test
    void listCopyOfSnapshotsSourceList() throws Exception {
        final Path project = project("list-copy-of-snapshot");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    final List<String> snapshot = List.copyOf(values);
                    values.add("right");
                    System.out.println(snapshot.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-copy-of-snapshot").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1\n");
    }

    @Test
    void arrayListCollectionConstructorCopiesSourceList() throws Exception {
        final Path project = project("arraylist-copy-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> copy = new ArrayList<>(List.of("left"));
                    System.out.println(copy.getFirst());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/arraylist-copy-constructor").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\n");
    }

    @Test
    void listOfNineArgumentsBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-of-nine-arguments");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = List.of("a", "b", "c", "d", "e", "f", "g", "h", "i");
                    System.out.println(values.get(8));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-of-nine-arguments").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("i\n");
    }

    @Test
    void listIteratorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-iterator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>();
                    values.add("left");
                    values.add("right");
                    for (final String value : values) {
                        System.out.println(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-iterator").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void abstractListDirectOwnerListIteratorBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("abstractlist-direct-owner-list-iterator");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.AbstractList;
            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final AbstractList<String> values = new ArrayList<>(List.of("left", "right"));
                    final ListIterator<String> iterator = values.listIterator();
                    System.out.println(iterator.next());
                    System.out.println(iterator.next());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/abstractlist-direct-owner-list-iterator").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void listListIteratorAtBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("list-list-iterator-at");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right"));
                    final ListIterator<String> iterator = values.listIterator(1);
                    System.out.println(iterator.next());
                    System.out.println(iterator.next());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/list-list-iterator-at").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("middle\nright\n");
    }

    @Test
    void listIteratorPreviousBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("listiterator-previous");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right"));
                    final ListIterator<String> iterator = values.listIterator(values.size());
                    System.out.println(iterator.previous());
                    System.out.println(iterator.previous());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/listiterator-previous").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("right\nmiddle\n");
    }

    @Test
    void listIteratorIndexesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("listiterator-indexes");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "right"));
                    final ListIterator<String> iterator = values.listIterator(1);
                    System.out.println(iterator.previousIndex());
                    System.out.println(iterator.nextIndex());
                    System.out.println(iterator.previous());
                    System.out.println(iterator.previousIndex());
                    System.out.println(iterator.nextIndex());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/listiterator-indexes").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("0\n1\nleft\n-1\n0\n");
    }

    @Test
    void listIteratorSetBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("listiterator-set");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "right"));
                    final ListIterator<String> iterator = values.listIterator();
                    System.out.println(iterator.next());
                    iterator.set("LEFT");
                    System.out.println(values.get(0));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/listiterator-set").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nLEFT\n");
    }

    @Test
    void listIteratorAddBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("listiterator-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "right"));
                    final ListIterator<String> iterator = values.listIterator(1);
                    iterator.add("middle");
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                    System.out.println(iterator.next());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/listiterator-add").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("3\nmiddle\nright\n");
    }

    @Test
    void listIteratorRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("listiterator-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.ListIterator;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right"));
                    final ListIterator<String> iterator = values.listIterator(1);
                    System.out.println(iterator.next());
                    iterator.remove();
                    System.out.println(values.size());
                    System.out.println(values.get(1));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/listiterator-remove").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("middle\n2\nright\n");
    }

    @Test
    void iteratorRemoveBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("iterator-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "middle", "right"));
                    final Iterator<String> iterator = values.iterator();
                    System.out.println(iterator.next());
                    iterator.remove();
                    System.out.println(values.size());
                    System.out.println(values.get(0));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/iterator-remove").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\n2\nmiddle\n");
    }

    @Test
    void iteratorForEachRemainingLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("iterator-foreach-remaining-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = new ArrayList<>(List.of("left", "right"));
                    final Iterator<String> iterator = values.iterator();
                    final String prefix = "item:";
                    iterator.forEachRemaining(value -> System.out.println(prefix + value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/iterator-foreach-remaining-lambda").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("item:left\nitem:right\n");
    }

    @Test
    void iteratorForEachRemainingConcreteConsumerBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("iterator-foreach-remaining-consumer");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Iterator;
            import java.util.List;
            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Iterator<String> iterator = new ArrayList<>(List.of("left", "right")).iterator();
                    iterator.forEachRemaining(new Printer());
                }

                private static final class Printer implements Consumer<String> {
                    @Override
                    public void accept(final String value) {
                        System.out.println(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/iterator-foreach-remaining-consumer").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void iterableForEachLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("iterable-foreach-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Iterable<String> values = new ArrayList<>(List.of("left", "right"));
                    final String prefix = "item:";
                    values.forEach(value -> System.out.println(prefix + value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/iterable-foreach-lambda").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("item:left\nitem:right\n");
    }

    @Test
    void iterableForEachConcreteConsumerBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("iterable-foreach-consumer");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.List;
            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Iterable<String> values = new ArrayList<>(List.of("left", "right"));
                    values.forEach(new Printer());
                }

                private static final class Printer implements Consumer<String> {
                    @Override
                    public void accept(final String value) {
                        System.out.println(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/iterable-foreach-consumer").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left\nright\n");
    }

    @Test
    void collectionRemoveIfLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-remove-if-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new ArrayList<>(List.of("keep", "drop", "stay"));
                    final boolean changed = values.removeIf(value -> value.equals("drop"));
                    System.out.println(changed);
                    System.out.println(values.contains("drop"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-remove-if-lambda").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void collectionRemoveIfConcretePredicateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-remove-if-predicate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;
            import java.util.List;
            import java.util.function.Predicate;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Collection<String> values = new ArrayList<>(List.of("keep", "drop", "stay"));
                    final boolean changed = values.removeIf(new DropPredicate());
                    System.out.println(changed);
                    System.out.println(values.contains("drop"));
                    System.out.println(values.size());
                }

                private static final class DropPredicate implements Predicate<String> {
                    @Override
                    public boolean test(final String value) {
                        return value.equals("drop");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-remove-if-predicate").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    void predicateTestDirectConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("predicate-test-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Predicate;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Predicate<String> predicate = new KeepPredicate();
                    System.out.println(predicate.test("keep"));
                    System.out.println(predicate.test("drop"));
                }

                private static final class KeepPredicate implements Predicate<String> {
                    @Override
                    public boolean test(final String value) {
                        return value.equals("keep");
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/predicate-test-concrete").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\nfalse\n");
    }

    @Test
    void biFunctionApplyConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bifunction-apply-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.BiFunction;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final BiFunction<Object, Object, Object> function = new Joiner();
                    System.out.println(function.apply("left", "right"));
                }
            }
            """);
        writeJava(project, "com.acme.Joiner", """
            package com.acme;

            import java.util.function.BiFunction;

            public final class Joiner implements BiFunction<Object, Object, Object> {
                @Override
                public Object apply(final Object left, final Object right) {
                    return ((String) left) + ":" + ((String) right);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/bifunction-apply-concrete").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left:right\n");
    }

    @Test
    void biFunctionApplyZeroCaptureLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bifunction-apply-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.BiFunction;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final BiFunction<String, String, String> function = (left, right) -> left + "-" + right;
                    System.out.println(function.apply("left", "right"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/bifunction-apply-lambda").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("left-right\n");
    }

    @Test
    void functionApplyConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("function-apply-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<Object, Object> function = new Loader();
                    System.out.println(function.apply("value"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.function.Function;

            public final class Loader implements Function<Object, Object> {
                @Override
                public Object apply(final Object value) {
                    return value + "-native";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/function-apply-concrete").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value-native\n");
    }

    @Test
    void functionApplyZeroCaptureLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("function-apply-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> function = value -> value + "-lambda";
                    System.out.println(function.apply("value"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/function-apply-lambda").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value-lambda\n");
    }

    @Test
    void functionStoredInFieldBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("function-stored-in-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Reader reader = new Reader(value -> value + "-stored");
                    System.out.println(reader.read("value"));
                }

                private static final class Reader {
                    private final Function<String, String> function;

                    private Reader(final Function<String, String> function) {
                        this.function = function;
                    }

                    private String read(final String value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/function-stored-in-field").toString())
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void capturedFunctionStoredInFieldSurvivesGcStress() throws Exception {
        final Path project = project("captured-function-stored-in-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String prefix = new String("captured-");
                    final Reader reader = new Reader(value -> prefix + value);
                    System.out.println(reader.read("value"));
                }

                private static final class Reader {
                    private final Function<String, String> function;

                    private Reader(final Function<String, String> function) {
                        this.function = function;
                    }

                    private String read(final String value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/captured-function-stored-in-field").toString()),
            defaultProcessTimeout(),
            java.util.Map.of("JAVAN_GC_STRESS", "1")
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void nestedCapturedFunctionStoredInFieldSurvivesGcStress() throws Exception {
        final Path project = project("nested-captured-function-stored-in-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String prefix = new String("nested-");
                    final Function<String, String> inner = value -> prefix + value;
                    final Function<String, String> outer = value -> inner.apply(value) + "-done";
                    System.out.println(new Reader(outer).read("value"));
                }

                private static final class Reader {
                    private final Function<String, String> function;

                    private Reader(final Function<String, String> function) {
                        this.function = function;
                    }

                    private String read(final String value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/nested-captured-function-stored-in-field").toString()),
                defaultProcessTimeout(),
                java.util.Map.of("JAVAN_GC_STRESS", "1")
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void deeplyForwardedFunctionBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("deeply-forwarded-function");
        final StringBuilder source = new StringBuilder("""
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(step0().apply("value"));
                }

            """);
        for (int index = 0; index < 513; index++) {
            source.append("    private static Function<String, String> step")
                .append(index)
                .append("() {\n        return step")
                .append(index + 1)
                .append("();\n    }\n\n");
        }
        source.append("""
                private static Function<String, String> step513() {
                    return Main::decorate;
                }

                private static String decorate(final String value) {
                    return "deep-" + value;
                }
            }
            """);
        writeJava(project, "com.acme.Main", source.toString());

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/deeply-forwarded-function").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mixedFunctionOriginStoredInFinalFieldIsRejected() throws Exception {
        final Path project = project("mixed-function-origin-stored-in-final-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> selected;
                    if (args.length == 0) {
                        selected = value -> "valid-" + value;
                    } else {
                        selected = null;
                    }
                    System.out.println(new Reader(selected).read("value"));
                }

                private static final class Reader {
                    private final Function<String, String> function;

                    private Reader(final Function<String, String> function) {
                        this.function = function;
                    }

                    private String read(final String value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            );
    }

    @Test
    void boundInstanceFunctionStoredInFieldSurvivesGcStress() throws Exception {
        final Path project = project("bound-instance-function-stored-in-field");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private final String prefix;
                private final Function<String, String> function;

                private Main(final String prefix) {
                    this.prefix = prefix;
                    function = this::read;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main(new String("bound-")).apply("value"));
                }

                private String apply(final String value) {
                    return function.apply(value);
                }

                private String read(final String value) {
                    return prefix + value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/bound-instance-function-stored-in-field").toString()),
                defaultProcessTimeout(),
                java.util.Map.of("JAVAN_GC_STRESS", "1")
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void boundInstanceFunctionOnNonFinalOwnerIsRejectedDeterministically() throws Exception {
        final Path project = project("bound-instance-function-non-final-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public class Main {
                private final Function<String, String> function;

                public Main() {
                    function = this::read;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().function.apply("value"));
                }

                private String read(final String value) {
                    return value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            );
    }

    @Test
    void storedJdkFunctionReferenceIsRejectedDeterministically() throws Exception {
        final Path project = project("stored-jdk-function-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<Object, String> function = String::valueOf;
                    System.out.println(new Reader(function).read("value"));
                }

                private static final class Reader {
                    private final Function<Object, String> function;

                    private Reader(final Function<Object, String> function) {
                        this.function = function;
                    }

                    private String read(final Object value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            )
            .doesNotContain("error[JAVAN030]");
    }

    @Test
    void supportedStoredFunctionDoesNotAuthorizeStoredJdkFunctionReference() throws Exception {
        final Path project = project("stored-jdk-function-reference-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> supported = Main::decorate;
                    System.out.println(supported != null);
                    final Function<Object, String> function = String::valueOf;
                    System.out.println(new Reader(function).read("value"));
                }

                private static String decorate(final String value) {
                    return "supported-" + value;
                }

                private static final class Reader {
                    private final Function<Object, String> function;

                    private Reader(final Function<Object, String> function) {
                        this.function = function;
                    }

                    private String read(final Object value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            )
            .doesNotContain("error[JAVAN030]");
    }

    @Test
    void storedJdkFunctionReferenceIsRejectedEvenWithReachableConcreteFunction() throws Exception {
        final Path project = project("stored-jdk-function-concrete-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Loader() != null);
                    final Function<Object, String> callback = String::valueOf;
                    System.out.println(callback.apply("value"));
                }

                private static final class Loader implements Function<Object, Object> {
                    @Override
                    public Object apply(final Object value) {
                        return value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN030]", "unsupported reachable bytecode", "invokedynamic")
            .doesNotContain("error[JAVAN012]");
    }

    @Test
    void supportedStoredFunctionDoesNotAuthorizeNullFunctionReceiver() throws Exception {
        final Path project = project("stored-function-null-receiver-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(apply(null));
                    final Function<Object, Object> supported = Main::decorate;
                    System.out.println(supported != null);
                }

                private static Object apply(final Function<Object, Object> function) {
                    return function.apply(null);
                }

                private static Object decorate(final Object value) {
                    return value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            )
            .doesNotContain("error[JAVAN030]");
    }

    @Test
    void materializedAndNullFunctionReceiversInOneMethodRemainIsolated() throws Exception {
        final Path project = project("stored-function-same-method-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private static final Function<Object, Object> SUPPORTED = Main::decorate;

                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(SUPPORTED.apply("valid"));
                    System.out.println(((Function<Object, Object>) null).apply("invalid"));
                }

                private static Object decorate(final Object value) {
                    return value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            );
    }

    @Test
    void storedFunctionWithPrimitiveCaptureIsRejectedAtVerification() throws Exception {
        final Path project = project("stored-function-primitive-capture");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final int suffix = args.length;
                    final Function<String, String> function = value -> value + suffix;
                    System.out.println(new Reader(function).read("value"));
                }

                private static final class Reader {
                    private final Function<String, String> function;

                    private Reader(final Function<String, String> function) {
                        this.function = function;
                    }

                    private String read(final String value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.stderr())
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            )
            .doesNotContain("error[JAVAN030]");
    }

    @Test
    void storedFunctionWithPrimitiveReturnIsRejectedAtVerification() throws Exception {
        final Path project = project("stored-function-primitive-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, Integer> function = Main::length;
                    System.out.println(new Reader(function).read("value"));
                }

                private static int length(final String value) {
                    return value.length();
                }

                private static final class Reader {
                    private final Function<String, Integer> function;

                    private Reader(final Function<String, Integer> function) {
                        this.function = function;
                    }

                    private Integer read(final String value) {
                        return function.apply(value);
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.stderr())
            .contains(
                "error[JAVAN012]",
                "Function.apply requires either a closed-world Function implementation class or a supported materialized Function lambda target."
            )
            .doesNotContain("error[JAVAN030]");
    }

    @Test
    void boundCustomObjectSamStoredInFieldBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bound-custom-object-sam");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final Renderer renderer;

                private Main() {
                    renderer = this::render;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().renderLater("row"));
                }

                private Object renderLater(final Object value) {
                    return renderer.render(value);
                }

                private Object render(final Object value) {
                    return "rendered:" + value;
                }
            }
            """);
        writeJava(project, "com.acme.Renderer", """
            package com.acme;

            @FunctionalInterface
            public interface Renderer {
                Object render(Object value);
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/bound-custom-object-sam").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void boundCustomObjectSamWithConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bound-custom-object-sam-mixed-receivers");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final Renderer renderer;

                private Main() {
                    renderer = this::render;
                }

                public static void main(final String[] args) {
                    final Main application = new Main();
                    System.out.println(
                        application.renderLater(new PrefixRenderer(), "row")
                            + ":"
                            + application.renderLater(application.renderer, "row")
                    );
                }

                private Object renderLater(final Renderer selected, final Object value) {
                    return selected.render(value);
                }

                private Object render(final Object value) {
                    return "materialized:" + value;
                }
            }
            """);
        writeJava(project, "com.acme.Renderer", """
            package com.acme;

            @FunctionalInterface
            public interface Renderer {
                Object render(Object value);
            }
            """);
        writeJava(project, "com.acme.PrefixRenderer", """
            package com.acme;

            public final class PrefixRenderer implements Renderer {
                @Override
                public Object render(final Object value) {
                    return "concrete:" + value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/bound-custom-object-sam-mixed-receivers").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void boundCustomLongSamStoredInFieldBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bound-custom-long-sam");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final KeySource keySource;

                private Main() {
                    keySource = this::key;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().keyLater(7L));
                }

                private String keyLater(final long value) {
                    return keySource.key(value);
                }

                private String key(final long value) {
                    return "row-" + value;
                }
            }
            """);
        writeJava(project, "com.acme.KeySource", """
            package com.acme;

            @FunctionalInterface
            public interface KeySource {
                String key(long value);
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/bound-custom-long-sam").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void staticCustomLongSamPassedThroughFactoryAndStoredInFieldBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("static-custom-long-sam");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final KeySource keySource;

                private Main(final KeySource keySource) {
                    this.keySource = keySource;
                }

                public static void main(final String[] args) {
                    final Main application = create(index -> "row-" + index);
                    System.out.println(application.keyLater(7L));
                }

                private static Main create(final KeySource keySource) {
                    return new Main(keySource);
                }

                private String keyLater(final long value) {
                    return keySource.key(value);
                }
            }
            """);
        writeJava(project, "com.acme.KeySource", """
            package com.acme;

            @FunctionalInterface
            public interface KeySource {
                String key(long value);
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/static-custom-long-sam").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void staticCustomLongSamWithCapturedListBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("static-custom-long-sam-captured-list");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private final KeySource keySource;

                private Main(final KeySource keySource) {
                    this.keySource = keySource;
                }

                public static void main(final String[] args) {
                    final List<String> rows = List.of("zero", "one");
                    final KeySource source = index -> rows.get((int) index);
                    final Main application = new Main(source);
                    System.out.println(application.keyLater(new PrefixKeySource(), application.keySource, 1L));
                }

                private String keyLater(final KeySource ignored, final KeySource selected, final long index) {
                    return selected.key(index);
                }
            }
            """);
        writeJava(project, "com.acme.KeySource", """
            package com.acme;

            @FunctionalInterface
            public interface KeySource {
                String key(long index);
            }
            """);
        writeJava(project, "com.acme.PrefixKeySource", """
            package com.acme;

            public final class PrefixKeySource implements KeySource {
                @Override
                public String key(final long index) {
                    return "prefix-" + index;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(
                project,
                List.of(project.resolve(".javan/bin/static-custom-long-sam-captured-list").toString()),
                defaultProcessTimeout(),
                java.util.Map.of("JAVAN_GC_STRESS", "1")
            ).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void staticCustomLongSamWithMultipleReferenceCapturesBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("static-custom-long-sam-multiple-captures");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private final KeySource keySource;

                private Main(final KeySource keySource) {
                    this.keySource = keySource;
                }

                public static void main(final String[] args) {
                    final String prefix = args.length == 0 ? "row-" : args[0];
                    final List<String> rows = List.of("zero", "one");
                    final KeySource source = index -> prefix + rows.get((int) index);
                    final Main application = new Main(source);
                    System.out.println(application.keyLater(new PrefixKeySource(), application.keySource, 1L));
                }

                private String keyLater(final KeySource ignored, final KeySource selected, final long index) {
                    return selected.key(index);
                }
            }
            """);
        writeJava(project, "com.acme.KeySource", """
            package com.acme;

            @FunctionalInterface
            public interface KeySource {
                String key(long index);
            }
            """);
        writeJava(project, "com.acme.PrefixKeySource", """
            package com.acme;

            public final class PrefixKeySource implements KeySource {
                @Override
                public String key(final long index) {
                    return "prefix-" + index;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(
                project,
                List.of(project.resolve(".javan/bin/static-custom-long-sam-multiple-captures").toString()),
                defaultProcessTimeout(),
                java.util.Map.of("JAVAN_GC_STRESS", "1")
            ).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void boundCustomLongSamWithConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bound-custom-long-sam-mixed-receivers");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final KeySource keySource;

                private Main() {
                    keySource = this::key;
                }

                public static void main(final String[] args) {
                    final Main application = new Main();
                    System.out.println(
                        application.keyLater(new PrefixKeySource(), 7L)
                            + ":"
                            + application.keyLater(application.keySource, 7L)
                    );
                }

                private String keyLater(final KeySource selected, final long value) {
                    return selected.key(value);
                }

                private String key(final long value) {
                    return "materialized-" + value;
                }
            }
            """);
        writeJava(project, "com.acme.KeySource", """
            package com.acme;

            @FunctionalInterface
            public interface KeySource {
                String key(long value);
            }
            """);
        writeJava(project, "com.acme.PrefixKeySource", """
            package com.acme;

            public final class PrefixKeySource implements KeySource {
                @Override
                public String key(final long value) {
                    return "concrete-" + value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/bound-custom-long-sam-mixed-receivers").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void twoObjectSamWithConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("two-object-sam-mixed-receivers");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Combiner materialized = Main::combine;
                    System.out.println(
                        combineLater(new PrefixCombiner(), "left", "right")
                            + ":"
                            + combineLater(materialized, "left", "right")
                    );
                }

                private static Object combineLater(
                    final Combiner selected,
                    final Object first,
                    final Object second
                ) {
                    return selected.combine(first, second);
                }

                private static Object combine(final Object first, final Object second) {
                    return "materialized-" + first + "-" + second;
                }
            }
            """);
        writeJava(project, "com.acme.Combiner", """
            package com.acme;

            @FunctionalInterface
            public interface Combiner {
                Object combine(Object first, Object second);
            }
            """);
        writeJava(project, "com.acme.PrefixCombiner", """
            package com.acme;

            public final class PrefixCombiner implements Combiner {
                @Override
                public Object combine(final Object first, final Object second) {
                    return "concrete-" + first + "-" + second;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/two-object-sam-mixed-receivers").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void booleanSamWithConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boolean-sam-mixed-receivers");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Matcher materialized = Main::matches;
                    System.out.println(
                        matchesLater(new NeverMatcher(), "row")
                            + ":"
                            + matchesLater(materialized, "row")
                    );
                }

                private static boolean matchesLater(final Matcher selected, final Object value) {
                    return selected.matches(value);
                }

                private static boolean matches(final Object value) {
                    return value != null;
                }
            }
            """);
        writeJava(project, "com.acme.Matcher", """
            package com.acme;

            @FunctionalInterface
            public interface Matcher {
                boolean matches(Object value);
            }
            """);
        writeJava(project, "com.acme.NeverMatcher", """
            package com.acme;

            public final class NeverMatcher implements Matcher {
                @Override
                public boolean matches(final Object value) {
                    return false;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/boolean-sam-mixed-receivers").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void consumerWithConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("consumer-mixed-receivers");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Consumer<String> materialized = Main::consume;
                    consumeLater(new PrefixConsumer(), "row");
                    consumeLater(materialized, "row");
                }

                private static void consumeLater(final Consumer<String> selected, final String value) {
                    selected.accept(value);
                }

                private static void consume(final String value) {
                    System.out.println("materialized-" + value);
                }
            }
            """);
        writeJava(project, "com.acme.PrefixConsumer", """
            package com.acme;

            import java.util.function.Consumer;

            public final class PrefixConsumer implements Consumer<String> {
                @Override
                public void accept(final String value) {
                    System.out.println("concrete-" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/consumer-mixed-receivers").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void biConsumerWithConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("bi-consumer-mixed-receivers");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.BiConsumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final BiConsumer<String, String> materialized = Main::consume;
                    consumeLater(new PrefixConsumer(), "left", "right");
                    consumeLater(materialized, "left", "right");
                }

                private static void consumeLater(
                    final BiConsumer<String, String> selected,
                    final String first,
                    final String second
                ) {
                    selected.accept(first, second);
                }

                private static void consume(final String first, final String second) {
                    System.out.println("materialized-" + first + "-" + second);
                }
            }
            """);
        writeJava(project, "com.acme.PrefixConsumer", """
            package com.acme;

            import java.util.function.BiConsumer;

            public final class PrefixConsumer implements BiConsumer<String, String> {
                @Override
                public void accept(final String first, final String second) {
                    System.out.println("concrete-" + first + "-" + second);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/bi-consumer-mixed-receivers").toString())).stdout()
            : "";

        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput)
            .isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void boundCustomSamWithPrimitiveCaptureReportsUnsupportedInterfaceDispatch() throws Exception {
        final Path project = project("bound-custom-sam-primitive-capture");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final Renderer renderer;

                private Main(final int suffix) {
                    renderer = value -> render(suffix, value);
                }

                public static void main(final String[] args) {
                    System.out.println(new Main(args.length).renderLater("row"));
                }

                private Object renderLater(final Object value) {
                    return renderer.render(value);
                }

                private Object render(final int suffix, final Object value) {
                    return value + ":" + suffix;
                }
            }
            """);
        writeJava(project, "com.acme.Renderer", """
            package com.acme;

            @FunctionalInterface
            public interface Renderer {
                Object render(Object value);
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "com/acme/Renderer.render(Ljava/lang/Object;)Ljava/lang/Object;",
                "Interface dispatch requires at least one concrete implementation in the closed world."
            )
            .doesNotContain("error[JAVAN901]", "JAVAN-RUNTIME-PANIC");
    }

    @Test
    void boundCustomSamWithPrimitiveReturnReportsUnsupportedInterfaceDispatch() throws Exception {
        final Path project = project("bound-custom-sam-primitive-return");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final Renderer renderer;

                private Main() {
                    renderer = this::render;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().renderLater("row"));
                }

                private int renderLater(final Object value) {
                    return renderer.render(value);
                }

                private int render(final Object value) {
                    return value.toString().length();
                }
            }
            """);
        writeJava(project, "com.acme.Renderer", """
            package com.acme;

            @FunctionalInterface
            public interface Renderer {
                int render(Object value);
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "com/acme/Renderer.render(Ljava/lang/Object;)I",
                "Interface dispatch requires at least one concrete implementation in the closed world."
            )
            .doesNotContain("error[JAVAN901]", "JAVAN-RUNTIME-PANIC");
    }

    @Test
    void boundCustomSamWithNonFinalOwnerReportsUnsupportedInterfaceDispatch() throws Exception {
        final Path project = project("bound-custom-sam-non-final-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public class Main {
                private final Renderer renderer;

                private Main() {
                    renderer = this::render;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().renderLater("row"));
                }

                private Object renderLater(final Object value) {
                    return renderer.render(value);
                }

                private Object render(final Object value) {
                    return "rendered:" + value;
                }
            }
            """);
        writeJava(project, "com.acme.Renderer", """
            package com.acme;

            @FunctionalInterface
            public interface Renderer {
                Object render(Object value);
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "com/acme/Renderer.render(Ljava/lang/Object;)Ljava/lang/Object;",
                "Interface dispatch requires at least one concrete implementation in the closed world."
            )
            .doesNotContain("error[JAVAN901]", "JAVAN-RUNTIME-PANIC");
    }

    @Test
    void boundCustomSamWithJdkOwnerReportsUnsupportedInterfaceDispatch() throws Exception {
        final Path project = project("bound-custom-sam-jdk-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private final Renderer renderer;

                private Main() {
                    renderer = "prefix-"::concat;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().renderLater("row"));
                }

                private String renderLater(final String value) {
                    return renderer.render(value);
                }
            }
            """);
        writeJava(project, "com.acme.Renderer", """
            package com.acme;

            @FunctionalInterface
            public interface Renderer {
                String render(String value);
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains(
                "error[JAVAN012]",
                "com/acme/Renderer.render(Ljava/lang/String;)Ljava/lang/String;",
                "Interface dispatch requires at least one concrete implementation in the closed world."
            )
            .doesNotContain("error[JAVAN901]", "JAVAN-RUNTIME-PANIC");
    }

    @Test
    void supplierGetConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("supplier-get-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Object> supplier = new FallbackSupplier();
                    System.out.println(supplier.get());
                }
            }
            """);
        writeJava(project, "com.acme.FallbackSupplier", """
            package com.acme;

            import java.util.function.Supplier;

            public final class FallbackSupplier implements Supplier<Object> {
                @Override
                public Object get() {
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/supplier-get-concrete").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void supplierGetZeroCaptureLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("supplier-get-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> supplier = () -> "fallback";
                    System.out.println(supplier.get());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/supplier-get-lambda").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void predicateTestConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("predicate-test-concrete-direct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Predicate;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Predicate<String> predicate = new Matcher();
                    System.out.println(predicate.test("value"));
                }
            }
            """);
        writeJava(project, "com.acme.Matcher", """
            package com.acme;

            import java.util.function.Predicate;

            public final class Matcher implements Predicate<String> {
                @Override
                public boolean test(final String value) {
                    return value.equals("value");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/predicate-test-concrete-direct").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void predicateTestDirectZeroCaptureLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("predicate-test-lambda-direct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Predicate;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Predicate<String> predicate = value -> value.equals("value");
                    System.out.println(predicate.test("value"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/predicate-test-lambda-direct").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void earlierPredicateLambdaDoesNotAuthorizeNullPredicateReceiver() throws Exception {
        final Path project = project("predicate-lambda-null-receiver-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Predicate;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Predicate<String> supported = value -> true;
                    final Predicate<String> receiver = null;
                    System.out.println(receiver.test("value"));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Predicate.test requires");
    }

    @Test
    void earlierSupplierLambdaDoesNotAuthorizeNullSupplierReceiver() throws Exception {
        final Path project = project("supplier-lambda-null-receiver-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> supported = () -> "supported";
                    final Supplier<String> receiver = null;
                    System.out.println(receiver.get());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Supplier.get requires");
    }

    @Test
    void earlierSupplierLambdaDoesNotAuthorizeNullOptionalOrElseGetCallback() throws Exception {
        final Path project = project("supplier-lambda-null-optional-or-else-get-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> supported = () -> "supported";
                    final Supplier<String> fallback = null;
                    System.out.println(Optional.<String>empty().orElseGet(fallback));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Optional.orElseGet requires");
    }

    @Test
    void earlierSupplierLambdaDoesNotAuthorizeNullOptionalOrCallback() throws Exception {
        final Path project = project("supplier-lambda-null-optional-or-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Optional<String>> supported = () -> Optional.of("supported");
                    final Supplier<Optional<String>> fallback = null;
                    System.out.println(Optional.<String>empty().or(fallback));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Optional.or requires");
    }

    @Test
    void earlierSupplierLambdaDoesNotAuthorizeNullObjectsFallbackCallback() throws Exception {
        final Path project = project("supplier-lambda-null-objects-fallback-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> supported = () -> "supported";
                    final Supplier<String> fallback = null;
                    System.out.println(Objects.requireNonNullElseGet(null, fallback));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Objects.requireNonNullElseGet requires");
    }

    @Test
    void mixedMaterializedAndNullSupplierReceiverIsRejected() throws Exception {
        final Path project = project("supplier-materialized-null-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> selected;
                    if (args.length == 0) {
                        selected = () -> "valid";
                    } else {
                        selected = null;
                    }
                    System.out.println(selected.get());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Supplier.get requires");
    }

    @Test
    void earlierFunctionLambdaDoesNotAuthorizeNullFunctionReceiver() throws Exception {
        final Path project = project("function-lambda-null-receiver-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> supported = value -> value;
                    final Function<String, String> receiver = null;
                    System.out.println(receiver.apply("value"));
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Function.apply requires");
    }

    @Test
    void consumerAcceptDirectConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("consumer-accept-concrete-direct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Consumer<String> consumer = new Printer();
                    consumer.accept("value");
                }
            }
            """);
        writeJava(project, "com.acme.Printer", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Printer implements Consumer<String> {
                @Override
                public void accept(final String value) {
                    System.out.println("seen:" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/consumer-accept-concrete-direct").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void consumerAcceptDirectZeroCaptureLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("consumer-accept-lambda-direct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Consumer<String> consumer = value -> System.out.println("seen:" + value);
                    consumer.accept("value");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/consumer-accept-lambda-direct").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void biConsumerAcceptDirectConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("biconsumer-accept-concrete-direct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.BiConsumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final BiConsumer<String, String> consumer = new Printer();
                    consumer.accept("left", "right");
                }
            }
            """);
        writeJava(project, "com.acme.Printer", """
            package com.acme;

            import java.util.function.BiConsumer;

            public final class Printer implements BiConsumer<String, String> {
                @Override
                public void accept(final String left, final String right) {
                    System.out.println(left + ":" + right);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/biconsumer-accept-concrete-direct").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void biConsumerAcceptDirectZeroCaptureLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("biconsumer-accept-lambda-direct");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.BiConsumer;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final BiConsumer<String, String> consumer = (left, right) -> System.out.println(left + ":" + right);
                    consumer.accept("left", "right");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/biconsumer-accept-lambda-direct").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapComputeIfPresentConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-if-present-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<Object, Object> values = new HashMap<>();
                    values.put("demo", "value");
                    System.out.println(values.computeIfPresent("demo", new Joiner()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Joiner", """
            package com.acme;

            import java.util.function.BiFunction;

            public final class Joiner implements BiFunction<Object, Object, Object> {
                @Override
                public Object apply(final Object key, final Object value) {
                    return key + ":" + value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-compute-if-present-concrete").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapComputeIfPresentLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-if-present-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("demo", "value");
                    System.out.println(values.computeIfPresent("demo", (key, value) -> key + "-" + value));
                    System.out.println(values.get("demo"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-compute-if-present-lambda").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapComputeIfPresentMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-if-present-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("present", "value");
                    System.out.println(values.computeIfPresent("missing", (key, value) -> key + "-" + value));
                    System.out.println(values.size());
                    System.out.println(values.containsKey("present"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-compute-if-present-missing").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapComputeIfPresentNullRemapRemovesEntryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-if-present-null-remap");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("demo", "value");
                    System.out.println(values.computeIfPresent("demo", (key, value) -> null));
                    System.out.println(values.containsKey("demo"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-compute-if-present-null-remap").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapMergeConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-merge-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<Object, Object> values = new HashMap<>();
                    values.put("demo", "left");
                    System.out.println(values.merge("demo", "right", new Joiner()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Joiner", """
            package com.acme;

            import java.util.function.BiFunction;

            public final class Joiner implements BiFunction<Object, Object, Object> {
                @Override
                public Object apply(final Object left, final Object right) {
                    return left + ":" + right;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-merge-concrete").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapMergeMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-merge-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.merge("demo", "value", (left, right) -> left + ":" + right));
                    System.out.println(values.get("demo"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-merge-missing").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapMergeNullRemapRemovesEntryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-merge-null-remap");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("demo", "value");
                    System.out.println(values.merge("demo", "other", (left, right) -> null));
                    System.out.println(values.containsKey("demo"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-merge-null-remap").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void mapComputeConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<Object, Object> values = new HashMap<>();
                    values.put("demo", "value");
                    System.out.println(values.compute("demo", new Joiner()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Joiner", """
            package com.acme;

            import java.util.function.BiFunction;

            public final class Joiner implements BiFunction<Object, Object, Object> {
                @Override
                public Object apply(final Object key, final Object value) {
                    return key + ":" + value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/map-compute-concrete").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void hashMapComputeIfAbsentCapturedLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-compute-if-absent-captured-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String suffix = "-value";
                    final HashMap<String, String> values = new HashMap<>();
                    System.out.println(values.computeIfAbsent("demo", key -> key + suffix));
                    System.out.println(values.computeIfAbsent("demo", key -> "other"));
                    System.out.println(values.get("demo"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        final String nativeOutput = run.exitCode() == 0
            ? process(project, List.of(project.resolve(".javan/bin/hashmap-compute-if-absent-captured-lambda").toString())).stdout()
            : "";
        assertThat(run.exitCode() + "\n" + run.stderr() + nativeOutput).isEqualTo("0\n" + jvmOutput);
    }

    @Test
    void materializedFunctionDoesNotAuthorizeNullComputeIfAbsentCallback() throws Exception {
        final Path project = project("map-compute-if-absent-function-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>();
                    final Function<String, String> supported = Main::decorate;
                    final Function<String, String> callback = null;
                    System.out.println(values.computeIfAbsent("demo", callback));
                }

                private static String decorate(final String value) {
                    return "supported-" + value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Map.computeIfAbsent requires");
    }

    @Test
    void materializedFunctionDoesNotAuthorizeNullOptionalMapCallback() throws Exception {
        final Path project = project("optional-map-function-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Optional<String> value = Optional.of("value");
                    final Function<String, String> supported = Main::decorate;
                    final Function<String, String> callback = null;
                    System.out.println(value.map(callback).orElse("missing"));
                }

                private static String decorate(final String value) {
                    return "supported-" + value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Optional.map requires");
    }

    @Test
    void materializedFunctionDoesNotAuthorizeNullOptionalFlatMapCallback() throws Exception {
        final Path project = project("optional-flat-map-function-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Optional<String> value = Optional.of("value");
                    final Function<String, Optional<String>> supported = Main::decorate;
                    final Function<String, Optional<String>> callback = null;
                    System.out.println(value.flatMap(callback).orElse("missing"));
                }

                private static Optional<String> decorate(final String value) {
                    return Optional.of("supported-" + value);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Optional.flatMap requires");
    }

    @Test
    void reachableConcreteFunctionDoesNotAuthorizeNullFunctionReceiver() throws Exception {
        final Path project = project("concrete-function-null-receiver-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Loader() != null);
                    final Function<String, String> callback = null;
                    System.out.println(callback.apply("demo"));
                }

                private static final class Loader implements Function<String, String> {
                    @Override
                    public String apply(final String value) {
                        return "loaded-" + value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Function.apply requires");
    }

    @Test
    void reachableConcreteFunctionDoesNotAuthorizeNullComputeIfAbsentCallback() throws Exception {
        final Path project = project("concrete-function-map-callback-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Loader() != null);
                    final HashMap<String, String> values = new HashMap<>();
                    final Function<String, String> callback = null;
                    System.out.println(values.computeIfAbsent("demo", callback));
                }

                private static final class Loader implements Function<String, String> {
                    @Override
                    public String apply(final String value) {
                        return "loaded-" + value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Map.computeIfAbsent requires");
    }

    @Test
    void reachableConcreteFunctionDoesNotAuthorizeNullOptionalMapCallback() throws Exception {
        final Path project = project("concrete-function-optional-map-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Loader() != null);
                    final Function<String, String> callback = null;
                    System.out.println(Optional.of("demo").map(callback).orElse("missing"));
                }

                private static final class Loader implements Function<String, String> {
                    @Override
                    public String apply(final String value) {
                        return "loaded-" + value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Optional.map requires");
    }

    @Test
    void reachableConcreteFunctionDoesNotAuthorizeNullOptionalFlatMapCallback() throws Exception {
        final Path project = project("concrete-function-optional-flat-map-isolation");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Loader() != null);
                    final Function<String, Optional<String>> callback = null;
                    System.out.println(Optional.of("demo").flatMap(callback).orElse("missing"));
                }

                private static final class Loader implements Function<String, Optional<String>> {
                    @Override
                    public Optional<String> apply(final String value) {
                        return Optional.of("loaded-" + value);
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .startsWith("2\n")
            .contains("error[JAVAN012]", "Optional.flatMap requires");
    }

    @Test
    void storedMaterializedFunctionRunsThroughMapComputeIfAbsent() throws Exception {
        final Path project = project("map-compute-if-absent-stored-function");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>();
                    final Function<String, String> callback = Main::decorate;
                    System.out.println(values.computeIfAbsent("demo", callback));
                }

                private static String decorate(final String value) {
                    return "stored-" + value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/map-compute-if-absent-stored-function").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\nstored-demo\n");
    }

    @Test
    void storedMaterializedFunctionRunsThroughOptionalMap() throws Exception {
        final Path project = project("optional-map-stored-function");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> callback = Main::decorate;
                    System.out.println(Optional.of("demo").map(callback).orElse("missing"));
                }

                private static String decorate(final String value) {
                    return "stored-" + value;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/optional-map-stored-function").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\nstored-demo\n");
    }

    @Test
    void storedMaterializedFunctionRunsThroughOptionalFlatMap() throws Exception {
        final Path project = project("optional-flat-map-stored-function");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, Optional<String>> callback = Main::decorate;
                    System.out.println(Optional.of("demo").flatMap(callback).orElse("missing"));
                }

                private static Optional<String> decorate(final String value) {
                    return Optional.of("stored-" + value);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/optional-flat-map-stored-function").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\nstored-demo\n");
    }

    @Test
    void materializedFunctionInTernaryBranchUsesExactDirectApplyTarget() throws Exception {
        final Path project = project("ternary-stored-function-direct-apply");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> callback = Main::decorate;
                    final String result = args.length == 0 ? callback.apply("demo") : "other";
                    System.out.println(result);
                }

                private static String decorate(final String value) {
                    return "materialized-" + value;
                }

                private static final class Loader implements Function<String, String> {
                    @Override
                    public String apply(final String value) {
                        return "concrete-" + value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/ternary-stored-function-direct-apply").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\nmaterialized-demo\n");
    }

    @Test
    void materializedFunctionInTernaryBranchUsesExactComputeIfAbsentTarget() throws Exception {
        final Path project = project("ternary-stored-function-compute-if-absent");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>();
                    final Function<String, String> callback = Main::decorate;
                    final String result = args.length == 0
                        ? values.computeIfAbsent("demo", callback)
                        : "other";
                    System.out.println(result);
                }

                private static String decorate(final String value) {
                    return "materialized-" + value;
                }

                private static final class Loader implements Function<String, String> {
                    @Override
                    public String apply(final String value) {
                        return "concrete-" + value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/ternary-stored-function-compute-if-absent").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\nmaterialized-demo\n");
    }

    @Test
    void materializedFunctionInTernaryBranchUsesExactOptionalMapTarget() throws Exception {
        final Path project = project("ternary-stored-function-optional-map");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Function<String, String> callback = Main::decorate;
                    final String result = args.length == 0
                        ? Optional.of("demo").map(callback).orElse("missing")
                        : "other";
                    System.out.println(result);
                }

                private static String decorate(final String value) {
                    return "materialized-" + value;
                }

                private static final class Loader implements Function<String, String> {
                    @Override
                    public String apply(final String value) {
                        return "concrete-" + value;
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        final String result = run.exitCode() == 0
            ? run.exitCode() + "\n" + run.stderr() + process(
                project,
                List.of(project.resolve(".javan/bin/ternary-stored-function-optional-map").toString())
            ).stdout()
            : run.exitCode() + "\n" + run.stderr();

        assertThat(result).isEqualTo("0\nmaterialized-demo\n");
    }

    @Test
    void mapComputeIfAbsentConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-if-absent-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.computeIfAbsent("demo", new Loader()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.function.Function;

            public final class Loader implements Function<String, String> {
                @Override
                public String apply(final String key) {
                    return key + ":value";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-compute-if-absent-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo:value\ndemo:value\n");
    }

    @Test
    void optionalMapConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-map-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").map(new Loader()).orElse("missing"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.function.Function;

            public final class Loader implements Function<Object, Object> {
                @Override
                public Object apply(final Object value) {
                    return value + "-native";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-map-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value-native\n");
    }

    @Test
    void optionalFlatMapConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-flat-map-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").flatMap(new Loader()).orElse("missing"));
                    System.out.println(Optional.<String>empty().flatMap(new Loader()).orElse("missing"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Function;

            public final class Loader implements Function<Object, Optional<Object>> {
                @Override
                public Optional<Object> apply(final Object value) {
                    return Optional.of(value + "-native");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-flat-map-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value-native\nmissing\n");
    }

    @Test
    void optionalFilterConcretePredicateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-filter-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").filter(new KeepPredicate()).orElse("missing"));
                    System.out.println(Optional.of("drop").filter(new KeepPredicate()).orElse("missing"));
                }
            }
            """);
        writeJava(project, "com.acme.KeepPredicate", """
            package com.acme;

            import java.util.function.Predicate;

            public final class KeepPredicate implements Predicate<Object> {
                @Override
                public boolean test(final Object value) {
                    return "value".equals(value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-filter-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nmissing\n");
    }

    @Test
    void optionalFilterBoundFinalReceiverPredicateBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-filter-bound-final-receiver");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Expected expected = new Expected("focused");
                    System.out.println(Optional.of("focused").filter(expected::matches).isPresent());
                    System.out.println(Optional.of("other").filter(expected::matches).isPresent());
                }

                private static final class Expected {
                    private final String value;

                    private Expected(final String value) {
                        this.value = value;
                    }

                    private boolean matches(final Object candidate) {
                        return value.equals(candidate);
                    }
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/optional-filter-bound-final-receiver").toString())
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetInlineSupplierLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else-get-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").orElseGet(() -> "fallback"));
                    System.out.println(Optional.<String>empty().orElseGet(() -> "fallback"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else-get-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nfallback\n");
    }

    @Test
    void optionalOrElseGetBoundInstanceMixedCaptureSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else-get-bound-instance-mixed-capture-supplier");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private final String prefix;

                private Main(final String prefix) {
                    this.prefix = prefix;
                }

                public static void main(final String[] args) {
                    System.out.println(new Main("row").select(Optional.empty(), "wide", 7));
                }

                private String select(final Optional<String> previous, final String viewport, final int row) {
                    final String state = "ready";
                    return previous.orElseGet(() -> resolve(state, viewport, row));
                }

                private String resolve(final String state, final String viewport, final int row) {
                    return prefix + ":" + state + ":" + viewport + ":" + row;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/optional-or-else-get-bound-instance-mixed-capture-supplier").toString())
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetBoundMethodSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else-get-bound-method-supplier");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                public static void main(final String[] args) {
                    System.out.println(new Main().select(Optional.empty()));
                }

                private String select(final Optional<String> previous) {
                    return previous.orElseGet(this::supply);
                }

                private String supply() {
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/optional-or-else-get-bound-method-supplier").toString())
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetBoundInstanceSupplierOnNonFinalClassFailsClearly() throws Exception {
        final Path project = project("optional-or-else-get-bound-instance-supplier-non-final");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public class Main {
                public static void main(final String[] args) {
                    System.out.println(new Main().select(Optional.empty()));
                }

                private String select(final Optional<String> previous) {
                    return previous.orElseGet(() -> supply());
                }

                private String supply() {
                    return "fallback";
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode() + "\n" + run.stderr())
            .contains("2\nerror[JAVAN012]", "Optional.orElseGet", "supported direct supplier lambda target");
    }

    @Test
    void optionalOrElseGetCapturedSupplierLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else-get-captured-supplier-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String prefix = "prefix";
                    final String middle = "middle";
                    final String suffix = "suffix";
                    final Supplier<String> fallback = () -> join(prefix, middle, suffix);
                    System.out.println(Optional.<String>empty().orElseGet(fallback));
                }

                private static String join(final String prefix, final String middle, final String suffix) {
                    return prefix + ":" + middle + ":" + suffix;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else-get-captured-supplier-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetMaterializedSupplierSkipsPresentValue() throws Exception {
        final Path project = project("optional-or-else-get-materialized-supplier-present");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> fallback = () -> fallback();
                    System.out.println(Optional.of("value").orElseGet(fallback));
                }

                private static String fallback() {
                    System.out.println("called");
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else-get-materialized-supplier-present").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetMaterializedSupplierInvokesEmptyValueOnce() throws Exception {
        final Path project = project("optional-or-else-get-materialized-supplier-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> fallback = () -> fallback();
                    System.out.println(Optional.<String>empty().orElseGet(fallback));
                }

                private static String fallback() {
                    System.out.println("called");
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else-get-materialized-supplier-empty").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrMaterializedSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-materialized-supplier");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Optional<String>> fallback = () -> fallback();
                    System.out.println(Optional.<String>empty().or(fallback).orElse("missing"));
                }

                private static Optional<String> fallback() {
                    System.out.println("called");
                    return Optional.of("fallback");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-materialized-supplier").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void objectsRequireNonNullElseGetMaterializedSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("objects-require-non-null-else-get-materialized-supplier");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Objects;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> fallback = () -> fallback();
                    System.out.println(Objects.requireNonNullElseGet(null, fallback));
                }

                private static String fallback() {
                    System.out.println("called");
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/objects-require-non-null-else-get-materialized-supplier").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void supplierGetMaterializedInstanceMethodReferenceBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("supplier-get-materialized-instance-method-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private final String value;

                private Main(final String value) {
                    this.value = value;
                }

                public static void main(final String[] args) {
                    final Main receiver = new Main("instance");
                    final Supplier<String> supplier = receiver::value;
                    System.out.println(supplier.get());
                }

                private String value() {
                    return value;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/supplier-get-materialized-instance-method-reference").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void unsupportedSupplierConstructorReferenceIsRejected() throws Exception {
        final Path project = project("unsupported-supplier-constructor-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<StringBuilder> fallback = StringBuilder::new;
                    System.out.println(Optional.<StringBuilder>empty().orElseGet(fallback));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.stderr()).contains("error[JAVAN012]", "Optional.orElseGet");
    }

    @Test
    void unsupportedSupplierShapeReportsExactSupportedSubset() throws Exception {
        final Path project = project("unsupported-supplier-shape-diagnostic");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<StringBuilder> unsupported = StringBuilder::new;
                    System.out.println(unsupported == null);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.stderr()).contains(
            "error[JAVAN030]",
            "Only StringConcatFactory string concatenation, exact record ObjectMethods equals/hashCode, exact LambdaMetafactory Function/Predicate shapes, "
                + "the exact Supplier subset (zero-argument reference-return invocation directly lowered to admitted application-static "
                + "implementations or final implementation-owner bound instance targets, plus application static/instance-target "
                + "materialization with reference-only captures and reference "
                + "returns), the current "
                + "Consumer/BiConsumer object-capture materialization slice, and the current custom-SAM materialization subset are implemented."
        );
    }

    @Test
    void jdkOwnerSupplierInstanceMethodReferenceReportsExactSupportedSubset() throws Exception {
        final Path project = project("unsupported-jdk-owner-supplier-method-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<String> unsupported = "value"::toString;
                    System.out.println(unsupported == null);
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.stderr()).contains(
            "error[JAVAN030]",
            "Only StringConcatFactory string concatenation, exact record ObjectMethods equals/hashCode, exact LambdaMetafactory Function/Predicate shapes, "
                + "the exact Supplier subset (zero-argument reference-return invocation directly lowered to admitted application-static "
                + "implementations or final implementation-owner bound instance targets, plus application static/instance-target "
                + "materialization with reference-only captures and reference "
                + "returns), the current "
                + "Consumer/BiConsumer object-capture materialization slice, and the current custom-SAM materialization subset are implemented."
        );
    }

    @Test
    void supplierGetMixedConcreteAndMaterializedReceiversBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("supplier-get-mixed-concrete-materialized");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String prefix = "captured";
                    final String suffix = "-lambda";
                    final Supplier<String> captured = () -> prefix + suffix;
                    System.out.println(read(new ConcreteSupplier()) + ":" + read(captured));
                }

                private static String read(final Supplier<String> supplier) {
                    return supplier.get();
                }
            }
            """);
        writeJava(project, "com.acme.ConcreteSupplier", """
            package com.acme;

            import java.util.function.Supplier;

            public final class ConcreteSupplier implements Supplier<String> {
                @Override
                public String get() {
                    return "concrete";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/supplier-get-mixed-concrete-materialized").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetMixedConcreteAndMaterializedSuppliersBuildAndMatchJvmOutput() throws Exception {
        final Path project = project("optional-or-else-get-mixed-concrete-materialized");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String prefix = "captured";
                    System.out.println(select(new ConcreteSupplier()));
                    System.out.println(select(() -> prefix + "-lambda"));
                }

                private static String select(final Supplier<String> supplier) {
                    return Optional.<String>empty().orElseGet(supplier);
                }
            }
            """);
        writeJava(project, "com.acme.ConcreteSupplier", """
            package com.acme;

            import java.util.function.Supplier;

            public final class ConcreteSupplier implements Supplier<String> {
                @Override
                public String get() {
                    return "concrete";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/optional-or-else-get-mixed-concrete-materialized").toString())
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void supplierGetMaterializedArrayCaptureBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("supplier-get-materialized-array-capture");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String[] values = new String[] {"zero", "one"};
                    final Supplier<String> captured = () -> values[1];
                    System.out.println(read(captured));
                }

                private static String read(final Supplier<String> supplier) {
                    return supplier.get();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/supplier-get-materialized-array-capture").toString()),
            defaultProcessTimeout(),
            java.util.Map.of("JAVAN_GC_STRESS", "1")
        ).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrMaterializedSupplierSkipsPresentValue() throws Exception {
        final Path project = project("optional-or-materialized-supplier-present");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Supplier<Optional<String>> fallback = () -> fallback();
                    System.out.println(Optional.of("value").or(fallback).orElse("missing"));
                }

                private static Optional<String> fallback() {
                    System.out.println("called");
                    return Optional.of("fallback");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        requireBuildSuccess(run(tempDir, "build", project.toString()));
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-materialized-supplier-present").toString())).stdout())
            .isEqualTo(jvmOutput);
    }

    @Test
    void optionalOrElseGetConcreteSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-else-get-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").orElseGet(new FallbackSupplier()));
                    System.out.println(Optional.<String>empty().orElseGet(new FallbackSupplier()));
                }
            }
            """);
        writeJava(project, "com.acme.FallbackSupplier", """
            package com.acme;

            import java.util.function.Supplier;

            public final class FallbackSupplier implements Supplier<String> {
                @Override
                public String get() {
                    return "fallback";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-else-get-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nfallback\n");
    }

    @Test
    void optionalOrConcreteSupplierBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-or-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Optional.of("value").or(new FallbackSupplier()).orElse("missing"));
                    System.out.println(Optional.<String>empty().or(new FallbackSupplier()).orElse("missing"));
                }
            }
            """);
        writeJava(project, "com.acme.FallbackSupplier", """
            package com.acme;

            import java.util.Optional;
            import java.util.function.Supplier;

            public final class FallbackSupplier implements Supplier<Optional<String>> {
                @Override
                public Optional<String> get() {
                    return Optional.of("fallback");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-or-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\nfallback\n");
    }

    @Test
    void optionalIfPresentMaterializedConsumerLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-if-present-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.of("value").ifPresent(value -> System.out.println("seen:" + value));
                    Optional.<String>empty().ifPresent(value -> System.out.println("seen:" + value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-if-present-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("seen:value\n");
    }

    @Test
    void optionalIfPresentConcreteConsumerBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("optional-if-present-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Optional;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    Optional.of("value").ifPresent(new Printer());
                    Optional.<String>empty().ifPresent(new Printer());
                }
            }
            """);
        writeJava(project, "com.acme.Printer", """
            package com.acme;

            import java.util.function.Consumer;

            public final class Printer implements Consumer<Object> {
                @Override
                public void accept(final Object value) {
                    System.out.println("seen:" + value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/optional-if-present-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("seen:value\n");
    }

    @Test
    void hashMapComputeIfAbsentConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("hashmap-compute-if-absent-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final HashMap<String, String> values = new HashMap<>();
                    System.out.println(values.computeIfAbsent("demo", new Loader()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.function.Function;

            public final class Loader implements Function<String, String> {
                @Override
                public String apply(final String key) {
                    return key + ":value";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/hashmap-compute-if-absent-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo:value\ndemo:value\n");
    }

    @Test
    void linkedHashMapComputeIfAbsentConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-compute-if-absent-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    System.out.println(values.computeIfAbsent("demo", new Loader()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.function.Function;

            public final class Loader implements Function<String, String> {
                @Override
                public String apply(final String key) {
                    return key + ":value";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-compute-if-absent-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo:value\ndemo:value\n");
    }

    @Test
    void treeMapComputeIfAbsentConcreteImplementationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("treemap-compute-if-absent-concrete");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.TreeMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final TreeMap<String, String> values = new TreeMap<>();
                    System.out.println(values.computeIfAbsent("demo", new Loader()));
                    System.out.println(values.get("demo"));
                }
            }
            """);
        writeJava(project, "com.acme.Loader", """
            package com.acme;

            import java.util.function.Function;

            public final class Loader implements Function<String, String> {
                @Override
                public String apply(final String key) {
                    return key + ":value";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/treemap-compute-if-absent-concrete").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo:value\ndemo:value\n");
    }

    @Test
    void linkedHashMapComputeIfAbsentCapturedLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("linkedhashmap-compute-if-absent-captured-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String suffix = "-value";
                    final LinkedHashMap<String, String> values = new LinkedHashMap<>();
                    System.out.println(values.computeIfAbsent("demo", key -> key + suffix));
                    System.out.println(values.computeIfAbsent("demo", key -> "other"));
                    System.out.println(values.get("demo"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/linkedhashmap-compute-if-absent-captured-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo-value\ndemo-value\ndemo-value\n");
    }

    @Test
    void treeMapComputeIfAbsentCapturedLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("treemap-compute-if-absent-captured-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.TreeMap;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final String suffix = "-value";
                    final TreeMap<String, String> values = new TreeMap<>();
                    System.out.println(values.computeIfAbsent("demo", key -> key + suffix));
                    System.out.println(values.computeIfAbsent("demo", key -> "other"));
                    System.out.println(values.get("demo"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/treemap-compute-if-absent-captured-lambda").toString())).stdout())
            .isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo-value\ndemo-value\ndemo-value\n");
    }

    @Test
    void mapComputeMissingKeyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.compute("demo", (key, value) -> key + ":" + value));
                    System.out.println(values.get("demo"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-compute-missing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo:null\ndemo:null\n1\n");
    }

    @Test
    void mapComputeNullExistingValueBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-null-existing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("demo", null);
                    System.out.println(values.compute("demo", (key, value) -> key + ":" + value));
                    System.out.println(values.get("demo"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-compute-null-existing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("demo:null\ndemo:null\n");
    }

    @Test
    void mapComputeNullRemapRemovesExistingEntryBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-null-remap-existing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    values.put("demo", "value");
                    System.out.println(values.compute("demo", (key, value) -> null));
                    System.out.println(values.containsKey("demo"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-compute-null-remap-existing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("null\nfalse\n0\n");
    }

    @Test
    void mapComputeNullRemapLeavesMissingKeyAbsentBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-compute-null-remap-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.HashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new HashMap<>();
                    System.out.println(values.compute("demo", (key, value) -> null));
                    System.out.println(values.containsKey("demo"));
                    System.out.println(values.size());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-compute-null-remap-missing").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("null\nfalse\n0\n");
    }

    @Test
    void mapForEachLambdaBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("map-foreach-lambda");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.LinkedHashMap;
            import java.util.Map;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Map<String, String> values = new LinkedHashMap<>();
                    values.put("left", "one");
                    values.put("right", "two");
                    final String prefix = "item:";
                    values.forEach((key, value) -> System.out.println(prefix + key + "=" + value));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/map-foreach-lambda").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("item:left=one\nitem:right=two\n");
    }

    @Test
    void listOfImmutableAddFailsAtRuntime() throws Exception {
        final Path project = project("list-of-immutable-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.List;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final List<String> values = List.of("left");
                    values.add("right");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/list-of-immutable-add").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("unsupported operation on immutable list");
    }

    @Test
    void collectionsUnmodifiableSetReflectsBackingMutationBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collections-unmodifiable-set-live-view");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.LinkedHashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final LinkedHashSet<String> base = new LinkedHashSet<>();
                    base.add("left");
                    final Set<String> view = Collections.unmodifiableSet(base);
                    base.add("right");
                    System.out.println(view.size());
                    System.out.println(view.contains("right"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collections-unmodifiable-set-live-view").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("2\ntrue\n");
    }

    @Test
    void collectionsUnmodifiableSetAddFailsAtRuntime() throws Exception {
        final Path project = project("collections-unmodifiable-set-add");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.Collections;
            import java.util.HashSet;
            import java.util.Set;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Set<String> values = Collections.unmodifiableSet(new HashSet<>());
                    values.add("right");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString());
        assertThat(run.exitCode()).as(run.stderr()).isZero();

        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/collections-unmodifiable-set-add").toString()));

        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("unsupported operation on immutable list");
    }

    @Test
    void boxedIntegerUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-integer-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Integer value = Integer.valueOf(7);
                    System.out.println(value.intValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-integer-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedBooleanUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-boolean-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Boolean value = Boolean.valueOf(true);
                    System.out.println(value.booleanValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-boolean-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedByteUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-byte-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Byte value = Byte.valueOf((byte) 7);
                    System.out.println(value.byteValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-byte-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedShortUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-short-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Short value = Short.valueOf((short) 12);
                    System.out.println(value.shortValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-short-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedCharacterUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-character-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Character value = Character.valueOf('A');
                    System.out.println(value.charValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-character-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedLongUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-long-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Long value = Long.valueOf(7L);
                    System.out.println(value.longValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-long-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedFloatUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-float-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Float value = Float.valueOf(1.5f);
                    System.out.println(value.floatValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-float-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedDoubleUnboxBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-double-unbox");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Double value = Double.valueOf(1.5d);
                    System.out.println(value.doubleValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-double-unbox").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedIntegerInstanceOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-integer-instanceof");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = Integer.valueOf(3);
                    System.out.println(value instanceof Integer);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-integer-instanceof").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedByteInstanceOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-byte-instanceof");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = Byte.valueOf((byte) 3);
                    System.out.println(value instanceof Byte);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-byte-instanceof").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedShortInstanceOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-short-instanceof");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = Short.valueOf((short) 3);
                    System.out.println(value instanceof Short);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-short-instanceof").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void boxedCharacterInstanceOfBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("boxed-character-instanceof");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Object value = Character.valueOf('A');
                    System.out.println(value instanceof Character);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/boxed-character-instanceof").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void collectionIsEmptyBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("collection-is-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import java.util.ArrayList;
            import java.util.Collection;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final ArrayList<String> values = new ArrayList<>();
                    final Collection<String> view = values;
                    System.out.println(view.isEmpty());
                    values.add("x");
                    System.out.println(view.isEmpty());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/collection-is-empty").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intShiftLeftBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-shift-left");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = 1;
                    int shift = 33;
                    System.out.println(value << shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-shift-left").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longShiftLeftBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-shift-left");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = 1L;
                    int shift = 65;
                    System.out.println(value << shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-shift-left").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intSignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-signed-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = -8;
                    int shift = 1;
                    System.out.println(value >> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-signed-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longSignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-signed-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = -8L;
                    int shift = 1;
                    System.out.println(value >> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-signed-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void intUnsignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("int-unsigned-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    int value = -1;
                    int shift = 1;
                    System.out.println(value >>> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/int-unsigned-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void longUnsignedShiftRightBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("long-unsigned-shift-right");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    long value = -1L;
                    int shift = 1;
                    System.out.println(value >>> shift);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/long-unsigned-shift-right").toString())).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void polymorphicSuperclassVirtualCallBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("polymorphic-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Base value = new Child();
                    System.out.println(value.text());
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                public Base() {
                }

                public String text() {
                    return "base";
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base {
                public Child() {
                }

                public String text() {
                    return "child";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/polymorphic-call").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("child\n");
    }

    @Test
    void inheritedMethodThroughFinalSubclassBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("final-subclass-inherited-method");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main extends Base {
                public Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(new Main().value());
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                public Base() {
                }

                public int value() {
                    return 42;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());
        if (run.exitCode() != 0) {
            throw new AssertionError(run.stderr());
        }

        assertThat(process(
            project,
            List.of(project.resolve(".javan/bin/final-subclass-inherited-method").toString())
        ).stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void singleImplementationInterfaceDispatchBuilds() throws Exception {
        final Path project = project("interface-dispatch");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new EnglishGreeter("Ada");
                    System.out.println(greeter.greet());
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                String greet();
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                private final String name;

                public EnglishGreeter(final String name) {
                    this.name = name;
                }

                public String greet() {
                    return name;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-dispatch").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("Ada\n");
    }

    @Test
    void multiImplementationInterfaceDispatchBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("interface-ambiguous");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Greeter greeter = new EnglishGreeter();
                    System.out.println(greeter.greet());
                }
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;

            public interface Greeter {
                String greet();
            }
            """);
        writeJava(project, "com.acme.EnglishGreeter", """
            package com.acme;

            public final class EnglishGreeter implements Greeter {
                public EnglishGreeter() {
                }

                public String greet() {
                    return "hello";
                }
            }
            """);
        writeJava(project, "com.acme.GermanGreeter", """
            package com.acme;

            public final class GermanGreeter implements Greeter {
                public GermanGreeter() {
                }

                public String greet() {
                    return "hallo";
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/interface-ambiguous").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("hello\n");
    }

    @Test
    void objectCloneBuildsAndMatchesJvmOutput() throws Exception {
        final Path project = project("object-clone");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    final Box box = new Box(7, "left");
                    final Box copy = box.copy();
                    box.count = 9;
                    box.label = "right";
                    System.out.println(copy.count);
                    System.out.println(copy.label);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box implements Cloneable {
                int count;
                String label;

                Box(final int count, final String label) {
                    this.count = count;
                    this.label = label;
                }

                Box copy() throws CloneNotSupportedException {
                    return (Box) super.clone();
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).as(run.stderr()).isZero();
        final Path nativeBinary = project.resolve(".javan/bin/object-clone");
        final ProcessResult nativeRun = process(project, List.of(nativeBinary.toString()));

        assertThat(nativeRun.stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("7\nleft\n");
    }

    @Test
    void covariantObjectCloneOverrideAndBridgeMatchJvmOutput() throws Exception {
        final Path project = project("object-clone-covariant-bridge");
        writeJava(project, "com.acme.Copyable", """
            package com.acme;

            public interface Copyable {
                Object clone() throws CloneNotSupportedException;
            }
            """);
        writeJava(project, "com.acme.Payload", """
            package com.acme;

            public final class Payload {
                int value;

                Payload(final int value) {
                    this.value = value;
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box implements Cloneable, Copyable {
                int value;
                Payload payload;

                Box(final int value, final Payload payload) {
                    this.value = value;
                    this.payload = payload;
                }

                @Override
                public Box clone() throws CloneNotSupportedException {
                    return (Box) super.clone();
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    final Payload payload = new Payload(7);
                    final Box source = new Box(8, payload);
                    final Box direct = source.clone();
                    final Copyable erased = source;
                    final Box bridged = (Box) erased.clone();

                    source.value = 9;
                    payload.value = 10;

                    System.out.println(source != direct);
                    System.out.println(source != bridged);
                    System.out.println(direct != bridged);
                    System.out.println(direct.value);
                    System.out.println(bridged.value);
                    System.out.println(direct.payload == source.payload);
                    System.out.println(bridged.payload == source.payload);
                    System.out.println(direct.payload.value);
                    System.out.println(bridged.payload.value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/object-clone-covariant-bridge").toString())
        );
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void objectCloneDeclaredByNonCloneableBaseWorksForCloneableSubtype() throws Exception {
        final Path project = project("object-clone-runtime-subtype");
        writeJava(project, "com.acme.Payload", """
            package com.acme;

            public final class Payload {
                int value;

                Payload(final int value) {
                    this.value = value;
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base {
                int baseValue;
                Payload payload;

                Base(final int baseValue, final Payload payload) {
                    this.baseValue = baseValue;
                    this.payload = payload;
                }

                Base copy() throws CloneNotSupportedException {
                    return (Base) super.clone();
                }
            }
            """);
        writeJava(project, "com.acme.Child", """
            package com.acme;

            public final class Child extends Base implements Cloneable {
                int childValue;

                Child(final int baseValue, final int childValue, final Payload payload) {
                    super(baseValue, payload);
                    this.childValue = childValue;
                }

                Child copyChild() throws CloneNotSupportedException {
                    return (Child) super.copy();
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    final Payload payload = new Payload(7);
                    final Child source = new Child(8, 9, payload);
                    final Child copy = source.copyChild();

                    source.baseValue = 10;
                    source.childValue = 11;
                    payload.value = 12;

                    System.out.println(source != copy);
                    System.out.println(copy instanceof Child);
                    System.out.println(copy.baseValue);
                    System.out.println(copy.childValue);
                    System.out.println(copy.payload == source.payload);
                    System.out.println(copy.payload.value);
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/object-clone-runtime-subtype").toString())
        );
        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void inheritedObjectCloneCopiesCompleteLayoutAndSurvivesGcStress() throws Exception {
        final Path project = project("object-clone-inheritance");
        writeJava(project, "com.acme.CopyMarker", """
            package com.acme;

            public interface CopyMarker extends Cloneable {
            }
            """);
        writeJava(project, "com.acme.Payload", """
            package com.acme;

            public final class Payload {
                int value;

                Payload(final int value) {
                    this.value = value;
                }
            }
            """);
        writeJava(project, "com.acme.Base", """
            package com.acme;

            public class Base implements CopyMarker {
                static int constructions;
                boolean flag;
                byte byteValue;
                char charValue;
                short shortValue;
                int hidden;
                long longValue;
                float floatValue;
                double doubleValue;
                Payload payload;
                int[] numbers;
                Object nullable;

                Base(final Payload payload, final int[] numbers) {
                    constructions++;
                    flag = true;
                    byteValue = 2;
                    charValue = 'A';
                    shortValue = 3;
                    hidden = 11;
                    longValue = 4L;
                    floatValue = 5.5f;
                    doubleValue = 6.25d;
                    this.payload = payload;
                    this.numbers = numbers;
                    nullable = null;
                }

                static int hidden(final Base value) {
                    return value.hidden;
                }

                static void hidden(final Base value, final int hidden) {
                    value.hidden = hidden;
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box extends Base {
                int hidden;
                String label;

                Box(final Payload payload, final int[] numbers) {
                    super(payload, numbers);
                    hidden = 22;
                    label = "left";
                }

                Box copy() throws CloneNotSupportedException {
                    return (Box) super.clone();
                }
            }
            """);
        writeJava(project, "com.acme.Empty", """
            package com.acme;

            public final class Empty implements Cloneable {
                Empty copy() throws CloneNotSupportedException {
                    return (Empty) super.clone();
                }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    final Payload payload = new Payload(7);
                    final int[] numbers = new int[]{8};
                    final Box source = new Box(payload, numbers);
                    final Box copy = source.copy();
                    final Box second = copy.copy();
                    final Empty empty = new Empty();
                    final Empty emptyCopy = empty.copy();
                    final Box current = churn();

                    source.flag = false;
                    source.byteValue = 9;
                    source.charValue = 'Z';
                    source.shortValue = 10;
                    Base.hidden(source, 33);
                    source.hidden = 44;
                    source.longValue = 12L;
                    source.floatValue = 13.5f;
                    source.doubleValue = 14.25d;
                    source.label = "right";
                    payload.value = 70;
                    numbers[0] = 80;

                    System.out.println(source != copy);
                    System.out.println(copy != second);
                    System.out.println(copy instanceof Box);
                    System.out.println(copy.flag);
                    System.out.println(copy.byteValue);
                    System.out.println(copy.charValue);
                    System.out.println(copy.shortValue);
                    System.out.println(Base.hidden(copy));
                    System.out.println(copy.hidden);
                    System.out.println(copy.longValue);
                    System.out.println(copy.floatValue);
                    System.out.println(copy.doubleValue);
                    System.out.println(copy.label);
                    System.out.println(copy.nullable == null);
                    System.out.println(copy.payload == source.payload);
                    System.out.println(copy.payload.value);
                    System.out.println(copy.numbers == source.numbers);
                    System.out.println(copy.numbers[0]);
                    System.out.println(Base.hidden(second));
                    System.out.println(second.hidden);
                    System.out.println(Base.hidden(current));
                    System.out.println(current.hidden);
                    System.out.println(current.payload.value);
                    System.out.println(current.numbers[0]);
                    System.out.println(Base.constructions);
                    System.out.println(empty != emptyCopy);
                }

                private static Box churn() throws CloneNotSupportedException {
                    Box current = new Box(new Payload(71), new int[]{81});
                    for (int index = 0; index < 4_000; index++) {
                        current = current.copy();
                    }
                    return current;
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path nativeBinary = project.resolve(".javan/bin/object-clone-inheritance");
        final ProcessResult nativeRun = process(project, List.of(nativeBinary.toString()));
        final ProcessResult stressRun = process(
            project,
            List.of(nativeBinary.toString()),
            Duration.ofSeconds(10),
            Map.of(
                "JAVAN_HEAP_LIMIT_BYTES", "65536",
                "JAVAN_GC_STRESS", "1"
            )
        );

        assertThat(nativeRun.exitCode()).as(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout()).isEqualTo(jvmOutput);
        assertThat(stressRun.exitCode()).as(stressRun.stderr()).isZero();
        assertThat(stressRun.stdout()).isEqualTo(jvmOutput);
    }

    @Test
    void objectCloneRejectsNonCloneableClassDuringCheck() throws Exception {
        final Path project = project("object-clone-non-cloneable");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    System.out.println(new Box().copy());
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box {
                Box copy() throws CloneNotSupportedException {
                    return (Box) super.clone();
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(check.exitCode()).isEqualTo(2);
        assertThat(check.stderr()).contains(
            "error[JAVAN050]: Object.clone requires a supported Cloneable class",
            "java/lang/Object.clone()Ljava/lang/Object;"
        );
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }

    @Test
    void nullGeneratedObjectCloneBuildsAndFailsClearly() throws Exception {
        final Path project = project("object-clone-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    Box.copy(null);
                }
            }
            """);
        writeJava(project, "com.acme.Box", """
            package com.acme;

            public final class Box implements Cloneable {
                static Box copy(final Box value) throws CloneNotSupportedException {
                    return (Box) value.clone();
                }
            }
            """);

        final CliRun build = run(tempDir, "build", project.toString());

        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/object-clone-null").toString())
        );
        assertThat(nativeRun.exitCode()).isEqualTo(1);
        assertThat(nativeRun.stderr()).contains("null object clone");
    }

    @Test
    void objectCloneRejectsRuntimeBackedSuperclassDuringCheck() throws Exception {
        final Path project = project("object-clone-runtime-backed-superclass");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws CloneNotSupportedException {
                    System.out.println(new CloneableScheduler(1).copy() != null);
                }
            }
            """);
        writeJava(project, "com.acme.CloneableScheduler", """
            package com.acme;

            import java.util.concurrent.ScheduledThreadPoolExecutor;

            public final class CloneableScheduler extends ScheduledThreadPoolExecutor implements Cloneable {
                CloneableScheduler(final int corePoolSize) {
                    super(corePoolSize);
                }

                CloneableScheduler copy() throws CloneNotSupportedException {
                    return (CloneableScheduler) super.clone();
                }
            }
            """);

        final CliRun check = run(tempDir, "check", project.toString());

        assertThat(check.exitCode()).isEqualTo(2);
        assertThat(check.stderr()).contains(
            "error[JAVAN050]: Object.clone requires a supported Cloneable class",
            "external superclass"
        );
        assertThat(project.resolve(".javan/generated")).doesNotExist();
    }
}
