package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImmutableCustomMapArityIntegrationTest extends CliIntegrationSupport {
    @Test
    void mapOfSupportsThreeAndTenApplicationKeys() throws Exception {
        final Path project = project("map-of-custom-arities");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var small = Map.of(new Key(1), "one", new Key(2), "two", new Key(3), "three");
                    var large = Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4", new Key(5), "5",
                        new Key(6), "6", new Key(7), "7", new Key(8), "8", new Key(9), "9", new Key(10), "10");
                    System.out.println(small.get(new Key(2)));
                    System.out.println(large.size());
                    System.out.println(large.get(new Key(10)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-arities").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("two\n10\n10\n");
    }

    @Test
    void mapOfSupportsEveryMiddleFixedArity() throws Exception {
        final Path project = project("map-of-custom-middle-arities");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    System.out.println(Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4").get(new Key(4)));
                    System.out.println(Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4", new Key(5), "5").get(new Key(5)));
                    System.out.println(Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4", new Key(5), "5", new Key(6), "6").get(new Key(6)));
                    System.out.println(Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4", new Key(5), "5", new Key(6), "6", new Key(7), "7").get(new Key(7)));
                    System.out.println(Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4", new Key(5), "5", new Key(6), "6", new Key(7), "7", new Key(8), "8").get(new Key(8)));
                    System.out.println(Map.of(new Key(1), "1", new Key(2), "2", new Key(3), "3", new Key(4), "4", new Key(5), "5", new Key(6), "6", new Key(7), "7", new Key(8), "8", new Key(9), "9").get(new Key(9)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-middle-arities").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("4\n5\n6\n7\n8\n9\n");
    }

    @Test
    void mapOfTenKeysReleasesLockAndRootsArgumentsDuringConcurrentGc() throws Exception {
        final Path project = project("map-of-ten-custom-keys-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() { while (signal.get() == 0) Thread.yield(); completed.set(1); }
                }
                static final class Key {
                    final int value; final boolean wait;
                    Key(int value, boolean wait) { this.value = value; this.wait = wait; }
                    public int hashCode() {
                        if (wait) {
                            signal.set(1);
                            for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                            if (completed.get() == 0) throw new IllegalStateException("locked");
                        }
                        return 1;
                    }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) throws Exception {
                    Thread worker = new Thread(new Worker()); worker.start();
                    var map = Map.of(new Key(1, true), 1, new Key(2, false), 2, new Key(3, false), 3,
                        new Key(4, false), 4, new Key(5, false), 5, new Key(6, false), 6, new Key(7, false), 7,
                        new Key(8, false), 8, new Key(9, false), 9, new Key(10, false), 10);
                    worker.join(); int total = 0;
                    for (int i = 1; i <= 10; i++) total += map.get(new Key(i, false));
                    System.out.println(total);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-ten-custom-keys-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("55\n");
    }

    @Test
    void mapOfKeepsKnownCallbacksWhenALaterKeyIsUnknown() throws Exception {
        final Path project = project("map-of-three-partly-known-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Known {
                    final int value; Known(int value) { this.value = value; }
                    public int hashCode() { return value; }
                    public boolean equals(Object other) { return other instanceof Known key && value == key.value; }
                }
                static final class Unknown {
                    public int hashCode() { System.setProperty("unused", "value"); return 1; }
                }
                static final class A { } static final class B { } static final class C { } static final class D { }
                static Object choose(String value) {
                    if (value.equals("unknown")) return new Unknown();
                    if (value.equals("a")) return new A();
                    if (value.equals("b")) return new B();
                    if (value.equals("c")) return new C();
                    if (value.equals("d")) return new D();
                    return "safe";
                }
                public static void main(String[] args) {
                    Object first = new Known(1); Object second = new Known(2); Object third = choose(args[0]);
                    Map<Object, String> map = null;
                    try { map = Map.of(first, "one", second, "two", third, "three"); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                    if (map != null) System.out.println(map.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/map-of-three-partly-known-keys").toString();
        final ProcessResult known = process(project, List.of(binary, "safe"));
        final ProcessResult unknown = process(project, List.of(binary, "unknown"));
        assertThat(known.exitCode()).as(known.stderr()).isZero();
        assertThat(known.stdout()).isEqualTo("3\n");
        assertThat(unknown.exitCode()).as(unknown.stderr()).isZero();
        assertThat(unknown.stdout()).isEqualTo("unsupported\n");
    }

    @Test
    void mapOfDetectsDuplicateApplicationKeysWithThreeEntries() throws Exception {
        final Path project = project("map-of-custom-three-duplicate");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value; Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    Key first = new Key(1); Key second = new Key(2); Key duplicate = new Key(1);
                    try { Map.of(first, "one", second, "two", duplicate, "duplicate"); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-three-duplicate").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n");
    }
}
