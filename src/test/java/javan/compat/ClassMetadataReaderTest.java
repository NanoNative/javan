package javan.compat;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ClassMetadataReaderTest {
    private static final Path SOURCE = Path.of("Sample.class");

    @Test
    void readInputStreamDelegatesToByteArrayReader() throws Exception {
        final ClassMetadata metadata = new ClassMetadataReader().read(
            new ByteArrayInputStream(minimalClassfile("sample/InputStream", constructorCode())),
            SOURCE
        );

        assertThat(metadata.name()).isEqualTo("sample/InputStream");
        assertThat(metadata.packageName()).isEqualTo("sample");
        assertThat(metadata.methods()).isEmpty();
        assertThat(metadata.constructors()).singleElement().satisfies(constructor -> {
            assertThat(constructor.name()).isEqualTo("<init>");
            assertThat(constructor.instructions()).extracting(InstructionMetadata::mnemonic)
                .containsExactly("aload_0", "invokespecial", "return");
        });
    }

    @Test
    void readRejectsNonClassFile() {
        assertThatThrownBy(() -> new ClassMetadataReader().read(new byte[]{0, 1, 2, 3}, SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Not a Java class file: " + SOURCE);
    }

    @Test
    void readRejectsUnsupportedConstantPoolTag() {
        final byte[] bytes = new Bytes()
            .u4(0xCAFEBABEL)
            .u2(0)
            .u2(65)
            .u2(2)
            .u1(99)
            .toByteArray();

        assertThatThrownBy(() -> new ClassMetadataReader().read(bytes, SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Unsupported constant pool tag 99");
    }

    @Test
    void readRejectsInvalidWideInstruction() {
        assertThatThrownBy(() -> new ClassMetadataReader().read(minimalClassfile("sample/Wide", new byte[]{(byte) 196}), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid wide instruction at 0");
    }

    @Test
    void readRejectsInvalidTableSwitchInstruction() {
        assertThatThrownBy(() -> new ClassMetadataReader().read(minimalClassfile("sample/TableSwitch", new byte[]{(byte) 170}), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid tableswitch at 0");
    }

    @Test
    void readRejectsInvalidLookupSwitchInstruction() {
        assertThatThrownBy(() -> new ClassMetadataReader().read(minimalClassfile("sample/LookupSwitch", new byte[]{(byte) 171}), SOURCE))
            .isInstanceOf(IOException.class)
            .hasMessage("Invalid lookupswitch at 0");
    }

    @Test
    void readCollectsBootstrapMethodsAndDeduplicatesRepeatedAttributes() throws Exception {
        final ClassMetadata metadata = new ClassMetadataReader().read(classfileWithBootstrapMethods(), SOURCE);

        assertThat(metadata.preview()).isTrue();
        assertThat(metadata.deprecated()).isTrue();
        assertThat(metadata.attributes()).containsExactly("BootstrapMethods", "Deprecated");
        assertThat(metadata.bootstrapMethods()).containsExactly("methodHandle#unknown args=[Utf8:bootstrap-text, unknown]");
        assertThat(metadata.withModuleName("java.base").moduleName()).isEqualTo("java.base");
        assertThat(metadata.withApplication(false).application()).isFalse();
        assertThat(metadata.packageName()).isEqualTo("");
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

    private static byte[] classfileWithBootstrapMethods() {
        return new Bytes()
            .u4(0xCAFEBABEL)
            .u2(65_535)
            .u2(65)
            .u2(15)
            .utf8("BootstrapDemo")
            .classInfo(1)
            .utf8("java/lang/Object")
            .classInfo(3)
            .utf8("<init>")
            .utf8("()V")
            .utf8("Code")
            .utf8("BootstrapMethods")
            .utf8("Deprecated")
            .utf8("bootstrap-text")
            .rawLong(1L)
            .nameAndType(5, 6)
            .methodRef(4, 13)
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
            .bytes(constructorCode())
            .u2(0)
            .u2(0)
            .u2(3)
            .u2(8)
            .u4(8)
            .u2(1)
            .u2(9)
            .u2(2)
            .u2(10)
            .u2(12)
            .u2(9)
            .u4(0)
            .u2(9)
            .u4(0)
            .toByteArray();
    }

    private static byte[] constructorCode() {
        return new byte[]{
            42,
            (byte) 183, 0, 14,
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
            final byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return u1(1).u2(encoded.length).bytes(encoded);
        }

        private Bytes rawLong(final long value) {
            return u1(5).u4(value >>> 32).u4(value);
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
}
