package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapKeySetCustomContainsIntegrationTest extends CliIntegrationSupport {
    @Test
    void invokesQueryHashAndEqualityForBackedKeyViews() throws Exception {
        final Path project = project("custom-keyset-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                static final class Key {
                    static int hashes, equals;
                    final int id;
                    Key(int id) { this.id = id; }
                    public int hashCode() { hashes++; return id; }
                    public boolean equals(Object other) { equals++; return other instanceof Key k && id == k.id; }
                }
                static void test(Map<Object, String> map) {
                    map.put(new Key(1), "one");
                    Key.hashes = 0; Key.equals = 0;
                    System.out.println(map.keySet().contains(new Key(1)));
                    System.out.println(Key.hashes); System.out.println(Key.equals);
                    System.out.println(map.keySet().contains(new Key(2)));
                }
                public static void main(String[] args) {
                    test(new HashMap<>()); test(new LinkedHashMap<>()); test(new ConcurrentHashMap<>());
                    Map<Object, String> source = new HashMap<>(); source.put(new Key(1), "one");
                    System.out.println(Collections.unmodifiableMap(source).keySet().contains(new Key(1)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/custom-keyset-contains").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void hashesAQueryOnlyKeyOnlyAfterAHashMapTableExists() throws Exception {
        final Path project = project("custom-keyset-contains-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    public int hashCode() { System.out.println("hash"); return 1; }
                    public boolean equals(Object other) { System.out.println("equals"); return false; }
                }
                public static void main(String[] args) {
                    Map<Object, String> map = new HashMap<>();
                    System.out.println(map.keySet().contains(new Key()));
                    map.put("x", "value"); map.clear();
                    System.out.println(map.keySet().contains(new Key()));
                    Map<Object, String> concurrent = new java.util.concurrent.ConcurrentHashMap<>();
                    System.out.println(concurrent.keySet().contains(new Key()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/custom-keyset-contains-empty").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\nhash\nfalse\nhash\nfalse\n");
    }
    @Test
    void transportsHashAndEqualityFailuresFromKeyViewQueries() throws Exception {
        final Path project = project("custom-keyset-contains-throw");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    final int mode;
                    Key(int mode) { this.mode = mode; }
                    public int hashCode() { if (mode == 1) throw new IllegalStateException(); return 7; }
                    public boolean equals(Object other) { if (mode == 2) throw new IllegalArgumentException(); return false; }
                }
                public static void main(String[] args) {
                    Map<Object, String> map = new HashMap<>(); map.put(new Key(0), "one");
                    Key hash = new Key(1); Key equals = new Key(2); var view = map.keySet();
                    try { view.contains(hash); } catch (IllegalStateException expected) { System.out.println("hash"); }
                    try { view.contains(equals); } catch (IllegalArgumentException expected) { System.out.println("equals"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/custom-keyset-contains-throw").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\nequals\n");
    }

    @Test
    void invokesConcurrentKeyEqualityOutsideTheRuntimeLockDuringGc() throws Exception {
        final Path project = project("custom-keyset-contains-concurrent");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), completed = new AtomicInteger();
                static final class Key {
                    public int hashCode() { return 7; }
                    public boolean equals(Object other) {
                        signal.set(1);
                        for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                        return completed.get() == 1 && other instanceof Key;
                    }
                }
                static final class Worker implements Runnable {
                    final Map<Object, String> map;
                    Worker(Map<Object, String> map) { this.map = map; }
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        map.put("other", "two"); completed.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Map<Object, String> map = new ConcurrentHashMap<>(); map.put(new Key(), "one");
                    Thread worker = new Thread(new Worker(map)); worker.start();
                    System.out.println(map.keySet().contains(new Key()));
                    worker.join(); System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/custom-keyset-contains-concurrent").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n2\n");
    }

    @Test
    void keepsUnusedHashCodeUnreachableForListMembership() throws Exception {
        final Path project = project("list-contains-unused-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    public int hashCode() { Runtime.getRuntime().halt(0); return 0; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    List<Key> keys = List.of(new Key());
                    System.out.println(keys.contains(new Key()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/list-contains-unused-hash").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n");
    }

    @Test
    void rejectsUnimplementedCustomTreeMapKeyViewsExplicitly() throws Exception {
        final Path project = project("tree-keyset-custom-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    public int hashCode() { return 1; }
                }
                public static void main(String[] args) {
                    Map<Object, String> map = new TreeMap<>();
                    System.out.println(map.keySet().contains(new Key()));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/tree-keyset-custom-unsupported").toString()));
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("java/lang/UnsupportedOperationException");
    }

    @Test
    void singletonViewsInvokeQueryEqualityEvenForTheSameKey() throws Exception {
        final Path project = project("singleton-keyset-custom-equality");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    public int hashCode() { System.out.println("hash"); return 1; }
                    public boolean equals(Object other) { System.out.println("equals"); return this != other; }
                }
                public static void main(String[] args) {
                    Key key = new Key();
                    Map<Key, String> first = Map.of(key, "one");
                    System.out.println(first.keySet().contains(key));
                    System.out.println(first.keySet().contains(new Key()));
                    Map<Key, String> second = Collections.singletonMap(key, "one");
                    System.out.println(second.keySet().contains(key));
                    System.out.println(second.keySet().contains(new Key()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/singleton-keyset-custom-equality").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("equals\nfalse\nequals\ntrue\nequals\nfalse\nequals\ntrue\n");
    }

    @Test
    void immutableMultiEntryViewInvokesEqualityForAnIdenticalKey() throws Exception {
        final Path project = project("immutable-keyset-custom-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.*;
            public final class Main {
                static final class Key {
                    static int hashes, equals;
                    public int hashCode() { hashes++; return 1; }
                    public boolean equals(Object other) { equals++; return this != other; }
                }
                public static void main(String[] args) {
                    Key key = new Key(); Map<Object, String> map = Map.of(key, "one", "other", "two");
                    Key.hashes = 0; Key.equals = 0;
                    System.out.println(map.keySet().contains(key));
                    System.out.println(Key.hashes); System.out.println(Key.equals);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/immutable-keyset-custom-identity").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void concreteConcurrentKeyViewFindsApplicationDefinedKeys() throws Exception {
        final Path project = project("concurrent-concrete-keyset-custom-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                static final class Key {
                    final int id;
                    Key(int id) { this.id = id; }
                    public int hashCode() { return id; }
                    public boolean equals(Object other) { return other instanceof Key k && id == k.id; }
                }
                public static void main(String[] args) {
                    var map = new ConcurrentHashMap<Key, String>(); map.put(new Key(1), "one");
                    var keys = map.keySet();
                    System.out.println(keys.contains(new Key(1)));
                    System.out.println(keys.contains(new Key(2)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/concurrent-concrete-keyset-custom-contains").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\n");
    }

}
