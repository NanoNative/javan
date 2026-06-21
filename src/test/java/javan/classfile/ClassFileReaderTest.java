package javan.classfile;

import javan.compat.ClassMetadata;
import javan.compat.ClassMetadataReader;
import javan.compat.MemberMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class ClassFileReaderTest {
    private static final Path SOURCE = Path.of("Modified.class");

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
            });
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

        private Bytes nameAndType(final int nameIndex, final int descriptorIndex) {
            return u1(12).u2(nameIndex).u2(descriptorIndex);
        }

        private Bytes methodRef(final int classIndex, final int nameAndTypeIndex) {
            return u1(10).u2(classIndex).u2(nameAndTypeIndex);
        }

        private Bytes methodHandle(final int referenceKind, final int referenceIndex) {
            return u1(15).u1(referenceKind).u2(referenceIndex);
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
