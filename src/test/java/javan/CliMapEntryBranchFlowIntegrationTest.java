package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryBranchFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void preservesUnchangedEntryArraysAcrossABranch() throws Exception {
        final Path project = project("map-entry-array-branch");
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
                    if (args.length == 0) System.out.println("empty");
                    else System.out.println("arguments");
                    var result = Map.ofEntries(entries);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-branch").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("empty\n2\n2\n");
        final ProcessResult jvmWithArgument = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            project.resolve("jvm-classes").toString(), "com.acme.Main", "argument"));
        assertThat(jvmWithArgument.exitCode()).as(jvmWithArgument.stderr()).isZero();
        final ProcessResult nativeWithArgument = process(project, List.of(
            project.resolve(".javan/bin/map-entry-array-branch").toString(), "argument"));
        assertThat(nativeWithArgument.exitCode()).as(nativeWithArgument.stderr()).isZero();
        assertThat(nativeWithArgument.stdout()).isEqualTo(jvmWithArgument.stdout()).isEqualTo("arguments\n2\n2\n");
    }

    @Test
    void preservesAnUnchangedEntryArrayAcrossALoop() throws Exception {
        final Path project = project("map-entry-array-loop");
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
                    int count = 0;
                    while (count < 2) count++;
                    var result = Map.ofEntries(entries);
                    System.out.println(count);
                    System.out.println(result.size());
                    System.out.println(hashes);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-array-loop").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\n2\n");
    }
}
