package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImmutableCustomMapEntriesIntegrationTest extends CliIntegrationSupport {
    @Test
    void mapOfEntriesUsesReachableCustomKeyCallbacks() throws Exception {
        final Path project = project("map-ofentries-custom-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var index = new HashMap<Key, String>(); index.put(new Key(1), "indexed");
                    var first = Map.entry(new Key(1), "one"); var second = Map.entry(new Key(2), "two");
                    var third = Map.entry(new Key(3), "three");
                    var map = Map.ofEntries(first, second, third);
                    System.out.println(index.get(new Key(1)));
                    System.out.println(map.get(new Key(2)));
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-custom-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("indexed\ntwo\n3\n");
    }

    @Test
    void mapOfEntriesDetectsDuplicateCustomKeys() throws Exception {
        final Path project = project("map-ofentries-custom-duplicate");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var index = new HashMap<Key, String>(); index.put(new Key(1), "indexed");
                    var first = Map.entry(new Key(1), "one"); var second = Map.entry(new Key(2), "two");
                    var duplicate = Map.entry(new Key(1), "again");
                    Map.Entry<Key, String>[] entries = new Map.Entry[] { first, second, duplicate };
                    try { Map.ofEntries(entries); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                    System.out.println(index.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-custom-duplicate").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n1\n");
    }

    @Test
    void mapOfEntriesChecksAllEntrySlotsBeforeCallingHashCode() throws Exception {
        final Path project = project("map-ofentries-null-slot-order");
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
                    var index = new HashMap<Key, String>(); index.put(new Key(), "indexed");
                    var first = Map.entry(new Key(), "one");
                    Map.Entry<Key, String>[] entries = new Map.Entry[] { first, null };
                    fail = true;
                    try { Map.ofEntries(entries); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                    catch (IllegalStateException expected) { System.out.println("hash"); }
                    var second = Map.entry(new Key(), "two");
                    Map.Entry<Key, String>[] valid = new Map.Entry[] { first, second };
                    try { Map.ofEntries(valid); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-null-slot-order").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nhash\n");
    }

    @Test
    void mapOfEntriesReleasesLockAndKeepsEntriesAliveDuringConcurrentGc() throws Exception {
        final Path project = project("map-ofentries-custom-keys-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static boolean wait;
                static final class Worker implements Runnable {
                    public void run() { while (signal.get() == 0) Thread.yield(); completed.set(1); }
                }
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() {
                        if (wait && value == 1) {
                            signal.set(1);
                            for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                            if (completed.get() == 0) throw new IllegalStateException("locked");
                        }
                        return 1;
                    }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                static final class Value { final int value; Value(int value) { this.value = value; } }
                public static void main(String[] args) throws Exception {
                    var index = new HashMap<Key, Value>(); index.put(new Key(1), new Value(11));
                    var first = Map.entry(new Key(1), new Value(11)); var second = Map.entry(new Key(2), new Value(22));
                    Map.Entry<Key, Value>[] entries = new Map.Entry[] { first, second };
                    Thread worker = new Thread(new Worker()); worker.start(); wait = true;
                    var map = Map.ofEntries(entries); worker.join(); index.clear();
                    entries[0] = null; entries[1] = null;
                    System.out.println(map.get(new Key(1)).value);
                    System.out.println(map.get(new Key(2)).value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-custom-keys-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("11\n22\n");
    }

    @Test
    void singletonMapOfEntriesDoesNotRequireUnusedKeyCallbacks() throws Exception {
        final Path project = project("map-ofentries-single-uncompiled-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    public int hashCode() { System.setProperty("unused", "never"); return 1; }
                }
                public static void main(String[] args) {
                    var entry = Map.entry(new Key(), "one");
                    var map = Map.ofEntries(entry);
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-single-uncompiled-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("1\n");
    }
    @Test
    void mapOfEntriesInfersOnlyKeysUsedInInlineEntries() throws Exception {
        final Path project = project("map-ofentries-inline-key-flow");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int calls;
                static final class First {
                    public int hashCode() { calls++; return 1; }
                    public boolean equals(Object other) { return other instanceof First; }
                }
                static final class Second {
                    public int hashCode() { calls++; return 2; }
                    public boolean equals(Object other) { return other instanceof Second; }
                }
                static final class Unused {
                    public int hashCode() { System.setProperty("unused", "never"); return 3; }
                }
                public static void main(String[] args) {
                    var unrelated = Map.entry(new Unused(), "unused");
                    var map = Map.ofEntries(Map.entry(new First(), "one"), Map.entry(new Second(), "two"));
                    System.out.println(map.size());
                    System.out.println(calls);
                    System.out.println(unrelated.getValue());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-inline-key-flow").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n2\nunused\n");
    }

    @Test
    void inlineMapOfEntriesPropagatesKeyCallbackFailuresAndDuplicates() throws Exception {
        final Path project = project("map-ofentries-inline-key-failures");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static boolean fail;
                static final class Key {
                    public int hashCode() { if (fail) throw new IllegalStateException("hash"); return 1; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                static void build() { Map.ofEntries(Map.entry(new Key(), "one"), Map.entry(new Key(), "two")); }
                public static void main(String[] args) {
                    try { build(); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                    fail = true;
                    try { build(); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-ofentries-inline-key-failures").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\nhash\n");
    }

}
