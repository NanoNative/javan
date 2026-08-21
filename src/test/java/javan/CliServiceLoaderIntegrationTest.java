package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

@Execution(SAME_THREAD)
@ResourceLock("native-cli-heavy")
@NativeTest
final class CliServiceLoaderIntegrationTest extends CliIntegrationSupport {
    @Test
    void nativeBuildLoadsStandardDescriptorLazilyAndReloads() throws Exception {
        final Path project = project("service-loader");
        writeJava(project, "com.acme.Greeter", """
            package com.acme;
            public interface Greeter { String greeting(); }
            """);
        writeJava(project, "com.acme.HelloGreeter", """
            package com.acme;
            public final class HelloGreeter implements Greeter {
                public static int created;
                public HelloGreeter() { created++; }
                public String greeting() { return "hello"; }
            }
            """);
        writeJava(project, "com.acme.GoodbyeGreeter", """
            package com.acme;
            public final class GoodbyeGreeter implements Greeter {
                public static int created;
                public GoodbyeGreeter() { created++; }
                public String greeting() { return "goodbye"; }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ServiceLoader;
            public final class Main {
                private Main() {}
                public static void main(String[] args) {
                    ServiceLoader<Greeter> loader = ServiceLoader.load(Greeter.class);
                    System.out.println(HelloGreeter.created);
                    var iterator = loader.iterator();
                    System.out.println(iterator.hasNext());
                    System.out.println(iterator.next().greeting());
                    System.out.println(HelloGreeter.created);
                    System.out.println(GoodbyeGreeter.created);
                    System.out.println(iterator.next().greeting());
                    System.out.println(GoodbyeGreeter.created);
                    System.out.println(loader.findFirst().orElseThrow().greeting());
                    System.out.println(ServiceLoader.loadInstalled(Greeter.class).findFirst().isEmpty());
                    loader.reload();
                    System.out.println(loader.findFirst().orElseThrow().greeting());
                    System.out.println(HelloGreeter.created);
                }
            }
            """);
        writeResource(project, "META-INF/services/com.acme.Greeter",
            "com.acme.HelloGreeter\ncom.acme.GoodbyeGreeter\n");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = runSlow(tempDir, "build", project.toString(), "--release");

        assertThat(run.exitCode()).withFailMessage(run.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project,
            List.of(project.resolve(".javan/bin/service-loader").toString()),
            Duration.ofSeconds(20),
            Map.of("JAVAN_GC_STRESS", "1", "JAVAN_GC_SAFEPOINT_INTERVAL", "1")
        );
        assertThat(nativeRun.exitCode()).withFailMessage(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout())
            .isEqualTo(jvmOutput)
            .isEqualTo("0\ntrue\nhello\n1\n0\ngoodbye\n1\nhello\ntrue\nhello\n2\n");
    }

    @Test
    void nativeBuildReturnsEmptyLoaderWithoutProviders() throws Exception {
        final Path project = project("service-loader-empty");
        writeJava(project, "com.acme.Greeter", "package com.acme; public interface Greeter {}\n");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ServiceLoader;
            public final class Main {
                private Main() {}
                public static void main(String[] args) {
                    ServiceLoader<Greeter> loader = ServiceLoader.load(Greeter.class);
                    System.out.println(loader.iterator().hasNext());
                    System.out.println(loader.findFirst().isEmpty());
                    System.out.println(ServiceLoader.load(Greeter.class, null).iterator().hasNext());
                    System.out.println(ServiceLoader.loadInstalled(Greeter.class).findFirst().isEmpty());
                }
            }
            """);

        final CliRun run = runSlow(tempDir, "build", project.toString());

        assertThat(run.exitCode()).withFailMessage(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/service-loader-empty").toString())).stdout())
            .isEqualTo("false\ntrue\nfalse\ntrue\n");
    }

    @Test
    void checkRejectsInvalidDescriptorProvider() throws Exception {
        final Path project = project("service-loader-invalid");
        writeJava(project, "com.acme.Greeter", "package com.acme; public interface Greeter {}\n");
        writeJava(project, "com.acme.WrongType", "package com.acme; public final class WrongType {}\n");
        writeJava(project, "com.acme.HiddenProvider", """
            package com.acme;
            final class HiddenProvider implements Greeter { public HiddenProvider() {} }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ServiceLoader;
            public final class Main {
                private Main() {}
                public static void main(String[] args) {
                    System.out.println(ServiceLoader.load(Greeter.class).findFirst().isEmpty());
                }
            }
            """);
        writeResource(project, "META-INF/services/com.acme.Greeter", "com.acme.Missing\n");

        final CliRun missing = run(tempDir, "check", project.toString());
        assertThat(missing.exitCode()).isEqualTo(2);
        assertThat(missing.stderr()).contains("error[JAVAN079]", "com/acme/Greeter -> com/acme/Missing");

        writeResource(project, "META-INF/services/com.acme.Greeter", "com.acme.WrongType\n");
        final CliRun wrongType = run(tempDir, "check", project.toString());
        assertThat(wrongType.exitCode()).isEqualTo(2);
        assertThat(wrongType.stderr()).contains("com/acme/Greeter -> com/acme/WrongType", "not assignable");

        writeResource(project, "META-INF/services/com.acme.Greeter", "com.acme.HiddenProvider\n");
        final CliRun hidden = run(tempDir, "check", project.toString());
        assertThat(hidden.exitCode()).isEqualTo(2);
        assertThat(hidden.stderr()).contains(
            "com/acme/Greeter -> com/acme/HiddenProvider",
            "public no-argument constructor"
        );
    }

    @Test
    void unusedServiceDescriptorDoesNotEnterTheClosedWorld() throws Exception {
        final Path project = project("service-loader-unused");
        writeJava(project, "com.acme.Greeter", "package com.acme; public interface Greeter {}\n");
        writeJava(project, "com.acme.Unused", "package com.acme; public interface Unused {}\n");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.NoSuchElementException;
            import java.util.ServiceLoader;
            public final class Main {
                private Main() {}
                public static void main(String[] args) {
                    System.out.println((Object) Unused.class == Greeter.class);
                    var iterator = ServiceLoader.load(Greeter.class).iterator();
                    System.out.println(iterator.hasNext());
                    try {
                        iterator.next();
                    } catch (NoSuchElementException expected) {
                        System.out.println("exhausted");
                    }
                }
            }
            """);
        writeResource(project, "META-INF/services/com.acme.Unused", "com.acme.Missing\n");

        final String jvmOutput = runJvmWithResources(project, "com.acme.Main");
        final CliRun run = run(tempDir, "build", "--release", project.toString());

        assertThat(run.exitCode()).withFailMessage(run.stderr()).isZero();
        final ProcessResult nativeRun = process(
            project, List.of(project.resolve(".javan/bin/service-loader-unused").toString())
        );
        assertThat(nativeRun.exitCode()).withFailMessage(nativeRun.stderr()).isZero();
        assertThat(nativeRun.stdout())
            .isEqualTo(jvmOutput)
            .isEqualTo("false\nfalse\nexhausted\n");
    }

    @Test
    void nativeBuildLoadsModuleProviderFactory() throws Exception {
        final Path project = project("service-loader-module");
        writeJava(project, "module-info", """
            module example.module {
                exports com.acme;
                uses com.acme.Greeter;
                provides com.acme.Greeter with com.acme.GreeterProvider;
            }
            """);
        writeJava(project, "com.acme.Greeter", """
            package com.acme;
            public interface Greeter { String greeting(); }
            """);
        writeJava(project, "com.acme.HelloGreeter", """
            package com.acme;
            public final class HelloGreeter implements Greeter {
                public String greeting() { return "module"; }
            }
            """);
        writeJava(project, "com.acme.GreeterProvider", """
            package com.acme;
            public final class GreeterProvider {
                private GreeterProvider() {}
                public static Greeter provider() { return new HelloGreeter(); }
            }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ServiceLoader;
            public final class Main {
                private Main() {}
                public static void main(String[] args) {
                    System.out.println(ServiceLoader.load(Greeter.class).findFirst().orElseThrow().greeting());
                }
            }
            """);

        final CliRun run = runSlow(tempDir, "build", project.toString());

        assertThat(run.exitCode()).withFailMessage(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/service-loader-module").toString())).stdout())
            .isEqualTo("module\n");
    }

    @Test
    void namedModuleMustDeclareServiceUse() throws Exception {
        final Path project = project("service-loader-module-uses");
        writeJava(project, "module-info", """
            module example.module {
                exports com.acme;
                provides com.acme.Greeter with com.acme.HelloGreeter;
            }
            """);
        writeJava(project, "com.acme.Greeter", "package com.acme; public interface Greeter {}\n");
        writeJava(project, "com.acme.HelloGreeter", """
            package com.acme;
            public final class HelloGreeter implements Greeter { public HelloGreeter() {} }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.util.ServiceConfigurationError;
            import java.util.ServiceLoader;
            public final class Main {
                private Main() {}
                public static void main(String[] args) {
                    try {
                        ServiceLoader.load(Greeter.class);
                    } catch (ServiceConfigurationError expected) {
                        System.out.println("missing-uses");
                    }
                    try {
                        ServiceLoader.loadInstalled(Greeter.class);
                    } catch (ServiceConfigurationError expected) {
                        System.out.println("missing-installed-uses");
                    }
                }
            }
            """);

        final Path classes = project.resolve("jvm-classes");
        java.nio.file.Files.createDirectories(classes);
        final ProcessResult compile = process(project, List.of(
            CliTestHarness.currentJavacCommand(), "-d", classes.toString(),
            project.resolve("src/main/java/module-info.java").toString(),
            project.resolve("src/main/java/com/acme/Greeter.java").toString(),
            project.resolve("src/main/java/com/acme/HelloGreeter.java").toString(),
            project.resolve("src/main/java/com/acme/Main.java").toString()
        ));
        assertThat(compile.exitCode()).withFailMessage(compile.stderr()).isZero();
        final ProcessResult jvm = process(project, List.of(
            CliTestHarness.currentJavaCommand(), "--module-path", classes.toString(),
            "--module", "example.module/com.acme.Main"
        ));
        assertThat(jvm.exitCode()).withFailMessage(jvm.stderr()).isZero();

        final CliRun run = runSlow(tempDir, "build", project.toString());

        assertThat(run.exitCode()).withFailMessage(run.stderr()).isZero();
        assertThat(process(project, List.of(project.resolve(".javan/bin/service-loader-module-uses").toString())).stdout())
            .isEqualTo(jvm.stdout())
            .isEqualTo("missing-uses\nmissing-installed-uses\n");
    }
}
