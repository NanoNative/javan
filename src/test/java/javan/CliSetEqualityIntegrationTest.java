package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliSetEqualityIntegrationTest extends CliIntegrationSupport {
    @Test
    void hashSetFindsAndRemovesEqualRecordElements() throws Exception {
        final Path project = project("record-set-elements");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                record Key(String name, int number) { }
                public static void main(String[] args) {
                    var set = new HashSet<Key>();
                    System.out.println(set.add(new Key("key", 7)));
                    System.out.println(set.add(new Key("key", 7)));
                    System.out.println(set.contains(new Key("key", 7)));
                    System.out.println(set.size());
                    System.out.println(set.remove(new Key("key", 7)));
                    System.out.println(set.size());
                    System.out.println(set.contains(new Key("missing", 7)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-set-elements").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\ntrue\n1\ntrue\n0\nfalse\n");
    }

    @Test
    void hashSetPropagatesEqualsFailureWithoutAddingTheElement() throws Exception {
        final Path project = project("set-equals-throws");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                static final class Key {
                    public boolean equals(Object other) { throw new IllegalStateException("equals failed"); }
                    public int hashCode() { return 1; }
                }
                public static void main(String[] args) {
                    var set = new HashSet<Key>();
                    set.add(new Key());
                    Key next = new Key();
                    try { set.add(next); }
                    catch (IllegalStateException expected) { System.out.println(expected.getMessage()); }
                    System.out.println(set.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/set-equals-throws").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("equals failed\n1\n");
    }

    @Test
    void hashSetAdditionAloneRetainsRecordEquality() throws Exception {
        final Path project = project("set-add-record-equality");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                record Key(String value) { }
                public static void main(String[] args) {
                    var set = new HashSet<Key>();
                    System.out.println(set.add(new Key("value")));
                    System.out.println(set.add(new Key("value")));
                    System.out.println(set.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/set-add-record-equality").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\n1\n");
    }

    @Test
    void recordElementEqualityWorksInReleaseBuild() throws Exception {
        final Path project = project("record-set-release");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                record Key(String value) { }
                public static void main(String[] args) {
                    var set = new HashSet<Key>();
                    set.add(new Key("key"));
                    System.out.println(set.contains(new Key("key")));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/record-set-release").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n");
    }

    @Test
    void hashSetUsesQueryEqualsRatherThanStoredElementEquals() throws Exception {
        final Path project = project("set-query-equals-direction");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                static final class Key {
                    private final int value;
                    Key(int value) { this.value = value; }
                    public boolean equals(Object other) { return other instanceof Key key && value >= key.value; }
                    public int hashCode() { return 1; }
                }
                public static void main(String[] args) {
                    var set = new HashSet<Key>();
                    set.add(new Key(10));
                    System.out.println(set.contains(new Key(5)));
                    System.out.println(set.contains(new Key(15)));
                    System.out.println(set.add(new Key(5)));
                    System.out.println(set.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/set-query-equals-direction").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\ntrue\ntrue\n2\n");
    }

    @Test
    void listAdditionThroughCollectionDoesNotReachUnusedEquals() throws Exception {
        final Path project = project("list-add-unused-equals");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ArrayList;
            import java.util.Collection;
            public final class Main {
                static final class Value {
                    public boolean equals(Object other) { System.setProperty("unused", "value"); return true; }
                }
                public static void main(String[] args) {
                    Collection<Value> values = new ArrayList<>();
                    values.add(new Value());
                    values.add(new Value());
                    System.out.println(values.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/list-add-unused-equals").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("2\n");
    }

    @Test
    void addingFirstSetElementDoesNotReachUnusedEquals() throws Exception {
        final Path project = project("set-first-add-unused-equals");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                static final class Value {
                    public boolean equals(Object other) { System.setProperty("unused", "value"); return true; }
                }
                public static void main(String[] args) {
                    var values = new HashSet<Value>();
                    values.add(new Value());
                    System.out.println(values.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/set-first-add-unused-equals").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\n");
    }

    @Test
    void freshSetLosesEmptyProofAfterBeingPassedToAnotherMethod() throws Exception {
        final Path project = project("set-fresh-escape");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                record Key(String value) { }
                static void fill(HashSet<Key> set) { set.add(new Key("value")); }
                public static void main(String[] args) {
                    var set = new HashSet<Key>();
                    fill(set);
                    System.out.println(set.add(new Key("value")));
                    System.out.println(set.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/set-fresh-escape").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\n1\n");
    }

    @Test
    void setCopyConstructorDoesNotClaimToBeEmpty() throws Exception {
        final Path project = project("set-copy-not-empty");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            import java.util.List;
            public final class Main {
                record Key(String value) { }
                public static void main(String[] args) {
                    var set = new HashSet<Key>(List.of(new Key("value")));
                    System.out.println(set.add(new Key("value")));
                    System.out.println(set.size());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/set-copy-not-empty").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\n1\n");
    }

    @Test
    void hashSetAdditionReleasesRuntimeLockDuringEqualityAndConcurrentGc() throws Exception {
        final Path project = project("set-add-equality-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger();
                static final AtomicInteger completed = new AtomicInteger();
                static final class Worker implements Runnable {
                    public void run() { while (signal.get() == 0) Thread.yield(); completed.set(1); }
                }
                static final class Key {
                    public boolean equals(Object other) {
                        signal.set(1);
                        for (int i = 0; i < 1000000 && completed.get() == 0; i++) Thread.yield();
                        return completed.get() == 1;
                    }
                    public int hashCode() { return 1; }
                }
                public static void main(String[] args) throws Exception {
                    var set = new HashSet<Key>(); set.add(new Key());
                    Thread worker = new Thread(new Worker()); worker.start();
                    System.out.println(set.add(new Key()));
                    worker.join(); System.out.println(set.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/set-add-equality-concurrent-gc").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("false\n1\n");
    }

    @Test
    void unknownSetAdditionRejectsAnUncompiledEqualsInsteadOfUsingIdentity() throws Exception {
        final Path project = project("set-unknown-equality");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.HashSet;
            public final class Main {
                static final class Key {
                    public boolean equals(Object other) { System.setProperty("unused", "value"); return true; }
                    public int hashCode() { return 1; }
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
                    var set = new HashSet<Object>();
                    set.add("seed");
                    Object value = choose(args[0]);
                    try { set.add(value); }
                    catch (UnsupportedOperationException expected) { System.out.println("unsupported equality"); }
                    System.out.println(set.size());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/set-unknown-equality").toString(), "key"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("unsupported equality\n1\n");
    }
}
