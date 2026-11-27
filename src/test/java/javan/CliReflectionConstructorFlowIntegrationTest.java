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

}
