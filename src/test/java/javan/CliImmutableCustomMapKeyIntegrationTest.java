package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImmutableCustomMapKeyIntegrationTest extends CliIntegrationSupport {
    @Test
    void mapOfUsesCustomKeyHashAndEquality() throws Exception {
        final Path project = project("map-of-custom-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value;
                    Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    var map = Map.of(new Key(1), "one", new Key(2), "two");
                    System.out.println(map.get(new Key(1)));
                    System.out.println(map.get(new Key(2)));
                    Key other = new Key(3);
                    try { map.put(other, "three"); }
                    catch (UnsupportedOperationException expected) { System.out.println("immutable"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("one\ntwo\nimmutable\n");
    }

    @Test
    void mapOfDetectsDuplicateCustomKeys() throws Exception {
        final Path project = project("map-of-custom-duplicate");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final int value;
                    Key(int value) { this.value = value; }
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Key key && value == key.value; }
                }
                public static void main(String[] args) {
                    Key first = new Key(1); Key duplicate = new Key(1);
                    try { Map.of(first, "one", duplicate, "duplicate"); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-duplicate").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n");
    }

    @Test
    void mapOfChecksEqualityFromTheLaterKey() throws Exception {
        final Path project = project("map-of-asymmetric-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class First {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return false; }
                }
                static final class Second {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof First; }
                }
                public static void main(String[] args) {
                    Object first = new First(); Object second = new Second();
                    try { Map.of(first, "one", second, "two"); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-asymmetric-keys").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("duplicate\n");
    }

    @Test
    void mapOfUsesLaterKeyEqualityForMixedPlatformAndCustomKeys() throws Exception {
        final Path project = project("map-of-mixed-key-equality");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    public int hashCode() { return 1; }
                    public boolean equals(Object other) { return other instanceof Integer; }
                }
                public static void main(String[] args) {
                    Object custom = new Key(); Object number = Integer.valueOf(1);
                    System.out.println(Map.of(custom, "custom", number, "number").size());
                    try { Map.of(number, "number", custom, "custom"); }
                    catch (IllegalArgumentException expected) { System.out.println("duplicate"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-mixed-key-equality").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\nduplicate\n");
    }

    @Test
    void mapOfPropagatesHashAndEqualsFailuresInJavaOrder() throws Exception {
        final Path project = project("map-of-custom-key-failures");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Key {
                    final boolean failHash;
                    Key(boolean failHash) { this.failHash = failHash; }
                    public int hashCode() { if (failHash) throw new IllegalStateException("hash"); return 1; }
                    public boolean equals(Object other) { throw new IllegalStateException("equals"); }
                }
                public static void main(String[] args) {
                    Key badHash = new Key(true); Key first = new Key(false); Key second = new Key(false);
                    String missing = null;
                    try { Map.of(badHash, "one", second, missing); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                    try { Map.of(first, "one", second, "two"); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-key-failures").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("hash\nequals\n");
    }

    @Test
    void mapOfReleasesRuntimeLockAndRootsKeysDuringConcurrentGc() throws Exception {
        final Path project = project("map-of-custom-key-gc");
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
                    Key first = new Key(1, true); Key second = new Key(2, false);
                    Thread worker = new Thread(new Worker()); worker.start();
                    var map = Map.of(first, "one", second, "two"); worker.join();
                    System.out.println(map.get(new Key(1, false)));
                    System.out.println(map.get(new Key(2, false)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/map-of-custom-key-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("one\ntwo\n");
    }

    @Test
    void knownKeyCallbacksRemainAvailableWhenTheOtherKeyIsUnknown() throws Exception {
        final Path project = project("map-of-partly-known-keys");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
            public final class Main {
                static final class Known {
                    public int hashCode() { return 7; }
                    public boolean equals(Object other) { return other instanceof Known; }
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
                    Object known = new Known(); Object other = choose(args[0]);
                    Map<Object, String> map = null;
                    try { map = Map.of(known, "one", other, "two"); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                    if (map != null) System.out.println(map.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/map-of-partly-known-keys").toString();
        final ProcessResult known = process(project, List.of(binary, "safe"));
        final ProcessResult unknown = process(project, List.of(binary, "unknown"));
        assertThat(known.exitCode()).as(known.stderr()).isZero();
        assertThat(known.stdout()).isEqualTo("2\n");
        assertThat(unknown.exitCode()).as(unknown.stderr()).isZero();
        assertThat(unknown.stdout()).isEqualTo("unsupported\n");
    }

    @Test
    void mapOfRejectsUnresolvedCustomKeyCallbacks() throws Exception {
        final Path project = project("map-of-unresolved-custom-key");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.Map;
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
                    Object key = choose(args[0]);
                    try { Map.of(key, "one", "other", "two"); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/map-of-unresolved-custom-key").toString(), "key"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported\n");
    }
}
