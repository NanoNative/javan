package javan;

import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliConstructorAccessIntegrationTest extends CliIntegrationSupport {
    @Test
    void privateConstructorAccessCanBeEnabledAndReset() throws Exception {
        final Path project = project("constructor-access-override");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Constructor<Target> constructor = Target.class.getDeclaredConstructor(String.class);
                    Object[] arguments = { "hello" };
                    System.out.println(constructor.canAccess(null));
                    try { constructor.newInstance(arguments); }
                    catch (IllegalAccessException expected) { System.out.println("denied"); }
                    System.out.println(constructor.trySetAccessible());
                    System.out.println(constructor.canAccess(null));
                    System.out.println(constructor.newInstance(arguments).message());
                    constructor.setAccessible(false);
                    System.out.println(constructor.canAccess(null));
                    constructor.setAccessible(true);
                    System.out.println(constructor.newInstance(arguments).message());
                    System.out.println(constructor.isAccessible());
                    java.lang.reflect.AccessibleObject generic = constructor;
                    generic.setAccessible(false);
                    System.out.println(generic.isAccessible());
                    System.out.println(generic.trySetAccessible());
                    System.out.println(generic.canAccess(null));
                }
            }
            """);
        writeJava(project, "com.acme.Target", """
            package com.acme;
            public final class Target {
                private final String value;
                private Target(String value) { this.value = value; }
                public String message() { return value; }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-access-override").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("false\ndenied\ntrue\ntrue\nhello\nfalse\nhello\ntrue\nfalse\ntrue\ntrue\n");
    }

    @Test
    void constructorCanAccessRejectsNonNullTarget() throws Exception {
        final Path project = project("constructor-access-target");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Constructor<Main> constructor = Main.class.getConstructor();
                    Object target = new Object();
                    try { constructor.canAccess(target); }
                    catch (IllegalArgumentException expected) { System.out.println("invalid target"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-access-target").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("invalid target\n");
    }
    @Test
    void nullConstructorAccessReceiverThrowsNullPointerException() throws Exception {
        final Path project = project("constructor-access-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Constructor;
            public final class Main {
                public static void main(String[] args) {
                    Constructor<?> constructor = null;
                    try { constructor.trySetAccessible(); }
                    catch (NullPointerException expected) { System.out.println("null"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/constructor-access-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("null\n");
    }

}
