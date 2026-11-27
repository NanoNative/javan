package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliRecordMapKeyIntegrationTest extends CliIntegrationSupport {
    @Test
    void hashMapFindsReplacesAndRemovesEqualRecordKeys() throws Exception {
        final Path project = project("record-map-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                record Key(String name, int number) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>();
                    map.put(new Key("key", 7), "first");
                    System.out.println(map.get(new Key("key", 7)));
                    System.out.println(map.put(new Key("key", 7), "second"));
                    System.out.println(map.size());
                    System.out.println(map.remove(new Key("key", 7)));
                    System.out.println(map.size());
                    System.out.println(map.get(new Key("missing", 7)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-map-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("first\nfirst\n1\nsecond\n0\nnull\n");
    }

    @Test
    void recordKeysSurviveMapGrowthAndRemoval() throws Exception {
        final Path project = project("record-map-growth");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                record Key(String name, int number) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, Integer>();
                    for (int i = 0; i < 100; i++) map.put(new Key("key", i), i);
                    int sum = 0;
                    for (int i = 0; i < 100; i++) sum += map.get(new Key("key", i));
                    for (int i = 0; i < 100; i += 2) map.remove(new Key("key", i));
                    map.put(new Key(null, 2), 99);
                    System.out.println(sum);
                    System.out.println(map.size());
                    System.out.println(map.get(new Key(null, 2)));
                    System.out.println(map.containsKey(new Key("key", 33)));
                    System.out.println(map.containsKey(new Key("key", 34)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-map-growth").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("4950\n51\n99\ntrue\nfalse\n");
    }

    @Test
    void recordMapKeysSurviveConcurrentGarbageCollection() throws Exception {
        final Path project = project("record-map-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                record Key(String name, int number) { }
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() {
                        var map = new HashMap<Key, Integer>();
                        for (int i = 0; i < 40; i++) map.put(new Key("key", i), i);
                        for (int i = 0; i < 40; i++) if (map.get(new Key("key", i)) == i) successes.incrementAndGet();
                    }
                }
                public static void main(String[] args) throws InterruptedException {
                    Thread a = new Thread(new Worker()); Thread b = new Thread(new Worker());
                    a.start(); b.start(); a.join(); b.join(); System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/record-map-concurrent-gc").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("80\n");
    }

    @Test
    void recordKeysWithEqualHashesRemainDistinct() throws Exception {
        final Path project = project("record-map-hash-collision");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                record Key(String value) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>();
                    map.put(new Key("Aa"), "first");
                    map.put(new Key("BB"), "second");
                    System.out.println(map.size());
                    System.out.println(map.get(new Key("Aa")));
                    System.out.println(map.get(new Key("BB")));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/record-map-hash-collision").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\nfirst\nsecond\n");
    }

    @Test
    void recordKeyLookupWorksInReleaseBuild() throws Exception {
        final Path project = project("record-map-release");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                record Key(String value, int number) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>();
                    map.put(new Key("key", 7), "found");
                    System.out.println(map.get(new Key("key", 7)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-map-release").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("found\n");
    }

    @Test
    void floatingPointRecordKeysRespectNanAndSignedZero() throws Exception {
        final Path project = project("record-map-floating-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashMap;
            public final class Main {
                record Key(double value, float other) { }
                public static void main(String[] args) {
                    var map = new HashMap<Key, String>();
                    map.put(new Key(Double.NaN, Float.NaN), "nan");
                    map.put(new Key(-0.0, 0.0f), "negative");
                    map.put(new Key(0.0, 0.0f), "positive");
                    System.out.println(map.get(new Key(Double.NaN, Float.NaN)));
                    System.out.println(map.get(new Key(-0.0, 0.0f)));
                    System.out.println(map.get(new Key(0.0, 0.0f)));
                    System.out.println(map.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/record-map-floating-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("nan\nnegative\npositive\n3\n");
    }

    @Test
    void concurrentMapFindsRecordKeysDuringConcurrentGc() throws Exception {
        final Path project = project("record-concurrent-map-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.concurrent.ConcurrentHashMap;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                record Key(String name, int number) { }
                static final ConcurrentHashMap<Key, Integer> map = new ConcurrentHashMap<>();
                static final AtomicInteger successes = new AtomicInteger();
                static final class Worker implements Runnable {
                    private final int offset;
                    Worker(int offset) { this.offset = offset; }
                    public void run() {
                        for (int i = 0; i < 40; i++) {
                            int number = offset + i;
                            map.put(new Key("key", number), number);
                            if (map.get(new Key("key", number)) == number) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws InterruptedException {
                    Thread a = new Thread(new Worker(0)); Thread b = new Thread(new Worker(40));
                    a.start(); b.start(); a.join(); b.join();
                    System.out.println(map.size()); System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/record-concurrent-map-keys").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("80\n80\n");
    }
}
