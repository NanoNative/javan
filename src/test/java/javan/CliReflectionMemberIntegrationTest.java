package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliReflectionMemberIntegrationTest extends CliIntegrationSupport {
    @Test
    void executableAndMemberExposeConstructorAndMethodMetadata() throws Exception {
        final Path project = project("reflection-executable-member");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Executable;
            import java.lang.reflect.Member;
            public final class Main {
                public static final class Target {
                    public Target(String value) { }
                    public void run(String value) { }
                }
                public static void main(String[] args) throws Exception {
                    Executable constructor = Target.class.getConstructor(String.class);
                    Executable method = Target.class.getDeclaredMethod("run", String.class);
                    Member constructorMember = constructor;
                    Member methodMember = method;
                    System.out.println(constructor.getName()); System.out.println(method.getName());
                    System.out.println(constructor.getParameterCount()); System.out.println(method.getParameterCount());
                    System.out.println(constructor.getParameterTypes()[0].getName());
                    System.out.println(method.getParameterTypes()[0].getName());
                    System.out.println(constructorMember.getDeclaringClass().getName());
                    System.out.println(methodMember.getName());
                    System.out.println(constructorMember.getModifiers() == methodMember.getModifiers());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-executable-member").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void nullMemberMetadataReceiverThrowsNullPointerException() throws Exception {
        final Path project = project("reflection-member-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Member;
            public final class Main {
                public static void main(String[] args) {
                    Member member = null;
                    try { member.getName(); }
                    catch (NullPointerException expected) { System.out.println("null member"); }
                    java.lang.reflect.Executable executable = null;
                    try { executable.getParameterCount(); }
                    catch (NullPointerException expected) { System.out.println("null executable"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-member-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("null member\nnull executable\n");
    }
}
