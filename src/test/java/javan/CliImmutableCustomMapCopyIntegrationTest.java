package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImmutableCustomMapCopyIntegrationTest extends CliIntegrationSupport {
    @Test
    void mapCopyOfRehashesCustomKeysAndCreatesImmutableSnapshot() throws Exception {
        final Path project = project("map-copyof-custom-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { hashes++; return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var source = new HashMap<Key, String>();
                    source.put(new Key(1), "one"); source.put(new Key(2), "two");
                    int before = hashes;
                    var copy = Map.copyOf(source);
                    System.out.println(hashes - before);
                    source.put(new Key(1), "changed");
                    System.out.println(copy.get(new Key(1)));
                    System.out.println(source.get(new Key(1)));
                    Key third = new Key(3);
                    try { copy.put(third, "three"); }
                    catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-custom-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\none\nchanged\nimmutable\n");
    }

    @Test
    void mapCopyOfReleasesLockAndKeepsSnapshotAliveDuringConcurrentGc() throws Exception {
        final Path project = project("map-copyof-custom-keys-gc");
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
                        return value;
                    }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                static final class Value { final int value; Value(int value) { this.value = value; } }
                public static void main(String[] args) throws Exception {
                    var source = new HashMap<Key, Value>(); source.put(new Key(1), new Value(11)); source.put(new Key(2), new Value(22));
                    Thread worker = new Thread(new Worker()); worker.start(); wait = true;
                    var copy = Map.copyOf(source); worker.join(); source.clear();
                    System.out.println(copy.get(new Key(1)).value);
                    System.out.println(copy.get(new Key(2)).value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-custom-keys-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("11\n22\n");
    }

    @Test
    void mapCopyOfReusesImmutableMapsWithoutRehashing() throws Exception {
        final Path project = project("map-copyof-existing-immutable");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static boolean fail;
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { if (fail) throw new IllegalStateException("unexpected hash"); return value; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var source = Map.of(new Key(1), "one", new Key(2), "two");
                    fail = true;
                    var copy = Map.copyOf(source);
                    System.out.println(copy == source);
                    System.out.println(copy.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-existing-immutable").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n2\n");
    }

    @Test
    void immutableSingletonCopyDoesNotRequireUnusedKeyCallbacks() throws Exception {
        final Path project = project("map-copyof-single-uncompiled-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    public int hashCode() { System.setProperty("unused", "never"); return 1; }
                }
                public static void main(String[] args) {
                    var source = Map.of(new Key(), "one");
                    var copy = Map.copyOf(source);
                    System.out.println(copy == source);
                    System.out.println(copy.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-single-uncompiled-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n1\n");
    }

    @Test
    void singletonMapCopyDoesNotHashItsKey() throws Exception {
        final Path project = project("map-copyof-single-custom-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static int hashes;
                static final class Key {
                    public int hashCode() { hashes++; return 1; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    var source = new HashMap<Key, String>(); source.put(new Key(), "one");
                    hashes = 0;
                    var copy = Map.copyOf(source);
                    System.out.println(hashes);
                    System.out.println(copy.get(new Key()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-single-custom-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("0\none\n");
    }

    @Test
    void mapCopyOfPreservesEntryValidationAndHashOrder() throws Exception {
        final Path project = project("map-copyof-null-before-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static boolean fail;
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { if (fail) throw new IllegalStateException("hash"); return value; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var source = new HashMap<Key, String>(); source.put(new Key(1), "one"); source.put(new Key(2), null);
                    fail = true;
                    try { Map.copyOf(source); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                    catch (IllegalStateException expected) { System.out.println("hash"); }
                    fail = false;
                    var nullFirst = new HashMap<Key, String>(); nullFirst.put(new Key(1), null); nullFirst.put(new Key(2), "two");
                    fail = true;
                    try { Map.copyOf(nullFirst); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                    catch (IllegalStateException expected) { System.out.println("hash"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-null-before-hash").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\nnull\n");
    }

    @Test
    void mapCopyOfRejectsKeysThatBecomeEqualBeforeCopying() throws Exception {
        final Path project = project("map-copyof-mutated-duplicate-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    int value; Key(int value) { this.value = value; }
                    public int hashCode() { return value; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var source = new HashMap<Key, String>(); Key first = new Key(1); Key second = new Key(2);
                    source.put(first, "one"); source.put(second, "two"); second.value = 1;
                    try { Map.copyOf(source); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                    System.out.println(source.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-mutated-duplicate-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n2\n");
    }

    @Test
    void mapCopyOfPropagatesCustomHashFailure() throws Exception {
        final Path project = project("map-copyof-custom-hash-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static boolean fail;
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { if (fail) throw new IllegalStateException("hash"); return value; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var source = new HashMap<Key, String>(); source.put(new Key(1), "one"); source.put(new Key(2), "two");
                    fail = true;
                    try { Map.copyOf(source); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                    System.out.println(source.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-copyof-custom-hash-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\n2\n");
    }
}
