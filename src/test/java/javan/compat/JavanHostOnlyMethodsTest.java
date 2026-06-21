package javan.compat;

import javan.classfile.MethodInfo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class JavanHostOnlyMethodsTest {
    @Test
    void isHostOnlyMethodAcceptsInternalInputStreamClassFileReader() {
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/classfile/ClassFileReader",
            method("read", "(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/classfile/ClassFile;")
        )).isTrue();
    }

    @Test
    void isHostOnlyMethodAcceptsInternalInputStreamMetadataReader() {
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/compat/ClassMetadataReader",
            method("read", "(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/compat/ClassMetadata;")
        )).isTrue();
    }

    @Test
    void isHostOnlyMethodAcceptsInternalToolchainHelpers() {
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/toolchain/JavanHome",
            method("property", "(Ljava/util/Properties;)Ljava/lang/String;")
        )).isTrue();
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/toolchain/ToolchainMetadataException",
            method("<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V")
        )).isTrue();
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/cli/Cli",
            method("run", "(Ljava/nio/file/Path;Ljava/io/PrintStream;Ljava/io/PrintStream;[Ljava/lang/String;)I")
        )).isTrue();
    }

    @Test
    void isHostOnlyMethodRejectsDifferentOwner() {
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/classfile/OtherReader",
            method("read", "(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/classfile/ClassFile;")
        )).isFalse();
    }

    @Test
    void isHostOnlyMethodRejectsDifferentName() {
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/toolchain/JavanHome",
            method("resolve", "(Ljava/util/Properties;)Ljava/lang/String;")
        )).isFalse();
    }

    @Test
    void isHostOnlyMethodRejectsDifferentDescriptor() {
        assertThat(JavanHostOnlyMethods.isHostOnlyMethod(
            "javan/cli/Cli",
            method("run", "(Ljava/nio/file/Path;[Ljava/lang/String;)I")
        )).isFalse();
    }

    private static MethodInfo method(final String name, final String descriptor) {
        return new MethodInfo(0, name, descriptor, Optional.empty());
    }
}
