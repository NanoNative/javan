package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliConstructorNameIntegrationTest extends CliIntegrationSupport {
    @Test
    void constructorNameIsTheDeclaringBinaryClassName() throws Exception {
        final Path project = project("constructor-reflection-name");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static final class Nested { public Nested() { } }
                public static void main(String[] args) throws Exception {
                    Constructor<Main> outer = Main.class.getConstructor();
                    Constructor<Nested> nested = Nested.class.getConstructor();
                    System.out.println(outer.getName());
                    System.out.println(nested.getName());
                    System.out.println(nested.getName().equals(nested.getDeclaringClass().getName()));
                    System.out.println(Main.class.getDeclaredMethod("main", String[].class).getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-reflection-name").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("com.acme.Main\ncom.acme.Main$Nested\ntrue\nmain\n");
    }

    @Test
    void nullConstructorNameReceiverThrowsNullPointerException() throws Exception {
        final Path project = project("constructor-reflection-name-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static void main(String[] args) {
                    Constructor<?> value = null;
                    try { value.getName(); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-reflection-name-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("null\n");
    }
    @Test
    void constructorNamesRemainValidDuringConcurrentGc() throws Exception {
        final Path project = project("constructor-reflection-name-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                public static final class Target { public Target() { } }
                static final class Worker implements Runnable {
                    final Constructor<?> constructor;
                    Worker(Constructor<?> constructor) { this.constructor = constructor; }
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            if (constructor.getName().equals("com.acme.Main$Target")) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Constructor<?> constructor = Target.class.getConstructor();
                    Thread a = new Thread(new Worker(constructor)); Thread b = new Thread(new Worker(constructor));
                    a.start(); b.start(); a.join(); b.join(); System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-reflection-name-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("200\n");
    }

}
