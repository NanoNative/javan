package javan;

import javan.util.Json;
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
final class CliRuntimeTranslationIntegrationTest extends CliIntegrationSupport {
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

}
