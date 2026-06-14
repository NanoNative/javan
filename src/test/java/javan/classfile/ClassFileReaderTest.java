package javan.classfile;

import javan.compat.ClassMetadata;
import javan.compat.ClassMetadataReader;
import javan.compat.MemberMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

@Execution(CONCURRENT)
final class ClassFileReaderTest {
    @Test
    void readersDecodeModifiedUtf8ConstantPoolValues() throws Exception {
        final String className = "modified/Nul\u0000Euro\u20AC";
        final byte[] bytes = minimalClassfile(className);
        final Path source = Path.of("Modified.class");

        final ClassFile classFile = new ClassFileReader().read(bytes, source);
        final ClassMetadata metadata = new ClassMetadataReader().read(bytes, source);

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

    private static void assertConstructorMetadata(final MemberMetadata constructor) {
        assertThat(constructor.name()).isEqualTo("<init>");
        assertThat(constructor.attributes()).containsExactly("Code");
        assertThat(constructor.instructions())
            .extracting(instruction -> instruction.mnemonic())
            .containsExactly("aload_0", "invokespecial", "return");
    }

    private static byte[] minimalClassfile(final String className) {
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
            .u4(17)
            .u2(1)
            .u2(1)
            .u4(5)
            .u1(42)
            .u1(183)
            .u2(9)
            .u1(177)
            .u2(0)
            .u2(0)
            .u2(0)
            .toByteArray();
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
