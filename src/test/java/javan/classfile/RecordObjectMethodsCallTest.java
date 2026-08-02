package javan.classfile;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@TestInstance(PER_CLASS)
@Execution(CONCURRENT)
final class RecordObjectMethodsCallTest {
    private ClassFile recordClass;
    private ClassFile genericRecordClass;
    private ClassFile mapRecordClass;
    private ClassFile ordinaryClass;
    private MethodInfo equalsMethod;
    private MethodInfo hashCodeMethod;
    private DynamicRef equalsDynamic;
    private DynamicRef hashCodeDynamic;

    @BeforeAll
    void compileRealJavacClassFiles(@TempDir final Path tempDir) throws Exception {
        final Path sourceRoot = tempDir.resolve("src");
        final Path classes = tempDir.resolve("classes");
        final Path recordSource = sourceRoot.resolve("com/acme/Sample.java");
        final Path genericRecordSource = sourceRoot.resolve("com/acme/GenericSample.java");
        final Path mapRecordSource = sourceRoot.resolve("com/acme/MapSample.java");
        final Path ordinarySource = sourceRoot.resolve("com/acme/Other.java");
        Files.createDirectories(recordSource.getParent());
        Files.createDirectories(classes);
        Files.writeString(recordSource, """
            package com.acme;

            public record Sample(int number, String text) {
            }
            """);
        Files.writeString(ordinarySource, """
            package com.acme;

            import java.util.function.Function;

            public final class Other {
                private Other() {
                }

                public static String concat(final int value) {
                    return "value=" + value;
                }

                public static Function<String, String> identity() {
                    return value -> value;
                }
            }
            """);
        Files.writeString(genericRecordSource, """
            package com.acme;

            import java.util.List;

            public record GenericSample(List<List<Sample>> values) {
            }
            """);
        Files.writeString(mapRecordSource, """
            package com.acme;

            import java.util.Map;

            public record MapSample(Map<String, String> values) {
            }
            """);
        final int exitCode = ToolProvider.getSystemJavaCompiler().run(
            null,
            null,
            null,
            "-d",
            classes.toString(),
            recordSource.toString(),
            genericRecordSource.toString(),
            mapRecordSource.toString(),
            ordinarySource.toString()
        );
        if (exitCode != 0) {
            throw new IllegalStateException("javac failed with exit code " + exitCode);
        }
        final ClassFileReader reader = new ClassFileReader();
        recordClass = reader.read(
            Files.readAllBytes(classes.resolve("com/acme/Sample.class")),
            classes.resolve("com/acme/Sample.class")
        );
        ordinaryClass = reader.read(
            Files.readAllBytes(classes.resolve("com/acme/Other.class")),
            classes.resolve("com/acme/Other.class")
        );
        genericRecordClass = reader.read(
            Files.readAllBytes(classes.resolve("com/acme/GenericSample.class")),
            classes.resolve("com/acme/GenericSample.class")
        );
        mapRecordClass = reader.read(
            Files.readAllBytes(classes.resolve("com/acme/MapSample.class")),
            classes.resolve("com/acme/MapSample.class")
        );
        equalsMethod = requiredMethod(recordClass, "equals", "(Ljava/lang/Object;)Z");
        hashCodeMethod = requiredMethod(recordClass, "hashCode", "()I");
        equalsDynamic = requiredDynamic(equalsMethod);
        hashCodeDynamic = requiredDynamic(hashCodeMethod);
    }

    @Test
    void resolvesRealJavacEqualsBootstrap() {
        assertThat(RecordObjectMethodsCall.resolve(recordClass, equalsMethod, equalsDynamic))
            .map(RecordObjectMethodsCall::fields)
            .contains(recordClass.fields());
    }

    @Test
    void resolvesRealJavacHashCodeBootstrap() {
        assertThat(RecordObjectMethodsCall.resolveHashCode(recordClass, hashCodeMethod, hashCodeDynamic))
            .map(RecordObjectMethodsCall::fields)
            .contains(recordClass.fields());
    }

    @Test
    void retainsRealJavacRecordComponents() {
        assertThat(recordClass.recordComponents()).contains(List.of(
            new RecordComponentInfo("number", "I"),
            new RecordComponentInfo("text", "Ljava/lang/String;")
        ));
    }

    @Test
    void retainsRealJavacGenericFieldSignature() {
        assertThat(genericRecordClass.fields().getFirst().signature())
            .contains("Ljava/util/List<Ljava/util/List<Lcom/acme/Sample;>;>;");
    }

    @Test
    void retainsRealJavacGenericRecordComponentSignature() {
        assertThat(genericRecordClass.recordComponents().orElseThrow().getFirst().signature())
            .contains("Ljava/util/List<Ljava/util/List<Lcom/acme/Sample;>;>;");
    }

    @Test
    void plansNestedListsFromRealJavacMetadata() {
        final MethodInfo method = requiredMethod(genericRecordClass, "hashCode", "()I");
        final RecordObjectMethodsCall.Shape sample = new RecordObjectMethodsCall.Shape(
            "Lcom/acme/Sample;",
            Optional.of("com/acme/Sample"),
            Optional.empty(),
            true
        );
        final RecordObjectMethodsCall.Shape nested = new RecordObjectMethodsCall.Shape(
            "Ljava/util/List;",
            Optional.of("java/util/List"),
            Optional.of(sample),
            true
        );
        final RecordObjectMethodsCall.Shape expected = new RecordObjectMethodsCall.Shape(
            "Ljava/util/List;",
            Optional.of("java/util/List"),
            Optional.of(nested),
            true
        );

        assertThat(RecordObjectMethodsCall.resolveHashCode(
            genericRecordClass,
            method,
            requiredDynamic(method)
        )).map(call -> call.components().getFirst().shape()).contains(expected);
    }

    @Test
    void retainsExactStringMapShapeFromRealJavacMetadata() {
        final MethodInfo method = requiredMethod(mapRecordClass, "hashCode", "()I");

        assertThat(RecordObjectMethodsCall.resolveHashCode(mapRecordClass, method, requiredDynamic(method)))
            .map(call -> call.components().getFirst().shape().isStringMap())
            .contains(true);
    }

    @Test
    void malformedMapSignatureProducesInvalidShape() {
        final FieldInfo originalField = mapRecordClass.fields().getFirst();
        final RecordComponentInfo originalComponent = mapRecordClass.recordComponents().orElseThrow().getFirst();
        final Optional<String> malformed = Optional.of("Ljava/util/Map<Ljava/lang/String;Ljava/lang/String;");
        final FieldInfo field = new FieldInfo(
            originalField.accessFlags(),
            originalField.name(),
            originalField.descriptor(),
            malformed
        );
        final RecordComponentInfo component = new RecordComponentInfo(
            originalComponent.name(),
            originalComponent.descriptor(),
            malformed
        );
        final MethodInfo method = requiredMethod(mapRecordClass, "hashCode", "()I");

        assertThat(RecordObjectMethodsCall.resolveHashCode(
            copyRecordClass(mapRecordClass, List.of(field), List.of(component)),
            method,
            requiredDynamic(method)
        )).map(call -> call.components().getFirst().shape().valid()).contains(false);
    }

    @Test
    void parameterizedStringMapSignatureProducesInvalidShape() {
        final FieldInfo originalField = mapRecordClass.fields().getFirst();
        final RecordComponentInfo originalComponent = mapRecordClass.recordComponents().orElseThrow().getFirst();
        final Optional<String> malformed = Optional.of(
            "Ljava/util/Map<Ljava/lang/String<Ljava/lang/Integer;>;Ljava/lang/String;>;"
        );
        final FieldInfo field = new FieldInfo(
            originalField.accessFlags(),
            originalField.name(),
            originalField.descriptor(),
            malformed
        );
        final RecordComponentInfo component = new RecordComponentInfo(
            originalComponent.name(),
            originalComponent.descriptor(),
            malformed
        );
        final MethodInfo method = requiredMethod(mapRecordClass, "hashCode", "()I");

        assertThat(RecordObjectMethodsCall.resolveHashCode(
            copyRecordClass(mapRecordClass, List.of(field), List.of(component)),
            method,
            requiredDynamic(method)
        )).map(call -> call.components().getFirst().shape().valid()).contains(false);
    }

    @Test
    void mapDescriptorWithListSignatureProducesInvalidShape() {
        final FieldInfo originalField = mapRecordClass.fields().getFirst();
        final RecordComponentInfo originalComponent = mapRecordClass.recordComponents().orElseThrow().getFirst();
        final Optional<String> listSignature = Optional.of("Ljava/util/List<Ljava/lang/String;>;");
        final FieldInfo field = new FieldInfo(
            originalField.accessFlags(),
            originalField.name(),
            originalField.descriptor(),
            listSignature
        );
        final RecordComponentInfo component = new RecordComponentInfo(
            originalComponent.name(),
            originalComponent.descriptor(),
            listSignature
        );
        final MethodInfo method = requiredMethod(mapRecordClass, "hashCode", "()I");

        assertThat(RecordObjectMethodsCall.resolveHashCode(
            copyRecordClass(mapRecordClass, List.of(field), List.of(component)),
            method,
            requiredDynamic(method)
        )).map(call -> call.components().getFirst().shape().valid()).contains(false);
    }

    @Test
    void rejectsRecordSuperclassWithoutRecordAttribute() {
        final ClassFile nonRecord = new ClassFile(
            recordClass.majorVersion(),
            recordClass.name(),
            recordClass.superName(),
            recordClass.accessFlags(),
            recordClass.interfaces(),
            recordClass.fields(),
            recordClass.methods(),
            recordClass.sourceFile(),
            recordClass.source(),
            recordClass.application()
        );

        assertThat(RecordObjectMethodsCall.resolve(nonRecord, equalsMethod, equalsDynamic)).isEmpty();
    }

    @Test
    void rejectsMalformedReferenceComponentDescriptor() {
        final List<FieldInfo> fields = List.of(
            recordClass.fields().getFirst(),
            new FieldInfo(recordClass.fields().get(1).accessFlags(), "text", "L")
        );
        final List<RecordComponentInfo> components = List.of(
            recordClass.recordComponents().orElseThrow().getFirst(),
            new RecordComponentInfo("text", "L")
        );
        final DynamicRef dynamicRef = withArgument(
            equalsDynamic,
            3,
            BootstrapArgument.methodHandle(1, new MethodRef(recordClass.name(), "text", "L"))
        );

        assertThat(RecordObjectMethodsCall.resolve(
            copyRecordClass(fields, components),
            equalsMethod,
            dynamicRef
        )).isEmpty();
    }

    @Test
    void rejectsRecordAttributeComponentMismatch() {
        final List<RecordComponentInfo> components = List.of(
            new RecordComponentInfo("renamed", "I"),
            recordClass.recordComponents().orElseThrow().get(1)
        );

        assertThat(RecordObjectMethodsCall.resolve(
            copyRecordClass(recordClass.fields(), components),
            equalsMethod,
            equalsDynamic
        )).isEmpty();
    }

    @Test
    void rejectsRecordAttributeSignatureMismatch() {
        final FieldInfo field = genericRecordClass.fields().getFirst();
        final RecordComponentInfo component = genericRecordClass.recordComponents().orElseThrow().getFirst();
        final List<RecordComponentInfo> components = List.of(new RecordComponentInfo(
            component.name(),
            component.descriptor(),
            Optional.of("Ljava/util/List<Ljava/lang/String;>;")
        ));
        final MethodInfo method = requiredMethod(genericRecordClass, "hashCode", "()I");

        assertThat(RecordObjectMethodsCall.resolveHashCode(
            copyRecordClass(genericRecordClass, List.of(field), components),
            method,
            requiredDynamic(method)
        )).isEmpty();
    }

    @Test
    void rejectsMutableRecordBackingField() {
        final FieldInfo original = recordClass.fields().getFirst();
        final List<FieldInfo> fields = new ArrayList<>(recordClass.fields());
        fields.set(0, new FieldInfo(
            original.accessFlags() & ~0x0010,
            original.name(),
            original.descriptor(),
            original.signature()
        ));

        assertThat(RecordObjectMethodsCall.resolve(
            copyRecordClass(fields, recordClass.recordComponents().orElseThrow()),
            equalsMethod,
            equalsDynamic
        )).isEmpty();
    }

    @Test
    void rejectsNonPrivateRecordBackingField() {
        final FieldInfo original = recordClass.fields().getFirst();
        final List<FieldInfo> fields = new ArrayList<>(recordClass.fields());
        fields.set(0, new FieldInfo(
            original.accessFlags() & ~0x0002,
            original.name(),
            original.descriptor(),
            original.signature()
        ));

        assertThat(RecordObjectMethodsCall.resolve(
            copyRecordClass(fields, recordClass.recordComponents().orElseThrow()),
            equalsMethod,
            equalsDynamic
        )).isEmpty();
    }

    @Test
    void malformedGenericSignatureProducesInvalidClosedShape() {
        final FieldInfo originalField = genericRecordClass.fields().getFirst();
        final RecordComponentInfo originalComponent =
            genericRecordClass.recordComponents().orElseThrow().getFirst();
        final Optional<String> malformed = Optional.of("Ljava/util/List<Lcom/acme/Sample;");
        final FieldInfo field = new FieldInfo(
            originalField.accessFlags(),
            originalField.name(),
            originalField.descriptor(),
            malformed
        );
        final RecordComponentInfo component = new RecordComponentInfo(
            originalComponent.name(),
            originalComponent.descriptor(),
            malformed
        );
        final MethodInfo method = requiredMethod(genericRecordClass, "hashCode", "()I");

        assertThat(RecordObjectMethodsCall.resolveHashCode(
            copyRecordClass(genericRecordClass, List.of(field), List.of(component)),
            method,
            requiredDynamic(method)
        )).map(call -> call.components().getFirst().shape().valid()).contains(false);
    }

    @Test
    void rejectsBootstrapWithWrongHandleKind() {
        assertThat(RecordObjectMethodsCall.resolve(
            recordClass,
            equalsMethod,
            copy(equalsDynamic, 5, equalsDynamic.bootstrapArgumentDetails())
        )).isEmpty();
    }

    @Test
    void rejectsBootstrapWithWrongRecordClassLiteral() {
        assertThat(RecordObjectMethodsCall.resolve(
            recordClass,
            equalsMethod,
            withArgument(equalsDynamic, 0, BootstrapArgument.classLiteral("com/acme/Other"))
        )).isEmpty();
    }

    @Test
    void rejectsBootstrapWithWrongComponentNames() {
        assertThat(RecordObjectMethodsCall.resolve(
            recordClass,
            equalsMethod,
            withArgument(equalsDynamic, 1, BootstrapArgument.string("text;number"))
        )).isEmpty();
    }

    @Test
    void rejectsBootstrapWithWrongGetterHandleKind() {
        final BootstrapArgument getter = equalsDynamic.bootstrapArgumentDetails().get(2);

        assertThat(RecordObjectMethodsCall.resolve(
            recordClass,
            equalsMethod,
            withArgument(
                equalsDynamic,
                2,
                BootstrapArgument.methodHandle(2, getter.methodRef().orElseThrow())
            )
        )).isEmpty();
    }

    @Test
    void rejectsBootstrapWithWrongGetterDescriptor() {
        final MethodRef getter = equalsDynamic.bootstrapArgumentDetails().get(2).methodRef().orElseThrow();

        assertThat(RecordObjectMethodsCall.resolve(
            recordClass,
            equalsMethod,
            withArgument(
                equalsDynamic,
                2,
                BootstrapArgument.methodHandle(1, new MethodRef(getter.owner(), getter.name(), "J"))
            )
        )).isEmpty();
    }

    @Test
    void rejectsBootstrapWithMissingGetter() {
        assertThat(RecordObjectMethodsCall.resolve(
            recordClass,
            equalsMethod,
            copy(
                equalsDynamic,
                equalsDynamic.bootstrapReferenceKind(),
                equalsDynamic.bootstrapArgumentDetails().subList(
                    0,
                    equalsDynamic.bootstrapArgumentDetails().size() - 1
                )
            )
        )).isEmpty();
    }

    @Test
    void stringConcatInvokedynamicIsNotRecordMetadata() {
        final MethodInfo concat = requiredMethod(ordinaryClass, "concat", "(I)Ljava/lang/String;");

        assertThat(RecordObjectMethodsCall.resolve(ordinaryClass, concat, requiredDynamic(concat))).isEmpty();
    }

    @Test
    void lambdaInvokedynamicIsNotRecordMetadata() {
        final MethodInfo identity = requiredMethod(ordinaryClass, "identity", "()Ljava/util/function/Function;");

        assertThat(RecordObjectMethodsCall.resolve(ordinaryClass, identity, requiredDynamic(identity))).isEmpty();
    }

    private static MethodInfo requiredMethod(
        final ClassFile classFile,
        final String name,
        final String descriptor
    ) {
        return classFile.method(name, descriptor).orElseThrow();
    }

    private ClassFile copyRecordClass(
        final List<FieldInfo> fields,
        final List<RecordComponentInfo> components
    ) {
        return copyRecordClass(recordClass, fields, components);
    }

    private static ClassFile copyRecordClass(
        final ClassFile source,
        final List<FieldInfo> fields,
        final List<RecordComponentInfo> components
    ) {
        return new ClassFile(
            source.majorVersion(),
            source.name(),
            source.superName(),
            source.accessFlags(),
            source.interfaces(),
            fields,
            source.methods(),
            source.sourceFile(),
            java.util.Optional.of(components),
            source.source(),
            source.application()
        );
    }

    private static DynamicRef requiredDynamic(final MethodInfo method) {
        for (final Instruction instruction : method.code().orElseThrow().instructions()) {
            if (instruction.dynamicRef().isPresent()) {
                return instruction.dynamicRef().orElseThrow();
            }
        }
        throw new IllegalStateException("method has no invokedynamic instruction");
    }

    private static DynamicRef withArgument(
        final DynamicRef dynamicRef,
        final int index,
        final BootstrapArgument argument
    ) {
        final List<BootstrapArgument> arguments = new ArrayList<>(dynamicRef.bootstrapArgumentDetails());
        arguments.set(index, argument);
        return copy(dynamicRef, dynamicRef.bootstrapReferenceKind(), arguments);
    }

    private static DynamicRef copy(
        final DynamicRef dynamicRef,
        final int bootstrapReferenceKind,
        final List<BootstrapArgument> arguments
    ) {
        return new DynamicRef(
            dynamicRef.name(),
            dynamicRef.descriptor(),
            dynamicRef.bootstrapOwner(),
            dynamicRef.bootstrapName(),
            dynamicRef.bootstrapDescriptor(),
            bootstrapReferenceKind,
            arguments.stream().map(BootstrapArgument::text).toList(),
            arguments
        );
    }
}
