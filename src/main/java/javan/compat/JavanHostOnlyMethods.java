package javan.compat;

import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.List;

/**
 * Exact Javan implementation methods used by JVM-hosted tooling but not by native self-hosted code.
 */
public final class JavanHostOnlyMethods {
    private static final List<MethodRef> METHODS = List.of(
        new MethodRef(
            "javan/classfile/ClassFileReader",
            "read",
            "(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/classfile/ClassFile;"
        ),
        new MethodRef(
            "javan/compat/ClassMetadataReader",
            "read",
            "(Ljava/io/InputStream;Ljava/nio/file/Path;)Ljavan/compat/ClassMetadata;"
        ),
        new MethodRef(
            "javan/toolchain/JavanHome",
            "property",
            "(Ljava/util/Properties;)Ljava/lang/String;"
        ),
        new MethodRef(
            "javan/toolchain/ToolchainMetadataException",
            "<init>",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V"
        ),
        new MethodRef(
            "javan/cli/Cli",
            "run",
            "(Ljava/nio/file/Path;Ljava/io/PrintStream;Ljava/io/PrintStream;[Ljava/lang/String;)I"
        )
    );

    private JavanHostOnlyMethods() {
    }

    /**
     * Returns true for exact JVM-host-only methods that may be ignored when unreachable.
     *
     * @param owner method owner
     * @param method method metadata
     * @return true for exact host-only methods
     */
    public static boolean isHostOnlyMethod(final String owner, final MethodInfo method) {
        for (final MethodRef methodRef : METHODS) {
            if (same(owner, method, methodRef)) {
                return true;
            }
        }
        return false;
    }

    private static boolean same(final String owner, final MethodInfo method, final MethodRef methodRef) {
        return methodRef.owner().equals(owner)
            && methodRef.name().equals(method.name())
            && methodRef.descriptor().equals(method.descriptor());
    }
}
