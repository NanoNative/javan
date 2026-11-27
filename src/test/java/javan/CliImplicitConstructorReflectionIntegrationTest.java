package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliImplicitConstructorReflectionIntegrationTest extends CliIntegrationSupport {
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
