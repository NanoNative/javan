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
            "javan/cli/Cli",
            "run",
            "(Ljava/nio/file/Path;Ljava/io/PrintStream;Ljava/io/PrintStream;[Ljava/lang/String;)I"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "stopInterruptedProcess",
            "(Ljava/lang/Process;Ljava/lang/InterruptedException;)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "stopProcessTree",
            "(Ljava/lang/Process;)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "stopRootProcess",
            "(Ljava/lang/Process;)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "processTree",
            "(Ljava/lang/Process;)Ljava/util/List;"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "addProcessTree",
            "(Ljava/util/List;Ljava/lang/Process;)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "addProcess",
            "(Ljava/util/List;Ljava/lang/ProcessHandle;)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "processDepth",
            "(Ljava/lang/ProcessHandle;)I"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "stopProcesses",
            "(Ljava/util/List;Z)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "stopDescendants",
            "(Ljava/util/List;Ljava/lang/ProcessHandle;Z)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "stopProcess",
            "(Ljava/lang/ProcessHandle;Z)V"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "waitForProcessesExit",
            "(Ljava/util/List;J)Z"
        ),
        new MethodRef(
            "javan/util/ProcessRunner",
            "allProcessesExited",
            "(Ljava/util/List;)Z"
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
