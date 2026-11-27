package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliReflectionConstructorFlowIntegrationTest extends CliIntegrationSupport {
    @Test
    void literalClassForNameThroughLocalRetainsOnlyItsConstructor() throws Exception {
        final Path project = project("reflection-local-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[0]; }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Class.forName("com.acme.Target");
                    Class<?> alias = type;
                    Object value = alias.getDeclaredConstructor(String.class).newInstance("local");
                    System.out.println(((Target) value).message());
                }
            }
            """);
        writeJava(project, "com.acme.Target", """
            package com.acme;
            public final class Target {
                private final String value;
                public Target(String value) { this.value = value; }
                public Target(int unused) { throw new IllegalStateException("unused overload"); }
                public String message() { return value; }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project,
            List.of(project.resolve(".javan/bin/reflection-local-constructor").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("local\n");
        assertThat(generatedProgramSource(project)).doesNotContain("unused overload");
    }
    @Test
    void conditionalClassTargetUsesDeclaredFallbacks() throws Exception {
        final Path project = project("reflection-conditional-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() throws Exception {
                    return new AnnotatedElement[] { First.class.getDeclaredConstructor(), Second.class.getDeclaredConstructor() };
                }
                public static void main(String[] args) throws Exception {
                    Class<?> type = First.class;
                    if (args.length > 0) type = Second.class;
                    System.out.println(type.getDeclaredConstructor().newInstance().getClass().getSimpleName());
                }
            }
            """);
        writeJava(project, "com.acme.First", "package com.acme; public final class First { public First() { } }");
        writeJava(project, "com.acme.Second", "package com.acme; public final class Second { public Second() { } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final String binary = project.resolve(".javan/bin/reflection-conditional-constructor").toString();
        final ProcessResult first = process(project, List.of(binary));
        final ProcessResult second = process(project, List.of(binary, "second"));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo("First\n");
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo("Second\n");
    }

    @Test
    void reassignedClassLocalKeepsTheLastTarget() throws Exception {
        final Path project = project("reflection-reassigned-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[0]; }
                public static void main(String[] args) throws Exception {
                    Class<?> type = First.class;
                    type = Second.class;
                    System.out.println(type.getDeclaredConstructor().newInstance().getClass().getSimpleName());
                }
            }
            """);
        writeJava(project, "com.acme.First", """
            package com.acme;
            public final class First { public First() { System.out.println("unreachable constructor"); } }
            """);
        writeJava(project, "com.acme.Second", "package com.acme; public final class Second { public Second() { } }");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-reassigned-constructor").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("Second\n");
        assertThat(generatedProgramSource(project)).doesNotContain("unreachable constructor");
    }

    @Test
    void publicConstructorLookupRetainsExactOverloadAndRejectsPrivateConstructors() throws Exception {
        final Path project = project("reflection-public-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[0]; }
                public static void main(String[] args) throws Exception {
                    Object value = Target.class.getConstructor(String.class).newInstance("public");
                    System.out.println(((Target) value).message());
                    try { Target.class.getConstructor(int.class); }
                    catch (NoSuchMethodException expected) { System.out.println("private"); }
                }
            }
            """);
        writeJava(project, "com.acme.Target", """
            package com.acme;
            public final class Target {
                private final String value;
                public Target(String value) { this.value = value; }
                private Target(int unused) { throw new IllegalStateException("private constructor body"); }
                public String message() { return value; }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-public-constructor").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("public\nprivate\n");
        assertThat(generatedProgramSource(project)).doesNotContain("private constructor body");
    }

    @Test
    void publicConstructorDeclarationAllowsExternalClassName() throws Exception {
        final Path project = project("reflection-public-constructor-declaration");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() throws Exception {
                    return new AnnotatedElement[] { Target.class.getConstructor(String.class) };
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(javanReflectionTargets().length);
                    Object value = Class.forName(args.length == 0 ? "com.acme.Target" : args[0]).getConstructor(String.class).newInstance("declared");
                    System.out.println(((Target) value).message());
                }
            }
            """);
        writeJava(project, "com.acme.Target", """
            package com.acme;
            public final class Target {
                private final String value;
                public Target(String value) { this.value = value; }
                public String message() { return value; }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-public-constructor-declaration").toString(), "com.acme.Target"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("1\ndeclared\n");
    }

    @Test
    void publicLookupDoesNotExposeDeclaredPrivateConstructor() throws Exception {
        final Path project = project("reflection-public-private-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() throws Exception {
                    return new AnnotatedElement[] { Target.class.getDeclaredConstructor(int.class) };
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(Target.class.getDeclaredConstructor(int.class) != null);
                    try { Target.class.getConstructor(int.class); }
                    catch (NoSuchMethodException expected) { System.out.println("private"); }
                }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { private Target(int value) { } }");
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-public-private-constructor").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nprivate\n");
    }

    @Test
    void publicConstructorDeclarationRejectsPrivateTarget() throws Exception {
        final Path project = project("reflection-invalid-public-constructor");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static AnnotatedElement[] javanReflectionTargets() throws Exception {
                    return new AnnotatedElement[] { Target.class.getConstructor(int.class) };
                }
                public static void main(String[] args) { }
            }
            """);
        writeJava(project, "com.acme.Target", "package com.acme; public final class Target { private Target(int value) { } }");
        final CliRun check = run(tempDir, "check", project.toString());
        assertThat(check.exitCode()).isNotZero();
        assertThat(check.stderr()).contains("JAVAN096");
    }

}
