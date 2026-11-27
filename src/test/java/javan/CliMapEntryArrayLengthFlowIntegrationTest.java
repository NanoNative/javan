package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryArrayLengthFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void preservesKeyFactsWhenReadingTheEntryArrayLength() throws Exception {
        final Path project = project("map-entry-array-length");
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
                    int length = entries.length;
                    var result = Map.ofEntries(entries);
                    System.out.println(length);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-length").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\n2\n");
    }
}
