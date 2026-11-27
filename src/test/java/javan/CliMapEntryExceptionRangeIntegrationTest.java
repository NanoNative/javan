package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapEntryExceptionRangeIntegrationTest extends CliIntegrationSupport {
    @Test
    void catchesInlineEntryFactoryFailureWithConstructorsInsideProtectedRange() throws Exception {
        final Path project = project("map-entry-inline-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    var known = new HashMap<Key, String>(); known.put(new Key(), "known");
                    try { Map.ofEntries(Map.entry(new Key(), "one"), Map.entry(new Key(), "two")); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-inline-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n");
    }
    @Test
    void catchesInlineStringEntryDuplicate() throws Exception {
        final Path project = project("map-entry-inline-strings-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                public static void main(String[] args) {
                    try { Map.ofEntries(Map.entry("same", "one"), Map.entry("same", "two")); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-inline-strings-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n");
    }

    @Test
    void catchesApplicationConstructorFailureInsideProtectedRange() throws Exception {
        final Path project = project("application-constructor-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Value {
                    Value() { throw new IllegalArgumentException("constructor"); }
                }
                public static void main(String[] args) {
                    try { new Value(); }
                    catch (IllegalArgumentException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/application-constructor-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("constructor\n");
    }

    @Test
    void catchesNullEntryBeforeTheMapFactory() throws Exception {
        final Path project = project("map-entry-inline-null-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                public static void main(String[] args) {
                    try { Map.ofEntries(Map.entry(null, "one"), Map.entry("two", "two")); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-inline-null-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\n");
    }

    @Test
    void catchesConstructorFailureDuringInlineEntryCreation() throws Exception {
        final Path project = project("map-entry-inline-constructor-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    Key() { throw new IllegalArgumentException("constructor"); }
                }
                public static void main(String[] args) {
                    try { Map.ofEntries(Map.entry(new Key(), "one"), Map.entry(new Key(), "two")); }
                    catch (IllegalArgumentException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-inline-constructor-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("constructor\n");
    }

    @Test
    void rejectsUnprovenArrayAccessInsideProtectedRange() throws Exception {
        final Path project = project("unproven-array-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    int[] values = {1};
                    try { System.out.println(values[2]); }
                    catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                }
            }
            """);
        assertThat(runJvm(project, "com.acme.Main")).isEqualTo("bounds\n");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).isNotZero();
        assertThat(build.stderr()).contains("JAVAN014");
    }

    @Test
    void catchesKeyCallbackFailureDuringInlineMapCreation() throws Exception {
        final Path project = project("map-entry-inline-hash-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static boolean fail;
                static final class Key {
                    public int hashCode() { if (fail) throw new IllegalStateException("hash"); return 1; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    var known = new HashMap<Key, String>(); known.put(new Key(), "known");
                    fail = true;
                    try { Map.ofEntries(Map.entry(new Key(), "one"), Map.entry(new Key(), "two")); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-inline-hash-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\n");
    }

    @Test
    void catchesDuplicateInlineApplicationKeysWithoutAnotherMapUse() throws Exception {
        final Path project = project("map-entry-inline-inferred-handler");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    try { Map.ofEntries(Map.entry(new Key(), "one"), Map.entry(new Key(), "two")); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-entry-inline-inferred-handler").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n");
    }

}
