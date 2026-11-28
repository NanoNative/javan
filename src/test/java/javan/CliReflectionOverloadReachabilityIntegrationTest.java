package javan;

import java.nio.file.Path;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliReflectionOverloadReachabilityIntegrationTest extends CliIntegrationSupport {
    @Test
    void retainsOnlyTheProvenReflectiveMethodOverload() throws Exception {
        final Path project = project("reflection-proven-overload");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run(String value) { return "selected:" + value; }
                    public String run(int value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    var method = Target.class.getDeclaredMethod("run", String.class);
                    System.out.println(method.invoke(new Target(), "value"));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-proven-overload").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("selected:value\n");
    }
    @Test
    void excludesTheSameSignatureOnAnUnrelatedRetainedClass() throws Exception {
        final Path project = project("reflection-proven-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run(String value) { return "selected:" + value; }
                }
                public static final class Unrelated {
                    public String id() { return "unrelated"; }
                    public String run(String value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(new Unrelated().id());
                    var method = Target.class.getDeclaredMethod("run", String.class);
                    System.out.println(method.invoke(new Target(), "value"));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-proven-owner").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("unrelated\nselected:value\n");
    }

    @Test
    void retainsMethodsForBothPossibleDynamicReceiverClasses() throws Exception {
        final Path project = project("reflection-dynamic-owner");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class First { public static String run(String value) { return "first:" + value; } }
                public static final class Second { public static String run(String value) { return "second:" + value; } }
                public static void main(String[] args) throws Exception {
                    Class<?> target = args.length == 0 ? First.class : Second.class;
                    var method = target.getDeclaredMethod("run", String.class);
                    System.out.println(method.invoke(null, "value"));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/reflection-dynamic-owner");
        final ProcessResult first = process(project, List.of(binary.toString()));
        assertThat(first.exitCode()).as(first.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo(expected).isEqualTo("first:value\n");
        final ProcessResult second = process(project, List.of(binary.toString(), "second"));
        final ProcessResult secondJvm = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            project.resolve("jvm-classes").toString(), "com.acme.Main", "second"));
        assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(secondJvm.exitCode()).as(secondJvm.stderr()).isZero();
        assertThat(second.stdout()).isEqualTo(secondJvm.stdout()).isEqualTo("second:value\n");
    }

    @Test
    void retainsMethodsForEachReferencedReceiverClass() throws Exception {
        final Path project = project("reflection-two-proven-owners");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class First { public static String run(String value) { return "first:" + value; } }
                public static final class Second { public static String run(String value) { return "second:" + value; } }
                public static void main(String[] args) throws Exception {
                    System.out.println(First.class.getDeclaredMethod("run", String.class).invoke(null, "value"));
                    System.out.println(Second.class.getDeclaredMethod("run", String.class).invoke(null, "value"));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-two-proven-owners").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("first:value\nsecond:value\n");
    }

    @Test
    void retainsTheProvenPrimitiveParameterOverload() throws Exception {
        final Path project = project("reflection-proven-primitive-overload");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run(int value) { return "selected:" + value; }
                    public String run(String value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    var method = Target.class.getDeclaredMethod("run", int.class);
                    System.out.println(method.invoke(new Target(), 7));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-proven-primitive-overload").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("selected:7\n");
    }

    @Test
    void retainsEachReferencedOverload() throws Exception {
        final Path project = project("reflection-two-proven-overloads");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run(String value) { return "string:" + value; }
                    public String run(int value) { return "int:" + value; }
                    public String run(long value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    var stringMethod = Target.class.getDeclaredMethod("run", String.class);
                    var intMethod = Target.class.getDeclaredMethod("run", int.class);
                    System.out.println(stringMethod.invoke(new Target(), "value"));
                    System.out.println(intMethod.invoke(new Target(), 7));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-two-proven-overloads").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("string:value\nint:7\n");
    }

    @Test
    void retainsAllNameMatchesWhenParameterTypesAreDynamic() throws Exception {
        final Path project = project("reflection-dynamic-overload-parameters");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public void run(String value) { }
                    public void run(Integer value) { }
                }
                public static void main(String[] args) throws Exception {
                    Class<?>[] types;
                    if (args.length == 0) types = new Class<?>[] { String.class };
                    else types = new Class<?>[] { Integer.class };
                    var method = Target.class.getDeclaredMethod("run", types);
                    System.out.println(method.getParameterTypes()[0].getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-dynamic-overload-parameters").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("java.lang.String\n");
        final ProcessResult jvmWithArgument = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            project.resolve("jvm-classes").toString(), "com.acme.Main", "argument"));
        assertThat(jvmWithArgument.exitCode()).as(jvmWithArgument.stderr()).isZero();
        final ProcessResult nativeWithArgument = process(project, List.of(
            project.resolve(".javan/bin/reflection-dynamic-overload-parameters").toString(), "argument"));
        assertThat(nativeWithArgument.exitCode()).as(nativeWithArgument.stderr()).isZero();
        assertThat(nativeWithArgument.stdout()).isEqualTo(jvmWithArgument.stdout()).isEqualTo("java.lang.Integer\n");
    }

    @Test
    void retainsOnlyTheNoArgumentOverloadForANullParameterArray() throws Exception {
        final Path project = project("reflection-null-parameter-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run() { return "selected"; }
                    public String run(String value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    var method = Target.class.getDeclaredMethod("run", (Class<?>[]) null);
                    System.out.println(method.invoke(new Target()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-null-parameter-array").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("selected\n");
    }

    @Test
    void retainsAnOverrideForTheProvenReflectiveSignature() throws Exception {
        final Path project = project("reflection-proven-overload-override");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static class Base {
                    public String run(String value) { return "base:" + value; }
                    public String run(int value) { System.setProperty("unusedBase", "never"); return "unused"; }
                }
                public static final class Child extends Base {
                    public String run(String value) { return "child:" + value; }
                    public String run(int value) { System.setProperty("unusedChild", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    var method = Base.class.getDeclaredMethod("run", String.class);
                    System.out.println(method.invoke(new Child(), "value"));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-proven-overload-override").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("child:value\n");
    }

    @Test
    void retainsOnlyTheProvenNoArgumentOverload() throws Exception {
        final Path project = project("reflection-proven-noarg-overload");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run() { return "selected"; }
                    public String run(int value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static void main(String[] args) throws Exception {
                    var method = Target.class.getDeclaredMethod("run");
                    System.out.println(method.invoke(new Target()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-proven-noarg-overload").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("selected\n");
    }

    @Test
    void invokesDynamicParameterOverloadsWithoutDiscardingEitherTarget() throws Exception {
        final Path project = project("reflection-dynamic-overload-invocation");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target {
                    public String run(String value) { return "string:" + value; }
                    public String run(Integer value) { return value.intValue() == 7 ? "integer:7" : "bad"; }
                }
                public static void main(String[] args) throws Exception {
                    Class<?>[] types;
                    Object argument;
                    if (args.length == 0) { types = new Class<?>[] { String.class }; argument = "value"; }
                    else { types = new Class<?>[] { Integer.class }; argument = Integer.valueOf(7); }
                    var method = Target.class.getDeclaredMethod("run", types);
                    System.out.println(method.invoke(new Target(), argument));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-dynamic-overload-invocation").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("string:value\n");
        final ProcessResult jvmWithArgument = process(project, List.of(CliTestHarness.currentJavaCommand(), "-cp",
            project.resolve("jvm-classes").toString(), "com.acme.Main", "argument"));
        assertThat(jvmWithArgument.exitCode()).as(jvmWithArgument.stderr()).isZero();
        final ProcessResult nativeWithArgument = process(project, List.of(
            project.resolve(".javan/bin/reflection-dynamic-overload-invocation").toString(), "argument"));
        assertThat(nativeWithArgument.exitCode()).as(nativeWithArgument.stderr()).isZero();
        assertThat(nativeWithArgument.stdout()).isEqualTo(jvmWithArgument.stdout()).isEqualTo("integer:7\n");
    }

    @Test
    void retainsTheProvenInheritedPublicOverload() throws Exception {
        final Path project = project("reflection-proven-inherited-overload");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static class Base {
                    public String run(String value) { return "selected:" + value; }
                    public String run(int value) { System.setProperty("unused", "never"); return "unused"; }
                }
                public static final class Child extends Base { }
                public static void main(String[] args) throws Exception {
                    var method = Child.class.getMethod("run", String.class);
                    System.out.println(method.invoke(new Child(), "value"));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-proven-inherited-overload").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("selected:value\n");
    }

}
