package javan.classfile;

import javan.analysis.BytecodeControlFlow;
import javan.compat.ClassMetadata;
import javan.compat.ClassMetadataReader;
import javan.compat.MemberMetadata;
import javan.testing.TestSuite.PlatformTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
@PlatformTest
final class ClassFileReaderTest {
    private static final Path SOURCE = Path.of("Modified.class");

    @Test
    void normalizesLegacySubroutinesBeforeAnalysis() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(legacySubroutineClassfile(), SOURCE);
        final MethodInfo method = classFile.method("value", "()I").orElseThrow();

        assertThat(method.code().orElseThrow().instructions())
            .extracting(Instruction::mnemonic)
            .doesNotContain("jsr", "jsr_w", "ret")
            .contains("aconst_null", "goto_w");
        final BytecodeControlFlow.Result controlFlow = BytecodeControlFlow.analyze(method);
        assertThat(controlFlow.valid()).withFailMessage(controlFlow.issues().toString()).isTrue();
    }

    @Test
    void clonesLegacySubroutineForEveryCallSite() throws Exception {
        final byte[] code = new byte[]{
            3, 59,
            (byte) 168, 0, 11,
            (byte) 168, 0, 8,
            26, (byte) 172,
            0, 0, 0,
            76,
            (byte) 132, 0, 1,
            (byte) 169, 1
        };

        final MethodInfo method = new ClassFileReader()
            .read(legacySubroutineClassfile(code, 1, 2), SOURCE)
            .method("value", "()I").orElseThrow();

        assertThat(method.code().orElseThrow().instructions())
            .filteredOn(instruction -> "iinc".equals(instruction.mnemonic()))
            .hasSize(2);
        assertThat(BytecodeControlFlow.analyze(method).valid()).isTrue();
    }

    @Test
    void normalizesNestedLegacySubroutinesAndWideRet() throws Exception {
        final byte[] code = new byte[]{
            3, 59,
            (byte) 168, 0, 6,
            26, (byte) 172,
            0,
            76,
            (byte) 168, 0, 8,
            (byte) 132, 0, 1,
            (byte) 169, 1,
            77,
            (byte) 132, 0, 1,
            (byte) 196, (byte) 169, 0, 2
        };

        final MethodInfo method = new ClassFileReader()
            .read(legacySubroutineClassfile(code, 1, 3), SOURCE)
            .method("value", "()I").orElseThrow();

        assertThat(method.code().orElseThrow().instructions())
            .extracting(Instruction::mnemonic)
            .doesNotContain("jsr", "jsr_w", "ret", "wide");
        assertThat(BytecodeControlFlow.analyze(method).valid()).isTrue();
    }

    @Test
    void preservesBranchesAndExceptionHandlersInsideLegacySubroutines() throws Exception {
        final byte[] code = new byte[]{
            (byte) 168, 0, 6,
            (byte) 177,
            0, 0,
            75,
            3,
            (byte) 153, 0, 6,
            1,
            (byte) 191,
            76,
            (byte) 169, 0
        };

        final MethodInfo method = new ClassFileReader()
            .read(legacySubroutineClassfile(code, 1, 2, new int[]{11, 13, 13, 0}), SOURCE)
            .method("value", "()I").orElseThrow();

        assertThat(method.code().orElseThrow().exceptionTable()).hasSize(1);
        assertThat(method.code().orElseThrow().instructions())
            .extracting(Instruction::mnemonic)
            .contains("ifne", "goto_w")
            .doesNotContain("jsr", "ret");
        final BytecodeControlFlow.Result controlFlow = BytecodeControlFlow.analyze(method);
        assertThat(controlFlow.valid()).withFailMessage(controlFlow.issues().toString()).isTrue();
    }

    @Test
    void rejectsRetOutsideLegacySubroutine() {
        assertThatThrownBy(() -> new ClassFileReader().read(
            legacySubroutineClassfile(new byte[]{(byte) 169, 0}, 0, 1), SOURCE
        )).isInstanceOf(IOException.class)
            .hasMessage("Invalid legacy jsr/ret bytecode: ret outside a legacy subroutine at 0");
    }

    @TempDir
    private Path tempDir;

    @Test
    void readersDecodeModifiedUtf8ConstantPoolValues() throws Exception {
        final String className = "modified/Nul\u0000Euro\u20AC";
        final byte[] bytes = minimalClassfile(className);

        final ClassFile classFile = new ClassFileReader().read(bytes, SOURCE);
        final ClassMetadata metadata = new ClassMetadataReader().read(bytes, SOURCE);

        assertThat(classFile.name()).isEqualTo(className);
        assertThat(classFile.superName()).isEqualTo("java/lang/Object");
        assertThat(classFile.methods()).singleElement().satisfies(method -> {
            assertThat(method.name()).isEqualTo("<init>");
            assertThat(method.code()).isPresent();
            assertThat(method.code().orElseThrow().instructions())
                .extracting(Instruction::mnemonic)
                .containsExactly("aload_0", "invokespecial", "return");
        });
        assertThat(metadata.name()).isEqualTo(className);
        assertThat(metadata.constructors()).singleElement().satisfies(ClassFileReaderTest::assertConstructorMetadata);
    }

    @Test
    void readerInputStreamDelegatesToByteArrayReader() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(
            new ByteArrayInputStream(minimalClassfile("stream/Input")),
            SOURCE
        );

        assertThat(classFile.name()).isEqualTo("stream/Input");
    }

    @Test
    void readerParsesSourceFileAttribute() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithLineNumberTableAndSourceFile(), SOURCE);

        assertThat(classFile.sourceFile()).contains("Demo.java");
    }

    @Test
    void readerParsesLineNumberTable() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithLineNumberTableAndSourceFile(), SOURCE);

        final CodeAttribute code = classFile.method("<init>", "()V").orElseThrow().code().orElseThrow();
        assertThat(code.lineNumbers()).containsExactly(new LineNumberEntry(0, 7), new LineNumberEntry(4, 8));
        assertThat(code.lineForOffset(0)).contains(7);
        assertThat(code.lineForOffset(3)).contains(7);
        assertThat(code.lineForOffset(4)).contains(8);
    }

    @Test
    void readerReturnsEmptyLineForOffsetWhenLineNumberTableIsMissing() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(minimalClassfile("line/Missing"), SOURCE);

        assertThat(classFile.method("<init>", "()V").orElseThrow().code().orElseThrow().lineForOffset(0)).isEmpty();
    }

    @Test
    void readerRejectsNonClassFile() {
        assertThatThrownBy(() -> new ClassFileReader().read(new byte[]{0, 1, 2, 3}, SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Not a Java class file: " + SOURCE);
    }

    @Test
    void readerParsesRecordAttributeComponents() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithRecordComponent("Ljava/lang/String;"), SOURCE);

        assertThat(classFile.recordComponents()).contains(List.of(
            new RecordComponentInfo("value", "Ljava/lang/String;")
        ));
    }

    @Test
    void readerParsesRealJavacPermittedSubclassesInSourceOrder() throws Exception {
        final Path source = tempDir.resolve("Part.java");
        final Path classes = tempDir.resolve("classes");
        Files.writeString(source, """
            sealed interface Part permits Value, Identity {
            }

            final class Value implements Part {
            }

            final class Identity implements Part {
            }
            """);
        Files.createDirectories(classes);
        final int result = ToolProvider.getSystemJavaCompiler().run(
            null, null, null, "-d", classes.toString(), source.toString()
        );
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }

        assertThat(new ClassFileReader().read(Files.readAllBytes(classes.resolve("Part.class")), source)
            .permittedSubclasses()).containsExactly("Value", "Identity");
    }

    @Test
    void readerUsesAuthoritativeNestHostMetadata() throws Exception {
        final Path source = tempDir.resolve("Outer.java");
        final Path classes = tempDir.resolve("nest-classes");
        Files.writeString(source, """
            public final class Outer {
                static final class Nested {
                }
            }
            """);
        Files.createDirectories(classes);
        final int result = ToolProvider.getSystemJavaCompiler().run(
            null, null, null, "-d", classes.toString(), source.toString()
        );
        if (result != 0) {
            throw new IllegalStateException("javac failed with exit code " + result);
        }

        final ClassFile outer = new ClassFileReader().read(Files.readAllBytes(classes.resolve("Outer.class")), source);
        final ClassFile nested = new ClassFileReader().read(
            Files.readAllBytes(classes.resolve("Outer$Nested.class")), source
        );

        assertThat(outer.nestHost()).isEqualTo("Outer");
        assertThat(nested.nestHost()).isEqualTo("Outer");
    }

    @Test
    void readerRejectsDuplicatePermittedSubclassesAttributes() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            permittedSubclasses(7), permittedSubclasses(7)
        ), SOURCE)).isInstanceOf(IOException.class).hasMessage("Duplicate PermittedSubclasses attribute");
    }

    @Test
    void readerRejectsDuplicatePermittedSubclassEntries() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            permittedSubclasses(7, 7)
        ), SOURCE)).isInstanceOf(IOException.class).hasMessage("Duplicate permitted subclass: sample/Value");
    }

    @Test
    void readerRejectsEmptyPermittedSubclassesAttribute() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            permittedSubclasses()
        ), SOURCE)).isInstanceOf(IOException.class).hasMessage("PermittedSubclasses attribute has no classes");
    }

    @Test
    void readerRejectsTrailingPermittedSubclassesAttributeBytes() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            new Bytes().u2(1).u2(7).u1(0).toByteArray()
        ), SOURCE)).isInstanceOf(IOException.class).hasMessage("Invalid PermittedSubclasses attribute length");
    }

    @Test
    void readerRejectsShortPermittedSubclassesAttribute() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            new Bytes().u2(1).u1(0).toByteArray()
        ), SOURCE)).isInstanceOf(IOException.class).hasMessage("Invalid PermittedSubclasses attribute length");
    }

    @Test
    void readerRejectsPermittedSubclassesAttributeWithShortCount() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            new Bytes().u1(0).toByteArray()
        ), SOURCE)).isInstanceOf(IOException.class).hasMessage("Invalid PermittedSubclasses attribute length");
    }

    @Test
    void readerRejectsPermittedSubclassEntryWithWrongConstantPoolKind() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            permittedSubclasses(6)
        ), SOURCE)).isInstanceOf(IOException.class)
            .hasMessage("Invalid PermittedSubclasses constant pool index 6: expected CONSTANT_Class");
    }

    @Test
    void readerRejectsOutOfRangePermittedSubclassConstantPoolIndex() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            permittedSubclasses(10)
        ), SOURCE)).isInstanceOf(IOException.class)
            .hasMessage("Invalid PermittedSubclasses constant pool index 10: out of range");
    }

    @Test
    void readerRejectsZeroPermittedSubclassConstantPoolIndex() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithPermittedSubclasses(
            permittedSubclasses(0)
        ), SOURCE)).isInstanceOf(IOException.class)
            .hasMessage("Invalid PermittedSubclasses constant pool index 0: out of range");
    }

    @Test
    void readerRejectsPermittedSubclassNameWithWrongConstantPoolKind() {
        assertThatThrownBy(() -> new ClassFileReader().read(
            classfileWithMalformedPermittedSubclassNameIndex(4), SOURCE
        )).isInstanceOf(IOException.class)
            .hasMessage("Invalid PermittedSubclasses class name constant pool index 4: expected CONSTANT_Utf8");
    }

    @Test
    void readerRejectsOutOfRangePermittedSubclassNameConstantPoolIndex() {
        assertThatThrownBy(() -> new ClassFileReader().read(
            classfileWithMalformedPermittedSubclassNameIndex(10), SOURCE
        )).isInstanceOf(IOException.class)
            .hasMessage("Invalid PermittedSubclasses class name constant pool index 10: out of range");
    }

    @Test
    void readerRejectsMalformedReferenceFieldDescriptor() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithRecordComponent("L"), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid field descriptor for value: L");
    }

    @Test
    void readerRejectsDuplicateRecordComponentNames() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithDuplicateRecordComponents(), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Duplicate record component: value");
    }

    @Test
    void readerRejectsDuplicateFields() {
        assertThatThrownBy(() -> new ClassFileReader().read(classfileWithDuplicateFields(), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Duplicate field: value Ljava/lang/String;");
    }

    @Test
    void readerRejectsUnsupportedConstantPoolTag() {
        final byte[] bytes = new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(2)
            .u1(99)
            .toByteArray();

        assertThatThrownBy(() -> new ClassFileReader().read(bytes, SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Unsupported constant pool tag 99");
    }

    @Test
    void readerRejectsInvalidWideInstruction() {
        assertThatThrownBy(() -> new ClassFileReader().read(minimalClassfile("broken/Wide", new byte[]{(byte) 196}), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid wide instruction at 0");
    }

    @Test
    void readerRejectsInvalidTableSwitchInstruction() {
        assertThatThrownBy(() -> new ClassFileReader().read(minimalClassfile("broken/TableSwitch", new byte[]{(byte) 170}), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid tableswitch at 0");
    }

    @Test
    void readerRejectsInvalidLookupSwitchInstruction() {
        assertThatThrownBy(() -> new ClassFileReader().read(minimalClassfile("broken/LookupSwitch", new byte[]{(byte) 171}), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid lookupswitch at 0");
    }

    @Test
    void readerDecodesInvokedynamicAndLiteralConstantKinds() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithInvokeDynamicAndLiterals(), SOURCE);

        final MethodInfo method = classFile.method("demo", "()V").orElseThrow();
        final List<Instruction> instructions = method.code().orElseThrow().instructions();
        assertThat(instructions).extracting(Instruction::mnemonic).contains(
            "ldc", "ldc2_w", "new", "anewarray", "checkcast", "instanceof", "invokedynamic"
        );
        assertThat(instructions.stream().filter(instruction -> instruction.mnemonic().equals("ldc")).findFirst().orElseThrow().stringValue())
            .contains("hello");
        assertThat(instructions.stream().filter(instruction -> instruction.intValue().isPresent()).findFirst().orElseThrow().intValue())
            .contains(7);
        assertThat(instructions.stream().filter(instruction -> instruction.floatValue().isPresent()).findFirst().orElseThrow().floatValue())
            .contains(1.5f);
        assertThat(instructions.stream().filter(instruction -> instruction.longValue().isPresent()).findFirst().orElseThrow().longValue())
            .contains(9L);
        assertThat(instructions.stream().filter(instruction -> instruction.doubleValue().isPresent()).findFirst().orElseThrow().doubleValue())
            .contains(2.5d);
        assertThat(instructions.stream().filter(instruction -> instruction.mnemonic().equals("ldc") && instruction.className().isPresent()))
            .singleElement()
            .satisfies(instruction -> assertThat(instruction.className()).contains("java/lang/String"));
        assertThat(instructions.stream().filter(instruction -> instruction.className().isPresent()).map(instruction -> instruction.className().orElseThrow()))
            .containsOnly("java/lang/String");
        assertThat(instructions.stream().filter(instruction -> instruction.dynamicRef().isPresent()).findFirst().orElseThrow().dynamicRef())
            .get()
            .satisfies(dynamicRef -> {
                assertThat(dynamicRef.name()).isEqualTo("dyn");
                assertThat(dynamicRef.descriptor()).isEqualTo("()Ljava/lang/String;");
                assertThat(dynamicRef.bootstrapOwner()).isEqualTo("bootstrap/Owner");
                assertThat(dynamicRef.bootstrapName()).isEqualTo("bootstrap");
                assertThat(dynamicRef.bootstrapDescriptor()).isEqualTo("()V");
                assertThat(dynamicRef.bootstrapArguments()).containsExactly("hello", "I", "7", "1.5", "9", "2.5");
                assertThat(dynamicRef.bootstrapArgumentDetails()).extracting(BootstrapArgument::text)
                    .containsExactly("hello", "I", "7", "1.5", "9", "2.5");
            });
    }

    @Test
    void readerPreservesConstantDynamicTagForLdc() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithConstantDynamicLiteral(new byte[]{18, 15, (byte) 177}, "Ljava/lang/String;"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(17);
        assertThat(instruction.className()).isEmpty();
        assertThat(instruction.stringValue()).isEmpty();
        assertThat(instruction.intValue()).isEmpty();
        assertThat(instruction.floatValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerPreservesConstantDynamicTagForLdcw() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithConstantDynamicLiteral(new byte[]{19, 0, 15, (byte) 177}, "Ljava/lang/String;"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(17);
        assertThat(instruction.className()).isEmpty();
        assertThat(instruction.stringValue()).isEmpty();
        assertThat(instruction.intValue()).isEmpty();
        assertThat(instruction.floatValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerPreservesConstantDynamicTagForLdc2w() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithConstantDynamicLiteral(new byte[]{20, 0, 15, (byte) 177}, "J"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc2_w");
        assertThat(instruction.constantPoolTag()).contains(17);
        assertThat(instruction.longValue()).isEmpty();
        assertThat(instruction.doubleValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerPreservesMethodTypeTagForLdc() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithMethodTypeLiteral(new byte[]{18, 6, (byte) 177}, "()Ljava/lang/String;"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(16);
        assertThat(instruction.className()).isEmpty();
        assertThat(instruction.stringValue()).isEmpty();
        assertThat(instruction.intValue()).isEmpty();
        assertThat(instruction.floatValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerPreservesMethodTypeTagForLdcw() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithMethodTypeLiteral(new byte[]{19, 0, 6, (byte) 177}, "()Ljava/lang/String;"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(16);
        assertThat(instruction.className()).isEmpty();
        assertThat(instruction.stringValue()).isEmpty();
        assertThat(instruction.intValue()).isEmpty();
        assertThat(instruction.floatValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerPreservesMethodHandleTagForLdc() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithMethodHandleLiteral(new byte[]{18, 14, (byte) 177}), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(15);
        assertThat(instruction.className()).isEmpty();
        assertThat(instruction.stringValue()).isEmpty();
        assertThat(instruction.intValue()).isEmpty();
        assertThat(instruction.floatValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerPreservesMethodHandleTagForLdcw() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithMethodHandleLiteral(new byte[]{19, 0, 14, (byte) 177}), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(15);
        assertThat(instruction.className()).isEmpty();
        assertThat(instruction.stringValue()).isEmpty();
        assertThat(instruction.intValue()).isEmpty();
        assertThat(instruction.floatValue()).isEmpty();
        assertThat(instruction.dynamicRef()).isEmpty();
    }

    @Test
    void readerDecodesStringLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithStringLiteral(new byte[]{18, 6, (byte) 177}, "hello"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(8);
        assertThat(instruction.stringValue()).contains("hello");
        assertThat(instruction.className()).isEmpty();
    }

    @Test
    void readerDecodesIntLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithIntLiteral(new byte[]{18, 5, (byte) 177}, 7), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(3);
        assertThat(instruction.intValue()).contains(7);
        assertThat(instruction.stringValue()).isEmpty();
    }

    @Test
    void readerDecodesFloatLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithFloatLiteral(new byte[]{18, 5, (byte) 177}, 1.5f), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(4);
        assertThat(instruction.floatValue()).contains(1.5f);
        assertThat(instruction.intValue()).isEmpty();
    }

    @Test
    void readerDecodesClassLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithClassLiteral(new byte[]{18, 6, (byte) 177}, "java/lang/String"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc");
        assertThat(instruction.constantPoolTag()).contains(7);
        assertThat(instruction.className()).contains("java/lang/String");
        assertThat(instruction.stringValue()).isEmpty();
    }

    @Test
    void readerDecodesWideStringLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithWideStringLiteral(new byte[]{19, 0, 6, (byte) 177}, "hello"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(8);
        assertThat(instruction.stringValue()).contains("hello");
        assertThat(instruction.className()).isEmpty();
    }

    @Test
    void readerDecodesWideIntLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithWideIntLiteral(new byte[]{19, 0, 5, (byte) 177}, 7), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(3);
        assertThat(instruction.intValue()).contains(7);
        assertThat(instruction.stringValue()).isEmpty();
    }

    @Test
    void readerDecodesWideFloatLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithWideFloatLiteral(new byte[]{19, 0, 5, (byte) 177}, 1.5f), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(4);
        assertThat(instruction.floatValue()).contains(1.5f);
        assertThat(instruction.intValue()).isEmpty();
    }

    @Test
    void readerDecodesWideClassLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithWideClassLiteral(new byte[]{19, 0, 6, (byte) 177}, "java/lang/String"), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc_w");
        assertThat(instruction.constantPoolTag()).contains(7);
        assertThat(instruction.className()).contains("java/lang/String");
        assertThat(instruction.stringValue()).isEmpty();
    }

    @Test
    void readerDecodesWideLongLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithWideLongLiteral(new byte[]{20, 0, 5, (byte) 177}, 9L), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc2_w");
        assertThat(instruction.constantPoolTag()).contains(5);
        assertThat(instruction.longValue()).contains(9L);
        assertThat(instruction.doubleValue()).isEmpty();
    }

    @Test
    void readerDecodesWideDoubleLiteral() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(classfileWithWideDoubleLiteral(new byte[]{20, 0, 5, (byte) 177}, 2.5d), SOURCE);

        final Instruction instruction = classFile.method("demo", "()V").orElseThrow().code().orElseThrow().instructions().getFirst();

        assertThat(instruction.mnemonic()).isEqualTo("ldc2_w");
        assertThat(instruction.constantPoolTag()).contains(6);
        assertThat(instruction.doubleValue()).contains(2.5d);
        assertThat(instruction.longValue()).isEmpty();
    }

    @Test
    void readerNormalizesSmallIntegerLiteralInstructions() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(
            minimalClassfile(
                "ints/Literals",
                new byte[]{
                    2, // iconst_m1
                    3, // iconst_0
                    4, // iconst_1
                    5, // iconst_2
                    6, // iconst_3
                    7, // iconst_4
                    8, // iconst_5
                    16, (byte) 0xFF, // bipush -1
                    16, 1, // bipush 1
                    17, (byte) 0xFF, (byte) 0xFE, // sipush -2
                    17, 0, 2, // sipush 2
                    (byte) 177 // return
                }
            ),
            SOURCE
        );

        final List<Integer> literalValues = classFile.method("<init>", "()V")
            .orElseThrow()
            .code()
            .orElseThrow()
            .instructions()
            .stream()
            .filter(instruction -> instruction.intValue().isPresent())
            .map(instruction -> instruction.intValue().orElseThrow())
            .toList();

        assertThat(literalValues).containsExactly(-1, 0, 1, 2, 3, 4, 5, -1, 1, -2, 2);
    }

    @Test
    void readerNormalizesBipushSignedBoundaryLiterals() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(
            minimalClassfile(
                "ints/BipushBoundaries",
                new byte[]{
                    16, (byte) 0x80,
                    16, (byte) 0x7F,
                    (byte) 177
                }
            ),
            SOURCE
        );

        final List<Integer> literalValues = classFile.method("<init>", "()V")
            .orElseThrow()
            .code()
            .orElseThrow()
            .instructions()
            .stream()
            .filter(instruction -> instruction.intValue().isPresent())
            .map(instruction -> instruction.intValue().orElseThrow())
            .toList();

        assertThat(literalValues).containsExactly(-128, 127);
    }

    @Test
    void readerNormalizesSipushSignedBoundaryLiterals() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(
            minimalClassfile(
                "ints/SipushBoundaries",
                new byte[]{
                    17, (byte) 0x80, (byte) 0x00,
                    17, (byte) 0x7F, (byte) 0xFF,
                    (byte) 177
                }
            ),
            SOURCE
        );

        final List<Integer> literalValues = classFile.method("<init>", "()V")
            .orElseThrow()
            .code()
            .orElseThrow()
            .instructions()
            .stream()
            .filter(instruction -> instruction.intValue().isPresent())
            .map(instruction -> instruction.intValue().orElseThrow())
            .toList();

        assertThat(literalValues).containsExactly(-32768, 32767);
    }

    @Test
    void readerNormalizesWideSmallLiteralInstructions() throws Exception {
        final ClassFile classFile = new ClassFileReader().read(
            minimalClassfile(
                "wide/Literals",
                new byte[]{
                    9,  // lconst_0
                    10, // lconst_1
                    11, // fconst_0
                    12, // fconst_1
                    13, // fconst_2
                    14, // dconst_0
                    15, // dconst_1
                    (byte) 177
                }
            ),
            SOURCE
        );

        final List<Instruction> instructions = classFile.method("<init>", "()V")
            .orElseThrow()
            .code()
            .orElseThrow()
            .instructions();

        assertThat(instructions.stream().filter(instruction -> instruction.longValue().isPresent()).map(instruction -> instruction.longValue().orElseThrow()).toList())
            .containsExactly(0L, 1L);
        assertThat(instructions.stream().filter(instruction -> instruction.floatValue().isPresent()).map(instruction -> instruction.floatValue().orElseThrow()).toList())
            .containsExactly(0.0f, 1.0f, 2.0f);
        assertThat(instructions.stream().filter(instruction -> instruction.doubleValue().isPresent()).map(instruction -> instruction.doubleValue().orElseThrow()).toList())
            .containsExactly(0.0d, 1.0d);
    }

    private static void assertConstructorMetadata(final MemberMetadata constructor) {
        assertThat(constructor.name()).isEqualTo("<init>");
        assertThat(constructor.attributes()).containsExactly("Code");
        assertThat(constructor.instructions())
            .extracting(instruction -> instruction.mnemonic())
            .containsExactly("aload_0", "invokespecial", "return");
    }

    private static byte[] minimalClassfile(final String className) {
        return minimalClassfile(className, constructorCode());
    }

    private static byte[] legacySubroutineClassfile() {
        final byte[] code = new byte[]{
            4, 59,
            (byte) 201, 0, 0, 0, 8,
            26, (byte) 172,
            0,
            76,
            (byte) 132, 0, 1,
            (byte) 169, 1
        };
        return legacySubroutineClassfile(code, 1, 2);
    }

    private static byte[] legacySubroutineClassfile(
        final byte[] code,
        final int maxStack,
        final int maxLocals
    ) {
        return legacySubroutineClassfile(code, maxStack, maxLocals, new int[0]);
    }

    private static byte[] legacySubroutineClassfile(
        final byte[] code,
        final int maxStack,
        final int maxLocals,
        final int[] handler
    ) {
        final int handlerCount = handler.length == 0 ? 0 : 1;
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(49)
            .u2(8)
            .utf8("legacy/Subroutine")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("value")
            .utf8("()I")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(5)
            .u2(6)
            .u2(1)
            .u2(7)
            .u4(12L + code.length + handlerCount * 8L)
            .u2(maxStack)
            .u2(maxLocals)
            .u4(code.length)
            .bytes(code)
            .u2(handlerCount)
            .optionalU2(handler, 0)
            .optionalU2(handler, 1)
            .optionalU2(handler, 2)
            .optionalU2(handler, 3)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] minimalClassfile(final String className, final byte[] methodCode) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8(className)
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("<init>")
            .utf8("()V")
            .utf8("Code")
            .nameAndType(5, 6)
            .methodRef(4, 8)
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0001)
            .u2(5)
            .u2(6)
            .u2(1)
            .u2(7)
            .u4(12L + methodCode.length)
            .u2(1)
            .u2(1)
            .u4(methodCode.length)
            .bytes(methodCode)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithRecordComponent(final String descriptor) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(8)
            .utf8("sample/Value")
            .classInfo(1)
            .utf8("java/lang/Record")
            .classInfo(3)
            .utf8("value")
            .utf8(descriptor)
            .utf8("Record")
            .u2(0x0031)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(1)
            .u2(0x0012)
            .u2(5)
            .u2(6)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(7)
            .u4(8)
            .u2(1)
            .u2(5)
            .u2(6)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithPermittedSubclasses(final byte[]... attributes) {
        final Bytes result = new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(69)
            .u2(10)
            .utf8("sample/Part")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("PermittedSubclasses")
            .utf8("sample/Value")
            .classInfo(6)
            .utf8("sample/Identity")
            .classInfo(8)
            .u2(0x0601)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(0)
            .u2(attributes.length);
        for (final byte[] attribute : attributes) {
            result.u2(5).u4(attribute.length).bytes(attribute);
        }
        return result.toByteArray();
    }

    private static byte[] permittedSubclasses(final int... classIndexes) {
        final Bytes result = new Bytes().u2(classIndexes.length);
        for (final int classIndex : classIndexes) {
            result.u2(classIndex);
        }
        return result.toByteArray();
    }

    private static byte[] classfileWithMalformedPermittedSubclassNameIndex(final int nameIndex) {
        final byte[] attribute = permittedSubclasses(7);
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(69)
            .u2(8)
            .utf8("sample/Part")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("PermittedSubclasses")
            .utf8("unused")
            .classInfo(nameIndex)
            .u2(0x0601)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(5)
            .u4(attribute.length)
            .bytes(attribute)
            .toByteArray();
    }

    private static byte[] classfileWithDuplicateRecordComponents() {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(8)
            .utf8("sample/Value")
            .classInfo(1)
            .utf8("java/lang/Record")
            .classInfo(3)
            .utf8("value")
            .utf8("Ljava/lang/String;")
            .utf8("Record")
            .u2(0x0031)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(1)
            .u2(0x0012)
            .u2(5)
            .u2(6)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(7)
            .u4(14)
            .u2(2)
            .u2(5)
            .u2(6)
            .u2(0)
            .u2(5)
            .u2(6)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithDuplicateFields() {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(7)
            .utf8("sample/Value")
            .classInfo(1)
            .utf8("java/lang/Record")
            .classInfo(3)
            .utf8("value")
            .utf8("Ljava/lang/String;")
            .u2(0x0031)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(2)
            .u2(0x0012)
            .u2(5)
            .u2(6)
            .u2(0)
            .u2(0x0012)
            .u2(5)
            .u2(6)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithLineNumberTableAndSourceFile() {
        final byte[] methodCode = constructorCode();
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(13)
            .utf8("line/Demo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("<init>")
            .utf8("()V")
            .utf8("Code")
            .nameAndType(5, 6)
            .methodRef(4, 8)
            .utf8("LineNumberTable")
            .utf8("SourceFile")
            .utf8("Demo.java")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0001)
            .u2(5)
            .u2(6)
            .u2(1)
            .u2(7)
            .u4(28L + methodCode.length)
            .u2(1)
            .u2(1)
            .u4(methodCode.length)
            .bytes(methodCode)
            .u2(0)
            .u2(1)
            .u2(10)
            .u4(10)
            .u2(2)
            .u2(0)
            .u2(7)
            .u2(4)
            .u2(8)
            .u2(1)
            .u2(11)
            .u4(2)
            .u2(12)
            .toByteArray();
    }

    private static byte[] classfileWithInvokeDynamicAndLiterals() {
        final byte[] code = new byte[]{
            18, 5,
            18, 6,
            18, 7,
            18, 13,
            20, 0, 8,
            20, 0, 10,
            (byte) 187, 0, 13,
            (byte) 189, 0, 13,
            (byte) 192, 0, 13,
            (byte) 193, 0, 13,
            (byte) 186, 0, 25, 0, 0,
            (byte) 177
        };
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(29)
            .utf8("sample/Demo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("hello")
            .rawInteger(7)
            .rawFloat(1.5f)
            .rawLong(9L)
            .rawDouble(2.5d)
            .utf8("java/lang/String")
            .classInfo(12)
            .utf8("I")
            .utf8("dyn")
            .utf8("()Ljava/lang/String;")
            .nameAndType(15, 16)
            .utf8("bootstrap/Owner")
            .classInfo(18)
            .utf8("bootstrap")
            .utf8("()V")
            .nameAndType(20, 21)
            .methodRef(19, 22)
            .methodHandle(6, 23)
            .dynamicEntry(18, 0, 17)
            .utf8("demo")
            .utf8("Code")
            .utf8("BootstrapMethods")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(26)
            .u2(21)
            .u2(1)
            .u2(27)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(28)
            .u4(18)
            .u2(1)
            .u2(24)
            .u2(6)
            .u2(5)
            .u2(14)
            .u2(6)
            .u2(7)
            .u2(8)
            .u2(10)
            .u2(11)
            .toByteArray();
    }

    private static byte[] classfileWithConstantDynamicLiteral(final byte[] code, final String dynamicDescriptor) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(20)
            .utf8("sample/CondyDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("dyn")
            .utf8(dynamicDescriptor)
            .nameAndType(5, 6)
            .utf8("bootstrap/Owner")
            .classInfo(8)
            .utf8("bootstrap")
            .utf8("()V")
            .nameAndType(10, 11)
            .methodRef(9, 12)
            .methodHandle(6, 13)
            .dynamicEntry(17, 0, 7)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .utf8("BootstrapMethods")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(16)
            .u2(17)
            .u2(1)
            .u2(18)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(19)
            .u4(6)
            .u2(1)
            .u2(14)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithMethodTypeLiteral(final byte[] code, final String methodTypeDescriptor) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(12)
            .utf8("sample/MethodTypeDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8(methodTypeDescriptor)
            .methodType(5)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .nameAndType(7, 8)
            .methodRef(4, 10)
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithMethodHandleLiteral(final byte[] code) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(15)
            .utf8("sample/MethodHandleDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .utf8("java/util/Map")
            .classInfo(8)
            .utf8("get")
            .utf8("(Ljava/lang/Object;)Ljava/lang/Object;")
            .nameAndType(10, 11)
            .u1(11).u2(9).u2(12)
            .methodHandle(9, 13)
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(5)
            .u2(6)
            .u2(1)
            .u2(7)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithStringLiteral(final byte[] code, final String value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8("sample/StringDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8(value)
            .stringInfo(5)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithIntLiteral(final byte[] code, final int value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(9)
            .utf8("sample/IntDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .rawInteger(value)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(6)
            .u2(7)
            .u2(1)
            .u2(8)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithFloatLiteral(final byte[] code, final float value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(9)
            .utf8("sample/FloatDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .rawFloat(value)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(6)
            .u2(7)
            .u2(1)
            .u2(8)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithClassLiteral(final byte[] code, final String className) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8("sample/ClassDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8(className)
            .classInfo(5)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithWideStringLiteral(final byte[] code, final String value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8("sample/WideStringDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8(value)
            .stringInfo(5)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithWideIntLiteral(final byte[] code, final int value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(9)
            .utf8("sample/WideIntDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .rawInteger(value)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(6)
            .u2(7)
            .u2(1)
            .u2(8)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithWideFloatLiteral(final byte[] code, final float value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(9)
            .utf8("sample/WideFloatDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .rawFloat(value)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(6)
            .u2(7)
            .u2(1)
            .u2(8)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithWideClassLiteral(final byte[] code, final String className) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8("sample/WideClassDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8(className)
            .classInfo(5)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(2)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithWideLongLiteral(final byte[] code, final long value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8("sample/WideLongDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .rawLong(value)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(4)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] classfileWithWideDoubleLiteral(final byte[] code, final double value) {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(10)
            .utf8("sample/WideDoubleDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .rawDouble(value)
            .utf8("demo")
            .utf8("()V")
            .utf8("Code")
            .u2(0x0021)
            .u2(2)
            .u2(4)
            .u2(0)
            .u2(0)
            .u2(1)
            .u2(0x0009)
            .u2(7)
            .u2(8)
            .u2(1)
            .u2(9)
            .u4(12L + code.length)
            .u2(4)
            .u2(1)
            .u4(code.length)
            .bytes(code)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
    }

    private static byte[] constructorCode() {
        return new byte[]{
            42,
            (byte) 183, 0, 9,
            (byte) 177
        };
    }

    private static final class Bytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private Bytes u1(final int value) {
            out.write(value & 0xFF);
            return this;
        }

        private Bytes u2(final int value) {
            return u1(value >>> 8).u1(value);
        }

        private Bytes optionalU2(final int[] values, final int index) {
            return index < values.length ? u2(values[index]) : this;
        }

        private Bytes u4(final long value) {
            return u1((int) (value >>> 24))
                .u1((int) (value >>> 16))
                .u1((int) (value >>> 8))
                .u1((int) value);
        }

        private Bytes utf8(final String value) {
            final byte[] encoded = modifiedUtf8(value);
            return u1(1).u2(encoded.length).bytes(encoded);
        }

        private Bytes classInfo(final int nameIndex) {
            return u1(7).u2(nameIndex);
        }

        private Bytes stringInfo(final int stringIndex) {
            return u1(8).u2(stringIndex);
        }

        private Bytes nameAndType(final int nameIndex, final int descriptorIndex) {
            return u1(12).u2(nameIndex).u2(descriptorIndex);
        }

        private Bytes methodRef(final int classIndex, final int nameAndTypeIndex) {
            return u1(10).u2(classIndex).u2(nameAndTypeIndex);
        }

        private Bytes methodHandle(final int referenceKind, final int referenceIndex) {
            return u1(15).u1(referenceKind).u2(referenceIndex);
        }

        private Bytes methodType(final int descriptorIndex) {
            return u1(16).u2(descriptorIndex);
        }

        private Bytes dynamicEntry(final int tag, final int bootstrapIndex, final int nameAndTypeIndex) {
            return u1(tag).u2(bootstrapIndex).u2(nameAndTypeIndex);
        }

        private Bytes rawInteger(final int value) {
            return u1(3).u4(value & 0xFFFF_FFFFL);
        }

        private Bytes rawFloat(final float value) {
            return u1(4).u4(Float.floatToRawIntBits(value) & 0xFFFF_FFFFL);
        }

        private Bytes rawLong(final long value) {
            return u1(5).u4((value >>> 32) & 0xFFFF_FFFFL).u4(value & 0xFFFF_FFFFL);
        }

        private Bytes rawDouble(final double value) {
            final long bits = Double.doubleToRawLongBits(value);
            return u1(6).u4((bits >>> 32) & 0xFFFF_FFFFL).u4(bits & 0xFFFF_FFFFL);
        }

        private Bytes bytes(final byte[] values) {
            out.writeBytes(values);
            return this;
        }

        private byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    private static byte[] modifiedUtf8(final String value) {
        final Bytes bytes = new Bytes();
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            if (character >= 0x0001 && character <= 0x007F) {
                bytes.u1(character);
            } else if (character <= 0x07FF) {
                bytes
                    .u1(0xC0 | ((character >> 6) & 0x1F))
                    .u1(0x80 | (character & 0x3F));
            } else {
                bytes
                    .u1(0xE0 | ((character >> 12) & 0x0F))
                    .u1(0x80 | ((character >> 6) & 0x3F))
                    .u1(0x80 | (character & 0x3F));
            }
        }
        return bytes.toByteArray();
    }
}
