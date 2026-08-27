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
    private static final MethodRef PROCESS_RUNNER_RUN_RESULT = new MethodRef(
        PROCESS_RUNNER_OWNER,
        "runResult",
        PROCESS_RUNNER_RUN_DESCRIPTOR
    );
    private static final String FILES2_OWNER = "javan/util/Files2";
    private static final String FILES2_CREATE_DIRECTORIES_IF_POSSIBLE_DESCRIPTOR = "(Ljava/nio/file/Path;)Z";
    private static final MethodRef FILES2_CREATE_DIRECTORIES_IF_POSSIBLE = new MethodRef(
        FILES2_OWNER,
        "createDirectoriesIfPossible",
        FILES2_CREATE_DIRECTORIES_IF_POSSIBLE_DESCRIPTOR
    );
    private static final List<String> REPORT_LINES = List.of(
        PROCESS_RUNNER_RUN.display() + " -> javan_process_run",
        PROCESS_RUNNER_RUN_RESULT.display() + " -> javan_process_run",
        FILES2_CREATE_DIRECTORIES_IF_POSSIBLE.display() + " -> javan_files_create_directories_if_possible"
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
        return matches(PROCESS_RUNNER_RUN, methodRef)
            || matches(PROCESS_RUNNER_RUN_RESULT, methodRef)
            || matches(FILES2_CREATE_DIRECTORIES_IF_POSSIBLE, methodRef);
    }

    /**
     * Returns runtime modules required by exact substituted calls.
     *
     * @param methodRef call target
     * @return ordered runtime modules
     */
    public static List<String> runtimeModules(final MethodRef methodRef) {
        if (isSubstitutedCall(methodRef)) {
            if (matches(FILES2_CREATE_DIRECTORIES_IF_POSSIBLE, methodRef)) {
                return List.of("filesystem");
            }
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
        return matches(PROCESS_RUNNER_RUN, owner, method)
            || matches(PROCESS_RUNNER_RUN_RESULT, owner, method)
            || matches(FILES2_CREATE_DIRECTORIES_IF_POSSIBLE, owner, method);
    }

    /**
     * Returns stable report lines for native substitutions.
     *
     * @return substitution report lines
     */
    public static List<String> reportLines() {
        return REPORT_LINES;
    }

    private static boolean matches(final MethodRef expected, final MethodRef actual) {
        return expected.owner().equals(actual.owner())
            && expected.name().equals(actual.name())
            && expected.descriptor().equals(actual.descriptor());
    }

    private static boolean matches(final MethodRef expected, final String owner, final MethodInfo method) {
        return expected.owner().equals(owner)
            && expected.name().equals(method.name())
            && expected.descriptor().equals(method.descriptor());
    }
}
