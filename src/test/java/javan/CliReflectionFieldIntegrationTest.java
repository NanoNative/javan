package javan;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import javan.testing.TestSuite.NativeTest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@NativeTest
final class CliReflectionFieldIntegrationTest extends CliIntegrationSupport {
    @Test
    void resolvesDeclaredFieldMetadataFromALiteralLookup() throws Exception {
        final Path project = project("reflection-field-literal");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                static final class Model { private int count; }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("count");
                    System.out.println(field.getName());
                    System.out.println(field.getDeclaringClass().getName());
                    System.out.println(field.getType().getName());
                    System.out.println(field.getModifiers());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-literal").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("count\ncom.acme.Main$Model\nint\n2\n");
    }

    @Test
    void supportsAProjectDeclaredFieldForRuntimeNameLookup() throws Exception {
        final Path project = project("reflection-field-declared");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static final class Model { public String value; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(Model.class.getDeclaredField(args[0]).getName());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-declared").toString(), "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("value\n");
    }
    @Test
    void exposesThePublicProjectDeclarationToApplicationTests() throws Exception {
        final Path project = project("reflection-field-declaration-call");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    AnnotatedElement[] declared = javanReflectionTargets();
                    Field field = (Field) declared[0];
                    System.out.println(declared.length); System.out.println(field.getName());
                    System.out.println(field.getType().getName());
                    System.out.println(field.getClass().getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-declaration-call").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("1\nvalue\nint\njava.lang.reflect.Field\n");
    }

    @Test
    void doesNotExposeAnUndeclaredFieldThroughAnExternalName() throws Exception {
        final Path project = project("reflection-field-not-retained");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value; public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    Class<?> target = Model.class;
                    String name = args[0];
                    Field field;
                    try { field = target.getDeclaredField(name); }
                    catch (NoSuchFieldException expected) { System.out.println("not retained"); return; }
                    System.out.println(field.getName());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult retained = process(project, List.of(project.resolve(".javan/bin/reflection-field-not-retained").toString(), "value"));
        assertThat(retained.exitCode()).as(retained.stderr()).isZero();
        assertThat(retained.stdout()).isEqualTo("value\n");
        final ProcessResult hidden = process(project, List.of(project.resolve(".javan/bin/reflection-field-not-retained").toString(), "hidden"));
        assertThat(hidden.exitCode()).as(hidden.stderr()).isZero();
        assertThat(hidden.stdout()).isEqualTo("not retained\n");
    }

    @Test
    void transportsMissingFieldsAndNullLookupArguments() throws Exception {
        final Path project = project("reflection-field-lookup-failures");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                static final class Model { int count; }
                public static void main(String[] args) throws Exception {
                    Class<?> model = Model.class; Class<?> none = null; String missing = "missing"; String nullName = null;
                    Field absent = null;
                    try { model.getDeclaredField(missing); } catch (NoSuchFieldException expected) { System.out.println("missing"); }
                    try { model.getDeclaredField(nullName); } catch (NullPointerException expected) { System.out.println("null-name"); }
                    try { none.getDeclaredField(missing); } catch (NullPointerException expected) { System.out.println("null-class"); }
                    try { absent.getName(); } catch (NullPointerException expected) { System.out.println("null-field"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-lookup-failures").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("missing\nnull-name\nnull-class\nnull-field\n");
    }

    @Test
    void retainsASelectedFieldTypeWithoutInitializingIt() throws Exception {
        final Path project = project("reflection-field-type-retention");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Target { public Payload field; }
                public static final class Payload { static { System.out.println("initialized"); } }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Target.class.getDeclaredField("field").getType();
                    System.out.println(type.getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-type-retention").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("com.acme.Main$Payload\n");
    }

    @Test
    void preservesTheEnumNatureOfASelectedFieldType() throws Exception {
        final Path project = project("reflection-field-enum-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                enum Kind { FIRST }
                static final class Model { Kind field; }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Model.class.getDeclaredField("field").getType();
                    System.out.println(type.getName()); System.out.println(type.isEnum());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-enum-type").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("com.acme.Main$Kind\ntrue\n");
    }

    @Test
    void followsLiteralClassAndFieldNamesThroughLocalsAndClassForName() throws Exception {
        final Path project = project("reflection-field-local-flow");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public int alpha; public int beta; }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Class.forName("com.acme.Main$Model");
                    String name;
                    if (args.length == 0) name = "alpha"; else name = "beta";
                    System.out.println(type.getDeclaredField(name).getName());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult first = process(project, List.of(project.resolve(".javan/bin/reflection-field-local-flow").toString()));
        final ProcessResult second = process(project, List.of(project.resolve(".javan/bin/reflection-field-local-flow").toString(), "x"));
        assertThat(first.exitCode()).as(first.stderr()).isZero(); assertThat(second.exitCode()).as(second.stderr()).isZero();
        assertThat(first.stdout()).isEqualTo("alpha\n"); assertThat(second.stdout()).isEqualTo("beta\n");
    }

    @Test
    void reportsArrayFieldTypeAndItsComponentType() throws Exception {
        final Path project = project("reflection-field-array-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Model { private static final String[] names = {}; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("names");
                    Class<?> type = field.getType();
                    System.out.println(type.getName()); System.out.println(type.isArray());
                    System.out.println(type.getComponentType().getName()); System.out.println(field.getModifiers());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-array-type").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("[Ljava.lang.String;\ntrue\njava.lang.String\n26\n");
    }

    @Test
    void reportsTheCorrectRuntimeClassForFieldObjects() throws Exception {
        final Path project = project("reflection-field-runtime-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                static final class Model { int value; }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value");
                    System.out.println(field.getClass() == Field.class);
                    System.out.println(Member.class.isInstance(field));
                    System.out.println(AnnotatedElement.class.isInstance(field));
                    System.out.println(AccessibleObject.class.isInstance(field));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-runtime-class").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\ntrue\ntrue\n");
    }

    @Test
    void lookupDoesNotInitializeTheDeclaringClass() throws Exception {
        final Path project = project("reflection-field-no-initialize");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Model { static { System.out.println("initialized"); } static int value; }
                public static void main(String[] args) throws Exception {
                    System.out.println(Model.class.getDeclaredField("value").getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-no-initialize").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value\n");
    }

    @Test
    void retainsDeclaredFieldObjectsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-concurrent-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                static final class Model { int value; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value");
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) field.getName();
                    worker.join(); System.out.println(field.getName()); System.out.println(field.getType().getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-concurrent-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("value\nint\n");
    }

    @Test
    void retainsTheEnumTypeOfAnExplicitlyDeclaredField() throws Exception {
        final Path project = project("reflection-field-declared-enum-type");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                enum Kind { FIRST }
                public static final class Model { public Kind field; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("field") };
                }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Model.class.getDeclaredField(args[0]).getType();
                    System.out.println(type.getName()); System.out.println(type.isEnum());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-declared-enum-type").toString(), "field"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("com.acme.Main$Kind\ntrue\n");
    }

    @Test
    void rejectsADeclaredFieldThatDoesNotExist() throws Exception {
        final Path project = project("reflection-field-declaration-missing");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("missing") };
                }
                public static void main(String[] args) { }
            }
            """);
        final CliRun check = run(tempDir, "check", project.toString());
        assertThat(check.exitCode()).isEqualTo(2);
        assertThat(check.stderr()).contains("JAVAN096", "missing");
    }

    @Test
    void rejectsComputedFieldNamesInTheDeclaration() throws Exception {
        final Path project = project("reflection-field-declaration-computed");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField(System.getProperty("name")) };
                }
                public static void main(String[] args) { }
            }
            """);
        final CliRun check = run(tempDir, "check", project.toString());
        assertThat(check.exitCode()).isEqualTo(2);
        assertThat(check.stderr()).contains("JAVAN096");
    }

    @Test
    void returnsAStableFieldNameAcrossRepeatedAccess() throws Exception {
        final Path project = project("reflection-field-name-identity");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Model { int größe; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("größe");
                    String first = field.getName(); String second = field.getName();
                    System.out.println(first); System.out.println(first == second);
                    System.out.println(first.length());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-name-identity").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("größe\ntrue\n5\n");
    }

    @Test
    void retainsTheSelectedFieldTypeHierarchyForAssignability() throws Exception {
        final Path project = project("reflection-field-type-hierarchy");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                interface Marker { }
                static final class Payload implements Marker { }
                static final class Model { Payload field; }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Model.class.getDeclaredField("field").getType();
                    System.out.println(Marker.class.isAssignableFrom(type));
                    System.out.println(type.isAssignableFrom(Marker.class));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-type-hierarchy").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\nfalse\n");
    }

    @Test
    void rejectsPlatformFieldsRatherThanExposingJvmFilteredMembers() throws Exception {
        final Path project = project("reflection-field-platform-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Class<?> string = String.class; Class<?> type = Class.class;
                    String value = "value"; String hidden = "classLoader";
                    try { string.getDeclaredField(value); }
                    catch (UnsupportedOperationException expected) { System.out.println("platform"); }
                    try { type.getDeclaredField(hidden); }
                    catch (UnsupportedOperationException expected) { System.out.println("filtered"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-platform-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("platform\nfiltered\n");
    }

    @Test
    void primitiveAndArrayClassesHaveNoDeclaredLengthField() throws Exception {
        final Path project = project("reflection-field-primitive-array");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static void main(String[] args) {
                    Class<?> primitive = int.class; Class<?> array = String[].class; String name = "length";
                    try { primitive.getDeclaredField(name); } catch (NoSuchFieldException expected) { System.out.println("primitive"); }
                    try { array.getDeclaredField(name); } catch (NoSuchFieldException expected) { System.out.println("array"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-primitive-array").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("primitive\narray\n");
    }

    @Test
    void rejectsUnmodeledPlatformFieldTypesWithoutReturningFalseMetadata() throws Exception {
        final Path project = project("reflection-field-platform-type-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                static final class Model { java.time.DayOfWeek field; java.time.DayOfWeek[] fields; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("field");
                    var array = Model.class.getDeclaredField("fields");
                    System.out.println(field.getName());
                    try { field.getType(); } catch (UnsupportedOperationException expected) { System.out.println("type"); }
                    try { array.getType(); } catch (UnsupportedOperationException expected) { System.out.println("array"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-platform-type-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("field\ntype\narray\n");
    }

    @Test
    void resolvesADeclaredFieldOnAClassNamedByExternalInput() throws Exception {
        final Path project = project("reflection-field-dynamic-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    Class<?> type = Class.forName(args[0]);
                    System.out.println(type.getDeclaredField(args[1]).getName());
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-dynamic-class").toString(),
            "com.acme.Main$Model", "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("value\n");
    }

}
