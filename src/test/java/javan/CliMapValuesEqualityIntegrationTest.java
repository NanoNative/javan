package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliMapValuesEqualityIntegrationTest extends CliIntegrationSupport {
    @Test
    void searchesAValuesViewUsingTheQueryValueEquals() throws Exception {
        final Path project = project("map-values-custom-contains");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) {
                        return other instanceof Value value && number == value.number;
                    }
                }
                static final class Unrelated {
                    String id() { return "unrelated"; }
                    public boolean equals(Object other) { System.setProperty("unused", "never"); return false; }
                }
                public static void main(String[] args) {
                    System.out.println(new Unrelated().id());
                    Map<String, Value> map = new HashMap<>();
                    map.put("a", new Value(1));
                    var values = map.values();
                    System.out.println(values.contains(new Value(1)));
                    System.out.println(values.contains(new Value(2)));
                    map.put("b", new Value(2));
                    System.out.println(values.contains(new Value(2)));
                    map.remove("a");
                    System.out.println(values.contains(new Value(1)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-contains").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("unrelated\ntrue\nfalse\ntrue\nfalse\n");
    }

    @Test
    void skipsEqualsForTheIdenticalStoredValueAndTransportsFailures() throws Exception {
        final Path project = project("map-values-custom-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Value {
                    public boolean equals(Object other) { throw new IllegalStateException("failed"); }
                }
                public static void main(String[] args) {
                    Map<String, Value> map = new HashMap<>();
                    Value value = new Value(); map.put("a", value);
                    var values = map.values();
                    System.out.println(values.contains(value));
                    Value other = new Value();
                    try { System.out.println(values.contains(other)); }
                    catch (IllegalStateException expected) { System.out.println("caught:" + expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-identity").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ncaught:failed\n");
    }

    @Test
    void keepsCustomValuesAliveDuringConcurrentCollection() throws Exception {
        final Path project = project("map-values-custom-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) {
                        return other instanceof Value value && number == value.number;
                    }
                }
                static final class Worker implements Runnable {
                    final ConcurrentHashMap<String, Value> map;
                    int found;
                    Worker(ConcurrentHashMap<String, Value> map) { this.map = map; }
                    public void run() { for (int n = 0; n < 200; n++) if (map.values().contains(new Value(1))) found++; }
                }
                public static void main(String[] args) throws Exception {
                    ConcurrentHashMap<String, Value> map = new ConcurrentHashMap<>(); map.put("a", new Value(1));
                    Worker a = new Worker(map); Worker b = new Worker(map);
                    Thread first = new Thread(a); Thread second = new Thread(b);
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(a.found + b.found);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("400\n");
    }
    @Test
    void callsEqualsOnTheQueryRatherThanTheStoredValue() throws Exception {
        final Path project = project("map-values-custom-direction");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Stored { public boolean equals(Object other) { return false; } }
                static final class Query { public boolean equals(Object other) { return other instanceof Stored; } }
                public static void main(String[] args) {
                    Map<String, Object> map = new HashMap<>(); map.put("a", new Stored());
                    System.out.println(map.values().contains(new Query()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-direction").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n");
    }

    @Test
    void keepsTheComparedValueAliveWhenAnotherThreadRemovesIt() throws Exception {
        final Path project = project("map-values-custom-remove-gc");
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
                        map.remove("a"); completed.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    ConcurrentHashMap<String, Value> map = new ConcurrentHashMap<>(); map.put("a", new Value(1));
                    Thread worker = new Thread(new Worker(map)); worker.start();
                    System.out.println(map.values().contains(new Value(1)));
                    worker.join(); System.out.println(map.isEmpty());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-remove-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\n");
    }

    @Test
    void searchesLinkedConcurrentImmutableAndUnmodifiableValuesViews() throws Exception {
        final Path project = project("map-values-custom-kinds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Collections;
            import java.util.LinkedHashMap;
            import java.util.Map;
            import java.util.concurrent.ConcurrentHashMap;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) { return other instanceof Value value && number == value.number; }
                }
                static void check(Map<String, Value> map) {
                    System.out.println(map.values().contains(new Value(1)));
                    System.out.println(map.values().contains(new Value(2)));
                }
                public static void main(String[] args) {
                    Map<String, Value> linked = new LinkedHashMap<>(); linked.put("a", new Value(1));
                    Map<String, Value> concurrent = new ConcurrentHashMap<>(); concurrent.put("a", new Value(1));
                    check(linked); check(concurrent); check(Collections.unmodifiableMap(linked));
                    check(Map.of("a", new Value(1)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-kinds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\ntrue\nfalse\ntrue\nfalse\ntrue\nfalse\n");
    }

    @Test
    void followsAValuesViewAndQueryThroughMethodArguments() throws Exception {
        final Path project = project("map-values-custom-argument-flow");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Collection;
            import java.util.HashMap;
            import java.util.Map;
            public final class Main {
                static final class Value {
                    final int number;
                    Value(int number) { this.number = number; }
                    public boolean equals(Object other) { return other instanceof Value value && number == value.number; }
                }
                static boolean contains(Collection<?> values, Object query) { return values.contains(query); }
                public static void main(String[] args) {
                    Map<String, Value> map = new HashMap<>(); map.put("a", new Value(1));
                    System.out.println(contains(map.values(), new Value(1)));
                    System.out.println(contains(map.values(), new Value(2)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-custom-argument-flow").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\n");
    }

    @Test
    void newlyCreatedValuesViewsAndIteratorsSurviveConcurrentGc() throws Exception {
        final Path project = project("map-values-creation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final HashMap<Integer, String> map = new HashMap<>();
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 2000; i++) {
                            var values = map.values();
                            var iterator = values.iterator();
                            if (iterator.hasNext() && iterator.next().equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    map.put(1, "value");
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-values-creation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("4000\n");
    }

    @Test
    void newlyCreatedKeyAndEntryViewsSurviveConcurrentGc() throws Exception {
        final Path project = project("map-key-entry-creation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final HashMap<Integer, String> map = new HashMap<>();
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 300; i++) {
                            var keys = map.keySet().iterator();
                            var entries = map.entrySet().iterator();
                            if (keys.hasNext() && keys.next() == 1 && entries.hasNext()
                                && entries.next().getValue().equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    map.put(1, "value");
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-key-entry-creation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("600\n");
    }

    @Test
    void newlyCreatedListIteratorsSurviveConcurrentGc() throws Exception {
        final Path project = project("list-iterator-creation-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final ArrayList<String> values = new ArrayList<>();
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        for (int i = 0; i < 300; i++) {
                            var iterator = values.listIterator(0);
                            if (iterator.hasNext() && iterator.next().equals("value")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    values.add("value");
                    Thread first = new Thread(new Worker()); Thread second = new Thread(new Worker());
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/list-iterator-creation-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("600\n");
    }

}
