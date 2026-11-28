package javan;

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class CliMalformedUtf8IntegrationTest extends CliIntegrationSupport {
    @Test
    void rejectsMalformedUtf8ConstantsLikeTheJvm() throws Exception {
        final ClassDesc subject = ClassDesc.of("dep.Subject");
        final byte[] valid = ClassFile.of().build(subject, builder -> builder
            .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
            .withField("VALUE", ClassDesc.of("java.lang.String"), field -> field
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL)
                .with(ConstantValueAttribute.of("target")))
            .withMethodBody("main", MethodTypeDesc.ofDescriptor("([Ljava/lang/String;)V"),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.return_()));
        final byte[] marker = "target".getBytes(StandardCharsets.US_ASCII);
        final List<byte[]> replacements = List.of(
            new byte[]{'t', 0, 'r', 'g', 'e', 't'},
            new byte[]{(byte) 0xc1, (byte) 0xb4, 'r', 'g', 'e', 't'},
            new byte[]{(byte) 0xe0, (byte) 0x81, (byte) 0xb4, 'g', 'e', 't'}
        );
        int index = -1;
        for (int position = 0; position <= valid.length - marker.length; position++) {
            boolean equal = true;
            for (int i = 0; i < marker.length; i++) if (valid[position + i] != marker[i]) equal = false;
            if (equal) { index = position; break; }
        }
        assertThat(index).isNotNegative();
        for (int scenario = 0; scenario < replacements.size(); scenario++) {
            final byte[] malformed = valid.clone();
            System.arraycopy(replacements.get(scenario), 0, malformed, index, marker.length);
            final class Loader extends ClassLoader {
                Class<?> define() { return defineClass(null, malformed, 0, malformed.length); }
            }
            assertThatThrownBy(() -> new Loader().define()).isInstanceOf(ClassFormatError.class);
            final Path classes = tempDir.resolve("malformed-utf8-" + scenario);
            Files.createDirectories(classes.resolve("dep"));
            Files.write(classes.resolve("dep/Subject.class"), malformed);
            final CliRun check = run(tempDir, "check", classes.toString(), "--main", "dep.Subject");
            assertThat(check.exitCode()).as(check.stderr()).isNotZero();
            assertThat(check.stderr()).contains("Malformed modified UTF-8");
            final CliRun repeat = run(tempDir, "check", classes.toString(), "--main", "dep.Subject");
            assertThat(repeat.exitCode()).isEqualTo(check.exitCode());
            assertThat(repeat.stderr()).isEqualTo(check.stderr());
        }
    }
}
