package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryArrayFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void infersKeysFromAnEntryArrayStoredInALocal() throws Exception {
        final Path project = project("map-entry-array-local");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { hashes++; return value; }
                    public boolean equals(Object x) { return x instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    Map.Entry<Key, String>[] entries = new Map.Entry[] {
                        Map.entry(new Key(1), "one"), Map.entry(new Key(2), "two")
                    };
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-local").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\n");
    }

    @Test
    void infersKeysAfterUpdatingAnArrayThroughALocalAlias() throws Exception {
        final Path project = project("map-entry-array-alias");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { hashes++; return value; }
                    public boolean equals(Object x) { return x instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    Map.Entry<Key, String>[] entries = new Map.Entry[] {
                        Map.entry(new Key(1), "one"), Map.entry(new Key(2), "two")
                    };
                    Map.Entry<Key, String>[] alias = entries;
                    alias[1] = Map.entry(new Key(3), "three");
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-alias").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\n");
    }
    @Test
    void invalidatesTheArrayFactWhenAnApplicationMethodCanMutateIt() throws Exception {
        final Path project = project("map-entry-array-escapes");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Unknown {
                    public int hashCode() { System.setProperty("uncompiled", "never"); return 1; }
                }
                static void replace(Map.Entry<Object, String>[] entries) {
                    entries[0] = Map.entry(new Unknown(), "replacement");
                }
                public static void main(String[] args) {
                    Map.Entry<Object, String>[] entries = new Map.Entry[] {
                        Map.entry("first", "one"), Map.entry("second", "two")
                    };
                    Map.Entry<Object, String>[] alias = entries;
                    replace(alias);
                    try { Map.ofEntries(entries); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-escapes").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported\n");
    }

    @Test
    void invalidatesTheArrayFactAcrossAConditionalMutation() throws Exception {
        final Path project = project("map-entry-array-conditional");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Unknown {
                    public int hashCode() { System.setProperty("uncompiled", "never"); return 1; }
                }
                public static void main(String[] args) {
                    Map.Entry<Object, String>[] entries = new Map.Entry[] {
                        Map.entry("first", "one"), Map.entry("second", "two")
                    };
                    Map.Entry<Object, String>[] alias = entries;
                    if (args.length == 0) alias[0] = Map.entry(new Unknown(), "replacement");
                    try { Map.ofEntries(entries); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-conditional").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported\n");
    }

    @Test
    void keepsSimultaneousLocalEntryArraysSeparate() throws Exception {
        final Path project = project("map-entry-distinct-local-arrays");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class A { public int hashCode() { hashes++; return 1; } }
                static final class B { public int hashCode() { hashes++; return 2; } }
                static final class Unused { public int hashCode() { System.setProperty("unused", "never"); return 3; } }
                public static void main(String[] args) {
                    Map.Entry<Object, String>[] first = new Map.Entry[] {
                        Map.entry(new A(), "a"), Map.entry("one", "one")
                    };
                    Map.Entry<Object, String>[] second = new Map.Entry[] {
                        Map.entry(new B(), "b"), Map.entry("two", "two")
                    };
                    Map.Entry<Object, String>[] unused = new Map.Entry[] {
                        Map.entry(new Unused(), "unused"), Map.entry("three", "three")
                    };
                    var firstMap = Map.ofEntries(first);
                    var secondMap = Map.ofEntries(second);
                    System.out.println(firstMap.size());
                    System.out.println(secondMap.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-distinct-local-arrays").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\n2\n");
    }

}
