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

    @Test
    void readsADeclaredPublicIntFieldFromAnInstance() throws Exception {
        final Path project = project("reflection-field-read-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value = 42; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField(args[0]);
                    System.out.println(field.getInt(new Model()));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-int").toString(), "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("42\n");
    }

    @Test
    void preservesIntFieldReadFailuresAndAccessChecks() throws Exception {
        final Path project = project("reflection-field-read-failures");
        writeJava(project, "other.Model", """
            package other;
            public final class Model { public int value = 42; private int secret = 9; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            import other.Model;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value");
                    Field secret = Model.class.getDeclaredField("secret");
                    Model model = new Model(); Object wrong = new Object(); Field absent = null;
                    try { value.getInt(null); } catch (NullPointerException e) { System.out.println("null target"); }
                    try { value.getInt(wrong); } catch (IllegalArgumentException e) { System.out.println("wrong target"); }
                    try { secret.getInt(model); } catch (IllegalAccessException e) { System.out.println("inaccessible"); }
                    try { secret.getInt(wrong); }
                    catch (IllegalAccessException e) { System.out.println("access before wrong target"); }
                    catch (IllegalArgumentException e) { System.out.println("wrong target before access"); }
                    try { secret.getInt(null); } catch (NullPointerException e) { System.out.println("private null target"); }
                    try { absent.getInt(model); } catch (NullPointerException e) { System.out.println("null field"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-failures").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void readsTheDeclaringFieldWhenASubclassHidesItsName() throws Exception {
        final Path project = project("reflection-field-read-hidden");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static class Base { public int value = 11; }
                public static final class Derived extends Base { public int value = 22; }
                public static void main(String[] args) throws Exception {
                    Derived target = new Derived();
                    System.out.println(Base.class.getDeclaredField("value").getInt(target));
                    System.out.println(Derived.class.getDeclaredField("value").getInt(target));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-hidden").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("11\n22\n");
    }

    @Test
    void rejectsUnsupportedStaticAndVolatileIntReads() throws Exception {
        final Path project = project("reflection-field-read-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public static int shared = 1; public volatile int value = 2; public long wide = 3; }
                public static void main(String[] args) throws Exception {
                    Field shared = Model.class.getDeclaredField("shared");
                    Field value = Model.class.getDeclaredField("value");
                    Field wide = Model.class.getDeclaredField("wide");
                    Model model = new Model();
                    try { shared.getInt(null); } catch (UnsupportedOperationException e) { System.out.println("static"); }
                    try { value.getInt(model); } catch (UnsupportedOperationException e) { System.out.println("volatile"); }
                    try { wide.getInt(model); } catch (UnsupportedOperationException e) { System.out.println("type"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-bounds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("static\nvolatile\ntype\n");
    }

    @Test
    void readsIntFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-read-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public int value = 42; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value"); Model target = new Model();
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) { if (field.getInt(target) != 42) throw new AssertionError(); }
                    worker.join(); System.out.println(field.getInt(target));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("42\n");
    }

    @Test
    void checksProtectedFieldAccessAgainstTheActualTarget() throws Exception {
        final Path project = project("reflection-field-read-protected");
        writeJava(project, "other.Base", """
            package other;
            public class Base { protected int value = 13; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import other.Base;
            public final class Main {
                public static final class Derived extends Base {
                    static void check() throws Exception {
                        Field field = Base.class.getDeclaredField("value");
                        Object allowed = new Derived(); Object denied = new Base(); Object wrong = new Object();
                        System.out.println(field.getInt(allowed));
                        try { field.getInt(denied); } catch (IllegalAccessException e) { System.out.println("base denied"); }
                        try { field.getInt(wrong); } catch (IllegalAccessException e) { System.out.println("wrong denied"); }
                    }
                }
                public static void main(String[] args) throws Exception { Derived.check(); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-protected").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("13\nbase denied\nwrong denied\n");
    }

    @Test
    void readsPrivateIntFieldsFromTheSameNest() throws Exception {
        final Path project = project("reflection-field-read-nest");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { private int value = 23; }
                public static void main(String[] args) throws Exception {
                    System.out.println(Model.class.getDeclaredField("value").getInt(new Model()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-nest").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("23\n");
    }

    @Test
    void widensByteShortAndCharFieldsForGetInt() throws Exception {
        final Path project = project("reflection-field-get-int-widen");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model {
                    public byte small = -127; public short middle = -32000; public char letter = 65535;
                }
                public static void main(String[] args) throws Exception {
                    Model model = new Model();
                    Field small = Model.class.getDeclaredField("small");
                    Field middle = Model.class.getDeclaredField("middle");
                    Field letter = Model.class.getDeclaredField("letter");
                    System.out.println(small.getInt(model));
                    System.out.println(middle.getInt(model));
                    System.out.println(letter.getInt(model));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-int-widen").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-127\n-32000\n65535\n");
    }

    @Test
    void widensFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-get-int-widen-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public byte small = -127; public short middle = -32000; public char letter = 65535; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Model target = new Model();
                    Field small = Model.class.getDeclaredField("small");
                    Field middle = Model.class.getDeclaredField("middle");
                    Field letter = Model.class.getDeclaredField("letter");
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) {
                        if (small.getInt(target) != -127 || middle.getInt(target) != -32000 || letter.getInt(target) != 65535) throw new AssertionError();
                    }
                    worker.join(); System.out.println(small.getInt(target)); System.out.println(middle.getInt(target)); System.out.println(letter.getInt(target));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-int-widen-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-127\n-32000\n65535\n");
    }

    @Test
    void widensDeclaredFieldsSelectedByExternalNames() throws Exception {
        final Path project = project("reflection-field-get-int-widen-external");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public byte small = -127; public short middle = -32000; public char letter = 65535; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("small"), Model.class.getDeclaredField("middle"), Model.class.getDeclaredField("letter") };
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(Class.forName(args[0]).getDeclaredField(args[1]).getInt(new Model()));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final Path binary = project.resolve(".javan/bin/reflection-field-get-int-widen-external");
        for (final List<String> example : List.of(List.of("small", "-127"), List.of("middle", "-32000"), List.of("letter", "65535"))) {
            final ProcessResult result = process(project, List.of(binary.toString(), "com.acme.Main$Model", example.getFirst()));
            assertThat(result.exitCode()).as(result.stderr()).isZero();
            assertThat(result.stdout()).isEqualTo(example.getLast() + "\n");
        }
    }

    @Test
    void writesADeclaredPublicIntFieldOnAnInstance() throws Exception {
        final Path project = project("reflection-field-write-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value = 42; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    Model target = new Model(); Field field = Model.class.getDeclaredField(args[0]);
                    field.setInt(target, -17); System.out.println(target.value); System.out.println(field.getInt(target));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-int").toString(), "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("-17\n-17\n");
    }

    @Test
    void evaluatesFieldWriteArgumentsInJavaOrder() throws Exception {
        final Path project = project("reflection-field-write-order");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model { public int value; }
                static final Model model = new Model();
                static Field field() throws Exception { System.out.println("field"); return Model.class.getDeclaredField("value"); }
                static Object target() { System.out.println("target"); return model; }
                static int value() { System.out.println("value"); return 99; }
                public static void main(String[] args) throws Exception {
                    field().setInt(target(), value()); System.out.println(model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-order").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("field\ntarget\nvalue\n99\n");
    }

    @Test
    void preservesIntFieldWriteFailuresAndAccessChecks() throws Exception {
        final Path project = project("reflection-field-write-failures");
        writeJava(project, "other.Model", """
            package other;
            public final class Model { public int value = 42; private int secret = 9; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import other.Model;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value"); Field secret = Model.class.getDeclaredField("secret");
                    Model model = new Model(); Object wrong = new Object(); Field absent = null;
                    try { value.setInt(null, 1); } catch (NullPointerException e) { System.out.println("null target"); }
                    try { value.setInt(wrong, 1); } catch (IllegalArgumentException e) { System.out.println("wrong target"); }
                    try { secret.setInt(model, 1); } catch (IllegalAccessException e) { System.out.println("inaccessible"); }
                    try { secret.setInt(wrong, 1); } catch (IllegalAccessException e) { System.out.println("access before target"); }
                    try { absent.setInt(model, 1); } catch (NullPointerException e) { System.out.println("null field"); }
                    System.out.println(model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-failures").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void writesTheDeclaringIntFieldWhenASubclassHidesItsName() throws Exception {
        final Path project = project("reflection-field-write-hidden");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static class Base { public int value = 11; }
                public static final class Derived extends Base { public int value = 22; }
                public static void main(String[] args) throws Exception {
                    Derived target = new Derived();
                    Base.class.getDeclaredField("value").setInt(target, 33);
                    Derived.class.getDeclaredField("value").setInt(target, 44);
                    System.out.println(((Base) target).value); System.out.println(target.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-hidden").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("33\n44\n");
    }

    @Test
    void rejectsUnsupportedStaticVolatileAndFinalIntWrites() throws Exception {
        final Path project = project("reflection-field-write-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model { public static int shared; public volatile int value; public final int fixed = 1; }
                public static void main(String[] args) throws Exception {
                    Field shared = Model.class.getDeclaredField("shared"); Field value = Model.class.getDeclaredField("value");
                    Field fixed = Model.class.getDeclaredField("fixed"); Model target = new Model();
                    try { shared.setInt(null, 7); } catch (UnsupportedOperationException e) { System.out.println("static"); }
                    try { value.setInt(target, 7); } catch (UnsupportedOperationException e) { System.out.println("volatile"); }
                    try { fixed.setInt(target, 7); } catch (UnsupportedOperationException e) { System.out.println("final"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-bounds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("static\nvolatile\nfinal\n");
    }

    @Test
    void writesIntFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-write-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public int value; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Model target = new Model(); Field field = Model.class.getDeclaredField("value");
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) { field.setInt(target, 42); if (target.value != 42) throw new AssertionError(); }
                    worker.join(); field.setInt(target, 73); System.out.println(target.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("73\n");
    }

    @Test
    void checksProtectedFieldWritesAgainstTheActualTarget() throws Exception {
        final Path project = project("reflection-field-write-protected");
        writeJava(project, "other.Base", """
            package other;
            public class Base { protected int value = 13; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import other.Base;
            public final class Main {
                public static final class Derived extends Base {
                    static void check() throws Exception {
                        Field field = Base.class.getDeclaredField("value");
                        Derived allowed = new Derived(); Object denied = new Base(); Object wrong = new Object();
                        field.setInt(allowed, 17); System.out.println(allowed.value);
                        try { field.setInt(denied, 19); } catch (IllegalAccessException e) { System.out.println("base denied"); }
                        try { field.setInt(wrong, 19); } catch (IllegalAccessException e) { System.out.println("wrong denied"); }
                    }
                }
                public static void main(String[] args) throws Exception { Derived.check(); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-protected").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("17\nbase denied\nwrong denied\n");
    }

    @Test
    void writesDeclaredFieldsSelectedByExternalNames() throws Exception {
        final Path project = project("reflection-field-write-external");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value = 42; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    Model model = new Model();
                    Class.forName(args[0]).getDeclaredField(args[1]).setInt(model, 73);
                    System.out.println(model.value);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-external").toString(),
            "com.acme.Main$Model", "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("73\n");
    }

}
