package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryFieldReadFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void preservesLocalEntryArrayFactsAcrossAnUnrelatedInstanceFieldRead() throws Exception {
        final Path project = project("map-entry-array-field-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Holder { int value = 42; }
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { hashes++; return value; }
                    public boolean equals(Object x) { return x instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    Holder holder = new Holder();
                    Map.Entry<Key, String>[] entries = new Map.Entry[] {
                        Map.entry(new Key(1), "one"), Map.entry(new Key(2), "two")
                    };
                    int observed = holder.value;
                    var result = Map.ofEntries(entries);
                    System.out.println(observed);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-field-read").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("42\n2\n2\n");
    }

    @Test
    void invalidatesArrayFactsWhenTheArrayIsStoredInAnInstanceField() throws Exception {
        final Path project = project("map-entry-array-field-escape");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Holder { Map.Entry<Object, String>[] entries; }
                static final class Unknown {
                    public int hashCode() { System.setProperty("uncompiled", "never"); return 1; }
                }
                public static void main(String[] args) {
                    Holder holder = new Holder();
                    Map.Entry<Object, String>[] entries = new Map.Entry[] {
                        Map.entry("first", "one"), Map.entry("second", "two")
                    };
                    holder.entries = entries;
                    holder.entries[0] = Map.entry(new Unknown(), "replacement");
                    try { Map.ofEntries(entries); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-field-escape").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported\n");
    }
}
