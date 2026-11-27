package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImplicitConstructorReflectionIntegrationTest extends CliIntegrationSupport {
    @Test
    void systemLoaderClassNameFlowRetainsConstructorsWithoutEarlyInitialization() throws Exception {
        final Path project = project("implicit-constructor-system-loader");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    String name = args.length == 0 ? "com.acme.First" : "com.acme.Second";
                    ClassLoader loader = ClassLoader.getSystemClassLoader();
                    Class<?> type = Class.forName(name, false, loader);
                    System.out.println("loaded");
                    Object value = type.getDeclaredConstructor().newInstance();
                    System.out.println(value.getClass().getName());
                }
            }
            """);
        writeJava(project, "com.acme.First", "package com.acme; public final class First { static { System.out.println(\"first-init\"); } public First() { System.out.println(\"first-ctor\"); } }");
        writeJava(project, "com.acme.Second", "package com.acme; public final class Second { static { System.out.println(\"second-init\"); } public Second() { System.out.println(\"second-ctor\"); } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { public Unrelated() { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/implicit-constructor-system-loader").toString();
        final ProcessResult first = process(project, List.of(binary));
        final ProcessResult second = process(project, List.of(binary, "second"));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo("loaded\nfirst-init\nfirst-ctor\ncom.acme.First\n");
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo("loaded\nsecond-init\nsecond-ctor\ncom.acme.Second\n");
    }

    @Test
    void directSystemLoaderLookupRetainsPublicConstructorAndInitializesBeforeInvocation() throws Exception {
        final Path project = project("implicit-public-constructor-system-loader");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Object value = Class.forName("com.acme.Target", true, ClassLoader.getSystemClassLoader())
                        .getConstructor().newInstance();
                    System.out.println(value.getClass().getName());
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { static { System.out.println(\"init\"); } public Target() { System.out.println(\"constructed\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/implicit-public-constructor-system-loader").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("init\nconstructed\ncom.acme.Target\n");
    }

    @Test
    void knownConstructorSupportsDynamicClassNameWithoutRetainingUnrelatedClasses() throws Exception {
        final Path project = project("implicit-dynamic-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Target.class.getDeclaredConstructor();
                    Object value = Class.forName(args[0]).getDeclaredConstructor().newInstance();
                    System.out.println(value.getClass().getName());
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { public Target() { System.out.println(\"created\"); } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { public Unrelated() { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/implicit-dynamic-constructor").toString();
        final ProcessResult known = process(project, List.of(binary, "com.acme.Target"));
        assertThat(known.exitCode()).as(known.stderr()).isZero();
        assertThat(known.stdout()).isEqualTo("created\ncom.acme.Target\n");
        final ProcessResult missing = process(project, List.of(binary, "com.acme.Unrelated"));
        assertThat(missing.exitCode()).isNotZero();
        assertThat(missing.stderr()).contains("ClassNotFoundException");
    }

    @Test
    void externalConstructorRequiresAStaticallyKnownOrDeclaredTarget() throws Exception {
        final Path project = project("implicit-external-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    System.out.println(Target.class.getName());
                    Object value = Class.forName(args[0]).getDeclaredConstructor().newInstance();
                    System.out.println(value.getClass().getName());
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { public Target() { } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(
            project.resolve(".javan/bin/implicit-external-constructor").toString(), "com.acme.Target"));
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stdout()).isEqualTo("com.acme.Target\n");
        assertThat(result.stderr()).contains("NoSuchMethodException");
    }

    @Test
    void classNameLiteralsThroughBranchesRetainTheirConstructors() throws Exception {
        final Path project = project("implicit-branched-class-names");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    String name = args.length == 0 ? "com.acme.First" : "com.acme.Second";
                    String alias = name;
                    Object value = Class.forName(alias).getDeclaredConstructor().newInstance();
                    System.out.println(value.getClass().getName());
                }
            }
            """);
        writeJava(project, "com.acme.First", "package com.acme; public final class First { public First() { } }");
        writeJava(project, "com.acme.Second", "package com.acme; public final class Second { public Second() { } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { public Unrelated() { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/implicit-branched-class-names").toString();
        final ProcessResult first = process(project, List.of(binary));
        final ProcessResult second = process(project, List.of(binary, "second"));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo("com.acme.First\n");
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo("com.acme.Second\n");
    }

    @Test
    void classLiteralsThroughBranchesRetainTheirConstructors() throws Exception {
        final Path project = project("implicit-branched-constructors");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Class<?> type = args.length == 0 ? First.class : Second.class;
                    Class<?> alias = type;
                    Object value = alias.getDeclaredConstructor().newInstance();
                    System.out.println(value.getClass().getName());
                }
            }
            """);
        writeJava(project, "com.acme.First", "package com.acme; public final class First { public First() { } }");
        writeJava(project, "com.acme.Second", "package com.acme; public final class Second { public Second() { } }");
        writeJava(project, "com.acme.Unrelated", "package com.acme; public final class Unrelated { public Unrelated() { System.setProperty(\"unused\", \"never\"); } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/implicit-branched-constructors").toString();
        final ProcessResult first = process(project, List.of(binary));
        final ProcessResult second = process(project, List.of(binary, "second"));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo("com.acme.First\n");
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo("com.acme.Second\n");
    }
}
