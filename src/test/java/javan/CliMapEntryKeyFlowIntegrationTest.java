package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryKeyFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void infersEachKeyFromAnInlineMapWithFiveDistinctKeyTypes() throws Exception {
        final Path project = project("map-entries-five-key-types");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class A { public int hashCode() { hashes++; return 1; } }
                static final class B { public int hashCode() { hashes++; return 2; } }
                static final class C { public int hashCode() { hashes++; return 3; } }
                static final class D { public int hashCode() { hashes++; return 4; } }
                static final class E { public int hashCode() { hashes++; return 5; } }
                static final class Unused { public int hashCode() { System.setProperty("unused", "never"); return 6; } }
                public static void main(String[] args) {
                    var unused = Map.entry(new Unused(), "unused");
                    var result = Map.ofEntries(Map.entry(new A(), "a"), Map.entry(new B(), "b"),
                        Map.entry(new C(), "c"), Map.entry(new D(), "d"), Map.entry(new E(), "e"));
                    System.out.println(result.size());
                    System.out.println(hashes);
                    System.out.println(unused.getValue());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entries-five-key-types").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("5\n5\nunused\n");
    }
    @Test
    void invokesEqualityForDistinctKnownKeyTypesWithHashCollisions() throws Exception {
        final Path project = project("map-entries-diverse-colliding-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int comparisons;
                static final class A { public int hashCode() { return 1; } public boolean equals(Object x) { comparisons++; return x instanceof A; } }
                static final class B { public int hashCode() { return 1; } public boolean equals(Object x) { comparisons++; return x instanceof B; } }
                static final class C { public int hashCode() { return 1; } public boolean equals(Object x) { comparisons++; return x instanceof C; } }
                static final class D { public int hashCode() { return 1; } public boolean equals(Object x) { comparisons++; return x instanceof D; } }
                static final class E { public int hashCode() { return 1; } public boolean equals(Object x) { comparisons++; return x instanceof E; } }
                public static void main(String[] args) {
                    var result = Map.ofEntries(Map.entry(new A(), "a"), Map.entry(new B(), "b"),
                        Map.entry(new C(), "c"), Map.entry(new D(), "d"), Map.entry(new E(), "e"));
                    System.out.println(result.size());
                    System.out.println(comparisons);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entries-diverse-colliding-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("5\n10\n");
    }

    @Test
    void infersKeysForALargerInlineEntryArray() throws Exception {
        final Path project = project("map-entries-sixteen-key-types");
        final StringBuilder types = new StringBuilder();
        final StringBuilder entries = new StringBuilder();
        for (int index = 0; index < 16; index++) {
            types.append("static final class K").append(index)
                .append(" { public int hashCode() { hashes++; return ").append(index + 1).append("; } }\n");
            if (index != 0) entries.append(", ");
            entries.append("Map.entry(new K").append(index).append("(), \"value\")");
        }
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                %s
                public static void main(String[] args) {
                    var result = Map.ofEntries(%s);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """.formatted(types, entries));
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entries-sixteen-key-types").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("16\n16\n");
    }

    @Test
    void preservesKnownKeyCallbacksBesideAnUnknownEntryKey() throws Exception {
        final Path project = project("map-entries-mixed-known-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Key { public int hashCode() { hashes++; return 1; } }
                public static void main(String[] args) {
                    String[] values = {"external"};
                    var result = Map.ofEntries(Map.entry(values[0], "a"), Map.entry(new Key(), "b"));
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entries-mixed-known-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n1\n");
    }

}
