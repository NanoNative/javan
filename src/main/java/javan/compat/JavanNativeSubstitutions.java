package javan.compat;

import javan.classfile.MethodInfo;
import javan.classfile.MethodRef;

import java.util.List;

/**
 * Exact Java methods whose bytecode body is replaced by a javan runtime intrinsic.
 */
public final class JavanNativeSubstitutions {
    private static final String PROCESS_RUNNER_OWNER = "javan/util/ProcessRunner";
    private static final String PROCESS_RUNNER_RUN_DESCRIPTOR =
        "(Ljava/nio/file/Path;Ljava/util/List;)Ljavan/util/ProcessRunner$Result;";
    private static final MethodRef PROCESS_RUNNER_RUN = new MethodRef(
        PROCESS_RUNNER_OWNER,
        "run",
        PROCESS_RUNNER_RUN_DESCRIPTOR
    );
    private static final List<String> REPORT_LINES = List.of(
        PROCESS_RUNNER_RUN.display() + " -> javan_process_run"
    );

    private JavanNativeSubstitutions() {
    }

    /**
     * Returns true when the call is lowered directly to a javan native runtime helper.
     *
     * @param methodRef call target
     * @return true for exact substituted calls
     */
    public static boolean isSubstitutedCall(final MethodRef methodRef) {
        return PROCESS_RUNNER_RUN.owner().equals(methodRef.owner())
            && PROCESS_RUNNER_RUN.name().equals(methodRef.name())
            && PROCESS_RUNNER_RUN.descriptor().equals(methodRef.descriptor());
    }

    /**
     * Returns runtime modules required by exact substituted calls.
     *
     * @param methodRef call target
     * @return ordered runtime modules
     */
    public static List<String> runtimeModules(final MethodRef methodRef) {
        if (isSubstitutedCall(methodRef)) {
            return List.of("process");
        }
        return List.of();
    }

    /**
     * Returns true when an unreachable Java fallback body is intentionally ignored by native verification.
     *
     * @param owner method owner
     * @param method method metadata
     * @return true for exact substituted fallback methods
     */
    public static boolean isSubstitutedFallbackMethod(final String owner, final MethodInfo method) {
        return PROCESS_RUNNER_RUN.owner().equals(owner)
            && PROCESS_RUNNER_RUN.name().equals(method.name())
            && PROCESS_RUNNER_RUN.descriptor().equals(method.descriptor());
    }

    /**
     * Returns stable report lines for native substitutions.
     *
     * @return substitution report lines
     */
    public static List<String> reportLines() {
        return REPORT_LINES;
    }
}
