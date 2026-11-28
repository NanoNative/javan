package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliCustomMapKeyIntegrationTest extends CliIntegrationSupport {
    @Test
    void customMapKeysUseApplicationHashCodeAndEquals() throws Exception {
        final Path project = project("map-custom-key-equality");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key {
                    private final String value;
                    Key(String value) { this.value = value; }
                    public boolean equals(Object other) { return other instanceof Key key && value.equals(key.value); }
                    public int hashCode() { return value.hashCode(); }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>();
                    map.put(new Key("Aa"), "first");
                    map.put(new Key("BB"), "second");
                    System.out.println(map.get(new Key("Aa")));
                    System.out.println(map.get(new Key("BB")));
                    System.out.println(map.put(new Key("Aa"), "changed"));
                    System.out.println(map.remove(new Key("BB")));
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-key-equality").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }
    @Test
    void customKeyFailuresPropagateWithoutMapMutation() throws Exception {
        final Path project = project("map-custom-key-throws");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key {
                    private final boolean failHash;
                    Key(boolean failHash) { this.failHash = failHash; }
                    public int hashCode() { if (failHash) throw new IllegalStateException("hash"); return 1; }
                    public boolean equals(Object other) { throw new IllegalStateException("equals"); }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); map.put(new Key(false), "first");
                    try { map.put(new Key(true), "bad"); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                    try { map.put(new Key(false), "bad"); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-key-throws").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\nequals\n1\n");
    }

    @Test
    void customMapKeyCallbacksReleaseRuntimeLockDuringConcurrentGc() throws Exception {
        final Path project = project("map-custom-key-unlocked-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() { while (signal.get() == 0) Thread.yield(); completed.set(1); }
                }
                static final class Key {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) {
                        signal.set(1);
                        for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                        return completed.get() == 1;
                    }
                }
                public static void main(String[] args) throws Exception {
                    var map = new HashMap<Key, String>(); map.put(new Key(), "first");
                    Thread worker = new Thread(new Worker()); worker.start();
                    System.out.println(map.put(new Key(), "changed"));
                    worker.join(); System.out.println(map.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/map-custom-key-unlocked-gc").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).as(result.stderr()).isEqualTo("first\n1\n");
    }

    @Test
    void customMapHashCodeRunsWithoutRuntimeLock() throws Exception {
        final Path project = project("map-custom-hash-unlocked");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() { while (signal.get() == 0) Thread.yield(); completed.set(1); }
                }
                static final class Key {
                    private final boolean wait;
                    Key(boolean wait) { this.wait = wait; }
                    public int hashCode() {
                        if (wait) {
                            signal.set(1);
                            for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                            return completed.get() == 1 ? 1 : 2;
                        }
                        return 1;
                    }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) throws Exception {
                    var map = new HashMap<Key, String>(); map.put(new Key(false), "first");
                    Thread worker = new Thread(new Worker()); worker.start();
                    System.out.println(map.get(new Key(true))); worker.join();
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-hash-unlocked").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("first\n");
    }

    @Test
    void customMapKeyOperationsKeepResultsAliveDuringConcurrentGc() throws Exception {
        final Path project = project("map-custom-results-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Key {
                    final int number;
                    Key(int number) { this.number = number; }
                    public int hashCode() { return number; }
                    public boolean equals(Object other) { return other instanceof Key key && number == key.number; }
                }
                static final class Value { final int number; Value(int number) { this.number = number; } }
                static final class Worker implements Runnable {
                    public void run() {
                        var map = new HashMap<Key, Value>();
                        for (int i = 0; i < 40; i++) map.put(new Key(i), new Value(i));
                        for (int i = 0; i < 40; i++) {
                            Value old = map.put(new Key(i), new Value(i + 1));
                            if (old.number == i) successes.incrementAndGet();
                            Value removed = map.remove(new Key(i));
                            if (removed.number == i + 1) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-results-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("160\n");
    }

    @Test
    void concurrentMapCustomKeysSurviveMutationAndGarbageCollection() throws Exception {
        final Path project = project("concurrent-map-custom-keys-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final ConcurrentHashMap<Key, Integer> map = new ConcurrentHashMap<>();
                static final AtomicInteger successes = new AtomicInteger();
                static final class Key {
                    final int number; Key(int number) { this.number = number; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && number == key.number; }
                }
                static final class Worker implements Runnable {
                    final int base; Worker(int base) { this.base = base; }
                    public void run() {
                        for (int i = 0; i < 20; i++) map.put(new Key(base + i), i);
                        for (int i = 0; i < 20; i++) {
                            Integer value = map.get(new Key(base + i));
                            if (value != null && value == i) successes.incrementAndGet();
                            map.remove(new Key(base + i));
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread a = new Thread(new Worker(0)); Thread b = new Thread(new Worker(100));
                    a.start(); b.start(); a.join(); b.join();
                    System.out.println(successes.get()); System.out.println(map.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/concurrent-map-custom-keys-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("40\n0\n");
    }

    @Test
    void customKeysWorkWithDefaultedLookupMembershipAndConditionalUpdates() throws Exception {
        final Path project = project("map-custom-conditional");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static class Key {
                    final String value; Key(String value) { this.value = value; }
                    public int hashCode() { return value.hashCode(); }
                    public boolean equals(Object other) { return other instanceof Key key && value.equals(key.value); }
                }
                static final class Child extends Key { Child(String value) { super(value); } }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); map.put(new Child("one"), null);
                    System.out.println(map.containsKey(new Child("one")));
                    System.out.println(map.getOrDefault(new Child("one"), "fallback"));
                    System.out.println(map.putIfAbsent(new Child("one"), "first"));
                    System.out.println(map.putIfAbsent(new Child("one"), "second"));
                    System.out.println(map.replace(new Child("one"), "third"));
                    System.out.println(map.getOrDefault(new Child("missing"), "fallback"));
                    System.out.println(map.replace(new Child("one"), "wrong", "ignored"));
                    System.out.println(map.replace(new Child("one"), "third", "fourth"));
                    System.out.println(map.remove(new Child("one"), "wrong"));
                    System.out.println(map.get(new Child("one")));
                    System.out.println(map.remove(new Child("one"), "fourth"));
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-conditional").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void emptyAndSingletonMapsDoNotInvokeCustomHashCodeForLookup() throws Exception {
        final Path project = project("map-custom-special-lookups");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Collections;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    public int hashCode() { throw new IllegalStateException("hash was called"); }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    Key key = new Key();
                    System.out.println(Collections.emptyMap().get(key));
                    System.out.println(Collections.singletonMap(new Key(), "one").get(key));
                    System.out.println(Map.of(new Key(), "one").get(key));
                    System.out.println(Map.copyOf(Collections.singletonMap(new Key(), "one")).get(key));
                    System.out.println(Map.of().get(key));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-special-lookups").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void unknownCustomHashCodeFailsExplicitlyBeforeInsertion() throws Exception {
        final Path project = project("map-unknown-custom-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key {
                    public int hashCode() { System.setProperty("unused", "value"); return 1; }
                    public boolean equals(Object other) { return true; }
                }
                static final class A { } static final class B { } static final class C { } static final class D { }
                static Object choose(String value) {
                    if (value.equals("key")) return new Key();
                    if (value.equals("a")) return new A();
                    if (value.equals("b")) return new B();
                    if (value.equals("c")) return new C();
                    return new D();
                }
                public static void main(String[] args) {
                    var map = new HashMap<Object, String>();
                    Object value = choose(args[0]);
                    try { map.put(value, "value"); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported hash"); }
                    System.out.println(map.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-unknown-custom-hash").toString(), "key"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported hash\n0\n");
    }

    @Test
    void enumMapLookupRejectsForeignKeyWithoutCallingItsHashCode() throws Exception {
        final Path project = project("enummap-custom-foreign-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.EnumMap;
            import java.util.Map;
            public final class Main {
                enum Color { RED }
                static final class Key {
                    public int hashCode() { throw new IllegalStateException("unexpected hash"); }
                    public boolean equals(Object other) { return true; }
                }
                @SuppressWarnings({"rawtypes", "unchecked"})
                public static void main(String[] args) {
                    Map<Object, String> map = (Map) new EnumMap<Color, String>(Color.class);
                    System.out.println(map.get(new Key()));
                    System.out.println(map.containsKey(new Key()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/enummap-custom-foreign-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nfalse\n");
    }

    @Test
    void valuesViewRemovesCustomKeysWithoutRehashingThem() throws Exception {
        final Path project = project("map-custom-values-removal");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static int hashes;
                static final class Key {
                    final int number; Key(int number) { this.number = number; }
                    public int hashCode() { hashes++; return number; }
                    public boolean equals(Object other) { return other instanceof Key key && number == key.number; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); map.put(new Key(1), "one"); map.put(new Key(2), "two");
                    int before = hashes;
                    var iterator = map.values().iterator();
                    System.out.println(iterator.next()); iterator.remove();
                    System.out.println(hashes == before); System.out.println(map.size());
                    System.out.println(map.values().remove("two"));
                    System.out.println(hashes == before); System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-values-removal").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void concurrentValuesIteratorsAdvanceWithCustomKeysDuringGc() throws Exception {
        final Path project = project("concurrent-custom-values-iterator-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger count = new AtomicInteger();
                static final class Key {
                    final int number; Key(int number) { this.number = number; }
                    public int hashCode() { return number; }
                    public boolean equals(Object other) { return other instanceof Key key && number == key.number; }
                }
                static final class Worker implements Runnable {
                    public void run() {
                        var map = new ConcurrentHashMap<Key, String>();
                        for (int i = 0; i < 20; i++) map.put(new Key(i), "value");
                        var iterator = map.values().iterator();
                        int visited = 0;
                        while (iterator.hasNext() && visited < 30) {
                            if (iterator.next().equals("value")) count.incrementAndGet();
                            visited++;
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread a = new Thread(new Worker()); Thread b = new Thread(new Worker());
                    a.start(); b.start(); a.join(); b.join(); System.out.println(count.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/concurrent-custom-values-iterator-gc").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("40\n");
    }

    @Test
    void putAllRunsCustomKeyHashOutsideRuntimeLock() throws Exception {
        final Path project = project("map-custom-putall-unlocked");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static boolean wait;
                static final class Worker implements Runnable {
                    public void run() { while (signal.get() == 0) Thread.yield(); completed.set(1); }
                }
                static final class Key {
                    public int hashCode() {
                        if (wait) {
                            signal.set(1);
                            for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                            return completed.get() == 1 ? 1 : 2;
                        }
                        return 1;
                    }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) throws Exception {
                    var source = new HashMap<Key, String>(); source.put(new Key(), "value");
                    var target = new HashMap<Key, String>(); Thread worker = new Thread(new Worker());
                    worker.start(); wait = true; target.putAll(source); worker.join();
                    System.out.println(target.get(new Key()));
                    var copy = new HashMap<Key, String>(source);
                    System.out.println(copy.get(new Key()));
                    var concurrentCopy = new java.util.concurrent.ConcurrentHashMap<Key, String>(source);
                    System.out.println(concurrentCopy.get(new Key()));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-putall-unlocked").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("value\nvalue\nvalue\n");
    }

    @Test
    void entryArrayFactoryRejectsUnsupportedCustomKeySemantics() throws Exception {
        final Path project = project("map-custom-factory-rejection");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int number; Key(int number) { this.number = number; }
                    public int hashCode() { return number; }
                    public boolean equals(Object other) { return other instanceof Key key && number == key.number; }
                }
                static Map.Entry<Key, String>[] source() {
                    Key first = new Key(1); Key second = new Key(2);
                    return new Map.Entry[] { Map.entry(first, "one"), Map.entry(second, "two") };
                }
                public static void main(String[] args) {
                    Map.Entry<Key, String>[] entries = source();
                    try { Map.ofEntries(entries); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported entries"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-factory-rejection").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported entries\n");
    }

    @Test
    void bulkCopyPropagatesCustomHashFailures() throws Exception {
        final Path project = project("map-custom-bulk-hash-error");
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
                static void copy(Map<Key, String> source) { new HashMap<Key, String>(source); }
                public static void main(String[] args) {
                    var source = new HashMap<Key, String>(); source.put(new Key(), "value");
                    var target = new HashMap<Key, String>(); fail = true;
                    try { target.putAll(source); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                    System.out.println(target.size());
                    try { copy(source); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-bulk-hash-error").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\n0\nhash\n");
    }

    @Test
    void immutableFactoryDetectsDuplicateSupportedRecordKeys() throws Exception {
        final Path project = project("map-record-factory-duplicates");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                record Key(int number) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); map.put(new Key(1), "first");
                    System.out.println(map.get(new Key(1)));
                    Key first = new Key(1); Key duplicate = new Key(1);
                    try { Map.of(first, "one", duplicate, "two"); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-record-factory-duplicates").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("first\nduplicate\n");
    }

    @Test
    void customKeysShareHashBucketsWithPlatformAndNullKeys() throws Exception {
        final Path project = project("map-custom-mixed-collisions");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Object, String>();
                    map.put(1, "integer"); map.put(new Key(), "custom"); map.put(null, "null");
                    System.out.println(map.get(1)); System.out.println(map.get(new Key()));
                    System.out.println(map.get(null)); System.out.println(map.remove(1));
                    System.out.println(map.get(new Key())); System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-mixed-collisions").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void mapUsesTheQueryKeyEqualsDirection() throws Exception {
        final Path project = project("map-custom-query-equals");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value >= key.value; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); map.put(new Key(10), "first");
                    System.out.println(map.get(new Key(5))); System.out.println(map.get(new Key(15)));
                    System.out.println(map.put(new Key(5), "second")); System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-custom-query-equals").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void nativeCustomHashWithoutImplementationFailsBeforeMutation() throws Exception {
        final Path project = project("map-native-key-hash");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key { public native int hashCode(); }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); Key key = new Key();
                    try { map.put(key, "value"); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                    System.out.println(map.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-native-key-hash").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported\n0\n");
    }

    @Test
    void resizingDoesNotRehashMutableKeys() throws Exception {
        final Path project = project("map-mutable-key-resize");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key {
                    final int id; int hash;
                    Key(int id, int hash) { this.id = id; this.hash = hash; }
                    public int hashCode() { return hash; }
                    public boolean equals(Object other) { return other instanceof Key key && id == key.id; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>(); Key key = new Key(1, 1); map.put(key, "found");
                    key.hash = 2; System.out.println(map.get(key));
                    for (int i = 10; i < 100; i++) map.put(new Key(i, i), "other");
                    System.out.println(map.get(key)); key.hash = 1; System.out.println(map.get(key));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-mutable-key-resize").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nnull\nfound\n");
    }

    @Test
    void recordKeysWithListComponentsUseTheirValueSemantics() throws Exception {
        final Path project = project("map-record-list-components");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.List;
            public final class Main {
                record Key(List<String> values) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>();
                    map.put(new Key(List.of("a", "b")), "value");
                    System.out.println(map.get(new Key(List.of("a", "b"))));
                    System.out.println(map.remove(new Key(List.of("a", "b"))));
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-record-list-components").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value\nvalue\n0\n");
    }

    @Test
    void newlyAllocatedMapsRemainAliveDuringConcurrentGc() throws Exception {
        final Path project = project("map-allocation-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Key {
                    final int number;
                    Key(int number) { this.number = number; }
                    public int hashCode() { return number; }
                    public boolean equals(Object other) { return other instanceof Key key && number == key.number; }
                }
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 300; i++) {
                            var map = new HashMap<Key, Integer>();
                            map.put(new Key(i), i);
                            if (map.get(new Key(i)) == i) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-allocation-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("600\n");
    }

    @Test
    void treeAndConcurrentMapConstructorsSurviveConcurrentGc() throws Exception {
        final Path project = project("map-types-allocation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            import java.util.TreeMap;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 300; i++) {
                            Map<Integer, String> map;
                            if ((i & 1) == 0) map = new TreeMap<>();
                            else map = new ConcurrentHashMap<>();
                            map.put(i, "value");
                            if (map.get(i).equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-types-allocation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("600\n");
    }

    @Test
    void newHashMapFactoryResultsSurviveConcurrentGc() throws Exception {
        final Path project = project("map-factory-allocation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 3000; i++) {
                            var map = HashMap.<Integer, String>newHashMap(1);
                            map.put(i, "value");
                            if (map.get(i).equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-factory-allocation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("6000\n");
    }
    @Test
    void newLinkedHashMapFactoryResultsSurviveConcurrentGc() throws Exception {
        final Path project = project("linked-map-factory-allocation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.LinkedHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 3000; i++) {
                            var map = LinkedHashMap.<Integer, String>newLinkedHashMap(1);
                            map.put(i, "value");
                            if (map.get(i).equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/linked-map-factory-allocation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("6000\n");
    }

    @Test
    void capacityConstructorsSurviveConcurrentGc() throws Exception {
        final Path project = project("map-capacity-allocation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 3000; i++) {
                            var map = new HashMap<Integer, String>(1);
                            map.put(i, "value");
                            if (map.get(i).equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-capacity-allocation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("6000\n");
    }
    @Test
    void linkedAndConcurrentCapacityConstructorsSurviveConcurrentGc() throws Exception {
        final Path project = project("other-map-capacity-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.LinkedHashMap;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 3000; i++) {
                            Map<Integer, String> map;
                            if ((i & 1) == 0) map = new LinkedHashMap<>(1);
                            else map = new ConcurrentHashMap<>(1);
                            map.put(i, "value");
                            if (map.get(i).equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/other-map-capacity-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("6000\n");
    }

    @Test
    void negativeExpectedMapSizesThrowCatchableIllegalArgumentException() throws Exception {
        final Path project = project("map-factory-negative-catch");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.LinkedHashMap;
            public final class Main {
                public static void main(String[] args) {
                    try { HashMap.newHashMap(-1); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
                    try { LinkedHashMap.newLinkedHashMap(Integer.MIN_VALUE); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-factory-negative-catch").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("Negative number of mappings: -1\nNegative number of mappings: -2147483648\n");
    }

    @Test
    void negativeFactoryExceptionsSurviveConcurrentGc() throws Exception {
        final Path project = project("map-factory-negative-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger count = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 150; i++) {
                            try { HashMap.newHashMap(-1); }
                            catch (IllegalArgumentException e) {
                                if (e.getMessage().equals("Negative number of mappings: -1")) count.incrementAndGet();
                            }
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(count.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-factory-negative-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("300\n");
    }

}
