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
    void rejectsUnsupportedVolatileAndWrongTypeIntReads() throws Exception {
        final Path project = project("reflection-field-read-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public static volatile int shared = 1; public volatile int value = 2; public long wide = 3; }
                public static void main(String[] args) throws Exception {
                    Field shared = Model.class.getDeclaredField("shared");
                    Field value = Model.class.getDeclaredField("value");
                    Field wide = Model.class.getDeclaredField("wide");
                    Model model = new Model();
                    try { shared.getInt(null); } catch (UnsupportedOperationException e) { System.out.println("static volatile"); }
                    try { value.getInt(model); } catch (UnsupportedOperationException e) { System.out.println("volatile"); }
                    try { wide.getInt(model); } catch (UnsupportedOperationException e) { System.out.println("type"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-read-bounds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("static volatile\nvolatile\ntype\n");
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
    void rejectsUnsupportedVolatileAndFinalIntWrites() throws Exception {
        final Path project = project("reflection-field-write-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model { public static volatile int shared; public volatile int value; public final int fixed = 1; }
                public static void main(String[] args) throws Exception {
                    Field shared = Model.class.getDeclaredField("shared"); Field value = Model.class.getDeclaredField("value");
                    Field fixed = Model.class.getDeclaredField("fixed"); Model target = new Model();
                    try { shared.setInt(null, 7); } catch (UnsupportedOperationException e) { System.out.println("static volatile"); }
                    try { value.setInt(target, 7); } catch (UnsupportedOperationException e) { System.out.println("volatile"); }
                    try { fixed.setInt(target, 7); } catch (UnsupportedOperationException e) { System.out.println("final"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-write-bounds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("static volatile\nvolatile\nfinal\n");
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

    @Test
    void initializesAndReadsADeclaredStaticIntField() throws Exception {
        final Path project = project("reflection-static-int-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static int value = 42;
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value");
                    System.out.println("before"); System.out.println(field.getInt(null));
                    System.out.println(field.getInt(new Object()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-read").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n42\n42\n");
    }


    @Test
    void initializesSuperclassBeforeReadingAStaticFieldWithoutAnInitializer() throws Exception {
        final Path project = project("reflection-static-int-super");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static class Base { static { System.out.println("base"); } }
                public static class Model extends Base { public static int value; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value");
                    System.out.println("before"); System.out.println(field.getInt(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-super").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\nbase\n0\n");
    }

    @Test
    void rejectsInaccessibleStaticFieldBeforeInitializingItsClass() throws Exception {
        final Path project = project("reflection-static-int-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { static { System.out.println("initialized"); } private static int value = 7; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value");
                    Object target = new Object();
                    try { field.getInt(target); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("denied\n");
    }

    @Test
    void retainsDeclaredStaticFieldsSelectedByExternalNames() throws Exception {
        final Path project = project("reflection-static-int-external");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static int value = 42; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    System.out.println("before"); System.out.println(Model.class.getDeclaredField(args[0]).getInt(null));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-external").toString(), "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("before\ninitialized\n42\n");
    }


    @Test
    void transportsFailedStaticFieldInitializationAndRepeatAccess() throws Exception {
        final Path project = project("reflection-static-int-initializer-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    public static int value = fail();
                    static int fail() { throw new IllegalStateException(); }
                }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value");
                    try { field.getInt(null); } catch (ExceptionInInitializerError e) { System.out.println("first"); }
                    try { field.getInt(null); } catch (NoClassDefFoundError e) { System.out.println("second"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-initializer-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("first\nsecond\n");
    }

    @Test
    void widensDeclaredStaticIntegralFields() throws Exception {
        final Path project = project("reflection-static-int-widen");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public static byte b = -127; public static short s = -32000; public static char c = 65535; }
                public static void main(String[] args) throws Exception {
                    System.out.println(Model.class.getDeclaredField("b").getInt(null));
                    System.out.println(Model.class.getDeclaredField("s").getInt(null));
                    System.out.println(Model.class.getDeclaredField("c").getInt(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-widen").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-127\n-32000\n65535\n");
    }

    @Test
    void readsStaticIntFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-static-field-read-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public static int value = 42; }
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
                    while (finished.get() == 0) { if (field.getInt(null) != 42) throw new AssertionError(); }
                    worker.join(); System.out.println(field.getInt(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-field-read-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("42\n");
    }

    @Test
    void readsStaticFinalIntegerConstant() throws Exception {
        final Path project = project("reflection-static-final-int");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public static final int value = 123; }
                public static void main(String[] args) throws Exception {
                    System.out.println(Model.class.getDeclaredField("value").getInt(null));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-final-int").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("123\n");
    }

    @Test
    void readsFinalStaticWidenedAndInitializedFields() throws Exception {
        final Path project = project("reflection-static-final-widen");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static final byte b = -127;
                    public static final short s = -32000;
                    public static final char c = 65535;
                    public static final int min = Integer.MIN_VALUE;
                    public static final int computed = compute();
                    private static int compute() { return 77; }
                }
                public static void main(String[] args) throws Exception {
                    var b = Model.class.getDeclaredField("b");
                    var s = Model.class.getDeclaredField("s");
                    var c = Model.class.getDeclaredField("c");
                    var min = Model.class.getDeclaredField("min");
                    var computed = Model.class.getDeclaredField("computed");
                    System.out.println("before");
                    System.out.println(b.getInt(null)); System.out.println(s.getInt(null));
                    System.out.println(c.getInt(null)); System.out.println(min.getInt(null));
                    System.out.println(computed.getInt(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-final-widen").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n-127\n-32000\n65535\n-2147483648\n77\n");
    }

    @Test
    void readsStaticFinalIntFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-static-final-field-read-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public static final int value = 42; }
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
                    while (finished.get() == 0) { if (field.getInt(null) != 42) throw new AssertionError(); }
                    worker.join(); System.out.println(field.getInt(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-final-field-read-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("42\n");
    }

    @Test
    void initializesAndWritesADeclaredStaticIntField() throws Exception {
        final Path project = project("reflection-static-int-write");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static int value = 3;
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value");
                    System.out.println("before"); field.setInt(null, 42);
                    System.out.println(field.getInt(new Object()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-write").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n42\n");
    }

    @Test
    void initializesAStaticIntFieldWhenOnlyAReflectiveWriteIsReachable() throws Exception {
        final Path project = project("reflection-static-int-write-only");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static int value = 3;
                }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField(args[0]);
                    System.out.println("before"); field.setInt(null, 42); System.out.println("done");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-write-only").toString(), "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("before\ninitialized\ndone\n");
    }

    @Test
    void transportsFailedStaticFieldWriteInitializationAndRepeatAccess() throws Exception {
        final Path project = project("reflection-static-int-write-initializer-failure");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    public static int value = fail();
                    static int fail() { throw new IllegalStateException(); }
                }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value");
                    try { field.setInt(null, 42); } catch (ExceptionInInitializerError e) { System.out.println("first"); }
                    try { field.setInt(null, 42); } catch (NoClassDefFoundError e) { System.out.println("second"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-write-initializer-failure").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("first\nsecond\n");
    }

    @Test
    void rejectsInaccessibleStaticFieldWriteBeforeInitializingItsClass() throws Exception {
        final Path project = project("reflection-static-int-write-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { static { System.out.println("initialized"); } private static int value = 7; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value"); Object target = new Object();
                    try { field.setInt(target, 42); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-int-write-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("denied\n");
    }

    @Test
    void writesStaticIntFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-static-field-write-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public static int value; }
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
                    while (finished.get() == 0) { field.setInt(null, 42); if (Model.value != 42) throw new AssertionError(); }
                    worker.join(); field.setInt(null, 73); System.out.println(Model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-field-write-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("73\n");
    }


    @Test
    void widensIntWritesToLongFloatAndDoubleFields() throws Exception {
        final Path project = project("reflection-field-set-int-widen");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    public long wide; public float decimal; public double precise;
                    public static long staticWide; public static float staticDecimal; public static double staticPrecise;
                }
                public static void main(String[] args) throws Exception {
                    Model target = new Model();
                    Model.class.getDeclaredField("wide").setInt(target, Integer.MIN_VALUE);
                    Model.class.getDeclaredField("decimal").setInt(target, Integer.MAX_VALUE);
                    Model.class.getDeclaredField("precise").setInt(target, Integer.MAX_VALUE);
                    Model.class.getDeclaredField("staticWide").setInt(null, Integer.MIN_VALUE);
                    Model.class.getDeclaredField("staticDecimal").setInt(null, Integer.MAX_VALUE);
                    Model.class.getDeclaredField("staticPrecise").setInt(null, Integer.MAX_VALUE);
                    System.out.println(target.wide == -2147483648L);
                    System.out.println(target.decimal == 2147483648.0f);
                    System.out.println(target.precise == 2147483647.0d);
                    System.out.println(Model.staticWide == -2147483648L);
                    System.out.println(Model.staticDecimal == 2147483648.0f);
                    System.out.println(Model.staticPrecise == 2147483647.0d);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-int-widen").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    void initializesStaticWideFieldsWhenOnlyReflectiveWritesAreReachable() throws Exception {
        final Path project = project("reflection-field-set-int-widen-static-only");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static long wide = 1; public static float decimal = 2; public static double precise = 3;
                }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("wide"), Model.class.getDeclaredField("decimal"), Model.class.getDeclaredField("precise") };
                }
                public static void main(String[] args) throws Exception {
                    var wide = Model.class.getDeclaredField(args[0]);
                    var decimal = Model.class.getDeclaredField(args[1]);
                    var precise = Model.class.getDeclaredField(args[2]);
                    System.out.println("before"); wide.setInt(null, 1); decimal.setInt(null, 2); precise.setInt(null, 3);
                    System.out.println("done");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-int-widen-static-only").toString(), "wide", "decimal", "precise"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("before\ninitialized\ndone\n");
    }

    @Test
    void widensIntFieldWritesDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-set-int-widen-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public long wide; public float decimal; public double precise; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Model target = new Model();
                    Field wide = Model.class.getDeclaredField("wide");
                    Field decimal = Model.class.getDeclaredField("decimal");
                    Field precise = Model.class.getDeclaredField("precise");
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) { wide.setInt(target, -42); decimal.setInt(target, -42); precise.setInt(target, -42); }
                    worker.join(); wide.setInt(target, 73); decimal.setInt(target, 73); precise.setInt(target, 73);
                    System.out.println(target.wide == 73 && target.decimal == 73.0f && target.precise == 73.0d);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-int-widen-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\n");
    }

    @Test
    void enumeratesFieldsOfAStaticallyKnownClass() throws Exception {
        final Path project = project("reflection-declared-fields-literal");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model { public int value; private long hidden; }
                public static final class Empty { }
                public static void main(String[] args) throws Exception {
                    Field[] fields = Model.class.getDeclaredFields();
                    boolean value = false, hidden = false;
                    for (Field field : fields) { value |= field.getName().equals("value"); hidden |= field.getName().equals("hidden"); }
                    System.out.println(fields.length); System.out.println(value); System.out.println(hidden);
                    System.out.println(Empty.class.getDeclaredFields().length);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-literal").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\ntrue\ntrue\n0\n");
    }

    @Test
    void enumeratesDeclaredClassSelectedByExternalName() throws Exception {
        final Path project = project("reflection-declared-fields-external");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public int value; private long hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Model.class }; }
                public static void main(String[] args) throws Exception {
                    Field[] fields = Class.forName(args[0]).getDeclaredFields();
                    boolean value = false, hidden = false;
                    for (Field field : fields) { value |= field.getName().equals("value"); hidden |= field.getName().equals("hidden"); }
                    System.out.println(fields.length); System.out.println(value); System.out.println(hidden);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-external").toString(), "com.acme.Main$Model"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("2\ntrue\ntrue\n");
    }

    @Test
    void returnsFreshFieldArraysWithoutInitializingTheClass() throws Exception {
        final Path project = project("reflection-declared-fields-fresh");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public int value; }
                public static void main(String[] args) throws Exception {
                    Field[] first = Model.class.getDeclaredFields(); Field[] second = Model.class.getDeclaredFields();
                    System.out.println(first != second); System.out.println(first[0] != second[0]);
                    first[0] = null; System.out.println(second[0].getName()); System.out.println("done");
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-fresh").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("true\ntrue\nvalue\ndone\n");
    }

    @Test
    void retainsEnumeratedFieldTypesWithoutInitializingThem() throws Exception {
        final Path project = project("reflection-declared-fields-types");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Helper { static { System.out.println("helper initialized"); } }
                public static final class Model { static { System.out.println("model initialized"); } public Helper value; }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredFields()[0];
                    System.out.println(field.getType().getName()); System.out.println("done");
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-types").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("com.acme.Main$Helper\ndone\n");
    }

    @Test
    void initializesAnEnumeratedStaticFieldOnReflectiveRead() throws Exception {
        final Path project = project("reflection-declared-fields-static-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static int value = 42; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredFields()[0];
                    System.out.println("before"); System.out.println(field.getInt(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-static-read").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n42\n");
    }

    @Test
    void tracesKnownHelperTargetsAndRejectsPlatformFieldEnumeration() throws Exception {
        final Path project = project("reflection-declared-fields-bounds");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Selected { public int value; }
                public static final class Unselected { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Selected.class }; }
                static void enumerate(Class<?> type) {
                    Field[] fields;
                    try { fields = type.getDeclaredFields(); }
                    catch (UnsupportedOperationException e) { System.out.println("unsupported"); return; }
                    System.out.println(fields.length);
                }
                public static void main(String[] args) {
                    enumerate(Selected.class); enumerate(Unselected.class); enumerate(String.class);
                    enumerate(int[].class);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-bounds").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\n1\nunsupported\n0\n");
    }

    @Test
    void enumeratedFieldsAndArraysSurviveConcurrentGc() throws Exception {
        final Path project = project("reflection-declared-fields-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public int first; private long second; public double third; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) {
                        Field[] fields = Model.class.getDeclaredFields();
                        if (fields.length != 3) throw new AssertionError();
                        for (Field field : fields) if (field.getName().length() == 0) throw new AssertionError();
                    }
                    worker.join(); System.out.println(Model.class.getDeclaredFields().length);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("3\n");
    }

    @Test
    void literalEnumerationDoesNotRetainFieldsOfUnrelatedDeclaredClasses() throws Exception {
        final Path project = project("reflection-declared-fields-unrelated");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Selected { public int value; }
                public static final class Other { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Other.class }; }
                public static void main(String[] args) throws Exception {
                    System.out.println(Selected.class.getDeclaredFields().length);
                    System.out.println(int.class.getDeclaredFields().length);
                    String name = args[0]; Field field;
                    try { field = Other.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-unrelated").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\n0\nnot retained\n");
    }

    @Test
    void enumeratedFieldsHaveJavaRuntimeTypes() throws Exception {
        final Path project = project("reflection-declared-fields-runtime-types");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Model { public int value; }
                public static void main(String[] args) {
                    Field[] fields = Model.class.getDeclaredFields();
                    System.out.println(fields.getClass().getName());
                    System.out.println(fields[0].getClass().getName());
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-runtime-types").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("[Ljava.lang.reflect.Field;\njava.lang.reflect.Field\n");
    }

    @Test
    void nullClassFieldEnumerationThrowsCatchableNullPointerException() throws Exception {
        final Path project = project("reflection-declared-fields-null");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) {
                    Class<?> missing = null;
                    try { Field[] fields = missing.getDeclaredFields(); }
                    catch (NullPointerException e) { System.out.println("null class"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-null").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null class\n");
    }

    @Test
    void tracesFieldEnumerationTargetsThroughAStaticHelperArgument() throws Exception {
        final Path project = project("reflection-declared-fields-static-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public int value; private long hidden; }
                static void inspect(Class<?> type) { System.out.println(type.getDeclaredFields().length); }
                public static void main(String[] args) { inspect(Model.class); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-static-helper").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n");
    }

    @Test
    void reassignedHelperParameterDoesNotRetainThePassedClassFields() throws Exception {
        final Path project = project("reflection-declared-fields-reassigned-helper");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class Passed { public int hidden; }
                public static final class Actual { public int visible; }
                static int inspect(Class<?> type) { type = Actual.class; return type.getDeclaredFields().length; }
                public static void main(String[] args) throws Exception {
                    System.out.println(inspect(Passed.class)); String name = args[0]; Field field;
                    try { field = Passed.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-reassigned-helper").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\nnot retained\n");
    }

    @Test
    void tracesFieldEnumerationAcrossForwardingHelpers() throws Exception {
        final Path project = project("reflection-declared-fields-forwarding");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public int first; public long second; }
                static int inspect(Class<?> type) { return type.getDeclaredFields().length; }
                static int forward(Class<?> type) { return inspect(type); }
                public static void main(String[] args) { System.out.println(forward(Model.class)); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-forwarding").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n");
    }

    @Test
    void helperFlowDoesNotRetainFieldsFromUnrelatedCalls() throws Exception {
        final Path project = project("reflection-declared-fields-helper-unrelated");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            public final class Main {
                public static final class First { public int value; }
                public static final class Second { public int a; public long b; }
                public static final class Unrelated { public int hidden; }
                static int inspect(Class<?> type) { return type.getDeclaredFields().length; }
                static void ignore(Class<?> type) { }
                public static void main(String[] args) throws Exception {
                    System.out.println(inspect(First.class)); System.out.println(inspect(Second.class));
                    ignore(Unrelated.class); String name = args[0]; Field field;
                    try { field = Unrelated.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-helper-unrelated").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\n2\nnot retained\n");
    }

    @Test
    void rejectsUndeclaredRuntimeSelectedEnumerationTargets() throws Exception {
        final Path project = project("reflection-declared-fields-runtime-undeclared");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Selected { public int value; }
                public static final class Unselected { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Selected.class }; }
                static void inspect(Class<?> type) {
                    try { Field[] fields = type.getDeclaredFields(); }
                    catch (UnsupportedOperationException e) { System.out.println("unsupported"); }
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(Unselected.class.getName());
                    Class<?> target = Class.forName(args[0]); inspect(target);
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-runtime-undeclared").toString(), "com.acme.Main$Unselected"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("com.acme.Main$Unselected\nunsupported\n");
    }

    @Test
    void initializesAStaticFieldReachedOnlyThroughHelperEnumerationAndWrite() throws Exception {
        final Path project = project("reflection-declared-fields-helper-static-write");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static int value = 3; }
                static void write(Class<?> type) throws Exception { type.getDeclaredFields()[0].setInt(null, 42); }
                public static void main(String[] args) throws Exception {
                    System.out.println("before"); write(Model.class); System.out.println("done");
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-declared-fields-helper-static-write").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\ndone\n");
    }

    @Test
    void knownHelperTargetsDoNotEnumerateUnrelatedProjectDeclarations() throws Exception {
        final Path project = project("reflection-enumeration-helper-unrelated-declaration");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Selected { public int value; }
                public static final class Other { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Other.class }; }
                static int inspect(Class<?> type) { return type.getDeclaredFields().length; }
                public static void main(String[] args) throws Exception {
                    System.out.println(inspect(Selected.class)); String name = args[0]; Field field;
                    try { field = Other.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-helper-unrelated-declaration").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\nnot retained\n");
    }

    @Test
    void declaredTargetsRemainAvailableThroughAHelperMethodReference() throws Exception {
        final Path project = project("reflection-enumeration-method-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            import java.util.function.Consumer;
            public final class Main {
                public static final class Model { public int value; private long hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Model.class }; }
                static void inspect(Class<?> type) { System.out.println(type.getDeclaredFields().length); }
                public static void main(String[] args) throws Exception {
                    Consumer<Class<?>> ref = Main::inspect;
                    ref.accept(Class.forName(args[0]));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-method-reference").toString(), "com.acme.Main$Model"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("2\n");
    }

    @Test
    void unrelatedMethodReferenceDoesNotBroadenHelperTargets() throws Exception {
        final Path project = project("reflection-enumeration-unrelated-method-reference");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            import java.util.function.Consumer;
            public final class Main {
                public static final class Selected { public int value; }
                public static final class Other { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Other.class }; }
                static int inspect(Class<?> type) { return type.getDeclaredFields().length; }
                static void unrelated(Class<?> type) { }
                public static void main(String[] args) throws Exception {
                    Consumer<Class<?>> run = Main::unrelated; run.accept(Selected.class);
                    System.out.println(inspect(Selected.class)); String name = args[0]; Field field;
                    try { field = Other.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-unrelated-method-reference").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\nnot retained\n");
    }

    @Test
    void tracesTrailingClassArgumentsAfterWideParameters() throws Exception {
        final Path project = project("reflection-enumeration-trailing-class");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public int first; private long second; }
                static int inspect(int tag, long wide, double decimal, Class<?> type) { return type.getDeclaredFields().length; }
                static int forward(double value, Class<?> type) { return inspect(1, 2L, value, type); }
                public static void main(String[] args) { System.out.println(forward(3.0, Model.class)); }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-trailing-class").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("2\n");
    }

    @Test
    void trailingClassFlowIgnoresWritesToOtherParametersAndUnrelatedDeclarations() throws Exception {
        final Path project = project("reflection-enumeration-trailing-unchanged");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Selected { public int value; }
                public static final class Other { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Other.class }; }
                static int inspect(int count, Class<?> type) { count = 4; return count + type.getDeclaredFields().length; }
                public static void main(String[] args) throws Exception {
                    System.out.println(inspect(0, Selected.class)); String name = args[0]; Field field;
                    try { field = Other.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-trailing-unchanged").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("5\nnot retained\n");
    }

    @Test
    void trailingClassFlowUsesDeclarationsForRuntimeTargets() throws Exception {
        final Path project = project("reflection-enumeration-trailing-dynamic");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.AnnotatedElement;
            public final class Main {
                public static final class Model { public int value; private long hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Model.class }; }
                static int inspect(long prefix, Class<?> type) { return type.getDeclaredFields().length; }
                public static void main(String[] args) throws Exception { System.out.println(inspect(7L, Class.forName(args[0]))); }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-trailing-dynamic").toString(), "com.acme.Main$Model"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("2\n");
    }

    @Test
    void reassignedTrailingClassParameterDoesNotRetainCallerTarget() throws Exception {
        final Path project = project("reflection-enumeration-trailing-reassigned");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Actual { public int value; }
                public static final class Passed { public int hidden; }
                public static AnnotatedElement[] javanReflectionTargets() { return new AnnotatedElement[] { Actual.class }; }
                static int inspect(long prefix, Class<?> type) { type = Actual.class; return type.getDeclaredFields().length; }
                public static void main(String[] args) throws Exception {
                    System.out.println(inspect(7L, Passed.class)); String name = args[0]; Field field;
                    try { field = Passed.class.getDeclaredField(name); }
                    catch (NoSuchFieldException e) { System.out.println("not retained"); return; }
                    System.out.println("retained");
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-enumeration-trailing-reassigned").toString(), "hidden"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("1\nnot retained\n");
    }

    @Test
    void readsLongFieldsAndWidensSmallerIntegralFields() throws Exception {
        final Path project = project("reflection-field-get-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    public byte small = -4; public short medium = -300; public char letter = 65535;
                    public int number = -70000; public long large = 5000000000L;
                }
                public static void main(String[] args) throws Exception {
                    Model value = new Model();
                    System.out.println(Model.class.getDeclaredField("small").getLong(value));
                    System.out.println(Model.class.getDeclaredField("medium").getLong(value));
                    System.out.println(Model.class.getDeclaredField("letter").getLong(value));
                    System.out.println(Model.class.getDeclaredField("number").getLong(value));
                    System.out.println(Model.class.getDeclaredField("large").getLong(value));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-long").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-4\n-300\n65535\n-70000\n5000000000\n");
    }

    @Test
    void initializesStaticLongFieldsBeforeReflectiveReads() throws Exception {
        final Path project = project("reflection-static-long-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static long value = 5000000000L;
                }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value");
                    System.out.println("before"); System.out.println(field.getLong(null));
                    System.out.println(field.getLong(new Object()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-long-read").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n5000000000\n5000000000\n");
    }

    @Test
    void rejectsUnsupportedLongFieldTypesExplicitly() throws Exception {
        final Path project = project("reflection-field-get-long-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public volatile long changing; public double decimal; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model();
                    try { Model.class.getDeclaredField("changing").getLong(model); }
                    catch (UnsupportedOperationException e) { System.out.println("volatile unsupported"); }
                    try { Model.class.getDeclaredField("decimal").getLong(model); }
                    catch (UnsupportedOperationException e) { System.out.println("type unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-long-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("volatile unsupported\ntype unsupported\n");
    }

    @Test
    void readsStaticLongFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-static-long-read-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public static long value = 5000000000L; }
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
                    while (finished.get() == 0) { if (field.getLong(null) != 5000000000L) throw new AssertionError(); }
                    worker.join(); System.out.println(field.getLong(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-long-read-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("5000000000\n");
    }

    @Test
    void checksLongFieldReceiverAndAccessBeforeReading() throws Exception {
        final Path project = project("reflection-field-get-long-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { public long value = 9; private long secret = 5; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value");
                    Field secret = Model.class.getDeclaredField("secret");
                    Object wrong = new Object(); Model model = new Model();
                    try { value.getLong(null); } catch (NullPointerException e) { System.out.println("null"); }
                    try { value.getLong(wrong); } catch (IllegalArgumentException e) { System.out.println("wrong"); }
                    try { secret.getLong(model); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-long-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nwrong\ndenied\n");
    }

    @Test
    void readsDeclaredLongFieldsSelectedByExternalNames() throws Exception {
        final Path project = project("reflection-field-get-long-external");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.*;
            public final class Main {
                public static final class Model { public long value = 5000000000L; }
                public static AnnotatedElement[] javanReflectionTargets() throws ReflectiveOperationException {
                    return new AnnotatedElement[] { Model.class.getDeclaredField("value") };
                }
                public static void main(String[] args) throws Exception {
                    System.out.println(Class.forName(args[0]).getDeclaredField(args[1]).getLong(new Model()));
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-long-external").toString(),
            "com.acme.Main$Model", "value"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("5000000000\n");
    }

    @Test
    void readsStaticFinalLongConstantsAndInitializesTheirClass() throws Exception {
        final Path project = project("reflection-static-final-long");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static final long MIN = Long.MIN_VALUE;
                    public static final long BIG = 5000000000L;
                }
                public static void main(String[] args) throws Exception {
                    var minimum = Model.class.getDeclaredField("MIN");
                    var large = Model.class.getDeclaredField("BIG");
                    System.out.println("before"); System.out.println(minimum.getLong(null));
                    System.out.println(large.getLong(null));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-final-long").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n-9223372036854775808\n5000000000\n");
    }

    @Test
    void writesLongFieldsAndWidensToFloatAndDouble() throws Exception {
        final Path project = project("reflection-field-set-long-widen");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public long wide; public float decimal; public double precise; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model();
                    Model.class.getDeclaredField("wide").setLong(model, Long.MIN_VALUE);
                    Model.class.getDeclaredField("decimal").setLong(model, 42L);
                    Model.class.getDeclaredField("precise").setLong(model, 5000000000L);
                    System.out.println(model.wide); System.out.println(model.decimal == 42.0f);
                    System.out.println(model.precise == 5000000000.0);
                    Model.class.getDeclaredField("decimal").setLong(model, Long.MAX_VALUE);
                    Model.class.getDeclaredField("precise").setLong(model, Long.MIN_VALUE);
                    System.out.println(model.decimal == (float) Long.MAX_VALUE);
                    System.out.println(model.precise == (double) Long.MIN_VALUE);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-long-widen").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-9223372036854775808\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    void initializesStaticFieldsBeforeWritingLongValues() throws Exception {
        final Path project = project("reflection-static-long-write");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static long wide; public static float decimal; public static double precise;
                }
                public static void main(String[] args) throws Exception {
                    var wide = Model.class.getDeclaredField("wide"); var decimal = Model.class.getDeclaredField("decimal");
                    var precise = Model.class.getDeclaredField("precise"); System.out.println("before");
                    wide.setLong(null, 5000000000L); decimal.setLong(null, 42L); precise.setLong(null, -7L);
                    System.out.println(Model.wide); System.out.println(Model.decimal == 42.0f); System.out.println(Model.precise == -7.0);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-long-write").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n5000000000\ntrue\ntrue\n");
    }

    @Test
    void checksLongFieldWriteReceiverAndAccess() throws Exception {
        final Path project = project("reflection-field-set-long-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { public long value; private long secret; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value"), secret = Model.class.getDeclaredField("secret");
                    Object wrong = new Object(); Model model = new Model();
                    try { value.setLong(null, 1L); } catch (NullPointerException e) { System.out.println("null"); }
                    try { value.setLong(wrong, 1L); } catch (IllegalArgumentException e) { System.out.println("wrong"); }
                    try { secret.setLong(model, 1L); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-long-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nwrong\ndenied\n");
    }

    @Test
    void rejectsUnsupportedLongFieldWritesExplicitly() throws Exception {
        final Path project = project("reflection-field-set-long-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public int narrow; public volatile long changing; public final long fixed = 3; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var narrow = Model.class.getDeclaredField("narrow");
                    var changing = Model.class.getDeclaredField("changing"); var fixed = Model.class.getDeclaredField("fixed");
                    try { narrow.setLong(model, 4L); } catch (UnsupportedOperationException e) { System.out.println("type unsupported"); }
                    try { changing.setLong(model, 4L); } catch (UnsupportedOperationException e) { System.out.println("volatile unsupported"); }
                    try { fixed.setLong(model, 4L); } catch (UnsupportedOperationException e) { System.out.println("final unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-long-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("type unsupported\nvolatile unsupported\nfinal unsupported\n");
    }

    @Test
    void writesLongFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-set-long-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public long value; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value"); Model model = new Model();
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) field.setLong(model, 5000000000L);
                    worker.join(); field.setLong(model, 5000000000L); System.out.println(model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-long-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("5000000000\n");
    }

    @Test
    void writesDoubleFieldsWithTheirExactBits() throws Exception {
        final Path project = project("reflection-field-set-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public double value; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var field = Model.class.getDeclaredField("value");
                    field.setDouble(model, 3.25); System.out.println(model.value);
                    field.setDouble(model, -0.0); System.out.println(Double.doubleToRawLongBits(model.value));
                    field.setDouble(model, Double.longBitsToDouble(0x7ff8000000000001L));
                    System.out.println(Double.doubleToRawLongBits(model.value));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-double").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("3.25\n-9223372036854775808\n9221120237041090561\n");
    }

    @Test
    void initializesStaticDoubleFieldBeforeReflectiveWrite() throws Exception {
        final Path project = project("reflection-static-double-write");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static double value = 2.5; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value"); System.out.println("before");
                    field.setDouble(null, 7.25); System.out.println(Model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-double-write").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n7.25\n");
    }

    @Test
    void checksDoubleFieldWriteReceiverAndAccess() throws Exception {
        final Path project = project("reflection-field-set-double-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { public double value; private double secret; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value"), secret = Model.class.getDeclaredField("secret");
                    Object wrong = new Object(); Model model = new Model();
                    try { value.setDouble(null, 1.0); } catch (NullPointerException e) { System.out.println("null"); }
                    try { value.setDouble(wrong, 1.0); } catch (IllegalArgumentException e) { System.out.println("wrong"); }
                    try { secret.setDouble(model, 1.0); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-double-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nwrong\ndenied\n");
    }

    @Test
    void rejectsUnsupportedDoubleFieldWritesExplicitly() throws Exception {
        final Path project = project("reflection-field-set-double-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public float narrow; public volatile double changing; public final double fixed = 3.0; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var narrow = Model.class.getDeclaredField("narrow");
                    var changing = Model.class.getDeclaredField("changing"); var fixed = Model.class.getDeclaredField("fixed");
                    try { narrow.setDouble(model, 4.0); } catch (UnsupportedOperationException e) { System.out.println("type unsupported"); }
                    try { changing.setDouble(model, 4.0); } catch (UnsupportedOperationException e) { System.out.println("volatile unsupported"); }
                    try { fixed.setDouble(model, 4.0); } catch (UnsupportedOperationException e) { System.out.println("final unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-double-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("type unsupported\nvolatile unsupported\nfinal unsupported\n");
    }

    @Test
    void writesDoubleFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-set-double-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public double value; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value"); Model model = new Model();
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) field.setDouble(model, 7.25);
                    worker.join(); field.setDouble(model, 7.25); System.out.println(model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-double-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("7.25\n");
    }

    @Test
    void readsDoubleFieldsAndWidensNumericFields() throws Exception {
        final Path project = project("reflection-field-get-double");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    public byte small = -4; public short medium = -300; public char letter = 65535;
                    public int number = -70000; public long large = 5000000000L;
                    public float decimal = 1.5f; public double precise = -0.0;
                }
                public static void main(String[] args) throws Exception {
                    Model value = new Model();
                    System.out.println(Model.class.getDeclaredField("small").getDouble(value));
                    System.out.println(Model.class.getDeclaredField("medium").getDouble(value));
                    System.out.println(Model.class.getDeclaredField("letter").getDouble(value));
                    System.out.println(Model.class.getDeclaredField("number").getDouble(value));
                    System.out.println(Model.class.getDeclaredField("large").getDouble(value));
                    System.out.println(Model.class.getDeclaredField("decimal").getDouble(value));
                    System.out.println(Double.doubleToRawLongBits(Model.class.getDeclaredField("precise").getDouble(value)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-double").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void initializesStaticDoubleFieldBeforeReflectiveRead() throws Exception {
        final Path project = project("reflection-static-double-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static double value = 2.5; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value"); System.out.println("before");
                    System.out.println(field.getDouble(null)); System.out.println(field.getDouble(new Object()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-double-read").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n2.5\n2.5\n");
    }

    @Test
    void checksDoubleFieldReadReceiverAndAccess() throws Exception {
        final Path project = project("reflection-field-get-double-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { public double value; private double secret; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value"), secret = Model.class.getDeclaredField("secret");
                    Object wrong = new Object(); Model model = new Model();
                    try { value.getDouble(null); } catch (NullPointerException e) { System.out.println("null"); }
                    try { value.getDouble(wrong); } catch (IllegalArgumentException e) { System.out.println("wrong"); }
                    try { secret.getDouble(model); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-double-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nwrong\ndenied\n");
    }

    @Test
    void rejectsUnsupportedDoubleFieldReadsExplicitly() throws Exception {
        final Path project = project("reflection-field-get-double-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public boolean flag; public volatile double changing; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var flag = Model.class.getDeclaredField("flag");
                    var changing = Model.class.getDeclaredField("changing");
                    try { flag.getDouble(model); } catch (UnsupportedOperationException e) { System.out.println("type unsupported"); }
                    try { changing.getDouble(model); } catch (UnsupportedOperationException e) { System.out.println("volatile unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-double-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("type unsupported\nvolatile unsupported\n");
    }

    @Test
    void readsDoubleFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-get-double-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public double value = 7.25; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value"); Model model = new Model();
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) { if (field.getDouble(model) != 7.25) throw new AssertionError(); }
                    worker.join(); System.out.println(field.getDouble(model));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-double-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("7.25\n");
    }

    @Test
    void roundsWideLongValuesWhenReadingAsDouble() throws Exception {
        final Path project = project("reflection-field-get-double-rounding");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public long value = 9007199254740993L; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var field = Model.class.getDeclaredField("value");
                    System.out.println(Double.doubleToRawLongBits(field.getDouble(model)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-double-rounding").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }

    @Test
    void writesFloatFieldsAndWidensToDouble() throws Exception {
        final Path project = project("reflection-field-set-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public float value; public double wide; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var value = Model.class.getDeclaredField("value");
                    var wide = Model.class.getDeclaredField("wide");
                    value.setFloat(model, -0.0f); wide.setFloat(model, 1.5f);
                    System.out.println(Float.floatToRawIntBits(model.value)); System.out.println(model.wide);
                    value.setFloat(model, Float.intBitsToFloat(0x7fc00001));
                    System.out.println(Float.floatToRawIntBits(model.value));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-float").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("-2147483648\n1.5\n2143289345\n");
    }


    @Test
    void checksFloatFieldWriteReceiverAndAccess() throws Exception {
        final Path project = project("reflection-field-set-float-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { public float value; private float secret; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value"), secret = Model.class.getDeclaredField("secret");
                    Object wrong = new Object(); Model model = new Model();
                    try { value.setFloat(null, 1.0f); } catch (NullPointerException e) { System.out.println("null"); }
                    try { value.setFloat(wrong, 1.0f); } catch (IllegalArgumentException e) { System.out.println("wrong"); }
                    try { secret.setFloat(model, 1.0f); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-float-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nwrong\ndenied\n");
    }

    @Test
    void rejectsUnsupportedFloatFieldWritesExplicitly() throws Exception {
        final Path project = project("reflection-field-set-float-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public int narrow; public volatile float changing; public final float fixed = 3.0f; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var narrow = Model.class.getDeclaredField("narrow");
                    var changing = Model.class.getDeclaredField("changing"); var fixed = Model.class.getDeclaredField("fixed");
                    try { narrow.setFloat(model, 4.0f); } catch (UnsupportedOperationException e) { System.out.println("type unsupported"); }
                    try { changing.setFloat(model, 4.0f); } catch (UnsupportedOperationException e) { System.out.println("volatile unsupported"); }
                    try { fixed.setFloat(model, 4.0f); } catch (UnsupportedOperationException e) { System.out.println("final unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-float-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("type unsupported\nvolatile unsupported\nfinal unsupported\n");
    }

    @Test
    void writesFloatFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-set-float-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public float value; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value"); Model model = new Model();
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) field.setFloat(model, 7.25f);
                    worker.join(); field.setFloat(model, 7.25f); System.out.println(model.value);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-set-float-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("7.25\n");
    }

    @Test
    void initializesStaticFieldsBeforeWritingFloatValues() throws Exception {
        final Path project = project("reflection-static-float-write");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static float value; public static double wide; }
                public static void main(String[] args) throws Exception {
                    var value = Model.class.getDeclaredField("value"); var wide = Model.class.getDeclaredField("wide");
                    System.out.println("before"); value.setFloat(null, 2.5f); wide.setFloat(null, 3.25f);
                    System.out.println(Model.value); System.out.println(Model.wide);
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-float-write").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n2.5\n3.25\n");
    }


    @Test
    void readsStaticFinalFloatingConstantsAndInitializesTheirClass() throws Exception {
        final Path project = project("reflection-static-final-floating");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    static { System.out.println("initialized"); }
                    public static final float FLOAT_VALUE = 3.25f;
                    public static final double DOUBLE_VALUE = -0.0;
                }
                public static void main(String[] args) throws Exception {
                    var first = Model.class.getDeclaredField("FLOAT_VALUE");
                    var second = Model.class.getDeclaredField("DOUBLE_VALUE");
                    System.out.println("before"); System.out.println(first.getDouble(null));
                    System.out.println(Double.doubleToRawLongBits(second.getDouble(null)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-final-floating").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n3.25\n-9223372036854775808\n");
    }

    @Test
    void readsFloatFieldsAndWidensNumericFields() throws Exception {
        final Path project = project("reflection-field-get-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model {
                    public byte small = -4; public short medium = -300; public char letter = 65535;
                    public int number = 16777217; public long large = 5000000001L; public float value = -0.0f;
                }
                public static void main(String[] args) throws Exception {
                    Model value = new Model();
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("small").getFloat(value)));
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("medium").getFloat(value)));
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("letter").getFloat(value)));
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("number").getFloat(value)));
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("large").getFloat(value)));
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("value").getFloat(value)));
                    value.value = Float.intBitsToFloat(0x7fc00001);
                    System.out.println(Float.floatToRawIntBits(Model.class.getDeclaredField("value").getFloat(value)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-float").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected);
    }


    @Test
    void initializesStaticFloatFieldBeforeReflectiveRead() throws Exception {
        final Path project = project("reflection-static-float-read");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static float value = 2.5f; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("value"); System.out.println("before");
                    System.out.println(field.getFloat(null)); System.out.println(field.getFloat(new Object()));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-float-read").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n2.5\n2.5\n");
    }

    @Test
    void checksFloatFieldReadReceiverAndAccess() throws Exception {
        final Path project = project("reflection-field-get-float-access");
        writeJava(project, "com.other.Model", """
            package com.other;
            public class Model { public float value; private float secret; }
            """);
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import com.other.Model;
            import java.lang.reflect.Field;
            public final class Main {
                public static void main(String[] args) throws Exception {
                    Field value = Model.class.getDeclaredField("value"), secret = Model.class.getDeclaredField("secret");
                    Object wrong = new Object(); Model model = new Model();
                    try { value.getFloat(null); } catch (NullPointerException e) { System.out.println("null"); }
                    try { value.getFloat(wrong); } catch (IllegalArgumentException e) { System.out.println("wrong"); }
                    try { secret.getFloat(model); } catch (IllegalAccessException e) { System.out.println("denied"); }
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-float-access").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("null\nwrong\ndenied\n");
    }

    @Test
    void rejectsUnsupportedFloatFieldReadsExplicitly() throws Exception {
        final Path project = project("reflection-field-get-float-unsupported");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { public boolean flag; public volatile float changing; public double narrow; }
                public static void main(String[] args) throws Exception {
                    Model model = new Model(); var flag = Model.class.getDeclaredField("flag");
                    var changing = Model.class.getDeclaredField("changing"); var narrow = Model.class.getDeclaredField("narrow");
                    try { flag.getFloat(model); } catch (UnsupportedOperationException e) { System.out.println("type unsupported"); }
                    try { changing.getFloat(model); } catch (UnsupportedOperationException e) { System.out.println("volatile unsupported"); }
                    try { narrow.getFloat(model); } catch (UnsupportedOperationException e) { System.out.println("narrow unsupported"); }
                }
            }
            """);
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-float-unsupported").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo("type unsupported\nvolatile unsupported\nnarrow unsupported\n");
    }

    @Test
    void readsFloatFieldsDuringConcurrentGc() throws Exception {
        final Path project = project("reflection-field-get-float-gc");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            import java.lang.reflect.Field;
            import java.util.concurrent.atomic.AtomicInteger;
            public final class Main {
                static final AtomicInteger signal = new AtomicInteger(), finished = new AtomicInteger();
                public static final class Model { public float value = 7.25f; }
                static final class Worker implements Runnable {
                    public void run() {
                        while (signal.get() == 0) Thread.yield();
                        for (int i = 0; i < 1000; i++) { byte[] value = new byte[64]; value[0] = 1; }
                        finished.set(1);
                    }
                }
                public static void main(String[] args) throws Exception {
                    Field field = Model.class.getDeclaredField("value"); Model model = new Model();
                    Thread worker = new Thread(new Worker()); worker.start(); signal.set(1);
                    while (finished.get() == 0) { if (field.getFloat(model) != 7.25f) throw new AssertionError(); }
                    worker.join(); System.out.println(field.getFloat(model));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString(), "--release");
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-field-get-float-gc").toString()),
            Duration.ofSeconds(60), Map.of("JAVAN_GC_SAFEPOINT_INTERVAL", "1", "JAVAN_GC_STRESS", "1"));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("7.25\n");
    }

    @Test
    void readsStaticFinalFloatConstantAndInitializesItsClass() throws Exception {
        final Path project = project("reflection-static-final-float");
        writeJava(project, "com.acme.Main", """
            package com.acme;
            public final class Main {
                public static final class Model { static { System.out.println("initialized"); } public static final float VALUE = -0.0f; }
                public static void main(String[] args) throws Exception {
                    var field = Model.class.getDeclaredField("VALUE"); System.out.println("before");
                    System.out.println(Float.floatToRawIntBits(field.getFloat(null)));
                }
            }
            """);
        final String expected = runJvm(project, "com.acme.Main");
        final CliRun build = run(tempDir, "build", project.toString());
        assertThat(build.exitCode()).as(build.stderr()).isZero();
        final ProcessResult result = process(project, List.of(project.resolve(".javan/bin/reflection-static-final-float").toString()));
        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stdout()).isEqualTo(expected).isEqualTo("before\ninitialized\n-2147483648\n");
    }


}
