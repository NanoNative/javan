package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryOverwriteFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void doesNotRetainAKeyDefinitelyOverwrittenBeforeMapCreation() throws Exception {
        final Path project = project("map-entry-overwritten-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Unused {
                    public int hashCode() { System.setProperty("unused", "never"); return 1; }
                }
                public static void main(String[] args) {
                    Map.Entry<Object, String>[] entries = new Map.Entry[] {
                        Map.entry(new Unused(), "old"), Map.entry("other", "two")
                    };
                    Map.Entry<Object, String>[] alias = entries;
                    alias[0] = Map.entry("replacement", "one");
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-overwritten-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n");
    }
    @Test
    void keepsOnlyTheLatestKeyForEachProvenIndex() throws Exception {
        final Path project = project("map-entry-repeated-overwrites");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class OldA { public int hashCode() { System.setProperty("unusedA", "never"); return 1; } }
                static final class OldB { public int hashCode() { System.setProperty("unusedB", "never"); return 2; } }
                static final class Key { public int hashCode() { hashes++; return 3; } }
                public static void main(String[] args) {
                    Map.Entry<Object, String>[] entries = new Map.Entry[] {
                        Map.entry(new OldA(), "oldA"), Map.entry(new OldB(), "oldB"), Map.entry("third", "three")
                    };
                    entries[0] = Map.entry("temporary", "temp");
                    entries[1] = Map.entry("second", "two");
                    entries[0] = Map.entry(new Key(), "one");
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-repeated-overwrites").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("3\n1\n");
    }

    @Test
    void keepsPossibleKeysWhenTheOverwriteIndexIsUnknown() throws Exception {
        final Path project = project("map-entry-unknown-index");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Old { public int hashCode() { hashes++; return 1; } }
                static final class New { public int hashCode() { hashes++; return 2; } }
                public static void main(String[] args) {
                    int index = "one".length() - 2;
                    Map.Entry<Object, String>[] entries = new Map.Entry[] {
                        Map.entry(new Old(), "old"), Map.entry("second", "two")
                    };
                    entries[index] = Map.entry(new New(), "new");
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-unknown-index").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\n");
    }

    @Test
    void discardsAnOverwrittenKeyAtALargeLiteralIndex() throws Exception {
        final Path project = project("map-entry-large-overwrite-index");
        final StringBuilder entries = new StringBuilder();
        for (int index = 0; index <= 130; index++) {
            if (index != 0) entries.append(", ");
            if (index == 130) entries.append("Map.entry(new Unused(), \"old\")");
            else entries.append("Map.entry(\"key").append(index).append("\", \"value\")");
        }
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Unused {
                    public int hashCode() { System.setProperty("unused", "never"); return 1; }
                }
                public static void main(String[] args) {
                    Map.Entry<Object, String>[] entries = new Map.Entry[] { %s };
                    entries[130] = Map.entry("replacement", "new");
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                }
            }
            """.formatted(entries));
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-large-overwrite-index").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("131\n");
    }

}
