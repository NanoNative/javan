package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliConstructorMetadataIntegrationTest extends CliIntegrationSupport {
    @Test
    void constructorMetadataReportsDeclaringClassParametersAndModifiers() throws Exception {
        final Path project = project("constructor-metadata");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Constructor<Target> constructor = Target.class.getDeclaredConstructor(String.class, int.class);
                    System.out.println(constructor.getDeclaringClass().getName());
                    System.out.println(constructor.getParameterCount());
                    Class<?>[] types = constructor.getParameterTypes();
                    System.out.println(types[0].getName() + ":" + types[1].getName());
                    types[0] = Object.class;
                    System.out.println(constructor.getParameterTypes()[0].getName());
                    System.out.println((constructor.getModifiers() & 2) != 0);
                }
                public static final class Target { private Target(String value, int count) { } }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-metadata").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void nullConstructorMetadataReceiverThrowsNullPointerException() throws Exception {
        final Path project = project("constructor-metadata-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static void main(String[] args) {
                    Constructor<?> constructor = null;
                    try { System.out.println(constructor.getParameterCount()); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-metadata-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("null\n");
    }
    @RepeatedTest(4)
    void constructorParameterTypesRemainValidDuringConcurrentGc() throws Exception {
        final Path project = project("constructor-metadata-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Target { public Target(String value, int count) { } }
                static final class Worker implements Runnable {
                    private final Constructor<?> constructor;
                    Worker(Constructor<?> constructor) { this.constructor = constructor; }
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            Class<?>[] types = constructor.getParameterTypes();
                            if (types[0] == String.class && types[1] == int.class) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Constructor<?> constructor = Target.class.getDeclaredConstructor(String.class, int.class);
                    Thread first = new Thread(new Worker(constructor));
                    Thread second = new Thread(new Worker(constructor));
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/constructor-metadata-gc").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("200\n");
    }

    @Test
    void methodParameterTypesRemainValidDuringConcurrentGc() throws Exception {
        final Path project = project("method-metadata-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Method;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger successes = new AtomicInteger();
                static final class Target { public void run(String value) { } }
                static final class Worker implements Runnable {
                    private final Method method;
                    Worker(Method method) { this.method = method; }
                    public void run() {
                        for (int i = 0; i < 100; i++) {
                            Class<?>[] types = method.getParameterTypes();
                            if (types[0] == String.class) successes.incrementAndGet();
                        }
                    }
                }
                public static void main(String[] args) throws Exception {
                    Method method = Target.class.getDeclaredMethod("run", String.class);
                    Thread first = new Thread(new Worker(method));
                    Thread second = new Thread(new Worker(method));
                    first.start(); second.start(); first.join(); second.join();
                    System.out.println(successes.get());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/method-metadata-gc").toString()), Duration.ofSeconds(60),
            Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("200\n");
    }

}
