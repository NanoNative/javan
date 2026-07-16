package javan;

import javan.util.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ)
final class CliDependencyProjectIntegrationTest extends CliIntegrationSupport {
    @Test
    void dependencyJarStaticIntMethodBuilds() throws Exception {
        final Path dependency = dependencyJar("mathlib", "dep.MathLib", """
            package dep;

            public final class MathLib {
                private MathLib() {
                }

                public static int twice(final int value) {
                    return value * 2;
                }
            }
            """);
        final Path project = project("dependency-jar");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.MathLib;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(MathLib.twice(21));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-jar").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n");
    }

    @Test
    void checkWritesDependencyAndLicenseReportsForResolvedClasspath() throws Exception {
        final Path used = dependencyJarWithMavenLicense("usedlib", "dep.Used", """
            package dep;

            public final class Used {
                private Used() {
                }

                public static int value() {
                    return 7;
                }
            }
            """, "com.acme", "usedlib", "1.2.3", "Apache License, Version 2.0");
        final Path unused = dependencyJar("unusedlib", "unused.Unused", """
            package unused;

            public final class Unused {
                private Unused() {
                }

                public static int value() {
                    return 99;
                }
            }
            """);
        final Path project = project("dependency-reports");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Used;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Used.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", used.toString(), "--classpath", unused.toString());

        assertThat(run.exitCode()).isZero();
        final String dependencies = Files.readString(project.resolve(".javan/reports/dependencies.json"));
        final String licenses = Files.readString(project.resolve(".javan/reports/licenses.json"));
        assertThat(dependencies).contains(
            "\"dependencyCount\": 2",
            "\"usedDependencies\": 1",
            "\"unusedDependencies\": 1",
            "\"reachableDependencyClasses\": 1",
            Json.string(used.toAbsolutePath().normalize().toString()),
            "\"coordinate\": \"com.acme:usedlib:1.2.3\"",
            "\"used\": true",
            "\"reachableClasses\": [\"dep/Used\"]",
            Json.string(unused.toAbsolutePath().normalize().toString()),
            "\"used\": false",
            "\"classes\": [\"unused/Unused\"]"
        );
        assertThat(licenses).contains(
            "\"licenseCount\": 2",
            "\"knownLicenses\": 1",
            "\"unknownLicenses\": 1",
            "\"id\": \"Apache License, Version 2.0\"",
            "\"policy\": \"warning\""
        );
    }

    @Test
    void javanModMainDependencyCompilesWithoutClasspathOption() throws Exception {
        final Path dependency = dependencyJar("mod-mathlib", "dep.ModMath", """
            package dep;

            public final class ModMath {
                private ModMath() {
                }

                public static int value() {
                    return 11;
                }
            }
            """);
        final Path project = project("javan-mod-main-dependency");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.ModMath;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(ModMath.value());
                }
            }
            """);
        Files.writeString(project.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require main %s
            """.formatted(pathForMod(project, dependency)), StandardCharsets.UTF_8);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve("javan.lock"))).contains(
            "\"scope\": \"main\"",
            "\"notation\": " + Json.string(pathForMod(project, dependency)),
            "\"status\": \"present\"",
            "\"checksumAlgorithm\": \"fnv64\""
        );
        assertThat(Files.readString(project.resolve(".javan/reports/dependencies.json"))).contains(
            Json.string(dependency.toAbsolutePath().normalize().toString()),
            "\"source\": \"javan.mod\"",
            "\"used\": true",
            "\"reachableClasses\": [\"dep/ModMath\"]"
        );
    }

    @Test
    void javanModTestDependencyDoesNotSatisfyMainCompilation() throws Exception {
        final Path dependency = dependencyJar("mod-testlib", "dep.TestOnly", """
            package dep;

            public final class TestOnly {
                private TestOnly() {
                }

                public static int value() {
                    return 17;
                }
            }
            """);
        final Path project = project("javan-mod-test-dependency");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.TestOnly;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(TestOnly.value());
                }
            }
            """);
        Files.writeString(project.resolve("javan.mod"), """
            module com.acme.app
            java 25
            require test %s
            """.formatted(pathForMod(project, dependency)), StandardCharsets.UTF_8);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.stderr()).contains("error[JAVAN901]: javac failed", "package dep does not exist");
        assertThat(Files.readString(project.resolve("javan.lock"))).contains(
            "\"scope\": \"test\"",
            "\"status\": \"present\""
        );
    }

    @Test
    void dependencyJarObjectConstructorAndInstanceMethodBuilds() throws Exception {
        final Path dependency = dependencyJar("scalelib", "dep.Scale", """
            package dep;

            public final class Scale {
                private final int base;

                public Scale(final int base) {
                    this.base = base;
                }

                public int apply(final int value) {
                    return base * value;
                }
            }
            """);
        final Path project = project("dependency-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Scale;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Scale scale = new Scale(7);
                    System.out.println(scale.apply(6));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("42\n");
    }

    @Test
    void dependencyJarObjectStringReturnBuilds() throws Exception {
        final Path dependency = dependencyJar("messagelib", "dep.Message", """
            package dep;

            public final class Message {
                private final String value;

                public Message(final String value) {
                    this.value = value;
                }

                public String value() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-string-object");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Message;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("from-dependency");
                    System.out.println(message.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-string-object").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("from-dependency\n");
    }

    @Test
    void dependencyJarGenericPairGetterBuilds() throws Exception {
        final Path dependency = dependencyJar("pairlib", "dep.Pair", """
            package dep;

            public final class Pair<L, R> {
                private final L key;
                private final R value;

                public Pair(final L key, final R value) {
                    this.key = key;
                    this.value = value;
                }

                public L getKey() {
                    return key;
                }

                public R getValue() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-pair");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Pair;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Pair<String, String> pair = new Pair<>("key", "value");
                    System.out.println(pair.getValue());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-pair").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("value\n");
    }

    @Test
    void dependencyJarRecordAccessorBuilds() throws Exception {
        final Path dependency = dependencyJar("metriclib", "dep.Metric", """
            package dep;

            public record Metric(String name, long value) {
            }
            """);
        final Path project = project("dependency-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Metric;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Metric metric = new Metric("requests", 7L);
                    System.out.println(metric.name() + "=" + metric.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-record").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("requests=7\n");
    }

    @Test
    void dependencyJarNullableRecordAccessorBuilds() throws Exception {
        final Path dependency = dependencyJar("nullmetriclib", "dep.Metric", """
            package dep;

            public record Metric(String tenant, String name, String type, String unit) {
            }
            """);
        final Path project = project("dependency-nullable-record");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Metric;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Metric metric = new Metric(null, "requests", null, null);
                    System.out.println(metric.name());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-nullable-record").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("requests\n");
    }

    @Test
    void dependencyJarStaticDurationFormatterBuilds() throws Exception {
        final Path dependency = dependencyJar("durationlib", "dep.DurationFormatter", """
            package dep;

            public final class DurationFormatter {
                private DurationFormatter() {
                }

                public static String formatDuration(final long nanos) {
                    final long totalSeconds = nanos / 1_000_000_000L;
                    return (totalSeconds / 60L) + "m " + (totalSeconds % 60L) + "s";
                }
            }
            """);
        final Path project = project("dependency-duration");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.DurationFormatter;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(DurationFormatter.formatDuration(65_000_000_000L));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-duration").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("1m 5s\n");
    }

    @Test
    void dependencyJarScheduledExecutorSubclassBuilds() throws Exception {
        final Path dependency = dependencyJar("schedulerlib", "dep.Scheduler", """
            package dep;

            import java.util.concurrent.ScheduledThreadPoolExecutor;

            public final class Scheduler extends ScheduledThreadPoolExecutor {
                public Scheduler(final String name) {
                    super(1);
                }
            }
            """);
        final Path project = project("dependency-scheduler");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Scheduler;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Scheduler scheduler = new Scheduler("probe");
                    scheduler.schedule(new Task(), 0L, TimeUnit.MILLISECONDS);
                    scheduler.shutdown();
                    System.out.println(scheduler.awaitTermination(1L, TimeUnit.SECONDS));
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("tick");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-scheduler").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("tick\ntrue\n");
    }

    @Test
    void dependencyJarScheduledExecutorFixedRateBuilds() throws Exception {
        final Path dependency = dependencyJar("fixedratelib", "dep.Scheduler", """
            package dep;

            import java.util.concurrent.ScheduledThreadPoolExecutor;

            public final class Scheduler extends ScheduledThreadPoolExecutor {
                public Scheduler(final String name) {
                    super(1);
                }
            }
            """);
        final Path project = project("dependency-scheduler-fixed-rate");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Scheduler;
            import java.util.concurrent.TimeUnit;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    final Scheduler scheduler = new Scheduler("probe");
                    scheduler.scheduleAtFixedRate(new Task(), 200L, 50L, TimeUnit.MILLISECONDS);
                    Thread.sleep(20L);
                    scheduler.shutdown();
                    System.out.println(scheduler.awaitTermination(1L, TimeUnit.SECONDS));
                    System.out.println("done");
                }
            }
            """);
        writeJava(project, "com.acme.Task", """
            package com.acme;

            public final class Task implements Runnable {
                @Override
                public void run() {
                    System.out.println("tick");
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-scheduler-fixed-rate").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("true\ndone\n");
    }

    @Test
    void dependencyClassDirectoryObjectStringReturnBuilds() throws Exception {
        final Path dependency = dependencyClasses("messageclasses", "dep.Message", """
            package dep;

            public class Message {
                private final String value;

                public Message(final String value) {
                    this.value = value;
                }

                public String value() {
                    return value;
                }
            }
            """);
        final Path project = project("dependency-class-directory");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Message;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    final Message message = new Message("from-classes");
                    System.out.println(message.value());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-class-directory").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("from-classes\n");
    }

    @Test
    void dependencyJarMainDoesNotConfuseMainDetection() throws Exception {
        final Path dependency = dependencyJar("dep-main", "dep.Tool", """
            package dep;

            public final class Tool {
                private Tool() {
                }

                public static void main(final String[] args) {
                    System.out.println("dependency-main");
                }

                public static int value() {
                    return 7;
                }
            }
            """);
        final Path project = project("dependency-main");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Tool;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Tool.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-main").toString())).stdout()).isEqualTo("7\n");
    }

    @Test
    void unreachableForbiddenApiInsideDependencyDoesNotWarn() throws Exception {
        final Path dependency = dependencyJar("dep-dead-reflection", "dep.Safe", """
            package dep;

            public final class Safe {
                private Safe() {
                }

                public static int value() {
                    return 5;
                }

                public static void dead() throws ClassNotFoundException {
                    Class.forName("dep.Plugin");
                }
            }
            """);
        final Path project = project("dependency-dead-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Safe;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println(Safe.value());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).doesNotContain("warning[JAVAN101]");
    }

    @Test
    void reachableForbiddenApiInsideDependencyFails() throws Exception {
        final Path dependency = dependencyJar("dep-live-reflection", "dep.Loader", """
            package dep;

            public final class Loader {
                private Loader() {
                }

                public static void load() throws ClassNotFoundException {
                    Class.forName("dep.Plugin");
                }
            }
            """);
        final Path project = project("dependency-live-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Loader;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws ClassNotFoundException {
                    Loader.load();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "java/lang/Class.forName");
    }

    @Test
    void dependencyJarInetAddressGetAllByNameIpv4LiteralBuilds() throws Exception {
        final Path dependency = dependencyJar("dep-live-network", "dep.Lookup", """
            package dep;

            import java.net.InetAddress;

            public final class Lookup {
                private Lookup() {
                }

                public static String host(final String value) throws Exception {
                    return InetAddress.getAllByName(value)[0].getHostAddress();
                }
            }
            """);
        final Path project = project("dependency-live-network");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Lookup;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Lookup.host("127.0.0.1"));
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-live-network").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("127.0.0.1\n");
    }

    @Test
    void dependencyJarInetAddressGetByNameIpv4LiteralBuilds() throws Exception {
        final Path dependency = dependencyJar("dep-inet-address", "dep.Lookup", """
            package dep;

            import java.net.InetAddress;

            public final class Lookup {
                private Lookup() {
                }

                public static String host() throws Exception {
                    return InetAddress.getByName("127.0.0.1").getHostAddress();
                }
            }
            """);
        final Path project = project("dependency-inet-address");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Lookup;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Lookup.host());
                }
            }
            """);

        final String jvmOutput = runJvm(project, "com.acme.Main", List.of(dependency));
        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/dependency-inet-address").toString())).stdout()).isEqualTo(jvmOutput);
        assertThat(jvmOutput).isEqualTo("127.0.0.1\n");
    }

    @Test
    void dependencyJarInetAddressGetAllByNameDnsHostFailsClearlyAtRuntime() throws Exception {
        final Path dependency = dependencyJar("dep-live-network-runtime-fail", "dep.Lookup", """
            package dep;

            import java.net.InetAddress;

            public final class Lookup {
                private Lookup() {
                }

                public static String host(final String value) throws Exception {
                    return InetAddress.getAllByName(value)[0].getHostAddress();
                }
            }
            """);
        final Path project = project("dependency-live-network-runtime-fail");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            import dep.Lookup;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws Exception {
                    System.out.println(Lookup.host("example.com"));
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--classpath", dependency.toString());

        assertThat(run.exitCode()).isZero();
        final ProcessResult nativeRun = process(project, List.of(project.resolve(".javan/bin/dependency-live-network-runtime-fail").toString()));
        assertThat(nativeRun.exitCode()).isNotZero();
        assertThat(nativeRun.stderr()).contains("unsupported inet address host");
    }

    @Test
    void reachableReflectionFails() throws Exception {
        final Path project = project("reachable-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) throws ClassNotFoundException {
                    Class.forName("com.acme.Plugin");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN001]", "dynamic class loading is not supported");
    }

    @Test
    void unreachableReflectionWarnsOnly() throws Exception {
        final Path project = project("unreachable-reflection");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static void load() throws ClassNotFoundException {
                    Class.forName("com.acme.Plugin");
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("warning[JAVAN101]");
        assertThat(Files.readString(project.resolve(".javan/reports/diagnostics.txt"))).contains("warning[JAVAN101]");
    }

    @Test
    void manyUnreachableWarningsPrintCompactSummary() throws Exception {
        final Path project = project("many-unreachable-warnings");
        writeJava(project, "com.acme.Main", repeatedReflectionSource(13));

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains(
            "Warnings: 26",
            "full details: .javan/reports/diagnostics.txt",
            "warning[JAVAN101] unsupported API in unreachable code: 13",
            "warning[JAVAN131] unsupported JDK call in unreachable code: 13"
        );
        assertThat(run.stdout()).doesNotContain("Subject:");
    }

    @Test
    void reachableNonAsciiStringConstantFailsUntilUtf16StringModelExists() throws Exception {
        final Path project = project("non-ascii-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("caf\\u00e9".length());
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains(
            "error[JAVAN046]",
            "non-ASCII string constants require the UTF-16 string model"
        );
    }

    @Test
    void unreachableNonAsciiStringConstantWarnsOnly() throws Exception {
        final Path project = project("unreachable-non-ascii-string");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    System.out.println("ok");
                }

                public static int unused() {
                    return "caf\\u00e9".length();
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains(
            "warning[JAVAN146]",
            "non-ASCII string constant in unreachable code"
        );
    }

    @Test
    void noMainFails() throws Exception {
        final Path project = project("no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN020]");
    }

    @Test
    void multipleMainFailsWithoutGuessing() throws Exception {
        final Path project = project("multiple-main");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeJava(project, "com.acme.Tool", mainClass("tool"));

        final CliRun run = run(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("error[JAVAN022]", "com.acme.Main", "com.acme.Tool");
    }

    @Test
    void explicitMainSelectsCandidate() throws Exception {
        final Path project = project("explicit-main");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeJava(project, "com.acme.Tool", mainClass("tool"));

        final CliRun run = run(tempDir, "build", project.toString(), "--main", "com.acme.Tool", "--output", "selected");

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/selected").toString())).stdout()).isEqualTo("tool\n");
    }

    @Test
    void cleanRemovesOutputDirectory() throws Exception {
        final Path project = project("clean");
        Files.createDirectories(project.resolve(".javan/reports"));
        Files.writeString(project.resolve(".javan/reports/project.json"), "{}");

        final CliRun run = run(tempDir, "clean", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan")).doesNotExist();
    }

    @Test
    void sourceFileInputBuilds() throws Exception {
        final Path source = tempDir.resolve("Single.java");
        Files.writeString(source, """
            public final class Single {
                private Single() {
                }

                public static void main(final String[] args) {
                    System.out.println("single");
                }
            }
            """);

        final CliRun run = run(tempDir, "build", source.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(tempDir, List.of(tempDir.resolve(".javan/bin/single").toString())).stdout()).isEqualTo("single\n");
    }

    @Test
    void classesDirectoryInputChecks() throws Exception {
        final Path project = project("classes-input");
        writeJava(project, "com.acme.Main", mainClass("main"));
        final Path classes = project.resolve("manual-classes");
        Files.createDirectories(classes);
        assertThat(process(project, List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString())).exitCode())
            .isZero();

        final CliRun run = run(tempDir, "check", classes.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 1");
    }

    @Test
    void jarInputBuilds() throws Exception {
        final Path project = project("jar-input");
        writeJava(project, "com.acme.Main", mainClass("main"));
        final Path classes = project.resolve("classes");
        final Path jar = project.resolve("app.jar");
        Files.createDirectories(classes);
        assertThat(process(project, List.of(CliTestHarness.currentJavacCommand(), "-d", classes.toString(), project.resolve("src/main/java/com/acme/Main.java").toString())).exitCode())
            .isZero();
        assertThat(process(project, List.of(CliTestHarness.currentJarCommand(), "--create", "--file", jar.toString(), "--main-class", "com.acme.Main", "-C", classes.toString(), ".")).exitCode())
            .isZero();

        final CliRun run = run(tempDir, "build", jar.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/app").toString())).stdout()).isEqualTo("main\n");
    }

    @Test
    void jarBuildDoesNotRequireMain() throws Exception {
        final Path project = project("jar-no-main");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-no-main.jar")).exists();
        assertThat(Files.readString(project.resolve(".javan/reports/report.json"))).contains(
            "{\"name\": \"project\", \"status\": \"present\"",
            "{\"name\": \"resources\", \"status\": \"present\""
        );
    }

    @Test
    void jarAliasBuildDoesNotRequireMain() throws Exception {
        final Path project = project("jar-alias");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }

                public static int add(final int left, final int right) {
                    return left + right;
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--jar");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-alias.jar")).exists();
    }

    @Test
    void jarBuildIncludesResourceFile() throws Exception {
        final Path project = project("jar-resource");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);
        writeResource(project, "public/index.html", "<h1>javan</h1>\n");

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-resource.jar").toFile())) {
            assertThat(jar.getEntry("public/index.html")).isNotNull();
            assertThat(new String(jar.getInputStream(jar.getEntry("public/index.html")).readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<h1>javan</h1>\n");
        }
    }

    @Test
    void jarBuildRemovesDeletedResourceFile() throws Exception {
        final Path project = project("jar-resource-delete");
        writeJava(project, "com.acme.Library", """
            package com.acme;

            public final class Library {
                private Library() {
                }
            }
            """);
        final Path resource = writeResource(project, "public/stale.txt", "stale\n");
        assertThat(run(tempDir, "build", project.toString(), "--kind", "jar").exitCode()).isZero();
        Files.delete(resource);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-resource-delete.jar").toFile())) {
            assertThat(jar.getEntry("public/stale.txt")).isNull();
        }
    }

    @Test
    void jarBuildWritesMainClassManifestWhenMainExplicit() throws Exception {
        final Path project = project("jar-manifest");
        writeJava(project, "com.acme.Main", mainClass("main"));

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar", "--main", "com.acme.Main");

        assertThat(run.exitCode()).isZero();
        try (JarFile jar = new JarFile(project.resolve(".javan/dist/jar-manifest.jar").toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class")).isEqualTo("com.acme.Main");
        }
    }

    @Test
    void jarBuildAllowsJvmOnlyBytecode() throws Exception {
        final Path project = project("jar-jvm-only");
        writeJava(project, "com.acme.Main", """
            package com.acme;

            public final class Main {
                private Main() {
                }

                public static void main(final String[] args) {
                    synchronized (Main.class) {
                        System.out.println("jar");
                    }
                }
            }
            """);

        final CliRun run = run(tempDir, "build", project.toString(), "--kind", "jar", "--main", "com.acme.Main");

        assertThat(run.exitCode()).isZero();
        assertThat(project.resolve(".javan/dist/jar-jvm-only.jar")).exists();
    }

    @Test
    void nativeBuildCopiesResourcesToDistribution() throws Exception {
        final Path project = project("native-resources");
        writeJava(project, "com.acme.Main", mainClass("main"));
        writeResource(project, "assets/logo.txt", "logo\n");

        final CliRun run = run(tempDir, "build", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(Files.readString(project.resolve(".javan/resources/assets/logo.txt"))).isEqualTo("logo\n");
        assertThat(Files.readString(project.resolve(".javan/dist/resources/assets/logo.txt"))).isEqualTo("logo\n");
        assertThat(Files.readString(project.resolve(".javan/reports/resources.json")))
            .contains("\"resourceCount\": 1", "\"path\": \"assets/logo.txt\"");
    }

    @Test
    void inspectMavenProjectUsesArtifactId() throws Exception {
        final Path project = project("maven-project");
        Files.writeString(project.resolve("pom.xml"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>sharp-tool</artifactId>
              <version>1.0.0</version>
            </project>
            """);

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: MAVEN", ".javan/bin/sharp-tool");
    }

    @Test
    void mavenProjectCheckFindsClassesAfterCompile() throws Exception {
        final Path project = project("maven-check");
        Files.writeString(project.resolve("pom.xml"), """
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId>
              <artifactId>maven-check</artifactId>
              <version>1.0.0</version>
              <properties>
                <maven.compiler.release>25</maven.compiler.release>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
            </project>
            """);
        writeJava(project, "com.acme.Main", mainClass("main"));

        final CliRun run = runSlow(tempDir, "check", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("reachable methods: 1");
        assertThat(project.resolve("target/classes/com/acme/Main.class")).exists();
    }

    @Test
    void inspectGradleProjectUsesRootProjectName() throws Exception {
        final Path project = project("gradle-project");
        Files.writeString(project.resolve("settings.gradle"), "rootProject.name = 'blade-tool'\n");
        Files.writeString(project.resolve("build.gradle"), "plugins { id 'java' }\n");

        final CliRun run = run(tempDir, "inspect", project.toString());

        assertThat(run.exitCode()).isZero();
        assertThat(run.stdout()).contains("Project: GRADLE", ".javan/bin/blade-tool");
    }
}
