package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapValuesRemovalIntegrationTest extends CliIntegrationSupport {
    @Test
    void removesOnlyTheFirstEqualValueFromABackedView() throws Exception {
        final Path project = project("map-values-custom-remove");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.LinkedHashMap;
            import java.util.Map;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) { return other instanceof Value value && number == value.number; }
                }
                public static void main(String[] args) {
                    Map<String, Value> map = new LinkedHashMap<>();
                    map.put("a", new Value(1)); map.put("b", new Value(1)); map.put("c", new Value(2));
                    var values = map.values();
                    System.out.println(values.remove(new Value(1)));
                    System.out.println(map.containsKey("a")); System.out.println(map.containsKey("b"));
                    System.out.println(values.remove(new Value(3))); System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-remove").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\ntrue\nfalse\n2\n");
    }

    @Test
    void invokesEqualsEvenForAnIdenticalValueAndTransportsItsFailure() throws Exception {
        final Path project = project("map-values-remove-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Value { public boolean equals(Object other) { throw new IllegalStateException("failed"); } }
                public static void main(String[] args) {
                    var map = new HashMap<String, Value>(); Value value = new Value(); map.put("a", value);
                    var values = map.values();
                    try { System.out.println(values.remove(value)); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-identity").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("caught:failed\n1\n");
    }

    @Test
    void rejectsAReadOnlyViewBeforeInvokingCustomEquality() throws Exception {
        final Path project = project("map-values-remove-readonly");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Collections;
            import java.util.HashMap;
            public final class Main {
                static final class Value {
                    public boolean equals(Object other) { System.out.println("called"); return true; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<String, Value>(); map.put("a", new Value());
                    var values = Collections.unmodifiableMap(map).values(); Value query = new Value();
                    try { System.out.println(values.remove(query)); }
                    catch (UnsupportedOperationException expected) { System.out.println("readonly"); }
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-readonly").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("readonly\n1\n");
    }

    @Test
    void removesTheReturnedConcurrentMapKeyAfterItsValueChangesDuringEquality() throws Exception {
        final Path project = project("map-values-remove-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) {
                        signal.set(1);
                        for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                        return completed.get() == 1 && other instanceof Value value && value.number == number;
                    }
                }
                static final class Worker implements Runnable {
                    final ConcurrentHashMap<String, Value> map;
                    Worker(ConcurrentHashMap<String, Value> map) { this.map = map; }
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        map.put("a", new Value(2)); completed.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    var map = new ConcurrentHashMap<String, Value>(); map.put("a", new Value(1));
                    Thread worker = new Thread(new Worker(map)); worker.start();
                    System.out.println(map.values().remove(new Value(1)));
                    worker.join(); System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n");
    }
    @Test
    void reportsConcurrentModificationWhenEqualityStructurallyChangesAHashMap() throws Exception {
        final Path project = project("map-values-remove-mutating-callback");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ConcurrentModificationException;
            import java.util.HashMap;
            public final class Main {
                static final class Value {
                    final HashMap<String, Value> map;
                    Value(HashMap<String, Value> map) { this.map = map; }
                    public boolean equals(Object other) { map.put("b", new Value(map)); return true; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<String, Value>(); map.put("a", new Value(map));
                    var values = map.values(); Value query = new Value(map);
                    try { System.out.println(values.remove(query)); }
                    catch (ConcurrentModificationException expected) { System.out.println("modified"); }
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-mutating-callback").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("modified\n2\n");
    }

    @Test
    void invokesEqualityBeforeAnImmutableMapRejectsAMatchingRemoval() throws Exception {
        final Path project = project("map-values-remove-immutable");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static int calls;
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) { calls++; return other instanceof Value value && number == value.number; }
                }
                public static void main(String[] args) {
                    var values = Map.of("a", new Value(1)).values();
                    Value missing = new Value(2); Value match = new Value(1);
                    System.out.println(values.remove(missing));
                    try { System.out.println(values.remove(match)); }
                    catch (UnsupportedOperationException expected) { System.out.println("readonly"); }
                    System.out.println(calls);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-immutable").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\nreadonly\n2\n");
    }

    @Test
    void removesCustomValuesWithoutRehashingApplicationKeys() throws Exception {
        final Path project = project("map-values-remove-custom-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Key { public int hashCode() { return 1; } }
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) { return other instanceof Value value && number == value.number; }
                }
                public static void main(String[] args) {
                    var map = new HashMap<Key, Value>(); map.put(new Key(), new Value(1));
                    System.out.println(map.values().remove(new Value(1)));
                    System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-custom-key").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n");
    }

    @Test
    void removesAnEqualReinsertedKeyDuringConcurrentEquality() throws Exception {
        final Path project = project("map-values-remove-reinserted-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) {
                        signal.set(1);
                        for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                        return completed.get() == 1 && other instanceof Value value && value.number == number;
                    }
                }
                static final class Worker implements Runnable {
                    final ConcurrentHashMap<String, Value> map;
                    Worker(ConcurrentHashMap<String, Value> map) { this.map = map; }
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        map.remove("a"); map.put(new String(new char[] {'a'}), new Value(2)); completed.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    var map = new ConcurrentHashMap<String, Value>(); map.put("a", new Value(1));
                    Thread worker = new Thread(new Worker(map)); worker.start();
                    System.out.println(map.values().remove(new Value(1)));
                    worker.join(); System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-reinserted-key").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n");
    }

    @Test
    void concurrentMapIteratorsRemoveAnEqualReinsertedKey() throws Exception {
        final Path project = project("map-iterator-remove-reinserted-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                public static void main(String[] args) {
                    var valuesMap = new ConcurrentHashMap<String, String>(); valuesMap.put("a", "old");
                    var values = valuesMap.values().iterator(); values.next(); valuesMap.remove("a");
                    valuesMap.put(new String(new char[] {'a'}), "new"); values.remove();
                    System.out.println(valuesMap.isEmpty());
                    Map<String, String> keysMap = new ConcurrentHashMap<>(); keysMap.put("a", "old");
                    var keys = keysMap.keySet().iterator(); keys.next(); keysMap.remove("a");
                    keysMap.put(new String(new char[] {'a'}), "new"); keys.remove();
                    System.out.println(keysMap.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-iterator-remove-reinserted-key").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n");
    }

    @Test
    void detectsMutationBeforeTheNextValueWhenEqualityReturnsFalse() throws Exception {
        final Path project = project("map-values-remove-false-mutating-callback");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ConcurrentModificationException;
            import java.util.LinkedHashMap;
            public final class Main {
                static final class Value {
                    final LinkedHashMap<String, Value> map;
                    Value(LinkedHashMap<String, Value> map) { this.map = map; }
                    public boolean equals(Object other) { map.clear(); return false; }
                }
                public static void main(String[] args) {
                    var map = new LinkedHashMap<String, Value>();
                    map.put("a", new Value(map)); map.put("b", new Value(map));
                    var values = map.values(); Value query = new Value(map);
                    try { System.out.println(values.remove(query)); }
                    catch (ConcurrentModificationException expected) { System.out.println("modified"); }
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-false-mutating-callback").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("modified\n0\n");
    }

    @Test
    void allowsAFalseEqualityResultAfterTheLastValueClearsTheMap() throws Exception {
        final Path project = project("map-values-remove-last-false-mutation");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.LinkedHashMap;
            public final class Main {
                static final class Value {
                    final LinkedHashMap<String, Value> map;
                    Value(LinkedHashMap<String, Value> map) { this.map = map; }
                    public boolean equals(Object other) { map.clear(); return false; }
                }
                public static void main(String[] args) {
                    var map = new LinkedHashMap<String, Value>(); map.put("a", new Value(map));
                    System.out.println(map.values().remove(new Value(map)));
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-remove-last-false-mutation").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\n0\n");
    }

    @Test
    void comparesCustomQueriesWithNullValuesAndRemovesANullKey() throws Exception {
        final Path project = project("map-values-custom-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                static final class Query { public boolean equals(Object other) { return other == null; } }
                public static void main(String[] args) {
                    var map = new HashMap<Object, Object>(); map.put(null, null);
                    var values = map.values(); Query query = new Query();
                    System.out.println(values.contains(query));
                    System.out.println(values.remove(query));
                    System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\ntrue\n");
    }

}
